# Appointment field audit compatibility

REST status, type and urgency updates again emit their historical
`AppointmentManager.updateAppointmentStatus`, `updateAppointmentType` and
`updateAppointmentUrgency` actions with `id=N` data. The same locked manager
path still performs authorization, patient restrictions, version checks,
archival, mutation and flush. Each successful edit emits one audit record with
the existing appointment, patient and caller context. Full edits retain their
generic action/data contract, including the changed-status suffix.

The new server-selected enum cannot be supplied as an arbitrary action string.
The overload has a default implementation delegating to the original interface
method, preserving existing extension linkage and behavior. REST full edits
continue calling the original overload.

The release reproduction runs each real REST field method through the real
manager and observes its audit call: all three field audit expectations fail
before the fix (10 cases, three failures). The corrected fixture's JUnit SHA-256
is `18e7a08dfa82e765f5ecf258cc605596663c8bcef304bb9430e8cb2ddb2640f0`.
The earlier invalid authorization-fixture attempt is retained separately and
is not counted as the bug reproduction.

All 286 final Java/HTTP/SOAP controls pass with zero failures, errors or skips.
They cover the three historical audit identities, exactly one audit per edit,
named-field mutation, over-posting refusal, stale/missing/patient-denied edits,
successful versioned edits and archive-before-mutation, legacy payloads, CXF
JSON errors, the proxied write-mode contract and extension compatibility.
Existing sync, consent, CRUD and date-validation contracts remain covered.

- Final JUnit SHA-256: `7f2178a757287795ca2b187d83a1aee610cd1536b561e02613be69c4cffafe81`.
- Source-bound coverage SHA-256: `23db5597e3c927904eacaf70e8ece985862c55a88475b891b1e0e21220cff8fd`.

Both manager/interface changed-code floors are 100% for lines, instructions and
branches. The REST service's measured floors exceed its prior thresholds;
no floor is lowered. These are selected-source JVM/HTTP proofs. The next combined
DEBs will qualify these exact production classes, with the existing native
appointment audit lifecycle selected as a GUI compatibility control. That GUI
uses its normal appointment routes; no browser proof of an unused REST UI is
claimed.

### Audit lookup failure rolls back field edits

Status, type and urgency edits use `LogAction.addLogStrict` inside their existing
transaction. Failure while locating the audit DAO now propagates and rolls back
both the changed appointment and its prior-state archive. The existing generic
manager update keeps its established audit API. Three regressions read committed
appointment and archive state: all three fail before repair on both H2 and actual
MariaDB 11.8.8/32K, and all pass after repair. The MariaDB before/after JUnit hashes
are `efa00736474feb03ac7c8c6daa082534f4fec82e9a65e04e0bd030dd5653dffe`
and `ca9e083d272e9667920c2f359edef7bb49a97e46738862f8aaea06b658c7aa25`.

All 289 selected schedule controls pass with 100% changed-line, instruction and
branch coverage. Their JUnit SHA-256 is
`64f3313943d93113c81bf9ba376ef142e8fca7b6e58dab9981d52d5fc35ee625`.
The physical component runner uses an independent disposable MariaDB server;
the application schema is untouched and the server is retired. This repair is
pending the next rebuilt DEB/native qualification.

The merged repair retains both histories, both rollback regressions and the CI
MariaDB report expectations. All 307 selected JVM controls pass (JUnit
`ef8d2327f941359fd43a7c3e7c4e995444d160c9dbecd1f615cdca2ec00f5ff5`),
with 100% changed-line/instruction/branch coverage, evidence
`13ac81122154d3c5da4be3f4befb4d8e22b3b87e060d1d8bf61f3a56dfc5fa9e`.
All 21 transaction cases from both integration classes also pass on actual
MariaDB 11.8.8/32K; JUnit
`492f5cdb1d791c06ffe4c9fc852d80aac57f31ab905051fc4e5a86e84a0b5745`.
The physical test server is retired and the application schema is untouched.
