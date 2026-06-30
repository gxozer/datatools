# RFC: Two-Pass Design for Donor Deduplication

**Ticket:** [PR-219](https://mgozer.atlassian.net/browse/PR-219)  
**Related:** [PR-156](https://mgozer.atlassian.net/browse/PR-156) (dedup implementation), [PR-207](https://mgozer.atlassian.net/browse/PR-207) (streaming isolation), [PR-208](https://mgozer.atlassian.net/browse/PR-208) (batch inserts)  
**Implementation:** `pipeline/src/main/kotlin/com/campaignfinances/pipeline/dedup/DedupRunner.kt`

---

## Problem

`DedupRunner` needs to:

1. Group contributions by normalized donor identity `(last, first, zip5, employer)`.
2. Insert one `donor` row per distinct group.
3. Insert one `donor_link` row per contribution, pointing to its donor.
4. Update `contribution.donor_id` for every matched contribution.

The full FEC dataset is tens of millions of rows. The design must not require loading all contribution IDs into JVM heap at once.

---

## Constraints

Three hard constraints shape the design.

### 1. Auto-increment IDs are not known until after INSERT

`donor.id` is assigned by MySQL. Until a `donor` row is inserted and the generated key is retrieved, the ID needed for `donor_link.donor_id` and `contribution.donor_id` does not exist anywhere in the system.

### 2. MySQL streaming blocks the connection

Row-at-a-time streaming (`fetchSize = Int.MIN_VALUE`) is required to avoid loading the entire result set into JVM heap. MySQL's wire protocol does not support multiplexing: while a streaming `ResultSet` is open on a connection, that connection cannot execute any other statement. Any write — INSERT, UPDATE — must go through a separate connection.

This means every pass needs two connections: one read-only for streaming, one transactional for writes.

### 3. Batching defers ID availability

Inserting donors one at a time (the original approach) produces one round-trip per new donor. With hundreds of thousands of distinct donors this caused multi-hour runtimes (observed: 11 356 s). Batching with `addBatch()`/`executeBatch()` is required. But batching means `donor.id` values are not available until a full batch of 1 000 flushes — not at the moment the first contribution for that donor is seen.

---

## The Two-Pass Design

```
Pass 1 ── read connection (streaming) ──► for each new DonorKey:
          write connection (batched)         INSERT INTO donor (batch 1 000)
                                             ← generated donor.id
                                          build keyToDonorId map in memory

Pass 2 ── read connection (streaming) ──► for each contribution:
          write connection (batched)         look up donor.id in keyToDonorId
                                             INSERT INTO donor_link (batch 1 000)
                                             UPDATE contribution.donor_id (batch 1 000)
```

**Pass 1** resolves every donor ID. When it finishes, `keyToDonorId` (a `Map<DonorKey, Long>`) holds one entry per distinct donor. Memory cost is O(D) where D is the number of distinct donors — typically in the hundreds of thousands, not tens of millions.

**Pass 2** writes links. Every contribution's donor ID is now known with certainty, so the write path is a simple lookup with no pending state.

Both passes share the same `STAGING_QUERY` (`staging_contribution JOIN contribution ORDER BY c.id`). Duplicate staging rows are handled identically in both: consecutive rows with the same `contribution.id` are skipped.

---

## Why Not One Pass?

A single pass is possible in theory. The obstacle is constraint 3 interacting with constraint 1.

When contribution #1 arrives for a new donor key, the donor is added to the insert batch. The `donor.id` is not yet available — the batch has not flushed. So the `donor_link` row for contribution #1 cannot be written yet either. It must be buffered.

Contributions #2, #3, … for the same pending donor also cannot be linked until the batch flushes. They join the buffer.

Meanwhile, contributions for *other* pending donors in the same batch also buffer. At flush time (every 1 000 new donors), the buffer is drained: generated IDs are zipped with pending donors, then buffered contributions are written out.

This works, but it replaces one conceptual loop with two interleaved buffers — pending donor inserts and pending contribution links — that must be kept in sync and flushed together. The code is harder to reason about, harder to test in isolation, and easier to corrupt (e.g. flushing one buffer without the other on exception).

Two passes separates these concerns cleanly. Pass 1 has one job (donors). Pass 2 has one job (links). Each is independently testable and auditable.

The cost of the second pass is one additional full scan of `staging_contribution JOIN contribution`. On a 10 M-row dataset this is a MySQL sequential scan — fast relative to the write work, and bounded by I/O rather than JVM computation.

---

## Why Both `contribution.donor_id` and `donor_link`?

Pass 2 writes to two places for every matched contribution. This section explains why both are needed.

### `contribution.donor_id`

A foreign key column on `contribution` pointing to `donor.id`. It is the **query shortcut**: joining `contribution` to `donor` via this FK is a single indexed lookup. Any query that needs to find a contribution's donor, or all contributions for a donor, uses this column.

### `donor_link`

A separate join table with columns `(donor_id, contribution_id, match_rule)`. It is the **audit record** of the dedup decision.

The critical column is `match_rule`. Currently it takes two values:

| Value | Meaning |
|---|---|
| `name+zip5+employer` | Matched on all three normalized fields — stronger signal |
| `name+zip5 (employer blank)` | Employer was absent; matched on two fields only — weaker signal |

A two-field match is more likely to be a false positive than a three-field match. Without `donor_link`, that distinction is lost: you would only know that contribution #42 belongs to donor #7, not whether the assignment rested on strong or weak evidence. You could not answer questions like "how many contributions were matched on only two fields?" or "show me all weak matches for this donor" without re-running the entire normalization pass from scratch.

`donor_link` also provides a natural extension point. Future columns — confidence score, manual review flag, override reason — belong here, not on `contribution`. A contribution's fields describe a payment; the dedup decision about who made that payment is a separate concern. Mixing them would blur that boundary and make the contribution table harder to query independently of dedup state.

### Why not just one of them?

| Option | Problem |
|---|---|
| `donor_link` only, no FK | Every donor lookup requires a join through `donor_link`; no direct FK index on `contribution` |
| `contribution.donor_id` only, no `donor_link` | Match reasoning (`match_rule`) is lost; no audit trail; re-running dedup produces no comparable artifact |
| Both | FK gives fast lookups; `donor_link` carries the audit metadata. Redundant by design. |

The redundancy is intentional. `DedupRunner` writes both atomically in the same transaction, so they are always consistent. The clear step at the start of each run truncates both before rebuilding.

---

## Alternatives Considered

### Application-assigned IDs

If `donor.id` were assigned by the JVM (e.g. a local counter) rather than MySQL auto-increment, IDs would be known before INSERT and one pass would become straightforward. Rejected: it shifts ID management to the application, complicates re-runs (the counter must be persisted or reset), and diverges from the schema convention used everywhere else in the pipeline.

### Staging table for pending links

Insert all donors first (in a separate pre-pass), then join `contribution` against `donor` in SQL to write `donor_link` in bulk. This removes the JVM entirely from pass 2 link-writing. Rejected for Phase 1: it requires a temporary table or a full `donor` table scan join, and the `DonorKey` normalization logic (suffix stripping, employer aliases, zip5 extraction) lives in Kotlin — reproducing it in SQL faithfully is error-prone. Remains a viable optimization if pass 2 becomes a bottleneck.

### Single connection with cursor

Some databases support server-side cursors that allow writes on the same connection while iterating. MySQL's JDBC driver does not expose this. Rejected: not available.

---

## Trade-offs Accepted

| Trade-off | Accepted because |
|---|---|
| Two full table scans instead of one | Second scan is I/O-bound; cheaper than the complexity of a one-pass buffer |
| `keyToDonorId` held in memory for the full run | O(D) donors, not O(C) contributions; hundreds of thousands of entries, not tens of millions |
| Four open connections at peak (two per pass, overlapping at transition) | MySQL connection limit is not a constraint in this deployment |
