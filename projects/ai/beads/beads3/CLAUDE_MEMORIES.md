# Shared Memories

## Beads

### Epic Parenting
When attaching tickets to an epic, use `bd update <id> --parent=<epic-id>`.
Do NOT use `bd dep add` — it fails with "tasks can only block other tasks, not epics".
The parent/child relationship and the dependency system are separate in beads.
