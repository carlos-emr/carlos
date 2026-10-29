# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""debian/check-war-commons-logging.py against synthetic WARs (#5413).

The packaged WAR must carry exactly one Commons Logging implementation,
WEB-INF/lib/commons-logging-<version>.jar, and nothing that overrides its
LogFactory discovery. Each case below builds a small WAR in a temporary
directory with the jar layouts that broke, or would break, that rule.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""
import io
import re
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CHECK = ROOT / "debian" / "check-war-commons-logging.py"
RULES = ROOT / "debian" / "rules"

# The five classes issue #5413 found defined twice, plus a few of the rest.
JCL_CLASSES = [
    "org/apache/commons/logging/Log.class",
    "org/apache/commons/logging/LogFactory.class",
    "org/apache/commons/logging/LogConfigurationException.class",
    "org/apache/commons/logging/impl/NoOpLog.class",
    "org/apache/commons/logging/impl/SimpleLog.class",
    "org/apache/commons/logging/impl/Log4jApiLogFactory.class",
]
SERVICE = "META-INF/services/org.apache.commons.logging.LogFactory"
LOG4J_API_FACTORY = "org/apache/commons/logging/impl/Log4jApiLogFactory.class"


def jar_bytes(entries):
    """A jar holding each named entry (value: bytes, or None for a stub)."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as jar:
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        for name, data in entries.items():
            jar.writestr(name, data if data is not None else b"\xca\xfe\xba\xbe")
    return buf.getvalue()


def commons_logging_jar():
    return jar_bytes({name: None for name in JCL_CLASSES})


def commons_logging_1_2_jar():
    """commons-logging 1.2: the same API classes, but no Log4jApiLogFactory."""
    return jar_bytes({name: None for name in JCL_CLASSES if "Log4jApi" not in name})


def flag_member(war_path, member, *, flag_bits=None, compress_type=None):
    """Rewrite one member's central-directory record in place.

    zipfile cannot write an encrypted entry or one with an unsupported
    compression method, but it reads both fields from the central directory,
    so patching the record reproduces what such a WAR looks like to the check.
    """
    data = bytearray(Path(war_path).read_bytes())
    name = member.encode()
    pos = 0
    while True:
        pos = data.index(b"PK\x01\x02", pos)
        name_len = int.from_bytes(data[pos + 28:pos + 30], "little")
        if data[pos + 46:pos + 46 + name_len] == name:
            break
        pos += 4
    if flag_bits is not None:
        data[pos + 8:pos + 10] = flag_bits.to_bytes(2, "little")
    if compress_type is not None:
        data[pos + 10:pos + 12] = compress_type.to_bytes(2, "little")
    Path(war_path).write_bytes(bytes(data))


def jcl_over_slf4j_jar():
    return jar_bytes({
        "org/apache/commons/logging/Log.class": None,
        "org/apache/commons/logging/LogFactory.class": None,
        "org/apache/commons/logging/impl/SLF4JLogFactory.class": None,
        SERVICE: b"org.apache.commons.logging.impl.SLF4JLogFactory\n",
    })


def unrelated_jar():
    return jar_bytes({"org/example/Thing.class": None})


class CheckWarCommonsLoggingTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def war(self, entries):
        """Write a WAR with the given {entry name: bytes} and return its path."""
        path = self.tmp / "carlos.war"
        with zipfile.ZipFile(path, "w") as war:
            war.writestr("WEB-INF/web.xml", "<web-app/>")
            for name, data in entries.items():
                war.writestr(name, data)
        return path

    def run_check(self, *args):
        """Run the checker as debian/rules does, capturing its output."""
        return subprocess.run(
            [sys.executable, str(CHECK), *map(str, args)],
            capture_output=True, text=True, check=False)

    def assert_refused(self, result, *fragments):
        """The check exited 1 and its diagnostics name every fragment."""
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        for fragment in fragments:
            self.assertIn(fragment, result.stderr)

    def test_accepts_single_commons_logging_jar(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/log4j-api-2.25.5.jar": unrelated_jar(),
            "WEB-INF/classes/io/github/Example.class": b"",
        }))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("WEB-INF/lib/commons-logging-1.4.0.jar", result.stdout)
        self.assertIn(f"({len(JCL_CLASSES)} classes)", result.stdout)

    def test_refuses_jcl_over_slf4j_beside_commons_logging(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/jcl-over-slf4j-2.0.20.jar": jcl_over_slf4j_jar(),
        }))
        self.assert_refused(
            result,
            "more than one class-path source",
            "WEB-INF/lib/jcl-over-slf4j-2.0.20.jar",
            f"carries {SERVICE}")

    def test_refuses_jcl_over_slf4j_as_only_implementation(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/jcl-over-slf4j-2.0.20.jar": jcl_over_slf4j_jar(),
        }))
        self.assert_refused(
            result, "WEB-INF/lib/jcl-over-slf4j-2.0.20.jar defines org.apache.commons.logging")

    def test_refuses_shaded_copy_in_another_jar(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/some-library-all-3.1.jar": jar_bytes({
                "org/apache/commons/logging/impl/NoOpLog.class": None}),
        }))
        self.assert_refused(result, "WEB-INF/lib/some-library-all-3.1.jar (1 classes)")

    def test_refuses_multi_release_copy_in_another_jar(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/mr-library-1.0.jar": jar_bytes({
                "META-INF/versions/11/org/apache/commons/logging/Log.class": None}),
        }))
        self.assert_refused(result, "WEB-INF/lib/mr-library-1.0.jar")

    def test_refuses_discovery_override_without_classes(self):
        # log4j-jcl defines no JCL classes but still redirects LogFactory.
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/log4j-jcl-2.25.5.jar": jar_bytes({
                SERVICE: b"org.apache.logging.log4j.jcl.LogFactoryImpl\n"}),
        }))
        self.assert_refused(result, f"WEB-INF/lib/log4j-jcl-2.25.5.jar carries {SERVICE}")

    def test_refuses_properties_override_in_web_inf_classes(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/classes/commons-logging.properties":
                b"org.apache.commons.logging.LogFactory=example.Factory\n",
        }))
        self.assert_refused(result, "WEB-INF/classes carries commons-logging.properties")

    def test_refuses_loose_classes_in_web_inf_classes(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/classes/org/apache/commons/logging/Log.class": b"",
        }))
        self.assert_refused(result, "WEB-INF/classes (1 classes)")

    def test_refuses_war_without_commons_logging(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/log4j-api-2.25.5.jar": unrelated_jar(),
        }))
        self.assert_refused(result, "no jar in WEB-INF/lib defines org.apache.commons.logging")

    def test_refuses_commons_logging_api_artifact(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-api-1.1.jar": commons_logging_jar(),
        }))
        self.assert_refused(result, "only WEB-INF/lib/commons-logging-<version>.jar may")

    def test_refuses_commons_logging_before_log4j_api_support(self):
        # A prebuilt CARLOS_WAR skips the Maven build, so this is the only gate
        # on the version: 1.2 has no Log4jApiLogFactory and cannot reach Log4j 2.
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.2.jar": commons_logging_1_2_jar(),
        }))
        self.assert_refused(
            result,
            "WEB-INF/lib/commons-logging-1.2.jar has no "
            "org/apache/commons/logging/impl/Log4jApiLogFactory.class",
            "commons-logging 1.3 or later")

    def test_refuses_factory_present_only_as_versioned_copy(self):
        # Java 25 does not load META-INF/versions/99, and nothing loads a versioned
        # entry from a jar without Multi-Release: true; only the base entry counts.
        classes = {name: None for name in JCL_CLASSES if "Log4jApi" not in name}
        classes["META-INF/versions/99/" + LOG4J_API_FACTORY] = None
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.2.jar": jar_bytes(classes),
        }))
        self.assert_refused(result, f"WEB-INF/lib/commons-logging-1.2.jar has no {LOG4J_API_FACTORY}")

    def test_refuses_versioned_discovery_override(self):
        # A multi-release jar serves META-INF/versions/N/commons-logging.properties
        # for getResources("commons-logging.properties") on a new enough JVM.
        versioned = "META-INF/versions/9/commons-logging.properties"
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/mr-library-1.0.jar": jar_bytes({
                versioned: b"org.apache.commons.logging.LogFactory=example.Factory\n"}),
        }))
        self.assert_refused(result, f"WEB-INF/lib/mr-library-1.0.jar carries {versioned}")

    def test_refuses_commons_logging_jar_without_log_factory(self):
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": jar_bytes({
                "org/apache/commons/logging/Log.class": None}),
        }))
        self.assert_refused(result, "has no org/apache/commons/logging/LogFactory.class")

    def test_ignores_jars_outside_web_inf_lib(self):
        # Tomcat reads only WEB-INF/lib/*.jar; a nested or stray jar is not on the class path.
        result = self.run_check(self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/nested/jcl-over-slf4j-2.0.20.jar": jcl_over_slf4j_jar(),
            "META-INF/jcl-over-slf4j-2.0.20.jar": jcl_over_slf4j_jar(),
        }))
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_fails_closed_on_unreadable_inputs(self):
        not_a_war = self.tmp / "carlos.war"
        not_a_war.write_bytes(b"not a zip")
        result = self.run_check(not_a_war)
        self.assertEqual(result.returncode, 2, result.stdout)
        self.assertIn("not a readable WAR", result.stderr)

        corrupt_jar = self.war({
            "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
            "WEB-INF/lib/broken-1.0.jar": b"not a zip",
        })
        result = self.run_check(corrupt_jar)
        self.assertEqual(result.returncode, 2, result.stdout)
        self.assertIn("WEB-INF/lib/broken-1.0.jar is not a readable jar", result.stderr)

        self.assertEqual(self.run_check().returncode, 2)
        self.assertEqual(self.run_check(self.tmp / "missing.war").returncode, 2)

    def test_fails_closed_on_members_zipfile_cannot_open(self):
        """Encrypted or unsupported members are unreadable input (exit 2), never a traceback."""
        for label, patch in (("encrypted", {"flag_bits": 0x1}),
                             ("unsupported compression", {"compress_type": 99})):
            with self.subTest(label):
                war = self.war({
                    "WEB-INF/lib/commons-logging-1.4.0.jar": commons_logging_jar(),
                    "WEB-INF/lib/opaque-1.0.jar": unrelated_jar(),
                })
                flag_member(war, "WEB-INF/lib/opaque-1.0.jar", **patch)
                result = self.run_check(war)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("WEB-INF/lib/opaque-1.0.jar is not a readable jar", result.stderr)
                self.assertNotIn("Traceback", result.stderr)


class RulesRunsCommonsLoggingCheckTest(unittest.TestCase):
    """debian/rules must check the WAR it packages, built or prebuilt."""

    CHECK_LINE = "\tpython3 $(CURDIR)/debian/check-war-commons-logging.py $(BUILDDIR)/carlos.war\n"

    def assert_checks_packaged_war(self, rules):
        """Fail unless `rules` checks carlos.war on its own line after the CARLOS_WAR if/else."""
        build = rules.split("override_dh_auto_build:", 1)[1].split("\noverride_", 1)[0]
        check = re.search(
            r"^\tpython3 \$\(CURDIR\)/debian/check-war-commons-logging\.py "
            r"\$\(BUILDDIR\)/carlos\.war$", build, re.M)
        self.assertIsNotNone(check, "override_dh_auto_build no longer checks carlos.war")
        # Its own recipe line, not a continuation inside a shell block: the line
        # before it must not end in a backslash.
        before = build[:check.start()].rstrip("\n").rsplit("\n", 1)[-1]
        self.assertFalse(before.endswith("\\"), f"check runs inside a shell block: {before!r}")
        # After the CARLOS_WAR if/else closes, so the prebuilt and the built WAR
        # are both checked; inside either branch, one of them would not be.
        branch = build.index('if [ -n "$$CARLOS_WAR" ]; then')
        closed = re.compile(r"^\tfi$", re.M).search(build, branch)
        self.assertIsNotNone(closed, "the CARLOS_WAR if/else no longer closes with a tab-fi line")
        self.assertGreater(check.start(), closed.end(), "check runs before the WAR is in place")

    def test_rules_checks_the_packaged_war(self):
        self.assert_checks_packaged_war(RULES.read_text(encoding="utf-8"))

    def test_contract_rejects_misplaced_checks(self):
        """Each way of moving or dropping the check must fail the contract, for its own reason."""
        rules = RULES.read_text(encoding="utf-8")
        self.assertIn(self.CHECK_LINE, rules)
        without = rules.replace(self.CHECK_LINE, "")
        built_copy = "\t  cp -f target/carlos-*.war $(BUILDDIR)/carlos.war; \\\n"
        prebuilt_branch = '\tif [ -n "$$CARLOS_WAR" ]; then \\\n'
        build_start = "override_dh_auto_build:\n"
        for anchor in (built_copy, prebuilt_branch, build_start):
            self.assertEqual(without.count(anchor), 1, anchor)
        expected_failure = {
            "removed": "no longer checks carlos.war",
            "inside the build branch": "no longer checks carlos.war",
            "inside the build branch, unindented": "no longer checks carlos.war",
            "continuing the build branch": "check runs inside a shell block",
            "before the WAR exists": "check runs before the WAR is in place",
        }
        mutants = {
            "removed": without,
            # only the Maven-built WAR would be checked; a prebuilt CARLOS_WAR would not
            "inside the build branch": without.replace(
                built_copy,
                built_copy + "\t  python3 $(CURDIR)/debian/check-war-commons-logging.py "
                             "$(BUILDDIR)/carlos.war; \\\n"),
            "inside the build branch, unindented": without.replace(
                built_copy, built_copy + self.CHECK_LINE.replace("\n", "; \\\n")),
            # a line that looks like its own command but continues the build branch
            "continuing the build branch": without.replace(
                built_copy, built_copy + self.CHECK_LINE),
            # its own recipe line, but before either branch has put the WAR in place
            "before the WAR exists": without.replace(
                build_start, build_start + self.CHECK_LINE),
        }
        for label, mutant in mutants.items():
            with self.subTest(label):
                self.assertNotEqual(mutant, rules)
                with self.assertRaises(AssertionError) as caught:
                    self.assert_checks_packaged_war(mutant)
                self.assertIn(expected_failure[label], str(caught.exception))


if __name__ == "__main__":
    unittest.main()
