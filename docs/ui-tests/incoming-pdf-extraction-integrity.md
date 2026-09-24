# Incoming PDF page mutation integrity

Issue #3775: extraction, rotation and page deletion used predictable temporary/output names and swallowed PDF writer-close failures. An existing extracted document could be overwritten, an unrelated `T<source>` file could be consumed, and incomplete output could replace the source without an error.

Rotation and deletion now use the same source-preserving publication path, including collision-safe recycled-page outputs. Rotation no longer deletes the source before renaming its replacement.

The implementation validates selection before changing files, writes independent PDF documents to unique non-PDF staging names, and closes every writer/stream/reader before publishing either result. An atomic create-if-absent hard link publishes the completed extracted file; atomic replacement publishes the remaining source. A destination collision fails without changing either original file. If source replacement fails, the newly published extracted file is removed. Staging cleanup failures are attached to the original failure. Original POSIX permissions and timestamps are retained. Filesystems without hard-link or atomic-move support fail with the source intact; the release Ubuntu VM supports both.

The visible error uses the existing translated extraction message; logs contain sanitized exception traces, not patient document paths.

Focused Java validation: 17 tests pass, including independent PDFBox verification of exact page counts/text, both writer finalizations throwing, atomic replacement failure with rollback, destination collision, legacy temporary-name collision, and invalid whole-document selection. Five of the original six fail against the original release classes (negative control). Additional cases cover exact single/all-page rotations, deleted/recycled page contents, replacement rollback for all operations, invalid page bounds, disabled recycling, and recycled-output collisions. The expanded negative control runs against the extraction-only implementation to expose the remaining rotation/deletion failures.

Installed validation12 Playwright confirmed source mutation when an application-owned E3 output already existed. This package predates the fix. The complete live workflow is in coverage PR #3773 and its positive run on the next matched package is pending. No schema, Flyway, or dependency changes.
