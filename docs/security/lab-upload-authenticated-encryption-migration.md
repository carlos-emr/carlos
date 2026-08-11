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

There is no protocol-version field, IV, nonce, or authenticated-encryption tag. The
receiver selects a downstream parser from the `type` column of `publicKeys`. The source
tree contains handlers for ALPHA, BIOTEST, CDL, CLS, CML, EPSILON, ExcellerisON, GDML,
HHSEMR, IHA/IHAPOI, MDS, PATHL7, PDFDOC, PHS, Spire, TRUENORTH, MEDITECH, and
FHIR_COMMUNICATION_REQUEST. Handler presence does not prove that a sender is active in a
particular deployment.

Sender implementations and vendor contacts are not stored in this repository. Each
deployment must inventory its own `publicKeys` rows and resolve the optional
`matchingProfessionalSpecialistId` contact before a cutoff can be scheduled.

## Required owner inventory

The deployment maintainer must create one row per configured `publicKeys.service` in the
following register. Do not copy private keys or patient data into the register.

| Service | Handler type | Deployment | Sender/vendor owner | Technical contact | v2 capable | Last legacy upload | Cutover approved |
| --- | --- | --- | --- | --- | --- | --- | --- |
| _Complete from each deployment_ | | | | | | | |

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
| `key` | Base64url, without padding, of a 256-bit AES key wrapped with RSA-OAEP-SHA-256 |
| `nonce` | Base64url, without padding, of a unique 12-byte random nonce |
| `file` | AES-256-GCM ciphertext followed by the 16-byte authentication tag |
| `signature` | Base64url RSA-PSS-SHA-256 signature of the canonical envelope |

The GCM additional authenticated data is the byte sequence `carlos-lab-upload`, `0x00`,
`2`, `0x00`, then the UTF-8 service identifier. The canonical signature input is the
length-prefixed concatenation of the protocol marker, UTF-8 service, wrapped key, nonce,
and ciphertext including the tag. Lengths are unsigned 32-bit big-endian byte counts.
Binary values are decoded before canonicalization.

Each sender must generate a fresh AES key and nonce for every upload. Nonce reuse with a
key is forbidden. RSA-OAEP uses SHA-256 for both the message digest and MGF1, with an
empty label. AES-GCM uses a 128-bit authentication tag.

### Parsing and downgrade rules

- An absent `protocol_version` is legacy only during the transition window.
- A value of `2` is always parsed as version 2. Any missing, malformed, truncated,
  unauthenticated, or unsupported version 2 field is rejected; it is never retried as
  legacy.
- Any other explicit version is rejected.
- After strict field parsing, the receiver verifies the sender signature before
  unwrapping the AES key, then authenticates and decrypts the file with GCM.
- The receiver verifies the version 2 signature and GCM tag before exposing plaintext to
  file storage or lab parsers.
- External responses use one generic rejection outcome and do not disclose whether key
  unwrap, signature verification, GCM authentication, or parsing failed.
- Logs and metrics contain the service identifier, protocol version, and a coarse outcome
  only. They must not contain keys, nonces, signatures, ciphertext, plaintext, or
  cryptographic exception details.

## Fixtures and conformance tests

Sender and receiver implementations must share non-production fixtures covering:

- one valid version 2 upload for every key size and handler type in active use;
- modified service, wrapped key, nonce, ciphertext, tag, and signature;
- truncated and oversized fields;
- unknown and malformed versions;
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
4. Upgrade one sender at a time. Record its first and last successful version 2 upload,
   then enforce version 2 for that service so it cannot downgrade.
5. Monitor version counts and rejection counts through at least one normal delivery
   cycle for every sender.
6. Set and communicate a legacy removal date only after every active service is enforced
   on version 2. Record that date in this document and in release notes.
7. Remove the legacy decryptor, its configuration, and transition telemetry after the
   cutoff. Confirm code scanning alerts 6904 and 5637 close.

Before per-service enforcement, rollback means reverting a sender to its previous release
while the receiver continues accepting both formats. After enforcement, rollback requires
an explicit maintainer decision and configuration change; the receiver must never retry a
message marked version 2 as legacy. Receiver rollback after any sender is enforced on
version 2 is prohibited unless those senders are rolled back first.

## Implementation gates

Receiver implementation must not begin until all of these are recorded in this document:

- [ ] Active-service owner inventory is complete for every supported deployment.
- [ ] Sender owners approve the exact wire format and canonical signature input.
- [ ] Shared fixtures are available without patient data.
- [ ] Receiver-first deployment order and sender rollout order are approved.
- [ ] Telemetry owner, dashboard, alert threshold, and retention are defined.
- [ ] Per-service downgrade enforcement is designed.
- [ ] Legacy removal date and emergency rollback owner are named.

Until these gates are complete, replacing `Cipher.getInstance("AES")` in
`LabUpload2Action` would cause an outage for every sender still using the unversioned
protocol.
