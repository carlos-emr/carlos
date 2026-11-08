# Abandoned appointment slot expiry (#5737)

Run the `appointment-slot-expiry` check in the `appointment-lock-expiry`
profile on an explicitly disposable installed deployment. The profile sets the
supported `appointment_locking_timeout=20` seconds and enables the ordinary
Add/Repeat/Group controls. The existing profile supervisor captures the exact
configuration bytes, restarts before the scenario, and restores those bytes and
restarts afterward, including on failure or interruption. It checks for unexpected
service restarts during the scenario. No configuration contents or credentials
are printed.

Three distinct owned logins use native Add Appointment editors. The owner holds
10:00; a doctor with no administrator privileges is blocked on an overlapping
10:05 slot with a different page ID. A third login holds a non-overlapping 16:00
slot and continues renewing throughout. Independent PageMonitor reads establish
each real lock, provider, page ID, configured timeout and server timestamp.

Chromium's DevTools `Debugger.pause` suspends the owned renderer's JavaScript without
navigating, closing its context, editing the application, invoking its release
handler or deliberately failing network requests. The last accepted server
heartbeat is recorded. A native successor refresh before expiry must still see
the foreign lock, with hidden booking controls and the frozen row unchanged.
The successor's regular native heartbeat then recovers the overlap through the
application's expiration handler. Recovery must occur after the server-clock
20-second boundary and within 12 seconds of it. SQL never deletes or ages the
monitor during the tested recovery, and the service is not restarted.

The independent live control must keep its original row ID and advance its
heartbeat. When the old owner resumes, its real heartbeat must remain unlocked
behind the successor. Its native Back/release must remove only its own monitor.
The successor searches and picks the owned patient, presses Add, and completes
one appointment in the recovered slot. Database fields, creator, monitor ownership
and the owner's native day sheet must agree. Form submission disables the
before-unload cancel handler in this release; any remaining non-renewing monitor
must retire through native timeout while the independent control keeps its
original row. Immediate cancellation is not invented as a new contract. No booking endpoint is called
directly. Existing explicit-release and non-admin workflows remain registered.

Cleanup checks actor identities before touching child rows, refuses unexpected
bookings, archives or extensions, deletes the owned booking/monitors/group, and
verifies absence plus the unchanged unrelated appointment inventory. Browser
shutdown and uncertain-write settlement precede SQL cleanup. Missing profile,
unreached timing/booking assertions, browser errors and cleanup failures cannot
produce a pass. Nine independent oracle controls exercise valid live/expired
boundaries and reject unlocked, malformed, ambiguous or incorrectly timed proof.

Example:

```sh
CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1 node scripts/run-playwright-suite.js \
  --province ON --profile appointment-lock-expiry --only appointment-slot-expiry
```

Installed ON/BC qualification and exact source receipts are required before
claiming this ticket's acceptance.

All stages pass in installed ON and BC Ubuntu 26.04 containers using snapshot31
(application target `e0601ced004c`, Chromium 149.0.7827.55). Abandoned recovery
takes 20.672 seconds on ON and 20.608 seconds on BC, within the configured
20-second timeout plus 12-second tolerance. Both final source digests match
`cc8296b59678a92d2a3037d09c7f12cb86010a7e2c8d402b3185438708f17d8a`.
Nine oracle controls pass, including exact-timeout rejection in both modes. Both profile supervisors finish successfully, exact original configuration hashes match,
installed health and package verification pass, and `NRestarts=0`. Independent
post-run audits find zero owned patients, providers, appointments, archives,
schedule groups or monitor names.
