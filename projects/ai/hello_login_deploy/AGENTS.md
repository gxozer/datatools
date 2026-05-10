# Agent Instructions

This project uses **Jira** (via MCP) for issue tracking. Use the `mcp__jira__*` tools for all ticket management.

## Quick Reference

| Action | Tool |
|--------|------|
| Find work | `mcp__jira__searchJiraIssuesUsingJql` |
| View issue | `mcp__jira__getJiraIssue` |
| Update issue | `mcp__jira__editJiraIssue` |
| Transition issue | `mcp__jira__transitionJiraIssue` |
| Comment on issue | `mcp__jira__addCommentToJiraIssue` |
| Create issue | `mcp__jira__createJiraIssue` |

## Jira Workflow for AI Agents

1. **Find ready work**: Query JQL for unassigned/open issues in your sprint or project
2. **Transition to In Progress** when starting work
3. **Comment** with a summary of what was done when finished
4. **Transition to Done** on completion
5. **Discovered new work?** Create a linked Jira issue

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

## Landing the Plane (Session Completion)

**When ending a work session**, complete ALL steps below.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create Jira issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Transition finished work to Done, update in-progress items with comments
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
