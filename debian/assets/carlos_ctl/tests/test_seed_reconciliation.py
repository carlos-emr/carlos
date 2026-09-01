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

    def test_every_imported_user_is_forced_to_reset(self):
        self.assertEqual(
            o19etl.force_reset_statement("carlos"),
            "UPDATE `carlos`.security SET forcePasswordReset = 1")


class TestRowParityExpectations(unittest.TestCase):

    def test_parity_helper_itemizes_admin_delta(self):
        # fake query: staging has 5 providers, target has 6 (5 + admin)
        def q(sql):
            if "information_schema" in sql:
                return [["provider"]]
            if "`stage`.`provider`" in sql:
                return [["5"]]
            if "`carlos`.`provider`" in sql:
                return [["6"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos")
        self.assertEqual(bad, [])
        self.assertTrue(any("break-glass admin" in line for line in ok))

    def test_parity_flags_a_short_copy(self):
        def q(sql):
            if "information_schema" in sql:
                return [["demographic"]]
            if "`stage`.`demographic`" in sql:
                return [["100"]]
            if "`carlos`.`demographic`" in sql:
                return [["90"]]
            return [["0"]]
        ok, bad = o19etl.row_parity(q, "stage", "carlos")
        self.assertEqual(len(bad), 1)
        self.assertIn("demographic: staging 100 -> target 90", bad[0])


if __name__ == "__main__":
    unittest.main()
