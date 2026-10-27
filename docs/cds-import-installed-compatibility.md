# Installed CDS browser compatibility gates

`scripts/demographic-cds-compatibility-playwright-checks.js` verifies the W04,
W05 and W06 release gates against real application endpoints. Its existence,
unit tests and source review are not installed execution results.

The browser JSON API is contract **2**. New clients send `apiVersion=2`; live
JSON replies identify `X-CARLOS-CDS-API-Version: 2` and numeric top-level
`apiVersion: 2`. Missing, duplicate or unsupported request versions refuse
before upload admission, raw-body access, acceptance or run controls. Durable
authorized report/original downloads and read-only source pages keep their
versionless links. Existing immutable receipts are not rewritten. The CLI
capability/delegation protocol remains **1**.

Run this exclusive gate only after the installed host/container and ordinary
browser workflows have completed, the maintenance window is released, and no
patient holds remain. It deliberately performs one ordinary document upload
through the existing E-Chart popup workflow. That workflow verifies the real
multipart request, native document/link/file, redirect and opener behavior,
and removes only its owned synthetic records/files during teardown.

Report tests require an existing delivered run owned by the configured browser
account. Event, error and receipt downloads must work for that owner. A separate
native login has `_cds_import:w` and `_demographic:w`, and no administrator
privilege: its successful owner-scoped upload-list read first proves it is an
import operator. All three foreign report downloads must refuse. Anonymous and
native-logout-invalidated sessions receive no protected attachment or report
fields. A temporary basename link explicitly returns
`410 IMPORT_LEGACY_LOG_LINK_EXPIRED`. Native logout is not evidence that a
session timed out naturally; these are separate scenarios.

Mixed-version tests use the exact JavaScript ZIP member from each immutable
WAR as a scoped cached-resource override on the opposite real server's page.
The runner stream-hashes each actual WAR, checks its single bounded bundle
member, and compares the staged bundle bytes. It does not substitute a mock
server or overlay server classes. An old cached client performs its actual
versionless upload POST against the new backend and must visibly receive
`409 IMPORT_CLIENT_REFRESH_REQUIRED`. The new cached client runs on the real
archived backend, must visibly require refresh after its contract-2 read, and
an actual authenticated contract-2 upload POST must return the archived
backend's exact `400 IMPORT_PARAMETERS_INVALID` before admission. A generic
disabled-feature response does not pass that check.

The archived server uses only the new isolated `cds_compatibility_old_test`
schema, its own archived migration chain, protected synthetic configuration,
and disabled import workers/admission. It must contain no accepted jobs,
uploads, checkpoints or clinical patients. Never attach it to retained or
clinical state. Record its WAR/class/resource checksums, configuration scope,
PID/start identity and complete migration/validation proof separately.

Both servers' import/quota/native counters and bounded filesystem metadata
inventories must remain unchanged during the report and mixed-version probes.
These inventories cover counts, administrative revisions/epochs and protected
entry identities/sizes/times; they are not described as complete byte-for-byte
clinical database snapshots. The ordinary multipart fixture is created before
that comparison. Expected contract-refusal browser diagnostics remain in the
protected evidence and qualify only by exact request identity and finite
400/409 contract. Competing same-route failures stay fatal. Canceling an
unsupported old response is a refusal, not successful metadata EOF.

Required environment:

```text
CDS_COMPATIBILITY_DISPOSABLE=true
CDS_COMPATIBILITY_EXCLUSIVE=true
CDS_COMPATIBILITY_SELECTORS=/protected/compatibility-selectors.json
CDS_COMPATIBILITY_REPORT_RUN=<existing owned delivered run UUID>
CDS_COMPATIBILITY_EVIDENCE=/absent/private/evidence-directory
DOCUMENT_DIR=<actual current ordinary document store>
```

Use the normal protected browser harness login/JDBC environment. The selectors
file must be private and bounded; it contains credentials and must never be
uploaded as an artifact. Its structure is:

```json
{
  "current": {
    "bundle": {
      "warFile": "/immutable/current.war",
      "warSha256": "<actual 64-character SHA256>",
      "file": "/protected/current-cds-import.js",
      "sha256": "<actual member SHA256>"
    },
    "roots": ["/current/source", "/current/staging", "/current/uploads", "/current/documents"]
  },
  "old": {
    "baseUrl": "http://127.0.0.1:19081/carlos",
    "mysql": {"host": "127.0.0.1", "user": "<synthetic DB user>", "password": "<private>", "database": "cds_compatibility_old_test"},
    "login": {"testUser": "<synthetic native login>", "testPassword": "<private>", "testPin": "<private>"},
    "bundle": {"warFile": "/immutable/old.war", "warSha256": "<actual SHA256>", "file": "/protected/old-cds-import.js", "sha256": "<actual member SHA256>"},
    "roots": ["/old/source", "/old/staging", "/old/uploads", "/old/documents"]
  }
}
```

The evidence directory must be outside those state roots. The actual WARs must
be readable by the driver; a supplied hash label alone is insufficient. Missing
prerequisites, any unavailable proof or an unexpected refusal fail this gate.

This verifies cached-client request compatibility, not a complete load-balancer
rollout, rollback rehearsal, or retroactive fencing of an unmodified old
application/reader. Stop and drain incompatible software and external readers
before deploying new clinical representations. That operational exclusion
remains an explicit manual release prerequisite.
