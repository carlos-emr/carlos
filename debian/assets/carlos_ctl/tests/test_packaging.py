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

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
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
    block = text.split("o19-import\" -maxdepth 1 -type f", 1)
    if len(block) < 2:
        raise AssertionError(
            "the postrm no longer shreds the import workspace by name -- "
            "this contract has lost its subject")
    return re.findall(r"-name '([^']+)'", block[1].split("-exec", 1)[0])


class TestSecretsAreNotBackedUp(unittest.TestCase):

    """Anything the postrm shreds as a credential must not be inside the
    restic file set, and the extracted bundle must not be either."""

    def test_every_file_the_postrm_shreds_is_excluded(self):
        names = postrm_shredded_names()
        self.assertTrue(names, "no shredded names parsed from the postrm")
        excluded = {e.rsplit("/", 1)[-1] for e in backup_excludes()}
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
        self.assertNotIn('--exclude "${O19_DIR}"\n', text)


if __name__ == "__main__":
    unittest.main()
