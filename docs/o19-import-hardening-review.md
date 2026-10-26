# OSCAR 19 import hardening review

Implemented for the `release/2026.08` schema line and CLI 1.2.1. This review
records the completed implementation and local verification; it is not a
clinic cutover sign-off. The supplied evidence was an alpha-test log, not a
clinic SQL dump. The operator procedure remains [o19-import-deb.md](o19-import-deb.md).

## Import approach

Restore source definitions into isolated staging, then copy into the existing
Flyway-created application tables. Source collations, engines and indexes do
not become application configuration. Preserve unmapped records and fields in
the documented archives; validate the complete application schema before and
after copying. History checks alone cannot detect an altered index or column.

- Recognize MariaDB's omitted `utf8_*` aliases under explicit historical
  `UTF8_IS_UTF8MB3` restore semantics. Both the head and streaming scans use a
  bounded SQL lexer that tracks quotes, comments and executable comments.
  Clinical strings containing SQL words remain data; literals are never rewritten.
- Block notes marked locked. An unlocked note with a placeholder password no
  longer blocks; a locked note stays protected even if its password is `NULL`
  or the literal string `NULL`, which can be a real password.
- Accept complementary full SQL parts, including transactional/MyISAM splits.
  Restore identical table overlaps once; refuse conflicting overlaps. Native
  nested backup layouts retain auxiliary artifacts. Explicit `o19-inputs.json`
  resolves ambiguous roles and declares full/incremental document order.
  Later document entries replace earlier paths; deletions require reconciliation.
  All parts must describe the same frozen source state.
- Snapshot SQL, documents and properties privately. Bind the ordered components,
  ETL contract and target schema contract to resume. Retire provenance with the
  completed run; remove raw snapshots only after verified preservation. Routine
  backups retain ledgers and clinical exports while excluding raw inputs.
- Use target types and collations for key comparisons, merge partitions and ID
  maps. Verify signedness, capacity, numeric precision, stored values and
  keyless-table multiplicity before completion checkpoints. Check remapped IDs
  after their parent maps exist. Reject SQL conversion diagnostics rather than
  recording a successful partial copy.
- Guard acknowledged charset repairs before speculative conversions. Correct
  accents and supplementary characters pass through; binary validation avoids
  doubling large notes through HEX. Mixed or repeatedly encoded text that cannot
  round-trip remains a blocker requiring source remediation.

## Collations, Hibernate and indexes

The explicit policy is [database-collation-policy.md](database-collation-policy.md)
and `database/mysql/collation-policy.json`.

`utf8mb4_uca1400_ai_ci` applies to eight reviewed human-text columns. Appointment
status codes use `utf8mb4_bin` because `C/c` and `H/h` have different meanings.
Technical identifiers and database/connection defaults retain explicit
`utf8mb4_general_ci` compatibility. Existing widths, nullability, defaults and
comments are preserved; complete affected unique keys are checked for collisions
before persistent column changes.

Flyway owns these per-column definitions. Spring/Hibernate runs with schema
generation disabled and respects them. Java 25, Spring 7 and Hibernate 7 tests
confirmed case-sensitive status queries and supplementary-character round trips.
Connector/J 9.6 selected a different collation when UCA14 was requested directly
in its URL; keeping the compatible connection setting is intentional.

Forward migrations **V1.0.59.5** and **V1.0.59.6** correct the collations and
reconcile 47 reviewed performance-index definitions across the provincial sets,
including order, prefixes, uniqueness, type and ignored status. Redundant
provider/demographic extension indexes are removed only when their complete
replacement covers them. Unexpected unique indexes block repair before other
repairs begin. Existing primary/unique constraints and hinted index names remain.
These are canonical DAO-supported decisions; workload-specific optimality still
requires representative clinic query plans.

Committed physical contracts describe all managed columns, defaults, indexes,
constraints and database defaults. Fresh installs, upgrades and imports use the
same contract. Package builds verify its binding to the exact migration bytes in
the WAR. Final import verification checks all declared foreign keys and refreshes
optimizer statistics. Original archive definitions are outside the application
contract.

Additional rehearsal findings corrected the retired worksheet's pristine seed
counts and the BC WorkSafeBC catalog merge rulings. CI now checks actual fresh
copy/merge seed expectations as well as physical definitions. Legacy notes without
a revision UUID display their recorded author; blank UUIDs never group unrelated
notes into a shared history. Imported note values are not rewritten for this.

## Verification completed

| Check | Result |
|---|---|
| CLI suite | 1,642 tests passed with current application contracts and MariaDB 11.8.9; standalone fixture suite also passed |
| Installed CLI regressions | 42 tests, one optional source-checkout check skipped; restore, ETL, schema and cleanup cases passed |
| Application manifest/generator suite | 253 tests passed |
| Real MariaDB collation/index migration cases | 5 passed, including collision guards, complete-definition repair and constraint preservation |
| Java 25 legacy-note regression cases | 4 passed; rebuilt WAR successfully |
| Package/release source checks | 99 passed; released migration files unchanged |
| Fresh versus upgraded schema | ON and BC contracts match on MariaDB 11.8.6 / 16 KiB and 11.8.9 / 32 KiB |
| Complete fixture imports | ON: 518 source-table digests and 1,146 row-parity checks; BC: 546 digests and 1,204 row-parity checks; final verification passed |
| Browser checks | Both provinces: 9 passed, one expected day-schedule check skipped for a program-10016 appointment; appointment history passed |
| Actual package upgrade | Both pending forward migrations applied; wrong/redundant indexes repaired; complete canonical schema restored; fingerprints of ten affected clinical tables unchanged |
| Backup and rollback | Post-import backup and restore drill passed; actual pre-import DB/configuration/document rollback restored the stock target and safely retired its pre-ETL workspace |
| Input lifecycle | Changed properties refused before ledger/data changes; live backup exclusions and verified cleanup confirmed; nested purge normal/fallback tests passed |
| Debian artifacts | Ubuntu 26.04 CLI and main application packages built and installed; lintian error gates passed |

The fixtures were generated from the pinned OSCAR source commit
`a7900d569d3faf741993e5e1da8c14021bbefede` with the repository's synthetic/public
clinic data. Native variants included two SQL parts with one identical overlap,
a source-only index, legacy collation aliases, unlocked placeholder note passwords,
auxiliary artifacts and fresh source digests. The document fixture intentionally
omits files: it correctly blocked, then resumed after supplying synthetic test
files. No real missing clinic document was fabricated or recovered.

## Delivery and rollout

The coordinated CLI commit is pinned in `debian/carlos-ctl.pin`; its release
version is 1.2.1 and the application dependency floor matches. Main application
artifact version: `2026.09.0~snapshot27`. The complete Chromium renderer is
included; the optional DrugRef service package was not built in this rehearsal.

The new migrations occupy the release line's reserved subversions below the
`develop` floor. Existing published migrations were not edited. Publishing the
CLI commit/tag and coordinated application release remains a release action;
the artifacts and commits produced here are local review results.

Before a clinic cutover, rehearse that clinic's frozen SQL/document backup set,
review every acknowledgement, verify backup recovery and examine the reports
and charts. Stop writers and take a verified backup before applying the forward
DDL. Partial imports must finish with their original contracts or roll back;
changing ETL rules halfway through a recorded copy is refused.
