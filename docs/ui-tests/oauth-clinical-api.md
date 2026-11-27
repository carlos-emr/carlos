# OAuth clinical API live check

`scripts/oauth-clinical-api-playwright-checks.js` exercises the deployed OAuth 1.0a
workflow and the database-backed clinical API calls an external OAuth client
(for example an AI scribe) relies on. It complements
unit and local-transport tests; those do not prove that OAuth consent, signed
requests, deployed routing, scope enforcement, and persisted writes work
together.

## What it covers

- Out-of-band OAuth request-token, browser consent, access-token exchange, and a
  signed token-identity request.
- Scoped demographic list/detail/basic/summary reads, both search forms,
  create/update/delete, and database-backed read-after-write.
- Scoped measurement read and new-measurement write using an owned synthetic BP
  measurement. Each new reading is saved separately; existing measurements
  remain unchanged. A `PUT` correction of a measurement is recorded as a `GAP`
  (HTTP 404/405, see #6136).
- eForm template listing, create, load, update, and persisted read-after-write.
- HL7 lab message listing and malformed-upload rejection. A valid synthetic
  PATHL7 ORU upload and patient-specific read-after-upload are also tested when
  `LAB_UPLOAD_DOCUMENT_STORE` points to the running server's `DOCUMENT_DIR`.
- Rx lookup search/details/instruction parsing, patient medication reads and
  writes (the discontinued drug row is checked in `drugs`), prescription
  history, and prescription listing (which must include the prescription the
  run created).
- Scheduling day/status/month/provider-period/patient-history reads, including
  the endpoint used to find the patient's existing appointments.
- `insufficient_scope` refusals for demographic update, measurement create,
  and eForm template read/update operations when the token lacks the
  corresponding scope. The limited `demographic.read measurement.read` token is
  accepted for the measurement read.

The eForm endpoints manage templates, not completed patient eForm instances.
New patient measurements use `POST /ws/services/measurements/{demographicNo}/new`
with a measurement `type` and `dataField`, plus optional comments, instructions,
and observation timestamp. This records a separate reading and does not edit a
previous measurement, preserving CARLOS EMR's measurement immutability and audit
history. `dataField` is limited to 255 characters, further limited by the
type's configured validation rule; `comments` and `measuringInstruction` are
also limited to 255.
The type must be a configured measurement type. `measuringInstruction` may be
left out only when the type is configured with one instruction; for a type such
as BP or WT, configured per site with several, send the exact configured
instruction (for example `sitting position`), or the request is refused with
`INSTRUCTION_REQUIRED`. The reading is stored under the configured spelling of
the type and instruction. `dateObserved` (epoch milliseconds) defaults to the
time of the request and is stored to the second. A `dateObserved` more than five
minutes ahead of the server clock is refused with `DATE_OBSERVED_IN_FUTURE`,
because the latest reading is shown as the patient's current one. Repeating a request with the same `dateObserved` and
values returns the reading already stored instead of saving a second one, so a
client that sends `dateObserved` can retry safely. Refusals are HTTP 400 with a
JSON body `{"code": ..., "message": ...}` (`UNKNOWN_TYPE`,
`INSTRUCTION_REQUIRED`, `AMBIGUOUS_TYPE`, `VALUE_REJECTED`, `INVALID_*`,
`DATE_OBSERVED_IN_FUTURE`, `UNSUPPORTED_FIELD`). A numeric rule accepts only plain decimals (no exponent,
hex, `NaN` or `Infinity`), and a date rule only `yyyy-MM-dd`. The endpoint
creates readings only: it does not correct or replace an existing measurement
(see #6136), and `PUT` to the measurements service is not routed.
Provider availability is not routed (the implementation is commented out),
and is reported as a capability gap. When the document-store environment
variable is omitted, the check still verifies rejection of a malformed upload
but marks successful HL7 import as unverified rather than leaving a stored lab
file behind.

## Prerequisites and execution

Run only against a **separate, disposable Ontario CARLOS EMR installation**
with synthetic data. Enable `oauth.scope.enforcement.enabled=true` for that
installation, restart it, and do not change the mode of a shared installation.
The OAuth mode setting is server-wide; see
[OAuth access mode validation](oauth-access-mode-validation.md) for the modes.
The check skips unless `CARLOS_DISPOSABLE_HOST=true` is set, the same opt-in
`scripts/run-playwright-suite.js` requires for every live run.

Set `BASE_URL` if the installation is not at
`http://127.0.0.1:8080/carlos`. A `BASE_URL` whose host is not loopback is
refused unless `ALLOW_NON_LOCAL_BASE_URL=true` is also set, and must use
`https`, because the check posts the test login to it. The login defaults to the devcontainer test
account (`carlosdoc` / `carlos2026`, PIN `2026`); override with `TEST_USER`,
`TEST_PASSWORD`, and `TEST_PIN` as appropriate. Set `MYSQL_PASSWORD`; optional
database connection overrides are `MYSQL_HOST`, `MYSQL_USER`, and
`MYSQL_DATABASE`. To test successful HL7 upload, also set
`LAB_UPLOAD_DOCUMENT_STORE` to the server's `DOCUMENT_DIR`, mounted where this
script can remove the uniquely named synthetic upload during cleanup. Database
writes are restricted to a loopback host by default. Do not set
`ALLOW_NON_LOCAL_MYSQL_HOST=true` except for a deliberately isolated,
disposable database.

From the repository root, run:

```sh
CARLOS_DISPOSABLE_HOST=true EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
# or: CARLOS_DISPOSABLE_HOST=true EXPECT_OAUTH_MODE=scoped npm run test:oauth-clinical-api-playwright
```

SIGINT or SIGTERM stops the run before its next request, then the usual
cleanup runs and the process exits 130 or 143.

For focused runs, set `OAUTH_CLINICAL_API_ONLY` to one of (with
`CARLOS_DISPOSABLE_HOST=true` also set):

```sh
OAUTH_CLINICAL_API_ONLY=demographics EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=measurements EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=measurements.read EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=measurements.write EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=labs EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=rxlookup EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=rx EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=schedule EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=eforms EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
OAUTH_CLINICAL_API_ONLY=eforms.templates EXPECT_OAUTH_MODE=scoped node scripts/oauth-clinical-api-playwright-checks.js
```

`all` or an unset selector runs the full check. `measurements` runs both the
read and new-measurement write checks. The `.read` and `.write` selectors run only
that operation group; the write group also verifies that a token without
`measurement.write` is refused and reads the saved result back to verify
persistence. `eforms` and `eforms.templates` both run the eForm template
checks. Every focused run still performs OAuth and creates the synthetic
patient and measurement fixture used by the API workflows, then skips the
other endpoint groups. Cleanup removes the temporary patient and its clinical
rows.

`EXPECT_OAUTH_MODE` is an assertion, not a server configuration mechanism.
The script refuses a non-scoped expectation, and the OAuth consent page must
not show a legacy-mode warning. A run prints an endpoint-by-endpoint table after
cleanup, including HTTP status, persisted read-after-write checks, scope
refusals, `UNVERIFIED` activities not executed, and explicit `GAP` rows for
unsupported operations. Only Rx lookup-details and the drug-list reads are
isolated: their failures are recorded while the run continues. Any other
failure stops the run, so every later group is skipped (its endpoints are
absent from the table). The script exits nonzero if any endpoint check failed.

The Rx lookup details check requires a DrugRef service that supports both
`get_drug_2` and `get_generic_name`; a successful search alone does not verify
that details can be loaded.

The check creates one uniquely marked synthetic demographic, two BP
measurements (a SQL fixture and, when the measurement-write group runs, the
API reading), one synthetic medication/prescription, one eForm template, and
one temporary OAuth client with issued tokens/nonces. When the `demographics`
group runs, the API soft-deletes the synthetic demographic (the row stays with
`patient_status` `DE`, which the check verifies). Final cleanup removes the
demographic row and verifies/removes any remaining owned clinical rows, OAuth
records, the eForm template, and (when configured) the imported lab rows, the
lab routing lock rows the run created, and the archived file. Run it serially
and never against a shared or production database.

## API capability follow-up

Measurement creation saves a new row and preserves previous readings.
