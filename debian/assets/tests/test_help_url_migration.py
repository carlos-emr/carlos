# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The postinst's one-time rewrite of the old stock Help link.

Every alpha shipped ``resource_base_url=http://oscarmanual.org/14/``, a path that
now returns 404, so the Help link on the schedule page and the provider menu
opened a "not found" page. install_once copied that line into
/etc/carlos-emr/carlos.properties, the override that wins over the WAR default,
so the postinst rewrites it once to the line the package's skeleton carries.

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
VERIFY = ROOT / "scripts" / "deb-upgrade-verify.sh"
SHIPPED = ROOT / "src" / "main" / "resources" / "carlos.properties"
SENTINEL = ".help-url-default-migrated"
KEY = "resource_base_url="
OLD = "resource_base_url=http://oscarmanual.org/14/"


def migration_block():
    """The postinst's help-URL block, from its sentinel to its closing fi."""
    text = POSTINST.read_text(encoding="utf-8")
    m = re.search(
        r'^        HELP_URL_SENTINEL=.*?^        fi\n', text, re.S | re.M)
    if not m:
        raise AssertionError(
            "no HELP_URL_SENTINEL block in carlos-emr.postinst -- this "
            "contract has lost its subject")
    return m.group(0)


def old_line():
    m = re.search(r"^\s*HELP_URL_OLD='([^']*)'$", migration_block(), re.M)
    if not m:
        raise AssertionError("the block does not state HELP_URL_OLD")
    return m.group(1)


def shipped_line():
    lines = [line for line in SHIPPED.read_text(encoding="utf-8").splitlines()
             if line.startswith(KEY)]
    if len(lines) != 1:
        raise AssertionError(
            "carlos.properties defines resource_base_url "
            "{0} times, not once".format(len(lines)))
    return lines[0]


class TestHelpUrlMigration(unittest.TestCase):

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

    def test_old_line_is_the_dead_oscar_manual_path(self):
        self.assertEqual(old_line(), OLD)
        self.assertNotEqual(old_line(), shipped_line())
        self.assertNotIn("oscarmanual.org/14", shipped_line())

    def test_verifier_states_the_same_pair(self):
        # scripts/deb-upgrade-verify.sh sanctions this rewrite in its
        # properties comparison; a pair that drifted from the postinst would
        # make a correct upgrade fail it.
        text = VERIFY.read_text(encoding="utf-8")
        self.assertIn("HELP_URL_OLD='{0}'".format(old_line()), text)
        self.assertIn("HELP_URL_NEW='{0}'".format(shipped_line()), text)

    def test_rewrites_the_old_stock_line_to_the_skeleton_line(self):
        self.write_properties("before=1", OLD, "after=2")

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         "before=1\n{0}\nafter=2\n".format(shipped_line()))
        self.assertIn(OLD + " -> " + shipped_line(), result.stdout)
        self.assertNotIn("WARNING", result.stderr)
        self.assertEqual(stat.S_IMODE(self.properties.stat().st_mode), 0o640)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")

    def test_leaves_a_url_the_operator_chose(self):
        for chosen in ("https://help.example.org/", "http://oscarmanual.org/15/",
                       "https://oscarmanual.org/14/", "http://oscarmanual.org/14"):
            with self.subTest(chosen=chosen):
                (self.state / SENTINEL).unlink(missing_ok=True)
                self.write_properties("before=1", KEY + chosen)
                before = self.properties.read_bytes()

                result = self.run_block()

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), before)
                self.assertTrue((self.state / SENTINEL).exists())
                self.assertNotIn("WARNING", result.stderr)

    def test_leaves_a_key_the_operator_defined_twice(self):
        # java.util.Properties honours the last definition, so a second one,
        # even an identical one, is the operator's override.
        self.write_properties(OLD, KEY + "https://help.example.org/")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_counts_definitions_in_java_properties_syntax(self):
        # An indented key, and a whitespace or colon separator, are
        # definitions too: the later one is the operator's.
        for override in ("  " + KEY + "x", "resource_base_url x",
                         "\tresource_base_url : x"):
            with self.subTest(override=override):
                (self.state / SENTINEL).unlink(missing_ok=True)
                self.write_properties(OLD, override)
                before = self.properties.read_bytes()

                result = self.run_block()

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), before)

    def test_does_not_touch_a_similarly_named_key(self):
        self.write_properties("resource_base_url_other=1", OLD)

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         "resource_base_url_other=1\n{0}\n".format(shipped_line()))

    def test_leaves_the_line_when_it_has_crlf_line_endings(self):
        # Not the exact stock line, so it is the operator's to change.
        self.properties.write_bytes(
            "before=1\r\n{0}\r\n".format(OLD).encode("utf-8"))
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_rewrites_a_file_without_a_final_newline(self):
        self.properties.write_text("before=1\n" + OLD, encoding="utf-8")

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
        self.write_properties("before=1", OLD)
        before = self.properties.read_bytes()

        result = self.run_block(path=str(bin_dir))

        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("could not rewrite resource_base_url", result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")
        self.assertFalse((self.state / SENTINEL).exists())

    def test_runs_only_once(self):
        self.write_properties(OLD)
        (self.state / SENTINEL).write_text("", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)

    def test_waits_for_the_provisioning_lock(self):
        self.write_properties(OLD)
        before = self.properties.read_bytes()

        result = self.run_block(lock_held="0")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())

    def test_retries_when_the_skeleton_lacks_the_key(self):
        self.write_properties(OLD)
        self.skel.write_text("other=stock\n", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())
        self.assertIn("does not define one live resource_base_url",
                      result.stderr)

    def test_retries_when_the_skeleton_value_is_empty_or_still_dead(self):
        for skel_line in (KEY, OLD):
            with self.subTest(skel_line=skel_line):
                self.write_properties(OLD)
                self.skel.write_text("other=stock\n" + skel_line + "\n",
                                     encoding="utf-8")
                before = self.properties.read_bytes()

                result = self.run_block()

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.properties.read_bytes(), before)
                self.assertFalse((self.state / SENTINEL).exists())
                self.assertIn("does not define one live resource_base_url",
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
