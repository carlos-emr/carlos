# Incoming PDF page mutation integrity

Issue #3775: extraction, rotation and page deletion used predictable temporary/output names and swallowed PDF writer-close failures. An existing extracted document could be overwritten, an unrelated `T<source>` file could be consumed, and incomplete output could replace the source without an error.

## Guarantees

All five page-edit entry points in `IncomingDocUtil` (extract, rotate one page, rotate all pages, delete page, delete document) run under the same per-document mutation lock used by filing, and under bounded synchronous PDF admission.

- **Private staging.** Every output is written to a unique `.carlos-*.tmp` scratch file in the target directory (`IncomingDocumentScratch`). Scratch names never look like queued PDFs and never collide with an existing queue file such as `Tfixture.pdf`.
- **Writers close before publication.** Every PDF writer, document, stream and reader is closed before anything is published. A close failure aborts the operation (`Could not finish PDF page extraction`), because a writer that did not finalize has not written its cross-reference table.
- **Selection is validated first.** Invalid or whole-document extraction selections and page numbers outside the document are rejected with `IllegalArgumentException` before any file changes.
- **Extraction never replaces an existing output.** The extract's destination name is reserved under a lease and published with an atomic create-if-absent hard link. If a document with that name is already queued, the user is told to file or delete it first and neither file changes.
- **Recycled pages never replace an earlier recycled copy.** With `INCOMINGDOCUMENT_RECYCLEBIN` on, a deleted page is filed under `<name>d<page>of<count>.pdf`, or the next free `-2`, `-3` ... suffix. With it off, the recycle directory is neither created nor required.
- **The source is replaced in one move.** The remaining pages replace the source with a single `REPLACE_EXISTING` move, never delete-then-rename. If that move fails, the source is intact and any newly published extract or recycled page is removed.
- **Access mode and timestamp.** The source's POSIX permissions are applied to the scratch copy before publication (the original is never chmodded). The source's modification time is carried over as a best-effort step that logs a warning, not an error, when the filesystem refuses it.
- **Rotation is normalized.** Rotations are stored as `Math.floorMod(current + degrees, 360)`, so rotating an upright page by -90 stores 270.
- **Extraction writes to the right document.** Selected pages are imported through the extract's own `PdfCopy` writer, not the remaining-pages writer.

Extraction failures are logged as sanitized traces (`LogSafe.exceptionTrace`) and shown to the user as the translated `dms.incomingDocs.cannotExtractPage` message, so filesystem exception text never reaches the browser. The one exception is an occupied extract name, whose message tells the user which queued file to file or delete.

## Focused tests

- `src/test/java/io/github/carlos_emr/carlos/documentManager/IncomingDocExtractionIntegrityUnitTest.java` checks page counts and text independently with PDFBox. It covers exact extraction, writer-finalization failure, source-replacement failure with rollback for every operation, extraction-destination collision, recycled-page collision, legacy `T<source>` collision, whole-document and out-of-range selections, exact single-page and all-page rotations, and disabled recycling.
- `IncomingDocUtilExtractionSafetyUnitTest`, `IncomingDocumentPublicationUnitTest` and `IncomingDocumentMutationLockUnitTest` cover access-mode preservation, publication and locking.

Browser coverage for the extract, rotate and delete workflow is in the release Playwright suite (see #3781). No schema, Flyway or dependency changes.
