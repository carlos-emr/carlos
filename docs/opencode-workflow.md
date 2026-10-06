# OpenCode contributor workflow

This opt-in GitHub workflow lets named contributors with current repository write
access request explanations, reviews, and changes using a configurable model API.
It changes no CARLOS application behavior or release metadata.

## Activation and release branch

The implementation lands on `release/2026.08`. **Slash commands will not run until
the workflows are promoted to the repository's default branch, currently `main`,
through the normal release process.** GitHub loads `issue_comment` workflows from
the default branch even when the comment is on a release-targeted PR. There is no
separate launcher, default-branch change, or automatic release promotion.

After promotion, issue-generated implementation PRs explicitly target
`release/2026.08`. Commands on existing same-repository PRs retain their existing
base and update their topic branch. Forks and protected branch heads are rejected.
The workflow never merges or approves PRs. New implementation PRs are drafts.

## Administrator setup

Create a dedicated GitHub App, install it only on `carlos-emr/carlos`, and grant:

- Contents: read and write
- Issues: read and write
- Pull requests: read and write
- Metadata: read (implicit)

Do not grant workflow-file write permission or add the App to protection/ruleset
bypass lists. Webhooks and user authorization are not needed. App installation
tokens allow generated PRs to trigger the existing CI. The publisher creates the
token only after authorization and revokes it in cleanup; GitHub expiry bounds
its lifetime if the runner is forcibly terminated.

Set these **repository Actions variables**:

| Variable | Value |
| --- | --- |
| `OPENCODE_ENABLED` | `false` initially; only the exact value `true` enables inference |
| `OPENCODE_ALLOWED_USERS` | JSON array, e.g. `["alice","bob"]`; default `[]` |
| `OPENCODE_API_BASE_URL` | HTTPS OpenAI-compatible chat-completions base URL |
| `OPENCODE_MODEL_ID` | Exact provider model ID; provider prefixes/slashes are preserved |
| `OPENCODE_APP_ID` | Numeric ID of the dedicated App |

Set these **repository Actions secrets**:

| Secret | Value |
| --- | --- |
| `OPENCODE_API_KEY` | Credential for the chosen model provider |
| `OPENCODE_APP_PRIVATE_KEY` | PEM private key generated for the GitHub App |

No provider is selected automatically. For DeepSeek's direct service, its current
documentation identifies `deepseek-flash` as V4.1 Flash; an example base URL is
`https://api.deepseek.com`. Another host can require a different model ID or `/v1`
suffix. Use that provider's documented base URL. The adapter is
`@ai-sdk/openai-compatible`; native Anthropic/Bedrock protocols are outside v1.
The workflow uses a conservative 65,536-token context / 8,192-token output model
configuration and at most 40 agent steps. Choose a model supporting those limits
and streaming tool calls. No automatic model substitution or reasoning-mode
override is configured. Real-provider compatibility must be smoke-tested before
general access is granted.

Sources: [DeepSeek API](https://api-docs.deepseek.com/),
[OpenCode custom providers](https://opencode.ai/docs/providers/),
[GitHub comment events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#issue_comment).

To grant access, add a complete username to `OPENCODE_ALLOWED_USERS`; the user
must also retain write-or-higher permission. Names match case-insensitively. No
owner, collaborator, or organization member is automatically exempt from the
allowlist. Custom roles qualify only if the permission response includes push
access. To revoke access, remove the username or remove repository write access.
The workflow checks live permissions again before publishing. A rerunning user
must also be allowlisted and have write access.

## Commands and results

Post a new top-level issue or PR conversation comment beginning with:

```text
/opencode explain how this code handles duplicate messages
/oc review this PR for error-handling regressions
/opencode implement the fix described in this issue
/oc fix the null check discussed above
```

`fix` aliases `implement`. An explicit verb and nonempty request are required.
Quoted commands, edits to old comments, inline review comments, schedules, and
automatic PR review are not supported. Editing or deleting an accepted command
invalidates it: post a new comment. Bots cannot invoke the workflow.

The response links to the Actions run and states whether work was denied,
disabled, failed, completed without changes, or published. Pending runs are
visible in Actions under `OpenCode #<issue> comment <comment-id>`. Requests for
the same issue/PR are serialized. The queue holds up to 100 pending runs;
overflow/cancelled runs are reported by the separate completion workflow.

Explain/review cannot edit or execute shell commands. Implement may edit files
and run available tests inside the container. The runtime has Node and standard
build tools, but does not reproduce CARLOS's Java/database/container test stack.
The response must identify tests not run. Agent-reported validation is not a CI
attestation. Existing CI, required reviews, DCO, and branch protections still apply.

Generated commits use the dedicated App's bot identity without a fabricated
human sign-off. After reviewing DCO coverage, an authorized maintainer can use
the repository's existing exact-head fallback:

```text
Confirming DCO sign off for all commits at <full-40-character-current-PR-head-SHA>
```

A subsequent push requires a fresh confirmation. If the original DCO check
failed, rerun that failed check after confirmation as described in
[the release process](release-process.md). The workflow grants no bot exemption.

## Isolation and limits

Workflow helpers come from the event's trusted default-branch SHA, separately
from the exact task source SHA. OpenCode runs as an unprivileged container user,
with a read-only root filesystem, bounded resources, no Docker socket, no GitHub
write credentials, and no App key. Project plugins/configuration and external
agent skills are disabled. Public session sharing and auto-updates are disabled.

The publisher runs on a fresh runner and applies only validated regular-file
contents through GitHub's Git Data API. It executes no generated code or hooks.
It rejects symlinks, special files, path traversal, and changes to automation
controls or Git configuration. Maximum output is 100 files / 4 MiB; maximum
discussion context is 100 KB, with pagination rather than silent truncation.
Responses longer than 44,000 characters are visibly truncated. Provider/model
settings cannot be changed through a comment.

Authorized implementation code can access the provider key and the network
inside its container. Isolation separates GitHub publication authority; it does
not make arbitrary repository code safe for a provider credential. Use a
dedicated, budget-limited provider key, and do not include patient data in
requests. Raw provider responses and reasoning are not logged or uploaded.
Request and validated result artifacts are retained for one day and are
accessible under the repository's Actions access policy. Plaintext, URL-encoded,
and base64 occurrences of the provider key are rejected before result upload;
this is not a general data-loss-prevention system.

## Failure handling and recovery

- Missing/invalid configuration, failed authorization lookups, closed requests,
  provider failures, incomplete CLI output, and invalid artifacts fail visibly.
- A changed PR head aborts publication. Post a new command; the publisher does
  not force-push or rebase over concurrent work.
- A rerun checks the deterministic output branch and commit request marker.
  If a push succeeded but PR creation/notification failed, it resumes delivery
  without another inference run or duplicate commit. A colliding branch without
  the marker is refused. Very old ambiguous rerun history is refused.
- The completion reporter handles failures, timeout, cancellation, and overflow
  using trusted run metadata only. It does not download artifacts or run PR code.
  Notification failures fail the reporter itself. Inspect Actions if a comment
  is absent; disabled Actions or GitHub outages can prevent delivery.
- Disable new execution/publication by setting `OPENCODE_ENABLED=false`.
  Cancel active runs as well to stop ongoing inference; revoking the provider
  credential stops its remaining use. Existing published PRs remain for review.

## Validation and upgrades

Pins and integrity values live in `scripts/opencode/runtime.json`. Change the CLI
version, archive checksum, and container digest through a reviewed PR. The
isolated mock API test checks the actual pinned CLI's streaming, tool calls,
read-only permissions, hostile project configuration, and authentication errors
without using a paid API. `CARLOS OpenCode Validation` runs it on relevant PRs;
policy/publication tests also run in the existing Script Regressions suite.

```sh
node --test scripts/opencode-workflow.test.js
node scripts/opencode/cli-smoke.cjs /path/to/verified/opencode
npm run test:scripts
```

Workflow linting currently needs one narrowly scoped compatibility exception:
actionlint 1.7.12 does not recognize GitHub's documented `concurrency.queue`.
Validate all remaining syntax with:

```sh
actionlint -ignore 'unexpected key "queue" for "concurrency" section' .github/workflows/opencode*.yml
```

The queue must remain `max` with `cancel-in-progress: false`; see
[GitHub's queue documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).
Remove this exception when actionlint supports it.

After promotion, verify all three workflows exist on the live default branch,
configure one permitted writer, then test an explanation, an issue implementation
targeting `release/2026.08`, a same-repository PR update, and rejection/error paths.
Confirm the App's publication triggers CI and DCO guidance. Only then broaden the
allowlist. Until these live checks pass, operational acceptance remains pending.
