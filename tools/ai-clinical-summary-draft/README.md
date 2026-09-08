# CARLOS clinical summary research prototype

This self-contained draft follows issue #3455 and the discussion/evaluation work
in PRs #3504 and #3553. It does not depend on either PR being merged.

CARLOS renders either the committed **hand-authored synthetic fixture** or a
read-only extract for an explicitly selected, authorized demographic. The chart
view reproduces recorded fields and note excerpts; it is **not an AI-generated
summary**. There are no chart writes, uploads, runtime model calls, persisted
artifacts or database migrations. The separate Python runner remains synthetic-only.

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
and reopened. On mobile, the clinical summary precedes the record-context rail.
Citation links open and focus the source.
Without JavaScript, all views remain readable. An artifact with an error finding
withholds the summary, while evidence and findings remain inspectable.
Malformed reference structure fails before the JSP receives any artifact.

## Local generation

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
with non-streaming structured output. See the
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

CARLOS does not import generated artifacts automatically. To change the rendered
fixture, review a synthetic candidate, keep the fixed artifact ID/workflow, replace
`src/main/resources/clinical/summary/synthetic-overview.json`, run the tests, and
rebuild/redeploy. Keep `sample-input.json` aligned with its immutable source bundle.

`ClinicalSummaryArtifactProvider` and `ClinicalSummarySourceProvider` receive the
authenticated user plus a request containing optional demographic/encounter scope.
The action binds only one strictly validated demographic number, not encounter,
artifact paths, model names or endpoints. `ChartClinicalSummaryProvider` builds a
fresh, request-local artifact after patient authorization. The synthetic provider
still rejects chart scope and arbitrary artifact IDs. Chart data never enters the
offline runner. Connecting model inference to patient data is separate work.

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
