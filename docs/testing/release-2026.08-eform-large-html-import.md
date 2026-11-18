# Large eForm HTML ZIP round trips (#6018)

The release exporter accepts supported HTML above 16 MiB, but its ZIP importer
rejects those same bytes. Three actual export/import regressions reproduce the
failure: 17 MiB HTML, 17 MiB HTML with a non-HTML filename, and exactly 50 MiB
HTML. Each fails before repair with `ZIP import text byte limit exceeded`.
The corrected shared harness is used on both sides; its original cleanup
assertion assumed an unmerged publication-lock implementation and was corrected
to the actual release's empty-staging contract before qualification.

The importer now allows 50 MiB for retained HTML, matching upload capacity.
Properties retain their separate 16 MiB limit. The 64 MiB aggregate text ceiling,
256 MiB streamed-entry ceiling, 1 GiB expanded-archive ceiling, entry count,
path checks, prepublication validation and cleanup remain enforced. HTML is
identified by its metadata reference, including non-HTML filename extensions.
Bulk packages exceeding 64 MiB of retained text still require splitting.

All 79 selected Java 25 cases pass with zero failures, errors or skips, including
actual byte-exact Unicode export/import, exact HTML-limit acceptance, one-byte
overflow rejection before publication, metadata/aggregate limits, path traversal,
archiver metadata, collisions and concurrent imports. Source-bound changed-line
floors are preserved or raised to 99.0566% lines, 99.4083% instructions and
92.3077% branches.

Pre-repair three-case JUnit SHA-256:
`5218912d4ef92b3364eda08d4949ca98c3b2271822231b731caf29c9894c10d5`.
Post-repair 79-case JUnit SHA-256:
`b04ea06944cc0c95445188ab412b48443817df768c4a1f1a26672c17186ef05b`.
Coverage evidence SHA-256:
`9c4d7b59cf285e9e54c59f364f2fe6a3591b2fe2aab85c9e6251c27a3f77a89e`.

`eform-large-html-roundtrip` is a manual native check. It creates one owned
synthetic 17 MiB template without patient instances, exports it through Manage
eForms, removes the source and imports the downloaded ZIP. It checks exact byte
length and SHA-256 without printing the large template, verifies unrelated rows,
and removes only its verified fixtures. Installed native qualification is pending;
this document does not claim the newly repaired importer is already packaged.

All 8,111 complete Node cases pass across 570 test files on Node 22.23.5,
with no failures, skips, cancellations or TODOs and all test-source fingerprints
verified. The tracked case inventory comes from that actual execution. Node
report SHA-256: `a622abac48d99d12fa96845233e30296f8984a8437f73aa06268ed47661b5a01`.
