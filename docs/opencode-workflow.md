# OpenCode contributor workflow

This opt-in GitHub workflow lets named contributors with current repository write
access request explanations, reviews, and changes using a configurable model API.
It changes no CARLOS application behavior or release metadata.

## Activation and release branch

The implementation PR targets `main`, the repository's current default branch.
GitHub loads `issue_comment` workflows from that branch, so merging this PR makes
the command listener available immediately. Inference remains disabled until
`OPENCODE_ENABLED=true` and the selected model/provider credentials and user
allowlist are configured. No release promotion is required for activation.

Issue-generated implementation PRs default to
`release/2026.08`. An explicit `implement --base <branch> <request>` selects another
existing branch in this repository. The workflow resolves that branch to an exact
commit and creates a separate topic branch and draft PR; it never pushes directly
to the selected base. Commands on existing same-repository PRs retain their existing
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
| `OPENCODE_ALLOWED_USERS` | Global JSON username allowlist, e.g. `["alice","bob"]`; default `[]` |
| `OPENCODE_MODEL_ALLOWED_USERS` | JSON mapping model aliases to allowed username arrays; default `{}` denies every model |
| `OPENCODE_MODELS` | JSON mapping the four permitted aliases to model IDs, provider slots and adapters; see below |
| `OPENCODE_REVIEW_PASSES` | `2` by default; set `3` for another verification pass; other values are rejected |
| `OPENCODE_APP_ID` | Numeric ID of the dedicated App |

Create the **`opencode-publish` environment** before enabling implementation:

- Configure required reviewers: trusted maintainers or a maintainer team. The
  requester may review their own generated changes if self-review is permitted.
- Restrict deployment branches to the default branch (`main`),
  because the trusted comment workflow executes there regardless of task base.
- Disable administrator bypass of protection rules.
- Store `OPENCODE_APP_PRIVATE_KEY` **only in this environment**, not as a
  repository or organization secret accessible to other jobs.

The gate and publisher verify that required reviewers exist and fail closed if
protection is missing. The publication job waits for GitHub environment approval;
reviewers must inspect `review.txt` and `result.json` in the matching run's
`opencode-result-<attempt>` artifact before approval. The preview includes the
source commit, selected base, complete changed text files and deletion/binary
notices. Compare deleted files against the source and inspect binary changes
separately. Do not execute downloaded content. Review within **one day** or the
artifact expires and publication fails visibly. Reject a suspicious result.

This gate is required because publishing through the App starts existing CI,
including jobs using repository secrets such as `SONAR_TOKEN`. Draft status
alone does not prevent CI execution. Review the full source being published on
an existing PR as well as the generated changes. Explain/review responses do not
need this environment or approval.

Set these **Actions secrets**:

| Secret | Location and value |
| --- | --- |
| `OPENCODE_PROVIDER_1_API_KEY` | Repository secret: dedicated, budget-limited provider credential |
| `OPENCODE_PROVIDER_1_BASE_URL` | Repository secret: HTTPS chat-completions base URL, e.g. `https://openrouter.ai/api/v1` |
| `OPENCODE_PROVIDER_2_API_KEY`, `OPENCODE_PROVIDER_2_BASE_URL` | Optional second provider or credential pair |
| `OPENCODE_PROVIDER_3_API_KEY`, `OPENCODE_PROVIDER_3_BASE_URL` | Optional third provider or credential pair |
| `OPENCODE_APP_PRIVATE_KEY` | `opencode-publish` environment secret: PEM private key for the GitHub App |

Set `OPENCODE_MODELS` to the contents of
[`models.example.json`](../scripts/opencode/models.example.json) for an OpenRouter
starting point. Each alias uses a numbered provider slot, an exact model ID and
an adapter (`openrouter` or `openai-compatible`). Several aliases can share one
key/URL pair; any alias can instead select slot 2 or 3 for another provider.
The host receives only the selected slot's secrets. URLs and keys are absent
from request/result artifacts and the agent environment. The gate sees only
whether both secrets exist. Invalid URLs are rejected before inference.

| Command alias | Example OpenRouter model ID (verified 2026-10-05) |
| --- | --- |
| `deepseek41flash` (default) | `deepseek/deepseek-v4.1-flash` |
| `kimi` | `~moonshotai/kimi-latest` |
| `glm53` | `z-ai/glm-5.3` |
| `sonnet` | `anthropic/claude-sonnet-5.5` |

The example is not loaded automatically. An omitted/null alias or a missing
key/URL pair produces an explicit decline without a provider call. There is no
fallback to another model or provider slot. The Kimi latest route can change at
the provider; use a concrete ID such as `moonshotai/kimi-k3` when reproducibility
is more important than automatically following latest. Software dependency
pins remain fixed regardless of model routing.

For a direct compatible provider, set the selected slot's URL and key secrets,
and change the alias's model ID and adapter. For example, DeepSeek's direct
service documents `deepseek-flash` with base URL `https://api.deepseek.com`.
The bundled OpenRouter adapter preserves its reasoning metadata through tool
calls; the generic adapter handles other OpenAI-compatible services. Native
Anthropic/Bedrock protocols are outside this implementation; Sonnet is supported
through OpenRouter or another compatible gateway. No SDK is downloaded at run time.

Models must support streaming tool calls, a 65,536-token context and an
8,192-token output budget. Review uses up to 30 steps per pass, other modes up to
40. All passes share a 100-request gateway limit and 25-minute inference budget;
verification multiplies inference cost and divides available time across passes.
Provider defaults control reasoning effort. Real-provider compatibility must be
smoke-tested after adding secrets and before general access is granted.

Sources: [OpenRouter model catalog](https://openrouter.ai/api/v1/models),
[OpenRouter reasoning preservation](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens),
[DeepSeek API](https://api-docs.deepseek.com/),
[OpenCode custom providers](https://opencode.ai/docs/providers/),
[GitHub comment events](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#issue_comment).

To grant access, add a complete username to `OPENCODE_ALLOWED_USERS` **and** to
each permitted model's array in `OPENCODE_MODEL_ALLOWED_USERS`. The user must
also retain repository write-or-higher permission. For example, with global
allowlist `["alice","bob"]`, configure the model grants as:

```json
{
  "deepseek41flash": ["alice", "bob"],
  "kimi": ["alice", "bob"],
  "glm53": ["alice"],
  "sonnet": ["alice"]
}
```

Here Bob can use DeepSeek Flash and Kimi; Alice can use all four. A model grant
alone does not grant global workflow access or repository write permission.
Names match exactly and case-insensitively. Wildcards, roles, unknown model
aliases and malformed mappings are rejected. Missing mappings, omitted aliases
and empty arrays deny access, including to the default model. There is no
implicit owner/admin exemption and no fallback to another model when denied.
A user granted only Sonnet must explicitly use `--model sonnet`; bare `/oc review`
continues to select `deepseek41flash` and will be denied for that user.

The grant applies to explain, review and implementation, including every review
verification pass. **Both the comment author and any rerunning actor** need the
selected model's grant, global allowlist membership and live write permission.
The trusted controller rechecks authorization before inference, before response
or publication, and immediately before attaching newly generated commits.
The model grant map is not passed to the agent or stored in its artifacts.

To revoke a model, remove the username from that model's array. To revoke all
access, remove global allowlist membership or repository write permission.
Cancel queued and active runs when revoking access; do not rely on variable
edits to stop work that was already authorized. Custom repository roles qualify
only if the live permission response includes push access.

When enabled, the availability workflow posts one brief usage note on a PR
opened, reopened or marked ready for an allowlisted author with live write
permission and at least one model grant. The note lists only that author's
granted aliases and uses an explicit `--model` when the default is not granted.
It checks the **PR author's** access, not the actor performing the transition.
It ignores bots and nonmembers, deduplicates its own authentic bot
comment, and never invokes the provider. Forks get an explicit note that their
heads cannot execute commands. Disabled automation does not advertise itself.
The notifier reads PR metadata only and checks out the trusted default branch;
it never fetches or executes PR code. Comment/API failures fail its Actions job.

## Commands and results

Post a new top-level issue or PR conversation comment beginning with:

```text
/opencode explain how this code handles duplicate messages
/oc review
/oc review focus on error handling and cross-tenant access
/oc review --model kimi focus on authorization and regression risks
/oc review --model sonnet independently check the migration assumptions
/opencode implement the fix described in this issue
/oc fix the null check discussed above
/oc implement --base develop --model glm53 the fix described in this issue
/oc implement --base release/2026.08 the requested tests
```

`fix` aliases `implement`. An explicit verb is required. Review direction is
optional; the default is a correctness/security/regression review. Explain and
implement require a nonempty request. Text after the leading options is passed
unchanged as task direction, including through every review verification pass.
`--model <alias>` selects any configured alias for all modes and all review
passes. Options precede free text, may use `--option=value`, and may appear in
either order. Repeated/unknown options or aliases fail visibly. `--base` accepts
branch names with slashes and is supported only for issue implementation.
Missing branches fail visibly. PR commands preserve the existing PR base; an
explicit `--base` on a PR is rejected rather than silently ignored.
Quoted commands, edits to old comments, inline review comments, schedules, and
automatic PR review are not supported. Editing or deleting an accepted command
invalidates it: post a new comment. Bots cannot invoke the workflow.

The response links to the Actions run and states whether work was denied,
disabled, failed, completed without changes, or published. Pending runs are
visible in Actions under `OpenCode #<issue> comment <comment-id>`. Requests for
the same issue/PR are serialized. The queue holds up to 100 pending runs;
overflow/cancelled runs are reported by the separate completion workflow.

Explain/review cannot edit or execute shell commands. Implement may edit files
and run available tests inside the container. The runtime has Node, pinned
ripgrep, and standard build tools; networking and dependency downloads are
blocked. It does not reproduce CARLOS's Java/database/container test stack.
Use existing CI after publication approval for tests needing that stack. A
reviewed, digest-pinned prepared image can add offline dependencies later.
The response must identify tests not run. Agent-reported validation is not a CI
attestation. Existing CI, required reviews, DCO, and branch protections still apply.

Review runs **two fresh sessions by default**, or three when configured. The
first collects candidate defects; subsequent passes challenge each candidate,
inspect callers/guards/tests and search for missed defects. Every pass must
actually read source files. On PRs the harness checks out the exact merge-base
snapshot read-only at `/baseline`, so reviewers can compare complete source,
including files whose GitHub diff excerpts are truncated. Every pass must also
read baseline files. Custom direction is retained in each pass.

The final verifier emits structured findings with severity, trigger/consequence,
file, line range and exact source quotation. The trusted host validates each
quotation against the immutable source snapshot before publishing, rejects
invented locations, duplicate findings, symlinks and malformed reports, and
links findings to the reviewed commit. Unsupported suspicions belong under
limitations. An empty finding list is permitted. A failed pass, missing required
inspection, or evidence mismatch fails the request; the first draft is never
silently presented as verified. Only the final verified response is published.
These are fresh sessions of the selected model, not independent human reviews;
evidence validation checks locations and quotations, not semantic correctness.

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
credentials, no App key, and `--network=none`. A Unix-socket inference gateway
on the trusted host owns the provider credential. Its fixed HTTPS upstream
accepts only chat completions for the configured model; it rejects redirects,
arbitrary paths/model changes and caller credentials, limits each request to
2 MiB, and caps a run at 100 requests. The container gets a placeholder key and
can communicate only with that gateway through the mounted socket. Project plugins/configuration and external
agent skills are disabled. Public session sharing and auto-updates are disabled.

The publisher runs on a fresh runner and applies only validated regular-file
contents through GitHub's Git Data API. It executes no generated code or hooks.
It rejects symlinks, special files, path traversal, and changes to automation
controls or Git configuration. Maximum output is 100 files / 4 MiB; maximum
discussion context is 100 KB, with pagination rather than silent truncation.
Responses longer than 44,000 characters are visibly truncated. Comments can select only configured model aliases; arbitrary model IDs,
provider URLs, keys and adapters cannot be supplied in a command.

Repository source and command context are sent to the selected model provider.
The agent can spend the bounded inference allowance and submit source as model
input; the gateway is not a content filter. Use a dedicated, budget-limited
provider key, and do not include patient data or secrets in requests or source.
Raw provider responses and reasoning are not logged or uploaded. Generated code
remains untrusted: the publication review, existing PR review and CI protections
are still necessary. Container isolation and version pins reduce risk; they do
not guarantee the absence of runtime vulnerabilities or malicious upstream code.
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
version, archive checksum, ripgrep pin and container digest through a reviewed
PR. GitHub actions are pinned to full commit SHAs and Node to an exact version;
automatic dependency caching is disabled and artifact digest mismatches fail.
The isolated mock API test checks the actual pinned CLI's streaming, read/search
and implementation tools, credential separation, blocked egress, read-only
permissions, hostile project configuration, three-pass verification, both
provider adapters, reasoning metadata and authentication errors
without using a paid API. `CARLOS OpenCode Validation` runs it on relevant PRs;
policy/publication tests also run in the existing Script Regressions suite.

```sh
node --test scripts/opencode-workflow.test.js
TOOLS_DIR=/path/to/verified/tools node scripts/opencode/container-smoke.cjs
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

After merging, verify all four workflows exist on the live default branch,
configure one permitted writer and the protected environment, then test an
explanation, reviews using default and alternate models with extra direction,
missing-model/secret and denied-model declines, issue implementations targeting
`release/2026.08` and an alternate branch, a same-repository PR update,
publication approval/rejection and errors.
Confirm the App's publication triggers CI and DCO guidance. Only then broaden the
allowlist. Until these live checks pass, operational acceptance remains pending.

## Security review (2026-10-05)

The pinned standalone OpenCode 1.18.34 is newer than the patched versions in:

- [GHSA-vxw4-wv6m-9hhh](https://github.com/anomalyco/opencode/security/advisories/GHSA-vxw4-wv6m-9hhh): unauthenticated server execution, fixed in 1.0.216.
- [GHSA-c83v-7274-4vgp](https://github.com/anomalyco/opencode/security/advisories/GHSA-c83v-7274-4vgp): web-UI XSS leading to execution, fixed in 1.1.10.
- [GHSA-632h-h47v-g4x4](https://github.com/anomalyco/opencode/security/advisories/GHSA-632h-h47v-g4x4): cross-site upgrade endpoint/npm package execution, fixed in 1.18.22.

This workflow uses no exposed `opencode serve` listener, browser UI or npm-based
OpenCode installation. It verifies archive checksums before execution and mounts
tools read-only. No action, CLI, search tool or container tag floats. Hosted
runner images and GitHub's action runtime remain platform-managed dependencies.
Recheck advisories when upgrading and periodically while pinned; pinning an old
release indefinitely is not a security update strategy.
