# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

ap = argparse.ArgumentParser(
    description="Audit OSCAR init/update text capacity against a CARLOS baseline checkout."
)
ap.add_argument("--oscar-src", type=Path, required=True)
ap.add_argument("--carlos-baseline", type=Path, required=True)
ap.add_argument("--out", type=Path, required=True)
args = ap.parse_args()
root = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location(
    "schema_audit", root / "scripts/migration/o19/generate_manifests.py"
)
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)
manifest = json.loads(
    (root / "debian/assets/o19-manifest/o19map_schema.json").read_text()
)
text_caps = {
    "tinytext": 255,
    "text": 65535,
    "mediumtext": 16777215,
    "longtext": 4294967295,
}
blob_caps = {k.replace("text", "blob"): v for k, v in text_caps.items()}


def capacity(decl):
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


class SourceCapacitySchema(g.Schema):
    """Keep the largest declaration across supported historical patches."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.largest = {}

    def remember(self, table):
        for col, declaration in self.tables.get(table, {}).items():
            cap = capacity(declaration)
            old = capacity(self.largest.get(table, {}).get(col, ""))
            if cap and (not old or cap[1] > old[1]):
                self.largest.setdefault(table, {})[col] = declaration

    def apply_create(self, name, body, if_not_exists=False):
        super().apply_create(name, body, if_not_exists)
        self.remember(name)

    def apply_alter(self, name, clauses):
        super().apply_alter(name, clauses)
        self.remember(name)


changes = {}
for province in ("on", "bc"):
    original_schema = g.Schema
    g.Schema = SourceCapacitySchema
    source = g.load_schema(
        g.expand_sources(args.oscar_src, g.o19_sql_sources(province)),
        if_not_exists_mode="union",
    ).largest
    g.Schema = original_schema
    migration = args.carlos_baseline / "database/mysql/migration"
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
            sd = source.get(table, {}).get(src, "")
            td = target.get(table, {}).get(col, "")
            a, b = capacity(sd), capacity(td)
            if not a or not b or a[0] != b[0]:
                continue
            family, source_limit, source_kind, source_bytes = a
            _, target_limit, target_kind, target_bytes = b
            required = source_limit * (
                4 if family == "text" and (target_bytes or source_bytes) else 1
            )
            if required <= target_limit:
                continue
            if not target_bytes and source_limit <= 16383:
                newtype = target_kind + "(" + str(source_limit) + ")"
            else:
                if family == "text" and not source_bytes and not target_bytes:
                    required *= 4  # a VARCHAR that must move to a byte-sized text tier
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
