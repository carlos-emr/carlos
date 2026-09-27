# PR #3997 patient letters and envelopes

The original #3963 port preserves uploaded letter-template filenames and handles empty letter
and envelope selections. Attribution: Liam Stanziani, Open-O commit 7ef5417eee. Review fixes
also address #4028 (complete, authorized letter batches) and #4029 (visible upload failures), plus #4030 (envelope name/address preservation).

## Review decisions

- Resolve and authorize every patient before template data access; canonicalize and deduplicate
  patient IDs. Filing letters requires patient-specific document write permission, and requested
  follow-ups require measurement write permission.
- Render every letter into exclusively created files, then merge every required PDF before
  persisting documents, the creation log and follow-ups together. A failed render, file write,
  merge or rolled-back transaction removes only the batch's owned files.
- Preserve files when the database cannot confirm whether the transaction committed, and display
  an explicit reconciliation message instead of claiming that nothing was saved. Never delete a
  potentially committed clinical PDF because a commit acknowledgement was lost.
- Preserve complete long template names without overflowing document descriptions; unique file
  names prevent repeat or concurrent batches from overwriting prior letters.
- Embed the bundled DejaVu Sans font so names retain supported Unicode characters; fit each
  complete address on one envelope, and refuse unsupported glyphs or overflow visibly.
- Reject partial envelope selections visibly. Empty selections still return the existing notice.
  Generated PDFs carry no-store headers and are buffered before the response is written.
- Validate upload metadata and JRXML before persistence. Invalid, missing or failed uploads return
  a localized error to Manage Letters, retaining the name and prevention return context.
- Replace the raced upload navigation wait with the shared navigation helper. Browser cleanup
  requires SQL and `LETTER_DOCUMENT_DIR` and verifies ownership before deleting its PDFs and rows.

## Validation

Focused unit and integration tests cover request-method/upload binding guards, safe filenames,
invalid uploads and persistence errors, patient permissions, duplicate selections, real PDF
rendering/merge, real Spring/H2 transaction rollback, lost commit acknowledgement, failed file
creation and long labels. Browser cleanup has ownership and failure regressions.

- Full Java suite: 13,392 tests, zero failures/errors, 51 existing skips. Focused action,
  upload-binding, authorization, transaction and real PDF checks also passed.
- Node suite: 1,034 tests passed; final cleanup and suite-manifest checks passed after the
  browser negative-input adjustment. Required BDD, security, encoder and i18n checks passed.
- JSP compilation (982 JSPs), WAR and Javadoc passed. Changed Java coverage: 254/279 lines
  (91.0%); failure/reconciliation branches include real transaction fault tests.
- Built all three Debian packages as `2026.08.0~alpha16~pr3997.1`, installed on the local
  Ubuntu 26.04 VM, and matched 6,670 packaged/installed classes and web files to tested output.
- Installed service health and six browser checks passed: application health, patient letters
  and envelopes, prevention recall, demographic report navigation, report-index surface audit
  (16 pages, no skips), and admin report validation.
- The letter check verifies original upload/download filenames, visible invalid-upload errors,
  complete PDF generation, exactly one document and follow-up, no follow-up on rejected input,
  partial-envelope refusal, empty-selection guards and CSRF-token handling. A synthetic patient
  proves `Łukasz Жуков` survives PDF text extraction from the installed envelope endpoint.
- The first browser run exposed front-door WAF rejection of the integer-overflow probe (403).
  The final browser check uses a malformed ID to verify the application's visible 400 response;
  integer-overflow handling remains covered by Java tests. All five neighboring checks passed
  on the first run, and the corrected letter check passed without findings.
- Ownership-scoped cleanup removed test templates, documents, logs, follow-ups, PDF files and
  the synthetic patient. The VM was stopped after validation; no schema/configuration change
  was needed. Compilation and VM execution were serialized with resource limits.


## Follow-up review

Returned letter/envelope error pages now prefer the submitted `demos` selection and retain
valid rows while omitting malformed or missing IDs. Reviewing that path exposed a shared
patient-label cache keyed only by patient ID. It could return a previous caller's patient
label without repeating authorization. The helper now resolves current access, patient data
and locale on every lookup (#4046); its public reset API remains as a compatibility no-op.

PDF delivery failure before response commitment now returns the visible input page. Letters
show the reconciliation warning because clinical records have already committed; envelopes
show their generation error. Committed responses retain their existing handling. A real
transaction regression also covers cleanup failure after commit: PDFs and committed rows
are retained, and the action must not claim nothing was saved. Binary PDF delivery advertises
application/pdf, no-store and nosniff; its narrowly documented Semgrep exception applies only
to that binary write.

Rejected upload content is caught at the upload callback, clears both bound fields, and reaches
the localized 400 path. Reads use the shared validated stream helper and an actual byte limit.
The document directory uses the existing canonical configured-directory helper. The exclusive
temporary-file operation has a method-local FindSecBugs justification: only that trusted
directory, a validated numeric template ID and a generated random suffix form the path.

Constructor/method/exception contracts are documented, test names follow BDD, guarded actions
verify no batch service interaction, and upload controls have associated labels. The batch
method separates selection validation and failed-file cleanup while preserving transaction
semantics. Browser cleanup attempts all operations, retains the primary failure and reports
additional cleanup failures; a cleanup-only failure still fails the test.

Validation:

- **13,410 Java tests**, zero failures/errors, **51 existing skips**. This includes cross-user
  label checks, invalid IDs, changed data/locale, real PDF/H2 transaction tests, post-commit
  failure, upload rejection and committed/uncommitted response-write failures.
- **1,037 Node tests** passed, including six cleanup ownership/error regressions. BDD naming
  across 278 files, encoder and security-message checks passed.
- **982 JSPs**, WAR and Javadocs built. Changed Java coverage: **297/317 lines (93.7%)**.
- All three DEBs `2026.08.0~alpha16~pr3997.2` installed on Ubuntu 26.04 with **6,670** tested
  payload and installed-file matches. The final browser harness change only delays closing
  its inspection page; application payloads are unchanged and the installed rerun passed.
- The letter/envelope workflow passed PDF/text and clinical-write assertions, upload failures,
  empty selections, and both returned error pages retaining the valid patient while excluding
  malformed IDs. Five neighboring checks passed: health, prevention recall, demographic report
  navigation, all 16 report-index surfaces and admin report validation.
- Every original row in **document, ctl_document, log_letters, report_letters, measurements
  and demographic** retained its exact fingerprint. Owned PDFs, templates, follow-ups, logs
  and synthetic patients were removed. Final health/file checks passed; zero automatic service
  restarts; VM stopped. No schema or configuration changes were required.
