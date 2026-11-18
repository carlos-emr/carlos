# MCEDT password encryption (#4498, consolidated #5257)

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
`a3914582796882b495691ad22b0c90f0f353c520bdd6ee0b56da807fe989d0dc`.
Source-bound changed-line coverage is 100/100/100 percent for the new credential
service and changed action lines. Delegate coverage rises from 0/0/100 to
66.67/64.29/100 percent; the remaining missed line is the existing malformed
keystore-URL diagnostic. The selected evidence preserves every retained floor.

Both native mailbox variants now check the encryption marker and inequality to
the synthetic input, without reading any stored credential. The clinic's own
rows remain parked and are restored with the existing digest proof. Native
component qualification is pending; the expected-failure entry and finding
status are retained until that verification completes.
