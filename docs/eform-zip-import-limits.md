# eForm ZIP import limits

The eForm Manager ZIP importer accepts at most:

| Resource | Limit |
| --- | --- |
| Expanded bytes in one entry | 16 MiB (16,777,216 bytes) |
| Expanded bytes across the archive | 64 MiB (67,108,864 bytes) |
| ZIP entries, including directories and archiver metadata | 1,024 |

These fixed limits allow multi-form packages and their image assets while bounding
the HTML and properties subsequently loaded into memory. They apply to bytes actually
decompressed, independently of ZIP size metadata or the compressed upload size.
Skipped macOS metadata and directory payloads consume the same budgets. Exact limits
are accepted; the next byte or entry rejects the entire archive with an I/O error.

Extraction and budget validation finish before any forms are registered or assets
published. Rejection closes the archive and removes that import's private staging
directory. Existing forms, images and other concurrent imports remain untouched.
Path containment validation still applies to every entry, including skipped entries.

Split oversized multi-form packages into smaller imports. Reduce any individual
form or image that exceeds the per-entry limit. Limits are defined beside
`EFormExportZip.importForm`; raising them requires reviewing memory and disk use.

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
