#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Preserve exact OSCAR19 medication FLOAT values in a dump of a quiescent source.

Run on the source with the same read-only connection options as the dump.
The generated SQL belongs at the end of that dump, for its staging restore.
Compatible with the Python 3.4 available on older OSCAR19 servers.
"""
import argparse
import re
import subprocess
import sys

TABLE_KEYS = {"drugs": "drugid", "favorites": "favoriteid", "drugdispensingmapping": "id"}
INTEGER_TYPES = ("tinyint", "smallint", "mediumint", "int", "bigint")
NUMBER = re.compile(r"^-?[0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?$")


def identifier(name):
    if not re.match(r"^[A-Za-z0-9_]+$", name):
        raise ValueError("unexpected identifier in medication schema")
    return "`" + name + "`"


def query(command, statement):
    process = subprocess.Popen(command + ["--execute", statement], stdout=subprocess.PIPE)
    try:
        for line in process.stdout:
            yield line.decode("ascii").rstrip("\r\n").split("\t")
    finally:
        process.stdout.close()
        if process.wait():
            raise ValueError("source database query failed")


def export(command, out):
    metadata = list(query(command, """SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_KEY, EXTRA
        FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND LOWER(TABLE_NAME) IN ('drugs','favorites','drugdispensingmapping')
        ORDER BY TABLE_NAME, ORDINAL_POSITION"""))
    tables = {}
    for table, column, kind, key, extra in metadata:
        tables.setdefault(table, []).append((column, kind, key, extra))
    if "drugs" not in [name.lower() for name in tables]:
        raise ValueError("the selected OSCAR19 schema has no visible drugs table")
    out.write("-- Exact source medication values for this staging restore.\n")
    for table in sorted(tables):
        columns = tables[table]
        key = TABLE_KEYS[table.lower()]
        primary = [name.lower() for name, kind, flag, _ in columns if flag == "PRI"]
        integers = [name.lower() for name, kind, _, _ in columns if kind in INTEGER_TYPES]
        floats = [identifier(name) for name, kind, _, extra in columns
                  if kind == "float" and name.lower() in ("takemin", "takemax")
                  and "generated" not in extra.lower()]
        if not floats:
            continue
        if primary != [key] or key not in integers:
            raise ValueError("unsupported medication primary key on " + table)
        stamps = [identifier(name) for name, _, _, extra in columns if "on update" in extra.lower()]
        # Adding an approximate zero widens a binary32 exactly even on servers that
        # predate CAST AS DOUBLE. Direct conversion of a FLOAT prints only six digits.
        statement = "SELECT {0}, {1} FROM {2} WHERE {3} ORDER BY {0}".format(
            identifier(key), ", ".join(col + " + 0e0" for col in floats), identifier(table),
            " OR ".join(col + " IS NOT NULL" for col in floats))
        for row in query(command, statement):
            if len(row) != len(floats) + 1 or not re.match(r"^-?[0-9]+$", row[0]):
                raise ValueError("invalid medication identifier or result shape")
            values = row[1:]
            if any(value != "NULL" and not NUMBER.match(value) for value in values):
                raise ValueError("unrecognised medication FLOAT representation")
            assignments = [col + "=" + value for col, value in zip(floats, values)]
            assignments.extend(col + "=" + col for col in stamps)
            out.write("UPDATE {0} SET {1} WHERE {2}={3};\n".format(
                identifier(table), ", ".join(assignments), identifier(key), row[0]))


def complete_dump(source, destination, command):
    """Keep the vendor's genuine completion record last while streaming large SQL statements."""
    tail = b""
    for chunk in iter(lambda: source.read(1024 * 1024), b""):
        tail += chunk
        if len(tail) > 8192:
            destination.write(tail[:-8192])
            tail = tail[-8192:]
    trailer = re.search(rb"(?:^|\n)(-- Dump completed[^\r\n]*\r?\n?)[ \t\r\n]*\Z", tail)
    if not trailer:
        raise ValueError("the source dump lacks its final completion record")
    destination.write(tail[:trailer.start(1)])

    class SqlOutput(object):
        def write(self, text):
            destination.write(text.encode("ascii"))

    export(command, SqlOutput())
    destination.write(tail[trailer.start(1):])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True)
    parser.add_argument("--mysql-cmd", default="mysql")
    parser.add_argument("--mysql-arg", action="append", default=[])
    parser.add_argument("--complete-dump", action="store_true",
                        help="read a completed vendor dump from stdin and write the exact dump to stdout")
    args = parser.parse_args()
    if not re.match(r"^[A-Za-z0-9_]+$", args.db):
        parser.error("invalid database name")
    if any(opt.startswith("-p") or opt.startswith("--password") for opt in args.mysql_arg):
        parser.error("use a client defaults file or MYSQL_PWD for credentials")
    command = [args.mysql_cmd] + args.mysql_arg + ["--quick", "--batch", "--raw", "--skip-column-names",
               "--database=" + args.db]
    if args.complete_dump:
        complete_dump(sys.stdin.buffer, sys.stdout.buffer, command)
    else:
        export(command, sys.stdout)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        sys.exit("medication FLOAT export failed: " + str(error))
