# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Contracts of the repo-side manifest generator that the shipped modules
depend on: Flyway version ordering, ADD COLUMN IF NOT EXISTS parsing, and
the credential-key filter that keeps stock secrets out of the manifest.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import importlib.util
import re
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


#: the parsed CARLOS schema, kept because the parse is the expensive
#: thing in this module (~30 s over the whole Flyway set) and more than
#: one class needs the real column definitions
_CARLOS_SCHEMA = []


def carlos_schema(gen):
    """CARLOS's own columns as the generator reads them: common + on,
    in Flyway order, `tables[t][col]` holding the whole definition."""
    if not _CARLOS_SCHEMA:
        _CARLOS_SCHEMA.append(gen.load_schema(gen.carlos_migration_files(
            [gen.MIGRATION_DIR / "common", gen.MIGRATION_DIR / "on"])))
    return _CARLOS_SCHEMA[0]


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

    def test_divergent_carlos_defaults_are_derived_from_the_shipped_file(
            self):
        # the manifest's CARLOS_DEFAULTS must come from the file the deb
        # installs as /etc/carlos-emr/carlos.properties, not from a
        # hand-kept list: an edit there changes what wins after cutover
        from carlos_ctl import o19map_props
        carlos = self.gen.parse_properties(self.gen.CARLOS_PROPERTIES)
        ov = self.gen.load_module(GEN.parent / "overrides_props.py")
        derived = self.gen.divergent_carlos_defaults(
            o19map_props.O19_DEFAULTS, carlos, ov)
        self.assertEqual(derived, o19map_props.CARLOS_DEFAULTS)
        self.assertIn("CONSULTATION_AUTO_INCLUDE_ALLERGIES", derived)
        # a key whose stock values agree is not divergent
        self.assertNotIn("billregion", derived)

    def test_only_carry_keys_become_divergent_carlos_defaults(self):
        ov = types.SimpleNamespace(
            KEYS={"kept": {"d": "carry"},
                  "owned": {"d": "deploy-owned"},
                  "same": {"d": "carry"}},
            PREFIX_RULES=[("pfx.", {"d": "carry"})])
        derived = self.gen.divergent_carlos_defaults(
            {"kept": "a", "owned": "a", "same": "a", "pfx.k": "a",
             "absent": "a"},
            {"kept": "b", "owned": "b", "same": "a", "pfx.k": "b"},
            ov)
        self.assertEqual(derived, {"kept": "b", "pfx.k": "b"})

    def test_bundle_renames_are_verified_against_the_carlos_bundle(self):
        # a target that does not exist would carry a token resolving to ""
        # into every signed note, so it must not be emitted at all
        from carlos_ctl import o19map_props
        carlos = self.gen.parse_properties(self.gen.CARLOS_RESOURCE_BUNDLE)
        for old, new in o19map_props.BUNDLE_KEY_RENAMES.items():
            self.assertIn(new, carlos, old)
            self.assertNotIn(old, carlos, old)

    def test_bundle_renames_drop_keys_carlos_no_longer_defines(self):
        ov = types.SimpleNamespace(
            BUNDLE_PREFIX_RENAMES=[("oldNs.", "newNs.")])
        renames = self.gen.bundle_key_renames(
            {"oldNs.kept": "x", "oldNs.gone": "y", "other.key": "z"},
            {"newNs.kept": "x", "other.key": "z"}, ov)
        self.assertEqual(renames, {"oldNs.kept": "newNs.kept"})

    def test_a_stale_or_pointless_bundle_rename_is_refused(self):
        stale = types.SimpleNamespace(
            BUNDLE_PREFIX_RENAMES=[("noSuchNs.", "newNs.")])
        with self.assertRaises(SystemExit):
            self.gen.bundle_key_renames({"oldNs.k": "x"}, {}, stale)
        pointless = types.SimpleNamespace(
            BUNDLE_PREFIX_RENAMES=[("oldNs.", "newNs.")])
        with self.assertRaises(SystemExit):
            self.gen.bundle_key_renames(
                {"oldNs.k": "x"}, {"oldNs.k": "x", "newNs.k": "x"},
                pointless)

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

    # --- the three defects the MariaDB oracle found -------------------
    # scripts/migration/o19/verify_ddl_parse.py replays this corpus
    # through a real server and compares; each case below is one
    # disagreement it reported, kept here so the suite catches a
    # regression without needing a database.

    def test_a_primary_key_names_the_column_whatever_its_case(self):
        # update-hsfo.sql declares `ID` and writes `PRIMARY KEY (id)`.
        # MySQL resolves the reference case-insensitively, so the key is
        # `ID`; recording `id` left pks[t][0] naming no column at all,
        # and the surrogate-key check looks that name up in tables[t].
        s = self.parse("CREATE TABLE t (ID int(10) NOT NULL "
                       "auto_increment, x varchar(10), PRIMARY KEY (id));")
        self.assertEqual(s.pks["t"], ["ID"])
        self.assertIn(s.pks["t"][0], s.tables["t"])

    def test_a_primary_key_naming_no_column_is_dropped(self):
        # MySQL refuses such a CREATE outright, so carrying the name
        # forward could only mislead a later lookup
        s = self.parse("CREATE TABLE t (a int, PRIMARY KEY (nosuch));")
        self.assertNotIn("t", s.pks)

    def test_an_add_primary_key_may_carry_an_index_name(self):
        # update-2012-10-30.sql writes `ADD PRIMARY KEY
        # billcenter_code(billcenter_code)`. MySQL accepts the name and
        # discards it; the parser used to reject the whole clause, so
        # billcenter came out with no primary key at all.
        s = self.parse(
            "CREATE TABLE billcenter (billcenter_code varchar(10) NOT NULL,"
            " x int);"
            "ALTER TABLE billcenter ADD PRIMARY KEY"
            " billcenter_code(billcenter_code);")
        self.assertEqual(s.pks["billcenter"], ["billcenter_code"])

    def test_an_unnamed_add_primary_key_still_parses(self):
        # the optional name must not eat the column list
        s = self.parse("CREATE TABLE t (a int NOT NULL, b int NOT NULL);"
                       "ALTER TABLE t ADD PRIMARY KEY (a, b);")
        self.assertEqual(s.pks["t"], ["a", "b"])

    def test_add_column_after_lands_where_mysql_puts_it(self):
        # 27 ALTERs in the O19 corpus position a column this way;
        # appending instead describes a table the clinic does not have
        s = self.parse(
            "CREATE TABLE vacancy (id int, templateId int, status int);"
            "ALTER TABLE vacancy ADD vacancyName VARCHAR(255) NOT NULL "
            "AFTER id;")
        self.assertEqual(list(s.tables["vacancy"]),
                         ["id", "vacancyName", "templateId", "status"])
        # ...and the position clause is not left inside the type text,
        # which default_nondefault_expr() and the surrogate-key check read
        self.assertNotIn("AFTER", s.tables["vacancy"]["vacancyName"])

    def test_add_column_first_lands_at_the_front(self):
        s = self.parse("CREATE TABLE t (a int, b int);"
                       "ALTER TABLE t ADD COLUMN z int FIRST;")
        self.assertEqual(list(s.tables["t"]), ["z", "a", "b"])

    def test_add_column_after_an_unknown_column_still_keeps_it(self):
        # a real server would refuse the statement, so there is no right
        # position to choose -- but losing the column would be worse
        s = self.parse("CREATE TABLE t (a int);"
                       "ALTER TABLE t ADD COLUMN z int AFTER nosuch;")
        self.assertEqual(list(s.tables["t"]), ["a", "z"])

    def test_change_column_matches_the_name_case_insensitively(self):
        # update-2012-11-11.sql: `change name name varchar(100)` against a
        # column declared NAME renames it to lowercase. Matching
        # case-sensitively left the old spelling standing.
        s = self.parse("CREATE TABLE t (TEMPLATE_ID int, NAME varchar(50));"
                       "ALTER TABLE t CHANGE name name varchar(100) NOT "
                       "NULL;")
        self.assertEqual(list(s.tables["t"]), ["TEMPLATE_ID", "name"])

    def test_a_renamed_column_takes_the_primary_key_with_it(self):
        # the other half of the case-insensitive CHANGE fix: leaving the old
        # spelling in pks names a column that no longer exists, and the
        # surrogate-key detection looks that name up in tables[t]
        s = self.parse("CREATE TABLE t (ID int, x int, PRIMARY KEY (id));"
                       "ALTER TABLE t CHANGE id ident int NOT NULL;")
        self.assertEqual(s.pks["t"], ["ident"])
        self.assertIn(s.pks["t"][0], s.tables["t"])

    def test_dropping_the_key_column_drops_the_key(self):
        s = self.parse("CREATE TABLE t (ID int, x int, PRIMARY KEY (id));"
                       "ALTER TABLE t DROP COLUMN ID;")
        self.assertEqual(list(s.tables["t"]), ["x"])
        self.assertNotIn("t", s.pks)

    def test_drop_primary_key_actually_drops_it(self):
        # invisible until the oracle compared keys on ALTERs: seven tables
        # in the O19 update history drop their key this way, and the
        # generic DROP COLUMN branch never sees the clause because its
        # keyword guard excludes "primary"
        s = self.parse("CREATE TABLE t (sdate date, p varchar(6), "
                       "PRIMARY KEY (sdate));"
                       "ALTER TABLE t DROP PRIMARY KEY;")
        self.assertNotIn("t", s.pks)

    def test_an_added_column_can_declare_the_key_inline(self):
        s = self.parse("CREATE TABLE t (sdate date, PRIMARY KEY (sdate));"
                       "ALTER TABLE t DROP PRIMARY KEY;"
                       "ALTER TABLE t ADD COLUMN id int(6) NOT NULL "
                       "auto_increment primary key;")
        self.assertEqual(s.pks["t"], ["id"])
        self.assertIn("id", s.tables["t"])

    def test_add_primary_key_as_its_own_clause(self):
        # reportTemplates drops the key and re-adds it in one statement
        s = self.parse("CREATE TABLE t (templateid int, x int);"
                       "ALTER TABLE t CHANGE templateid templateid int(11) "
                       "NOT NULL auto_increment, ADD PRIMARY KEY(templateid);")
        self.assertEqual(s.pks["t"], ["templateid"])

    def test_drop_column_matches_the_name_case_insensitively(self):
        s = self.parse("CREATE TABLE t (a int, NAME varchar(50));"
                       "ALTER TABLE t DROP COLUMN name;")
        self.assertEqual(list(s.tables["t"]), ["a"])


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestTheDdlOracleStaysUsable(unittest.TestCase):

    """verify_ddl_parse.py is a maintainer tool with no CI job, so the
    cheapest thing that keeps it from rotting is checking that it still
    imports and that its pure helpers still do what the oracle needs.

    The comparison itself needs a MariaDB and is not run here; see
    scripts/migration/o19/README.md."""

    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location(
            "verify_ddl_parse", GEN.parent / "verify_ddl_parse.py")
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_a_create_is_retargeted_at_the_probe_table(self):
        # the probe name is what lets one statement be replayed without
        # colliding with the real table or an earlier revision of itself
        for sql in ("CREATE TABLE `t` (a int)",
                    "create table if not exists t (a int)",
                    "CREATE TABLE IF NOT EXISTS `t` (a int)"):
            got = self.mod.probe_create(sql, "p7")
            self.assertTrue(got.startswith("CREATE TABLE `p7` ("), got)
            self.assertNotIn("`t`", got)

    def test_an_alter_is_retargeted_at_the_probe_table(self):
        got = self.mod.probe_alter("ALTER TABLE t ADD c int", "p7")
        self.assertEqual(got, "ALTER TABLE `p7` ADD c int")

    def test_both_oracle_failures_are_reported_not_just_the_first(self):
        """An unbuildable probe used to `return 1` before the
        DISAGREEMENTS block, so a run carrying one of each printed the
        lesser finding, dropped the scratch schema holding the greater
        one, and left the maintainer reading "fix scaffold()" with no
        hint that the parse also disagreed with MariaDB."""
        mismatch = ("create", "oscarinit.sql", "demographic",
                    ["a"], ["b"], None, None)
        out, err, rc, keep = self.mod.summarise(9, 1, 2, [mismatch], "scr")
        text = "\n".join(out)
        self.assertIn("DISAGREEMENTS (1)", text)
        self.assertIn("NOT CHECKED", "\n".join(err))
        self.assertEqual(rc, 1)
        # and the schema that holds the disagreement survives the run
        self.assertTrue(keep)
        self.assertIn("kept for inspection", text)

    def test_the_oracle_says_ok_only_when_everything_was_checked(self):
        out, err, rc, keep = self.mod.summarise(9, 1, 0, [], "scr")
        self.assertIn("OK - the generator's parse agrees", "\n".join(out))
        self.assertEqual((rc, keep, err), (0, False, []))
        # an unchecked statement is never a pass, and never says OK
        out, err, rc, keep = self.mod.summarise(9, 1, 2, [], "scr")
        self.assertNotIn("OK -", "\n".join(out))
        self.assertEqual((rc, keep), (1, False))

    def test_a_setup_failure_exits_with_the_documented_status(self):
        # the module docstring reserves 1 for "the parse disagreed" and 2
        # for a usage or connection error; `raise SystemExit("text")`
        # exits 1, which would report a dead client as a generator bug
        with self.assertRaises(SystemExit) as caught:
            self.mod.fail("no server")
        self.assertEqual(caught.exception.code, 2)

    def test_a_password_in_argv_is_named_back_to_the_operator(self):
        # the message quotes what it refuses, so a bare --password must
        # come back as --password and not as the first two characters
        self.assertEqual(self.mod.reject_password_args(
            ["--password"]), ["--password"])
        self.assertEqual(self.mod.reject_password_args(
            ["--password=hunter2"]), ["--password"])
        self.assertEqual(self.mod.reject_password_args(["-phunter2"]), ["-p"])
        # the BARE form is the one that makes the client PROMPT, which in
        # a scripted oracle run means an indefinite wait on stdin
        self.assertEqual(self.mod.reject_password_args(["-p"]), ["-p"])

    def test_an_innocent_client_argument_is_left_alone(self):
        # --protocol starts with "-p" too; refusing it would block a
        # legitimate invocation
        self.assertEqual(self.mod.reject_password_args(
            ["-uroot", "--protocol=tcp", "--socket=/run/m.sock"]), [])

    def test_the_scaffold_preserves_column_order_and_quotes_names(self):
        got = self.mod.scaffold("p1", ["id", "we`ird"])
        self.assertEqual(
            got,
            "CREATE TABLE `p1` (`id` varchar(1), `we``ird` varchar(1)) "
            "ENGINE=MyISAM;")
        self.assertEqual(self.mod.scaffold("p1", []), "")

    def test_the_scaffold_can_build_the_widest_form_in_the_corpus(self):
        """A probe the server refuses is a statement the oracle never
        checks, and the widest tables are the encounter forms -- exactly
        where a mis-parsed column is a clinical field silently dropped.

        Both limits are real and both were hit: at varchar(191) the
        65535-byte row cap refused every table past 85 columns, and on
        InnoDB the 1017-column cap refused the forms past that. The
        oracle now counts an unbuildable probe as a failure, so this
        test is the database-free half of the same guard."""
        wide = ["c{0}".format(i) for i in range(1515)]
        got = self.mod.scaffold("p1", wide)
        widths = set(int(w) for w in re.findall(r"varchar\((\d+)\)", got))
        self.assertEqual(widths, {1}, "one declared width, and a narrow one")
        # utf8mb4 charges 4 bytes per character plus a 2-byte length
        row_bytes = len(wide) * (max(widths) * 4 + 2)
        self.assertLess(row_bytes, 65535,
                        "the probe would be refused: row size too large")
        self.assertIn("ENGINE=MyISAM", got,
                      "InnoDB stops at 1017 columns; the corpus goes past it")


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestTheEntityNameCheckerReadsTheRightMember(unittest.TestCase):

    """check-entity-names.py compares each entity's Java property against
    the DB column the manifest carries, so reading the WRONG property
    turns the audit into noise -- and silently: a mismatch it invents is
    indistinguishable from one it found.

    JPA reads @Column from the field or from the getter, per entity. On a
    getter-annotated entity the field pattern used to win anyway, because
    `return providerNo;` in the getter body has the shape of a
    declaration and the search is not anchored -- so the annotation was
    bound to a field belonging to a different property further down the
    400-character window."""

    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location(
            "check_entity_names", GEN.parent / "check-entity-names.py")
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def entity(self, body):
        import tempfile
        tmp = tempfile.TemporaryDirectory(prefix="o19entity-")
        self.addCleanup(tmp.cleanup)
        path = Path(tmp.name) / "E.java"
        path.write_text('@Entity\n@Table(name = "e")\n'
                        'public class E {\n' + body + "\n}\n",
                        encoding="utf-8")
        return self.mod.parse_entity(str(path))

    def test_a_getter_annotation_binds_to_that_getter(self):
        # the shape that misread: the getter's own body, then another
        # property's field, inside one 400-character window
        got = self.entity(
            '    @Column(name = "provider_no")\n'
            '    public String getProviderNo() {\n'
            '        return this.providerNo;\n'
            '    }\n'
            '    public void setProviderNo(String v) {\n'
            '        this.providerNo = v;\n'
            '    }\n'
            '    private String otherThing;\n')
        self.assertEqual(got[2], {"providerNo": "provider_no"})

    def test_a_getter_returning_its_field_plainly_binds_to_the_getter(self):
        # `return name;` IS `<type> <name>;` to a pattern that does not
        # know Java statements from declarations
        got = self.entity(
            '    @Column(length = 255)\n'
            '    public String getName() {\n'
            '        return name;\n'
            '    }\n'
            '    private String status;\n')
        self.assertEqual(got[2], {"name": "name"})

    def test_a_statement_is_not_read_as_a_declaration(self):
        """FIELD_RE itself, not parse_entity.

        Driving this through an entity proves nothing: the nearest-member
        rule picks the getter first, so the assertion passes with or
        without the boundary guard. The regex has to be asked directly --
        `re.search` is unanchored, so a bare keyword list is satisfied by
        starting one character in and matching `eturn providerNo;`."""
        self.assertIsNone(self.mod.FIELD_RE.search("        return "
                                                   "providerNo;\n"))
        self.assertIsNone(self.mod.FIELD_RE.search("        throw ex;\n"))
        # and a real declaration is still found
        self.assertEqual(
            self.mod.FIELD_RE.search("    private String lastName;")
            .group(1), "lastName")
        self.assertEqual(
            self.mod.FIELD_RE.search("    Integer formId;").group(1),
            "formId")

    def test_a_getter_shaped_comment_does_not_win(self):
        # a Javadoc example between the annotation and the real field
        # would otherwise be the nearest match
        got = self.entity(
            '    @Column(name = "last_name")\n'
            '    /** e.g. public String getSomethingElse() { } */\n'
            '    private String lastName;\n')
        self.assertEqual(got[2], {"lastName": "last_name"})

    def test_a_url_in_an_annotation_is_not_read_as_a_comment(self):
        # `//` inside a string literal: a regex masker blanks to end of
        # line from there and the member vanishes from the audit without
        # a word -- the same mistake the Java `#` stripper had
        got = self.entity(
            '    @Column(name = "u")\n'
            '    @Doc(url = "http://example/x") private String u;\n')
        self.assertEqual(got[2], {"u": "u"})

    def test_a_real_comment_is_still_blanked(self):
        # the masker must not become a no-op in the process
        got = self.entity(
            '    @Column(name = "last_name")\n'
            '    // public String getSomethingElse() { }\n'
            '    private String lastName;\n')
        self.assertEqual(got[2], {"lastName": "last_name"})

    def test_a_field_annotation_still_binds_to_the_field(self):
        # the ordinary case must not regress: the field sits right after
        # the annotation, so it wins on position
        got = self.entity(
            '    @Column(name = "last_name")\n'
            '    private String lastName;\n'
            '    public String getLastName() { return lastName; }\n')
        self.assertEqual(got[2], {"lastName": "last_name"})

    def test_a_package_private_field_still_binds(self):
        # composite-id classes declare `Integer formId;` with no modifier
        got = self.entity(
            '    @Column(name = "form_id")\n'
            '    Integer formId;\n')
        self.assertEqual(got[2], {"formId": "form_id"})


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestTheSqlSemanticsOracleStaysUsable(unittest.TestCase):

    """verify_sql_semantics.py is a maintainer tool with no CI job, so
    the cheapest guard against rot is that it still imports and still
    describes the table it drives.

    The scenarios themselves need a MariaDB; see
    scripts/migration/o19/README.md."""

    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location(
            "verify_sql_semantics",
            GEN.parent / "verify_sql_semantics.py")
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_it_drives_a_table_the_manifest_still_calls_merge(self):
        # if consultationServices ever stops being merge-class, or loses
        # its surrogate key, the script would be checking nothing
        entry = o19map_schema.TABLES[self.mod.TABLE]
        self.assertEqual(entry["class"], "merge")
        self.assertEqual(entry["merge_keys"], self.mod.ENTRY["merge_keys"])
        self.assertEqual(entry["surrogate_pk"],
                         self.mod.ENTRY["surrogate_pk"])
        self.assertEqual(entry["cols"], self.mod.ENTRY["cols"])

    def test_the_fk_oracle_drives_the_shipped_consent_entries(self):
        # the fixture merges consentType and copies Consent through its
        # id map; if either shipped entry drifts, the oracle proves a
        # shape the import no longer runs
        parent = o19map_schema.TABLES["consentType"]
        self.assertEqual(parent["merge_keys"],
                         self.mod.FK_PARENT_ENTRY["merge_keys"])
        self.assertEqual(parent["surrogate_pk"],
                         self.mod.FK_PARENT_ENTRY["surrogate_pk"])
        self.assertEqual(set(self.mod.FK_PARENT_ENTRY["cols"]),
                         set(parent["cols"]))
        self.assertEqual(o19map_schema.TABLES["Consent"]["fk_remap"],
                         self.mod.FK_CHILD_ENTRY["fk_remap"])

    def test_the_charset_samples_all_survive_a_cp1252_round_trip(self):
        # MySQL's latin1 is CP1252: a sample whose UTF-8 bytes are not
        # decodable as CP1252 could not be stored by a real O19 either, so
        # it would be checking a case that cannot happen
        for good in self.mod.CHARSET_SAMPLES:
            good.encode("utf-8").decode("cp1252")

    def test_a_prefix_that_is_not_an_identifier_is_refused(self):
        # the prefix reaches SQL unquoted and the script DROPs what it
        # builds from it
        self.assertIsNotNone(self.mod.prefix_problem("a`b"))
        self.assertIsNotNone(self.mod.prefix_problem(""))
        self.assertIsNone(self.mod.prefix_problem("carlos_merge_verify"))

    def test_a_prefix_that_would_overrun_the_schema_name_is_refused(self):
        # MariaDB refuses a schema name over 64 characters, and the
        # longest suffix is `_arch`; without this the run dies as ERROR
        # 1102 from the first DROP, which reads like a broken server
        self.assertIsNone(self.mod.prefix_problem("p" * self.mod.MAX_PREFIX))
        self.assertIsNotNone(
            self.mod.prefix_problem("p" * (self.mod.MAX_PREFIX + 1)))
        self.assertEqual(self.mod.MAX_PREFIX + len("_arch"), 64)

    def test_every_scenario_states_why_it_exists(self):
        # a scenario with no stated reason is a scenario nobody can judge
        self.assertGreaterEqual(len(self.mod.SCENARIOS), 4)
        for sc in self.mod.SCENARIOS:
            self.assertTrue(sc.why.strip(), sc.name)
            self.assertTrue(sc.stage.strip(), sc.name)


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
        cls.carlos = carlos_schema(cls.gen)

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
class TestASurrogateKeyIsAnIntegerKey(unittest.TestCase):

    """A merge table's primary key is treated as a surrogate the ETL must
    reassign only when it really is an integer.

    `Schema.tables[t][col]` holds the whole column DEFINITION -- type,
    NOT NULL, DEFAULT and COMMENT, whitespace-collapsed -- so the
    substring test this replaces read `varchar(20) COMMENT 'appointment
    slug'`, `DEFAULT 'internal'` and `enum('Uninterested', ...)` as
    integers. The CARLOS schema already carries two such columns
    (appointment_status.short_letters, form_hsfo2_visit.PtView); neither
    is a single-column primary key today, which is the only reason this
    was latent rather than shipped.

    Read wrong, the refusal on the next branch is skipped and a
    `surrogate_pk` is emitted for a string column. `idmap_statements`
    then declares `old_id BIGINT NOT NULL PRIMARY KEY` and inserts that
    string into it: under the ETL's `sql_mode=''` non-numeric codes all
    coerce to 0, so the insert dies on a duplicate key mid-merge -- or,
    for numeric-prefixed codes, distinct keys collapse onto one id and
    every child row remapped through the map is re-pointed at the wrong
    parent.
    """

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def test_it_reads_the_type_and_not_the_comment(self):
        for definition in ("int(11) NOT NULL AUTO_INCREMENT", "bigint(20)",
                           "integer", "tinyint(1)", "smallint",
                           "mediumint(9)", "INT UNSIGNED"):
            self.assertTrue(self.gen.integer_column(definition), definition)
        for definition in ("varchar(20) NOT NULL COMMENT 'appointment "
                           "slug'", "varchar(8) DEFAULT 'internal'",
                           "enum('Uninterested','x')", "char(2)", "point",
                           "text COMMENT 'print this'", ""):
            self.assertFalse(self.gen.integer_column(definition), definition)

    def test_a_string_primary_key_is_refused_not_made_a_surrogate(self):
        o19 = self.gen.Schema("union")
        carlos = self.gen.Schema("skip")
        for schema in (o19, carlos):
            schema.tables["reasonCode"] = {
                "slug": "varchar(20) NOT NULL COMMENT 'appointment slug'",
                "code": "varchar(10)"}
            schema.pks["reasonCode"] = ["slug"]
        ov = types.SimpleNamespace(
            CLASS_MERGE={"reasonCode": ["code"]}, CLASS_REFERENCE=set(),
            ARCHIVE_PATIENT=set(), ARCHIVE_OTHER=set(), DROP=set(),
            B3_COLUMNS=set(), CHARSET_SCAN={}, CHUNK_TABLES=set())
        with self.assertRaises(SystemExit) as caught:
            self.gen.build_tables(o19, carlos, ov)
        self.assertIn("must equal its primary key", str(caught.exception))

    def test_the_shipped_surrogates_are_all_int_typed_columns(self):
        """The manifest that ships, read back through the same rule.

        Every `surrogate_pk` must be a column whose CARLOS TYPE the ETL
        can put in a `BIGINT` id map -- which means reading the type out
        of the CARLOS schema in this repository, not merely checking
        that the name appears in `cols`. Nothing here needs an OSCAR 19
        checkout, so it guards a PR as well as a regeneration."""
        carlos = carlos_schema(self.gen)
        surrogates = [(t, e["surrogate_pk"])
                      for t, e in o19map_schema.TABLES.items()
                      if e.get("surrogate_pk")]
        self.assertTrue(surrogates)
        for table, col in surrogates:
            definition = carlos.tables.get(table, {}).get(col)
            self.assertIsNotNone(
                definition,
                "{0}.{1} is not a CARLOS column".format(table, col))
            self.assertTrue(
                self.gen.integer_column(definition),
                "{0}.{1} is {2!r} — idmap_statements would declare "
                "old_id BIGINT and insert that into it".format(
                    table, col, definition))
            self.assertIn(col, o19map_schema.TABLES[table]["cols"],
                          "{0}.{1}".format(table, col))
            self.assertNotIn(col,
                             o19map_schema.TABLES[table]["merge_keys"],
                             "{0}.{1} is both the surrogate and a merge "
                             "key".format(table, col))


class TestForeignKeyRefusals(unittest.TestCase):
    """`build_tables` refuses to emit a manifest while a copied id names a
    table whose ids the import does not keep, and each ruling that lets
    it through means what it says.

    The defect this guards: `consentType` was ruled `reference` while
    `Consent.consent_type_id` was copied raw, and the two seeds disagree
    on id 1 -- so every clinic's integrator consent arrived filed as the
    demonstration consent, and P7 passed because the value was copied
    faithfully. Driven over synthetic two-table schemas: the shipped
    overlay has every case ruled, so the refusal is unreachable from it.
    """

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()

    def overlay(self, **kw):
        ns = types.SimpleNamespace(
            CLASS_MERGE={}, CLASS_REFERENCE=set(), ARCHIVE_PATIENT=set(),
            ARCHIVE_OTHER=set(), DROP=set(), B3_COLUMNS=set(),
            CHARSET_SCAN={}, CHUNK_TABLES=set())
        for k, v in kw.items():
            setattr(ns, k, v)
        return ns

    def schemas(self, tables, int_cols=("id",)):
        """Both sides identical; `int_cols` are typed int so the generator
        can see a surrogate PK (its rule needs "int" in the PK's type)."""
        o19 = self.gen.Schema("union")
        carlos = self.gen.Schema("skip")
        for schema in (o19, carlos):
            for name, cols in tables.items():
                schema.tables[name] = {
                    c: ("int(11)" if c in int_cols else "varchar(20)")
                    for c in cols}
                schema.pks[name] = [list(cols)[0]]
        return o19, carlos

    #: the shape of the defect: a child whose column NAMES the parent
    FK_SHAPED = {"consentType": ["id", "type"],
                 "Consent": ["id", "consent_type_id"]}

    def build(self, ov, tables=None, **kw):
        o19, carlos = self.schemas(tables or self.FK_SHAPED, **kw)
        return self.gen.build_tables(o19, carlos, ov)

    def test_a_reference_parent_named_by_a_child_column_refuses(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(CLASS_REFERENCE={"consentType"}))
        text = str(caught.exception)
        self.assertIn("unruled foreign key", text)
        self.assertIn("Consent.consent_type_id -> consentType", text)
        self.assertIn("reference", text)
        self.assertIn("NOT_FK", text)

    def test_a_surrogate_merge_parent_named_by_a_child_column_refuses(
            self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(CLASS_MERGE={"consentType": ["type"]}))
        text = str(caught.exception)
        self.assertIn("Consent.consent_type_id -> consentType", text)
        self.assertIn("surrogate", text)

    def test_an_fk_remap_ruling_lets_generation_through(self):
        tables = self.build(self.overlay(
            CLASS_MERGE={"consentType": ["type"]},
            FK_REMAP={"Consent": {"consent_type_id": "consentType"}}))
        self.assertEqual(tables["Consent"]["fk_remap"],
                         {"consent_type_id": "consentType"})
        self.assertEqual(tables["consentType"]["surrogate_pk"], "id")

    def test_a_not_fk_ruling_lets_generation_through(self):
        tables = self.build(self.overlay(
            CLASS_REFERENCE={"consentType"},
            NOT_FK={("Consent", "consent_type_id"):
                    "both seeds agree on every id (compared 2026-09)"}))
        self.assertEqual(tables["Consent"]["class"], "copy")
        self.assertNotIn("fk_remap", tables["Consent"])

    def test_a_blank_not_fk_reason_is_not_a_ruling(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(
                CLASS_REFERENCE={"consentType"},
                NOT_FK={("Consent", "consent_type_id"): "   "}))
        self.assertIn("has no reason", str(caught.exception))

    def test_a_not_fk_ruling_that_no_longer_applies_is_stale(self):
        # the parent is copy-class here, so its ids ARE kept and nothing
        # is flagged: the ruling now excuses nothing
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(
                NOT_FK={("Consent", "consent_type_id"): "a reason"}))
        self.assertIn("stale entry", str(caught.exception))

    def test_a_column_ruled_both_ways_refuses(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(
                CLASS_MERGE={"consentType": ["type"]},
                FK_REMAP={"Consent": {"consent_type_id": "consentType"}},
                NOT_FK={("Consent", "consent_type_id"): "a reason"}))
        self.assertIn("one ruling, not both", str(caught.exception))

    def test_a_parent_whose_ids_are_kept_is_not_flagged(self):
        with self.subTest("copy-class parent keeps its ids"):
            tables = self.build(self.overlay())
            self.assertEqual(tables["Consent"]["class"], "copy")
        with self.subTest("a merge on its own PK keeps its ids"):
            tables = self.build(
                self.overlay(CLASS_MERGE={"consentType": ["id"]}),
                int_cols=())
            self.assertNotIn("surrogate_pk", tables["consentType"])

    def test_the_convention_folds_case_and_underscores_and_nothing_else(
            self):
        parents = {"consenttype": "consentType",
                   "hrmcategory": "HRMCategory",
                   "criteriatype": "criteria_type",
                   "ticklercategory": "tickler_category"}
        by_name = self.gen.fk_parent_by_name
        self.assertEqual(by_name("consent_type_id", parents), "consentType")
        self.assertEqual(by_name("hrmCategoryId", parents), "HRMCategory")
        self.assertEqual(by_name("CRITERIA_TYPE_ID", parents),
                         "criteria_type")
        self.assertIsNone(by_name("id", parents))
        # the documented blind spot: `category_id` does not name
        # tickler_category by convention; it is ruled in FK_REMAP by hand
        self.assertIsNone(by_name("category_id", parents))


@unittest.skipUnless(GEN.is_file(), "generator not in this checkout")
class TestForeignKeyRulings(unittest.TestCase):
    """The shipped manifest, read by the generator's own rule.

    This is the copy that guards a pull request: the generator itself
    only runs with an O19 checkout, which CI does not have, so the rule
    is re-applied here to the manifest as shipped."""

    @classmethod
    def setUpClass(cls):
        cls.gen = load_generator()
        cls.flagged = cls.gen.flagged_fk_columns(o19map_schema.TABLES)

    def test_the_rule_still_sees_the_columns_it_was_written_for(self):
        """A rule that matched nothing would make the assertion below
        pass vacuously."""
        self.assertIn(("Consent", "consent_type_id", "consentType"),
                      self.flagged)
        self.assertIn(("LookupListItem", "lookupListId", "LookupList"),
                      self.flagged)

    def test_every_id_shaped_column_into_a_renumbered_parent_is_ruled(
            self):
        unruled = [
            "{}.{} -> {}".format(t, c, p) for t, c, p in self.flagged
            if c not in o19map_schema.TABLES[t].get("fk_remap", {})]
        self.assertEqual(
            unruled, [],
            "copied id(s) into a table whose ids the import does not "
            "keep, with no FK_REMAP: copied raw, each points at whichever "
            "CARLOS row happens to hold that id. Rule each in "
            "overrides_schema.py and regenerate.")

    def test_consent_rows_follow_their_type_through_the_id_map(self):
        parent = o19map_schema.TABLES["consentType"]
        self.assertEqual(parent["class"], "merge")
        self.assertEqual(parent["merge_keys"], ["type"])
        self.assertEqual(parent["surrogate_pk"], "id")
        self.assertEqual(o19map_schema.TABLES["Consent"]["fk_remap"],
                         {"consent_type_id": "consentType"})
        self.assertEqual(o19map_schema.SEED_ROW_COUNTS["consentType"], 2)

    def test_hrm_documents_follow_their_category_through_the_id_map(self):
        parent = o19map_schema.TABLES["HRMCategory"]
        self.assertEqual(parent["class"], "merge")
        self.assertEqual(parent["merge_keys"], ["subClassNameMnemonic"])
        self.assertEqual(parent["surrogate_pk"], "id")
        for child in ("HRMDocument", "HRMSubClass"):
            self.assertEqual(o19map_schema.TABLES[child]["fk_remap"],
                             {"hrmCategoryId": "HRMCategory"}, child)
        self.assertEqual(o19map_schema.SEED_ROW_COUNTS["HRMCategory"], 20)


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
        tables = self.build(self.overlay(NOT_RENAMES={
            ("t", "code"): ("coincidence, not a rename", ("codeValue",))}))
        self.assertIn("code", tables["t"]["dropped"])

    def test_a_blank_column_reason_is_not_a_ruling(self):
        with self.assertRaises(SystemExit) as caught:
            self.build(self.overlay(
                NOT_RENAMES={("t", "code"): ("   ", ("codeValue",))}))
        self.assertIn("has no reason", str(caught.exception))

    def test_a_ruling_that_names_no_carlos_columns_is_not_a_ruling(self):
        # the shape check earns its place: a bare reason string is what
        # every ruling used to be, and read as a pair it would silently
        # become reason="c", covered="o" -- a ruling covering a column
        # named "o"
        # a non-sequence `covered` (a bare count, a None) must reach the
        # same refusal: iterating it raised TypeError, which is a
        # traceback where the maintainer should be told what to write
        for value in ("coincidence", ("only a reason",),
                      ("reason", "codeValue"), ("reason", 3),
                      ("reason", None), ("reason", {"codeValue"})):
            with self.assertRaises(SystemExit) as caught:
                self.build(self.overlay(
                    NOT_RENAMES={("t", "code"): value}))
            self.assertIn("NOT_RENAMES", str(caught.exception), repr(value))

    def test_a_new_carlos_column_re_opens_an_existing_ruling(self):
        """The hole this closes: CARLOS is the side still under Flyway
        development, so the column that appears opposite an already-ruled
        drop appears on THAT side -- and a ruling keyed by the O19 column
        alone would cover it without anyone having looked."""
        ruled = self.overlay(NOT_RENAMES={
            ("t", "code"): ("coincidence, not a rename", ("codeValue",))})
        grown = ({"t": ["id", "code"]},
                 {"t": ["id", "codeValue", "code_value"]})
        with self.assertRaises(SystemExit) as caught:
            self.build(ruled, tables=grown)
        text = str(caught.exception)
        self.assertIn("covers CARLOS t.{codeValue}", text)
        self.assertIn("code_value", text)

    def test_a_table_pair_filed_as_a_column_ruling_says_where_it_goes(self):
        # the bug this test exists for: (o19_table, carlos_table) in
        # NOT_RENAMES is read as (table, dropped_column) and dies as a
        # stale entry, so the documented escape hatch was unusable
        ov = self.overlay(NOT_RENAMES={
            ("t", "code"): ("ruled", ("codeValue",)),
            ("old_t", "new_t"): ("not a rename", ())})
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
