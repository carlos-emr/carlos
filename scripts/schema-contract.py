#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Export/check the physical contract of a freshly migrated CARLOS database.

Use only a disposable reference DB created by the complete Flyway chain, never
an installed clinic database. Runtime metadata and data are deliberately absent.
Credentials belong in a MariaDB defaults file, not client arguments.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FORMAT = 2
FIELDS = {
    "defaults": ("SCHEMATA", ["SCHEMA_NAME", "DEFAULT_CHARACTER_SET_NAME", "DEFAULT_COLLATION_NAME"], "1=1"),
    "tables": ("TABLES", ["TABLE_NAME", "ENGINE", "ROW_FORMAT", "TABLE_COLLATION"], "TABLE_TYPE = 'BASE TABLE'"),
    "columns": ("COLUMNS", ["TABLE_NAME", "COLUMN_NAME", "COLUMN_TYPE", "IS_NULLABLE", "COLUMN_DEFAULT", "EXTRA", "CHARACTER_SET_NAME", "COLLATION_NAME", "GENERATION_EXPRESSION", "ORDINAL_POSITION"], "1=1"),
    "indexes": ("STATISTICS", ["TABLE_NAME", "INDEX_NAME", "NON_UNIQUE", "SEQ_IN_INDEX", "COLUMN_NAME", "COLLATION", "SUB_PART", "INDEX_TYPE", "IGNORED"], "1=1"),
    "keys": ("KEY_COLUMN_USAGE", ["TABLE_NAME", "CONSTRAINT_NAME", "ORDINAL_POSITION", "COLUMN_NAME", "REFERENCED_TABLE_SCHEMA", "REFERENCED_TABLE_NAME", "REFERENCED_COLUMN_NAME"], "1=1"),
    "references": ("REFERENTIAL_CONSTRAINTS", ["TABLE_NAME", "CONSTRAINT_NAME", "REFERENCED_TABLE_NAME", "UPDATE_RULE", "DELETE_RULE"], "1=1"),
    "checks": ("CHECK_CONSTRAINTS", ["TABLE_NAME", "CONSTRAINT_NAME", "CHECK_CLAUSE"], "1=1"),
}


def migration_digest(province):
    digest = hashlib.sha256()
    for location in ("common", province):
        for path in sorted((ROOT / "database/mysql/migration" / location).glob("V*.sql")):
            digest.update((location + "/" + path.name + "\0").encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()


def validate_policy(metadata):
    policy = json.loads((ROOT / "database/mysql/collation-policy.json").read_text())
    columns = {(row[0], row[1]): row for row in metadata["columns"]}
    for override in policy["columns"]:
        column = columns.get((override["table"], override["column"]))
        if not column or column[6] != policy["charset"] or column[7] != override["collation"]:
            raise ValueError("reference schema violates the explicit collation policy for {table}.{column}".format(**override))


def validate_seed_floors(client, schema, province, tables):
    """Ensure current manifests accept a fresh Flyway install's reference rows."""
    manifest = json.loads((ROOT / "debian/assets/o19-manifest/o19map_schema.json").read_text())
    profile = manifest if province == manifest["O19_PROFILE"] else manifest["PROFILES"][province]
    floors = profile["SEED_ROW_COUNTS"]
    if not floors:
        raise ValueError("manifest has no seed floors")
    managed = {table: entry["class"] for table, entry in profile["TABLES"].items()
               if entry["class"] in ("copy", "merge") and table in tables}
    tolerated = set(profile.get("PRISTINE_TOLERATED_TABLES", []))
    sql = " UNION ALL ".join("SELECT '{0}', COUNT(*) FROM `{1}`.`{0}`".format(table, schema)
                             for table in sorted(managed))
    cp = subprocess.run(client + ["--batch", "--raw", "--skip-column-names"],
                        input=sql, text=True, capture_output=True, check=True)
    for line in cp.stdout.splitlines():
        table, count = line.split("\t")
        expected = floors.get(table, 0)
        if table in tolerated:
            continue
        if int(count) < expected or (managed[table] == "copy" and int(count) != expected):
            raise ValueError("manifest seed expectation for {0} ({1}, {2}) differs from fresh Flyway rows ({3}); update the curated table ruling/seed counts and regenerate manifests".format(table, managed[table], expected, count))


def export(client, schema, province):
    if not schema.replace("_", "").isalnum():
        raise ValueError("reference database name must be a plain identifier")
    history = subprocess.run(client + ["--batch", "--raw", "--skip-column-names"],
        input="SELECT version, type, script, success FROM `{0}`.flyway_schema_history ORDER BY installed_rank".format(schema),
        text=True, capture_output=True, check=True).stdout.splitlines()
    actual = set()
    for row in history:
        version, kind, script, success = row.split("\t")
        if kind != "SQL" or success != "1":
            raise ValueError("contract export requires a fresh, fully migrated reference DB without baselined/failed entries")
        actual.add(script)
    expected = {path.name for location in ("common", province)
                for path in (ROOT / "database/mysql/migration" / location).glob("V*.sql")}
    if actual != expected:
        raise ValueError("reference DB has not applied exactly the current migration set")
    metadata = {}
    for kind, (source, columns, predicate) in FIELDS.items():
        schema_field = ("SCHEMA_NAME" if source == "SCHEMATA" else
                        "CONSTRAINT_SCHEMA" if source in ("REFERENTIAL_CONSTRAINTS", "CHECK_CONSTRAINTS") else "TABLE_SCHEMA")
        statement = "SELECT JSON_ARRAY({0}) FROM information_schema.{1} WHERE {2} = '{3}' AND {4}".format(
            ", ".join(columns), source, schema_field, schema, predicate)
        cp = subprocess.run(client + ["--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"],
                            input=statement, text=True, capture_output=True, check=True)
        values = []
        for line in cp.stdout.splitlines():
            row = json.loads(line)
            if kind == "defaults":
                row[0] = "$self"
            if row[0] == "flyway_schema_history" or row[0].startswith("import_archived_"):
                continue
            if kind == "columns" and row[1].startswith("import_archived_"):
                continue
            if kind == "keys" and row[4] == schema:
                row[4] = "$self"
            values.append(row)
        metadata[kind] = sorted(values, key=lambda row: json.dumps(row, ensure_ascii=True))
    validate_policy(metadata)
    validate_seed_floors(client, schema, province, {row[0] for row in metadata["tables"]})
    return {"format": FORMAT, "province": province, "migration_sha256": migration_digest(province), "schema": metadata}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database")
    parser.add_argument("--province", choices=("on", "bc"))
    parser.add_argument("--client-arg", action="append", default=[])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--verify-files", action="store_true", help="verify contract/migration/policy binding without a database")
    parser.add_argument("--verify-war", type=Path, help="also verify that the WAR contains exactly these migration bytes")
    args = parser.parse_args()
    if args.verify_files:
        for province in ("on", "bc"):
            document = json.loads((ROOT / "database/mysql/schema-contract" / (province + ".json")).read_text())
            if document.get("format") != FORMAT or document.get("province") != province or document.get("migration_sha256") != migration_digest(province):
                raise SystemExit("schema contract is stale for " + province)
            validate_policy(document["schema"])
        if args.verify_war:
            expected = {"WEB-INF/classes/db/migration/" + location + "/" + path.name: path.read_bytes()
                        for location in ("common", "on", "bc")
                        for path in (ROOT / "database/mysql/migration" / location).glob("V*.sql")}
            with zipfile.ZipFile(args.verify_war) as archive:
                actual = {name for name in archive.namelist()
                          if name.startswith("WEB-INF/classes/db/migration/") and name.endswith(".sql")}
                if actual != set(expected) or any(archive.read(name) != value for name, value in expected.items()):
                    raise SystemExit("WAR migration payload differs from the packaged schema contract; rebuild the WAR")
        return
    if not args.database or not args.province:
        parser.error("--database and --province are required unless --verify-files is used")
    output = args.output or ROOT / "database/mysql/schema-contract" / (args.province + ".json")
    document = export(["mariadb"] + args.client_arg, args.database, args.province)
    serialized = json.dumps(document, indent=1, sort_keys=True, ensure_ascii=True) + "\n"
    if args.check:
        if output.read_text() != serialized:
            raise SystemExit("physical schema contract differs: regenerate it from a fresh Flyway reference DB")
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized)


if __name__ == "__main__":
    main()
