# OSCAR 19 → CARLOS migration tooling (experimental)

Development-side tooling for the `carlos-ctl import-o19` clinic importer.
Design and operational spec: `docs/oscar19-to-carlos-migration-plan.md`;
operator runbook for the shipped verbs: `docs/o19-import-deb.md`.
The importer itself ships with the Debian package
(`debian/assets/carlos_ctl/o19*.py`); this directory holds what does NOT
ship — the manifest generator, its curation overlays, and rehearsal fixtures.

> The migration path is **(experimental)**: every migration's output should
> receive a technical review — verification report, spot checks, UI smoke —
> before clinical use.

## Layout

```
generate_manifests.py    regenerates the shipped manifest modules from an
                         OSCAR 19 checkout + the CARLOS Flyway set (read-only)
verify_ddl_parse.py      checks that generator's DDL reader against a real
                         MariaDB (see "Verifying the DDL parse")
verify_merge_semantics.py  settles the merge/id-map invariants against a real
                         MariaDB (see "Verifying the merge semantics")
overrides_schema.py      hand-curated table/column classifications (durable)
overrides_props.py       hand-curated oscar.properties dispositions (durable)
build-o19-fixture.sh     builds the rehearsal database + turnkey inputs
fixtures/                vendored O19 demo data & stock properties
                         (PROVENANCE.md), synthetic clinic properties and
                         role/legacy-data cases (demo-data/roles.sql) and
                         clinical rows + text encodings the vendored
                         dataset lacks (demo-data/clinical.sql),
                         documents-tree manifest/generator + fixture rows,
                         documents/make-o19-bundle.sh (packs the inputs into
                         every --bundle variant used by the rehearsal)
```

## Packing the rehearsal bundles

```bash
scripts/migration/o19/fixtures/documents/make-o19-bundle.sh \
    /tmp/o19-inputs /tmp/o19-bundles
```

Writes `o19-bundle.tar`, `.tar.gz`, `.tar.enc` and `.tar.gz.enc`, the matching
`o19-bundle.sha256` (what `--bundle-sha256` verifies) and
`bundle-password.txt`. The password is a fixed test value: rehearsal and test
use only, never a clinic handoff.

## Regenerating the manifests

```bash
git clone --branch OSCAR_19_RC1 --depth 1 \
    https://bitbucket.org/oscaremr/oscar.git /tmp/oscar19
python3 scripts/migration/o19/generate_manifests.py --oscar-src /tmp/oscar19
cd debian/assets && python3 -m unittest discover -s carlos_ctl/tests -t .
```

Outputs (generated — never hand-edit): `debian/assets/carlos_ctl/
o19map_schema.py`, `o19map_props.py`, and the marker-delimited data block in
`o19_preflight.py`. Any O19 table the overlays don't classify is emitted as
class `unknown`, which `test_manifest_integrity.py` refuses — classify it in
`overrides_schema.py` and bump `SCHEMA_MAP_VERSION` (`o19map-N`; deliberately
not CalVer-shaped so it can't be misread as a CARLOS release version).

`--check` regenerates in memory and exits non-zero on drift (for review);
it covers both manifest modules and the generated block in
`o19_preflight.py`. The outputs carry no wall-clock stamp, only the O19
source commit, so an unchanged input regenerates byte-identical output.
Credential-bearing stock defaults are never emitted (`SECRET_DEFAULT_KEYS`
lists the keys instead; the props phase always surfaces them for review).

## Verifying the DDL parse

`generate_manifests.py` reads the SQL with a hand-written DDL reader
(`walk_ddl` + `Schema`). That reader decides which columns exist, so it
decides what `carlos-ctl import-o19` copies, archives or leaves behind: a
column it silently misses is a column the import silently drops. Rather than
trust it, ask MariaDB.

```bash
python3 scripts/migration/o19/verify_ddl_parse.py --oscar-src /tmp/oscar19 \
    --mysql-arg=--socket=/run/mysqld/mysqld.sock --mysql-arg=-uroot
```

Every CREATE TABLE is replayed into a scratch schema under a probe name and
every ALTER TABLE against a probe built from the model's state at that point;
the resulting columns and primary key, read back from `information_schema`,
are compared with what the generator concluded from the same statement. Exit
0 means they agree everywhere they could be compared. The scratch schema
(`--db`, default `carlos_ddl_verify`) is **dropped and recreated on every
run** — point it at a throwaway server, never at a clinic's.

Statements a modern server refuses outright (a 2006 definition such as
`AUTO_INCREMENT` with no key, or an ALTER whose anchor column the model does
not carry) are reported as "not comparable" rather than as disagreements.

Run it after any change to the reader. It found three defects on its first
pass — a primary key recorded in the clause's spelling rather than the
column's, `ADD COLUMN ... AFTER x` appending instead of positioning, and
`CHANGE` matching a column name case-sensitively — each now also pinned by a
database-free unit test in `test_generator.py::TestTheDdlParser`.

**Why not an off-the-shelf SQL parser?** Measured on this corpus, not
assumed. sqlglot 30.18.0 (MySQL dialect) silently degrades 7 CREATE TABLE and
7 ALTER TABLE statements to opaque nodes carrying no column list — among them
`facility`, `custom_filter*`, `tickler_*` and `bed_check_time` — which would
drop those tables from the manifest with no error. simple-ddl-parser reads
those but returns a grouped result rather than an ordered statement stream,
and document order is load-bearing here (a file that DROPs then CREATEs the
same table must not be applied phase-ordered). sqlparse builds no DDL model
at all. The reader stays; this script is what makes it checkable, and is also
how you would prove a future library good enough to replace it.

## Verifying the merge semantics

`o19etl.merge_statement` is an anti-join that reads the table it inserts into,
so whether the `NOT EXISTS` sees rows the same statement just added is an
engine question the unit tests cannot answer — they assert on generated SQL
text. It matters: if it did see them, a clinic table holding two rows on one
natural key would silently lose one.

```bash
python3 scripts/migration/o19/verify_merge_semantics.py \
    --mysql-arg=--socket=/run/mysqld/mysqld.sock --mysql-arg=-uroot
```

Builds the real tables, runs the real generated statements over four
seed/staging shapes, and asserts on rows: the CARLOS seed keeps its row, the
clinic's twins are preserved rather than deduplicated, every source id gets an
id-map entry (a source id with no entry is a dangling child foreign key), and
the "overridden" figure the operator's report carries adds up against what
actually landed. Scratch schemas are dropped and recreated on every run —
throwaway server only.

Answer on MariaDB 10.11.14 (2026-09-04): the anti-join does **not** see
same-statement inserts, and the id map's twin pairing holds in all four
shapes. Removing the surplus-twin fallback from `idmap_statements` breaks
three of them, which is what makes the check worth keeping.

## Building the rehearsal fixture

```bash
scripts/migration/o19/build-o19-fixture.sh \
    --oscar-src /tmp/oscar19 --out /tmp/o19-inputs \
    --mysql-cmd mariadb --mysql-arg -uroot --mysql-password-file /root/.o19pw
```

The password never goes on the command line (`-pSECRET` is refused): use
`--mysql-password-file` (exported as `MYSQL_PWD` for the client), a client
defaults file (`--mysql-arg --defaults-extra-file=FILE`), or a pre-set
`MYSQL_PWD`.

Creates a **latin1** `o19_fixture` database (init scripts in
`createdatabase_generic.sh` order, then the vendored `release/demo.sql` demo
dataset, the fixture document rows, `demo-data/roles.sql` — the synthetic
role/privilege and legacy-data cases the roles post-step reconciles — and
`demo-data/clinical.sql`, which adds the encounter notes, appointments,
ticklers, consultations and Ontario billing the vendored dataset does not
carry, together with accented text in both the correctly-encoded and the
deliberately double-encoded form so the charset scan, the per-row repair
and the `charset-repair` sign-off are actually exercised) and emits the
three turnkey inputs:
`o19-fixture.sql.gz`, `o19-documents.tar.gz` (generated placeholder tree —
includes one deliberate missing-file row and one orphan file so the
documents-phase reconciliation gate is exercised), and `oscar.properties`
(the synthetic clinic-example file covering every props disposition).
`--with-olis` additionally loads `olis/olisinit.sql` to exercise the
OLIS-dropped path. `--with-updates` applies the O19 `updates/*.sql` patch
history best-effort before the demo data (real dumps carry ~280 privilege
rows those patches add; 2006-era patches routinely fail on a modern server,
each failure is named with its diagnostic, and a failed file may have applied
its earlier statements — a rehearsal input, not a clinic). Note that
`update-2008-04-07.sql` recreates `secUserRole`: the stock assignments are
renumbered and every one becomes `activeyn = 1`, so the roles.sql cases
are the only NULL-activeyn rows left; the builder asserts the anchors the
rehearsal depends on (the seed clinician's two roles, a Facility, a
clinic row) after the replay.
