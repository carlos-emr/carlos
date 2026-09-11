# Security and privacy operations baseline

This baseline applies to the synthetic-data evaluation. It records decisions that can be made
without creating a production service or collecting patient data. It does not authorize a pilot.

## Supported-version boundary

There is no supported patient release today. Windows, macOS, Android and iOS are evaluation targets;
Linux is an unsupported compatibility monitor while GHSA-wrw7-89jp-8q8g remains in the resolved
runtime graph. Debug/unsigned artifacts are test evidence only. Minimum OS/WebView versions and a
production end-of-support schedule remain an owner decision and release gate.

## Diagnostics and data egress

- Analytics, crash upload, remote logging and telemetry are disabled.
- The allow-list of fields permitted to leave the device is therefore the empty set.
- There is no automatic support bundle. A future bundle must be generated locally, display every
  field to the patient before export, contain no document/profile/file name/path, key material,
  passphrase-derived value, vault/object ID or free-form log, and require an explicit save action.
- Adding any network, analytics, crash, support or update dependency requires a threat-model and
  `MASVS_MAPPING.md` update before code lands.

## Vulnerability intake and response

Use the repository's private GitHub Security Advisory process in `../SECURITY.md`; never place a
suspected patient-data issue in a public ticket. Until a dedicated repository exists, its published
acknowledgement/assessment targets apply. A production owner must additionally set remediation and
release targets by severity, name an incident commander and privacy/clinical contacts, and exercise
the process before a pilot.

For an evaluation security incident:

1. Stop distributing and running affected artifacts; preserve hashes and build/run identifiers.
2. Do not request vaults, exports, screenshots or logs from a tester. Assume any such artifact may
   contain sensitive data even though only synthetic data is authorized.
3. Open a private advisory with the minimum reproducible technical detail and no record content.
4. Identify affected commit/dependency/platform boundaries and whether signing or build credentials
   could be involved.
5. Patch and test on the oldest affected line, rotate/revoke credentials if applicable, and prepare
   coordinated disclosure. Never move or reuse a released tag.
6. Record containment, eradication, recovery and lessons learned without copying sensitive input.

## Release supply chain

CI produces reproducible CycloneDX inventories for the locked npm and Cargo graphs, rejects high or
critical production npm findings, and runs a pinned Rust advisory scan on pull requests and weekly.
The known `glib` advisory is ignored only for the exact internal draft PR while Linux remains an
unsupported compatibility monitor. GitHub Actions are commit pinned and checkout does not persist
credentials. These are inventory and pull-request controls, not release provenance.

Before distribution, a dedicated protected repository must bind each signed platform artifact to
its source revision, lockfiles, SBOM, build identity and provenance attestation. Signing/notarization,
hardware-backed key custody, updater/store policy, reproducible-build comparison and key-loss/
revocation drills remain release gates because they require credentials, infrastructure and named
owners that this evaluation does not have.

## Synthetic canary drill

Before each promotion gate, seed unique fake names and byte strings, exercise create/import/lock/
background/export/delete/failure/crash flows, and inspect app storage, platform backups/indexes,
recent files, logs, crash artifacts, swap/app snapshots, clipboard, notifications and generated
support artifacts. Any canary outside the explicitly selected plaintext export destination fails the
gate. Physical-device evidence must record OS/app version, exact build hash, steps and artifact hash.
