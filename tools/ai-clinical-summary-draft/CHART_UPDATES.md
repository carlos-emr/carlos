# Reviewed chart-update prototype

This add-on starts from the **original selected document**, not generated summary
prose. The document summary links to a separate review page. It is off by default
and has not been deployed or clinically validated.

## Scope

- Extract up to 20 exact source passages as optional follow-up reminders or
  diagnosis/history candidates. An empty result is valid, not evidence that no
  follow-up is required. The model can misclassify or omit relevant passages.
- Display accessible Medical history/Ongoing concerns entries and active
  ticklers beside the proposals, with the full source available for comparison.
- Edit text and accept or dismiss **one item at a time**. Ticklers require an
  explicit due date and active assignee. History requires Medical history or
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

## Enable only in an approved test environment

Apply `database/mysql/migration/common/V1.0.29__reviewed_chart_update_receipts.sql`
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
  generating another review replaces the previous one.
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

Verified in this worktree: 190 Python tests and 73 focused Java tests pass. The
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

Seven browser scenarios cover generation without writes, blank required fields,
escaped source/edited text, mobile overflow, dismissal, edited reminders, signed
history, durable replay, stale-source/chart rejection, expiry, an older tab after
another tab refreshes the review, missing CSRF tokens and GET mutation rejection.
The runner terminates its own server and leaves logs plus desktop/mobile screenshots
in its reported artifact directory. Only fixed synthetic text is used.

Before live use, review the schema on the target MySQL/MariaDB version, exercise
the complete UI with synthetic documents and authorized/unauthorized users,
validate concurrent/retried saves and injected failures, and conduct clinician
review of extraction quality. Passing source-excerpt checks does not establish
clinical accuracy or completeness. No live model or patient-chart validation is
implied by mocked/unit tests.
