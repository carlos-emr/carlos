# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Contracts of the repo-side manifest generator that the shipped modules
depend on: Flyway version ordering, ADD COLUMN IF NOT EXISTS parsing, and
the credential-key filter that keeps stock secrets out of the manifest.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import importlib.util
import types
import unittest
from pathlib import Path

from carlos_ctl import o19etl, o19map_schema

GEN = Path(__file__).resolve().parents[4] / "scripts" / "migration" / \
    "o19" / "generate_manifests.py"


def load_generator():
    spec = importlib.util.spec_from_file_location("generate_manifests", GEN)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestGenerator(unittest.TestCase):

    """Contracts of the repo-side manifest generator.

    Flyway ordering, the CREATE/ALTER parsing the CARLOS side is built
    from, the seed counters the pristine gate depends on, and the
    secret-key filter that keeps stock credentials out of the shipped
    manifest."""
    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def test_flyway_files_sort_by_numeric_version(self):
        names = ["V1.0.10__a.sql", "V1.0.2__b.sql", "V1__base.sql",
                 "V1.0.9__c.sql", "V1.0.13__d.sql"]
        ordered = sorted((Path(n) for n in names),
                         key=self.gen.flyway_version)
        self.assertEqual([p.name for p in ordered],
                         ["V1__base.sql", "V1.0.2__b.sql", "V1.0.9__c.sql",
                          "V1.0.10__a.sql", "V1.0.13__d.sql"])

    def test_migration_dirs_are_merged_in_version_order(self):
        files = self.gen.carlos_migration_files(
            [self.gen.MIGRATION_DIR / "common",
             self.gen.MIGRATION_DIR / "on"])
        versions = [self.gen.flyway_version(f) for f in files]
        self.assertEqual(versions, sorted(versions))
        self.assertTrue(files[0].name.startswith("V1__"))

    def test_add_column_if_not_exists_records_the_real_column(self):
        schema = self.gen.Schema("skip")
        schema.feed("CREATE TABLE t (id INT NOT NULL PRIMARY KEY);\n"
                    "ALTER TABLE t ADD COLUMN IF NOT EXISTS direction "
                    "VARCHAR(8) NOT NULL DEFAULT 'out';\n"
                    "ALTER TABLE t ADD IF NOT EXISTS `flag` TINYINT;\n")
        cols = schema.tables["t"]
        self.assertIn("direction", cols)
        self.assertIn("flag", cols)
        self.assertNotIn("IF", cols)
        self.assertNotIn("if", cols)

    def test_parenthesized_add_column_form_is_parsed(self):
        schema = self.gen.Schema("skip")
        schema.feed("CREATE TABLE t (id INT NOT NULL PRIMARY KEY);\n"
                    "ALTER TABLE t ADD COLUMN (a INT, b VARCHAR(5));\n"
                    "ALTER TABLE t ADD (c DATE);\n")
        self.assertEqual(sorted(schema.tables["t"]), ["a", "b", "c", "id"])

    def test_tab_after_double_dash_starts_a_comment(self):
        stripped = self.gen.strip_line_comments(
            "SELECT 1;\n--\tCREATE TABLE gone (x INT);\nSELECT 2;\n")
        self.assertNotIn("gone", stripped)
        self.assertIn("SELECT 2", stripped)

    def test_seed_counter_ignores_comments_between_tuples(self):
        text = ("INSERT INTO `t` VALUES\n(1,'a'),\n(2,'b'),\n"
                "-- a note between tuples\n(3,'c');\n")
        stripped = self.gen.strip_line_comments(text)
        self.assertEqual(self.gen.count_insert_rows(stripped), {"t": 3})

    def test_seed_counter_counts_insert_ignore_tuples(self):
        # forward migrations seed whole lookup tables with INSERT IGNORE
        # (V1.0.5: bed_type, lst_*); skipping them left copy-class floors
        # of 0 that every Flyway-built target violated at P0
        text = ("INSERT INTO `t` VALUES (1,'a'),(2,'b');\n"
                "INSERT IGNORE INTO `t` (a, b) VALUES (3,'c');\n"
                "INSERT IGNORE INTO `u` VALUES (1,'z');\n")
        self.assertEqual(self.gen.count_insert_rows(text), {"t": 3, "u": 1})

    def test_seed_string_column_reads_the_quoted_field(self):
        text = ("INSERT INTO `secRole` VALUES (1,'doctor','doctor'),"
                "(2,'Site Manager','Site Manager'),\n(3,'O\\'Neil','x'),"
                "(4,'O''Brien','x'),(5,'back\\\\slash','x');\n"
                "INSERT INTO `other` VALUES (9,'nope','n');\n")
        self.assertEqual(self.gen.seed_string_column(text, "secRole", 1),
                         ["doctor", "Site Manager", "O'Neil", "O'Brien",
                          "back\\slash"])

    def test_prevention_type_map_parses_direct_updates_only(self):
        text = ("UPDATE preventions SET prevention_type = 'Inf' WHERE "
                "prevention_type = 'Flu';\n"
                "UPDATE preventions SET prevention_type = 'Inf' WHERE "
                "prevention_type = 'Influenza';\n"
                "UPDATE preventionsExt pe JOIN preventions p ON pe.id = p.id "
                "SET pe.val = 'x' WHERE p.prevention_type NOT IN ('Inf');\n")
        self.assertEqual(self.gen.parse_prevention_type_map(text),
                         {"Flu": "Inf", "Influenza": "Inf"})
        with self.assertRaises(SystemExit):
            self.gen.parse_prevention_type_map(
                text + "UPDATE preventions SET prevention_type = 'Var' "
                       "WHERE prevention_type = 'Flu';\n")

    def test_prevention_items_parser_reads_item_names(self):
        xml = ('<items><item\n  name="Inf"\n  desc="flu"/>'
               '<item name="Var" desc="v"/><other name="no"/></items>')
        self.assertEqual(self.gen.parse_prevention_items(xml),
                         ["Inf", "Var"])

    def test_secret_key_filter(self):
        secret = ("db_password", "hcv.service.pass", "clinicaid_api_key",
                  "hcv.service.conformanceKey", "PGP_KEY", "email.password",
                  "hcv.service.user", "TOMCAT_KEYSTORE_PASSWORD")
        plain = ("password_min_length", "mandatory_password_reset",
                 "casemgmt.note.password.enabled", "email.host",
                 "billregion", "IGNORE_PASSWORD_REQUIREMENTS")
        for k in secret:
            self.assertTrue(self.gen.is_secret_key(k), k)
        for k in plain:
            self.assertFalse(self.gen.is_secret_key(k), k)

    def test_generated_modules_carry_no_wall_clock_stamp(self):
        ctl = self.gen.CTL_DIR
        for name in ("o19map_schema.py", "o19map_props.py"):
            text = (ctl / name).read_text(encoding="utf-8")
            self.assertNotIn("GENERATED_AT", text)


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestTheDdlParser(unittest.TestCase):

    """Parse cases where getting it wrong drops a column silently.

    A column the parser cannot see is not copied, not listed as
    `dropped`, not shadow-captured and invisible to the unruled-rename
    gate -- a silent data drop, not a parse warning."""

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def parse(self, ddl, mode="union"):
        schema = self.gen.Schema(mode)
        schema.feed(ddl)
        return schema

    def test_a_backticked_reserved_word_is_a_column_not_a_constraint(self):
        # MySQL requires the quoting precisely so a column may be called
        # `key`; O19 has one (phr_document_ext.`key`)
        s = self.parse("CREATE TABLE t (`id` int, `key` varchar(255), "
                       "`value` text, PRIMARY KEY (`id`));")
        self.assertEqual(sorted(s.tables["t"]), ["id", "key", "value"])
        self.assertEqual(s.pks["t"], ["id"])

    def test_an_unquoted_constraint_clause_is_still_not_a_column(self):
        # the other direction: the fix must not turn KEY/UNIQUE clauses
        # into columns called "key" and "unique"
        s = self.parse("CREATE TABLE t (id int, name varchar(20), "
                       "PRIMARY KEY (id), KEY name_idx (name), "
                       "UNIQUE KEY u (name));")
        self.assertEqual(sorted(s.tables["t"]), ["id", "name"])
        self.assertEqual(s.pks["t"], ["id"])

    def test_a_primary_key_with_an_index_prefix_keeps_the_bare_name(self):
        s = self.parse("CREATE TABLE t (`code` varchar(64), "
                       "PRIMARY KEY (`code`(20)));")
        self.assertEqual(s.pks["t"], ["code"])


class TestPreservedColumnsFitTheRow(unittest.TestCase):
    """Every CARLOS table the manifest widens must have room for the
    columns it will gain, measured against the REAL migration schema.

    The import adds `import_archived_<col>` to live tables, and MySQL
    refuses an ALTER that would push a row past 65,535 declared bytes --
    in the middle of the table loop, with the import part-written.

    A BOUND, not the exact sum: the added column keeps its O19 source
    type, which this checkout cannot see, so each is measured at 1 KB
    (a varchar(255) in utf8mb4 -- the widest ordinary shape an O19 text
    column takes). A curation that dropped something wider still trips
    `oversized_rows` at run time, which is the real safety net; what
    this pins is that the manifest today is nowhere near the ceiling, so
    the runtime refusal stays the rare case it is meant to be.
    """

    #: what one added column is measured at: a varchar(255) in utf8mb4,
    #: which is the widest ordinary shape an O19 text column takes. A
    #: TEXT would count 10 bytes, so this is the generous direction.
    PER_COLUMN_BYTES = 255 * 4 + 2

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()
        cls.carlos = cls.gen.load_schema(cls.gen.carlos_migration_files(
            [cls.gen.MIGRATION_DIR / "common", cls.gen.MIGRATION_DIR / "on"]))

    def test_every_widened_table_stays_inside_the_row_limit(self):
        worst = (0, None)
        for table, entry in sorted(o19map_schema.TABLES.items()):
            dropped = entry.get("dropped") or {}
            if not dropped or table not in self.carlos.tables:
                continue
            current = sum(o19etl.column_bytes(t)
                          for t in self.carlos.tables[table].values())
            after = current + self.PER_COLUMN_BYTES * len(dropped)
            self.assertLess(after, o19etl.MAX_ROW_BYTES,
                            "{0}: {1} bytes after {2} preserved column(s)"
                            .format(table, after, len(dropped)))
            worst = max(worst, (after, table))
        # the measurement, not just the bound: if this drifts toward the
        # ceiling the margin is worth re-reading rather than trusting
        self.assertLess(worst[0], o19etl.MAX_ROW_BYTES // 2,
                        "widest widened table is {1} at {0} bytes"
                        .format(*worst))


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestRenameRefusals(unittest.TestCase):
    """`build_tables` refuses to emit a manifest while a rename might be
    hiding, and each refusal has an escape hatch that actually works.

    Driven over synthetic two-table schemas rather than the real ones: the
    shipped overlay has every case ruled, so the refusals themselves are
    unreachable from it, and a check nobody can trip is not a check.
    """

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def overlay(self, **kw):
        """A minimal overlay: every bucket empty unless a test fills it."""
        ns = types.SimpleNamespace(
            CLASS_MERGE={}, CLASS_REFERENCE=set(), ARCHIVE_PATIENT=set(),
            ARCHIVE_OTHER=set(), DROP=set(), B3_COLUMNS=set(),
            CHARSET_SCAN={}, CHUNK_TABLES=set())
        for k, v in kw.items():
            setattr(ns, k, v)
        return ns

    def schemas(self, o19_tables, carlos_tables):
        o19 = self.gen.Schema("union")
        carlos = self.gen.Schema("skip")
        for schema, tables in ((o19, o19_tables), (carlos, carlos_tables)):
            for name, cols in tables.items():
                schema.tables[name] = {c: "varchar(20)" for c in cols}
                schema.pks[name] = [list(cols)[0]]
        return o19, carlos

    # -- columns ------------------------------------------------------
    #: `code` is dropped on the O19 side while CARLOS's `codeValue` is
    #: never written -- the exact shape of a rename the name matching
    #: cannot see.
    RENAME_SHAPED = ({"t": ["id", "code"]}, {"t": ["id", "codeValue"]})

    def build(self, ov, tables=None):
        o19, carlos = self.schemas(*(tables or self.RENAME_SHAPED))
        return self.gen.build_tables(o19, carlos, ov)

    def test_unruled_column_co_occurrence_refuses(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay())
        self.assertIn("unruled possible rename", str(caught.exception))
        self.assertIn("t.code", str(caught.exception))

    def test_a_column_ruling_lets_generation_through(self):
        tables = self.build(self.overlay(
            NOT_RENAMES={("t", "code"): "coincidence, not a rename"}))
        self.assertIn("code", tables["t"]["dropped"])

    def test_a_blank_column_reason_is_not_a_ruling(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(NOT_RENAMES={("t", "code"): "   "}))
        self.assertIn("has no reason", str(caught.exception))

    def test_a_table_pair_filed_as_a_column_ruling_says_where_it_goes(self):
        # the bug this test exists for: (o19_table, carlos_table) in
        # NOT_RENAMES is read as (table, dropped_column) and dies as a
        # stale entry, so the documented escape hatch was unusable
        ov = self.overlay(NOT_RENAMES={("t", "code"): "ruled",
                                       ("old_t", "new_t"): "not a rename"})
        pair = ({"t": ["id", "code"], "old_t": ["a", "b", "c"]},
                {"t": ["id", "codeValue"], "new_t": ["a", "b", "c"]})
        with self.assertRaises(SystemExit) as caught:
            self.build(ov, tables=pair)
        self.assertIn("belongs in NOT_RENAMED_TABLES", str(caught.exception))

    # -- tables -------------------------------------------------------
    #: same three columns on both sides, one name each -- Jaccard 1.0
    TWIN_SHAPED = ({"old_t": ["a", "b", "c"]}, {"new_t": ["a", "b", "c"]})

    def test_unruled_table_twin_refuses(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(), tables=self.TWIN_SHAPED)
        self.assertIn("possible table rename", str(caught.exception))
        self.assertIn("100% of their column names agree",
                      str(caught.exception))

    def test_a_table_ruling_lets_generation_through(self):
        tables = self.build(
            self.overlay(ARCHIVE_OTHER={"old_t"}, NOT_RENAMED_TABLES={
                ("old_t", "new_t"): "unrelated tables that share a shape"}),
            tables=self.TWIN_SHAPED)
        self.assertEqual(tables["old_t"]["class"], "archive")

    def test_a_blank_table_reason_is_not_a_ruling(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(NOT_RENAMED_TABLES={
                ("old_t", "new_t"): ""}), tables=self.TWIN_SHAPED)
        self.assertIn("has no reason", str(caught.exception))

    # -- a ruling that inverted rather than went stale ----------------
    #
    # ARCHIVE_PATIENT / ARCHIVE_OTHER / DROP are read ONLY in the
    # o19_only loop. A table named there that CARLOS later gains stops
    # being O19-only, falls through to `class = "copy"`, and "removed
    # module, do not migrate" silently becomes "copy every clinic row
    # into the live table" -- with no warning and a --check that still
    # passes.

    SHARED = ({"t": ["id", "code"]}, {"t": ["id", "code"]})

    def test_a_drop_ruling_that_now_names_a_shared_table_refuses(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(DROP={"t"}), tables=self.SHARED)
        self.assertIn("DROP names t", str(caught.exception))
        self.assertIn("exists on both sides", str(caught.exception))

    def test_an_archive_ruling_that_now_names_a_shared_table_refuses(self):
        for bucket in ("ARCHIVE_PATIENT", "ARCHIVE_OTHER"):
            with self.subTest(bucket=bucket):
                with self.assertRaises(SystemExit) as caught:
                    self.build(self.overlay(**{bucket: {"t"}}),
                               tables=self.SHARED)
                self.assertIn("{0} names t".format(bucket),
                              str(caught.exception))

    def test_an_o19_only_table_in_those_buckets_still_passes(self):
        # the ordinary case the buckets exist for: the refusal must fire
        # on the inversion, not on the rule working as intended
        tables = self.build(self.overlay(DROP={"gone"}),
                            tables=({"t": ["id"], "gone": ["id"]},
                                    {"t": ["id"]}))
        self.assertEqual(tables["gone"]["class"], "drop")

    def test_a_table_ruling_that_no_longer_applies_is_stale(self):
        # dead weight that would silently cover a FUTURE pair of the same
        # names, so it is an error rather than a warning
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(NOT_RENAMED_TABLES={
                ("gone", "also_gone"): "ruled long ago"}),
                tables=self.TWIN_SHAPED)
        self.assertIn("stale entry", str(caught.exception))

    def test_a_contained_small_table_is_not_flagged(self):
        # the threshold is Jaccard, not intersection-over-smaller. Here
        # every O19 column appears on the CARLOS side, so the containment
        # ratio is 1.0 and would flag; Jaccard is 4/8 and does not. That
        # difference is not academic -- scoring by the smaller side made
        # five audit-shaped tables "match" larger unrelated ones.
        tables = self.build(self.overlay(ARCHIVE_OTHER={"old_t"}), tables=(
            {"old_t": ["id", "a", "b", "c"]},
            {"new_t": ["id", "a", "b", "c", "d", "e", "f", "g"]}))
        self.assertEqual(tables["old_t"]["class"], "archive")


if __name__ == "__main__":
    unittest.main()
