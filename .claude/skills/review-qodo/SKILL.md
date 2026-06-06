---
name: review-qodo
description: Review all Qodo findings on a PR — both inline threads AND the full review body summary. Use when asked to review Qodo issues, fix Qodo findings, or address Qodo comments on a PR.
---

# Qodo PR Review

Qodo reports findings in two places. **You must read both or you will miss items.**

## Step 1 — Get all Qodo findings

Run these two commands in parallel:

```bash
# 1. Inline line-level threads (items with dedicated code annotations)
gh api repos/OWNER/REPO/pulls/NUMBER/comments \
  | python3 -c "
import sys, json
for c in json.load(sys.stdin):
    if 'qodo' in c['user']['login']:
        print(c['id'], c['path'] + ':' + str(c.get('line','?')))
        print(c['body'][:300])
        print()
"

# 2. Full review body (contains ALL items including Review Recommended)
gh api repos/OWNER/REPO/pulls/NUMBER/reviews \
  | python3 -c "
import sys, json
for r in json.load(sys.stdin):
    if 'qodo' in r['user']['login']:
        print(r['body'])
"
```

The review body contains the full numbered list of findings split into:
- **Action Required** — must be fixed before merge
- **Review Recommended** — lower priority but should be addressed

**Do not stop after reading inline comments.** The review body is the source of truth for the complete list.

## Step 2 — Build the full issue list

Parse both sources and deduplicate. Each item should have:
- Number and title
- Category (Action Required / Review Recommended)
- File and line (if available)
- Description and suggested fix

## Step 3 — Address all items

For each item:
1. Read the relevant code
2. Determine if it's a real bug, a deliberate design choice, or already fixed
3. Fix real bugs; document deliberate choices with a PR comment explaining the rationale

## Step 4 — Reply to each inline thread

For every item that has an inline thread, post a reply explaining what was done:
```bash
gh api repos/OWNER/REPO/pulls/comments/COMMENT_ID/replies \
  -X POST -f body="Fixed. <explanation>"
```

## Step 5 — Resolve inline threads

After replying, resolve each thread via GraphQL:
```bash
gh api graphql -f query='mutation { resolveReviewThread(input: {threadId: "THREAD_ID"}) { thread { isResolved } } }'
```

Get thread IDs first:
```bash
gh api graphql -f query='{
  repository(owner: "OWNER", name: "REPO") {
    pullRequest(number: NUMBER) {
      reviewThreads(first: 20) {
        nodes { id isResolved comments(first:1) { nodes { author { login } } } }
      }
    }
  }
}' --jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved==false and (.comments.nodes[0].author.login | contains("qodo"))) | .id'
```

## Step 6 — Note on Qodo summary updates

Qodo's summary comment (the numbered list) only updates when a **new commit is pushed** to the PR branch. Resolving threads via the API does NOT update the summary. After fixes are committed and pushed, Qodo will re-run and mark resolved items as struck through.
