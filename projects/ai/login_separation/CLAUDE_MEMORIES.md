# Shared Memories

## Git

Never commit or push. Show the diff and describe what would be committed, then wait for the user to explicitly say "commit" or "push".

## PR Reviews

When reviewing a PR, always post review comments directly to the PR using `gh pr review <url> --comment --body "..."`. Do NOT only report findings in the chat.

## Jira

Issue tracking is done via Jira MCP. Use the `mcp__jira__*` tools for all ticket management (create, update, transition, comment, etc.). Do NOT use beads (`bd`) commands.

## Dev Watcher

The global SessionStart hook handles asking about the watcher and setting it up automatically. When the user says yes, auto-detect all test commands for this project (Makefile targets, pytest, terraform test, etc.) and run ALL of them on any source file change. Skip .git/, node_modules/, .claude/, .venv/ and non-source files. If tests pass, spawn a background Agent to update docs (README.md, docs/, docstrings, API docs).
