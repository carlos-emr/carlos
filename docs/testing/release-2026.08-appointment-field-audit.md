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
