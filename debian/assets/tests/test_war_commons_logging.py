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
        return subprocess.run(
            [sys.executable, str(CHECK), *map(str, args)],
            capture_output=True, text=True, check=False)

    def assert_refused(self, result, *fragments):
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


class RulesRunsCommonsLoggingCheckTest(unittest.TestCase):
    """debian/rules must check the WAR it packages, built or prebuilt."""

    def test_rules_checks_the_packaged_war(self):
        rules = RULES.read_text(encoding="utf-8")
        build = rules.split("override_dh_auto_build:", 1)[1].split("\noverride_", 1)[0]
        check = re.search(
            r"^\tpython3 \$\(CURDIR\)/debian/check-war-commons-logging\.py "
            r"\$\(BUILDDIR\)/carlos\.war$", build, re.M)
        self.assertIsNotNone(check, "override_dh_auto_build no longer checks carlos.war")
        # after both branches have put the WAR in place, before it is unpacked
        war_ready = build.index("cp -f target/carlos-*.war $(BUILDDIR)/carlos.war")
        self.assertGreater(check.start(), war_ready)


if __name__ == "__main__":
    unittest.main()
