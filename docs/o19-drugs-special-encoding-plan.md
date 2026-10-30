# OSCAR 19 `drugs.special` encoding: investigation and repair plan

Status: proposed implementation; this document does not change import behavior.
Investigated 2026-10-30 for `release/2026.08`.

## Findings

The tester reported three affected drug records and supplied two reconstructed
replacements. The record IDs identify that clinic's examples; they must never
become conditions in a general repair. No original database, full instruction
values, column definitions, or import logs were available. The investigation
therefore reproduces the supplied bytes with synthetic records, rather than
claiming to have examined those three clinical records.

| Supplied UTF-8 hex | Stored Unicode code points | Intended replacement | Replacement UTF-8 hex |
|---|---|---|---|
| `C3A2C280C293` | `U+00E2 U+0080 U+0093` | en dash, `–` (`U+2013`) | `E28093` |
| `C3A2C289C2A4` | `U+00E2 U+0089 U+00A4` | less-than-or-equal, `≤` (`U+2264`) | `E289A4` |

Both sequences are valid UTF-8 containing the wrong characters. They are
consistent with the original symbol's UTF-8 bytes being interpreted as
ISO-8859-1 and then encoded as UTF-8 again. They do not establish where or when
that happened, or whether every other character in the same row is corrupted.
The tester did not need to have made a manual edit for this to occur.

The existing repair uses MariaDB/MySQL `latin1`, whose mapping is Windows-1252,
not ISO-8859-1. In particular, the reported C1 controls are not representable by
that mapping. `double_encoded_predicate` correctly rejects these values;
`repair_expr` leaves them unchanged. Forcing the unguarded `REPAIR_TEMPLATE`
converts either isolated sequence to `3F3F3F` (`???`), with conversion warnings.
Changing the connection charset cannot recover these already stored characters.

With an ordinary case-insensitive column collation, the existing marker scan
finds both supplied sequences and both preflight and `detect_repairs` raise
`B8-charset-unrepairable` / B8. This reproduces the reported failure class. The
failure is a missing supported repair, rather than a missing `drugs.special`
scan: `CHARSET_SCAN` already includes that column.

There is also a silent detection gap: for these same isolated sequences in a
`utf8mb4_bin` column, both scans report no encoding finding or repair. The marker
is `[U+00C3 U+00C2][U+0080-U+00BF]`; the actual first character is `U+00E2`.
Case-insensitive matching folds `â` to `Â`, whereas binary collation does not.
Recognition of the new rules must use exact character bytes, independently of
collation. This does not prove the tester used a binary column.

## Evidence checked

The release branch was inspected at
`218140ca7e28e82e7404eaed6b95d2e46adab059`. Its
[`debian/carlos-ctl.pin`](../debian/carlos-ctl.pin) names `1.2.1`; the importer
lives in [carlos-emr/carlos-ctl](https://github.com/carlos-emr/carlos-ctl), which
has no `release/2026.08` branch. Runtime changes belong there, followed by a
release integration change here.

The synthetic checks used the actual predicates, `repair_expr`,
`detect_repairs`, and `check_text_encoding` from both:

- `carlos-ctl` tag `1.2.1`, peeled commit
  `4ffc31c54d3ff08e8d1decac2447f642e8a6e91c`;
- current `main`, `58813c12f28c089e396050b5020889297bb6a238`.

Both were checked on disposable MariaDB 10.11.19 and 11.8.9 servers. Each of the
four combinations exercised 12 synthetic values: the two reported sequences,
ordinary Windows-1252 mojibake mixed with one sequence, valid multilingual text
around both sequences, repeated occurrences, already correct symbols, `1,800`,
correct accents, NULL, empty text, CRLF/tab text, and an unmapped C1 sequence.

For all combinations, exact replacements produced the expected UTF-8 hex, left
all other characters unchanged, generated zero warnings, and were idempotent.
A separate genuine `latin1` staging-column control confirmed the existing
Windows-1252 repair still decodes `CafÃ©` to `Café` without warnings. The binary
column scan gap was also reproduced with 1.2.1 on 10.11 and current main on 11.8.
These checks validate the root cause and the replacement primitive; they do
not validate an integrated fix, a complete clinic import, or the tester's
unavailable full rows. MySQL and Python 3.4 were not run during this investigation.

The following standalone SQL reproduces the byte mapping and collation issue
without updating clinical tables. Run it in a disposable MariaDB session with
an existing scratch database selected and `--default-character-set=utf8mb4`.
The temporary table lasts only for that session.
The marker literal assumes the normal SQL backslash-escape mode used by the
existing importer.

```sql
CREATE TEMPORARY TABLE o19_special_probe (
  id INT PRIMARY KEY,
  special TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
);
INSERT INTO o19_special_probe VALUES
  (1, CONVERT(UNHEX('C3A2C280C293') USING utf8mb4)),
  (2, CONVERT(UNHEX('C3A2C289C2A4') USING utf8mb4));

SELECT id, HEX(special) AS original_hex,
  HEX(REPLACE(REPLACE(CONVERT(special USING utf8mb4),
    CONVERT(UNHEX('C3A2C280C293') USING utf8mb4),
    CONVERT(UNHEX('E28093') USING utf8mb4)),
    CONVERT(UNHEX('C3A2C289C2A4') USING utf8mb4),
    CONVERT(UNHEX('E289A4') USING utf8mb4))) AS candidate_hex,
  special REGEXP '[\\x{C3}\\x{C2}][\\x{80}-\\x{BF}]' AS old_marker_ci,
  special COLLATE utf8mb4_bin
    REGEXP '[\\x{C3}\\x{C2}][\\x{80}-\\x{BF}]' AS old_marker_bin
FROM o19_special_probe ORDER BY id;
SHOW COUNT(*) WARNINGS;
```

Expected candidate hex: `E28093`, then `E289A4`; marker results: `1,0` for each
row; warning count: zero. Existing non-round-tripping conversions can be
investigated separately, but must not be used as an UPDATE recipe.

## Implementation plan

### 1. Add a narrow, versioned repair rule

Start with exactly the two mappings above, scoped to `drugs.special`. Match
whole encoded sequences using binary `LOCATE` or an equivalent byte-exact
operation after normalizing the declared column encoding to utf8mb4. Do not
match an unaligned substring of `HEX(special)`. Do not key on drug IDs or patient
IDs. Do not expand to all narrative columns, punctuation, or arbitrary Unicode
without separate evidence and review.

Keep the existing Windows-1252 repair and its safety guards. Apply that guarded
whole-row repair first where it already applies, then the two exact substring
replacements. A value containing these C1 sequences will normally fail the
whole-row predicate and reach only the exact replacements. Do not run a second
whole-row conversion on the resulting valid dash or mathematical symbol.

This ordering deliberately leaves a row containing other unresolved mojibake
blocked. For example, `CafÃ©` beside a reported C1 sequence must not become an
accepted row merely because the C1 sequence was replaced. If supporting that
additional mixture requires a broader decoder, stop and extend the reviewed
rules and fixtures separately.

Represent the rule version and column scope consistently in the package and
the standalone assessment script. The standalone script must remain a single
Python 3.4-compatible file with no package imports. Pin the deliberate duplicate
with behavior tests in `tests/test_sql_escape_contract.py`.

### 2. Assess candidates without writing to the source

Extend `check_text_encoding` and `detect_repairs` together. Count rows matching
each exact rule, then evaluate the existing unrepairable and repeated-encoding
checks on the proposed output. Do not clear B8 by subtracting every row that
contains a known sequence: a single row can contain both known and unknown
corruption. Detect residual C1-style corruption independently of the existing
case-dependent marker on the target. Include the unmapped en/em-dash neighbor
`C3A2C280C294` as a required blocking fixture under binary collation.

Use `CONVERT`, `UNHEX`, and binary `LOCATE` for known-rule counts on older source
servers; these counts must not depend on modern PCRE escapes. Preserve the
existing `charset-b8-unmeasured` disclosure where the remaining B8 scan cannot
be performed. A query error remains an uncompleted check or a hard failure,
never a count of zero. Distinguish an absent lower-patch-level column using only
the existing absent-object handling.

Keep `--accept charset-repair` as the explicit acknowledgement, with report
text naming the two new rules. Known, fully repairable rows require that flag;
unresolved rows remain B8 with no override. Preflight must never issue UPDATEs,
rewrite a dump, or change source digests. Do not print medication instructions
or patient identifiers in routine reports. Report column, rule, affected-row
count, and, if collected, a separately labeled occurrence count.

### 3. Copy and verify the same candidate

Route accepted transformations through `source_expr` / `repair_expr` so the
copy, overlength pre-checks, per-batch stored-value checks, and P7 content
verification share one expression. Preserve NULL, empty strings, line endings,
and unaffected characters. Keep strict write-warning and truncation refusals.
Do not add an independent UPDATE pass after the copy.

Retain the original source snapshot and existing rollback path. Record the
applied rule version, column, and counts in the import ledger/report without
clinical text. P2 transfer digests must still compare the untouched source and
staging values; P7 must compare the transformed target against the approved
candidate. Counts alone are not sufficient verification.

Bind the new transformation rules to `ETL_CONTRACT_VERSION` and the existing
resume checks. An import already written under older rules must finish with
its original package or restore the pre-import backup before restarting.
Never reinterpret its completed checkpoints under the new repair. An unchanged
rerun of the new version must remain idempotent.

### 4. Prove the integrated behavior before release

| Regression group | Required result |
|---|---|
| Both reported sequences; repeated occurrences; different synthetic drug IDs | Exact expected target hex, no dependence on the tester's IDs. |
| Correct `–`, `≤`, accents, non-Latin text, emoji, ASCII `1,800`, NULL, empty, CRLF/tab | Unchanged bytes in the utf8mb4-normalized value outside the two matched spans. |
| Known and unknown corruption in the same row; unmapped C1 sequence; mixed or thrice-encoded text | B8 remains; `--accept charset-repair` cannot clear it. |
| Known-only rows, with and without acknowledgement | Both assessment and import require the flag; accepted copy succeeds and records rules/counts. |
| utf8mb4_general_ci, utf8mb4_bin, target 11.8 default; genuine latin1 staging | Known detection is collation-independent; existing Windows-1252 behavior survives. |
| Old source regex engine; denied/corrupt scan; absent optional column | Known detection still works; unmeasured/error/absence states remain explicit. |
| P2/P7, deliberate target alteration, and text-size boundary | Source digests agree; faithful transformed copy verifies; altered/truncated output fails. |
| Interrupted/resumed import and package change | Same-version resume succeeds; changed-rule resume refuses before further writes. |

Add live database tests to `carlos-ctl/tests/test_etl_mariadb.py` and verdict /
standalone parity cases to `tests/test_preflight.py` and
`tests/test_sql_escape_contract.py`. Extend
[`verify_sql_semantics.py`](../scripts/migration/o19/verify_sql_semantics.py) and
the synthetic O19 fixture here so an end-to-end rehearsal exercises the new
path. Include installed-package tests, rather than testing only a source import.
Run the package unit suite/lint, standalone Python 3.4 compatibility gate, and
live MariaDB 10.11/11.8 checks. Rehearse a complete import, verification, and
rollback on a disposable fixture before describing the fix as ready.

### 5. Deliver through the correct release dependency

Submit the runtime and regression changes to `carlos-emr/carlos-ctl`'s supported
development branch. Publish a tested package release, then submit a separate
integration PR to CARLOS `release/2026.08` updating `debian/carlos-ctl.pin` to the
actual published tag and refreshing the operator documentation and rehearsal
evidence. Do not invent a package version or pin an unpublished asset. Review
whether the dependency floor must rise; the release pin and minimum dependency
serve different purposes.

Regenerate the standalone assessment from the upgraded pair of packages and
reassess before exporting the final frozen source set. Keep existing CLI flags,
report verdicts, schema, and source backups compatible. New rule/audit metadata
must be additive; changing the ETL contract is an explicit protection against
mixing old and new conversion rules during a partial import.

This plan is complete when the integrated regression matrix and full rehearsal
pass, the published CLI is pinned by the release branch, and the release notes
state the exact scope. Until then, the current B8 stop remains in force. Any
manual correction still needs review against the original medication
instructions and a fresh assessment/export; the reconstructed updates alone
do not establish the correctness of every character in a full clinical row.
