# Installed browser checks

Use `run-playwright-suite.js` and `playwright-suite.json` to select named checks.
Run installed checks serially against a disposable deployment with the documented
private browser/database credentials and fixture prerequisites.

## Stored document policy coverage

`stored-document-mutations` has two explicit modes:

- `STORED_DOCUMENT_EXPECT_CONTENT_UPDATES=false` (default) requires hidden controls
  for the owned patient-assigned document. It sends all four operations with valid
  CSRF and current source revisions and requires JSON403, `accepted:false`,
  `retryable:false`, and unchanged document bytes/rows/routes. Its coverage output
  identifies **assigned-policy-denial**; this does not prove successful mutations.
- `STORED_DOCUMENT_EXPECT_CONTENT_UPDATES=true` requires the actual application
  `ALLOW_UPDATE_DOCUMENT_CONTENT` property enabled and controls visible. It tests
  real rotations, removal, split, independent-session revision conflicts and
  capacity recovery. Its output identifies **enabled-policy** coverage.

The environment flag declares the expected policy; it never changes application
configuration. A mismatch fails. Full release coverage needs both modes, with the
enabled mode in a separately controlled installation phase and exact restoration
of the original configuration. Preserve each deployment's test identity.

The check owns its synthetic patient, admission, document and routes. Only an
actual dispatched write marks a mutation pending. A lost or unconfirmed write
retains the fixture and its private recovery journal; a failure before dispatch
does not imply an uncertain write. Never clear unrelated signatures, change shared
preferences or delete unowned documents to satisfy these checks.
