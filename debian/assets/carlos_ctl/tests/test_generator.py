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
            "CREATE TABLE `p1` (`id` varchar(191), `we``ird` varchar(191));")
        self.assertEqual(self.mod.scaffold("p1", []), "")


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
