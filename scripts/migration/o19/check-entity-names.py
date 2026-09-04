#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Audit O19 -> CARLOS column names against the ENTITY MODELS, not the DDL.

`generate_manifests.py` matches columns by name, case-folded, and refuses
to emit a manifest while any table has both an unmatched O19 column and an
unwritten CARLOS column -- the signature of a rename it cannot see. That
catches the case where a rename leaves a hole on each side.

It cannot catch a rename where BOTH sides happen to have a column of the
matching name (a swap), and it says nothing about intent. The entity
models can: O19 and CARLOS both annotate the same JPA field with
`@Column(name = ...)`, so a field whose column name differs between the
two trees is a rename stated by the code itself.

This is an audit tool, not part of the build: it needs an OSCAR 19
checkout, which CI does not have, and the generator's own refusal is what
gates a manifest. Run it when curating renames, or when a schema diff
looks surprising.

    python3 scripts/migration/o19/check-entity-names.py \\
        --oscar-src /path/to/oscar [--carlos-src src/main/java]

Exit status: 0 clean, 1 mismatches found, 2 bad arguments.
"""

import argparse
import os
import re
import sys

TABLE_RE = re.compile(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"')
COLUMN_RE = re.compile(r'@Column\s*\(([^)]*)\)', re.S)
NAME_RE = re.compile(r'name\s*=\s*"([^"]+)"')
FIELD_RE = re.compile(
    r'(?:private|protected|public)\s+[\w<>,\[\]\.\s]+?\s+(\w+)\s*[;=]')

#: Hibernate quotes reserved words in @Column(name = "`value`"); the DDL
#: column is unquoted, and the manifest is built from the DDL.
QUOTES = '`"[]'


def parse_entity(path):
    """(class, table, {java field: db column}) for one .java file, or None
    when it declares no mapped columns."""
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            src = fh.read()
    except OSError:
        return None
    columns = {}
    for match in COLUMN_RE.finditer(src):
        name = NAME_RE.search(match.group(1))
        if not name:
            continue
        # the field declaration follows the annotation; 400 chars is well
        # past any javadoc or further annotations between them
        field = FIELD_RE.search(src[match.end():match.end() + 400])
        if field:
            columns[field.group(1)] = name.group(1).strip(QUOTES)
    if not columns:
        return None
    table = TABLE_RE.search(src)
    cls = os.path.basename(path)[:-len(".java")]
    return cls, (table.group(1) if table else cls), columns


def scan(root):
    """{class: (table, {field: column})} for every mapped entity under
    `root`. Keyed by CLASS because the packages differ between the two
    trees (org.oscarehr.* vs io.github.carlos_emr.carlos.*) while the
    class names are what survived the migration."""
    out = {}
    for dirpath, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith(".java"):
                continue
            parsed = parse_entity(os.path.join(dirpath, name))
            if parsed:
                out.setdefault(parsed[0], parsed[1:])
    return out


def mismatches(o19, carlos):
    """Every place the two trees give one entity field two different
    database names, table names included."""
    found = []
    for cls in sorted(set(o19) & set(carlos)):
        (o19_table, o19_cols) = o19[cls]
        (carlos_table, carlos_cols) = carlos[cls]
        if o19_table.lower() != carlos_table.lower():
            found.append(("table", cls, o19_table, carlos_table))
        for field in sorted(set(o19_cols) & set(carlos_cols)):
            if o19_cols[field].lower() != carlos_cols[field].lower():
                found.append(("column", "{0}.{1}".format(cls, field),
                              o19_cols[field], carlos_cols[field]))
    return found


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--oscar-src", required=True,
                    help="path to an oscaremr/oscar (OSCAR 19) checkout")
    ap.add_argument("--carlos-src", default="src/main/java",
                    help="CARLOS java root (default: src/main/java)")
    args = ap.parse_args(argv)
    for path in (args.oscar_src, args.carlos_src):
        if not os.path.isdir(path):
            return ap.error("not a directory: {0}".format(path))

    o19 = scan(os.path.join(args.oscar_src, "src", "main", "java"))
    carlos = scan(args.carlos_src)
    shared = set(o19) & set(carlos)
    print("mapped entities: O19 {0}, CARLOS {1}, shared {2}".format(
        len(o19), len(carlos), len(shared)))
    found = mismatches(o19, carlos)
    if not found:
        print("no name mismatches: every shared entity field maps to the "
              "same database column on both sides")
        return 0
    print("\n{0} name mismatch(es) -- each is a rename the DDL comparison "
          "cannot see:".format(len(found)))
    for kind, where, a, b in found:
        print("  {0:<7} {1:<44} O19={2:<26} CARLOS={3}".format(
            kind, where, a, b))
    return 1


if __name__ == "__main__":
    sys.exit(main())
