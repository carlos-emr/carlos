# PR #5598: email browser workflows on develop

Validated 2026-11-03 (America/Toronto) for
[issue #5166](https://github.com/carlos-emr/carlos/issues/5166), targeting `develop`.

## Deployment

- Fresh disposable Ubuntu 26.04.1 systemd container with Ontario demonstration
  data; HTTPS front door at `https://127.0.0.1/carlos` inside the container.
- CARLOS and DrugRef Debian packages: `2026.09.0~snapshot27`, built from source
  inside Ubuntu 26.04. The full pinned Chromium renderer was included: revision
  `1685886`, Chromium `154.0.8025.0`; both downloads passed their pinned SHA256 checks.
- Application source: develop `313face61944e4c5c85743a5fc742d7df7b4fd13`.
  This PR changes browser checks, fixture cleanup and validation notes. The
  installed `emailCompose.jsp` matches the source SHA256
  `c41f5e121604f74c3c75334f45f31aec0e5ad1034d06c88fe50943e5bb54f470`.
- DrugRef source: the pin `0d240f07ee5c32464060dd7eecd98f317b6b3a08`.
  `carlos-ctl` was built from the pin's fallback commit
  `6e93a62188bc82ab5b093ce1580561bf99dcbe36`, with version `1.2.2` stamped only
  in its temporary packaging worktree.
- All three Debian packages were installed with `apt`, including the renderer,
  MariaDB, private Tomcat, nginx and blocking ModSecurity front door.
  `carlos-ctl check` passed before and after the live email checks.
- The runbook fixtures and forced first-login password reset were exercised.
  No patient portal service was configured. The disposable installation's
  `_portal.booking_prompt` grants were restricted so unrelated master-record
  portal requests did not return 503. No portal responses or email responses
  were mocked. SMTP sinks accepted only local synthetic test traffic.

## Results

```sh
EXCLUSIVE=1 node scripts/run-playwright-suite.js \
  --only admin-email-config \
  --only eform-email-attachment-binding \
  --only eform-email-two-windows \
  --province ON --residue-audit --junit focused.xml
```

All three checks passed: 15 workflow steps, zero failures and zero skips.
The residue audit reported **no residue**. Only the allowlisted audit `log`
table grew. The absent `billingmaster` table was reported separately by the
audit and is unrelated to the selected email checks.

- `admin-email-config`: encrypted email acceptance, fixed secure notice/PDF,
  absence of composed clinical text and compose-only passphrase metadata from
  the cleartext MIME, encryption flags without stored passphrase metadata,
  failed SMTP delivery, sender/status filtering and persisted Resolve status.
- `eform-email-attachment-binding`: changing the submitted patient number cannot
  redirect the staged email or eForm; a consumed token cannot be replayed; the
  real POST-based Copy as new email control and another patient's composer each
  keep their own attachments.
- `eform-email-two-windows`: both simultaneous composers send exactly their own
  patient's eForm, with cleanup of their durable archive rows and files.

The original develop attachment-binding check was also run against the installed
application. It fails at `locator('#bodyEmail').fill(...)` with a 20-second
timeout, reproducing #5166. The updated checks pass on the same application;
no application code was replaced to obtain the passing result.

The archive cleanup validates the fixture patient, log and archive identities,
archive document metadata, file references and paths before mutating anything.
It preserves older dangling DOC routes using marks taken before sending and
requires `DOCUMENT_DIR` and `CARLOS_DISPOSABLE_VM=true`. Failed child cleanup
retains the secondary patient for recovery.

Additional verification: the source WAR build and pinned DrugRef build passed;
72 focused Java email tests passed with no failures or skips. The `carlos-ctl`
package's build gate ran 1,808 tests, with 173 skips and no failures. Eleven
archive-cleanup regression tests cover ownership changes, shared references,
unsafe filenames, symbolic links, SQL failure and the disposable-install guard.
The full Node regression suite also ran as an unprivileged user inside Ubuntu
26.04 with Node 22: **6,527 passed, zero failed, one skipped and one TODO**.
All 68 manifest regression tests passed after the validation notes were updated.

CI and PR review results are tracked on
[PR #5598](https://github.com/carlos-emr/carlos/pull/5598).
