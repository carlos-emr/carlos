# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Clinic-side content digests (M22 stage 1 of the end-to-end chain).

`o19_preflight.py` is the only tool in the set that runs on the clinic's
OSCAR 19 server, so it is where the pre-dump content can be measured. The
numbers it emits here are what `carlos-ctl import-o19` compares against
the restored staging schema at P2 -- the only check that can say the
dump, the transfer and the restore carried every VALUE rather than merely
the right number of rows.

Everything below is about the COLLECTION: what gets digested, what
happens when a table cannot be, and what the emitted document says. That
the SQL itself is the same SQL the import side builds is pinned
separately, in test_sql_escape_contract.py.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_preflight_digests -v
"""

import json
import os
import shutil
import tempfile
import unittest

from carlos_ctl import o19_preflight as pf


class FakeDb(object):
    """Answers the two shapes `collect_digests` issues.

    `columns` is ordered per table on purpose: information_schema is
    asked for ORDINAL_POSITION order and the digest depends on it, so a
    fake that sorted would hide a collection that re-sorted."""

    def __init__(self, columns, digests=None, fail=None, malformed=None):
        self.columns = columns
        self.digests = dict(digests or {})
        self.fail = dict(fail or {})
        self.malformed = dict(malformed or {})
        self.queries = []

    def __call__(self, sql):
        self.queries.append(sql)
        if "information_schema.TABLES" in sql:
            return [[t] for t in self.columns]
        if "information_schema.COLUMNS" in sql:
            out = []
            for table, cols in self.columns.items():
                for name, coltype in cols:
                    out.append([table, name, coltype])
            return out
        table = sql.split("FROM `", 1)[1].split("`", 1)[0]
        if table in self.fail:
            raise RuntimeError(self.fail[table])
        if table in self.malformed:
            return self.malformed[table]
        return [list(self.digests.get(table, ("0", "0", "0")))]

    def digest_query_for(self, table):
        marker = "FROM `{0}`".format(table)
        found = [q for q in self.queries if q.endswith(marker)]
        assert len(found) == 1, (table, found)
        return found[0]


SCHEMA = {
    # deliberately NOT alphabetical: ORDINAL_POSITION order is part of
    # the digest, and a collection that sorted would change the hash
    "demographic": [("demographic_no", "int"), ("last_name", "varchar"),
                    ("first_name", "varchar")],
    "document": [("document_no", "int"), ("contents", "blob")],
}


class TestEveryTableIsMeasured(unittest.TestCase):

    def setUp(self):
        self.db = FakeDb(SCHEMA, digests={
            "demographic": ("3", "123456789012345678901234567890", "77"),
            "document": ("1", "5", "6")})
        self.doc = pf.collect_digests(self.db, "'oscar'",
                                      pf.base_table_names(self.db,
                                                          "'oscar'"),
                                      province="on", db_name="oscar")

    def test_each_table_gets_an_entry(self):
        self.assertEqual(sorted(self.doc["tables"]),
                         ["demographic", "document"])
        self.assertEqual(self.doc["errors"], {})

    def test_the_numbers_survive_a_json_round_trip(self):
        """The SUM lane is a DECIMAL(30, 0) and outruns 64 bits; carried
        as a JSON number it would be rounded by readers that use a
        double, and a rounded digest matches a corrupted table."""
        entry = json.loads(json.dumps(self.doc))["tables"]["demographic"]
        self.assertEqual(entry["rows"], 3)
        self.assertEqual(int(entry["total"]),
                         123456789012345678901234567890)
        self.assertEqual(int(entry["parity"]), 77)

    def test_the_column_order_is_the_servers_order_not_sorted(self):
        self.assertEqual(
            [c for c, _t in self.doc["tables"]["demographic"]["columns"]],
            ["demographic_no", "last_name", "first_name"])

    def test_the_types_travel_with_the_columns(self):
        self.assertEqual(self.doc["tables"]["document"]["columns"],
                         [["document_no", "int"], ["contents", "blob"]])

    def test_the_query_is_unqualified_so_it_follows_the_selected_db(self):
        # the clinic runs one `mysql <db>` per query and has no second
        # schema in reach; a qualified name would guess the db name
        self.assertTrue(
            self.db.digest_query_for("demographic").endswith(
                "FROM `demographic`"))

    def test_a_blob_column_is_hexed_rather_than_converted(self):
        sql = self.db.digest_query_for("document")
        self.assertIn("HEX(`contents`)", sql)
        self.assertNotIn("CONVERT(`contents`", sql)

    def test_text_columns_are_normalised_to_utf8mb4(self):
        # the clinic stores latin1 and staging is utf8mb4: without this
        # the two sides disagree on every accented row of every clinic
        self.assertIn("CONVERT(`last_name` USING utf8mb4)",
                      self.db.digest_query_for("demographic"))

    def test_the_envelope_says_what_was_measured_and_how(self):
        self.assertEqual(self.doc["digest_format"], pf.DIGEST_FORMAT)
        self.assertEqual(self.doc["schema_map_version"],
                         pf.SCHEMA_MAP_VERSION)
        self.assertEqual(self.doc["province"], "on")
        self.assertEqual(self.doc["database"], "oscar")
        self.assertTrue(self.doc["generated_at"])


class TestATableThatCannotBeMeasuredIsNotReportedAsMeasured(
        unittest.TestCase):

    """The fail-closed half.

    A table recorded with zeros reads downstream as "empty, and
    verified"; the one table nobody could measure is exactly the one an
    operator must be told about."""

    def _collect(self, db):
        return pf.collect_digests(db, "'oscar'",
                                  pf.base_table_names(db, "'oscar'"))

    def test_a_refused_table_lands_in_errors_not_in_tables(self):
        db = FakeDb(SCHEMA, fail={
            "document": "ERROR 1142: SELECT command denied"})
        doc = self._collect(db)
        self.assertNotIn("document", doc["tables"])
        self.assertIn("SELECT command denied", doc["errors"]["document"])

    def test_one_refused_table_does_not_cost_the_others(self):
        db = FakeDb(SCHEMA, fail={"document": "denied"},
                    digests={"demographic": ("3", "9", "8")})
        doc = self._collect(db)
        self.assertIn("demographic", doc["tables"])
        self.assertEqual(list(doc["errors"]), ["document"])

    def test_only_the_last_line_of_a_client_error_is_kept(self):
        # the client's stderr may carry the whole connection banner; the
        # document is shipped to another organisation
        db = FakeDb(SCHEMA, fail={"document": "banner\nERROR 1142: denied"})
        doc = self._collect(db)
        self.assertEqual(doc["errors"]["document"], "ERROR 1142: denied")

    def test_a_table_information_schema_does_not_describe_is_an_error(self):
        db = FakeDb(SCHEMA)
        names = pf.base_table_names(db, "'oscar'") + ["invisible"]
        doc = pf.collect_digests(db, "'oscar'", names)
        self.assertNotIn("invisible", doc["tables"])
        self.assertIn("no columns", doc["errors"]["invisible"])

    def test_an_empty_result_is_an_error_not_an_empty_table(self):
        db = FakeDb(SCHEMA, malformed={"document": []})
        doc = self._collect(db)
        self.assertNotIn("document", doc["tables"])
        self.assertIn("no row", doc["errors"]["document"])

    def test_a_non_numeric_answer_is_an_error_not_a_zero(self):
        db = FakeDb(SCHEMA, malformed={"document": [["NULL", "x", "y"]]})
        doc = self._collect(db)
        self.assertNotIn("document", doc["tables"])
        self.assertIn("document", doc["errors"])

    def test_a_column_with_no_safe_rendering_makes_the_table_unmeasured(
            self):
        """One exotic column costs the whole table, on purpose: a digest
        over the other columns would read as "this table was verified"
        while the column nobody could hash went unchecked."""
        db = FakeDb({"vendor": [("id", "int"), ("blob_or_not", "widget")]})
        doc = self._collect(db)
        self.assertNotIn("vendor", doc["tables"])
        self.assertIn("blob_or_not", doc["errors"]["vendor"])

    def test_a_short_row_is_an_error(self):
        db = FakeDb(SCHEMA, malformed={"document": [["1", "2"]]})
        doc = self._collect(db)
        self.assertNotIn("document", doc["tables"])
        self.assertIn("no row", doc["errors"]["document"])


class TestWhatGetsDigested(unittest.TestCase):

    def test_views_are_left_out(self):
        """A view carries no rows of its own; digesting one would
        double-count the table underneath and then disagree with staging,
        where the view may not exist at all."""
        db = FakeDb(SCHEMA)
        pf.base_table_names(db, "'oscar'")
        self.assertIn("TABLE_TYPE = 'BASE TABLE'", db.queries[0])

    def test_the_column_types_are_read_in_one_query_for_the_schema(self):
        # ~580 tables, each query a fresh client process
        db = FakeDb(SCHEMA)
        pf.collect_digests(db, "'oscar'", ["demographic", "document"])
        self.assertEqual(
            len([q for q in db.queries
                 if "information_schema.COLUMNS" in q]), 1)

    def test_the_column_query_asks_for_ordinal_position_order(self):
        db = FakeDb(SCHEMA)
        pf.column_types(db, "'oscar'")
        self.assertIn("ORDER BY TABLE_NAME, ORDINAL_POSITION",
                      db.queries[0])


class TestTheDocumentIsWrittenPrivately(unittest.TestCase):

    """It names every table and column in the clinic's schema, and it is
    written on a host the clinic still works on."""

    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="o19digests-")
        self.addCleanup(shutil.rmtree, self.dir, True)

    def test_the_file_is_0600(self):
        path = os.path.join(self.dir, "d.json")
        pf.write_private_json(path, {"tables": {}})
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)

    def test_rewriting_a_loose_file_tightens_it(self):
        # os.open's mode applies to a NEW file only, so a re-run over a
        # report left 0644 by an earlier version would keep that mode
        path = os.path.join(self.dir, "d.json")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("{}")
        os.chmod(path, 0o644)
        pf.write_private_json(path, {"tables": {}})
        self.assertEqual(os.stat(path).st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
