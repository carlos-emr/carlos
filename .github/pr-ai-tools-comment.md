> **AI & CI Tools Available** — Tag `@claude`, `@coderabbitai`, `@sourcery-ai`, `@cubic-dev-ai`, `@dependabot`, or `@SocketSecurity` in comments.

<details>
<summary><b>Available Commands & Automation</b></summary>

## AI Code Review & Assistance

<details>
<summary><b>Claude Code</b> — <code>@claude</code></summary>

| Command | Description |
|---------|-------------|
| `@claude <request>` | General assistance with any request |
| `@claude review this PR` | Review the pull request |
| `@claude explain <file/function>` | Explain code |
| `@claude implement <feature>` | Implement a feature |
| `@claude fix <issue>` | Fix a bug or issue |
| `@claude refactor <code>` | Suggest refactoring |
| `@claude write tests for <file>` | Generate tests |

**Works in:** PR comments, issue comments

</details>

<details>
<summary><b>CodeRabbit</b> — <code>@coderabbitai</code></summary>

| Command | Description |
|---------|-------------|
| `@coderabbitai review` | Trigger incremental review |
| `@coderabbitai full review` | Full review of all files |
| `@coderabbitai summary` | Regenerate PR summary |
| `@coderabbitai resolve` | Resolve all CodeRabbit comments |
| `@coderabbitai configuration` | Show current config |
| `@coderabbitai help` | Show all commands |
| `@coderabbitai pause` | Pause reviews on this PR |
| `@coderabbitai resume` | Resume reviews on this PR |

**File-specific** (reply to a review comment):
| Command | Description |
|---------|-------------|
| `@coderabbitai generate docstrings` | Add docstrings to file |
| `@coderabbitai generate unit tests` | Generate unit tests |
| `@coderabbitai modularize` | Suggest modularization |

</details>

<details>
<summary><b>Sourcery AI</b> — <code>@sourcery-ai</code></summary>

| Command | Description |
|---------|-------------|
| `@sourcery-ai review` | Trigger a code review |
| `@sourcery-ai summary` | Generate/regenerate PR summary |
| `@sourcery-ai title` | Generate/regenerate PR title |
| `@sourcery-ai guide` | Generate reviewer's guide |
| `@sourcery-ai resolve` | Resolve all Sourcery comments |
| `@sourcery-ai issue` | Create issue from review comment |
| `@sourcery-ai plan` | Generate plan of action (on issues) |

</details>

<details>
<summary><b>Cubic</b> — <code>@cubic-dev-ai</code></summary>

| Command | Description |
|---------|-------------|
| `@cubic-dev-ai` | Trigger a new review |
| `@cubic-dev-ai review this PR` | Explicitly request review |
| `@cubic-dev-ai <question>` | Ask about the code |
| `@cubic-dev-ai fix this issue` | Request a fix |

Reply to Cubic's comments to continue the conversation.

</details>

## Security & Dependencies

<details>
<summary><b>Socket Security</b> — <code>@SocketSecurity</code></summary>

| Command | Description |
|---------|-------------|
| `@SocketSecurity ignore pkg@ver` | Ignore a dependency alert |

Socket automatically comments on PRs with dependency security risks.

</details>

<details>
<summary><b>Dependabot</b> — <code>@dependabot</code></summary>

| Command | Description |
|---------|-------------|
| `@dependabot rebase` | Rebase the PR |
| `@dependabot recreate` | Recreate PR (overwrites edits) |
| `@dependabot merge` | Merge after CI passes |
| `@dependabot squash and merge` | Squash and merge after CI |
| `@dependabot cancel merge` | Cancel a pending merge |
| `@dependabot reopen` | Reopen a closed PR |
| `@dependabot close` | Close PR, stop updates |

**Ignore commands:**
| Command | Description |
|---------|-------------|
| `@dependabot ignore this dependency` | Stop all updates |
| `@dependabot ignore this major version` | Ignore major updates |
| `@dependabot ignore this minor version` | Ignore minor updates |
| `@dependabot unignore <dep>` | Remove ignore rules |
| `@dependabot show <dep> ignore conditions` | Show ignore rules |

</details>

## Automatic CI & Reviews

| Tool | Trigger | What It Does |
|------|---------|--------------|
| **CodeRabbit** | PR open/update | Auto code review, summary, walkthrough |
| **Sourcery AI** | PR open/update | Auto code review with suggestions |
| **Cubic** | PR open/update | Auto code review, learns team patterns |
| **Claude Code Review** | PR open/update | AI code review (GitHub Action) |
| **Dependency Review** | PR to protected branches | Scans for vulnerable dependencies |
| **Socket Security** | PR with dep changes | Alerts on dependency security risks |
| **Maven Build & Test** | PR to main/develop | Builds project, runs all tests |

## Issue Automation

<details>
<summary><b>Issue Lifecycle Keywords</b></summary>

**For issue authors to verify fixes** (comment on the issue):
| Keyword | Effect |
|---------|--------|
| `verified`, `fixed`, `works` | Auto-closes issue, adds `verified-fixed` label |
| `not fixed`, `still broken` | Adds `fix-failed` label |

**PR-to-Issue linking** (in PR title or description):
| Pattern | Effect |
|---------|--------|
| `Fixes #123` | Links PR, closes issue on merge |
| `Closes #456` | Links PR, closes issue on merge |
| `Resolves #789` | Links PR, closes issue on merge |

</details>

## IDE Tools

| Tool | Setup |
|------|-------|
| **GitHub Copilot** | IDE extension · Custom instructions: `.github/copilot-instructions.md` |

</details>
