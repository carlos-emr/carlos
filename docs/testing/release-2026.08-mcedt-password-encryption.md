# MCEDT password encryption (#4498, consolidated #5257)

> Implementation/proof history: see [the review follow-up](release-2026.08-mcedt-credential-review.md)
> for the current dedicated encrypted design and qualification status, including
> intermediate installed snapshots and the latest follow-up retake. The older
> component proof below applies to the earlier property-based format.

The release Change Password action stores its input directly in the property
row, and the delegate passes that value directly to the WS-Security builder.
Finding 189 retains the original installed reproduction with a synthetic value.
Four new regressions reproduce plaintext storage, the lack of key-failure
protection, missing legacy migration, and sending stored ciphertext unchanged.
The pre-repair 18-case run has those four failures and fourteen passing controls;
JUnit SHA-256: `7820865421c419f9d31e4cdc05ebe1822750e1dcdcbd6ff5b5fd9b7767611f65`.

`McedtCredentials` uses the existing AES-GCM `EncryptionUtils` and application
key. Change Password encrypts before changing a row and reports failure if the
key or persistence is unavailable. The delegate receives the decrypted password.
Legacy rows migrate on their first credential read, under row locks in the same
transaction. Duplicate legacy rows also migrate. Already encrypted rows retain
their ciphertext; missing/blank values retain the configured-password fallback.
Unread or disabled MCEDT accounts migrate when first used, rather than through a
startup migration. The encryption key must be retained with the installation's
existing encrypted service credentials.

All 26 selected cases pass with no failures, errors or skips, including six real
H2/Spring/JPA commit, rollback and concurrent-writer cases. A held writer lock
blocks migration; migration subsequently sees the committed replacement instead
of overwriting it. A decryption failure rolls back other rows migrated in the
same transaction. Corrupted ciphertext and unavailable keys fail closed, and
exceptions expose only static messages without credential-bearing causes.
Delegate tests intercept the actual builder configuration without making network
requests. These are H2 and unit controls, not an authentication run against the
provincial claims service.

26-case JUnit SHA-256:
`9e96a75c31991d9b3067f97b70922f255a046d6a2cf70e5b70fd2568a824e005`.
Source-bound changed-line coverage is 100/100/100 percent for the new credential
service and changed action lines. Delegate coverage rises from 0/0/100 to
66.67/64.29/100 percent; the remaining missed line is the existing malformed
keystore-URL diagnostic. The selected evidence preserves every retained floor.

Both native mailbox variants now check the encryption marker and inequality to
the synthetic input, without reading any stored credential. The clinic's own
rows remain parked and are restored with the existing digest proof. Native
component qualification passed on Ubuntu 26.04 with the full mailbox, focused
plaintext-storage and static-HTML metadata workflows (three cases, no skips).
JUnit SHA-256: `bb659a9c83b1bf1a285fab5b8aec8c4b5a5d9eb2f53460ed652538e07245d8b6`.
The raw log audit retained exit 1: four recognized events and exactly three
owned invalid-delete refusals, with zero unexplained events after scoped
disposition. All 10,755 original application files, properties and execution
inputs were restored. This temporary six-class component deployment is not a
rebuilt-DEB qualification or Ministry authentication. The plaintext expected
failure is removed and finding 189 is marked fixed; combined-DEB qualification
remains pending.

The final JavaDoc-bound source rerun also passes all 26 cases. Fresh evidence
SHA-256: `a9427b2fec3faf3ab7c1fb99b32dfc8c65633fd16f215b9e1e6a71d89d79ce20`.
All 8,111 complete Node cases pass on Node 22.23.5, across 570 files, with zero
failures, skips, TODOs, cancellations or changed test sources. The tracked case
inventory is generated from that execution.

All 528 Python CI-tooling cases also pass with the required pinned defusedxml
and actual xmllint prerequisites present. The initial missing-prerequisite
runs and one transient orphan-process test failure are retained; the isolated
seven-case process-group rerun and the final complete suite pass.
