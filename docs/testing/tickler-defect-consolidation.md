# Tickler defect consolidation

Target: `release/2026.08`, initially reviewed at `9004536d`.
This is the acceptance registry for consolidated PR #6101, retaining the Delete work from #5687. Historical validation
in that PR does not establish validation of the expanded candidate.

| Ticket | Implementation | Required installed acceptance |
| --- | --- | --- |
| #5683 | Existing patient/main list confirmation and cancellation checks retained | Both lists: Cancel sends no write; OK updates only selected tasks, preserves history |
| #5824 | Carry draft in POST search forms, including repeated search/paging; no storage | Attach/change/cancel with exact text, independent composers, authorized patients |
| #5825 | Empty recipient plus guidance for missing/inactive MRP; active recipient checked before save | Single/multisite, two active MRPs, missing/inactive/unavailable, explicit/manual override, exact recipients |
| #5899 | Dedicated suggested-text action; reject cross-operation dispatch; map malformed-list failure | Both routes, malformed inputs, non-POST, permissions, normal suggested-text editing |
| #6037 | Strict supported 12/24-hour parser; retain invalid text; translated guidance | Picker, blur, Enter, outside press, save, impossible input and DST refusal |
| #6041 | Shared local-calendar month clamp in Add/Edit | Month ends, leap years, combined/subtracted offsets, Clear/manual edits; saved dates |
| #6043 | SecurityException propagation and explicit permission acknowledgement | Read-only mutation refused with 403, no clinical writes, draft retained, no ERROR stack |
| #5812 | Existing MRP coverage retained; final save no longer refills supplied draft; submitted/stored text compared | Recipient usability, exact task identity/creator/patient, unchanged controls, cleanup |
| #6073 | Shared aggregate findings helper | One matching failure can match a pin; two failures or cleanup failure must fail |

## Verification commands

- `mvn package -Dtest='*Tickler*Test,MutatorActionGetRejectionContractUnitTest'`
- `npm run test:scripts` after `npm ci` against the committed lock.
- `node scripts/tickler-controls-browser-probe.js` uses real Chromium and the shipped
  Flatpickr/controller; override `PLAYWRIGHT_EXECUTABLE_PATH` if needed. This is a
  component probe, not evidence of an installed application run.
- `scripts/check-i18n-properties.sh` and encoder/security/BDD/Struts lints.
- Build the candidate WAR/Debian packages and install on disposable Ubuntu 26.04.
  Run native HTTPS checks in ON and BC, plus supported multisite configuration.
  Required scenarios cannot be satisfied by skips or missing structured results.

## Compatibility and failure contracts

Patient selection transports the message only through existing POST bodies, with
context encoding. It does not introduce browser/session draft storage. Automatic
MRP defaults follow the selected patient; an explicit or deliberate assignee
selection takes precedence. Unavailable recipients require an active replacement.
In multisite mode, changing clinics deliberately clears the transported recipient;
search/result and search/Back must retain that clear and refuse saving until an
active recipient is chosen. A deliberate recipient must also survive both routes
when Preferences is Default (no saved property). The native MRP routing workflow
covers these eight combinations under MRP and Default preferences, comparing
displayed, submitted and stored recipients and preserving an unrelated control.
Dates keep Add's cumulative base and Edit's today-based behavior; only invalid
month-day overflow is corrected. Dashboard accepted values preserve their time.

Retain Add's explicit saved-with-attachment-warning outcome and Edit's attachment
refusal rollback. Permission denial, validation failure and complete success must
remain distinguishable. GET/HEAD mutation protection and CSRF remain enforced.

The release manifest separately records two existing concurrency failures:
`concurrency-tickler-edit` (stale edit overwrites completion) and
`concurrency-tickler-list-status` (stale Complete changes Deleted to Completed).
They remain visible and must be compared with the base; no exemption is added.
Any new or worsened failure blocks completion.

## Completion gate

Installed qualification, exact artifact identity, log/fixture cleanup, final-head
CI and review dispositions must be recorded before marking this PR ready. This
registry is not a claim that these checks have passed.
