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
2. **Comment** with a summary of what was done when finished
3. **Discovered new work?** Create a linked Jira issue — per PR-134, file a ticket for every independent task, including refactors and doc passes
4. **Do NOT transition issues** (In Progress, Done, etc.) unless the user explicitly asks — they manage ticket status themselves

## Code Style

- Prefer plain `if`/`else` with early returns over stacked elvis (`?:`) chains and scope functions (`let`, `run`, `also`) where a flat version is just as correct.
- Extract named helper functions instead of nesting lambdas (e.g. a `sequence {}` builder inside a `.use {}` block inside a `when`).
- KDoc every class and function: contract, `@param`/`@return`, and the rationale with ticket/TDS cross-references where relevant.
- Inline comments on non-obvious mechanics (encoding choices, coroutine bridging, why a timeout is disabled, etc.) — not on what the code obviously does.

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

**WORKFLOW:**

1. **File issues for remaining work** - Create Jira issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - `./gradlew test`, plus any linters
3. **Comment on in-progress tickets** summarizing what was done
4. **Hand off** - Summarize what changed and what's left, so the user can pick up cleanly

**CRITICAL RULES:**
- **Never commit or push without explicit instruction from the user** — uncommitted/unpushed work at session end is expected, not a failure
- **Never transition a Jira ticket's status** (In Progress, Done, etc.) without the user explicitly asking — they manage ticket status themselves
- If the user does ask you to commit/push/transition, do exactly the scope they asked for — not more
