# Reviewed chart-update prototype

This add-on starts from the **original selected document**, not generated summary
prose. The eChart header opens a modal containing the authorized patient document
list, with review links for active text and HTML documents. Choosing a document
continues inside that modal, one suggestion at a time with Previous/Next controls. The document summary also links to the review page. It is off by default
and is available only in the isolated synthetic trial; it has not been clinically validated.

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

- Extract up to 200 exact source passages across medical/surgical history, findings,
  social and family history, risks, medications, allergies, observations/results,
  care advice, immunizations/screening, patient details and outpatient follow-up.
  This is a bounded review inventory; the model can still omit or misclassify facts.
- Display accessible signed, unlocked, unarchived notes from all seven CPP sections,
  active ticklers, current medications, active allergies, measurements and prevention
  records. Section permissions, program/role filters and native manager access checks apply.
  The full source is visible with the selected quotation highlighted.
- Edit and approve one note or reminder at a time. The seven note destinations are
  Medical history, Ongoing concerns, Social history, Family history, Risk factors,
  Other medications and Reminders. Each write rechecks section permissions and uses
  the existing eChart editing lock, signed-note save and native document link.
- Medication and allergy candidates stay dedicated-record review items. They have
  no note-save action. Use the corresponding normal form from eChart. Embedded
  prescription **and allergy** entry are disabled because their landing pages replace
  shared Rx session patient/stash state. Safe integration needs patient-bound native
  mutation paths; opening another modal must not change an existing prescription tab.
- Prevention and demographic candidates can open their normal patient-specific forms
  in a nested modal when the clinician has read/write access. Source text remains visible.
  Those forms perform their own saves; **Done reviewing only closes the review item**
  and explicitly says it did not save a record. Close asks about unsaved form edits.
- Measurements/results are offered as note facts; no structured measurement editor
  is opened because its legacy routes depend on session-wide eChart patient state.
- Every saved note/reminder preserves its exact source quotation and document reference.
  Existing chart entries are never overwritten. No structured record is created from
  a model output or from dismissing a card.

This does not perform automatic medication reconciliation, prescribing, ordering,
ICD coding or conflict resolution. Current chart data stays local and is not sent
back to the model. Readable records are available for clinician-led comparison;
restricted records, inactive ticklers, discontinued drugs and other modules are
excluded. Demographics are checked in the native form rather than in a full local
comparison extract. Review the rest of the normal chart when needed.

Browser warnings use exact text and a small conservative English paraphrase matcher.
History comparison is restricted to the selected CPP section, and references show
that section's name. Changing the destination updates the warning. Identical family
history does not block adding the patient's own medical history. Server-side hard
blocking compares complete normalized text in the same destination; reminders must
also have the same due date and assignee. Earlier workflow annotations are stripped
only when patient, target, document and evidence match a committed approval receipt.
Receipts also prevent replaying an accepted proposal. Neither check proves clinical equivalence.

Possible duplicates show the original matching passage beside the editable draft,
with a link to the existing entry. Comparisons update as the draft changes and span
the corresponding record kind and note destination already authorized and displayed on the page.
The local matcher recognizes HTN/hypertension, OA/osteoarthritis,
COPD/chronic obstructive pulmonary disease, T2DM/type 2 diabetes mellitus,
GP/general practitioner, physio/physiotherapy and follow-up/review/recheck wording.
It normalizes written intervals from one to twelve and ignores common filler words;
all remaining terms must match, allowing reordering within a simple statement.
It compares individual sentences or explicit list items and preserves wrapped lines.
Dates, numbers, laterality, severity and other remaining qualifiers must agree.

Paraphrase matching is suppressed for recognized negation, family history,
uncertainty, conditional or completed/resolved events, unknown heading scopes,
compound statements, repeated laterality and causal statements. Identical whole entries or unblocked statements
can still match verbatim, including their qualifiers. This intentionally misses
some duplicates rather than treating a qualifier as disposable. The alias list is
small, English-only, and does not infer diagnoses from symptoms. Arbitrary clinical
paraphrases, multilingual qualifiers and complicated scope are not understood.

Warnings never delete, approve or block a suggestion. The server independently
reloads the chart and checks this complete-entry identity before saving; receipt checks prevent replaying an already accepted proposal. No chart
content leaves the browser for these comparisons and no model calls are made.

Suggestions sharing at least three significant words and 60% of the smaller
word set show an advisory comparison of the other drafts. This updates while
editing, displays text safely, and identifies completed suggestions by their
outcome. Shared wording is not clinical equivalence; nothing is removed or approved
automatically. Different qualifications in related diagnoses remain available.

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
account is in the active assignee list. Notes suggest the extracted section when available; legacy proposals use the existing Medical history/Ongoing concerns heuristic. The clinician can change the destination. Native medication/allergy fields are not guessed or automatically populated.

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
chart-proposal contract. It does not extract from a generated summary. The service
partitions the source into sections and processes bounded groups. Each group has an
initial selection and a second pass to find missed facts. Both passes retain the whole
source, numbering only the working range and nearby context to control request size.
A subsequent model pass reviews each candidate against the full source for eligibility,
source context and completed/superseded follow-up. It does not suppress repeated facts;
exact duplicates are handled by the host and overlapping passages by the clinician. Every candidate needs a valid decision
before any output is released. Even an empty initial selection receives the omission check.
The final candidate review is skipped only when both selection passes and section fallbacks are empty.

Numbered list markers remain attached to their own item. If a proposed reminder
spans an unqualified multi-item plan, the host separates independent follow-up
items before review. It omits plans with shared conditions or dependencies that
would make this separation ambiguous. This prevents a whole treatment/order list
from appearing as a single reminder; it can also omit potentially useful items.

The host also removes whitespace-equivalent duplicate selections of the same kind
and excludes recognized family-history sections from patient history. These are
bounded checks, not semantic deduplication or clinical verification; unfamiliar
headings, lost qualifications, omissions and model classification errors remain
possible. The Java evidence validator, chart comparison and per-entry approval
remain authoritative. The public limit is 200 proposals; exceeding it fails without returning a partial inventory.
If the first selected history bullet immediately follows a recognized past-history
heading, the host retains that heading in the exact quotation. This preserves the
context used by the form's Medical History destination suggestion.

Event-relative timing such as “six weeks post-surgery” is left blank when the event
date is unknown. The document date is not substituted for a surgery date.

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

## Broad extraction review

The shared single-document agent transport is reused. Selection returns source ranges
and destinations; the host copies quotations, expands plain independent lists and
adds bounded explicit history/allergy/medication/impression sections as fallback.
Qualified or nested lists stay together. Adjacent negations, conditions and explicit
source-note headings are retained; some cards therefore overlap. Conditional
discharge follow-up is not detached from its condition to create a tickler.
Oversized fallback sections are skipped,
not truncated; selected evidence must still pass the 2,000 UTF-16-unit limit.
A full-source reviewer checks context and eligible destinations. It is instructed
not to suppress paraphrased/repeated facts: exact repeats and identical heading
variants are handled by the host, and overlaps remain visible for clinician review.
This reduces lost facts but leaves some duplicate suggestions to dismiss manually.

Each review request retains the entire source. If the candidates exceed the configured
request-byte budget, they are divided into at most eight bounded batches prepared
before review starts. Every candidate requires a valid decision; a failed/missing
batch releases no partial result. This can require more than one review call.
The synthetic gateway caches exact inputs; changes to extraction or review logic
invalidate final results. Clinical coverage/accuracy is not established by passing
software tests or by the number of returned suggestions.


## Section coverage audit

The orchestration service runs at most eight selection groups (two calls per group)
and eight final review batches. Source headings, note boundaries and bounded paragraph
ranges define at most 256 sections. All sections partition the original text without
removing whitespace. Selection and review requests remain subject to the configured
byte budget and gateway deadline. Any failed request, malformed response, excess
candidate count or missing reviewer decision stops generation without partial output.

The service adds coverage metadata after all passes finish. Java validates its complete,
ordered UTF-16 partition and exact rejected quotations. The dedicated chart-update HTTP orchestration service owns
this processing audit. Generic HTTP adapters do not claim this capability. Direct Ollama completions cannot attest to multiple passes; their
coverage fields are ignored and the UI says that no audit is available. Older HTTP results
remain usable with the same unavailable notice. Model, contract and implementation changes
invalidate the synthetic gateway caches; older accepted quotations are never relabelled
as having passed the new omission check.

The modal's **Document coverage review** shows each source section, links to retained
suggestions, exact text fragments without a retained suggestion, and AI reviewer rejection
reasons. Counts and gaps are derived locally from source quotations. Repeated occurrences
of the same exact quotation link to the same suggestion but remain manual-review gaps: the
quotation does not identify which occurrence was selected. A repeat may belong to a negation,
relative or another date. This is text coverage, not a count of facts. A retained quotation may itself contain additional facts requiring attention.
Gap fragments can lose surrounding context, so the entire section is shown beside them and
they are never offered as standalone clinical assertions or approval actions. Rejection
reasons are untrusted AI explanations, displayed as escaped text.

The panel also states the remaining workflow limits: chart notes do not populate structured
measurements, results, diagnoses, procedures, referrals or orders. Medication, allergy,
prevention and demographic suggestions require the normal forms. Coverage navigation preserves
review drafts and never approves a change. Current-chart comparison remains local; it is not
sent to the selector, omission check or reviewer. This audit does not establish clinical
completeness or replace clinician review.


The [October 2 synthetic extraction results](quality/2026-10-02/section-coverage-results.json)
retain 169 suggestions from the 4,537-word, 37-note compilation and 42 from the
561-word clerking note (earlier results: 88 and 34). Their audits contain 190 and
11 source sections; the long-record reviewer rejected six candidates. All 211 retained
quotations pass CARLOS's existing Java evidence-boundary checks. These counts include
related and overlapping passages; they do not measure unique facts or clinical recall.
The results are cached for the isolated AIFACT005 trial. No clinical changes were approved.

Source-boundary restoration preserves numeric/timestamp prefixes, unpunctuated section
headings and adjacent signatures when needed. It stops at explicit patient/family section
boundaries and reapplies family routing after expansion. A restored paragraph may contain
more than one fact; its classification still needs full-source AI and clinician review.
Oversized expanded candidates are withheld intact and remain source gaps; they do not
block unrelated valid candidates. Unclear prescription/follow-up boundaries may be omitted from reminder suggestions and
remain visible as source text in the audit.
