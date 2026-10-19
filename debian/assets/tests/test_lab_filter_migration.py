# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The postinst's one-time rewrite of the old stock HL7_LAB_MEASUREMENT_FILTER.

Until #4613 the stock filter had the alternative ``^([+-\\\\?]{1,4})``, whose
``+-\\?`` is a character RANGE, so Hl7textResultsData filed every lab result
made of five or more digits and signs as its first four characters (112.5 as
"112."). install_once copied that line into /etc/carlos-emr/carlos.properties,
the override that wins over the WAR default, so the postinst rewrites it once.

The block is cut out of the real postinst and run under ``sh`` against a
fixture file, so these tests exercise the shipped shell, not a copy of it.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import os
import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"
POSTRM = ROOT / "debian" / "carlos-emr.postrm"
SHIPPED = ROOT / "src" / "main" / "resources" / "carlos.properties"
SENTINEL = ".lab-measurement-filter-migrated"
KEY = "HL7_LAB_MEASUREMENT_FILTER="


def migration_block():
    """The postinst's lab-filter block, from its sentinel to its closing fi."""
    text = POSTINST.read_text(encoding="utf-8")
    m = re.search(
        r'^        LAB_FILTER_SENTINEL=.*?^        fi\n', text, re.S | re.M)
    if not m:
        raise AssertionError(
            "no LAB_FILTER_SENTINEL block in carlos-emr.postinst -- this "
            "contract has lost its subject")
    return m.group(0)


def old_line():
    m = re.search(r"^\s*LAB_FILTER_OLD='([^']*)'$", migration_block(), re.M)
    if not m:
        raise AssertionError("the block does not state LAB_FILTER_OLD")
    return m.group(1)


def shipped_line():
    lines = [line for line in SHIPPED.read_text(encoding="utf-8").splitlines()
             if line.startswith(KEY)]
    if len(lines) != 1:
        raise AssertionError(
            "carlos.properties defines HL7_LAB_MEASUREMENT_FILTER "
            "{0} times, not once".format(len(lines)))
    return lines[0]


class TestLabFilterMigration(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.state = root / "state"
        self.share = root / "share"
        self.etc = root / "etc"
        for d in (self.state, self.share / "skel", self.etc):
            d.mkdir(parents=True)
        self.properties = self.etc / "carlos.properties"
        self.skel = self.share / "skel" / "carlos.properties"
        self.skel.write_text("other=stock\n{0}\n".format(shipped_line()),
                             encoding="utf-8")

    def run_block(self, lock_held="1", path=None):
        # The postinst sets these four itself; the block reads them as shell
        # variables, which the environment provides here.
        env = dict(os.environ, PROVISION_LOCK_HELD=lock_held,
                   STATE=str(self.state), SHARE=str(self.share),
                   PROPERTIES=str(self.properties))
        if path:
            env["PATH"] = path + os.pathsep + env["PATH"]
        return subprocess.run(["sh", "-c", "set -e\n" + migration_block()],
                              env=env, capture_output=True, text=True,
                              check=False)

    def write_properties(self, *lines):
        self.properties.write_text("".join(l + "\n" for l in lines),
                                   encoding="utf-8")
        self.properties.chmod(0o640)

    def test_old_line_is_the_four_character_range_filter(self):
        self.assertTrue(old_line().startswith(KEY))
        self.assertIn("|^([+-\\\\?]{1,4})|", old_line())
        self.assertNotEqual(old_line(), shipped_line())
        self.assertNotIn("{1,4}", shipped_line())

    def test_rewrites_the_old_stock_line_to_the_skeleton_line(self):
        self.write_properties("before=1", old_line(), "after=2")

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         "before=1\n{0}\nafter=2\n".format(shipped_line()))
        self.assertIn("corrected the stock HL7_LAB_MEASUREMENT_FILTER",
                      result.stdout)
        self.assertNotIn("WARNING", result.stderr)
        self.assertEqual(stat.S_IMODE(self.properties.stat().st_mode), 0o640)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")

    def test_leaves_a_key_the_operator_defined_twice(self):
        self.write_properties(old_line(), KEY + ".*")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())
        # the definition in effect is the operator's, so nothing to warn about
        self.assertNotIn("WARNING", result.stderr)

    def test_counts_definitions_in_java_properties_syntax(self):
        # java.util.Properties takes an indented key, and a whitespace
        # separator, as a definition too: the later one is the operator's.
        for override in ("  " + KEY + ".*", "HL7_LAB_MEASUREMENT_FILTER .*",
                         "\tHL7_LAB_MEASUREMENT_FILTER : .*"):
            with self.subTest(override=override):
                (self.state / SENTINEL).unlink(missing_ok=True)
                self.write_properties(old_line(), override)
                before = self.properties.read_bytes()

                result = self.run_block()

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), before)
                self.assertNotIn("WARNING", result.stderr)

    def test_leaves_a_value_the_operator_chose(self):
        self.write_properties("before=1", KEY + "^\\\\d+")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertNotIn("WARNING", result.stderr)

    def test_warns_when_the_old_filter_has_crlf_line_endings(self):
        # java.util.Properties reads CRLF, so the cut goes on; the line is not
        # the exact stock one, so it is left alone and named instead.
        self.properties.write_bytes(
            "before=1\r\n{0}\r\n".format(old_line()).encode("utf-8"))
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("still has the alternative", result.stderr)
        self.assertIn("#4613", result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_warns_when_the_old_filter_has_trailing_blanks(self):
        self.write_properties(old_line() + "  ")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("still has the alternative", result.stderr)

    def test_rewrites_a_file_without_a_final_newline(self):
        self.properties.write_text("before=1\n" + old_line(),
                                   encoding="utf-8")

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         "before=1\n{0}\n".format(shipped_line()))

    def test_keeps_the_file_and_fails_when_the_rewrite_fails(self):
        # A chown that fails stands in for any failed step of the rewrite.
        bin_dir = Path(self.tmp.name) / "bin"
        bin_dir.mkdir()
        chown = bin_dir / "chown"
        chown.write_text("#!/bin/sh\nexit 1\n", encoding="utf-8")
        chown.chmod(0o755)
        self.write_properties("before=1", old_line())
        before = self.properties.read_bytes()

        result = self.run_block(path=str(bin_dir))

        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("could not rewrite HL7_LAB_MEASUREMENT_FILTER",
                      result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")
        self.assertFalse((self.state / SENTINEL).exists())

    def test_runs_only_once(self):
        self.write_properties(old_line())
        (self.state / SENTINEL).write_text("", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)

    def test_waits_for_the_provisioning_lock(self):
        self.write_properties(old_line())
        before = self.properties.read_bytes()

        result = self.run_block(lock_held="0")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())

    def test_retries_when_the_skeleton_lacks_the_key(self):
        self.write_properties(old_line())
        self.skel.write_text("other=stock\n", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())
        self.assertIn("does not define one non-empty HL7_LAB_MEASUREMENT_FILTER",
                      result.stderr)

    def test_retries_when_the_skeleton_value_is_empty(self):
        # An empty filter would file every result as an empty value.
        self.write_properties(old_line())
        self.skel.write_text("other=stock\n" + KEY + "\n", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())
        self.assertIn("does not define one non-empty HL7_LAB_MEASUREMENT_FILTER",
                      result.stderr)

    def test_writes_the_sentinel_on_a_fresh_install(self):
        self.write_properties(shipped_line())
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_purge_removes_the_sentinel(self):
        # purge keeps carlos.properties, so a reinstall must migrate again
        self.assertIn('"${STATE}/' + SENTINEL + '"',
                      POSTRM.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
