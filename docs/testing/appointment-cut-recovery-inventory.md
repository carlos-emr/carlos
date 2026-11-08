# Cut destination recovery and clipboard snapshots (#5738)

`appointment-cut-recovery` runs with the disposable `appointment-lock-expiry`
profile, alongside the expiry workflow. The shared profile restores exact
configuration bytes and verifies no unexpected restart during either scenario.
The scenario owns its patient, login, day-sheet group, three distinct source
appointments and an unrelated control booking. Every source has distinguishable
reason/notes, dates, times and patient/provider identity. SQL only creates these
fixtures and observes/cleans state; all Cut, Copy, Paste, validation, save, edit
and delete operations use native editor controls.

The release implements a **session snapshot**, not a transactional pending move:
`ApptUtil.copyAppointmentIntoSession` captures form values; Cut immediately
archives and removes the original and records `Appointment.cut`. The session's
`ApptData` is retained after Add. A local-storage flag triggers automatic Paste
once and resets when the destination opens. Further explicit Paste actions can
reuse the same snapshot. Paste itself fills a form and does not save a booking.
The clipboard is not a reference that tracks later source edits or deletion.
These are observed release behaviors, not an added undo or restoration feature.

The check requires:

- One exact complete archive, one cut audit and an absent original immediately
  after Cut. Every original column supported by the archive is compared; the
  schema's absent `reasonCode` column is explicitly excluded.
- Closing an automatically pasted destination leaves the move incomplete, with
  no restored source or destination. A reopened form requires explicit Paste
  and must recover the same patient, reason, notes and duration.
- An invalid cross-day duration gives actionable visible guidance and sends no
  Add request or partial write. Correcting the form submits once and produces
  exactly one new appointment at the intended slot with a native day-sheet link.
- Repeated explicit Paste and Back after the move perform no hidden save or
  duplicate creation. The retained snapshot's patient attribution remains exact.
- Losing the browser context after another Cut gives a fresh authenticated
  context no clipboard. The source remains absent, its complete archive remains,
  and no destination exists. This is reported explicitly as an **abandoned,
  incomplete move**, never as a recovered appointment.
- Copy leaves its original untouched. A subsequent native source edit/delete
  writes the expected two archive versions, while explicit Paste still exposes
  the old session snapshot without silently creating another booking. The moved
  booking, abandoned archive and unrelated control remain unchanged.

There is no automatic undo or user-facing recovery of an abandoned Cut in this
observed contract. Whether that archive-only recovery policy is acceptable is a
product decision; this test does not manufacture a completed move or approve that
policy. Existing successful Copy/Cut, explicit-release, permission and GET-refusal
checks remain registered.

Cleanup first checks provider identity and every dependent appointment's patient
and marker. Unexpected archives, appointments or linked records retain fixtures
for reconciliation. Known owned appointment/archive/monitor/group rows are then
removed and verified; the unrelated appointment inventory must remain identical.
The account is retained when dependent rows remain. Browser shutdown and pending
write settlement precede SQL cleanup. Native failures and unreached later stages
remain visible failures. Installed ON/BC outcomes and source receipts are required
before claiming acceptance.

All six stages pass in installed ON and BC Ubuntu 26.04 containers using
snapshot31 (application target `e0601ced004c`, Chromium 149.0.7827.55). Each run
finishes with exact owned workflow cleanup and unchanged control/unrelated
appointments. Final source SHA-256 is
`827f92c20843937757491c1079a92ae54f104e8e0019b8b2f744dd67207fd5d9`.
Both profile supervisors finish successfully, exact original configuration hashes match,
installed health and package verification pass, and `NRestarts=0`. Independent
post-run audits find zero owned patients, providers, appointments, archives,
schedule groups or monitor names.
