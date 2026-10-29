# CDS web import and clinical source fields

Target branch: `release/2026.08`. The existing **Administration → Data Management → Demographic Import** menu and `/form/importUpload` bookmark open the durable importer.

Follow the [maintenance admission procedure](cds-maintenance-admission.md), [clinical import contract](cds-clinical-import-contract.md), and [implementation plan](cds-import-implementation-plan.md). The web and foreground command adapters share the application service, source parser, ledger and clinical writers.

Import administration requires a current active account/provider with live `_admin w` or `_cds_import w`, in addition to the applicable demographic, merge, provider and facility/program permissions. Migration `1.0.59.21` registers `_cds_import` without granting it to existing roles. An ordinary authenticated doctor cannot acquire import administration through the bookmarked route or JSON endpoints. Clinical source-field readers use their own patient/category read permissions and do not need import administration.

## Operator workflow

1. Register the actual source namespace and approved specification profile on the server. The page lists only registered namespace/profile pairs. Select the importing provider and authorized facility/program scope. Existing-patient merge starts **off**.
2. Select XML or ZIP. Each selected top-level file gets a separate upload and durable run. A patient package with attachments must be a ZIP preserving its relative names; use one ZIP for large patient batches. Nested attachment ZIPs are retained as attachment bytes, rather than recursively treated as new migration packages.
3. A dry run validates and plans without clinical writes. Its terminal `DRY_RUN_COMPLETE` receipt is distinct from clinical completion. To execute that same immutable upload, clear **Dry run**, select its upload ID and use **Submit or recover acceptance**. Execute and dry run have separate acceptance identities.
4. Read the durable run state, per-patient outcomes, category counts and diagnostics. Transfer reaching 100%, HTTP 202 acceptance, a heartbeat and a final clinical state are separate observations. The page continues polling until the server reports that the entire operation is terminal, including report delivery and cleanup.
5. Download the event log, error/diagnostic log, item reconciliation log and completion receipt. The complete item log includes every expected clinical and helper unit, its outcome and source/target digests; it distinguishes new commits, verified prior work and uncommitted work. Event rows identify the source patient/copy and reconcile all 17 source categories, including verified prior occurrences and uncommitted occurrences. Bounded web pages do not replace the complete reports.
6. Resolve versioned clinical review findings through the review flow. Corrections require the exact indexed scalar-field XPath and a clinical reason. Enter the original source value before any synthetic date shift; a correction applies to every authorized copy of that source patient. A decline affects only the identified patient copy and is unavailable when the diagnostic cannot identify it. Acknowledging a warning cannot clear a blocker. A stale review version or unsupported correction is explicitly refused. Cancellation preserves committed data; resume uses the durable source and commit receipts.
7. Complete the admission procedure before reopening ordinary chart access. Held or incomplete charts remain unavailable through clinical source-field views as well as the usual modules.

Each accepted job has a dedicated, bookmarkable status page. **Open status page** opens it outside the administration frame; **Copy status link** copies only its opaque job ID, without CSRF tokens or import options. The address follows the selected job, and the server's owned upload history lets the same operator find both import and validation jobs after signing in again or using another browser. Browser storage is only a shortcut.

Keep the browser open during file transfer. After durable acceptance, server processing continues when the page, tab or browser closes. Upload duration depends on file size and network speed and is additional to any server processing measurements; the dashboard does not invent a completion estimate.

The primary status view distinguishes source patients from destination patient charts, clinical records from preserved source-field work, new records from verified prior records, and pending work from findings needing attention. Totals remain unknown until the complete inventory is sealed. Clinical work reaching its count does not show overall completion while reports, cleanup or access release remain unfinished. A dry run explicitly states that it imported no clinical data. Source integrity checks and report preparation can continue without advancing record counts. **Status last checked** records a successful status read; **Last recorded update** is the server's recorded job update, and neither claims a worker heartbeat.

If a status read fails or the session expires, the page displays **Status unknown**, retains the exact job bookmark and clearly marked last confirmed counts, and offers a read-only status refresh. It does not submit a replacement import. Terminal report or cleanup failures ask for attention rather than suggesting that background finalization is still progressing. Detailed JSON remains available under the initially closed **Advanced job details** disclosure.

Each job response contains the job and its summary from one authorized database snapshot. The report generation, cancellation state and terminal predicates therefore belong together. Reopening a job, including the same job, supersedes older status and page requests: delayed responses, errors and polling callbacks cannot overwrite its newer confirmed state. These checks preserve the existing polling interval, request deadlines and bounded pages. The [final review's R10 decision](cds-import-final-review.md#coherent-browser-job-observations) describes the transaction and concurrency regressions.

Scheduled status polling, including the patient and diagnostic pages refreshed by that poll, carries `autoRefresh=true`. Both session inactivity filters continue to enforce authentication and expiry without treating these background reads as user activity. Manual opening, retrying, pagination and controls retain their normal activity semantics. The first authenticated request still initializes the inactivity timer. Leaving a status page open therefore does not keep a login alive indefinitely; an expired session can sign in again and return to its saved job URL while the durable import continues independently.

The shared session-heartbeat endpoint remains available for GET/HEAD during maintenance. It reports only whether this browser's existing session is valid, creates no session and queries no clinical data. This lets the standard login-expiry UI work on long-running import pages without repeated maintenance errors. Other status routes, noncanonical paths and heartbeat mutation methods remain subject to the maintenance barrier.

A cancel, resume or review acknowledgement can also be uncertain. Its warning belongs to the job on which the request was made; a late error cannot replace another job's notice. Open or refresh the original job to check its durable state. After that successful status read, **Status checked — dismiss this warning** clears the browser reminder and permits another control request. Dismissing the reminder does not confirm that the earlier request succeeded or failed. The browser sends no automatic retry and retains pending or uncertain reminders within a bounded collection; when full, it offers links to the original jobs to check them. These reminders are local hints, while the server's job and audit history remain authoritative after a reload or a different browser session.

An administrative upload-cleanup failure and a terminated import worker appear as separate visible alerts. Their safe error codes can be provided to an administrator without copying patient values into an ordinary log.

Where a diagnostic carries verified technical failure evidence, **Technical evidence** displays a bounded run/diagnostic projection after fresh operator authorization. Exception types, SQL state/vendor codes and code locations can identify the failing layer. Source values, exception messages, SQL text, filenames and physical storage paths are excluded; evidence bytes must match the protected registry's size and SHA256. This secondary evidence does not alter clinical outcomes or replace the event/error/completeness reports.

Corrections that fail complete XML validation remain inactive drafts. Their count and diagnostics remain visible; add the remaining required corrections and revalidate before resuming. An inactive or unknown draft count cannot be displayed as clean completion.

For revised raw source, options or rules, submit the corrected file normally with the same registered source identity. Review the **new** accepted run's `SOURCE_REVISION_REVIEW_REQUIRED` finding. `APPROVE_UNWRITTEN_REVISION` is available only when durable eligibility is affirmative; the backend locks and proves that both attempts have no clinical commits, holds or uncertainty before authorizing replacement. The version and reason are audited. It accepts neither an arbitrary server path nor a namespace change as a way around existing clinical data.

## Transfer and acceptance recovery

The browser hashes and sends one bounded raw `application/octet-stream` chunk at a time. This uses the existing CSRF guard without its multipart buffering path. It requires a secure browser context: approved HTTPS in operation, or a loopback origin for isolated testing.

If transfer stops before acceptance, reopen the page's owned upload list, select the upload ID and reselect exactly the same local file. Every committed prefix chunk is checked against its durable receipt before new bytes are appended. Matching filename and size alone cannot establish a safe resume. A changed chunk at an already committed offset is refused.

After acceptance, the background job survives closing the browser. Reopen its run ID/bookmark or use the owned upload history. Lost acceptance acknowledgements are recovered with the same upload ID and immutable options. A different option set cannot silently become the same accepted submission.

The upload receipt keeps `dryRunId` and actual `runId` separate. Cross-mode acceptance uses a nonblocking upload lock and reconciles both deterministic submission keys against their durable jobs, including a lost association acknowledgement. Execution waits for any accepted preview's durable snapshot; `UPLOAD_PREVIEW_SNAPSHOT_PENDING` means reopen or resume that preview before retrying the same upload. A new preview after actual acceptance is refused. Exact existing-attempt replay remains valid, and an uncertain `SUBMITTING` claim stays fenced until its exact job binding is proven.

Unsubmitted abandoned/expired payloads can be removed by bounded housekeeping. Accepted or acceptance-uncertain bytes are retained until the verified snapshot-publication protocol proves a durable replacement. Dry-run bytes remain available for later execute. Actual snapshot publication retires the redundant payload and releases its reservation once; the small chunk receipt journal remains available for retry verification.

## Clinical category views

Links in the medication, allergy, prevention, laboratory, document, appointment, demographic, measurement and case-management/history modules open the corresponding protected source-field category. Contact details remain in the demographic source category, alongside their native contact associations.

Each page contains at most 50 fields. It shows the logical XPath, XML namespace, value type, date precision, occurrence and separately encoded original/normalized values. A display preview is limited to 2,000 characters and explicitly identified when shortened. The complete original value is available through an authorized opaque field-ID download; large values are streamed with bounded buffers after range-hash verification. Physical storage paths never become download parameters or view-model fields.

The effective normalized value includes any accepted logical correction while the original value remains unchanged. A separate bounded review-history page shows the operator, timestamp, decision, request state, reason, XPath and accepted request version. A ready review request is not proof that revalidation or clinical import has completed. Correction payloads and storage paths never enter this history projection.

Access rechecks the current account/provider, requested patient, category privilege, patient-record restriction, maintenance state and import hold. Source-restricted laboratory data additionally requires the laboratory sensitivity gate. Import-operator access does not substitute for clinical patient/category read access.

Repeated conflicting addresses or telephone values, unrepresentable native scalars and incomplete calendar timing remain in their clinical source category with explicit diagnostics. Native clinical statements are never constructed from a clipped narrative. Notes above the bounded native-note projection use a visible source-view marker while retaining the complete original field.

Appointment notes retain the complete CDS 32K field in native storage and the editor. The browser retains up to 65,536 UTF-16 units so 32,768 supplementary Unicode codepoints fit; browser and server validators enforce the 32,768-codepoint business limit. Live and archive storage must both support the imported notes and status before deployment.

Date-only, partial and missing source note timestamps remain unknown in native encounter timing; the exact source precision stays in the category view. Default recent and CPP lists include undated latest notes, with stable ordering and unknown dates last for either chronological direction. Editing an existing undated note with a blank date preserves that uncertainty. New ordinary notes keep their existing current-time default. Explicit date-range reports continue to select known encounter dates within the requested range.

## MVC and memory boundaries

- Prototype actions enforce HTTP methods, reauthorize, bind a bounded command and delegate. No action chooses an arbitrary server input or report path.
- `CdsImportViewModelAssembler` and `CdsSourceFieldsViewModelAssembler` build immutable bounded presentation models. JSPs render encoded model values and perform no DAO work.
- `CdsUploadService` owns durable byte checkpoints, quota reservation and payload retirement. Body transfer occurs outside database transactions; short row-locked transactions seal completed chunks.
- `CdsImportService` owns durable job operations. Focused native demographic/history/appointment writers load bounded hash-bound DTOs prepared outside the clinical transaction. Unbounded source metadata scans do not run under a clinical unit's database locks.
- Source-field reads take a shared admission lock through authorization and bounded metadata selection. Protected payload I/O occurs after that short transaction ends.
- The browser keeps only bounded opaque run IDs in local storage, replaces bounded table pages, limits decoded status JSON to 2 MiB through a backpressured `pipeTo`/`WritableStream` consumer and aborts timed-out requests. Oversize or malformed UTF-8 cancels the source and fails visibly. It clears stale run content when switching runs and requires a fresh receipt before enabling run controls.

## Configuration and routes

| Property | Default / purpose |
| --- | --- |
| `cds.import.enabled` | `false`; explicit admission |
| `cds.import.sourceNamespaces` | Explicit server-owned namespace list |
| `cds.import.source.<namespace>.profiles` | Registered `pcon-2024-02` and/or `omd-5.2` profiles |
| `cds.import.uploadRoot` | Required private upload storage |
| `cds.import.upload.maxChunkBytes` | 4 MiB; supported range 64 KiB–16 MiB |
| `cds.import.upload.maxFileBytes` | 64 GiB |
| `cds.import.upload.maxTotalBytes` | 256 GiB reserved transfer bytes |
| `cds.import.upload.maxFiles` | 20,000 reserved uploads |
| `cds.import.minimumFreeDiskBytes` | 512 MiB floor |
| `cds.import.upload.ttlHours` | 24 hours for unsubmitted expiry |
| `cds.import.clinicalZone` | Explicit clinic/source time zone; no guessed local-time resolution |
| `cds.import.native.maxNoteBytes` | 256 KiB bounded native narrative projection; maximum 384 KiB |

Live JSON requests use the explicit browser contract `apiVersion=2`; successful JSON responses include numeric `apiVersion: 2` and `X-CARLOS-CDS-API-Version: 2`. A missing, duplicated or unsupported mutator version requires a refresh before reading a raw chunk or reserving upload quota. Durable report and original-field downloads remain separately authorized, versionless immutable files, including JSON completion receipts. The foreground CLI protocol is independent.

All mutators require authenticated CSRF-protected POST. GET/HEAD on upload, submit or control returns 405 before mutation dependencies. A stale synchronous POST to `/form/importUpload` returns 409 and requires a refresh. Old temporary-basename log URLs return an explicit expired-link response.

Read routes return bounded authorized status/patient/diagnostic/upload/chunk pages, or stream a selected durable report by run ID. Clinical source routes accept an exact patient/category and opaque field ID. They accept no caller-controlled storage path.

## Verification

The focused unit suites cover durable chunk retries and orphan tails, changed-prefix refusal, quota/retirement ordering, current patient/category authorization, hold/sensitivity failures, range-only original streaming, stale-client/method refusal, native source projection and the existing appointment editor bounds. Node tests exercise real SHA256, bounded network JSON, exact prefix verification, truthful completion presentation and complete Unicode note limits.

The menu-to-form Playwright workflows exercise real upload/acceptance, dry-run/execute separation, page reload recovery, complete protected reports, repeat acceptance/chunk requests and multi-patient ZIP replay. They require an enabled registered source, migrated disposable database, approved admission state and a working secure browser context. Their execution remains an explicit release gate; unit success does not imply browser deployment success.

Use the repository's normal protected browser credential/environment setup, including `BASE_URL`, `TEST_USER`, `TEST_PASSWORD`, `TEST_PIN` and the isolated `MYSQL_*` selectors. Never place credentials in a URL or replace the real login, MFA/PIN, CSRF or authorization checks. A browser container can share the native Tomcat network namespace so `http://127.0.0.1:8080/carlos` has loopback cryptography without publishing a public port.

```sh
node scripts/cds-import-summary.test.js
node --test scripts/cds-import-status-concurrency.test.js
node scripts/appointment-text-bounds.test.js
node scripts/demographic-cds-import-playwright-checks.js
CDS_BROWSER_CORPUS=true node scripts/demographic-cds-import-playwright-checks.js
node scripts/audit-log-demographic-import-playwright-checks.js
```

The shared browser gate retains strict console, HTTP, navigation and network checks. A passive metadata observer can classify only an exact tagged JSON request whose production bounded consumer completed fatal UTF-8 decoding, JSON parsing and stream EOF with HTTP 200/202, no redirect, abort or navigation, and independent owner/run/upload, bounded page/cursor and visible UI evidence. Clinical completion also requires native checkpoint/patient and protected report proof; a clean dry run separately proves zero native writes. Raw Chromium `net::ERR_ABORTED` events and their bounded qualification evidence remain in the audit. Unmatched, early, truncated, malformed, unsuccessful and binary-download failures remain fatal. This handles a reproduced Chromium streaming-completion bookkeeping case without changing production transport or adding URL-wide exceptions.

The manual large-ingress gate is `scripts/demographic-cds-large-ingress-playwright-checks.js`. It requires an isolated disposable database and an actual Nginx/ModSecurity proxy selected by `CDS_LARGE_INGRESS_BASE_URL` plus its private `CDS_LARGE_INGRESS_PROXY_TOKEN`. It checks 64 MiB XML through declared and unknown-length bounded chunks, the physical owned payload SHA256, journal hashes, oversized-request refusal and measured server memory without accepting a clinical job. Its manifest lists the private evidence, candidate hash and server selectors. The separate [lifecycle gate](cds-import-lifecycle-verification.md) verifies an already accepted nonzero-progress import across tab closure, native session expiry and lost acknowledgement, plus explicitly approved synthetic-course command parity. These database-wide manual gates require exclusive execution; a unit-test pass does not establish their deployed results.

The versioned browser assertions enter through the existing Administration menu and require a real durable receipt, source/category occurrence reconciliation and protected report round trips. The scripts retain incomplete held fixtures for recovery and remove only verified owned completed clinical rows. Complete import ledgers and audit evidence remain available for investigation.


## Native demographic details and recovery

New patients receive the canonical CARLOS spoken-language label for a recognized source ISO language code, exact person-status and health-card-expiry dates, and the complete fitting patient note in the existing demographic patient-notes field. A note exceeding that field's UTF-8 capacity receives an explicit visible source-view notice; its complete original and normalized values remain available under the patient's Demographics CDS source fields. Literal tag text and Unicode are preserved in native patient notes and the existing editor, schedule tooltip and chart printer.

Only ASCII case variants of a recognized language key receive that native label. Unicode lookalikes retain the original source value and the existing source-only warning. Laboratory Final/Pending labels likewise require their recognized ASCII status tokens; other source statuses remain literal and unclassified.

Current enrolment is projected only from an unambiguous latest dated transition: source status `1` becomes native `RO`, and ended enrolment `0` becomes native `TE` with its exact start and termination dates. The OntarioMD guide requires those dates and prohibits status `0` for never-enrolled patients. Missing required dates or contradictory active/termination state stop preflight. Conflicting latest transitions retain the complete structured history with an explicit source-only warning and no guessed current roster. Historical events remain structured category-local source values; the importer does not invent whole-chart historical snapshots. Source contextual family/referring physicians and preferred pharmacy use new native associations, without guessing existing professional/catalogue matches or asserting undocumented consent.

The dashboard keeps completed clinical work distinct from maintenance release and report delivery. Resume can retry an incomplete release and reports without replaying committed clinical units. A fatal review worker exposes its safe error code and permits explicit Resume of that preserved review; ordinary pending reviews and inactive correction drafts block execution. A failed supervisor requires administrator repair and service restart. A changed zero-write source revision is approved only on the new normally submitted run's identified conflict, using fresh server-side eligibility proof; the UI never treats a pending request as approval.

A web node can restart while a retained maintenance window remains closed. It boots authentication and the authorized import dashboard without creating default programs/sites, warming clinical access caches, starting configured jobs, or running the HRM startup fixer. Those tasks remain pending. After the actual database barrier is released, the next ordinary clinical request starts one asynchronous initialization attempt; clinical requests receive HTTP 503 until initialization completes. A failed attempt remains unavailable and a later request can retry after 30 seconds. This works when another node or the foreground CLI released maintenance; it does not depend on a local release notification or polling loop. Context shutdown cancels pending initialization. Default program/site rows and provider links commit in one transaction; role caches publish only a complete successfully loaded snapshot.

The retained-window browser gate is versioned in `scripts/demographic-cds-retained-recovery-playwright-checks.js`. With the normal browser-harness credentials and disposable reference database, set `CDS_RETAINED_RUN_ID` to the stopped unwritten run and run that script; its default mode performs only native login, bookmarked recovery rendering and durable reads. It accepts only the expected clinical-schedule 503 during this login; unrelated assets, scripts and network failures still fail the gate.

To exercise an explicitly coordinated replacement, set `CDS_RECOVERY_EXECUTE=true`, the original local `CDS_RECOVERY_SOURCE_FILE`, independently verified `CDS_RECOVERY_SOURCE_SHA256`, original XML `CDS_RECOVERY_SOURCE_NAME`, and the reviewed `CDS_RECOVERY_PROVIDER_NO`, `CDS_RECOVERY_PROGRAM_ID`, `CDS_RECOVERY_FACILITY_ID` and `CDS_RECOVERY_MATCH_PROVIDER_NAMES`. The gate cancels only a stopped proven-unwritten run, submits identical bytes in the same registered namespace/profile, uses the visible revision-review form and requires native completion, protected report reconciliation and actual maintenance release. If an attempt was already accepted, supply its exact `CDS_RECOVERY_RUN_ID`; the gate refuses to create another attempt. Source, accepted jobs, review history and the synthetic chart are retained for reconciliation.
