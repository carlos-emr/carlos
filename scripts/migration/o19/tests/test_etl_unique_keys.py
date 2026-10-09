# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The pinned CLI copies with the target's unique keys enforced (#4100).

With FOREIGN_KEY_CHECKS=0 and UNIQUE_CHECKS=0, MariaDB 11.8 stores no rows
and reports success when an INSERT ... SELECT into an empty InnoDB table
repeats a unique key. CARLOS's `demographicExt` carries a key OSCAR 19's
does not, and OSCAR 19 appends a row on every edit of a patient's extension
value, so a real clinic's copy can collide. The live proof is
`verify_sql_semantics.py` ("unique keys during the copy"), which has no CI
job; these tests run in debian-python-tests.yml against the pinned
carlos-ctl and keep that proof from going stale or vacuous.

Run (from the repository root):
    python3 -m unittest discover -s scripts/migration/o19/tests -t .
"""

import importlib.util
import subprocess
import unittest
from pathlib import Path
from unittest import mock

from carlos_ctl import o19etl, o19import, o19map_schema

VERIFY = Path(__file__).resolve().parents[1] / "verify_sql_semantics.py"


def init_command(argv):
    found = [a for a in argv if a.startswith("--init-command=")]
    if len(found) != 1:
        raise AssertionError("expected one --init-command, got {0!r}"
                             .format(found))
    return found[0][len("--init-command="):]


class TestThePinnedEtlSession(unittest.TestCase):

    def test_the_copy_session_keeps_unique_checks_on(self):
        calls = []

        def fake(argv, sql):
            calls.append(argv)
            return subprocess.CompletedProcess(argv, 0, "", "")

        for timeout in (0, 3600):
            with self.subTest(statement_timeout=timeout), \
                    mock.patch.object(o19import, "run_sql_client", fake):
                o19import.make_etl_query(["mariadb"], timeout)("SELECT 1")
                prelude = init_command(calls[-1]).replace(" ", "").upper()
                self.assertIn("UNIQUE_CHECKS=1", prelude)
                self.assertNotIn("UNIQUE_CHECKS=0", prelude)
                # foreign keys stay deferred: the copy runs in manifest
                # order, not dependency order
                self.assertIn("FOREIGN_KEY_CHECKS=0", prelude)

    def test_a_duplicate_key_refusal_withholds_the_value(self):
        error = ("ERROR 1062 (23000) at line 1: Duplicate entry "
                 "'100-FAKE-PATIENT' for key 'uk_demo_ext_single_value'\n")

        def fake(argv, sql):
            return subprocess.CompletedProcess(argv, 1, "", error)

        with mock.patch.object(o19import, "run_sql_client", fake):
            query = o19import.make_etl_query(["mariadb"])
            with self.assertRaises(o19etl.QueryError) as raised:
                query("INSERT INTO `oscar`.`demographicExt` SELECT 1")
        for text in (str(raised.exception), raised.exception.stderr):
            self.assertIn("1062", text)
            self.assertNotIn("FAKE", text)


class TestTheLiveCheckStaysMeaningful(unittest.TestCase):
    """`verify_sql_semantics.py` builds the target from the migrations; a
    schema change that moved the table or the key must break here, not
    turn the live check into one that passes with nothing to collide."""

    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location(
            "verify_sql_semantics_under_test", VERIFY)
        cls.verify = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.verify)

    def test_the_target_ddl_carries_the_unique_key_the_check_relies_on(self):
        create, upgrade = self.verify._demographic_ext_target_ddl()
        self.assertTrue(create.startswith("CREATE TABLE `demographicExt` ("))
        self.assertIn("UNIQUE KEY `uk_demo_ext`", create)
        self.assertIn("uk_demo_ext_single_value", upgrade)
        self.assertIn("excludeIndicator", upgrade)

    def test_demographic_ext_is_copied_with_the_fixture_columns(self):
        entry = o19map_schema.TABLES["demographicExt"]
        self.assertEqual(entry["class"], "copy")
        # the fixture's VALUES are positional over exactly these columns
        self.assertEqual(entry["cols"], ["id", "demographic_no",
                                         "provider_no", "key_val", "value",
                                         "date_time", "hidden"])

    def test_the_fixture_collides_only_where_carlos_forbids_it(self):
        def key(row):
            fields = [f.strip().strip("'")
                      for f in row.strip("()").split(",")]
            demographic, key_val = fields[1], fields[3]
            if demographic == "NULL" or key_val == "excludeIndicator":
                return None  # the key admits these repeats
            return demographic, key_val

        allowed = [key(r) for r in self.verify.UNIQUE_ALLOWED_ROWS]
        kept = [k for k in allowed if k is not None]
        self.assertEqual(len(kept), len(set(kept)),
                         "the allowed rows must not collide among themselves")
        colliding = key(self.verify.UNIQUE_COLLIDING_ROW)
        self.assertIn(colliding, kept)
        # MariaDB's 1062 text names the KEY, not the payload: the marker the
        # live check looks for in a leaked refusal must be in the key
        self.assertIn("FAKEKEY", colliding[1])


if __name__ == "__main__":
    unittest.main()
