# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The Java runtime gate in front of every packaged Flyway command.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .

dbops._find_java() picks the JVM that db-migrate / db-info / db-validate /
db-baseline / db-repair run on. The engine, the JDBC driver and the migration
files all come out of the deployed WAR, so the gate is not cosmetic: the WAR
is compiled at class file version 69 and an older JVM cannot load it at all,
while a newer one fails deep in class loading instead of with a usable
message. That makes an off-by-one here an operator-visible migration failure
on a production upgrade, which is why the accepted version is pinned by test
and not only by the string in the source.
"""

import os
import stat
import tempfile
import unittest
from unittest import mock

from carlos_ctl import dbops


def _make_jvm(root, name, release_line, executable=True):
    """Lay out the two things _find_java() looks at: the release file it
    reads the version from, and bin/java it must be able to execute."""
    home = os.path.join(root, name)
    os.makedirs(os.path.join(home, "bin"), exist_ok=True)
    if release_line is not None:
        with open(os.path.join(home, "release"), "w", encoding="utf-8") as fh:
            fh.write('IMPLEMENTOR="Ubuntu"\n')
            fh.write(release_line + "\n")
            fh.write('OS_NAME="Linux"\n')
    java = os.path.join(home, "bin", "java")
    with open(java, "w", encoding="utf-8") as fh:
        fh.write("#!/bin/sh\n")
    mode = stat.S_IRUSR | stat.S_IWUSR
    os.chmod(java, mode | stat.S_IXUSR if executable else mode)
    return home


class TestIsJava25(unittest.TestCase):
    """_is_java_25() reads the JDK's own release file rather than spawning a
    JVM, so it has to be exact about what that file can say."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, self.tmp, True)

    def test_a_java_25_release_file_is_accepted(self):
        home = _make_jvm(self.tmp, "j25", 'JAVA_VERSION="25.0.1"')
        self.assertTrue(dbops._is_java_25(home))

    def test_a_java_25_ga_release_file_is_accepted(self):
        home = _make_jvm(self.tmp, "j25ga", 'JAVA_VERSION="25"')
        self.assertTrue(dbops._is_java_25(home))

    def test_java_21_is_rejected(self):
        home = _make_jvm(self.tmp, "j21", 'JAVA_VERSION="21.0.10"')
        self.assertFalse(dbops._is_java_25(home))

    def test_a_newer_jdk_is_rejected_too(self):
        # Newer is not "good enough": the failure mode for a JVM the WAR was
        # not built against is a class-loading crash, not a clean refusal.
        home = _make_jvm(self.tmp, "j26", 'JAVA_VERSION="26"')
        self.assertFalse(dbops._is_java_25(home))

    def test_a_missing_release_file_is_rejected(self):
        home = _make_jvm(self.tmp, "norelease", None)
        self.assertFalse(dbops._is_java_25(home))

    def test_a_directory_that_does_not_exist_is_rejected(self):
        self.assertFalse(dbops._is_java_25(os.path.join(self.tmp, "absent")))


class TestFindJava(unittest.TestCase):
    """_find_java() walks the candidate JVM homes and returns the first that
    is both Java 25 and actually executable."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, self.tmp, True)

    def _scoped(self):
        """_find_java() also probes two hardcoded absolute paths
        (/usr/lib/jvm/java-25-openjdk and /usr/lib/jvm/default-java). Confine
        the real gate to this test's temp tree so a JVM that happens to be
        installed on the build machine cannot decide the outcome. The gate
        itself is exercised for real on the temp candidates, and covered
        directly by TestIsJava25 above."""
        real = dbops._is_java_25
        return lambda home: home.startswith(self.tmp) and real(home)

    def _find(self, globbed):
        with mock.patch.object(dbops.glob, "glob", return_value=globbed), \
             mock.patch.object(dbops, "_is_java_25", side_effect=self._scoped()):
            return dbops._find_java()

    def test_a_java_25_home_is_selected(self):
        home = _make_jvm(self.tmp, "java-25-openjdk-amd64", 'JAVA_VERSION="25.0.1"')
        self.assertEqual(self._find([home]), os.path.join(home, "bin", "java"))

    def test_a_java_21_home_is_passed_over_for_the_java_25_one(self):
        old = _make_jvm(self.tmp, "java-21-openjdk-amd64", 'JAVA_VERSION="21.0.10"')
        new = _make_jvm(self.tmp, "java-25-openjdk-amd64", 'JAVA_VERSION="25.0.1"')
        self.assertEqual(self._find([old, new]), os.path.join(new, "bin", "java"))

    def test_a_java_25_home_without_an_executable_java_is_skipped(self):
        # A half-removed JDK leaves the release file behind; running it would
        # fail with a permission error far from the cause.
        #
        # _find_java() SORTS its glob results, so the broken home has to sort
        # BEFORE the good one or the loop returns without ever reaching it and
        # this test passes even with the os.access() guard deleted. The "a-" /
        # "z-" prefixes exist solely to pin that order; keep them if these
        # names are ever changed. Asserted below rather than left to the reader.
        broken = _make_jvm(self.tmp, "java-25-openjdk-a-broken",
                           'JAVA_VERSION="25.0.1"', executable=False)
        good = _make_jvm(self.tmp, "java-25-openjdk-z-good", 'JAVA_VERSION="25.0.1"')
        self.assertEqual(sorted([broken, good])[0], broken,
                         "broken JVM must sort first or the guard is untested")
        self.assertEqual(self._find([broken, good]),
                         os.path.join(good, "bin", "java"))

    def test_no_java_25_anywhere_is_a_named_failure(self):
        old = _make_jvm(self.tmp, "java-21-openjdk-amd64", 'JAVA_VERSION="21.0.10"')
        with self.assertRaises(SystemExit) as caught:
            self._find([old])
        self.assertNotEqual(caught.exception.code, 0)

    def test_nothing_installed_at_all_is_a_named_failure(self):
        with self.assertRaises(SystemExit):
            self._find([])


if __name__ == "__main__":
    unittest.main()
