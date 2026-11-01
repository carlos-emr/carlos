# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location(
    "schema_audit", root / "scripts/migration/o19/generate_manifests.py"
)
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)
text_caps = {
    "tinytext": 255,
    "text": 65535,
    "mediumtext": 16777215,
    "longtext": 4294967295,
}
blob_caps = {k.replace("text", "blob"): v for k, v in text_caps.items()}


def capacity(decl):
    """Describe a SQL text/binary declaration, including its size unit."""
    decl = decl.strip()
    match = re.match(r"\w+", decl.lower())
    kind = match.group() if match else ""
    sized = re.match(r"(?:var)?(char|binary)\((\d+)\)", decl.lower())
    if sized:
        return (
            "binary" if sized.group(1) == "binary" else "text",
            int(sized.group(2)),
            kind,
            False,
        )
    if kind in text_caps:
        return ("text", text_caps[kind], kind, True)
    if kind in blob_caps:
        return ("binary", blob_caps[kind], kind, True)
    return None


BASE_SCHEMA = g.Schema


class SourceCapacitySchema(BASE_SCHEMA):
    """Keep the largest declaration across supported historical patches."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.largest = {}

    def remember(self, table, columns=None):
        """Record maximum historical capacities independently of union precedence."""
        for col, declaration in (
            self.tables.get(table, {}) if columns is None else columns
        ).items():
            table = table.lower()
            col = col.lower()
            cap = capacity(declaration)
            old = capacity(self.largest.get(table, {}).get(col, ""))
            if cap and (not old or cap[1] > old[1]):
                self.largest.setdefault(table, {})[col] = declaration

    def apply_create(self, name, body, if_not_exists=False):
        """Inspect each incoming CREATE even when union mode retains an earlier one."""
        incoming = BASE_SCHEMA()
        incoming.apply_create(name, body)
        self.remember(name, incoming.tables.get(name, {}))
        super().apply_create(name, body, if_not_exists)
        self.remember(name)

    def apply_alter(self, name, clauses):
        """Record each intermediate ALTER so a later narrowing cannot hide it."""
        for clause in g._split_top_level(clauses):
            super().apply_alter(name, clause)
            self.remember(name)


def audit(oscar_src, carlos_baseline, manifest):
    """Recommend lossless capacities only after validating every configured source."""
    source_files = {province: g.expand_sources(oscar_src, g.o19_sql_sources(province), strict=True)
                    for province in ("on", "bc")}
    changes = {}
    for province in ("on", "bc"):
        original_schema = g.Schema
        g.Schema = SourceCapacitySchema
        try:
            source = g.load_schema(
                source_files[province],
                if_not_exists_mode="union",
            ).largest
        finally:
            g.Schema = original_schema
        migration = carlos_baseline / "database/mysql/migration"
        target = g.load_schema(
            g.carlos_migration_files([migration / "common", migration / province])
        ).tables
        tables = (
            manifest["TABLES"]
            if province == "on"
            else manifest["PROFILES"][province]["TABLES"]
        )
        for table, entry in tables.items():
            if entry["class"] not in ("copy", "merge"):
                continue
            for col in entry["cols"]:
                src = entry.get("renames", {}).get(col, col)
                sd = source.get(table.lower(), {}).get(src.lower(), "")
                td = target.get(table, {}).get(col, "")
                a, b = capacity(sd), capacity(td)
                if not a or not b or a[0] != b[0]:
                    continue
                family, source_limit, _source_kind, source_bytes = a
                _, target_limit, target_kind, target_bytes = b
                required = source_limit * (
                    4 if family == "text" and (target_bytes or source_bytes) else 1
                )
                if required <= target_limit:
                    continue
                if not target_bytes and source_limit <= 16383:
                    kind = ({"char": "varchar", "binary": "varbinary"}.get(
                        target_kind, target_kind) if source_limit > 255 else target_kind)
                    newtype = kind + "(" + str(source_limit) + ")"
                else:
                    if family == "text" and not source_bytes and not target_bytes:
                        required *= (
                            4  # a VARCHAR that must move to a byte-sized text tier
                        )
                    options = text_caps if family == "text" else blob_caps
                    candidates = [(v, k) for k, v in options.items() if v >= required]
                    if not candidates:
                        print(
                            f"{table}.{col}: declared character-set expansion exceeds the engine maximum; actual values remain subject to the import capacity guard",
                            file=sys.stderr,
                        )
                        continue
                    newtype = min(candidates)[1].upper()
                key = (table, col)
                item = {
                    "table": table,
                    "column": col,
                    "source": sd,
                    "target": td,
                    "new_type": newtype,
                    "required_utf8_bytes": required
                    if not newtype.lower().startswith(("varchar(", "char("))
                    else None,
                    "provinces": [province],
                }
                if key in changes:
                    old = changes[key]
                    if capacity(newtype)[1] > capacity(old["new_type"])[1]:
                        item["provinces"] = old["provinces"] + [province]
                        changes[key] = item
                    else:
                        old["provinces"].append(province)
                else:
                    changes[key] = item
    rows = sorted(changes.values(), key=lambda x: (x["table"], x["column"]))
    return rows


def main():
    """Write a complete source audit; a failed audit leaves existing output intact."""
    ap = argparse.ArgumentParser(
        description="Audit OSCAR historical text capacity against a CARLOS baseline checkout."
    )
    ap.add_argument("--oscar-src", type=Path, required=True)
    ap.add_argument("--carlos-baseline", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    manifest = json.loads(
        (root / "debian/assets/o19-manifest/o19map_schema.json").read_text()
    )
    rows = audit(args.oscar_src, args.carlos_baseline, manifest)
    args.out.write_text(json.dumps(rows, indent=2) + "\n")
    print("PROACTIVE TEXT/BINARY CAPACITY CHANGES:", len(rows))
    for r in rows:
        print(
            r["table"] + "." + r["column"],
            r["target"],
            "=>",
            r["new_type"],
            ",".join(r["provinces"]),
        )


if __name__ == "__main__":
    main()
