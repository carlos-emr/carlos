# CARLOS clinical summary research prototype

This self-contained draft follows issue #3455 and the discussion/evaluation work
in PRs #3504 and #3553. It does not depend on either PR being merged.

CARLOS renders either the committed **hand-authored synthetic fixture** or a
read-only extract for an explicitly selected, authorized demographic. The chart
view reproduces recorded fields and note excerpts; it is **not an AI-generated
summary**. Optional agent generation is limited to the three checksum-verified
NHS development fixtures. Ollama is the default; a versioned HTTP interface also
supports replaceable agents. There are no chart writes, on-disk runtime drafts or
database migrations. Validated Ollama drafts can be reused from a bounded process-memory cache. HTTP agents control their own downstream data
handling. The separate Python runner remains synthetic-only.

## CARLOS view

In a development CARLOS properties file, explicitly enable:

```properties
clinical.ai_summary_prototype.enabled=true
```

After rebuilding/deploying CARLOS, open
`<context>/clinical/AiSummaryPrototype` with an authenticated account holding
`_eChart r`. The property defaults to false (404); other HTTP methods receive 405
with `Allow: GET, HEAD`. Responses are marked `Cache-Control: no-store`.
The JSP is under `WEB-INF`; direct access is unavailable.

For a patient, open **Patient overview** in the eChart header, or use
`<context>/clinical/AiSummaryPrototype?demographicNo=<positive-integer>`.
The demographic-number field switches to another explicitly selected patient.
No demographic parameter retains the synthetic demonstration; malformed or
duplicate values return 400, missing records return 404, and denied access never
falls back to the fixture. The route and eChart link are feature-flagged off by default.

Chart reads additionally require patient-specific `_eChart r`, `_demographic r`,
`isAllowedAccessToPatientRecord`, and the existing program-domain policy. Medication
and allergy reads use their managers only with the corresponding patient-specific
read privilege. Notes require the server-side `case_program_id` and pass through
`CaseManagementManager.filterNotes` (program/role and configured facility policy).
Missing program context excludes notes rather than displaying unfiltered records.
The view audits patient access, uses `no-store` and `no-referrer`, and does not log
chart text. Source patient identity is checked before rendering.

Included scope: unarchived, nondeleted prescription records (not proof of current
use), active allergy records, and eligible signed notes from the latest 50 note
revisions. Unsigned, archived, locked, blank and unclassified-role notes are omitted.
Note excerpts retain the full source text in evidence. Labs, documents, forms,
integrated records and other chart sections are not included. Limits and inaccessible
modules are shown explicitly; empty sections are not negative clinical findings.

The view follows the compact CARLOS patient-overview mock: a patient strip,
record-context rail, and Overview, Fact ledger, Coverage and Validation tabs.
Selecting a statement opens its linked documents in Source evidence. A source
selector and previous/next controls expose every cited document. The desktop
divider supports pointer and keyboard resizing; the evidence pane can be closed
and reopened. Citation counts and IDs are not shown inline with summary prose;
select the statement to inspect its evidence. On mobile, the clinical summary precedes the record-context rail.
Citation links open and focus the source.
Without JavaScript, all views remain readable. An artifact with an error finding
withholds the summary, while evidence and findings remain inspectable.
Malformed reference structure fails before the JSP receives any artifact.

## Generate from CARLOS

For a different agent framework, see [the pluggable agent API](AGENT_API.md).
The instructions below use the default local Ollama adapter.

Install Ollama in the same environment as CARLOS, then run:

```bash
sh tools/ai-clinical-summary-draft/serve-local-model.sh
# In another terminal:
ollama pull qwen3.5:2b
```

The launcher binds numeric loopback and disables cloud features. Model downloads
are explicit operator actions; CARLOS never downloads or starts models. Enable in
the development CARLOS properties file and restart:

```properties
clinical.ai_summary_prototype.enabled=true
clinical.ai_summary_generation.enabled=true
clinical.ai_summary_generation.agent=ollama
clinical.ai_summary_generation.ollama.port=11434
clinical.ai_summary_generation.ollama.model=qwen3.5:2b
# Optional for slow CPU development machines (default 600; maximum 1800):
clinical.ai_summary_generation.ollama.timeoutSeconds=1800
```

Open a seeded NHS synthetic patient's eChart, then **Patient overview** and
**Generate AI draft**. The CSRF-protected POST displays a pending state while
waiting for local inference. Errors retain a freshly authorized chart extract.
Successful drafts show model/timestamp metadata and use the existing citation and
evidence controls. **Recorded facts** returns to the deterministic GET view.
Drafts have no database, file or session persistence. Repeated POSTs can reuse a
validated draft from the server-memory cache described below. Refreshing the POST
result may prompt the browser to resubmit.

Both flags default off. Patient permissions are checked before reading sources and
again after inference. Generation additionally requires exact fixture identity,
all original note text/date hashes, and no additional source types. Missing,
inaccessible, edited or extra notes fail closed. This does not enable inference
for arbitrary demographics or real patient records.

The Ollama adapter accepts only local Qwen 3.5 tags `0.8b`, `2b`, `4b` and `9b`. Requests use
numeric loopback with proxies and redirects disabled; cloud-backed model metadata
is rejected before source transmission. One generation runs at a time, with a
configurable read timeout (10 minutes by default, 30 maximum), 60,000-byte request
limit, 4 MiB response limit, 65,536-token
context and 4,096-token output limit. Truncated, malformed or inconsistent output
is withheld. Generated drafts also use fixed clinical sections and bounded prose;
duplicate claims or coverage reasons, fixture metadata presented as claims, and
claims without basic lexical overlap with cited evidence are withheld. The overlap
check recognizes a small fixed set of common clinical abbreviations such as HR,
BP, RR, SpO2 and HF so readable expansions are not rejected. These checks do not
establish clinical accuracy or citation support.

## Start small and compare model sizes

Start with `qwen3.5:2b`, the CARLOS default, after confirming GPU access. Use the
same synthetic charts and expected clinical facts to review completeness,
contradictions, medication details, chronology and citation support alongside
readability. The earlier 2B experiments missed facts, so fluent prose alone is
not a passing result. The historical 27B experiments do not establish 2B quality.

If 2B falls short, explicitly install `qwen3.5:4b`, then `qwen3.5:9b` as hardware
permits, update `clinical.ai_summary_generation.ollama.model`, and restart CARLOS.
These tags are already supported by the Java adapter. There is no need to begin
laptop development with 27B. Keep the prompt, sources and checks unchanged while
comparing model sizes, and check `ollama ps` for CPU offloading at each size.

Set `clinical.ai_summary_generation.cache.enabled=false` during repeated
quality/latency experiments so each trial really invokes the model; enable it
again for normal use. Changing the model tag or digest also forces a fresh result.
Record first-generation latency separately from cache-hit latency. Summary
length and validation limits are unchanged by caching or this model-size workflow.

## Validated result cache

Repeated generation for an unchanged, authorized synthetic fixture reuses the
exact validated draft, including its original generation timestamp. The default
Ollama adapter verifies that the configured model is local on every lookup and
uses its `/api/tags` content digest, Ollama version, endpoint and generation
settings as the model revision. A missing digest/version disables reuse.

The cache key includes the full authorized source snapshot (including patient
context, ledger, coverage and findings), prompt, schema and adapter revision.
Only transient snapshot-assembly IDs/timestamps and request IDs are excluded.
Source changes, deletions, access-dependent omissions, model replacements and
contract changes cause a miss. Fixture eligibility and the action's existing
pre/post authorization and source-freshness checks still run on hits. A cache
entry is never permission to read a chart. Failed or unrenderable drafts are not
cached, and a model revision change during inference prevents insertion.

The process-wide cache holds at most 16 entries and 16 MiB of serialized drafts,
expires entries 15 minutes after insertion, and evicts least-recently-used entries
when full. It does not write patient data or keys to logs, disk, the database, or
HTTP sessions; application restart/redeploy clears it. HTTP agents bypass this
cache because their current protocol provides no immutable backend revision.
In-process adapters may opt in through `ClinicalSummaryAgent.cacheIdentity()` only
when they can identify and revalidate every output-affecting backend setting.
To bypass the cache entirely, set and restart CARLOS:

```properties
clinical.ai_summary_generation.cache.enabled=false
```

This caches completed results, not extracted facts or unfinished generations.
A hit avoids model inference; first-time or changed-chart generation still incurs
its full cost. It does not change summary length, prompts, model selection or
validation thresholds. It also does not cache the Python evaluation campaigns.

## GPU access in the devcontainer

The base Compose configuration requests no GPU devices. A GPU on the host alone
therefore does not make it available to an Ollama process in this container.
Rebuilding also removes manually installed Ollama binaries and unmounted model
files. The application never installs Ollama or pulls models automatically.

For **Windows + WSL2 + GeForce RTX**, first verify the Windows NVIDIA driver,
update WSL with `wsl --update`, and enable Docker Desktop's WSL2 engine. In a
Windows terminal, `nvidia-smi --query-gpu=name,memory.total --format=csv` identifies
the exact RTX model and its VRAM. Use the Windows NVIDIA driver; do not install a
Linux NVIDIA display driver inside WSL. See
[Docker Desktop GPU prerequisites](https://docs.docker.com/desktop/features/gpu/)
and [NVIDIA's WSL driver instructions](https://docs.nvidia.com/cuda/wsl-user-guide/).
For native Linux Docker Engine, configure the NVIDIA Container Toolkit on the
host instead. These host settings cannot be changed from this devcontainer.

For an **NVIDIA** host with working GPU container support, the optional
`.devcontainer/docker-compose.nvidia.yml` reserves GPUs and persists model files.
Add it after the base file in `.devcontainer/devcontainer.json`:

```json
"dockerComposeFile": ["docker-compose.yml", "docker-compose.nvidia.yml"]
```

From the host checkout, validate the merged configuration before rebuilding:

```bash
docker compose -f .devcontainer/docker-compose.yml \
  -f .devcontainer/docker-compose.nvidia.yml config --quiet
```

Rebuild the devcontainer, install Ollama inside it if absent, and start it using
`serve-local-model.sh` above. Install the desired supported model explicitly.
While a model request is running, check inside the same container:

```bash
nvidia-smi
ollama ps
```

`ollama ps` reports whether the loaded model uses CPU, GPU, or both; an empty list
only means no model is loaded. GPU offloading depends on available VRAM, model
size and context size. The 27B evaluation model alone occupied about 17 GB in the
saved CPU experiments, before context and runtime memory. Those experiments used
`keep_alive: 0` to avoid their recorded memory failures; the CARLOS adapter uses
five minutes. Do not infer that keeping a model resident solves CPU inference cost.

The NVIDIA overlay is not for AMD or Apple GPUs. AMD requires the appropriate
ROCm/Vulkan installation and device mappings; the correct setup depends on the
host. Keep the agent's numeric-loopback restriction when configuring inference.
See [Docker GPU reservations](https://docs.docker.com/compose/how-tos/gpu-support/),
[Ollama GPU container setup](https://docs.ollama.com/docker), and
[Ollama processor diagnostics](https://docs.ollama.com/faq#how-can-i-tell-if-my-model-was-loaded-onto-the-gpu).

## Offline local generation

Python 3.10+ is sufficient; there are no Python package dependencies.
Install Ollama and a **local** Qwen 3.5 model yourself; the runner never pulls models.
For a local research session, start Ollama with cloud features disabled:

```bash
OLLAMA_NO_CLOUD=1 ollama serve
ollama pull qwen3.5:4b
python3 tools/ai-clinical-summary-draft/run.py --dry-run
python3 tools/ai-clinical-summary-draft/run.py --model qwen3.5:4b
```

Use `--port` for another local Ollama port and `--input` for another synthetic
bundle following `sample-input.json`. The synthetic flag is an assertion, not a
de-identification tool: only supply invented records. No real patient data is in scope.

The runner uses numeric loopback, disables HTTP proxies and redirects, permits
only explicit local Qwen tags, and rejects cloud-backed model metadata.
The operator must run a local Ollama server configured without remote inference.
Request/response files and validated `artifact.json` are written to ignored
`runs/<timestamp>-<random>/`. Dry-run validates input/configuration, reports the
output path, and creates no files or network connections.

Generation follows Ollama's [generate API](https://docs.ollama.com/api/generate)
with the same source-only prompt boundary, context/output limits, and strict
completion/model checks as the runtime adapter. JSON with duplicate keys is
rejected. See the
[Qwen 3.5 library](https://ollama.com/library/qwen3.5) and
[Ollama cloud controls](https://docs.ollama.com/faq#how-do-i-disable-ollamas-cloud-features).

The runner preserves the input patient context, source documents and fact ledger.
The model supplies only sections, claims and coverage. Validation findings are
generated by the runner, not accepted as model self-review.
A malformed candidate leaves request/response diagnostics but no renderable artifact.

**Validation is structural, not clinical verification.** A valid source ID can
still accompany an unsupported statement. These checks do not detect every
omission, fabricated fact, dosage error, conflict, or inappropriate recommendation.
Generated text remains unverified research output requiring source-by-source review.
The broader evaluation harness in #3553 remains separate work.

## Artifact review and chart integration

See [CONTRACT.md](CONTRACT.md). To inspect a generated artifact:

```bash
python3 tools/ai-clinical-summary-draft/validate_artifact.py tools/ai-clinical-summary-draft/runs/<run>/artifact.json
```

CARLOS does not import offline-runner artifacts automatically. To change the rendered
fixture, review a synthetic candidate, keep the fixed artifact ID/workflow, replace
`src/main/resources/clinical/summary/synthetic-overview.json`, run the tests, and
rebuild/redeploy. Keep `sample-input.json` aligned with its immutable source bundle.

`ClinicalSummaryArtifactProvider` and `ClinicalSummarySourceProvider` receive the
authenticated user plus a request containing optional demographic/encounter scope.
The action binds only one strictly validated demographic number, not encounter,
artifact paths, model names or endpoints. `ChartClinicalSummaryProvider` builds a
fresh, request-local artifact after patient authorization. The synthetic provider
still rejects chart scope and arbitrary artifact IDs. Chart data never enters the
offline runner. The runtime generator accepts only checksum-verified NHS fixtures;
real patient model integration remains separate work.

## Verification

```bash
mvn -Dtest=AiClinicalSummaryPrototype*Test,StrutsClinicalConfigTest test
python3 -m unittest discover -s tools/ai-clinical-summary-draft/tests -v
```

Tests cover method/privilege/default-disable gates, demographic validation and
patient-level denial, module omission, program note filtering, cross-patient source
rejection, source identity and references,
section membership, coverage consistency, validation states, dry-run, local-runner
success/failure, protected input fields, cloud/redirect rejection, and ignored runs.

The browser check requires the repository's Playwright dependency and a running
synthetic prototype with the committed fixture. Use an authenticated Playwright
storage-state file for a CARLOS deployment, or omit it for an isolated JSP preview:

```bash
AI_SUMMARY_URL=http://127.0.0.1:8080/carlos/clinical/AiSummaryPrototype \
AI_SUMMARY_STORAGE_STATE=/tmp/carlos-test-session.json \
node tools/ai-clinical-summary-draft/tests/browser-checks.cjs
```

It checks five viewport sizes, tabs and keyboard navigation, source selection,
statement navigation, evidence resizing and close/reopen behavior, and the
no-JavaScript fallback. Screenshots are written to a temporary directory. The
optional `AI_SUMMARY_PREVIEW_STATES=true` also exercises the `error`, `empty`, and
`xss` modes supplied by the isolated development JSP harness; these modes are not
part of the CARLOS action.

To verify chart selection in a deployed CARLOS instance, use an authenticated
storage state and at least two accessible **invented test demographics**:

```bash
AI_SUMMARY_URL=http://127.0.0.1:8080/carlos/clinical/AiSummaryPrototype \
AI_SUMMARY_STORAGE_STATE=/tmp/carlos-test-session.json \
AI_SUMMARY_DEMOGRAPHICS=1,2 \
node tools/ai-clinical-summary-draft/tests/chart-browser-checks.cjs
```

This submits the demographic selector, checks scoped identity and source evidence,
checks desktop/mobile layouts, and verifies invalid demographic rejection. It writes
screenshots to a temporary local directory; do not run it against real patient data.

For an end-to-end runtime generation check, first open the NHS fixture's eChart
with the same session so its authorized program context is established. Use only
one of the seeded NHS demographics and a running local model:

```bash
AI_SUMMARY_URL=http://127.0.0.1:8080/carlos/clinical/AiSummaryPrototype \
AI_SUMMARY_STORAGE_STATE=/tmp/carlos-test-session.json \
AI_SUMMARY_DEMOGRAPHIC=3003 \
node tools/ai-clinical-summary-draft/tests/generation-browser-checks.cjs
```

The example number is allocated locally; select the actual seeded demographic.
This check exercises method/CSRF rejection, pending state, agent generation,
unchanged evidence, citations, three viewport sizes and return to recorded facts.
Set `AI_SUMMARY_AGENT_LABEL` to the configured adapter's display name when using
an HTTP agent; by default the check expects `qwen3.5:2b via local Ollama`.
Use `AI_SUMMARY_EXPECT=error` with an unavailable configured agent to check the
failure state. It sends synthetic sources to loopback and writes only temporary
synthetic-chart screenshots. Full-fixture generation on a slow CPU can exceed
10 minutes; use the bounded development timeout above. A GPU is preferable for
interactive turnaround. No model-quality benchmark is implied by a smoke test.
