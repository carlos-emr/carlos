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

Obtain the leaf certificate from the portal's authenticated administration
channel. If a proxy or load balancer terminates TLS, pin the key it presents to
CARLOS. Compute `sha256/<base64 digest>` from that verified certificate using the
recipe in `src/main/resources/carlos.properties`. Do not establish the initial
pin by reading an unverified network connection or blindly copying a mismatch
error. TLS pins are separate from the Ed25519 staff assertion keys described below.

For rotation, verify the new key through that same trusted channel, configure
both old and new pins, and restart CARLOS before changing the TLS terminator's
key. Confirm connectivity, then remove the old pin and restart CARLOS again.
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
by its length as an eight-byte big-endian integer. CARLOS encodes Unicode URL
prefixes before signing and sends the same UTF-8 body bytes that it hashes.
Proxies must preserve the path, query, and body seen by the portal. Rewriting
these values invalidates authentication; do not enable legacy authentication
to work around a mismatch.

The transport allows four concurrent exchanges per client and has no request
queue. `patient_portal.timeout.request.ms` defaults to 20000 and must be positive
and below 60000. It bounds the caller's wait through connection establishment and
body reading, in addition to the connect/read inactivity timeouts. On expiration
or interruption, CARLOS cancels the underlying HTTP request. A worker that does
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

`V1.0.31` grants `doctor` full `_portal.invite` and read-only `_portal.account`, because `doctor` is the
only non-admin role the baseline grants `_email`. Unlocking a portal account stays with `admin`, where
`V1.0.30` put it. Front-desk roles hold `_demographic` but not `_email`: granting them `_portal.invite`
in Administration > Security lets them see and revoke invitations, but not send one or resolve an
unfinished delivery.

Two settings are required, and invitations are refused until both are set:

| Property | Meaning |
|---|---|
| `patient_portal.public_base_url` | The address patients open, `https://` only. It is not the pinned internal API origin and usually differs from it. |
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

An attempt stopped before the commit is `ABANDONED`, and CARLOS revokes the prepared code on the
portal: a live preparation otherwise blocks every new invitation for the patient until it expires.
After the commit, CARLOS never revokes on uncertainty; a refused send is fixed by a resend, which
issues a new code and keeps the old one valid until the replacement is committed. The one uncertain
case before the send is a commit whose answer is lost twice: CARLOS withdraws the new code, and since
the portal may already have retired the old one while activating the new, the page tells staff that a
replaced invitation may no longer work and a new one should be sent.

Why an attempt stands where it does is stored as an `outcome` code (`PatientPortalInviteDelivery.Outcome`),
with a separate `revoke_failed` flag when an unused code could not be withdrawn and will expire on its
own. The row holds no prose and nothing from a portal response; the staff page translates the codes.

The email links to `<public_base_url>/auth/activate` and carries the code as text. The code is never
placed in a URL, a log, or a browser-visible message.

The code is a credential that activates a patient's account, so CARLOS keeps it no longer than it must.
It lives in the outbox row only between the store and the send, which is the window the portal's
contract requires; once the send resolves either way, or staff resolve an unfinished delivery, the
stored body is replaced with a note saying the code is not kept. One exception: when the send fails
before the portal activates the code (an archive or permission refusal, say), the code is withdrawn on
the portal but can stay in that failed outbox row; it can no longer activate anything. The outbound
email archive, a
permanent patient document, never holds it: the service names the code in
`EmailData.setArchiveRedactions`, and `EmailManager` archives the message with it replaced by
`[redacted]` and the artifact type suffixed `_REDACTED` (`SMTP_RFC822_REDACTED` or
`API_PAYLOAD_REDACTED`), so the copy is never mistaken for the
exact bytes sent. If the code cannot be found verbatim in the prepared message, the send is refused
before the portal activates anything. Reopening a portal invitation in the email compose window is refused outright, so the
message history cannot hand the credential to a reader who holds email access but no portal rights. A
patient who never received their email gets a resend, which issues a new code; CARLOS never re-sends the
stored one. The email passes through the same consent gate as every patient email: `OPT_IN`, or
`UNKNOWN` with a documented override reason. Text-message invitations are reserved until CARLOS has
an SMS provider.

An attempt that did not finish shows as incomplete on the page. After 15 minutes without a change,
staff can resolve it: **Stop and withdraw the code** before the commit, or **It arrived** / **It did
not arrive; revoke it** after it. Recovery re-checks that the patient and the portal connection match
the attempt. Nothing runs in the background.

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
