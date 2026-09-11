# OWASP MASVS v2.1.0 working mapping

This is an engineering traceability map for the synthetic-data evaluation. It is **not** a claim of
MASVS compliance and it does not replace an independent mobile security assessment. The mapping is
pinned to [OWASP MASVS v2.1.0](https://github.com/OWASP/masvs/releases/tag/v2.1.0); it must be
reviewed against the current MASVS and applicable MASTG/MASWE tests before a pilot.

Status means:

- **Implemented**: the current source contains the control and automated evidence exercises it.
- **Partial**: useful controls exist, but required platform or adversarial evidence is still open.
- **Not applicable now**: the local-only evaluation has no such boundary; adding one reopens it.
- **Open**: the evaluation does not implement the control.

| MASVS control | Status | Current evidence | Remaining promotion gate |
| --- | --- | --- | --- |
| STORAGE-1 Secure storage | Partial | `VAULT_FORMAT.md`; encrypted manifests and chunked objects; private-file tests in `src-tauri/src/vault.rs` | Physical backup, restore, second-user, OS-index and platform file-protection inspection |
| STORAGE-2 Prevent data leakage | Partial | No telemetry; renderer receives opaque IDs/sanitized metadata; plaintext-free canary test; explicit export warning | Crash/swap/keyboard/app-switcher/screenshot/notification inspection; provider failure cleanup exercise |
| CRYPTO-1 Strong cryptography | Partial | Argon2id, XChaCha20-Poly1305, HKDF-SHA-256 and HMAC-SHA-256 documented in `VAULT_FORMAT.md`; corruption/boundary/property tests | Independent cryptographic review and physical-device Argon2 measurements |
| CRYPTO-2 Key management | Partial | Random master/object keys, authenticated key wrapping, domain separation, rotation checks and zeroization | Recovery kit, portable backup, device-bound biometric wrapping and crash/memory inspection |
| AUTH-1 Remote authentication/authorization | Not applicable now | No account or remote service exists | Reopen for production identity, passkeys/MFA, sync, sharing or CARLOS integration |
| AUTH-2 Local authentication | Partial | Offline passphrase unlock, wrong-passphrase behavior, configurable auto-lock and lifecycle concealment | Native lifecycle/device-account testing and biometric implementation |
| AUTH-3 Additional authentication for sensitive operations | Open | Sensitive operations require an unlocked vault | Product/security owner must define exact reauthentication operations and timing, then tests must enforce them |
| NETWORK-1 Secure network traffic | Not applicable now | Production CSP has no remote network origin; no network client or telemetry exists | Reopen for every future endpoint; require TLS configuration and captured-traffic tests |
| NETWORK-2 Endpoint identity pinning | Not applicable now | No developer-controlled endpoint exists | Decide pinning strategy when sync, identity, update or CARLOS endpoints exist |
| PLATFORM-1 Secure IPC | Partial | Empty Tauri renderer permission list; opaque-ID commands; bounded/invalid IPC property tests | Platform URI/provider, intent/deep-link and generated-permission abuse testing |
| PLATFORM-2 Secure WebViews | Partial | Bundled UI, restrictive production CSP, prototype freezing, no remote frames/objects, hostile-metadata/off-origin browser regression | Isolated capability-free hostile-PDF viewer and native navigation/deep-link/network regression suite |
| PLATFORM-3 Secure user interface | Partial | Immediate concealment, inactivity lock, explicit export/screenshot/deletion warnings and automated WCAG A/AA browser gate | Native snapshot suppression, screen-recording policy, notification/clipboard/share/print and manual assistive-technology tests |
| CODE-1 Up-to-date platform versions | Open | Hosted jobs use current pinned runner images/toolchains | Define and enforce minimum supported OS/WebView versions and end-of-support policy |
| CODE-2 Enforced app updates | Open | No updater is enabled in this evaluation | Signed updater, rollback protection, key rotation/revocation and store-release policy |
| CODE-3 Components without known vulnerabilities | Partial | Lockfiles, commit-pinned Actions, dependency review, npm production audit and CycloneDX SBOM CI | Remove the Linux `glib` advisory exception; artifact-specific release gate and response ownership |
| CODE-4 Input validation/sanitization | Partial | Bounded headers/manifests/batches, strict semantic validation, path/handle protections, chunk and malformed-input tests | Hostile PDF parser/viewer corpus, real provider/NTFS race tests and continued regression corpus |
| RESILIENCE-1 Platform integrity validation | Open | None | Risk-based rooted/jailbroken/compromised-device policy and bypass testing |
| RESILIENCE-2 Anti-tampering | Open | Authenticated vault data only; application binary is unsigned | Release signing, notarization/store signing, integrity response and tamper tests |
| RESILIENCE-3 Anti-static-analysis | Open | Release builds strip symbols | Owner/security review must decide proportional obfuscation and secret-extraction requirements |
| RESILIENCE-4 Anti-dynamic-analysis | Open | None | Owner/security review must decide proportional debugging/hooking defenses and test profile |
| PRIVACY-1 Minimize sensitive access | Partial | Local-only design; no analytics; narrow commands/capabilities; ciphertext-at-rest | Data-flow/privacy review once identity, CARLOS, sync, support or viewer boundaries exist |
| PRIVACY-2 Prevent user identification | Partial | No remote identifier or tracking exists | Inspect app/OS metadata and define privacy-preserving remote identifiers before integration |
| PRIVACY-3 Transparency | Partial | Synthetic-only warning and plaintext/loss/deletion boundaries are shown in-product | Approved privacy notice, app-store disclosures, consent/retention and legal review |
| PRIVACY-4 User control | Partial | Patient can import, organize, export and cryptographically delete from the live vault | Recovery/backup/sync deletion semantics, portable export, account/device deletion and policy approval |

## Review rule

Every change to native commands, capabilities, cryptography, storage, document rendering, recovery,
networking, identity, diagnostics or supported platforms must update this map and the corresponding
threat IDs in `THREAT_MODEL.md`. Only an independent reviewer may turn this working map into an
assessment result.

Every **Partial** or **Open** row blocks promotion. The accountable role is the application owner,
with security-reviewer disposition required for security controls and privacy/release/platform
owner disposition required for their respective controls. Those people are not yet named in this
evaluation repository, so assignment and written acceptance remain open rather than being silently
attributed to a contributor.
