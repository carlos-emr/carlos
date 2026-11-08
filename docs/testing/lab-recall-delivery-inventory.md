# Lab-recall delivery and delegate usability (#5804)

`lab-recall-delegate-delivery` retains the existing Lab Recall Settings and
lab-recall workflow and extends it with a real send and separately authenticated
delegate/control sessions. It requires `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1`,
an active configured clinic, installed HTTPS/browser/SQL prerequisites and the
normal application service-management capability. Its exclusive, serialized run
restores cache/readiness before the next check; the existing destructive probe
retains its unique final position in the suite.
The sender has owned admin/doctor roles for fixture registration; the delegate
and non-recipient control are owned doctor logins.

SQL creates the owned account/contact/clinic associations and a synthetic patient
and PATHL7 lab. The active-provider list is cached, so a native administration
profile save for the owned sender refreshes the application's provider caches.
Its archive and audit belong to the same owned sender. No shared provider is
saved. After all browser, clinical and account cleanup, an explicit application
restart discards caches of SQL-deleted fixture providers. Restart/readiness
failure remains a cleanup failure. The fixture does not disable caching or
change production source/configuration.

The native preferences workflow saves/reopens the recall delegate, subject,
tickler assignee and priority, checks independent toggles and refuses an inactive
delegate. A fresh-sender Recall probe requires the explicit delegate to exist in
the deployed chooser before judging its selected value. It records a wrong
selection as a fatal finding. It closes the two unsent/unsaved forms and verifies
that neither a message nor a task was written. MRP is then selected through the
sender's native Tickler Preferences; the owned patient's MRP is the sender, so
the subsequent explicit recall delegate must override a different MRP default.
The fresh probe cannot become a pass merely because the later case works.

The actual Recall operation must open the message and companion task for the
same patient/lab, with the saved subject, delegate and High priority. Only the
owned local delegate may be checked; remote or other recipients are refused by
the harness before Send. A marked body is sent through the normal Messenger
button. SQL requires one sender-attributed message, exactly one new delegate
delivery and exactly one patient map. The existing native task save must create
one correctly attributed task with its exact HL7 link.

In separate sessions, the delegate finds and opens the message twice, checking
rendered sender, subject, body and patient identity. The delivery becomes read;
the control has no copy. The delegate's assigned-task filter must return exactly
the companion task. Its editor retains the assignee, and its native attachment
link must open the exact accession/lab and owned patient route. Reading/reopening
must not resend, duplicate the task or alter shared message/task/attachment data.

Cleanup claims empty namespaces before writes and recovers a lab INSERT whose
acknowledgement was lost by its exact binary payload. Changed, disappeared,
ambiguous or unsafe lab identities are refused; nine independent ownership
controls cover those outcomes. Message cleanup refuses changed content, another
recipient or another patient map. Clinical/task/attachment rows, preferences,
contacts, provider archives and accounts are removed with identity checks and
verified absent. The patient's temporary MRP is restored before account cleanup;
the prior Messenger coordinator row is preserved or its exact owned replacement
is retired. Browser shutdown and pending-write settlement precede SQL cleanup.

The earlier development runs are retained as failures: they exposed stale
provider-cache fixtures and an administration navigation/close assumption.
Installed ON/BC full outcomes, current-source receipts, restored cache/health
and independent residue audits remain required before claiming acceptance.

The final snapshot31 ON/BC retakes pass this delivery workflow and the existing
`lab-recall-delegate` and `lab-linked-tickler` checks together: three passes in
each province, zero failures/skips, Chromium 149.0.7827.55 on Ubuntu 26.04.
Application source is official target `e0601ced004c`. The published delivery
source SHA-256 is
`f2380ad2b3c681050bbb24099fee2127d94b6bc4c294e93fc865ab03d99b0833`.
Independent audits find zero owned patient/account/archive/message/task/lab
metadata residue and the original empty coordinator baseline. Configuration
hashes and package bytes remain unchanged; installed health passes and
`NRestarts=0`. Nine ownership controls and all 68 manifest controls pass.
