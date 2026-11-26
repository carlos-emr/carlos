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

import importlib.util
import os
import re
import shutil
import subprocess
import sys
import tempfile
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

    """The asset build reads the tag and source tests require its exact commit.

    The installed CDS resource gate always requires the fallback commit, so
    it remains mandatory even after the tag is published.
    """

    PIN = ROOT / "debian" / "carlos-ctl.pin"

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

    def test_the_release_build_requires_the_published_asset(self):
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
    """Run both workflow checkout steps against a local Git repository."""

    WORKFLOWS = (ROOT / ".github/workflows/debian-python-tests.yml",
                 ROOT / ".github/workflows/script-regressions.yml")
    TAG = "1.1.0"

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.mirror = self.root / "mirror"
        self.mirror.mkdir()
        self.git("init", "--quiet", "--initial-branch", "main")
        self.git("config", "user.name", "CARLOS workflow fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        (self.mirror / "version.txt").write_text("original fixture revision\n", encoding="utf-8")
        self.git("add", "version.txt")
        self.git("commit", "--quiet", "-m", "First fixture revision")
        self.expected = self.git("rev-parse", "HEAD")
        (self.mirror / "version.txt").write_text("other fixture revision\n", encoding="utf-8")
        self.git("commit", "--quiet", "-am", "Other fixture revision")
        self.other = self.git("rev-parse", "HEAD")
        self.pin = self.root / "debian/carlos-ctl.pin"
        self.pin.parent.mkdir()
        self.pin.write_text(f"tag={self.TAG}\nfallback={self.expected}\n", encoding="utf-8")
        scripts = self.root / "scripts/ci"
        scripts.mkdir(parents=True)
        for name in ("carlos_ctl_pin.py", "git_environment.py", "run-pinned-carlos-ctl-suite.py"):
            shutil.copyfile(ROOT / "scripts/ci" / name, scripts / name)
        self.config = self.root / "git-config"
        self.use_mirror(self.mirror)

    def git(self, *args, source=None):
        return subprocess.check_output(["git", *args], cwd=source or self.mirror, text=True).strip()

    def use_mirror(self, mirror):
        self.config.write_text(
            f'[url "{mirror.as_uri()}"]\n\tinsteadOf = https://github.com/carlos-emr/carlos-ctl.git\n',
            encoding="utf-8")

    def run_checkout(self, workflow):
        text = workflow.read_text(encoding="utf-8")
        match = re.search(
            r"^      - name: Check out the pinned carlos-ctl release\n"
            r"        run: (.+)$", text, re.M)
        self.assertIsNotNone(match, workflow.name)
        destination = self.root / workflow.stem
        result = subprocess.run(
            ["bash", "-e", "-c", match.group(1)], cwd=self.root,
            env={**os.environ, "CARLOS_CTL_SRC": str(destination), "GIT_ALLOW_PROTOCOL": "file",
                 "GIT_CONFIG_GLOBAL": str(self.config), "GIT_CONFIG_NOSYSTEM": "1"},
            capture_output=True, text=True)
        return result, destination

    def test_published_annotated_tag_matches_the_pinned_commit(self):
        self.git("tag", "-a", self.TAG, "-m", "Fixture release", self.expected)
        for workflow in self.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, destination = self.run_checkout(workflow)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.git("rev-parse", "HEAD", source=destination), self.expected)
                self.assertIn(f"verified at {self.expected}", result.stdout)

    def test_published_prerelease_tag_matches_the_pinned_commit(self):
        tag = f"{self.TAG}-rc1"
        self.pin.write_text(f"tag={tag}\nfallback={self.expected}\n", encoding="utf-8")
        self.git("tag", tag, self.expected)
        result, destination = self.run_checkout(self.WORKFLOWS[0])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.git("rev-parse", "HEAD", source=destination), self.expected)

    def test_moved_tag_is_rejected_even_when_the_pin_remains_fetchable(self):
        self.git("tag", "-a", self.TAG, "-m", "Original fixture release", self.expected)
        self.git("tag", "-f", "-a", self.TAG, "-m", "Moved fixture release", self.other)
        for workflow in self.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, destination = self.run_checkout(workflow)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"is at {self.other}. carlos-ctl.pin requires {self.expected}", result.stderr)
                self.assertFalse(destination.exists())

    def test_missing_tag_uses_the_exact_commit_even_with_a_same_named_branch(self):
        self.git("branch", self.TAG, self.other)
        for workflow in self.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, destination = self.run_checkout(workflow)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.git("rev-parse", "HEAD", source=destination), self.expected)
                self.assertIn(f"::warning::carlos-ctl tag {self.TAG} is unpublished", result.stdout)

    def test_remote_failure_is_not_treated_as_an_unpublished_tag(self):
        self.use_mirror(self.root / "missing-mirror")
        for workflow in self.WORKFLOWS:
            with self.subTest(workflow=workflow.name):
                result, destination = self.run_checkout(workflow)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("ls-remote", result.stderr)
                self.assertNotIn("::warning::", result.stdout)
                self.assertFalse(destination.exists())

    def test_pin_requires_one_full_commit_even_when_tag_exists(self):
        self.git("tag", self.TAG, self.expected)
        for fallback in ("", "fallback=1234567\n", f"fallback={self.expected}\nfallback={self.other}\n"):
            self.pin.write_text(f"tag={self.TAG}\n{fallback}", encoding="utf-8")
            with self.subTest(fallback=fallback):
                result, destination = self.run_checkout(self.WORKFLOWS[0])
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("::error::", result.stderr)
                self.assertFalse(destination.exists())

    def test_unpublishable_release_tags_are_rejected_before_checkout(self):
        for workflow, suffix in zip(self.WORKFLOWS, ("rc..1", "rc"), strict=True):
            tag = f"{self.TAG}-{suffix}"
            with self.subTest(tag=tag):
                self.pin.write_text(f"tag={tag}\nfallback={self.expected}\n", encoding="utf-8")
                result, destination = self.run_checkout(workflow)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("must name a carlos-ctl release tag", result.stderr)
                self.assertFalse(destination.exists())

    def test_pinned_suite_rejects_a_moved_local_tag_even_at_the_pinned_head(self):
        self.git("tag", self.TAG, self.other)
        self.git("checkout", "--quiet", "--detach", self.expected)
        result = subprocess.run(
            [sys.executable, "scripts/ci/run-pinned-carlos-ctl-suite.py", "--output", "out.json"],
            cwd=self.root, env={**os.environ, "CARLOS_CTL_SRC": str(self.mirror)},
            capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f"is at {self.other}. carlos-ctl.pin requires {self.expected}", result.stderr)
        self.assertFalse((self.root / "out.json").exists())


class TestDebianPythonDependencyLock(unittest.TestCase):
    def test_workflow_requires_wheel_hashes_for_bcrypt(self):
        workflow = (ROOT / ".github/workflows/debian-python-tests.yml").read_text(encoding="utf-8")
        lock = ROOT / "debian/assets/tests/requirements.txt"
        self.assertIn(f"--require-hashes --only-binary=:all: -r {lock.relative_to(ROOT)}", workflow)
        self.assertRegex(lock.read_text(encoding="utf-8"),
                         r"(?m)^bcrypt==[0-9.]+ \\\n    --hash=sha256:[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
