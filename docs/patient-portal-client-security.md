# Patient portal client security checks

The CARLOS client signs the authenticated provider, clinic, permissions, key ID,
and exact request. The portal verifies the signature and consumes its UUID nonce
before executing an internal operation. Patient access and privilege checks still
run in CARLOS before signing.

## Authenticating the portal server

The portal is optional and off by default. It is used only when
`patient_portal.enabled=true`; a clinic that does not use it sets nothing, and
setting it back to `false` switches the portal off without removing its
credentials. With the portal off, the rest of CARLOS is unaffected and no portal
call is made. A blank value counts as off; any other value than `true` or `false`
is a configuration error, and the log names `patient_portal.enabled`. Anything
waiting on the portal when it is switched off, such as an unresolved portal
email, waits until it is switched back on.

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
