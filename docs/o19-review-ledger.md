# OSCAR 19 importer — the M24 review ledger

Every finding from the multi-agent review of the importer's end state, with
its verification verdict and where it was fixed. It exists because the review
was the largest single input to this feature's correctness and a summary
sentence ("N findings, all addressed") is not auditable: a reader who wants to
know whether a specific defect was real, and whether it is gone, should be
able to answer that from this file and `git show`.

## How the review was run

Seventeen dimensions — nine vertical slices of the feature and eight
cross-cutting concerns — were reviewed in parallel against the tree at
`008acdc4`. Each finding was then put to two independent agents: a **second
opinion** and an **adversarial refuter** told to try to refute it and to
default to "refuted" when uncertain.

| | |
|---|---|
| findings raised | **89** |
| survived verification | **48** |
| refuted | **17** |
| never got two verdicts | **24** |
| severity | 1 critical, 18 high, 40 medium, 30 low |

## What "refuted" turned out to mean

Two different things, and the split is the most useful number in this file:
**9 of the 17 were already fixed; the other 8 were still live and the
refuters were simply wrong.**

The 9 are an artefact of how the verification was run. The refuters checked
the branch **tip** (`40e1b75d`) rather than the reviewed tree (`008acdc4`),
and four commits landed in between —

| commit | what it fixed |
|---|---|
| `fe7e8ab0` | a copied id into a re-seeded parent misfiled every clinic's consents (F01, F11, F43) |
| `52d79397` | the report's missing merge-override rows, an un-cleanable verified import, a mid-P4 `KeyError`, a check that paired unlike the write (F18–F27, F40, F41, F46, F80, F86) |
| `d5e82c8a` | an `import_archived_` column took the target's charset and silently truncated a full latin1 TEXT (F02, F10) |
| `40e1b75d` | the validation report said nothing about the transfer, the documents or the properties (F08, F53, F58) |

For those 9, "refuted" means *already fixed*, not *not a defect*.

The remaining **8 were live defects the adversarial pass dismissed** — it was
wrong on nearly half of what it was given:

| finding | sev | what it was | fixed in |
|---|---|---|---|
| **F28** | medium | the decrypted clinic bundle — the whole source EMR in plaintext — written at **0644** | `b5d34685` |
| F57 | medium | the report's verdict was a bare `PASSED` when content mismatches had been acknowledged | `3f49af6f` |
| F60 | low | the oversized-row refusal told the operator their values had been archived, from a pre-check that refuses before any write | `b17c7274` |
| F67 | low | the staging account's defaults file was the one credential write with no `fchmod` after `open` | `382e7ec1` |
| F69 | low | the break-glass-admin resume witness compared a VARCHAR column to an unquoted number | `382e7ec1` |
| F71 | low | `sql_escape`'s documented CR invariant was wrong over this tool's own transport | `b5d34685` |
| F78 | low | the dump collation pre-check scanned only the first 64 KiB | `f191afde` |
| F83 | low | `escape_property_key` double-escaped a leading space | `382e7ec1` |

F28 is why this matters. A world-readable plaintext copy of a clinic's entire
EMR is the worst thing in the 89, and the pass whose job was to catch
overclaims waved it through. **M25's re-open of all 17 was therefore not
caution — it was the step that caught it**, and `382e7ec1` ("five findings two
refuters dismissed, and the evidence did not") is its record. The 24 findings
that never received two verdicts were triaged by hand in M24-c on the same
rule: valid unless demonstrated otherwise.

**Every one of the 89 is fixed.** Nothing was deferred, downgraded, or closed
as "won't fix" — the triage rule for this feature was that a valid finding is
work, with no top-N cut.

## Reading the table

- **Verifier** is what the verification pass returned, not a judgement about
  the finding: 9 of the 17 `refuted` rows were already fixed and 8 were live
  defects the pass got wrong. The **disposition** column is what separates
  them.
- **Disposition** — `fixed before review base` marks the findings whose fix
  had already landed between `008acdc4` and `40e1b75d`; everything else was
  fixed after the review.
- **Commit** is where the behaviour changed. Several findings are duplicates
  of one another raised by different dimensions (the merge-override report
  bucket was found four times, the `--cleanup` accept set four times); they
  share a commit and are listed separately so the count is honest.
- Locations are as reported against `008acdc4` and will not match current line
  numbers.

| # | Sev | Dimension | Location | Finding | Verifier | Disposition | Commit |
|---|-----|-----------|----------|---------|----------|-------------|--------|
| F01 | critical | manifest-generator | `overrides_schema.py:68` | consentType is class "reference" while patient Consent rows keep clinic consent_type_id — consent records get r… | refuted | fixed before review base | `fe7e8ab0` |
| F02 | high | × data-loss | `o19etl.py:1154` | import_archived_ columns are created in the target's utf8mb4 charset, so a latin1 TEXT-family source column is … | refuted | fixed before review base | `d5e82c8a` |
| F03 | high | × error-handling | `o19_preflight.py:2134` | P2/preflight cannot see the B8 charset stop, so a mixed-encoding clinic is told "go" and then dies unoverridabl… | unverified | fixed | `f08487fa` |
| F04 | high | × phi | `dbops.py:693` | `carlos-ctl destroy-data` leaves the entire OSCAR 19 import estate behind, then reports success | survived | fixed | `815bf3a2, 11876e10, f191afde` |
| F05 | high | × structure | `o19import.py:1347` | A VIEW anywhere in the clinic schema fails the P1 restore, and every remedy the tool prints is already true of … | unverified | fixed | `1a1c04ee` |
| F06 | high | × test-quality | `o19import.py:869` | P0 and P1 orchestration is entirely undriven: nine safety gates can be deleted with the suite still green | survived | fixed | `986a60bd, 0743e23c` |
| F07 | high | bundle-p2 | `o19digest.py:146` | The content digest silently collapses to the NULL marker when a row (or one column) renders larger than max_all… | survived | fixed | `37bca8af` |
| F08 | high | bundle-p2 | `o19import.py:1892` | The operator's validation report never mentions the P2 content-transfer outcome, so an accepted `content-transf… | survived | fixed before review base | `40e1b75d` |
| F09 | high | documents | `o19docs.py:93` | P5 rejects every documents tar built by the documented `tar` command: real directory members have no trailing s… | survived | fixed | `2e35ee29` |
| F10 | high | etl-engine | `o19etl.py:1154` | import_archived_ columns are created in the target's charset, so latin1 TEXT-family values are silently truncat… | survived | fixed before review base | `d5e82c8a` |
| F11 | high | manifest-generator | `overrides_schema.py:66` | HRMCategory is class "reference" while HRMDocument.hrmCategoryId and HRMSubClass.hrmCategoryId are copied id-in… | refuted | fixed before review base | `fe7e8ab0` |
| F12 | high | orchestration | `o19import.py:828` | The P0 disk gate re-demands the full fresh-run budget on every --resume, so a host sized to the documented 2.5x… | survived | fixed | `bc6f60bd` |
| F13 | high | preflight | `o19_preflight.py:1823` | A Facility table without `disabled` is filed as INFO on a claim the ETL does not honour — clinic gets `go`, P4 … | survived | fixed | `882d39e0` |
| F14 | high | preflight | `o19_preflight.py:2123` | The charset blocker measures only the repairable half of the mojibake check; thrice-encoded text verdicts `go` … | survived | fixed | `f08487fa` |
| F15 | high | preflight | `o19_preflight.py:1507` | Tables the assessment account cannot see at all are indistinguishable from tables that do not exist — B1/B2/B9 … | survived | fixed | `01d65f59` |
| F16 | high | properties | `o19props.py:296` | Baseline-diff drops 14 carried keys whose CARLOS default differs from the O19 default, flipping consultation/la… | survived | fixed | `68f2c005` |
| F17 | high | roles | `o19roles.py:986` | P7 fails the whole import, unclearably, on any clinic that ever retired a 'Rich Text Letter' eForm | survived | fixed | `68b95260` |
| F18 | high | verify-report | `o19import.py:3226` | `--cleanup` ignores the recorded `--accept content-migration`, so an import verified with it can never be clean… | survived | fixed before review base | `52d79397` |
| F19 | high | verify-report | `o19import.py:1928` | The report's "WHAT DID NOT ARRIVE" section omits every merge-overridden clinic row, and prints "every staging t… | refuted | fixed before review base | `52d79397` |
| F20 | medium | × crash-safety | `o19import.py:1928` | The operator's validation report omits the merge-override lines - the only record of clinic rows the CARLOS see… | refuted | fixed before review base | `52d79397` |
| F21 | medium | × crash-safety | `o19roles.py:1184` | Roles-step report facts measured ACROSS a write are not ledger-backed: a resumed run reports different or missi… | survived | fixed | `57b9cb24` |
| F22 | medium | × data-loss | `o19etl.py:2109` | etl_archived_columns raises an uncaught KeyError mid-P4 when the dump spells a manifest `dropped` column with d… | refuted | fixed before review base | `52d79397` |
| F23 | medium | × data-loss | `o19etl.py:3277` | merge_backfill_mismatch_sql does not model the write it verifies: it pairs rows with unsanitized key expression… | refuted | fixed before review base | `52d79397` |
| F24 | medium | × docs | `o19import.py:3217` | --cleanup ignores the recorded `content-migration` sign-off, so an import the docs say may be cleaned up can ne… | unverified | fixed before review base | `52d79397` |
| F25 | medium | × docs | `o19import.py:720` | The no-backup rollback hint names a recovery that the tool then refuses: destroy-data leaves state.json and o19… | unverified | fixed | `815bf3a2, f191afde` |
| F26 | medium | × error-handling | `o19import.py:3223` | --cleanup drops the recorded --accept set, so a run that passed verification with --accept content-migration ca… | unverified | fixed before review base | `52d79397` |
| F27 | medium | × error-handling | `o19import.py:1928` | The operator's validation report omits the merge-overridden lines — the one class of clinic rows the import del… | unverified | fixed before review base | `52d79397` |
| F28 | medium | × phi | `o19bundle.py:425` | The decrypted clinic bundle — the whole source EMR in plaintext — is written at 0644 | refuted | fixed | `b5d34685` |
| F29 | medium | × phi | `o19import.py:1432` | P2 writes preflight.json and preflight.txt at 0644 while a purpose-built 0600 writer sits unused in the same fe… | survived | fixed | `9c921ca0` |
| F30 | medium | × structure | `o19_preflight.py:2178` | The unknown-column rule is implemented twice — the preflight blocker and the ETL's preservation plan — with no … | unverified | fixed | `343a9920` |
| F31 | medium | × structure | `o19etl.py:172` | The two halves of the tool disagree about what counts as a staged table: only the preflight side filters BASE T… | unverified | fixed | `b5d34685` |
| F32 | medium | × structure | `o19etl.py:2696` | The ETL's manifest-class dispatch falls through to copy, and both P7 parity halves skip anything they do not re… | unverified | fixed | `b5d34685` |
| F33 | medium | × test-quality | `o19docs.py:343` | _same_file's SHA-256 comparison never decides any test — only the size guard is exercised | unverified | fixed | `9daccc33` |
| F34 | medium | × test-quality | `o19docs.py:69` | contained()'s separator boundary is untested — the sibling-directory prefix bypass is unpinned | unverified | fixed | `20d390b1` |
| F35 | medium | × test-quality | `o19import.py:201` | staging_holds_rows has no test at all — the detector feeding the DROP DATABASE gate is unverified | unverified | fixed | `986a60bd` |
| F36 | medium | × test-quality | `test_etl_sql.py:238` | The test pinning the id-map surplus-twin fallback passes when the fallback is removed (substring occurs twice) | unverified | fixed | `20d390b1` |
| F37 | medium | documents | `o19docs.py:166` | A missing HRM report file is silently satisfied by an unrelated document file of the same basename, so reconcil… | survived | fixed | `8b0ed608` |
| F38 | medium | documents | `o19docs.py:985` | The archive CSV export silently and non-injectively mangles every non-UTF-8 byte, corrupting the clinic's reada… | survived | fixed | `3a5dfdd1` |
| F39 | medium | documents | `o19docs.py:729` | relocate_hrm_reports moves HRM reports by path with shutil.move, reopening the exact root-follows-a-planted-sym… | survived | fixed | `32b59f6e` |
| F40 | medium | etl-engine | `o19etl.py:2109` | etl_archived_columns raises an unhandled KeyError when the dump spells a manifest `dropped` column with differe… | survived | fixed before review base | `52d79397` |
| F41 | medium | etl-engine | `o19import.py:1928` | The operator's validation report never renders the merge-override ledger bucket, so "clinic rows the CARLOS see… | survived | fixed before review base | `52d79397` |
| F42 | medium | manifest-generator | `test_manifest_integrity.py:417` | Nothing pins the shipped manifest against the curated overlay's table-class buckets, so an overlay edit committ… | survived | fixed | `20d390b1` |
| F43 | medium | manifest-generator | `generate_manifests.py:978` | No guard exists for a copied column that references a reference/merge-class parent's surrogate id — FK_REMAP co… | refuted | fixed before review base | `fe7e8ab0` |
| F44 | medium | manifest-generator | `generate_manifests.py:1170` | A NOT_RENAMES ruling is keyed only by the O19 dropped column, so a newly added unfilled CARLOS column on an alr… | survived | fixed | `20d390b1` |
| F45 | medium | manifest-generator | `verify_ddl_parse.py:208` | verify_ddl_parse's scaffold cannot build a probe for tables wider than ~85 columns, so every ALTER against them… | survived | fixed | `f191afde` |
| F46 | medium | orchestration | `o19import.py:3226` | --cleanup is permanently unreachable after an import that used --accept content-migration: the cleanup context … | survived | fixed before review base | `52d79397` |
| F47 | medium | orchestration | `o19import.py:2273` | run_cleanup recomputes row parity under the INSTALLED manifest, and manifest_change_refusal explicitly routes a… | survived | fixed | `b99b1278` |
| F48 | medium | orchestration | `o19import.py:1279` | After the documented restic rollback, state.json and etl-progress.json disagree and every recovery path refuses… | survived | fixed | `8a2860c8` |
| F49 | medium | preflight | `o19_preflight.py:2600` | `--digests` records unmeasurable tables in the JSON and tells the operator nothing; a clean `go` ships a digest… | survived | fixed | `9acf2176` |
| F50 | medium | preflight | `o19_preflight.py:1794` | check_identifier_class scans table names only; the ETL refuses odd COLUMN names too, with no flag, at P4 | survived | fixed | `5638ad90` |
| F51 | medium | preflight | `o19_preflight.py:2198` | The standalone assessment cannot see the overlength / numeric-coercion class of P4 refusals, and the report pre… | survived | fixed | `34daee1a` |
| F52 | medium | preflight | `test_sql_escape_contract.py:104` | Three standalone duplicates are unpinned, including `_absent_object` — the helper that decides no-go vs shrug | survived | fixed | `882d39e0` |
| F53 | medium | properties | `o19import.py:1892` | P6's results never reach the operator's validation report — import-report.txt says PASSED without the unknown-k… | survived | fixed before review base | `40e1b75d` |
| F54 | medium | properties | `overrides_props.py:54` | A customised ECHART_SIGN_LINE is carried verbatim with the O19 resource-bundle prefix CARLOS renamed, so signed… | survived | fixed | `20f6a764` |
| F55 | medium | roles | `o19roles.py:616` | privilege-diff.txt itemises four of the six ways the target's grant matrix differs from O19 — both omissions ar… | survived | fixed | `db08149e` |
| F56 | medium | verify-report | `o19import.py:2019` | P7 reports NOT-CHECKED tables and acknowledged mismatches as "check(s) pass" | survived | fixed | `664cdcc1` |
| F57 | medium | verify-report | `o19import.py:1970` | The validation report's verdict is a bare "PASSED" when content mismatches were acknowledged, unlike the prefli… | refuted | fixed | `3f49af6f` |
| F58 | medium | verify-report | `o19import.py:1892` | The validation report says nothing about the document tree, including when documents were skipped entirely | survived | fixed before review base | `40e1b75d` |
| F59 | medium | verify-report | `o19import.py:1756` | content-details.txt from a failed P7 survives a later clean P7, so a PASSED import ships a file listing "rows w… | survived | fixed | `3f49af6f` |
| F60 | low | × data-loss | `o19etl.py:1118` | The oversized-row refusal tells the operator their values were archived and offers a manual path, but it is a p… | refuted | fixed | `b17c7274` |
| F61 | low | × docs | `o19bundle.py:128` | Bundle refusal messages still say "three" inputs after the digest member made it four | unverified | fixed | `dbb04cdf` |
| F62 | low | × docs | `o19etl.py:474` | merge_statement cites an oracle script that does not exist (verify_merge_semantics.py); the real file is verify… | unverified | fixed | `b5d34685` |
| F63 | low | × docs | `o19import.py:1982` | The validation report gives the full go-live NEXT STEPS even when the verdict is FAILED | unverified | fixed | `f191afde` |
| F64 | low | × docs | `carlos-ctl.8:222` | Man page overstates what is lost without the clinic's content digests: P7 still compares values | unverified | fixed | `779dd74c` |
| F65 | low | × docs | `o19-import-deb.md:136` | Prerequisite 4 names one exclusion from the pre-import snapshot; the backup script excludes four more | unverified | fixed | `9c921ca0` |
| F66 | low | × error-handling | `o19etl.py:474` | merge_statement's engine-semantics comment cites an oracle script that does not exist in the repository | unverified | fixed | `b5d34685` |
| F67 | low | × phi | `o19import.py:1145` | The staging account's client defaults file is the one credential write with no fchmod after open | refuted | fixed | `382e7ec1` |
| F68 | low | × phi | `carlos-emr.postrm:105` | postrm purge shreds the derived credential fragment but not the source oscar.properties beside it, and mislabel… | survived | fixed | `9c921ca0` |
| F69 | low | × sql-safety | `o19etl.py:2652` | Break-glass-admin resume witness compares a VARCHAR column to an unquoted number, so an unrelated provider row … | refuted | fixed | `382e7ec1` |
| F70 | low | × sql-safety | `o19etl.py:265` | enum_values and the COLUMN_DEFAULT round-trip assume backslash escaping, but MariaDB renders an embedded quote … | survived | fixed | `597d63c1` |
| F71 | low | × sql-safety | `util.py:126` | sql_escape's documented invariant that CR is safe inside a literal is wrong over this tool's own transport: the… | refuted | fixed | `b5d34685` |
| F72 | low | × structure | `o19bundle.py:237` | Dead code kept alive by its tests: the abandoned `tar -tv` parser the replacement's own docstring warns against… | unverified | fixed | `b5d34685` |
| F73 | low | × structure | `o19import.py:942` | P0 and P7 both own `verify-details.txt` and both truncate it | unverified | fixed | `9c921ca0` |
| F74 | low | × structure | `o19report.py:127` | The validation report declares a `carlos-ctl` version header that no caller ever populates, so the operator's a… | unverified | fixed | `907dad81` |
| F75 | low | × test-quality | `o19etl.py:3767` | archived_column_parity accepts a target holding MORE preserved values than staging | unverified | fixed | `d09487a1` |
| F76 | low | × test-quality | `o19report.py:127` | The validation report declares a carlos-ctl version field that no caller ever fills | unverified | fixed | `907dad81` |
| F77 | low | bundle-p2 | `o19bundle.py:602` | open_bundle SHA-256s every extracted member into a `members` map that nothing ever reads, costing a full extra … | survived | fixed | `dbb04cdf` |
| F78 | low | bundle-p2 | `o19import.py:1302` | The dump collation pre-check only scans the first 64 KiB, so on a real dump it cannot see most tables' collatio… | refuted | fixed | `f191afde` |
| F79 | low | documents | `o19docs.py:239` | eForm image reconciliation decodes references with unquote() but the servlet decodes query parameters with '+' … | survived | fixed | `fb8cbad6` |
| F80 | low | etl-engine | `o19etl.py:3286` | merge_backfill_mismatch_sql builds the backfill's join without dst_cols, so P7 models that write differently fr… | survived | fixed before review base | `52d79397` |
| F81 | low | manifest-generator | `generate_manifests.py:902` | The merge-table surrogate-key test matches the substring "int" anywhere in the column definition, so a varchar … | survived | fixed | `20d390b1` |
| F82 | low | orchestration | `o19import.py:3072` | The phase-ordering invariant and the running-webapp guard have no tests at all | survived | fixed | `0743e23c` |
| F83 | low | properties | `o19props.py:201` | escape_property_key double-escapes a leading space, so a key beginning with a space round-trips to a different … | refuted | fixed | `382e7ec1` |
| F84 | low | properties | `overrides_props.py:140` | hl7_a04_fail_dir and l7_a04_sent_dir are `translate`d into the fragment although CARLOS has no reader for either | survived | fixed | `024a54d1` |
| F85 | low | roles | `o19roles.py:434` | choose_template scores object names case-sensitively while carlos_era_objects folds them | survived | fixed | `d3afcf12` |
| F86 | low | verify-report | `o19etl.py:3277` | merge_backfill_mismatch_sql builds its natural-key join without the target column info, so it does not model th… | refuted | fixed before review base | `52d79397` |
| F87 | low | verify-report | `o19import.py:1769` | The console warning for accepted content mismatches calls live copy/merge tables "preserved table(s)" | survived | fixed | `3f49af6f` |
| F88 | low | verify-report | `o19import.py:1935` | Verification problems are truncated to 40 in both the report and the log, with no marker and no full listing an… | survived | fixed | `c14b9b00` |
| F89 | low | verify-report | `o19import.py:1907` | The report's `tool_version` header field is declared but never populated | survived | fixed | `907dad81` |

## Not in this ledger

The review rounds before M24 (M13, M14, M16, M21) and the automated reviewers
(cubic, CodeRabbit, SonarQube, CodeFactor) are recorded in the PR thread and
in the commit history rather than here; this file covers the M24 review only,
because that is the one that enumerated the feature systematically rather than
reacting to a diff.
