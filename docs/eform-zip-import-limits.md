# eForm ZIP import limits

The eForm Manager ZIP importer uses generous resource ceilings to stop egregious
resource exhaustion while accepting large images and bulk form packages:

| Resource | Limit |
| --- | --- |
| Expanded bytes in one entry | 256 MiB (268,435,456 bytes) |
| Expanded bytes across the archive | 1 GiB (1,073,741,824 bytes) |
| ZIP entries, including directories and archiver metadata | 10,000 |
| Expanded bytes in one properties or form HTML entry | 16 MiB (16,777,216 bytes) |
| Expanded bytes across properties and retained form HTML | 64 MiB (67,108,864 bytes) |

These are abuse backstops, not recommended image sizes. Large images, image-heavy
forms and multi-form packages within all applicable ceilings must import successfully;
users should not need to downsample ordinary assets or split ordinary packages.
Assets stream to private staging files using a fixed-size copy buffer. The larger
ceilings bound disk expansion. The separate text ceilings bound properties parsed
and HTML retained in memory, including HTML whose filename has a non-HTML extension.
Properties are staged and checked before parsing; all retained HTML is checked
before any asset or form is published. Properties from skipped forms also count.
They apply to bytes actually decompressed, independently of ZIP size metadata or
the compressed upload size. The eForm importer has no compression-ratio limit:
high compression alone is not grounds for rejection.
Skipped macOS metadata and directory payloads consume the same budgets. Exact limits
are accepted; the next byte or entry rejects the entire archive with an I/O error.

Extraction and budget validation finish before any forms are registered or assets
published. Rejection closes the archive and removes that import's private staging
directory. Existing forms, images and other concurrent imports remain untouched.
Path containment validation still applies to every entry, including skipped entries.

Limits are defined beside `EFormExportZip.importForm`. If a supported real-world
package exceeds these generous ceilings, review the budgets against that use case
and available memory and disk rather than treating large images as abuse.

## Demographic imports

On `release/2026.08`, the former demographic `unzipFile` workflow has been replaced
by the CDS snapshot pipeline. It already counts actual expanded bytes and entries
before publishing a usable snapshot or applying patient updates. Its per-member
byte ceiling is `cds.import.maxSourceBytes`; the aggregate ceiling is
`cds.import.maxExpandedBytes`. Directory payloads are rejected, and compression-ratio,
disk-reserve and duration limits also apply. See [CDS configuration](cds-import-configuration.md).

Failed CDS attempts retain private, unsealed evidence within their durable job
root under the existing recovery/retention policy. This evidence cannot be loaded
as an accepted snapshot. It is distinct from the temporary eForm extraction tree,
which is removed immediately on rejection.
