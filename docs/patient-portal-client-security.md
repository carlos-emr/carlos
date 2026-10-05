# Patient portal client security checks

The CARLOS client signs the authenticated provider, clinic, permissions, key ID,
and exact request. The portal verifies the signature and consumes its UUID nonce
before executing an internal operation. Patient access and privilege checks still
run in CARLOS before signing.

## Authenticating the portal server

`patient_portal.certificate.pins` is required whenever the integration is
configured. Missing, empty, or malformed pins prevent client initialization;
there is no fallback to CA-only trust. Leaving the whole integration unconfigured
still leaves the rest of CARLOS available. Existing deployments must provision
verified pins before deploying this change and restarting CARLOS.

CARLOS requires TLS 1.2/1.3, normal certificate validation, a matching hostname,
and a SHA-256 pin matching the leaf certificate's public key (SubjectPublicKeyInfo).
An impostor with a certificate accepted by the JVM truststore still fails when
its key is not pinned. Appending the genuine portal certificate to an impostor's
chain does not satisfy the pin. Redirects are disabled so credentials are not
forwarded to a different endpoint.

Pin the key of whatever terminates TLS for CARLOS, such as nginx on the portal
host. The recommended first pin comes from a key the clinic generates itself: compute
`sha256/<base64 digest>` from that key before any certificate is issued, so nothing is
read off the network, and make and pin a standby key the same way. For a portal already
serving a key the clinic did not generate, copy its certificate file from the server over
an authenticated channel and compute the pin from that file. Both recipes are in
`src/main/resources/carlos.properties`. Do not establish the initial pin by reading an
unverified network connection or blindly copying a mismatch error. TLS pins are separate
from the Ed25519 staff assertion keys described below.

For rotation, obtain the new key's pin the same way, configure both old and new pins, and
restart CARLOS before changing the TLS terminator's key. Confirm connectivity, then remove the
old pin and restart CARLOS again.
Certificate renewal with the same public key preserves the pin. Operators must
coordinate automated key rotation with this process; failures stop portal calls.

This authenticates the configured TLS endpoint. It does not protect a compromised
portal, a stolen pinned private key, modified CARLOS configuration, or patients
who visit a separate phishing site. Deployment and the actual pins need separate
verification; repository tests do not attest to a live installation.

## Authenticating CARLOS requests

Configure `patient_portal.staff_assertion.key_id` to match an entry in the portal's
`PATIENT_PORTAL_INTERNAL_STAFF_ASSERTION_PUBLIC_KEYRING`. Deploy a new public key
in that ring before changing the CARLOS key ID and private key. Keep the old
public key until outstanding assertions have expired. The complete configuration
and rotation example is in `src/main/resources/carlos.properties`.

The request hash is SHA-256 over four components: uppercase ASCII method, raw
UTF-8 path, raw UTF-8 query (without `?`), and body bytes. Each component is prefixed
by its length as an eight-byte big-endian integer. CARLOS sends the same raw
path, query and UTF-8 body bytes that it hashes. Proxies must preserve the path,
query, and body seen by the portal. Rewriting these values invalidates
authentication; do not enable legacy authentication to work around a mismatch.

`patient_portal.base_url` names the portal's origin only (`https://host[:port]`).
A path is rejected when the portal client is first used after a restart, because
its settings are read lazily. CARLOS calls `/internal/carlos/` at the root
of that origin even when patients reach the portal under a prefix. The portal's
reference proxy answers `/<prefix>/internal/` with 404, and a proxy that strips a
prefix would change the raw path the request hash binds.

The transport allows four concurrent exchanges per client and has no request
queue. `patient_portal.timeout.request.ms` defaults to 20000 and must be positive
and at most 59000: the 60-second assertion lifetime, less one second because the
assertion's times are rounded down to whole seconds. It bounds the caller's wait
through connection establishment and body reading, in addition to the
connect/read inactivity timeouts. On expiration or interruption, CARLOS cancels
the underlying HTTP request. A worker that does
not respond to cancellation retains its slot until it actually exits, preventing
unbounded replacement threads. A timed-out mutation may already have applied;
check current state before retrying.

## Inviting a patient

Staff invite a patient from the **Patient portal** link on the demographic record. The link appears
only when the portal is configured and the user holds `_portal.invite` or `_portal.account` read for
the patient. Resolving an unfinished delivery needs `_portal.invite` and `_email` write, the same
email privilege the rest of CARLOS requires to create or close an outbox row. Sending also needs
`_edoc` write, because every sent email is archived as a patient document. Both are checked before the
portal is asked for anything, and the page shows only the controls the user's rights allow.

`V1.0.43` grants `doctor` full `_portal.invite`, because `doctor` is the only non-admin role the
baseline grants `_email`. Read-only `_portal.account`, which the invitation panel uses to show whether
the patient already has an account, comes from `V1.0.41`; `V1.0.43` does not grant it again, so a
clinic that removed it keeps that choice. Unlocking a portal account stays with `admin`, where
`V1.0.41` put it. Front-desk roles hold `_demographic` but not `_email`: granting them `_portal.invite`
in Administration > Security lets them see and revoke invitations, but not send one or resolve an
unfinished delivery.

Two settings are required, and invitations are refused until both are set:

| Property | Meaning |
|---|---|
| `patient_portal.public_base_url` | The address patients open, `https://` only. It is not the pinned internal API origin and usually differs from it. Unlike `base_url`, it may carry the path prefix the portal's patient pages are served under. |
| `patient_portal.invite.sender_email` | The sender address of an active CARLOS email account. |

The portal's two-phase contract decides the order of every invitation. `PortalInviteDeliveryService`
records each step in `patient_portal_invite_delivery` before the next network call:

1. **Prepare.** The portal returns an inactive code for a new operation id. A lost response is
   retried once with the same operation id, which the portal answers with the same code.
2. **Store.** The email carrying the code is written to `emailLog` as `PENDING`. This is the durable
   job the portal requires before it activates anything.
3. **Commit.** Inside `EmailManager.DispatchGate`, once the email row exists and the message is built and
   archived, immediately before the transport sends it, CARLOS calls `commit-delivery` with
   `delivery_reference=emaillog:<id>`. Every local step that could still fail has already succeeded, so a
   commit is followed by nothing but the send. A lost answer is retried once; the portal treats a repeat
   with the same operation id and reference as the same commit. The portal activates the code and starts
   its seven-day lifetime. If the commit fails for any reason, nothing is sent.
4. **Send.** The normal email stack sends the message, and the attempt records `SENT`, `SEND_FAILED`
   or `SEND_UNCERTAIN`.

Stopping an attempt before sending claims `ABANDONING`, and CARLOS revokes its code on the
portal: a live preparation otherwise blocks every new invitation for the patient until it expires.
Confirmed withdrawal finishes the attempt as `ABANDONED`; a failed withdrawal stays open for retry.
After the commit, CARLOS never revokes on uncertainty; a refused send is fixed by a resend, which
issues a new code and keeps the old one valid until the replacement is committed. The uncertain cases
before the send are a commit whose answer is lost twice, and a commit the portal made that CARLOS could
not record (a failed database write, or a crash, after which staff stop the attempt). CARLOS withdraws
the new code, which never left, and records the commit as unconfirmed, never refused: since the portal
may already have retired the old code while activating the new, the page tells staff that a replaced
invitation may no longer work and a new one should be sent. A queued attempt without a recorded
commit refusal keeps activation unconfirmed when stopped by staff: a remote commit may still complete
after a status lookup, so observing the earlier invitation pending cannot prove that its replacement
was never activated. A recorded commit refusal is preserved under the database row lock, including
when the gate records it after recovery has read an older snapshot.

Why an attempt stands where it does is stored as an `outcome` code (`PatientPortalInviteDelivery.Outcome`),
with a separate `revoke_failed` flag when code withdrawal remains unconfirmed and needs a retry. The row holds no prose and nothing from a portal response; the staff page translates the codes.

The email links to `<public_base_url>/auth/activate` and carries the code as text. The code is never
placed in a URL, a log, or a browser-visible message.

The code is a credential that activates a patient's account, so CARLOS keeps it no longer than it must.
It lives in the outbox row only between the store and the send, which is the window the portal's
contract requires; once the send resolves either way, or staff resolve an unfinished delivery, the
stored body is replaced with a note saying the code is not kept. `EmailManager` also attempts this
body-only cleanup when a synchronous failure precedes the dispatch gate, including consent snapshot,
archive or authorization failures. Cleanup failure preserves the send result and is logged without
email content. Process crashes can still leave stored bodies; this finalizer does not guarantee
cleanup after process death. The outbound email archive, a
permanent patient document, never holds it: the service names the code in
`EmailData.setArchiveRedactions`, and `EmailManager` archives the message with it replaced by
`[redacted]` and the artifact type suffixed `_REDACTED` (`SMTP_RFC822_REDACTED` or
`API_PAYLOAD_REDACTED`), so the copy is never mistaken for the
exact bytes sent. If the code cannot be found verbatim in the prepared message, the send is refused
before the portal activates anything. Reopening a portal invitation in the email compose window is refused outright, so the
message history cannot hand the credential to a reader who holds email access but no portal rights.
Manage Emails and the chart's email viewer send staff to the patient's portal page instead. While an
invitation's delivery is open, Manage Emails does not resolve its outbox row by hand: the delivery
record, not that row, says whether a code is live. Once the delivery has finished, the row resolves like
any other, which clears one left pending by a status write that failed after the send. A
patient who never received their email gets a resend, which issues a new code; CARLOS never re-sends the
stored one. A replacement, whether from **Resend** or from inviting again over a pending
invitation, keeps the identity details the original invitation was issued with: CARLOS sends none with
it, and the portal copies the original's. So after correcting the patient's email address, date of birth
or health card number on the chart, staff must revoke the invitation and invite again; a replacement
would carry a code the patient can never activate. The page says so before it replaces an invitation. The email passes through the same consent gate as every patient email: `OPT_IN`, or
`UNKNOWN` with a documented override reason. Text-message invitations are reserved until CARLOS has
an SMS provider.

An attempt that did not finish shows as incomplete on the page. After 15 minutes without a change,
staff can resolve it: **Stop and withdraw the code** before the commit, or **It arrived** / **It did
not arrive; revoke it** once the send call has returned with an uncertain outcome (`SEND_UNCERTAIN`).
A `COMMITTED` attempt may still have a paused sender and offers **It arrived** only. Confirm that
choice from actual arrival evidence; it records that evidence without cancelling the sender or
sending another email. A crash-stuck `COMMITTED` attempt cannot safely be marked not sent online:
negative cleanup requires verified shutdown/quiescence of all sender nodes and separately authorized
maintenance. This deliberately narrows the original recovery choices in #3854. Explicit **Revoke**
is still available as intentional code invalidation; it does not claim that no email was sent and
cannot cancel or recall an email already in progress. Stopping first atomically marks the attempt `ABANDONING`, before
looking up or revoking any code. This blocks a paused sender from advancing to `COMMITTED` and
sending; if the sender already advanced, stopping fails without revoking. The 15-minute wait only
controls when recovery is offered and is not proof that a sender has stopped. An interrupted
`ABANDONING` attempt stays unfinished and offers the same stop action again. A lost or refused
withdrawal response also keeps that state, until a retry proves the code revoked or superseded.
If the portal proves the invitation already activated an account, the stopped attempt records
`CODE_ALREADY_USED`; it does not invent an email delivery confirmation or chart note. A preparation whose
response arrives after that claim is discarded and withdrawn without sending. If replay cannot
identify a lost preparation, list recovery matches its exact delivery operation id; it never guesses
ownership from another attempt's age or missing invite id. No database row lock
is held across a portal or email call. Deploy this recovery change to every CARLOS sender node
together after draining or stopping existing sends: older nodes do not understand `ABANDONING`
and retain the old recovery protocol. "It did not arrive" first marks the attempt `REVOKING`, which is
unfinished, and marks it `REVOKED` only once the portal has confirmed the code dead; if that is
interrupted or the call fails, the attempt stays `REVOKING` and staff can revoke it again after the
same wait. A timeout can still apply remotely, so positive confirmation stays unavailable unless the
portal proves the invitation was already accepted (an irreversible state that cannot be revoked). Recovery re-checks that the patient and the portal connection match
the attempt, and the page offers no decision for an attempt made on another portal connection. Each
decision is written to the CARLOS audit log as `PortalInviteDeliveryService.recover.<decision>`, with the
delivery id, the patient, and the state and outcome codes it left; never the code. Nothing runs in the
background.

An attempt stuck before the commit usually leaves a prepared code on the portal, which blocks every new
invitation for that patient until it expires. So when staff next invite or resend, an attempt stuck that
way for 15 minutes on the same portal connection is refused with `stale_attempt_exists`; the page asks
whether to withdraw it, and on confirmation (`withdrawStale=true`) withdraws it exactly as **Stop and
withdraw the code** would, then sends. Nothing from such an attempt reached the patient. An attempt
whose code went live is never withdrawn this way: only staff can know whether its email arrived.

A sent invitation is recorded on the patient's chart as a short signed note naming the address it went
to, never the email itself: a chart note is permanent, and the email carries the account credential. The
note is written when the send succeeds, or when staff confirm an uncertain one arrived. If the note
cannot be written, the invitation stays sent and the attempt records `chart_note_failed`, which the page
shows so staff can add the note by hand. Other patient emails can copy their full content to the chart;
portal invitations must never be switched to that.

## Verification

Run the CARLOS regression suite:

```sh
mvn -Dtest='*Portal*UnitTest,MutatorActionGetRejectionContractUnitTest' test
```

`PortalStaffAssertionSignerUnitTest` compares Java's complete signed assertions
with `src/test/resources/patientportal/assertion-contract.json`. These vectors
use a public RFC 8032 test key and were generated against portal commit
`c8d558dbffdc1c8bf701884b91e7310d1c05150c`. They cover empty bodies, Unicode bodies,
encoded deployment paths, and query ordering/escaping. They contain no operational
credentials or patient data.

Using a Python environment with the corresponding `carlos-patient-portal` package
installed, check the same vectors with the portal's real verifier:

```sh
/path/to/portal-venv/bin/python scripts/check-patient-portal-assertion-contract.py
```

This check requires no running portal or network calls. It disables legacy
assertions, exercises modified method/patient/query/body rejection, keyring
overlap and retirement, expiration, and duplicate nonce rejection across two
database sessions using an in-memory SQLite database. It patches only the clock
to the fixture's timestamp. Run both suites when updating the fixture or either
side of the authentication contract.

`PortalRequestDeadlineUnitTest` exercises slow response cancellation, recovery,
excess-work rejection, interruption, and shutdown using real loopback sockets.

`PortalTlsTrustUnitTest` uses real TLS servers and a temporary test truststore to
verify that a trusted impostor with the wrong key and a pinned certificate for
the wrong hostname receive no HTTP requests. It also verifies successful pinned
connections during key overlap, rejection of untrusted chains even with matching
pins, and rejection of a genuine certificate appended behind an impostor leaf.
Settings and transport construction tests reject absent pins before any connection.
