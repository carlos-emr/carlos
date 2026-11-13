# CDS import configuration

Admission defaults to disabled. These are server-owned Java properties, read by
`cdsImportProperties` and the trusted CLI configuration, not upload parameters. Rehearse the
exact deployed artifacts and complete the [operator](cds-import-operator-guide.md) and
[maintenance](cds-maintenance-admission.md) prerequisites before enabling imports.

The CLI currently reads its trusted configuration file and overlays runtime settings; omitted
application properties must not be assumed to inherit from the WAR. Its synchronous runtime
forces `cds.import.worker.enabled=false`. Numeric settings require positive values unless a
stricter range is stated. MiB and GiB mean binary units. Raising capacity requires measurement.

## Admission, storage and runtime

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.enabled` | `false` | Admit imports/resume. Disabling admission does not release existing holds or maintenance. |
| `cds.import.maintenanceGuardEnabled` | `false` | Application JDBC writer guard and bounded connections. Required for execution; admission alone still protects import metadata. |
| `cds.import.maintenanceMode` | `false` | Explicit permission to acquire a maintenance window, separate from guard readiness. |
| `cds.import.clinicalZone` | empty | Required IANA zone when enabled; its rules must match the web JVM zone. The CLI selects its one-shot process zone before Spring startup. |
| `cds.import.allowedRoot` | none | Absolute root for trusted CLI source files. |
| `cds.import.stagingRoot` | none | Absolute private engine staging/report/evidence root, also used for CLI diagnostics. |
| `cds.import.uploadRoot` | none | Absolute private browser upload root. |
| `cds.import.drainTimeoutSeconds` | `30` | Maintenance admission/drain SQL timeout, seconds. |
| `cds.import.cliHeapMiB` | `512` | CLI heap policy, integer MiB from `128` to `16384`; launcher and engine must agree. |
| `cds.import.worker.enabled` | `true` | Web durable-work dispatcher; forced off in the CLI. |
| `cds.import.preacceptRetentionMillis` | `86400000` | Abandoned preaccept staging retention, milliseconds; live accepted work is preserved. |
| `cds.import.approvedTriggerManifest` | empty | Approved trigger manifest for target safety checks. Empty does not approve arbitrary triggers. |
| `cds.import.native.maxNoteBytes` | `262144` | Native history-note UTF-8 byte limit; included in normalization provenance. |
| `cds.import.mapping.restoreArchivedAllergies` | `false` | `true` restores an allergy carrying `CARLOS.Allergy.Archived.v1=true` as a native archived allergy. The default imports it active with a `CDS_ALLERGY_ARCHIVED_SOURCE_MARKER` warning; when enabled each restored allergy gets `CDS_ALLERGY_ARCHIVED_SOURCE_RESTORED`. Any other value refuses. Part of the normalization policy digest (fixed per run) and recorded in each prepared allergy. |

Roots must pass engine ownership, permissions, filesystem and no-symlink checks. A pathname
alone does not establish readiness. Originals/journals are mode `0600`; verified published
documents are mode `0640` in the document-store group.

## Native-baseline witness key

Each committed import unit stores a field witness of every native row it wrote
(`cds_import_unit.native_baseline`, copied to `cds_import_checkpoint`), which the exporter
uses to tell which native columns changed since import. Its per-column SHA-256 and byte
length are reversible for low-entropy PHI, so the witness is stored only sealed with
AES-256-GCM under a per-installation key held outside the database. New witnesses are deflated
before they are sealed (protocol 4, about a quarter of the uncompressed size); witnesses sealed
uncompressed by earlier builds (protocol 3) still open. The import and export
(web and both CLIs) read the same two properties:

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.nativeBaseline.keyRoot` | none | Absolute directory holding `<keyId>.key` files. |
| `cds.nativeBaseline.activeKey` | none | Key ID (`[A-Za-z0-9][A-Za-z0-9_-]{0,63}`) that seals new witnesses. |

Provision it like the CDS export master key: each key file holds exactly 32 random bytes, is
owned by root and readable by the service group only (for example `root:carlos 0640` below a
root-owned `0750` directory); a group- or world-writable file or ancestor, a symlink or a
writable service-owned file is refused. For example:

```bash
sudo install -d -o root -g carlos -m 0750 /etc/carlos-emr/cds-baseline-keys
sudo sh -c 'umask 0137; head -c 32 /dev/urandom > /etc/carlos-emr/cds-baseline-keys/k2026a.key'
sudo chown root:carlos /etc/carlos-emr/cds-baseline-keys/k2026a.key
# carlos.properties
cds.nativeBaseline.keyRoot=/etc/carlos-emr/cds-baseline-keys
cds.nativeBaseline.activeKey=k2026a
```

An import attempt refuses before any claim or clinical write with
`NATIVE_BASELINE_KEY_UNAVAILABLE` when the active key is missing or unreadable; a unit never
commits an unsealed witness. Each witness records its key ID: to rotate, add a new key file and
change `activeKey`, and keep retired key files while their imports may still be exported.
Removing every key makes old witnesses unreadable; the exporter then fails with
`CDS_EXPORT_NATIVE_BASELINE_KEY_UNAVAILABLE` rather than guess. Escrow the key files with the
other installation secrets: they are deliberately not in the database or its backups. Packaged
installs need `carlos-ctl` to provision the directory and an initial key (a companion change).

## Source registration and delegation

Replace placeholders with deployment-owned identifiers. Comma-separated lists are trimmed,
ignore empty entries and are limited to 8192 characters.

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.sourceNamespaces` | empty | Browser source choices; each also needs profile registration. |
| `cds.import.source.<namespace>.profiles` | empty | Allowed source profiles: `pcon-2024-02` and/or `omd-5.2`. |
| `cds.import.source.<namespace>.synthetic` | `false` | Synthetic source for the separately authorized course-copy workflow. |
| `cds.import.operator.<operator>.providers` | empty | Explicit CLI provider delegation; active-provider and domain checks still apply. |
| `cds.import.operator.<operator>.facilities` | empty | Optional facility restriction; a nonempty list requires a matching selected facility. |
| `cds.import.operator.<operator>.programs` | empty | Optional program restriction with the same selection rule. |
| `cds.import.course.<courseId>.educators` | empty | Authorized educator provider numbers; course-copy also requires synthetic source, active course, membership and `OSCAR_LEARNING=yes`. |

The normalization digest includes configured `cds.import.native.*`, `cds.import.mapping.*`,
`cds.import.normalization.*` and selected `cds.import.source.<namespace>.*` keys, excluding the
source's `profiles` list. This fingerprints policy; unused keys do not become mapping switches.
The JVM calendar zone and `hibernate.jdbc.time_zone` also contribute to the digest.

## Input bounds

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.maxSourceBytes` | 64 GiB | Maximum source bytes. |
| `cds.import.maxExpandedBytes` | 512 GiB | Maximum expanded archive bytes. |
| `cds.import.maxEntries` | `2000000` | Maximum archive entries. |
| `cds.import.maxXmlDepth` | `256` | XML depth; minimum `4`. |
| `cds.import.maxScalarCharacters` | `65536` | Bounded scalar characters; minimum `65536`. Large supported values use streaming. |
| `cds.import.maxBinaryBytes` | 64 GiB | Maximum decoded binary bytes. |
| `cds.import.minimumFreeDiskBytes` | 512 MiB | Free disk reserve during staging and upload. |
| `cds.import.maxDurationMillis` | `86400000` | Attempt duration, milliseconds; also sizes private ownership-session idle timeouts plus an unwind allowance. |
| `cds.import.maxPathCharacters` | `8192` | Source path characters; minimum `256`. |
| `cds.import.maxCompressionRatio` | `200` | Finite expanded/compressed ratio; minimum `1`. |
| `cds.import.maxNamespaceAliases` | `128` | XML namespace aliases. |
| `cds.import.maxUniqueXmlNames` | `4096` | Distinct XML names. |
| `cds.import.maxXmlNameCharacters` | `256` | XML name length; minimum `64`. |
| `cds.import.maxNamespaceUriCharacters` | `4096` | Namespace URI length; minimum `128`. |

Bounds refuse work rather than truncate source data. Schema identity-table bounds are fixed
in `CdsInputLimitsDto`, not configurable properties.

## Browser upload capacity

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.upload.maxChunkBytes` | 4 MiB | Chunk bytes, also advertised to the browser. |
| `cds.import.upload.maxFileBytes` | 64 GiB | Reserved bytes for one upload. |
| `cds.import.upload.maxTotalBytes` | 256 GiB | Total reserved upload bytes. |
| `cds.import.upload.maxFiles` | `20000` | Reserved upload count. |
| `cds.import.upload.ttlHours` | `24` | Upload expiry interval, hours, used by cleanup. |

Ordinary database, document-store and application settings remain necessary. Credentials retain
their exact configured values and must never appear in public evidence. See
[JDBC timeout verification](cds-import-jdbc-timeouts.md) and the
[installed CLI gate](cds-import-cli-verification.md).
