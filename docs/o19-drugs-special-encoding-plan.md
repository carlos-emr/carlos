<!--
Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

This software is published under the GPL GNU General Public License.
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

CARLOS EMR Project
https://github.com/carlos-emr/carlos
-->

# OSCAR 19 `drugs.special` encoding: investigation and repair plan

Status: runtime implementation is under review in
[carlos-ctl #18](https://github.com/carlos-emr/carlos-ctl/pull/18).
The CARLOS-side fixture/oracle is included alongside this document. This is not
yet a shipped release: the current CLI pin remains unchanged until a fixed
package is published, after which both the pin and minimum CLI dependency must
be updated as described below.
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

### Follow-up design review

The second review found an ordering issue in the initial proposal. For example,
the synthetic envelope `C383C2A2C382E282ACC382E2809C` decodes through the existing
guarded Windows-1252 repair to `C3A2C280C293`. Chaining the new replacement after
that would produce `E28093` in the same pass, accepting an extra encoding layer.
The revised plan below chooses a single repair path from the original value
and leaves the newly exposed C1 sequence blocked.

The revised candidate and residual-check design was probed with 22 synthetic
cases under both `utf8mb4_general_ci` and `utf8mb4_bin` on each MariaDB version:
88 case/collation/server combinations. The probes also checked emoji, NUL,
CRLF/tab preservation, a 65535-byte source TEXT, partial sequences, both
Windows-1252 envelopes, immutable source values, stored candidate hex, accepted
candidate idempotence, and detection of deliberate target alteration, including
NULL-to-text changes. Unrelated C1 characters and a legitimate NEL line
separator beside an accented word were preserved. All passed with zero
candidate conversion warnings.
These are design probes built with the existing guarded repair and proposed
SQL expressions, not tests of an implemented repair classifier, ledger wiring,
or complete import. The integration and release gates below are still required.

## Implementation plan

### Approach selection

Use an exact, explicitly acknowledged repair while copying from immutable
staging. This is the best fit for the supplied evidence and the existing
importer: it supports both reported cases without guessing about neighboring
characters or changing source preservation and verification semantics.

| Alternative | Assessment |
|---|---|
| Rewrite the source during preflight | Changes the clinic's data during assessment and invalidates the measured source set. Keep preflight read-only. |
| Generic Unicode cleanup, transliteration, or ignored conversion errors | Can alter valid medication instructions or discard meaningful symbols. Do not use. |
| Decode every affected row through ISO-8859-1 | Assumes all characters share one encoding history; mixed correct Unicode disproves that assumption. Do not use. |
| Patch staging or target in a separate UPDATE phase | Requires a new mutation/checkpoint protocol and a second model for digests and verification. The existing copy expression already supplies those controls. |
| Exact replacements in the existing copy expression | Limits changes to reviewed spans, preserves the source, and lets the existing value checks verify the transformation. Recommended. |

No heuristic can establish the intended meaning of every arbitrary corrupted
clinical value. Recognized suspicious cases with no approved repair remain
blocked for investigation;
additional supported sequences require evidence, reviewed rules, and fixtures.

### 1. Add a narrow, versioned repair rule

Start with exactly the two mappings above, scoped to `drugs.special`. Match
whole encoded sequences using binary `LOCATE` or an equivalent byte-exact
operation after normalizing the declared column encoding to utf8mb4. Do not
match an unaligned substring of `HEX(special)`. Do not key on drug IDs or patient
IDs. Do not expand to all narrative columns, punctuation, or arbitrary Unicode
without separate evidence and review.

Keep the existing Windows-1252 repair and its safety guards. Choose mutually
exclusive paths from the ORIGINAL normalized value:

1. If the original `drugs.special` contains either exact sequence, apply only
   the two substring replacements to that value. Preserve all other spans.
2. Otherwise, use the existing guarded Windows-1252 repair unchanged.
3. Check the candidate for residual corruption before accepting either path.

Do not chain the two paths, run the whole-row decoder after inserting valid
symbols, or repeatedly decode until the value looks clean. In particular, a
Windows-1252-encoded envelope around a reported sequence can expose that sequence
after one legacy decode. Automatically replacing it in the same pass would
silently accept an additional encoding layer. It must remain B8. Keep existing
original-value repeated-encoding refusals as well as the candidate checks.

This ordering deliberately leaves a row containing other unresolved mojibake
blocked. For example, `CafÃ©` beside a reported C1 sequence must not become an
accepted row merely because the C1 sequence was replaced. If supporting that
additional mixture requires a broader decoder, stop and extend the reviewed
rules and fixtures separately.

Keep the reviewed rule registry in CLI-owned code, keyed by the full
`(table, source_column)` identity, with a stable rule-set version. Do not derive
scope from a bare column name: `source_expr` receives a table entry, not the
table name. Attach trusted scope metadata through the common `effective_entry`
path, or pass it explicitly through every caller. P4 and P7 must reconstruct
the same metadata; entry copying, renames, and archived-column expansion must
not drop it. Enrich a copied entry rather than mutating shared ON/BC profile
data. Never apply these rules to `drugs.comment`, `special_instruction`,
an archived column, or another table's `special` merely because the name matches.

Keep the registry and shared SQL helpers in the standalone assessment code
OUTSIDE its generated manifest block, and import those same helpers from the
package. This provides one implementation without adding package imports to
the standalone file. `load_manifest` and
`write_standalone_preflight` must not erase or change it. The standalone script
must remain one Python 3.4-compatible file with no package imports. Pin scope,
rule bytes, branch selection, and classifier agreement with behavior tests in
`tests/test_sql_escape_contract.py`, including a freshly exported standalone
file and both Ontario and British Columbia profiles. This avoids requiring a
new schema-manifest format merely to deliver CLI repair behavior.

### 2. Assess candidates without writing to the source

Extend `check_text_encoding` and `detect_repairs` together. Count rows matching
each exact rule, then evaluate the existing unrepairable and repeated-encoding
checks on the proposed output. Do not clear B8 by subtracting every row that
contains a known sequence: a single row can contain both known and unknown
corruption. Detect residual C1-style corruption independently of the existing
case-dependent marker on the target. Specifically recognize residual ISO-style
fragments beginning `U+00E2` followed by a C1 control (`U+0080` through `U+009F`)
in this scoped column after the candidate is built. Use byte-exact character
searches or a validated Unicode-aware predicate. Do not strip those characters
or infer their replacements. Do not introduce a blanket ban on C1 characters:
an unrelated NEL separator, for example, is outside these repair rules.
The adjacent `â`+C1 pattern is a suspicion marker, not proof of corruption:
valid UTF-8 can intentionally contain that literal pair. It remains an
explicit B8 manual-review case because its intended meaning cannot be inferred
from the bytes. No automatic replacement or stripping applies to an unknown pair.
Include the unmapped
em-dash sequence `C3A2C280C294`, partial sequences, and newly exposed known
sequences as required blocking fixtures under binary collation. Preserve the
existing mixed-encoding checks for residual corruption without C1 controls.

Use guarded CASE branches for candidate evaluation and any target-side
speculative conversions. Reusing the unguarded `REPAIR_TEMPLATE` or assuming
that AND predicates short-circuit is unsafe: correct Unicode and C1 values can
produce conversion warnings or question marks. New candidate/count expressions
must produce no conversion warnings; an unexpected warning/error fails the
check. Do not change session SQL modes or warning handling globally to make a
new expression pass.

Use `CONVERT`, `UNHEX`, and binary `LOCATE` for known-rule counts on older source
servers; these counts must not depend on modern PCRE escapes. Preserve the
existing `charset-b8-unmeasured` disclosure where the remaining B8 scan cannot
be performed. A query error remains an uncompleted check or a hard failure,
never a count of zero. Distinguish an absent lower-patch-level column using only
the existing absent-object handling. Do not claim complete residual validation
on an older source server when it is unmeasured; the target-side gate remains
authoritative. Do not call the target's modern PCRE-guarded repair builder from
the standalone old-server path. Known-pattern and residual-C1 counts can use
the portable exact-character operations without modern regex support.

Keep `--accept charset-repair` as the explicit acknowledgement, with report
text naming the two new rules. Known, fully repairable rows require that flag;
unresolved rows remain B8 with no override. Preflight must never issue UPDATEs,
rewrite a dump, or change source digests. Do not print medication instructions
or patient identifiers in routine reports. Report column, rule, affected-row
count, and, if collected, a separately labeled occurrence count.

Extend the repair-set decision itself: a column needs repair when it has legacy
repairable rows OR exact-rule rows. These reported C1 rows have a legacy count
of zero; adding only a finding or a replacement helper would leave
`source_expr` unwrapped and silently copy the original corruption. Preserve the
existing `repairs = {table: [column, ...]}` ledger shape and existing finding /
verdict fields. Record rule version and counts in separate additive metadata.
Count affected rows as a union so a row matching both rules is not counted
twice; keep per-rule counts separately. If any row fails the residual gate, the
entire import remains no-go before the copy, regardless of acknowledgements.

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
rerun of the new version must remain idempotent. Refuse unknown or inconsistent
recorded rule versions; do not fall back to today's rules. Keep supported
cleanup/retirement under the original package available for older workspaces.

### 4. Prove the integrated behavior before release

| Regression group | Required result |
|---|---|
| Both reported sequences; repeated occurrences; different synthetic drug IDs | Exact expected target hex, no dependence on the tester's IDs. |
| Correct `–`, `≤`, accents, non-Latin text, emoji, ASCII `1,800`, NULL, empty, CRLF/tab | Unchanged bytes in the utf8mb4-normalized value outside the two matched spans. |
| Unrelated C1 characters; a NEL line separator after an accented word | No blanket control-character cleanup or new refusal outside the recognized corruption patterns. |
| Known and unknown corruption in the same row; unmapped C1 sequence; mixed or thrice-encoded text | B8 remains; `--accept charset-repair` cannot clear it. |
| Legacy decode exposes a new allowlisted sequence; partial or truncated sequence | No chained decode; residual corruption remains B8. |
| Known-only rows, with and without acknowledgement | Both assessment and import require the flag; accepted copy succeeds and records rules/counts. |
| Only new-rule rows; legacy repair count is zero | `drugs.special` is still recorded in the repair set, copied with the right expression, and verified by P7. |
| utf8mb4_general_ci, utf8mb4_bin, target 11.8 default; genuine latin1 staging | Known detection is collation-independent; existing Windows-1252 behavior survives. |
| Another column/table named `special`, archives, ON/BC profile switches | New rules never escape their scope; freshly exported standalone and target agree. |
| Old source regex engine; denied/corrupt scan; absent optional column | Known detection still works; unmeasured/error/absence states remain explicit. |
| P2/P7, deliberate target alteration, and text-size boundary | Source digests agree; faithful transformed copy verifies; altered/truncated output fails. |
| Interrupted/resumed import and package change | Same-version resume succeeds; changed-rule resume refuses before further writes. |
| Upgrade from the currently supported CARLOS and CLI pair | Package dependencies install the fixed CLI; target runs it rather than an older satisfying version. |

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
evidence. Raise `debian/control`'s CLI minimum to the first published version
providing this repair when the CARLOS release advertises it. A pin selects the
release asset; it does not force an installed older CLI satisfying the existing
dependency to upgrade. Test both fresh installation and upgrade from the current
pair, and assert the version actually invoked. Do not invent a package version
or pin an unpublished asset.

Regenerate the standalone assessment from the upgraded pair of packages and
reassess before exporting the final frozen source set. Keep existing CLI flags,
report verdicts, schema, and source backups compatible. New rule/audit metadata
must be additive; changing the ETL contract is an explicit protection against
mixing old and new conversion rules during a partial import.

The intended safety changes are explicit: residual ISO-style C1 patterns previously missed
by a binary-collation scan now stop, and an import already written under old
conversion rules cannot resume under the new contract. These are documented
refusals, not automatic rewrites, dropped values, or a promise that upgrading
mid-import is supported. No Flyway migration, application schema, public CLI
flag, or unrelated-column transformation changes are required for this repair.

This plan is complete when the integrated regression matrix and full rehearsal
pass, the published CLI is pinned by the release branch, and the release notes
state the exact scope. Until then, the current B8 stop remains in force. Any
manual correction still needs review against the original medication
instructions and a fresh assessment/export; the reconstructed updates alone
do not establish the correctness of every character in a full clinical row.

## Implementation evidence

The runtime PR implements the mutually exclusive repair paths, exact matching,
residual refusal, scope propagation through copy/P7, explicit old-source
measurement gaps, checked query/warning results, and versioned ledger metadata.
It bumps the ETL contract to 3 without changing the public acceptance flag.

Validation of the implementation includes the full CLI unit suite on Python
3.12 and the actual Debian build on Ubuntu 26.04 / Python 3.14 (1,863 tests in
each suite, with optional environment-dependent skips), six live encoding test
methods with subcases on each of MariaDB 10.11.19 and 11.8.9, and 34 installed
package scope/encoding/existing-copy regressions on Python 3.14 and MariaDB.
The installed-package CI job now includes the new encoding module.

`fixtures/drug-special.sql` and the SQL semantics verifier here add a separate
synthetic oracle for the stored bytes, hard B8 refusals, source preservation,
and P7's detection of a deliberately altered repaired instruction. Until the
fixed CLI is published, run that verifier with `CARLOS_CTL_SRC` selecting the
reviewed implementation checkout; an older CLI fails explicitly instead of
skipping the regression. Complete clinic cutover/rollback and release-upgrade
rehearsals remain release gates, rather than claims established by these tests.
