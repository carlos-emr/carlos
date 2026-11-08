# MRP task routing (#5812)

`tickler-mrp-routing` requires an explicitly disposable, exclusive installed
deployment (`CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1`) and an active clinic.
It owns an admin/doctor author, two doctor MRPs, a doctor control, two synthetic
patients and a separate control task. Native registration refreshes provider
caches before the scenarios. The existing fixed-provider, Default-reset and
concurrent-preference checks remain registered.

Preferences > Set Tickler Preferences selects MRP. Each patient's native master
record opens Add Tickler, whose displayed and submitted assignee must match that
patient's distinct active MRP. Actual saves require exactly one task with the
correct patient, assignee, author and status. Separate authenticated MRP sessions
must each see only their intended marked task and open the correct patient;
the control must retain only its unchanged control task.

A composer opened from the global task list starts without a patient. The check
enters an unsaved message, attaches the second patient through native search,
then changes to the first patient through the same supported flow. Patient/MRP
reassociation and the final saved association are checked independently of draft
preservation. A lost draft is recorded as fatal; explicit re-entry permits the
independent association checks to continue and does not waive the loss.

A deliberate manual assignee change must override MRP. Missing-MRP and inactive-
MRP cases inspect the release's displayed default and guidance. The second MRP
is made inactive through its owned native administration form, after its receiving
session closes. The harness refuses to submit to an unrelated selected clinician.
An explicit manual choice of the owned author must preserve the draft and save
the intended recovery task. The unsafe default comparison remains fatal.
Explicit `taskTo` precedence over a different MRP default is independently covered
by `lab-recall-delegate-delivery` in the same installed ON/BC qualification.

Both provinces reach the active-MRP, recipient/patient, late-association, manual
override and manual-recovery stages on snapshot31 (official application source
`e0601ced004c`, Ubuntu 26.04, Chromium 149.0.7827.55). They fail exactly four
comparisons: lost draft on attachment, lost draft on patient change, missing-MRP
arbitrary fallback, and inactive-MRP arbitrary fallback. These are tracked in
[#5824](https://github.com/carlos-emr/carlos/issues/5824) and
[#5825](https://github.com/carlos-emr/carlos/issues/5825). No save to the unrelated
fallback recipient is attempted. Browser diagnostics remain clean. No expected-
failure pin was added and no full pass is claimed.

Cleanup guards both patients and every actor, refuses unexpected tasks or
attachments, removes exact owned tasks/history/preferences/provider archives,
and restores the first patient's original MRP before deleting accounts. The
second patient uses the strict metadata/reference fixture and cannot be deleted
while a browser remains connected or a clinical reference remains. A final
application restart discards caches of SQL-deleted actors. Failures in ownership,
cleanup or restart remain failures; unrelated clinical data is not repaired.

BC stores the known `formRourke2009` reference table under MyISAM, whereas ON
uses Aria. The shared cleanup helper now supports that exact table read-only
under either engine; owned writes still require InnoDB and other nontransactional
tables remain unsupported. `mixed-engine-reference-mariadb-checks.js` qualifies
the real locking/refusal behavior in a nonce-owned private database. For both
engines, an empty reference permits only the exact owned parent deletion; a live
reference refuses deletion and leaves parent, control and legacy rows unchanged.
All four checks of the mixed-engine reference-table locking and refusal harness
pass on both installed 16 KiB MariaDB servers and the separate 32 KiB MariaDB
server. The four MRP workflow failures described above remain fatal. Each private database is verified removed.

```sh
CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1 \
  npm run test:mixed-engine-reference-mariadb
```

`MARIADB_REFERENCE_SOCKET` optionally selects a separate local test server. It
does not change the application connection configuration. The harness requires
CREATE/DROP DATABASE privileges and refuses an existing or unowned namespace.
Independent post-run residue, health/configuration/package audits and final-head
review/CI remain part of consolidated acceptance.
