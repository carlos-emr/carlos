# AI-Scribe OAuth API live check

`scripts/ai-scribe-oauth-api-live-check.js` exercises the deployed OAuth 1.0a
workflow and database-backed API calls that AI-Scribe needs. It complements
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
  remain unchanged.
- eForm template listing, create, load, update, and persisted read-after-write.
- HL7 lab message listing and malformed-upload rejection. A valid synthetic
  PATHL7 ORU upload and patient-specific read-after-upload are also tested when
  `AI_SCRIBE_DOCUMENT_STORE` points to the running server's `DOCUMENT_DIR`.
- Rx lookup search/details/instruction parsing, patient medication reads and
  writes, prescription history, and prescription listing.
- Scheduling day/status/month/provider-period/patient-history reads, including
  the endpoint used to find the patient's existing appointments.
- `insufficient_scope` refusals for demographic and eForm operations when the
  token lacks the corresponding scope.

The eForm endpoints manage templates, not completed patient eForm instances.
New patient measurements use `POST /ws/services/measurements/{demographicNo}/new`
with a measurement `type` and `dataField`, plus optional comments, instructions,
and observation timestamp. This records a separate reading and does not edit a
previous measurement, preserving CARLOS EMR's measurement immutability and audit
history.
Provider availability is not routed (the implementation is commented out),
and is reported as a capability gap. When the document-store environment
variable is omitted, the check still verifies rejection of a malformed upload
but marks successful HL7 import as unverified rather than leaving a stored lab
file behind.

## Prerequisites and execution

Run only against a **separate, disposable Ontario CARLOS EMR installation**
with synthetic data. Enable `oauth.scope.enforcement.enabled=true` for that
installation, restart it, and do not change the mode of a shared installation.
The OAuth mode setting is server-wide.

Set `BASE_URL` if the installation is not at
`http://127.0.0.1:8080/carlos`. The login defaults to the devcontainer test
account (`carlosdoc` / `carlos2026`, PIN `2026`); override with `TEST_USER`,
`TEST_PASSWORD`, and `TEST_PIN` as appropriate. Set `MYSQL_PASSWORD`; optional
database connection overrides are `MYSQL_HOST`, `MYSQL_USER`, and
`MYSQL_DATABASE`. To test successful HL7 upload, also set
`AI_SCRIBE_DOCUMENT_STORE` to the server's `DOCUMENT_DIR`, mounted where this
script can remove the uniquely named synthetic upload during cleanup. Database
writes are restricted to a loopback host by default. Do not set
`ALLOW_NON_LOCAL_MYSQL_HOST=true` except for a deliberately isolated,
disposable database.

From the repository root, run:

```sh
EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
```

For focused runs, set `AI_SCRIBE_API_ONLY` to one of:

```sh
AI_SCRIBE_API_ONLY=demographics EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=measurements EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=measurements.read EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=measurements.write EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=labs EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=rxlookup EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=rx EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=schedule EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
AI_SCRIBE_API_ONLY=eforms EXPECT_OAUTH_MODE=scoped node scripts/ai-scribe-oauth-api-live-check.js
```

`all` or an unset selector runs the full check. `measurements` runs both the
read and new-measurement write checks. The `.read` and `.write` selectors run only
that operation group; the write group also verifies that a token without
`measurement.write` is refused and reads the saved result back to verify
persistence. Every focused run still performs OAuth and creates the synthetic
patient and measurement fixture used by the API workflows, then skips the
other endpoint groups. Cleanup removes the temporary patient and its clinical
rows.

`EXPECT_OAUTH_MODE` is an assertion, not a server configuration mechanism.
The script refuses a non-scoped expectation, and the OAuth consent page must
not show a legacy-mode warning. A run prints an endpoint-by-endpoint table after
cleanup, including HTTP status, persisted read-after-write checks, scope
refusals, `UNVERIFIED` activities not executed, and explicit `GAP` rows for
unsupported operations. Rx lookup-details and medication-list failures are
recorded while unrelated workflows continue. A failure in a workflow with
dependent steps stops those steps, and the script exits nonzero if any endpoint
check failed.

The Rx lookup details check requires a DrugRef service that supports both
`get_drug_2` and `get_generic_name`; a successful search alone does not verify
that details can be loaded.

The check creates one uniquely marked synthetic demographic and BP
measurement, one synthetic medication/prescription, one eForm template, and
one temporary OAuth client with issued tokens/nonces. The API deletes the
synthetic demographic during the workflow; final cleanup verifies/removes any
remaining owned clinical rows, OAuth records, eForm template, and (when
configured) the imported lab rows and archived file. Run it serially and never
against a shared or production database.

## API capability follow-up

Measurement creation saves a new row and preserves previous readings. If
AI-Scribe needs completed eForm instances rather than reusable
templates, that workflow needs a distinct API contract and test coverage.
