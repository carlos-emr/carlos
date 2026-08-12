# Lab upload authenticated-encryption migration

## Status

The `/lab/newLabUpload` receiver still accepts the original, unversioned lab-upload
protocol. Its message cipher is AES/ECB with PKCS#5 padding and its wrapped key uses
RSA PKCS#1 v1.5. These algorithms cannot be changed in the receiver until every active
sender has implemented and tested the same replacement protocol.

This document is the coordination contract for that migration. It does not authorize a
receiver-only cipher change. Code scanning alerts 6904 and 5637 remain valid until the
legacy path is removed.

## Current protocol

An external sender posts a multipart request to `/lab/newLabUpload` with these fields:

| Field | Current format |
| --- | --- |
| `file` | Raw AES/ECB/PKCS5Padding ciphertext |
| `key` | Base64-encoded AES key wrapped with RSA/ECB/PKCS1Padding |
| `signature` | Base64-encoded MD5withRSA signature of the plaintext file |
| `service` | Sender identifier used to select a row from `publicKeys` |

Two distinct RSA keypairs are already in play, and version 2 keeps the same roles:

- The **receiver keypair** is the row of `oscarKeys` whose `name` is `oscar`, held in
  columns `pubKey` and `privKey`. The sender wraps the message key to the receiver public
  key; the receiver unwraps with its private key.
- The **sender keypair** is per service. The sender signs with its private key; the
  receiver verifies with `publicKeys.pubKey` for the requested `service`. Note that the
  Java field is named `base64EncodedPublicKey`; the column it maps to is `pubKey`.

Both public keys are base64 X.509 `SubjectPublicKeyInfo`; the private keys are base64
PKCS#8. The table is `oscarKeys`, plural — there is no `oscarKey` table.

There is no protocol-version field, IV, nonce, or authenticated-encryption tag. The
receiver selects a downstream parser from the `type` column of `publicKeys`. The source
tree contains handlers for ALPHA, BIOTEST, CDL, CLS, CML, EPSILON, ExcellerisON, GDML,
HHSEMR, IHA/IHAPOI, MDS, PATHL7, PDFDOC, PHS, Spire, TRUENORTH, MEDITECH, and
FHIR_COMMUNICATION_REQUEST. Handler presence does not prove that a sender is active in a
particular deployment.

Sender implementations and vendor contacts are not stored in this repository. Each
deployment must inventory its own `publicKeys` rows and resolve the optional
`matchingProfessionalSpecialistId` contact before a cutoff can be scheduled.

Three legacy receiver behaviors are recorded here because version 2 changes them and a
reviewer must not read the change as accidental. Each was reproduced against a running
receiver; see the verification note below.

- The legacy path decrypts and writes plaintext into `DOCUMENT_DIR` **before** the
  signature is checked. A message that decrypts but fails signature verification still
  lands on disk as readable plaintext. Because the receiver public key is public, the
  sender signature is the only thing standing between an authorized `_lab` caller and an
  arbitrary file in `DOCUMENT_DIR`, and it is checked too late. Version 2 forbids this
  ordering.
- A legacy signature failure leaves that already-written plaintext file on disk. Nothing
  removes it. Version 2 must not create a stored artifact for a message it rejects.
- An unrecognized `service` produces an empty client-info list, and the subsequent
  element access throws out of `execute()`. The `lab` Struts package maps
  `java.lang.Exception` to `errorpage.jsp` (`struts-lab.xml`), and that result is a JSP
  forward, so the sender receives **HTTP 200 with an HTML error page**. A sender using
  `use_http_response_code` reads that as a successful delivery. A misconfigured or
  retired service therefore fails silently in the direction that loses labs. Version 2
  requires the generic rejection outcome, with a rejection status, for unknown services.

### Verification note

The current-protocol table and the three behaviors above were confirmed against a running
receiver using an independently written sender built only from this document. Observed
outcomes, with `use_http_response_code` set: valid signature `200`; invalid signature
`406` with the decrypted file left in `DOCUMENT_DIR`; unknown service `200` with an error
page; and re-delivery of identical lab content under a freshly generated message key
`409`, confirming that `FileUploadCheck` deduplicates decrypted content rather than the
envelope. Reproduce with synthetic keys and synthetic lab content only.

In the default configuration, `/lab/newLabUpload` is a CSRFGuard-protected route: it is
absent from the `org.owasp.csrfguard.unprotected.*` list, so a non-browser sender must
present a valid session and `CSRF-TOKEN` in addition to the cryptographic material above.
Each deployment must confirm how its senders satisfy this today, because it constrains how
a version 2 sender is built and is not visible from the cryptographic contract alone.

## Required owner inventory

The deployment maintainer must create one row per configured `publicKeys.service` in the
following register. Do not copy private keys or patient data into the register.

| Service | Handler type | Deployment | Sender/vendor owner | Technical contact | Key bits | Key rotated | v2 capable | Last legacy upload | Cutover approved |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| _Complete from each deployment_ | | | | | | | | | |

Record the RSA modulus size of each `publicKeys` row and of the deployment's `oscarKeys`
receiver key. Anything below 2048 bits must be rotated before that service can be marked
v2 capable.

Use the Key Manager and deployment database to build the inventory. The
`matchingProfessionalSpecialistId` field may identify a contact in
`professionalSpecialists`; a null value means CARLOS has no recorded return contact.
Repository handler names are only discovery hints and must not be treated as a complete
production sender list.

The CARLOS maintainer owns the receiver rollout. The organization operating each sender
owns its sender release, fixtures, and cutover approval. A named owner and technical
contact are required for every active service before legacy removal.

## Proposed version 2 contract

This contract is a proposal until the sender owners approve it. Any change must update
the fixtures and this document before receiver code is merged.

### Multipart fields

| Field | Version 2 format |
| --- | --- |
| `protocol_version` | ASCII `2`; required and covered by the signature |
| `service` | Existing sender identifier; UTF-8 and covered by the signature and GCM AAD |
| `timestamp` | ASCII decimal seconds since the Unix epoch, UTC, no sign and no padding |
| `key` | Base64url, without padding, of a 256-bit AES key wrapped to the **receiver** public key with RSA-OAEP-SHA-256 |
| `nonce` | Base64url, without padding, of a unique 12-byte random nonce |
| `file` | AES-256-GCM ciphertext followed by the 16-byte authentication tag |
| `signature` | Base64url, without padding, of the **sender** RSA-PSS-SHA-256 signature over the canonical signature input |

`publicKeys.service` is `varchar(100)` with no character restriction, so version 2 adds
one. The proposed bound is 1–100 characters from `[A-Za-z0-9._-]`, rejected before any
key lookup or cryptographic operation. Confirm against the owner inventory before
approval: any real service identifier outside that set either widens the rule or is
renamed, and this must be settled while the contract is still a proposal.

#### Key material and minimum strength

The AES key is wrapped to the receiver public key (`oscarKeys` row `oscar`) and unwrapped
with the receiver private key. The signature is produced with the sender private key and
verified with `publicKeys.pubKey` for the requested `service`. Neither
key is interchangeable with the other; a message wrapped to the sender's own key is not
decryptable and must be rejected.

Both keypairs must be RSA with a modulus of at least 2048 bits. A service whose
`publicKeys` row holds a smaller key is not v2-capable and must rotate before cutover;
see the rotation step in the rollout. Deployments whose `oscarKeys` row predates the
2048-bit generator must rotate the receiver keypair at rollout step 4, before any sender
is upgraded, and that rotation is itself a coordinated change because every sender wraps
to that key.

#### Canonical encodings

The GCM additional authenticated data is the ASCII protocol marker `carlos-lab-upload`,
the one-byte ASCII protocol version `2` (`0x32`), the UTF-8 service identifier, and the
ASCII timestamp, in that order, each prefixed with its unsigned 32-bit big-endian byte
length. The AAD uses the same length-prefixed framing as the signature input so that a
single serializer can produce both.

The canonical signature input contains these seven fields in order: the ASCII protocol
marker `carlos-lab-upload`, the one-byte ASCII protocol version `2` (`0x32`), the UTF-8
service identifier, the ASCII timestamp, the wrapped key, the nonce, and the ciphertext
including its tag. Each field is prefixed with its unsigned 32-bit big-endian byte
length. Base64url fields are decoded to binary before their lengths are calculated and
before they are added to the canonical input.

Each sender must generate a fresh AES key and nonce for every upload. Nonce reuse with a
key is forbidden. RSA-OAEP uses SHA-256 for both the message digest and MGF1, with an
empty label. AES-GCM uses a 128-bit authentication tag. RSA-PSS uses SHA-256 as the
message digest, MGF1 with SHA-256, a salt length of exactly 32 bytes, and the standard
trailer field `0xBC`. Senders must not use a variable or maximum salt length; the
receiver verifies against the fixed 32-byte salt and any other length fails.

#### Size limits

A version 2 message is rejected before decryption unless every decoded field is within
these bounds: `key` is exactly the **receiver** modulus length in bytes; `signature` is
exactly the **sender** modulus length in bytes; `nonce` is exactly 12 bytes; `file` is at
least 16 bytes and no larger than the deployment's configured lab upload maximum;
`timestamp` is 1–20 characters. These are parser guards, not a substitute for the
container upload limit.

### Parsing and downgrade rules

- Every field named above must appear exactly once. A request carrying a repeated
  `protocol_version`, `service`, `timestamp`, `key`, `nonce`, `file`, or `signature` part
  is rejected without inspecting any occurrence. The receiver must not silently take the
  first value.
- Field values are compared byte-for-byte with no whitespace trimming, no case folding,
  and no base64 padding tolerance.
- An absent `protocol_version` is legacy only during the transition window, and only if
  the request also carries no `timestamp` and no `nonce`. A request that mixes legacy and
  version 2 fields is rejected rather than resolved toward either format.
- A value of `2` is always parsed as version 2. Any missing, malformed, truncated,
  unauthenticated, or unsupported version 2 field is rejected; it is never retried as
  legacy.
- Any other explicit version is rejected.
- After strict field parsing, the receiver verifies the sender signature before
  unwrapping the AES key, then authenticates and decrypts the file with GCM. The
  freshness-window and replay-cache checks sit between signature verification and key
  unwrap: `timestamp` is only trustworthy once the signature over it has verified, and
  neither check should cost an RSA private-key operation. A cheap window pre-filter before
  verification is permitted as a denial-of-service guard, but never as a replacement for
  the authenticated check.
- The receiver verifies the version 2 signature and GCM tag before exposing plaintext to
  file storage or lab parsers. A message that fails any check leaves no stored artifact.
- An unknown or malformed `service` produces the same generic rejection as a
  cryptographic failure; it must not surface as an unhandled exception.
- External responses use one generic rejection outcome and do not disclose whether key
  unwrap, signature verification, GCM authentication, or parsing failed. The existing
  `use_http_response_code` behavior is narrowed for version 2. Measured legacy codes are
  `200` accepted, `409` already delivered, `406` signature rejected, and `200` for an
  unknown service via the error-page forward. Version 2 keeps `200` and `409`, because
  senders need to distinguish delivery from duplicate, and collapses every rejection —
  including the unknown-service case that currently returns `200` — onto one status.
  Confirm that status with the sender owners during approval and record it here.
- Logs and metrics contain the service identifier, protocol version, and a coarse outcome
  only. They must not contain keys, nonces, signatures, ciphertext, plaintext, or
  cryptographic exception details.

### Freshness and replay

The legacy protocol has no replay protection: a captured upload can be re-posted and
still verifies. Version 2 closes this because the signature and the GCM AAD both cover
`timestamp`.

- The receiver rejects a message whose `timestamp` is more than 300 seconds from receiver
  clock time in either direction. The window is configurable per deployment but must be
  bounded.
- Within that window, the receiver recognizes exact repeats by the signature value, which
  is unique per message because the AES key and nonce are freshly generated. A repeat is
  **not** processed a second time.
- A repeat is not a security rejection. Senders retry after network timeouts, and a retry
  of the identical request is indistinguishable from a capture-replay — both are harmless
  precisely because neither produces a second side effect. The receiver therefore replays
  the *recorded outcome* of the first attempt rather than returning the generic rejection.
  A retry of a message that succeeded reports the same success or duplicate outcome it
  reported the first time, so timeout-and-retry keeps working unchanged.
- The signature cache must retain entries for at least the freshness window plus the
  allowed clock skew. A cache that expires sooner reopens the replay it exists to close.
- This cache is separate from `FileUploadCheck`, which continues to deduplicate identical
  lab *content* across the longer operational history. The cache is keyed on the envelope,
  not the payload, so it does not affect distinct uploads that happen to carry equal
  content.
- Receiver clock skew is therefore an availability dependency. Deployments must run NTP,
  and the telemetry owner must alert on a rise in timestamp-window rejections.

The window and the cache are both required. The window bounds how long a captured message
stays useful; the cache neutralizes repeats inside it. Neither alone is sufficient.

## Fixtures and conformance tests

Sender and receiver implementations must share non-production fixtures covering:

- one valid version 2 upload per handler type in active use, at each approved RSA modulus
  size of 2048 bits or more — sub-2048-bit keys are not fixtured because they are not
  v2-capable;
- modified service, timestamp, wrapped key, nonce, ciphertext, tag, and signature;
- truncated and oversized fields, including a wrapped key or signature whose length does
  not match the modulus, and a nonce that is not 12 bytes;
- an RSA-PSS signature produced with a salt length other than 32 bytes, which must fail;
- a message wrapped to the sender key instead of the receiver key, which must fail;
- unknown and malformed versions;
- a repeated `protocol_version` part and a request mixing legacy and version 2 fields,
  both of which must be rejected without falling back;
- a timestamp outside the freshness window; a verbatim replay inside it, asserting that no
  second side effect occurs and that the first outcome is replayed; and a verbatim replay
  after the cache retention has elapsed;
- a valid legacy upload during the transition window;
- an explicit version 2 upload that cannot fall back to legacy; and
- duplicate delivery behavior, which must remain compatible with `FileUploadCheck`.

Fixtures must use generated test keys and synthetic lab content with no patient data.

## Rollout, telemetry, and rollback

1. Complete the owner inventory and obtain written approval of the version 2 contract.
2. Merge receiver support for both formats, with protocol-version metrics and generic
   decryption failures. Keep legacy behavior unchanged.
3. Deploy the receiver before any sender. Confirm version 2 fixtures in a non-production
   environment.
4. Rotate any keypair below 2048 bits. Receiver-key rotation is coordinated across all
   senders at once; sender-key rotation is per service and updates that `publicKeys` row.
   Record each rotation date in the register.
5. Upgrade one sender at a time. Record its first and last successful version 2 upload,
   then enforce version 2 for that service so it cannot downgrade.
6. Monitor version counts, rejection counts, and timestamp-window rejections through at
   least one normal delivery cycle for every sender.
7. Set and communicate a legacy removal date only after every active service is enforced
   on version 2. Record that date in this document and in release notes.
8. Remove the legacy decryptor, its configuration, and transition telemetry after the
   cutoff. Confirm code scanning alerts 6904 and 5637 close.

Before per-service enforcement, rollback means reverting a sender to its previous release
while the receiver continues accepting both formats. After enforcement, rollback requires
an explicit maintainer decision and configuration change; the receiver must never retry a
message marked version 2 as legacy. Receiver rollback after any sender is enforced on
version 2 is prohibited unless those senders are rolled back first.

## Implementation gates

The gates are split because some of them can only be answered by a receiver that already
exists. Do not treat the second list as a precondition for writing code.

**Before receiver implementation begins**, all of these must be recorded here:

- [ ] Active-service owner inventory is complete for every supported deployment.
- [ ] Sender owners approve the exact wire format, canonical signature input, GCM AAD,
      RSA-PSS parameters, and minimum key size.
- [ ] Shared fixtures are available without patient data.
- [ ] Receiver-first deployment order and sender rollout order are approved.
- [ ] Telemetry owner, dashboard, alert threshold, and retention are defined.
- [ ] Per-service downgrade enforcement is designed.
- [ ] Freshness window, replay-cache retention, and receiver time-sync requirement are
      agreed.
- [ ] Emergency rollback owner is named.

**Before the legacy path is removed**, all of these must additionally be recorded here:

- [ ] Every active service is enforced on version 2, with its last legacy upload dated.
- [ ] Every keypair below 2048 bits has been rotated.
- [ ] Legacy removal date is set and communicated in release notes.

Until the first list is complete, replacing `Cipher.getInstance("AES")` in
`LabUpload2Action` would cause an outage for every sender still using the unversioned
protocol.
