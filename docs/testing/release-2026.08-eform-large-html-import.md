# Large eForm HTML ZIP round trips (#6018)

The release exporter accepts supported HTML above 16 MiB, but its ZIP importer
rejects those same bytes. Three actual export/import regressions reproduce the
failure: 17 MiB HTML, 17 MiB HTML with a non-HTML filename, and exactly 50 MiB
HTML. Each fails before repair with `ZIP import text byte limit exceeded`.
The corrected shared harness is used on both sides; its original cleanup
assertion assumed an unmerged publication-lock implementation and was corrected
to the actual release's empty-staging contract before qualification.

The importer now allows 50 MiB per retained HTML entry.
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
the newly repaired importer still requires the next rebuilt-DEB qualification.

All 8,111 complete Node cases pass across 570 test files on Node 22.23.5,
with no failures, skips, cancellations or TODOs and all test-source fingerprints
verified. The tracked case inventory comes from that actual execution. Node
report SHA-256: `a622abac48d99d12fa96845233e30296f8984a8437f73aa06268ed47661b5a01`.

Review follow-up: publication and registration now share one process-wide
critical section across importer instances, bounding simultaneous retained-HTML
loads. Extraction remains concurrent and disk-backed. Loading no longer creates
a growing byte buffer plus its full-size byte-array copy. The form-name check
is repeated under the registration lock, so another local import that finished
during extraction is skipped before HTML loading or asset publication. This
does not introduce a database uniqueness migration or claim cross-process
atomicity for unrelated writers.

Two concurrent-import regressions fail on the published pre-review source
(JUnit SHA-256 `434e42f815330659551e2cb93abc514b2b9546c7019f08bdb97fe7317d1dd841`).
All 81 selected cases pass after repair, including distinct-name handoff and
same-name rejection (JUnit SHA-256
`051d598a66cdb6ed7d492e74704f8dd172e26647f8b1bacfbf10949c8a2635ef`).
Fresh floors rise to 99.1597% / 99.4690% / 93.1034%.

The current release migration
`V1.0.59.27__preserve_oscar19_text_capacity.sql` upgrades `eform.form_html`
to LONGTEXT. The installed MariaDB information schema independently confirms
LONGTEXT with a 4,294,967,295-byte capacity, rather than the historical
MEDIUMTEXT baseline. The existing 50 MiB compressed multipart request ceiling
remains independent of expanded HTML and archive budgets: compression, other
entries and framing must fit that request ceiling. This change does not claim
that every arbitrarily large exported archive fits a single upload.

Review source-bound evidence SHA-256:
`529924bcbec3ffa1b05036c07b9e140a01a588cb2df261885e591a7b55e23f21`. The final complete Node retake also passes all 8,111 cases across 570 files,
with zero failures, skips, TODOs, cancellations or changed test sources.

The final cleanup assertions also accept the cooperating publication lock
implementation: only an empty regular `.publication.lock` file may remain;
symlinks and all extracted files/directories are refused. The public-source
81-case rerun passes (JUnit SHA-256
`102d7c3e101647a056a3e2bf2433cd9e24bf952b7971e046c6794839c03342a1`).
Fresh public-source evidence SHA-256: `02b553ca17f64cacdbb2604ed4ee8f6b14080fa1432210df13cb0efb1ab67757`.
The original combined run with five cleanup-expectation failures is retained;
the corrected composed-source retake passes all 131 cases, preserving the
existing asset lease and rollback controls. Its JUnit SHA-256 is
`aa2678094046547b26b22839680733ec63630878cc19d9bb053793825a601603`.
