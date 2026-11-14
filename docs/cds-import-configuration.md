# CDS import configuration

Admission defaults to disabled. These are server-owned Java properties, read by
`cdsImportProperties` and the trusted CLI configuration, not upload parameters. Rehearse the
exact deployed artifacts and complete the [operator](cds-import-operator-guide.md) and
[maintenance](cds-maintenance-admission.md) prerequisites before enabling imports.

The CLI currently reads its trusted configuration file and overlays runtime settings; omitted
application properties must not be assumed to inherit from the WAR. Its synchronous runtime
forces `cds.import.worker.enabled=false`. Numeric settings require positive values unless a
stricter range is stated. MiB and GiB mean binary units.

No setting limits how large an import is or how long it runs. An import stops for its size only
when its storage would actually run short (the free-disk reserve below), and for time only when
its worker makes no progress for the stall window; both stop it loudly, under their own codes,
and the same run resumes once the cause is fixed. A slow but working database never stops it
either: no import transaction carries a clock, and a read the server ends at its deadline is sent
again in the same transaction ([statement deadlines are waits](cds-import-jdbc-timeouts.md#statement-deadlines-are-waits)).

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
| `cds.import.drainTimeoutSeconds` | `30` | How long entering maintenance waits, in seconds, for clinical transactions already admitted to finish while new clinical work is refused. It bounds how long the clinic is held out, not how slow the database may be: when it passes, the attempt ends with `MAINTENANCE_ACQUISITION_FAILED` before any unit and the run is resumed later. |
| `cds.import.cliHeapMiB` | `512` | CLI heap policy, integer MiB of at least `128` (no upper limit in CARLOS or `carlos-ctl`; the engine reads it as a Java int); launcher and engine must agree. |
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

Replace placeholders with deployment-owned identifiers. Comma-separated lists are trimmed and
ignore empty entries; a list may be of any length, so an operator can be delegated any number of
providers. A source namespace is a token of up to 128 characters (a letter or digit, then
letters, digits, `.`, `_` or `-`), the width of the run and lineage columns that store it.

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

## Disk reserve and stall window

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.minimumFreeDiskBytes` | 512 MiB | Free space every import writer leaves on its store: the staged snapshot, the parse and plan spools, the published documents and the browser upload store. |
| `cds.import.maxDurationMillis` | `86400000` (24 hours) | The stall window: how long the import worker may reach no checkpoint before it is treated as dead. Not a time limit; see below. |

**Disk reserve.** Every writer checks the reserve as it starts writing and again after every
16 MiB it writes (a browser upload before every chunk). Reaching it stops the import with the writer's own code:
`CDS_INPUT_DISK_RESERVE` (snapshot and parse), `PLANNING_DISK_RESERVE` (plan spools),
`DOCUMENT_PUBLICATION_DISK_RESERVE` (document store) or `UPLOAD_DISK_RESERVE_REFUSED` (HTTP 507,
browser upload). Nothing half-written is published: a stopped snapshot stays an unsealed
`.pending` directory kept as failed-attempt evidence, and a refused chunk is not stored. Free
space (remove what the clinic no longer needs, or enlarge the store), then resume the same run
or upload. This is the only thing that stops an import for its size, so an archive of any size,
entry count or compression ratio is accepted while it fits. A decompression bomb is one such
archive: it is expanded as a stream through fixed buffers, so it cannot exhaust memory, and it
stops at the reserve. Its partial copy is kept as failed-attempt evidence, like any other, and
occupies the store until it is removed under the clinic's retention policy (see the
[recovery runbook](cds-import-recovery-runbook.md)); a resume of that run would stop at the
reserve again, so cancel it instead. Running out of inodes is reported as an I/O failure of the
step that needed one (`CDS_SOURCE_SNAPSHOT_FAILED` for the snapshot).

**Stall window.** `cds.import.maxDurationMillis` keeps its name but no longer limits how long an
import runs. Every bounded read or write of the snapshot, every parse callback, planned row,
preparation step, verification read and clinical-unit callback is a checkpoint, and the window
bounds only the time since the last one. An import that keeps making progress is never stopped,
however long its source takes. One window covers the whole attempt, from snapshot to the last
unit, so no stage starts a fresh clock that could hide a silent gap; report generation has its
own window under the same setting. A worker that was silent for the whole window, because one
read, lock or statement never answered, stops at its next checkpoint with `IMPORT_WORKER_STALLED`
(report generation: `IMPORT_REPORT_GENERATION_STALLED`, a nonblocking warning), the same way the
exporter ends a silent attempt. A stall is final for that attempt; committed units, holds and
receipts are kept and the same run resumes with a fresh window. A worker that never returns
leaves its coordinator and maintenance sessions silent: each sets the database session idle
timeout (`wait_timeout`) to the window plus a 300-second unwind allowance, after which the
database closes it and releases its lock exactly as for a dead process, and a resume can take
over. A statement that only waits for the database is not a checkpoint: its waits are sent
again with doubling deadlines and end with the same code once the window has passed. Set the
window well above the slowest single step; minutes, not milliseconds. The window must not exceed
about one year (MariaDB's largest `wait_timeout`).

## XML input guards

These protect the parser's memory, which they keep from growing with anything but the schema.
Each counts only what the pinned schema fixes (the element and attribute names, their namespaces,
the nesting and the attributes), so a genuine CDS 5.2 file, from CARLOS or another vendor and of
any size, does not reach it: the size of a file only adds more of the same records. What the
schema leaves to the writer is not counted. In particular a writer may declare its namespace
prefixes as it likes (all on the root, the same prefix again on every element, or a new prefix
for every declaration): only the prefixes in scope at one time and the namespace URIs are
counted, and names are counted by namespace and local name, so prefixes do not multiply them.
Earlier builds counted every prefix and every prefixed name a document declared, which a
schema-valid file whose writer invents a new prefix per declaration could pass.

| Property | Default | Guards | Why a genuine file cannot reach it |
| --- | --- | --- | --- |
| `cds.import.maxXmlDepth` | `256`; minimum `4` | Element nesting held on the parser stack. | The schema's deepest path is 8 elements from the root. |
| `cds.import.maxScalarCharacters` | `65536`; minimum `65536` | One schema-constrained value held for validation, and one start tag, comment or processing instruction, which the parser holds whole. | The longest length-restricted schema type is `text64K` (65,536 characters), so a longer constrained value is schema-invalid. Unrestricted text, numbers and base64 binary values are not counted: they stream to the value spool, so a note or report of any length is imported. Start tags carry only the schema's short attributes; CARLOS writes one short header comment. |
| `cds.import.maxPathCharacters` | `8192`; minimum `256` | One archive member path or record-relative XML path. | Record paths are at most 8 short schema names with ordinals; member paths are file names within the export. |
| `cds.import.maxNamespaceAliases` | `128` | Namespace prefixes in scope at one time (declared on the current element or an ancestor), and distinct namespace URIs in one document. Prefixes declared and gone out of scope are not counted. | A CDS file uses three namespaces (`cds`, `cds_dt` and the XML Schema instance), declared on the root or redeclared on any element. Only a file that declares more than 128 prefixes on one element path, or more than 128 namespaces it does not use, reaches it. |
| `cds.import.maxUniqueXmlNames` | `4096` | Distinct names in one document, by namespace and local name (prefixes do not multiply them), and processing-instruction targets. | The schema defines a few hundred element and attribute names, whatever prefixes a writer gives them. |
| `cds.import.maxXmlNameCharacters` | `256`; minimum `64` | One qualified name or prefix. | The schema's longest name is under 50 characters. |
| `cds.import.maxNamespaceUriCharacters` | `4096`; minimum `128` | One namespace URI. | The schema's URIs are `cds`, `cds_dt` and the 41-character XML Schema instance URI. |

Reaching one of these refuses the file with its code (`CDS_XML_DEPTH_LIMIT`,
`CDS_XML_SCALAR_RESOURCE_LIMIT`, `CDS_XML_MARKUP_LIMIT`, `CDS_XML_PATH_LIMIT`,
`CDS_SOURCE_MEMBER_PATH_INVALID`, `CDS_XML_NAMESPACE_RESOURCE_LIMIT`,
`CDS_XML_NAME_RESOURCE_LIMIT`) rather than truncating source data. The schema identity-table
guards (16,384 identity values and 1,048,576 characters per XML), 128 attributes and 128
distinct child names per element are fixed in `CdsInputLimitsDto` and `CdsXmlParser`, not
configurable; the schema declares no identity types and far fewer attributes and children.

## Retired settings

These no longer do anything. Each is accepted, whatever its value, and ignored; the first
import or upload of a process logs one WARN naming the ones still set, never their values:
`CDS_IMPORT_RETIRED_SETTINGS_IGNORED settings=[...]`. Remove them when convenient.

| Retired property | Former default | What replaced it |
| --- | --- | --- |
| `cds.import.maxSourceBytes` | 64 GiB | The disk reserve, checked as the source and each member are copied. |
| `cds.import.maxExpandedBytes` | 512 GiB | The disk reserve, checked as the archive is expanded and the spools grow. |
| `cds.import.maxEntries` | `2000000` | Nothing: entries, patients and course copies are kept on disk and in the database, one at a time, so memory does not grow with their number. |
| `cds.import.maxBinaryBytes` | 64 GiB | The disk reserve, checked as a decoded value is spooled. |
| `cds.import.maxCompressionRatio` | `200` | The disk reserve: a highly compressed genuine member (a blank scanned page) is accepted, and a decompression bomb stops at the reserve. |
| `cds.import.upload.maxFileBytes` | 64 GiB | The upload store's disk reserve, checked before every chunk. |
| `cds.import.upload.maxTotalBytes` | 256 GiB | The upload store's disk reserve, checked before every chunk. |
| `cds.import.upload.maxFiles` | `20000` | Nothing: any number of uploads may wait for their runs' snapshots. A store out of space refuses at the disk reserve, and one out of inodes fails the step that needed one. |

## Browser upload

No upload size, total or count refuses a browser upload: a file of any size is sent in chunks and
stored until the upload store reaches its free-disk reserve, when the chunk is refused
(`UPLOAD_DISK_RESERVE_REFUSED`, HTTP 507) and the same upload resumes once space is freed. Any
number of uploads may be begun and wait for their runs' snapshots (one XML file per patient is
one upload per patient). A store that runs out of inodes fails the step that needed one
(`UPLOAD_STORAGE_UNAVAILABLE`, HTTP 503).

| Property | Default | Meaning |
| --- | --- | --- |
| `cds.import.upload.maxChunkBytes` | 4 MiB | Transfer unit, 64 KiB to 16 MiB, also advertised to the browser. A chunk is never failed for the time it takes (see below), so a slow link needs no smaller chunk. |
| `cds.import.upload.ttlHours` | `24` | The unsubmitted upload's stall window: hours an upload that is neither submitted nor sent a chunk may stay idle before cleanup abandons it and removes its bytes. Every stored chunk restarts the interval, and the import page's upload list shows, for each unsubmitted upload, when it will be removed ("Removed at ..."). A submitted upload is never removed for idleness. |

A chunk is given up only when it stops moving, never for its total time. The server fails a chunk
(`UPLOAD_CHUNK_STALLED`, HTTP 408) only when two minutes pass without one of its bytes arriving.
The page sends each chunk with `XMLHttpRequest`, restarts the same two-minute wait on every upload
progress event and every part of the reply, and aborts only when the wait runs out. Either way the
page sends the same offset and hash again, up to three attempts; after that the upload is resumed
from the page's upload list. The window is fixed in `CdsUploadService.CHUNK_STALL_MILLIS`, not a
setting, and the page is given the server's value. Behind the packaged proxy a request body that
stops for 15 seconds is ended sooner by nginx (`client_body_timeout`, time between two reads,
not the whole transfer) and retried the same way. The page's other requests carry small JSON
replies and are abandoned after 30 seconds in which no part of the reply arrived.

Ordinary database, document-store and application settings remain necessary. Credentials retain
their exact configured values and must never appear in public evidence. See
[JDBC timeout verification](cds-import-jdbc-timeouts.md) and the
[installed CLI gate](cds-import-cli-verification.md).
