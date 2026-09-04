# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Seed-reconciliation ordering and safety contracts: the break-glass admin
exists BEFORE any seed deletion, the delete list matches the manifest, and
the seed-group retry path can never wipe the admin.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import unittest

from carlos_ctl import o19etl, o19map_schema


class TestSeedScriptOrdering(unittest.TestCase):

    """Removing CARLOS's seeded clinician without locking anyone out.

    The break-glass administrator is created FIRST and inherits the
    seed's roles; the deletes then run in an order that never orphans a
    security row."""
    def admin_stmts(self):
        return o19etl.seed_admin_statements(
            "carlos", "breakglass", "100001",
            "{bcrypt}$2b$12$fakefakefakefakefakefake", "1234")

    def test_admin_creation_covers_provider_security_roles(self):
        stmts = self.admin_stmts()
        self.assertEqual(len(stmts), 3)
        self.assertIn("INSERT INTO `carlos`.provider", stmts[0])
        self.assertIn("INSERT INTO `carlos`.security", stmts[1])
        self.assertIn("INSERT INTO `carlos`.secUserRole", stmts[2])

    def test_admin_clones_the_seed_clinicians_roles(self):
        stmts = self.admin_stmts()
        self.assertIn("WHERE provider_no = '{0}'".format(
            o19map_schema.SEED_PROVIDER_NO), stmts[2])
        # cloned ACTIVE: hasPrivilege ignores rows whose activeyn is NULL
        self.assertIn("activeyn", stmts[2])
        self.assertIn(", 1, NOW()", stmts[2])

    def test_admin_gets_forced_password_reset(self):
        self.assertIn("forcePasswordReset", self.admin_stmts()[1])

    def test_delete_statements_come_from_the_manifest_in_order(self):
        stmts = o19etl.seed_delete_statements("carlos")
        self.assertEqual(len(stmts),
                         len(o19map_schema.CARLOSDOC_SEED_DELETES))
        for stmt, (table, where) in zip(
                stmts, o19map_schema.CARLOSDOC_SEED_DELETES):
            self.assertEqual(
                stmt, "DELETE FROM `carlos`.`{0}` WHERE {1}".format(
                    table, where))

    def test_manifest_deletes_remove_security_before_provider(self):
        # the security row references provider_no; deleting the provider
        # first would leave a dangling login for a moment mid-script
        tables = [t for t, _ in o19map_schema.CARLOSDOC_SEED_DELETES]
        self.assertLess(tables.index("security"), tables.index("provider"))

    def test_manifest_deletes_target_the_seed_identity_only(self):
        for _table, where in o19map_schema.CARLOSDOC_SEED_DELETES:
            self.assertTrue(
                o19map_schema.SEED_PROVIDER_NO in where
                or o19map_schema.SEED_USER_NAME in where,
                "seed delete WHERE must pin the seed identity: " + where)


class TestSeedGroupRetry(unittest.TestCase):
    """A retry of a seed-group table must exclude the break-glass admin."""

    def test_security_retry_keeps_the_admin_by_user_name(self):
        sql = o19etl.seed_group_retry_delete(
            "security", "carlos", "breakglass", "100001",
            {"user_name": {}, "provider_no": {}})
        self.assertEqual(sql, "DELETE FROM `carlos`.`security` "
                              "WHERE user_name <> 'breakglass'")

    def test_provider_retry_keeps_the_admin_by_provider_no(self):
        sql = o19etl.seed_group_retry_delete(
            "provider", "carlos", "breakglass", "100001",
            {"provider_no": {}})
        self.assertIn("provider_no <> '100001'", sql)

    def test_camel_case_provider_column_is_recognized(self):
        sql = o19etl.seed_group_retry_delete(
            "ProviderPreference", "carlos", "breakglass", "100001",
            {"providerNo": {}})
        self.assertIn("providerNo <> '100001'", sql)

    def test_seed_group_is_derived_from_the_manifest(self):
        group = o19etl.seed_group_tables()
        for t, _ in o19map_schema.CARLOSDOC_SEED_DELETES:
            self.assertIn(t, group)
        self.assertIn("provider", group)
        self.assertIn("security", group)


class TestForceReset(unittest.TestCase):

    """Every imported login is forced to reset its password."""
    def test_every_imported_user_is_forced_to_reset(self):
        self.assertEqual(
            o19etl.force_reset_statement("carlos"),
            "UPDATE `carlos`.security SET forcePasswordReset = 1")


class TestRowParityExpectations(unittest.TestCase):

    """The admin identity is the only tolerated row-count delta."""
    def test_parity_helper_itemizes_admin_delta(self):
        # fake query: staging has 5 providers, target has 6 (5 + admin);
        # the admin's own row is counted EXACTLY on the target
        def q(sql):
            if "information_schema" in sql:
                return [["provider"]]
            if "`carlos`.`provider` WHERE provider_no = '100001'" in sql:
                return [["1"]]
            if "`stage`.`provider`" in sql:
                return [["5"]]
            if "`carlos`.`provider`" in sql:
                return [["6"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos",
                                    admin_user="breakglass",
                                    admin_provider_no="100001")
        self.assertEqual(bad, [])
        self.assertTrue(any("+1 break-glass admin" in line for line in ok))

    def test_parity_rejects_extra_rows_beyond_the_admin(self):
        # target has 7 providers: 5 copied + admin + one unexplained row
        def q(sql):
            if "information_schema" in sql:
                return [["provider"]]
            if "WHERE provider_no = '100001'" in sql:
                return [["1"]]
            if "`stage`.`provider`" in sql:
                return [["5"]]
            if "`carlos`.`provider`" in sql:
                return [["7"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos",
                                    admin_user="breakglass",
                                    admin_provider_no="100001")
        self.assertEqual(len(bad), 1)

    def test_parity_without_admin_identity_or_ledger_tolerates_nothing(self):
        def q(sql):
            if "information_schema" in sql:
                return [["ProviderPreference"]]
            if "`stage`." in sql:
                return [["3"]]
            return [["4"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos")
        self.assertEqual(len(bad), 1)

    def test_admin_row_predicates_cover_only_identity_tables(self):
        self.assertEqual(set(o19etl.ADMIN_ROW_PREDICATES),
                         {"provider", "security", "secUserRole"})
        self.assertIsNone(o19etl.admin_row_count_sql(
            "ProviderPreference", "carlos", "a", "1"))
        sql = o19etl.admin_row_count_sql("security", "carlos", "a'b", "1")
        self.assertIn("user_name = 'a\\'b'", sql)


class TestAdminUserSafety(unittest.TestCase):

    """--admin-user reaches SQL, so its accepted class is narrow."""
    def test_plain_names_pass(self):
        for name in ("breakglass", "it.admin@clinic", "ops-2", "A"):
            self.assertEqual(o19etl.validate_admin_user(name), name)

    def test_quotes_and_sql_fragments_are_refused(self):
        for bad in ("x'; DROP TABLE security; --", "a b", "", None,
                    "x" * 31, "-lead", "semi;colon"):
            with self.assertRaises(ValueError):
                o19etl.validate_admin_user(bad)

    def test_seed_statements_refuse_unsafe_user(self):
        with self.assertRaises(ValueError):
            o19etl.seed_admin_statements("carlos", "x'y", "1", "h", "1234")
        with self.assertRaises(ValueError):
            o19etl.seed_admin_cleanup_statements("carlos", "x'y", "1")

    def test_nul_is_encoded_not_passed_raw(self):
        # the client refuses a raw NUL in a statement; decoded batch
        # values may carry one
        self.assertEqual(o19etl._sql_str("a\0b"), "a\\0b")
        self.assertEqual(o19etl._sql_str("a\\'b"), "a\\\\\\'b")

    def test_hash_and_pin_are_escaped(self):
        stmts = o19etl.seed_admin_statements(
            "carlos", "bg", "100001", "{bcrypt}$2b$12$a'b", "12'4")
        self.assertIn("'{bcrypt}$2b$12$a\\'b'", stmts[1])
        self.assertIn("'12\\'4'", stmts[1])

    def test_cleanup_targets_only_the_admin_identity(self):
        stmts = o19etl.seed_admin_cleanup_statements("carlos", "bg", "100001")
        self.assertEqual(len(stmts), 3)
        for sql in stmts:
            self.assertIn("'100001'", sql)
        self.assertTrue(
            stmts[0].startswith("DELETE FROM `carlos`.secUserRole"))
        self.assertIn("user_name = 'bg' AND provider_no = '100001'", stmts[1])
        self.assertTrue(stmts[2].startswith("DELETE FROM `carlos`.provider"))


if __name__ == "__main__":
    unittest.main()
