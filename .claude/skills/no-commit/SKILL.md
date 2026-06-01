---
name: no-commit
description: Prevents git commits and pushes. Shows what would be committed instead.
---

# No-Commit Policy

**Never run `git commit` or `git push`.** This project enforces a strict no-commit policy — all commits and pushes must be made explicitly by the user.

## When asked to commit or push

1. Run `git status` to show what files have changed
2. Run `git diff --staged` (and `git diff` if nothing is staged) to show the full diff
3. Describe what the commit would contain and suggest a commit message
4. Stop there — do NOT run `git commit` or `git push`
5. Tell the user: type `git commit` or `git push` yourself, or say "commit" / "push" explicitly to proceed

## When you encounter this policy mid-task

If you were about to commit as part of a larger task, pause, show the diff, and wait for the user to explicitly say "commit" or "push" before proceeding.
