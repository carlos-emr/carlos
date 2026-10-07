# Patient portal client security checks

The CARLOS client signs the authenticated provider, clinic, permissions, key ID,
and exact request. The portal verifies the signature and consumes its UUID nonce
before executing an internal operation. Patient access and privilege checks still
run in CARLOS before signing.

## Authenticating the portal server

The portal is optional and off by default. It is used only when
`patient_portal.enabled=true`; a clinic that does not use it sets nothing, and
setting it back to `false` switches the portal off without removing its
credentials. With the portal off, no portal call is made. A portal JSON action
that would call the portal (the panel read, invitation revoke, account unlock
and access) answers 503 `portal_not_configured`, saying the portal is not
switched on. Requests refused for their method, patient, action name,
invitation id or privileges are refused as before (an account access request's
`enabled` and `reason` are checked only once the portal is on), and invitation
create and resend answer 503 `portal_invitation_unavailable` whether the portal
is on or off. The email
recovery page reports the portal as switched off or not set up correctly when a
recovery is attempted. The rest of CARLOS is unaffected, with two exceptions.
While `patient_portal.email.enabled=true`, every encrypted email is refused;
setting it to `false` puts encrypted email back on staff-entered passwords. And
whatever that setting, an email whose portal password still needs recovery
cannot be recovered until the portal is switched on again. Case does not matter for
`patient_portal.enabled` and a blank value counts as off, whereas
`patient_portal.email.enabled` must be exactly `true` or `false` in lower case;
for the switch, any value other than `true` or `false`, including one
followed by a `#` comment on the same line, is a configuration error; only the
portal's JSON actions name `patient_portal.enabled` in the log. A change takes
effect when CARLOS restarts.

Upgrading: an install that set up the portal before this switch existed, such as
a staging server, has no `patient_portal.enabled` line. After the upgrade its
portal is off, as described above, until `patient_portal.enabled=true` is added
and CARLOS restarts. If any email is still waiting for its portal password to be
recovered, add the line before upgrading.

`patient_portal.certificate.pins` is required whenever the integration is
enabled. Missing, empty, or malformed pins prevent client initialization;
there is no fallback to CA-only trust. Existing deployments must provision
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

## Booking prompt API draft (#3849)

`PatientPortalService` can create, list, and withdraw the portal's fixed-vocabulary booking prompts.
`portal.booking_prompt.manage` maps to `_portal.booking_prompt`. The JSON action is
`POST demographic/portalBookingPrompt`, with `method=create|list|withdraw` and `demographicNo`.
Every request checks patient-record access and the patient's booking privilege; list requires read,
and create/withdraw require write. Create also requires `_portal.account` read and checks for an
active account before requesting a prompt. The normal Struts CSRF protection applies.

Create accepts `operationId`, `urgency`, and `appointmentType`. Keep the same operation ID after
an uncertain response. The portal returns HTTP 201 with `created=true` initially and also HTTP 201
with `created=false` on a retry, without another notification. The staff action distinguishes those
outcomes with HTTP 201/200 and the same `created` flag. A confirmed retry records the prompt ID as
`create.confirmed`, allowing an earlier `create.unconfirmed` audit to be followed by a known result.
The portal response does not echo `operation_id`; the client verifies patient scope and the supplied
prompt fields, while the portal owns operation-ID matching.

List returns the portal's latest 100 prompts, including read/unread and notice timestamps. Withdrawal
first verifies the selected prompt belongs to the patient's list, then checks the returned prompt ID,
patient, and withdrawn state. An ID omitted from that bounded list is refused. Provider attribution
is optional; the action omits it until the staff UI has a server-verified provider selector, and never
accepts a provider name from browser free text. Audit failures after a confirmed remote change do not
turn that change into a retryable failure.

The UI draft adds a shared panel to the persisted appointment's patient and the master record.
Changing the appointment's patient ID or editing/previewing its patient name blocks portal
controls until save/reopen; every mutation rechecks the current inputs, including programmatic
changes that emit no input event.
The fragment checks patient access and booking read before rendering; create controls require
booking write and account read. The `panel` POST reports account eligibility, prompt history, and
current create/withdraw capabilities. It reads account status only when permitted and treats an
explicit absent account separately from an outage. It never reads unrelated invitations.

The browser waits for CSRF bootstrap, uses fixed pick-lists and text-only rendering, and disables
concurrent changes. An uncertain create retains its operation ID and fixed choices in tab-scoped
session storage, keyed by actor and patient, across refresh/navigation. No provider names, patient
names, or portal credentials are stored. Until confirmed, retries retain those choices and ID.
Sending is disabled if storage cannot retain the retry identity. Withdrawal failures require a
status refresh; no prompt is presented as withdrawn without the confirmed ID/state response.
The latest-100 history and optional provider attribution limitations above still apply.

All five catalogs use the same English labels. A separately approved security-object/default-role
database seed remains required before #3849 is complete. No permission is granted by this code alone. Offered-slot selection, atomic appointment creation, the polling
system principal, and decline/expiry ticklers belong to #3850. The draft is stacked on #3478 and
requires that client before mainline integration.
