# Reviewed chart-update prototype

This add-on starts from the **original selected document**, not generated summary
prose. The eChart header opens a modal containing the authorized patient document
list, with review links for active text and HTML documents. Choosing a document
continues inside that modal, one suggestion at a time with Previous/Next controls. The document summary also links to the review page. It is off by default
and has not been deployed or clinically validated.

The review page uses CARLOS's shared grey header, white background and standard
buttons. **Back** returns to the same patient's document list after generation or
approval. Close returns to the eChart without opening another tab and restores
focus to the launch link. The existing unsaved-edit warning also applies when
leaving with Back; closing the modal asks before discarding edits. Closing is
blocked while a generation/save request is in progress. Switching suggestions
keeps their edits, and the existing form submission carries other cards' edits.

The modal contains same-origin pages using the existing authorization, CSRF and
POST/redirect flow. It does not generate automatically or change the saving
boundary. Direct standalone review links remain available, showing all cards.

## Scope

- Extract up to 20 exact source passages as optional follow-up reminders or
  diagnosis/history candidates. An empty result is valid, not evidence that no
  follow-up is required. The model can misclassify or omit relevant passages.
- Display accessible Medical history/Ongoing concerns entries and active
  ticklers beside the proposals. The full source is visible with the selected
  quotation highlighted; Previous/Next follows the current suggestion and
  Show passage in document moves focus to the source.
- Edit text and accept or dismiss **one item at a time**. Ticklers require a
  confirmed due date and active assignee. History requires Medical history or
  Ongoing concerns as the destination and is appended as a signed note under the
  clinician's account, using the existing eChart editing lock.
- Preserve the exact source passage and document reference in each saved entry,
  and create its native document link. Never overwrite existing chart entries.

This is not medication reconciliation, prescribing, ordering, ICD coding,
automatic conflict resolution, or a complete longitudinal problem-list merger.
Chart comparison is clinician-led; duplicate matching is only normalized text
containment plus durable replay protection, not semantic equivalence detection.
The comparison excludes restricted notes, other chart sections and inactive
ticklers. Review the normal chart when needed.

Matching text is flagged before approval, with links to existing entries. The
browser compares the source quotation and edited draft against the authorized
entries already on the page, ignoring case and whitespace. These advisory matches
span both history entries and reminders; matching source text can also occur in
an earlier entry's provenance. They do not establish clinical equivalence and do
not automatically dismiss a suggestion. The server independently reloads the
chart and blocks matching edited text within the same entry kind before saving;
receipt checks prevent replaying an already accepted proposal.

Highlighting and comparison run locally without model calls. Source text is built
from text nodes, including markup-like content. Repeated source passages show up
to 100 highlights; the warning lists up to 10 matching entry links to keep long,
repetitive documents usable. The original source text remains intact.

The document list checks availability through the same authorized read boundary
before navigating. Missing or unreadable documents show a modal over the list,
with the document name and a Close button. An Open original link is available
when extraction fails but the source can still be opened. When the original file
is missing and there is no stored HTML fallback, the modal explains this and hides
the link; direct viewer requests return HTTP 404. Escape
closes the modal and focus returns to the selected review link. Network/access
failures show a generic message without replacing the list. This check does not
generate proposals, expose source/chart text or change the session review.

## Suggested form values

The review form pre-fills exact source text and makes editable suggestions without
another model call. Reminders initially select the signed-in clinician if that
account is in the active assignee list. History passages explicitly describing
past history or a resolved condition suggest Medical history; other clinical
findings suggest Ongoing concerns. The clinician can change either selection.

Clear intervals such as "in four weeks" and "tomorrow" calculate from the source
document observation date, which is shown beside the suggested date. An explicit
ISO date following "on", "by", "due", "review" or "recheck" can be copied directly.
Calendar months and years use calendar arithmetic. Past dates are flagged and
retained. Missing dates, ranges, multiple intervals, conditions and timing tied to
another event stay blank. These English phrase rules are deliberately limited;
clinicians must confirm the intended date and document-date anchor.

Suggestions are created once per review. Refreshes and saves on other cards keep
clinician edits, including deliberately cleared fields. Approval stays unchecked.
The source passage, suggested date and its basis remain visible for comparison.

## Enable only in an approved test environment

Apply `database/mysql/migration/common/V1.0.33__reviewed_chart_update_receipts.sql`
through the normal schema migration process first. Its version is draft and must
be reconciled with other pending migrations before merging. Existing published
migrations are unchanged. The new receipt table stores identifiers and hashes,
not clinical prose. Target tables must be InnoDB; writes fail closed otherwise.

In the server's properties, enable all three flags:

```properties
clinical.ai_summary_generation.enabled=true
clinical.ai_document_summary.enabled=true
clinical.ai_chart_updates.enabled=true
```

The existing server-selected summary agent configuration is reused. Local Ollama
must be an approved local model. HTTP mode uses numeric loopback and the new
`/v1/chart-update-proposals` operation. The provided Python OpenRouter gateway
accepts only complete, exact documents from the committed synthetic corpus;
it rejects unknown/partial/modified clinical sources **before any network call**.
Do not relax this restriction to send real patient data to a hosted model.
No existing chart entries, patient IDs, provider IDs or write tools are sent to
the model. The document text itself can contain identifiers: this does not
de-identify a source document.

The bundled OpenRouter extractor selects numbered source ranges; the host copies
the original text, including line breaks and spelling. It uses the same reference
selection approach as the single-document summarizer, while retaining the separate
chart-proposal contract. It does not extract from a generated summary. A second
model pass reviews each candidate against the full source for eligibility, repeated
facts and completed/superseded follow-up. Every candidate needs a valid decision
before any output is released. Empty candidate lists need only one call.

The host also removes whitespace-equivalent duplicate selections of the same kind
and excludes recognized family-history sections from patient history. These are
bounded checks, not semantic deduplication or clinical verification; unfamiliar
headings, lost qualifications, omissions and model classification errors remain
possible. The Java evidence validator, chart comparison and per-entry approval
remain authoritative. The public 20-proposal limit is unchanged.

For the isolated empty-chart trial, `browser/hosted_chart_gateway.py` adds only the
exact NHSSYN005 compilation rebuilt from the verified corpus to the proposal
allow-list. Its summary route retains the original single-note restriction. Its
private result cache includes source/contract, model settings and extractor code
in the key, revalidates source and output on replay, and returns fresh request IDs.
This lets browser tests reuse measured model outputs without more paid calls.

Open the patient's eChart to establish authorized program context (and its edit
lock for history), then open a linked, active readable document and its summary.
Choose **Review chart updates**, generate proposals, inspect source and chart,
fill in missing details, and explicitly confirm each accepted item. No bulk
accept and no background writes are provided. If the document cannot be read
completely or exceeds the request budget, no partial proposals are generated.

## Safety and lifecycle

- Existing document visibility, patient, program/facility and chart/tickler read
  permissions are rechecked. Saving additionally requires the destination's write
  permission. POST mutations use the application's CSRFGuard protection.
- The session owns the patient, source, proposal kind and evidence. Forms carry
  an opaque review token and the fingerprint of the displayed comparison, not
  authority to choose arbitrary chart targets. Reviews expire after 15 minutes;
  generating another review replaces the previous one. Successful submissions
  redirect to the review page, so refresh does not repeat model calls or saves.
  With JavaScript enabled, submitting a card retains edits to the other cards in
  the bounded session review; each remaining item still requires fresh approval.
  No draft text is placed in browser storage. Without JavaScript, review and save
  one card at a time.
- Source and chart are reloaded after inference and before saving, bypassing the
  request's JPA identity cache. A stale form must be reviewed again, including an
  older browser tab after another response refreshed the session review.
- This assistant's saves are serialized per patient using a database row lock.
  Native record, source link and unique receipt are in one database transaction.
  The receipt key uses patient, source-text hash, proposal kind and exact passage,
  so repeats across sessions or identical re-imports cannot add the same proposal
  again. It intentionally does not permit editing an accepted item through replay;
  use the ordinary chart/tickler correction workflow instead.
- This does not lock every external document/chart writer. A change made through
  another workflow after the final freshness check is still a concurrency limit;
  history also requires the normal editing lock.
- Dismissal is session-only and does not create a clinical entry. A new generation
  may propose the same dismissed passage again. Page responses are `no-store` and
  audit events contain IDs, not source text.

## Verification

Run the synthetic gateway suite without credentials or network access:

```sh
cd tools/ai-clinical-summary-draft
python3 -m unittest discover -s tests -v
```

Run the Java tests from the repository root:

```sh
mvn -o -B -Dcheckstyle.skip=true '-Dtest=*ChartUpdate*UnitTest,*DocumentSummary*UnitTest,ChartUpdateTransactionIntegrationTest' test
```

Verified in this worktree: 190 Python tests and 75 focused Java tests pass. The
Java tests include native tickler and signed-history persistence, source links,
signature hash, durable receipt replay, and rollback after an injected receipt
failure. Database tests use isolated H2, with the MySQL-specific engine check
stubbed; the engine guard has separate unit coverage. Both changed JSPs are also
checked with Jasper compilation. These are not live-model clinical validation
results or a full deployment test against a production database.

### Isolated browser walkthrough

After the Maven test run above, with Node, Playwright/Chromium, a JDK and a local
Tomcat installation available:

```sh
node tools/ai-clinical-summary-draft/browser/check.js
```

The runner obtains the dependency classpath from the Maven test reports. Set
`CHART_TEST_TOMCAT` if Tomcat is not installed at `/usr/local/tomcat`, and optionally
`CHART_TEST_CHROMIUM` to use a specific Chromium executable. Playwright may be
installed locally or globally. No credentials or model API key are used.

This starts a separate Tomcat process on an ephemeral **loopback-only** port,
using an isolated webroot under `target/chart-update-browser-*`. It renders the
real JSP and runs the real action, approval service and CSRF filter. Login,
permissions, model output, database locks and persistence are synthetic test
doubles; this does not exercise the full Struts/login/database deployment.
The native persistence/rollback tests above cover the database boundary separately.
It does not start, stop, reconfigure or deploy to the installed/shared Tomcat.

Nine browser scenarios cover generation without writes, blank required fields,
escaped source/edited text, mobile overflow, dismissal, edited reminders, signed
history, preservation of other cards' edits, refresh-safe redirects, durable replay, stale-source/chart rejection, expiry, an older tab after
another tab refreshes the review, missing CSRF tokens and GET mutation rejection.
The runner terminates its own server and leaves logs plus desktop/mobile screenshots
in its reported artifact directory. Only fixed synthetic text is used.

Before live use, review the schema on the target MySQL/MariaDB version, exercise
the complete UI with synthetic documents and authorized/unauthorized users,
validate concurrent/retried saves and injected failures, and conduct clinician
review of extraction quality. Passing source-excerpt checks does not establish
clinical accuracy or completeness. No live model or patient-chart validation is
implied by mocked/unit tests.

For the real CARLOS/MariaDB walkthrough with three existing NHS synthetic charts,
see [the repeatable integration check](browser/NHS_LIVE_CHECK.md). Its fixed
proposal gateway tests the application workflow without model credentials;
those results do not measure AI extraction quality.

The [September 28 CARLOS/MariaDB results](quality/2026-09-28/chart-update-integration.md)
cover NHSSYN001–003, signed history, reminders, source links, access auditing and
replay protection. The run found and fixed the read-only transaction that rejected
access-audit inserts. All three patient walkthroughs and 75 focused Java tests
passed; the proposal source was a fixed fixture gateway, with no model inference.
