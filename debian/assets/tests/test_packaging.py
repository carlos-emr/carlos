# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Agreements between the maintainer scripts and the backup script.

These two files are written by different hands at different times and
never import each other, so a rule stated in one can quietly stop being
true in the other. The one that matters most is the credential rule: the
postrm decides which files in the import workspace are secret enough to
shred on purge, and the nightly backup decides which files it copies
into a restic repository kept for up to a year. A file can be on both
lists, and for a while `o19-derived-carlos.properties` was -- shredded
as a credential store by one script, snapshotted for a year by the
other.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import os
import importlib.util
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BACKUP = ROOT / "debian" / "assets" / "bin" / "carlos-emr-backup"
POSTRM = ROOT / "debian" / "carlos-emr.postrm"


class TestCdsPublishedDocumentMode(unittest.TestCase):
    def test_installed_qualification_accepts_only_shared_group_readable_documents(self):
        spec = importlib.util.spec_from_file_location("packaged_cds_document_gate", ROOT / "scripts/ci/run-cds-performance.py")
        gate = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(gate)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            document = root / "owned-synthetic-report.pdf"
            document.write_bytes(b"synthetic packaging proof")
            document.chmod(0o600)
            with self.assertRaises(RuntimeError):
                gate.native_document_proof(document, root)
            document.chmod(0o640)
            self.assertEqual(gate.native_document_proof(document, root),
                             (document.stat().st_size, gate.digest(document)))


def backup_excludes():
    """Every `--exclude` argument the file-set `restic backup` passes,
    with the shell variables left as written."""
    text = BACKUP.read_text(encoding="utf-8")
    # the file-set invocation only: the db and binlog runs back up a
    # working directory this script made itself and exclude nothing
    block = text.split("restic backup --tag files", 1)
    if len(block) != 2:
        raise AssertionError(
            "no `restic backup --tag files` invocation in "
            "carlos-emr-backup -- this contract has lost its subject")
    block = block[1].split('"${targets[@]}"', 1)[0]
    return re.findall(r'--exclude "([^"]+)"', block)


def postrm_shredded_names():
    """The `-name` globs the postrm shreds out of the import workspace:
    the package's own statement of what in there is a credential."""
    text = POSTRM.read_text(encoding="utf-8")
    # Match recursive traversal and older depth-limited forms. Nested
    # backup layouts and immutable source snapshots require recursion.
    m = re.search(r'find(?:\s+-P)?\s+"\$\{STATE\}/o19-import"\s+(?:-maxdepth \d+\s+)?-type f(.*?)-exec', text,
                  re.S)
    if not m:
        raise AssertionError(
            "the postrm no longer shreds the import workspace by name -- "
            "this contract has lost its subject")
    return re.findall(r"-name '([^']+)'", m.group(1))


class TestSecretsAreNotBackedUp(unittest.TestCase):

    """Anything the postrm shreds as a credential must not be inside the
    restic file set, and the extracted bundle must not be either."""

    def test_every_file_the_postrm_shreds_is_excluded(self):
        names = postrm_shredded_names()
        self.assertTrue(names, "no shredded names parsed from the postrm")
        excluded = set(backup_excludes())
        # No exemptions. `oscar.properties` sits inside the excluded
        # bundle/ today, so "the directory covers it" was true -- and it
        # left the two scripts free to disagree the moment that file is
        # written anywhere else the postrm's recursive find reaches.
        # Require both root and nested exclusions for every credential name.
        for name in names:
            for directory in ("${O19_DIR}/", "${O19_DIR}/**/"):
                self.assertIn(
                    directory + name, excluded,
                    "the postrm shreds {0} from the import workspace because it "
                    "holds credentials, but the nightly backup copies it into "
                    "the restic repository, where the retention policy keeps it "
                    "for up to a year".format(directory + name))

    def test_the_extracted_bundle_is_excluded(self):
        # bundle/ is the clinic's whole database as plaintext SQL, their
        # documents tar and their oscar.properties. It exists from the
        # moment the bundle is opened until --cleanup, which spans the
        # mandatory pre-import snapshot, so without an exclusion every
        # import guarantees an unmanaged second copy of the entire
        # pre-migration record in the repository.
        excluded = {e.rsplit("/", 1)[-1] for e in backup_excludes()}
        for name in ("bundle", "bundle-assess", "bundle-sources", "bundle-assess-sources"):
            self.assertIn(
                name, excluded,
                "the extracted bundle directory {0}/ is not excluded from "
                "the nightly backup".format(name))

    def test_the_workspace_itself_is_still_backed_up(self):
        # the exclusions must not grow into "skip the whole workspace":
        # the CSV export of o19_archive and the run ledgers exist nowhere
        # else, and a restore without the ledgers cannot resume or clean
        # up the import
        text = BACKUP.read_text(encoding="utf-8")
        self.assertIn('targets+=("${O19_DIR}")', text)
        # against the PARSED list, not the file text: every --exclude
        # line ends in a ` \` continuation, so a literal match on
        # `--exclude "${O19_DIR}"` followed by a newline could never fire
        # and the guard passed no matter what was excluded
        self.assertNotIn("${O19_DIR}", backup_excludes())


class TestTheCarlosCtlPin(unittest.TestCase):

    """The release build reads the tag; CI reads the tag or exact fallback.

    The installed CDS resource gate always requires the fallback commit, so
    it remains mandatory even after the tag is published.
    """

    PIN = ROOT / "debian" / "carlos-ctl.pin"
    WORKFLOWS = (ROOT / ".github" / "workflows" / "debian-python-tests.yml",
                 ROOT / ".github" / "workflows" / "script-regressions.yml")

    def _fields(self):
        fields = {}
        for line in self.PIN.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                key, _, value = line.partition("=")
                fields[key] = value
        return fields

    def test_the_tag_is_a_debian_native_version(self):
        tag = self._fields().get("tag")
        self.assertRegex(tag or "", r"^\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?$")

    def test_fallback_names_one_full_commit(self):
        """Require the immutable CLI source commit used by installed CI."""
        # a branch name would move under the job; a short id is ambiguous
        fallback = self._fields().get("fallback")
        self.assertRegex(fallback or "", r"^[0-9a-f]{40}$")
        self.assertEqual(set(self._fields()) - {"tag", "fallback"}, set(),
                         "unknown key in debian/carlos-ctl.pin")

    def test_the_jobs_read_the_tag_and_the_fallback_the_same_way(self):
        for workflow in self.WORKFLOWS:
            text = workflow.read_text(encoding="utf-8")
            self.assertIn("sed -n 's/^tag=//p' debian/carlos-ctl.pin", text, workflow.name)
            self.assertIn("sed -n 's/^fallback=//p' debian/carlos-ctl.pin", text, workflow.name)
            self.assertIn('checkout --quiet "$fallback"', text, workflow.name)
        # the release build never falls back: a missing release is a failure
        fetch = (ROOT / "debian" / "fetch-carlos-ctl.sh").read_text(encoding="utf-8")
        self.assertNotIn("fallback", fetch)


class TestTheDrugrefPin(unittest.TestCase):

    """debian/drugref.pin names the DrugRef revision carlos-emr-drugref is
    built from when no DRUGREF_SRC or DRUGREF_WAR is given. debian/rules
    reads it with sed and debian/fetch-drugref.sh checks that revision out,
    so a branch name would ship different code under one package version
    and a short id can become ambiguous (#5006 moved it by hand)."""

    PIN = ROOT / "debian" / "drugref.pin"

    def _fields(self):
        fields = {}
        for line in self.PIN.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                key, _, value = line.partition("=")
                self.assertNotIn(key, fields, f"{key}= appears twice in debian/drugref.pin")
                fields[key] = value
        return fields

    def test_the_ref_names_one_full_commit(self):
        fields = self._fields()
        self.assertEqual(set(fields), {"ref"}, "debian/drugref.pin must hold exactly one ref= line")
        self.assertRegex(fields["ref"], r"^[0-9a-f]{40}$")

    def test_the_build_reads_the_ref_the_way_the_pin_writes_it(self):
        rules = (ROOT / "debian" / "rules").read_text(encoding="utf-8")
        self.assertIn("DRUGREF_REF ?= $(shell sed -n 's/^ref=//p' debian/drugref.pin)", rules)
        self.assertIn('debian/fetch-drugref.sh "$(DRUGREF_REF)"', rules)


class TestPinnedCliWorkflowCheckout(unittest.TestCase):
    """Run both workflow checkout steps without a network or GitHub runner."""

    def run_checkout(self, workflow, published, fallback):
        text = workflow.read_text(encoding="utf-8")
        match = re.search(
            r"      - name: Check out the pinned carlos-ctl release\n"
            r"        run: \|\n((?:          .*\n|\n)+)", text)
        self.assertIsNotNone(match, workflow.name)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "debian").mkdir()
            (root / "debian/carlos-ctl.pin").write_text(
                "tag=1.1.0\n" + ("fallback=" + fallback + "\n" if fallback else ""))
            stub = root / "git"
            stub.write_text("""#!/bin/bash
printf '%s\\n' "$*" >> "$GIT_CALLS"
if [[ "$*" == *'--branch'* && "$PUBLISHED" != 1 ]]; then exit 1; fi
if [[ "$*" == *'rev-parse HEAD'* ]]; then echo fixture-commit; fi
""")
            stub.chmod(0o755)
            calls = root / "calls"
            result = subprocess.run(
                ["bash", "-c", textwrap.dedent(match.group(1))], cwd=root,
                env={**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                     "CARLOS_CTL_SRC": str(root / "ctl"), "GIT_CALLS": str(calls),
                     "PUBLISHED": "1" if published else "0"},
                capture_output=True, text=True)
            return result, calls.read_text()

    def test_published_tag_is_preferred_to_fallback(self):
        for workflow in TestTheCarlosCtlPin.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, calls = self.run_checkout(workflow, True, "a" * 40)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("--branch 1.1.0", calls)
                self.assertNotIn("checkout --quiet", calls)

    def test_missing_tag_uses_exact_fallback_and_warns(self):
        for workflow in TestTheCarlosCtlPin.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, calls = self.run_checkout(workflow, False, "a" * 40)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("checkout --quiet " + "a" * 40, calls)
                self.assertIn("::warning::", result.stdout)

    def test_missing_tag_and_fallback_fail_instead_of_skipping_tests(self):
        for workflow in TestTheCarlosCtlPin.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, calls = self.run_checkout(workflow, False, None)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("::error::", result.stdout)
                self.assertNotIn("checkout --quiet", calls)


class TestPostinstLintianBashismOverrides(unittest.TestCase):
    """#6336: each possible-bashism override names text the postinst still has.

    checkbashisms misreads embedded Python method calls as shell function
    declarations. A stale override (for code since removed) or a wildcard one
    silently widens the suppression, and lintian only runs in the package
    workflows, so the pairing is pinned here.
    """

    OVERRIDES = ROOT / "debian" / "carlos-emr.lintian-overrides"
    POSTINST = ROOT / "debian" / "carlos-emr.postinst"
    TAG = "possible-bashism-in-maintainer-script"
    NGINX_PATTERNS = (
        "link.lstat()", "link.unlink()", "query.stdout.splitlines()",
        "usedforsecurity=False).hexdigest()",
    )

    def bashism_overrides(self):
        found = []
        for number, line in enumerate(self.OVERRIDES.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("#") or self.TAG not in line:
                continue
            match = re.fullmatch(self.TAG + r" '([^']*)' \[([^\]]+)\]", line.strip())
            self.assertIsNotNone(match, f"line {number} is not '<tag> '<pattern>' [<scope>]': {line!r}")
            found.append((number, match.group(1), match.group(2)))
        return found

    def nginx_heredoc(self):
        text = self.POSTINST.read_text(encoding="utf-8")
        match = re.search(r"<<'NGINX_DEFAULT_SITE'\n(.*?)\nNGINX_DEFAULT_SITE\n", text, re.S)
        self.assertIsNotNone(match, "quoted NGINX_DEFAULT_SITE heredoc is missing from the postinst")
        return match.group(1)

    def test_every_override_pattern_occurs_literally_in_the_postinst(self):
        postinst = self.POSTINST.read_text(encoding="utf-8")
        overrides = self.bashism_overrides()
        self.assertTrue(overrides)
        for number, pattern, _scope in overrides:
            with self.subTest(line=number, pattern=pattern):
                self.assertIn(pattern, postinst)

    def test_overrides_are_scoped_to_the_postinst_and_never_bare_wildcards(self):
        for number, pattern, scope in self.bashism_overrides():
            with self.subTest(line=number, pattern=pattern):
                self.assertEqual(scope, "postinst:*")
                self.assertGreaterEqual(len(pattern.strip("*? ")), 8)
                self.assertNotIn("*", pattern.replace("sys.stdin.read().strip()", ""))

    def test_nginx_default_site_overrides_are_exactly_the_four_python_calls(self):
        patterns = [pattern for _n, pattern, _s in self.bashism_overrides()]
        heredoc = self.nginx_heredoc()
        for pattern in self.NGINX_PATTERNS:
            with self.subTest(pattern=pattern):
                self.assertEqual(patterns.count(pattern), 1)
                self.assertIn(pattern, heredoc)
        self.assertNotIn("configuration.flush()", patterns)
        self.assertEqual(sorted(patterns),
                         sorted(list(self.NGINX_PATTERNS) + ["sys.stdin.read().strip()"]))

    @unittest.skipUnless(shutil.which("checkbashisms"), "checkbashisms is not installed")
    def test_checkbashisms_reports_nothing_an_override_does_not_cover(self):
        run = subprocess.run(["checkbashisms", str(self.POSTINST)], capture_output=True, text=True)
        patterns = [pattern for _n, pattern, _s in self.bashism_overrides()]
        uncovered = [line for line in run.stderr.splitlines()
                     if line.startswith("possible bashism") is False and line.strip()
                     and not any(pattern in line for pattern in patterns)
                     and not line.startswith(("script ", "error:"))]
        self.assertEqual(uncovered, [], run.stderr)


class TestRulesShipTheConfigurationPreflight(unittest.TestCase):
    """#6133 / #6336: debian/rules builds and installs both preflight artifacts.

    The service unit runs the root wrapper before start and the JVM loads the
    jar as an agent, so a package without either fails to start; only the
    installed lifecycle lanes would otherwise notice a wrong rules line.
    """

    RULES = ROOT / "debian" / "rules"

    def rules(self):
        return self.RULES.read_text(encoding="utf-8")

    def test_build_target_runs_the_preflight_jar_script_that_exists(self):
        text = self.rules()
        target = re.search(r"^override_dh_auto_build:.*?(?=^\S)", text, re.S | re.M)
        self.assertIsNotNone(target)
        self.assertIn("sh $(CURDIR)/debian/build-configuration-preflight.sh $(BUILDDIR)", target.group(0))
        self.assertTrue((ROOT / "debian" / "build-configuration-preflight.sh").is_file())

    def test_wrapper_is_installed_executable_from_a_source_that_exists(self):
        self.assertIn("install -m 0755 debian/assets/bin/carlos-emr-config-preflight $(STAGE)/$(LIBDIR)/",
                      self.rules())
        self.assertTrue((ROOT / "debian" / "assets" / "bin" / "carlos-emr-config-preflight").is_file())

    def test_jar_is_installed_non_executable_under_the_shared_lib_directory(self):
        self.assertIn(
            "install -m 0644 $(BUILDDIR)/carlos-configuration-preflight.jar $(STAGE)/$(SHAREDIR)/lib/",
            self.rules())
        script = (ROOT / "debian" / "build-configuration-preflight.sh").read_text(encoding="utf-8")
        self.assertIn("carlos-configuration-preflight.jar", script)


if __name__ == "__main__":
    unittest.main()
