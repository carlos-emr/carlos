# OSCAR 19 → CARLOS migration tooling (experimental)

Development-side tooling for the `carlos-ctl import-o19` clinic importer.
Design and operational spec: `docs/oscar19-to-carlos-migration-plan.md`.
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
overrides_schema.py      hand-curated table/column classifications (durable)
overrides_props.py       hand-curated oscar.properties dispositions (durable)
build-o19-fixture.sh     builds the rehearsal database + turnkey inputs
fixtures/                vendored O19 demo data & stock properties
                         (PROVENANCE.md), synthetic clinic properties,
                         documents-tree manifest/generator + fixture rows
```

## Regenerating the manifests

```bash
git clone --depth 1 https://bitbucket.org/oscaremr/oscar.git /tmp/oscar19
python3 scripts/migration/o19/generate_manifests.py --oscar-src /tmp/oscar19
cd debian/assets && python3 -m unittest discover -s carlos_ctl/tests -t .
```

Outputs (generated — never hand-edit): `debian/assets/carlos_ctl/
o19map_schema.py`, `o19map_props.py`, and the marker-delimited data block in
`o19_preflight.py`. Any O19 table the overlays don't classify is emitted as
class `unknown`, which `test_manifest_integrity.py` refuses — classify it in
`overrides_schema.py` and bump `SCHEMA_MAP_VERSION` (`o19map-N`; deliberately
not CalVer-shaped so it can't be misread as a CARLOS release version).

`--check` regenerates in memory and exits non-zero on drift (for review).

## Building the rehearsal fixture

```bash
scripts/migration/o19/build-o19-fixture.sh \
    --oscar-src /tmp/oscar19 --out /tmp/o19-inputs \
    --mysql-cmd mariadb --mysql-arg -uroot --mysql-arg -pSECRET
```

Creates a **latin1** `o19_fixture` database (init scripts in
`createdatabase_generic.sh` order, then the vendored `release/demo.sql` demo
dataset and the fixture document rows) and emits the three turnkey inputs:
`o19-fixture.sql.gz`, `o19-documents.tar.gz` (generated placeholder tree —
includes one deliberate missing-file row and one orphan file so the
documents-phase reconciliation gate is exercised), and `oscar.properties`
(the synthetic clinic-example file covering every props disposition).
`--with-olis` additionally loads `olis/olisinit.sql` to exercise the
OLIS-dropped path.
