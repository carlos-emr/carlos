# Lab upload coverage for issues #3774 and PR #3915

## Storage and retry contract

The lab upload entry points use `FileUploadCheck.storeIfNew`: ON legacy CML, BC PathNet, inside-lab batch, signed-feed, manual form, REST HL7, and SOAP lab import. A checksum and the parsed lab's database writes commit together. Parser rejection, routing failure, or a failed database lookup rolls them back together, leaving the content retryable. Only an existing checksum produces a duplicate outcome. Empty CML reports and empty PathNet batches are rejected instead of committing a checksum without a lab.

`storeIfNew` takes a checksum-selected lock and starts an independent READ_COMMITTED transaction with REQUIRES_NEW. Duplicate lookup occurs inside that transaction. REQUIRED parser/DAO work joins it; an ambient caller transaction is suspended and restored. The lock remains held through commit or rollback, so a waiting upload observes the completed result, even when the caller already has a transaction. The 64 lock stripes bound memory use; different checksums can collide and serialize. Coordination is within one application instance, not across servers.

The compatibility `addFile` API shares the locks but does not make its caller's later work atomic. The legacy compound demographic importer still uses that API; this PR does not claim transactional coverage for that separate importer. MD5 remains the existing duplicate-index format, not a security or authenticity check.

REST prepares its response lookup/conversion before committing, so a lookup failure cannot return an error after permanently claiming the file. Invalid input returns 400, genuine duplicates return 409, and storage failures return 500. SOAP PDF and FHIR document parsing also receives an independent transaction; rejected parsing returns the initialized failure response and rolls back document/routing rows. SOAP failures do not return raw database exception messages.

## Files and routing

PathNet writes its archive inside the storage transaction. A failed copy rolls back the lab and checksum; a later rollback removes the archive. An archive whose deletion fails remains tracked, preventing a second diagnostic copy. File ownership begins only after CREATE_NEW succeeds: a failed open must never remove an existing destination. ON CML applies the same ownership rule, and both actions preserve the validated original multipart filename.

FHIR-generated PDFs are deleted after a confirmed rollback and retained after commit or an unknown commit outcome. A failed deletion is scheduled again at JVM shutdown. PDF/FHIR inbox routing uses the non-swallowing DAO operation so a routing failure can roll back the upload. Missing patient/provider matches can use the established unmatched/unclaimed routes; database failures must propagate rather than masquerade as missing matches. Asynchronous audit entries remain outside the lab transaction.

## Java validation

The review first passed 831 selected Java tests in 35 top-level classes, including 812 tests in the lab packages. Those results precede the final REST response-lookup regression and merge of the current `release/2026.08` base; they are not a claimed final-head full-suite total.

After that merge, production and all test sources were clean-built at `3dee684719`, and all **88 affected regression tests in 16 classes passed**, with no failures, errors, or skips. This is the current focused validation total; it supersedes the earlier 792/815/817 totals. The selection covers:

- `FileUploadCheckUnitTest` and `FileUploadCheckTransactionUnitTest`: commit/rollback, an ambient caller transaction, joined rollback-only work, and concurrent commit/duplicate versus rollback/retry. The latter uses a real H2 database and Spring `DataSourceTransactionManager`, checking persisted rows. The lock test observes the queued waiter before allowing the first upload to complete.
- SOAP endpoint/regression and REST endpoint/upload tests, including response-lookup failure before commit.
- `LabArchiveOwnershipUnitTest`, `LabUploadEntryPointsUnitTest`, Inside Lab, PathNet, and CML action tests.
- `ABCDParserUnitTest`, `CMLHandlerUnitTest`, `FHIRCommunicationRequestHandlerUnitTest`, `UtilitiesUploadUnitTest`, and `SubmitLabByForm2ActionTest`.

`RecordingTransactionManager` models REQUIRED joins, rollback-only state, and REQUIRES_NEW suspend/resume. It complements the database-backed tests rather than replacing them.

Run test classes serially on a constrained VM (`-Dtest.forkCount=1 -Djunit.jupiter.execution.parallel.enabled=false`), with a resource check before each class. The initial file-copy failure fixture exposed an infinite generated stream after its one simulated exception; the release branch's corrected fixture now keeps failing subsequent reads. The corrected test passed without temporary-file growth. This explains that test's cgroup OOM, not earlier host crashes.

The original #3774 JaCoCo measurements used a different base and smaller selection; no new line-coverage percentage is claimed here.

## Installed Debian package and browser validation

On 2026-09-26, all three Debian packages (`carlos-emr`, `carlos-emr-drugref`, `carlos-emr-eform-renderer`) were built in the Ubuntu 26.04 `carlos-val` VM from `3dee684719`, then installed as local validation version `2026.08.0~alpha14+pr3915.1`. DrugRef and Chromium inputs match the branch's pinned versions. All 8,197 deployed WAR entries matched the tested build, allowing only the expected Debian build-identity stamp. No loose class overlays were used.

The source also passed all **1,014 JavaScript tests**, serially. Four Playwright workflows passed against the packaged Ontario install, with no skips:

| Workflow | Verified behavior |
|---|---|
| `application-health` | Authenticated application routes render without HTTP, authentication, or browser failures. |
| `lab-upload` | Invalid content remains retryable; HL7 stores once and routes to the synthetic patient; duplicates are refused; a wrong CML key is denied; header-only CML is rejected twice without a checksum; a valid legacy CML report stores its result and patient route once. |
| `lab-upload-rollback` | A run-specific MariaDB trigger rejects HL7 metadata after checksum/raw-message insertion; no partial lab or checksum remains; dropping the trigger allows the exact same file to succeed. Also runs the normal lab-upload assertions. |
| `document-upload` | Inbox and chart PDF uploads, patient attachment, empty-file rejection, and forwarding with visible errors, recipient selection, and persisted routing. |

`scripts/lab-upload-playwright-checks.js` is the `core` Ontario-only workflow; `scripts/lab-upload-rollback-playwright-checks.js` is the `extended` Ontario-only workflow. Set `CML_UPLOAD_KEY` to the isolated server's configured key to exercise all keyed CML cases, and `LAB_UPLOAD_DOCUMENT_STORE` to its actual `DOCUMENT_DIR` to verify archive cleanup. The rollback workflow additionally requires CREATE/DROP TRIGGER privileges in the isolated synthetic-data database. Its trigger applies only to the run's random accession and is removed in `finally` and cleanup. Both workflows remove their synthetic database rows and uniquely named archives.

```bash
npm run test:lab-upload-playwright
npm run test:lab-upload-rollback-playwright
# Or select each workflow separately through the suite runner:
node scripts/run-playwright-suite.js --only lab-upload-rollback --province ON
```

Builds and tests ran one at a time, with CPU/memory caps and host/guest resource monitoring. The first document workflow was interrupted when guest memory crossed the reserve during first-use chart initialization and reported a blank chart; it is not counted as a pass. Returning unused JVM heap with runtime MinHeapFreeRatio=20/MaxHeapFreeRatio=40 restored the reserve without restarting the application. The complete document workflow then passed. BC PathNet has Java regression coverage; its browser flow was not exercised on this Ontario installation.

## Static-analysis model regression

Semgrep's custom path-traversal rule initially flagged the signed-feed uploader even though
its saved path is constrained by `validateExistingDocumentPath`. The rule now models that
helper's enforced `DOCUMENT_DIR` boundary, matching the existing CodeQL model. On CI's
Semgrep 1.160.0, the new fixture first reproduced three false positives with the old rule;
the corrected rule passed while still detecting raw paths, canonicalization-only helpers,
and one-argument upload validation. All four custom rule schemas validated and the
originally flagged action scanned with zero findings. This scanner/fixture change does
not alter the packaged application code validated above.
