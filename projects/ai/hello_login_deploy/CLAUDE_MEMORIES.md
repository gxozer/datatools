# Shared Memories

## Git

Never commit or push. Show the diff and describe what would be committed, then wait for the user to explicitly say "commit" or "push".

## PR Reviews

When reviewing a PR, always post review comments directly to the PR using `gh pr review <url> --comment --body "..."`. Do NOT only report findings in the chat.

## Jira

Issue tracking is done via Jira MCP. Use the `mcp__jira__*` tools for all ticket management (create, update, transition, comment, etc.). Do NOT use beads (`bd`) commands.

## Dev Watcher

IMPORTANT: When responding to the very first user message of a new session — before answering anything else — ask: "Would you like me to start the file watcher?" (yes/no). Do this even if the first message is a task or question; ask first, then proceed.

If yes, arm a single persistent Monitor (check TaskList first — skip if one is already running) that polls `git status` every 30s, ignoring `.claude/`. On CHANGED events, orchestrate inline:

- `backend/**` changed → `make -C /Users/gozer/common/ws/datatools/projects/ai/hello_login_deploy test-backend-unit`
- `frontend/**` changed → `make -C /Users/gozer/common/ws/datatools/projects/ai/hello_login_deploy test-frontend`
- both changed or `tests/e2e/**` changed → run all three test targets including `make test-e2e-docker`
- `.github/`, `.claude/`, or other non-source files → skip tests entirely
- All tests pass → spawn a background Agent to update: README.md, docs/ folder, docstrings in changed Python files, and API docs
- Any test fails → report failures, skip docs

One monitor only. No loop, no ScheduleWakeup — handle everything inline when the monitor fires.
