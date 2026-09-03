# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Replay the privilege-table SQL the importer issues against an in-memory
database and compare the EFFECTIVE grant set, not the statement text:
the merge keeps CARLOS's row on a shared (roleUserGroup, objectName),
appends clinic rows, drops only the excluded objects; the backfill adds
each template grant once; the diff/append/exclusion listings name exactly
the rows the review must see; the prevention rewrite is case-sensitive.

sqlite3 (stdlib) stands in for MariaDB: the statements are translated
token-for-token where the dialects differ (`<=>` -> IS, INSERT IGNORE ->
INSERT OR IGNORE, BINARY dropped — sqlite's `=` is already binary).

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import sqlite3
import unittest

from carlos_ctl import o19etl, o19map_schema, o19roles

SRC, DST, ARCH = "src", "dst", "arch"


def col():
    return {"type": "varchar", "nullable": True, "char_len": 0,
            "column_type": "varchar", "has_default": False,
            "auto_increment": False}


def translate(sql):
    return (sql.replace(" <=> ", " IS ")
               .replace("INSERT IGNORE INTO", "INSERT OR IGNORE INTO")
               .replace("BINARY prevention_type", "prevention_type"))


SEED = [("doctor", "_rx", "x", 0), ("doctor", "_billing", "x", 0),
        ("doctor", "_fax", "x", 0), ("doctor", "_email", "x", 0),
        ("nurse", "_rx", "r", 0), ("nurse", "_fax", "x", 0),
        ("admin", "_admin", "x", 0), ("admin", "_pmm.newClient", "x", 0),
        ("-1", "_fax", "x", 0)]
CLINIC = [("doctor", "_rx", "x", 0),            # identical: one row
          ("doctor", "_billing", "r", 0),       # override: CARLOS wins
          ("admin", "_admin.consult", "x", 0),  # stock role, no seed row
          ("nurse", "_admin.traceability", "x", 0),   # dead object
          ("nurse", "_pmm.newClient", "x", 0),  # live object, carried
          ("Triage Nurse", "_rx", "r", 0), ("Triage Nurse", "_pmm", "x", 0),
          ("_all", "_eChart$5", "|or|", 0), ("nurse", "_queue.2", "x", 0),
          ("999997", "_rx", "o", 0)]


class TestPrivilegeReplay(unittest.TestCase):

    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        for schema in (SRC, DST, ARCH):
            self.db.execute("ATTACH DATABASE ':memory:' AS {0}".format(schema))
        for schema in (SRC, DST):
            self.db.execute(
                "CREATE TABLE {0}.secObjPrivilege (roleUserGroup TEXT, "
                "objectName TEXT, privilege TEXT, priority INTEGER, "
                "provider_no TEXT, PRIMARY KEY (roleUserGroup, objectName))"
                .format(schema))
        self.db.executemany(
            "INSERT INTO dst.secObjPrivilege VALUES (?, ?, ?, ?, NULL)", SEED)
        self.db.executemany(
            "INSERT INTO src.secObjPrivilege VALUES (?, ?, ?, ?, '999998')",
            CLINIC)
        self.entry = dict(o19map_schema.TABLES["secObjPrivilege"])
        self.cols = {c: col() for c in self.entry["cols"]}

    def run_sql(self, sql):
        return self.db.execute(translate(sql)).fetchall()

    def grants(self, schema=DST):
        return set(self.db.execute(
            "SELECT roleUserGroup, objectName, privilege FROM {0}."
            "secObjPrivilege".format(schema)).fetchall())

    def snapshot(self):
        for sql in o19roles.snapshot_statements(DST, ARCH):
            if "secObjPrivilege" in sql:
                self.run_sql(sql)

    def merge(self):
        self.run_sql(o19etl.merge_statement("secObjPrivilege", self.entry,
                                            SRC, DST, self.cols))

    def test_merge_yields_seed_union_clinic_minus_overrides_and_dead(self):
        self.snapshot()
        self.merge()
        expected = {(r, o, p) for r, o, p, _ in SEED}
        seed_keys = {(r, o) for r, o, _, _ in SEED}
        expected |= {(r, o, p) for r, o, p, _ in CLINIC
                     if (r, o) not in seed_keys
                     and o != "_admin.traceability"}
        self.assertEqual(self.grants(), expected)
        # cardinality: 9 seed + 10 clinic - 2 shared keys - 1 excluded
        self.assertEqual(len(self.grants()), 16)
        # CARLOS's value stands on the shared key
        self.assertIn(("doctor", "_billing", "x"), self.grants())
        self.assertNotIn(("doctor", "_billing", "r"), self.grants())
        # live _pmm objects and the non-role groups ride along
        for row in (("nurse", "_pmm.newClient", "x"), ("Triage Nurse",
                                                       "_pmm", "x"),
                    ("_all", "_eChart$5", "|or|"), ("nurse", "_queue.2", "x"),
                    ("999997", "_rx", "o")):
            self.assertIn(row, self.grants())

    def test_merge_is_idempotent(self):
        self.snapshot()
        self.merge()
        once = self.grants()
        self.merge()
        self.assertEqual(self.grants(), once)

    def test_backfill_adds_each_template_grant_once_and_never_overwrites(
            self):
        self.snapshot()
        self.merge()
        era = ["_fax", "_email", "_rx"]  # _rx is NOT era, but the IGNORE
        #                                  must never overwrite the row
        pending = o19roles.backfill_pending_count_sql(
            DST, ARCH, "Triage Nurse", "nurse", era)
        self.assertEqual(self.run_sql(pending)[0][0], 1)  # nurse/_fax only
        stmt = o19roles.backfill_statement(DST, ARCH, "Triage Nurse", "nurse",
                                           era)
        self.run_sql(stmt)
        self.assertEqual(self.run_sql(pending)[0][0], 0)
        self.assertIn(("Triage Nurse", "_fax", "x"), self.grants())
        self.assertIn(("Triage Nurse", "_rx", "r"), self.grants())  # kept
        n = len(self.grants())
        self.run_sql(stmt)
        self.assertEqual(len(self.grants()), n)

    def test_review_listings_name_exactly_the_interesting_rows(self):
        self.snapshot()
        self.merge()
        diff = self.run_sql(o19roles.privilege_diff_sql(SRC, ARCH))
        self.assertEqual(diff, [("doctor", "_billing", "r", 0, "x", 0)])
        appends = self.run_sql(o19roles.stock_role_appends_sql(
            SRC, ARCH, ["doctor", "nurse", "admin"]))
        self.assertEqual(appends, [("admin", "_admin.consult", "x", 0),
                                   ("nurse", "_pmm.newClient", "x", 0),
                                   ("nurse", "_queue.2", "x", 0)])
        excluded = self.run_sql(o19roles.excluded_grants_sql(SRC))
        self.assertEqual(excluded, [("nurse", "_admin.traceability", "x")])
        self.assertEqual(self.run_sql(
            o19roles.excluded_grants_count_sql(SRC))[0][0], 1)

    def test_prevention_rewrite_is_case_sensitive(self):
        self.db.execute("CREATE TABLE dst.preventions (id INTEGER PRIMARY "
                        "KEY, prevention_type TEXT)")
        self.db.executemany("INSERT INTO dst.preventions VALUES (?, ?)",
                            [(1, "dTaP"), (2, "DTaP"), (3, "Flu")])
        for legacy, canonical, count_sql, update_sql in \
                o19roles.prevention_type_statements(
                    DST, {"dTaP": "Tdap", "Flu": "Inf"}):
            self.run_sql(update_sql)
        self.assertEqual(self.db.execute(
            "SELECT id, prevention_type FROM dst.preventions ORDER BY id")
            .fetchall(), [(1, "Tdap"), (2, "DTaP"), (3, "Inf")])


if __name__ == "__main__":
    unittest.main()
