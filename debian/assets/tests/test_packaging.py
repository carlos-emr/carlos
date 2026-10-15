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
import re
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BACKUP = ROOT / "debian" / "assets" / "bin" / "carlos-emr-backup"
POSTRM = ROOT / "debian" / "carlos-emr.postrm"


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
    # the depth is part of the block's shape and has changed once (the
    # clinic's own oscar.properties sits one level down, inside the
    # extracted bundle), so it is matched rather than spelled
    m = re.search(r'o19-import" -maxdepth \d+ -type f(.*?)-exec', text,
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
        excluded = {e.rsplit("/", 1)[-1] for e in backup_excludes()}
        # No exemptions. `oscar.properties` sits inside the excluded
        # bundle/ today, so "the directory covers it" was true -- and it
        # left the two scripts free to disagree the moment that file is
        # written anywhere else the postrm's -maxdepth 2 find reaches.
        # The backup names it instead, at both depths.
        for name in names:
            self.assertIn(
                name, excluded,
                "the postrm shreds {0} from the import workspace because it "
                "holds credentials, but the nightly backup copies it into "
                "the restic repository, where the retention policy keeps it "
                "for up to a year".format(name))

    def test_the_extracted_bundle_is_excluded(self):
        # bundle/ is the clinic's whole database as plaintext SQL, their
        # documents tar and their oscar.properties. It exists from the
        # moment the bundle is opened until --cleanup, which spans the
        # mandatory pre-import snapshot, so without an exclusion every
        # import guarantees an unmanaged second copy of the entire
        # pre-migration record in the repository.
        excluded = {e.rsplit("/", 1)[-1] for e in backup_excludes()}
        for name in ("bundle", "bundle-assess"):
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

    """debian/carlos-ctl.pin is read by three hands: the release build
    (fetch-carlos-ctl.sh, the tag only), and the two CI jobs that check
    out the pinned CLI and, until that tag is published, its fallback
    commit. Its shape is a contract between them."""

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

    def test_a_fallback_names_one_full_commit(self):
        # a branch name would move under the job; a short id is ambiguous
        fallback = self._fields().get("fallback")
        if fallback is not None:
            self.assertRegex(fallback, r"^[0-9a-f]{40}$")
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


if __name__ == "__main__":
    unittest.main()
