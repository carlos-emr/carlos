# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""While a properties recovery is deferred, the postinst writes nothing to carlos.properties.

An interrupted in-place rewrite of /etc/carlos-emr/carlos.properties leaves a recovery record; when an OSCAR 19
import holds provisioning the configure defers the recovery and ends successfully
(PROPERTIES_RECOVERY_PENDING=1). The file may then be torn, and any further write (a ``sed -i`` replaces its inode,
a ``carlos-ctl init-config`` renders it anew, the cutover preflight appends to it) would make the later recovery
refuse for good. This is a grep-level contract over the shipped postinst: every statement that writes the file, or
runs a carlos-ctl step that does, sits behind ``PROVISION_LOCK_HELD = 1`` (forced to 0 while the recovery is
pending) or checks ``PROPERTIES_RECOVERY_PENDING`` itself. A new writer without one of the two fails here.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"

# "= 0" is the head of an if/elif chain whose later branches run only with the lock held (the database steps).
GATE = re.compile(r'PROPERTIES_RECOVERY_PENDING|PROVISION_LOCK_HELD\}" = [01]')
# How far back a gating condition may sit from the statement it guards: the nested ifs of one migration block, or
# the if/elif chain of the provisioning steps.
WINDOW = 90

WRITERS = [
    ("sed -i on carlos.properties", re.compile(r'\bsed -i\b[^\n]*"\$\{PROPERTIES\}"')),
    ("the in-place rewrite helper with a line to change",
     re.compile(r'migrate_lab_filter "\$\{PROPERTIES\}" "[^"]')),
    ("carlos-ctl init-config", re.compile(r'^\s*(?:if ! )?carlos-ctl init-config\b')),
    ("carlos-ctl db-users", re.compile(r'^\s*(?:if ! )?carlos-ctl db-users\b')),
    ("carlos-ctl db-apply-settings", re.compile(r'^\s*carlos-ctl db-apply-settings\b')),
    ("the cutover preflight", re.compile(r'config-preflight reconcile')),
]


def statements():
    """(line number, statement) of the postinst, a line continued by a backslash joined to its end."""
    lines = POSTINST.read_text(encoding="utf-8").splitlines()
    out, i = [], 0
    while i < len(lines):
        start, text = i + 1, lines[i]
        while text.endswith("\\") and i + 1 < len(lines):
            i += 1
            text = text[:-1] + " " + lines[i].strip()
        out.append((start, text))
        i += 1
    return out


class TestPropertiesRecoveryContract(unittest.TestCase):

    def test_every_writer_of_the_properties_is_behind_the_lock_or_the_pending_flag(self):
        lines = POSTINST.read_text(encoding="utf-8").splitlines()
        found = 0
        for number, text in statements():
            if text.lstrip().startswith("#"):
                continue
            for label, pattern in WRITERS:
                if not pattern.search(text):
                    continue
                found += 1
                window = "\n".join(lines[max(0, number - 1 - WINDOW):number - 1]) + "\n" + text
                with self.subTest(line=number, writer=label):
                    self.assertRegex(window, GATE,
                                     f"postinst line {number} ({label}) can write carlos.properties while a recovery "
                                     "is deferred: put it behind PROVISION_LOCK_HELD = 1 or check "
                                     "PROPERTIES_RECOVERY_PENDING")
        self.assertGreaterEqual(found, 10, "the contract has lost its subjects")

    def test_the_deferral_forces_the_lock_to_not_held_for_everything_after_it(self):
        text = POSTINST.read_text(encoding="utf-8")
        self.assertRegex(text, r'if \[ "\$\{PROPERTIES_RECOVERY_PENDING:-0\}" = 1 \]; then\n\s+PROVISION_LOCK_HELD=0\n'
                               r'\s+O19_IMPORT_HOLDS_LOCK=1\n\s+fi')
        # The flag is initialised before the recovery that may set it, and that comes before the lock is taken.
        self.assertLess(text.index("PROPERTIES_RECOVERY_PENDING=0"), text.index("PROPERTIES_RECOVERY_PENDING=1"))
        self.assertLess(text.index("PROPERTIES_RECOVERY_PENDING=1"),
                        text.index('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 1 ]; then\n            PROVISION_LOCK_HELD=0'))

    def test_the_cutover_preflight_is_skipped_while_the_recovery_is_pending(self):
        text = POSTINST.read_text(encoding="utf-8")
        self.assertIn('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 0 ] \\\n'
                      '            && ! /usr/lib/carlos-emr/carlos-emr-config-preflight reconcile; then', text)


if __name__ == "__main__":
    unittest.main()
