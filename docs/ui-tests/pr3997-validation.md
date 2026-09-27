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

The final Java, Node, JSP, DEB and installed browser results will be recorded after completion.
