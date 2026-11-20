# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""While a properties recovery is deferred, the postinst writes nothing to carlos.properties.

An interrupted in-place rewrite of /etc/carlos-emr/carlos.properties leaves a recovery record; when an OSCAR 19
import holds provisioning the configure defers the recovery and ends successfully
(PROPERTIES_RECOVERY_PENDING=1). The file may then be torn, and any further write (a ``sed -i`` replaces its inode,
a ``carlos-ctl init-config`` renders it anew, the cutover preflight appends to it) would make the later recovery
refuse for good.

This is a structural contract over the shipped postinst: every statement that writes the file, or runs a carlos-ctl
step that does, must sit in an ENCLOSING ``if`` branch whose own condition allows it, that is
``PROVISION_LOCK_HELD = 1`` (forced to 0 while the recovery is pending) or ``PROPERTIES_RECOVERY_PENDING = 0``, or
in a later branch of a chain whose earlier branch tested the opposite (``PROVISION_LOCK_HELD = 0``). Comments and
here-document bodies are ignored, so a gate mentioned in a comment or a gate on a sibling block counts for nothing.
A new writer without one fails here. ``violations`` is also run over small synthetic postinst snippets that must be
rejected (the mutations an earlier, window-based version of this check let through) and accepted.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"

# The condition of the branch that holds the statement allows writing.
ALLOWS = re.compile(r'PROVISION_LOCK_HELD\}" = 1|PROPERTIES_RECOVERY_PENDING:-0\}" = 0')
# An earlier branch of the same if/elif chain tested the opposite, so a later branch runs with the lock held.
EARLIER_DENIES = re.compile(r'PROVISION_LOCK_HELD\}" = 0|PROPERTIES_RECOVERY_PENDING:-0\}" = 1')

WRITERS = [
    ("sed -i on carlos.properties", re.compile(r'\bsed -i\b[^\n]*"\$\{PROPERTIES\}"')),
    ("the in-place rewrite helper with a line to change",
     re.compile(r'migrate_lab_filter "\$\{PROPERTIES\}" "[^"]')),
    ("carlos-ctl init-config", re.compile(r'(?:^|\bif !? ?|&& ?!? ?)carlos-ctl init-config\b')),
    ("carlos-ctl db-users", re.compile(r'(?:^|\bif !? ?|&& ?!? ?)carlos-ctl db-users\b')),
    ("carlos-ctl db-apply-settings", re.compile(r'(?:^|\bif !? ?|&& ?!? ?)carlos-ctl db-apply-settings\b')),
    ("the cutover preflight", re.compile(r'config-preflight reconcile')),
]


def statements(text):
    """(line number, statement): comments, blank lines and here-document bodies dropped, a line continued by a
    backslash joined to the next."""
    lines = text.splitlines()
    out, i, heredoc_end = [], 0, None
    while i < len(lines):
        start, stmt = i + 1, lines[i]
        if heredoc_end is not None:
            if stmt.strip() == heredoc_end:
                heredoc_end = None
            i += 1
            continue
        while stmt.endswith("\\") and i + 1 < len(lines):
            i += 1
            stmt = stmt[:-1] + " " + lines[i].strip()
        i += 1
        stmt = stmt.strip()
        if not stmt or stmt.startswith("#"):
            continue
        marker = re.search(r"<<-?\s*'?\"?([A-Za-z_]+)'?\"?", stmt)
        if marker:
            heredoc_end = marker.group(1)
        out.append((start, stmt))
    return out


def violations(text):
    """(line, writer label) of every writer that no enclosing branch allows."""
    found, chains = [], []          # a chain: [list of branch conditions, index of the current branch]
    for number, stmt in statements(text):
        one_line = stmt.startswith("if ") and re.search(r";\s*fi\s*$", stmt) is not None
        if stmt.startswith("if ") and not one_line:
            chains.append([[stmt], 0])
        elif stmt.startswith("elif "):
            chains[-1][0].append(stmt)
            chains[-1][1] += 1
        elif stmt == "else" or stmt.startswith("else "):
            chains[-1][0].append("")
            chains[-1][1] += 1
        for label, pattern in WRITERS:
            if not pattern.search(stmt):
                continue
            scope = chains + ([[[stmt], 0]] if one_line else [])
            allowed = any(ALLOWS.search(conds[branch]) or any(EARLIER_DENIES.search(c) for c in conds[:branch])
                          for conds, branch in scope)
            if not allowed:
                found.append((number, label))
        if stmt == "fi" or stmt.startswith("fi ") or stmt.startswith("fi;"):
            chains.pop()
    return found


class TestPropertiesRecoveryContract(unittest.TestCase):

    def test_every_writer_of_the_properties_is_behind_an_enclosing_gate(self):
        text = POSTINST.read_text(encoding="utf-8")
        self.assertEqual(violations(text), [], "a carlos.properties writer can run while a recovery is deferred: put "
                                               "it in a branch gated by PROVISION_LOCK_HELD = 1 or "
                                               "PROPERTIES_RECOVERY_PENDING = 0")
        subjects = [number for number, stmt in statements(text) for _, p in WRITERS if p.search(stmt)]
        self.assertGreaterEqual(len(subjects), 12, "the contract has lost its subjects")

    def test_the_deferral_forces_the_lock_to_not_held_for_everything_after_it(self):
        text = POSTINST.read_text(encoding="utf-8")
        self.assertRegex(text, r'if \[ "\$\{PROPERTIES_RECOVERY_PENDING:-0\}" = 1 \]; then\n\s+PROVISION_LOCK_HELD=0\n'
                               r'\s+O19_IMPORT_HOLDS_LOCK=1\n\s+fi')
        # The flag is initialised before the recovery that may set it, and that comes before the lock is forced.
        self.assertLess(text.index("PROPERTIES_RECOVERY_PENDING=0"), text.index("PROPERTIES_RECOVERY_PENDING=1"))
        self.assertLess(text.index("PROPERTIES_RECOVERY_PENDING=1"),
                        text.index('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 1 ]; then\n            PROVISION_LOCK_HELD=0'))

    def test_the_messages_after_a_deferral_name_dpkg_reconfigure(self):
        text = POSTINST.read_text(encoding="utf-8")
        self.assertIn("once the OSCAR 19 import has finished, run: sudo dpkg-reconfigure carlos-emr", text)
        self.assertIn("a pending recovery, see above", text)


class TestTheCheckerItself(unittest.TestCase):
    """Synthetic snippets: the mutations a window-based check let through must be rejected."""

    def rejected(self, snippet):
        self.assertTrue(violations(snippet), snippet)

    def accepted(self, snippet):
        self.assertEqual(violations(snippet), [], snippet)

    def test_a_fax_normaliser_without_the_flag_is_rejected(self):
        self.rejected('if [ -f "${PROPERTIES}" ] && grep -Eqi \'^x\' \\\n    "${PROPERTIES}"; then\n'
                      '    sed -i -E \\\n        -e \'s/a/b/\' \\\n        "${PROPERTIES}"\nfi\n')

    def test_a_health_tracker_block_without_the_flag_is_rejected(self):
        self.rejected('if [ ! -e "${SENTINEL}" ]; then\n'
                      '    if [ -n "${2:-}" ] && [ -f "${PROPERTIES}" ]; then\n'
                      "        sed -i 's/a/b/' \"${PROPERTIES}\"\n    fi\nfi\n")

    def test_an_unguarded_sed_after_the_forcing_block_is_rejected(self):
        self.rejected('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 1 ]; then\n    PROVISION_LOCK_HELD=0\nfi\n'
                      "sed -i 's/a/b/' \"${PROPERTIES}\"\n")

    def test_a_gate_only_in_a_comment_counts_for_nothing(self):
        self.rejected('# PROPERTIES_RECOVERY_PENDING:-0}" = 0 and PROVISION_LOCK_HELD}" = 1 are checked here\n'
                      "sed -i 's/a/b/' \"${PROPERTIES}\"\n")

    def test_a_gate_on_a_sibling_block_counts_for_nothing(self):
        self.rejected('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 0 ]; then\n    echo hi\nfi\n'
                      "sed -i 's/a/b/' \"${PROPERTIES}\"\n")

    def test_the_deferral_direction_is_not_a_gate(self):
        self.rejected('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 1 ]; then\n    sed -i \'s/a/b/\' "${PROPERTIES}"\nfi\n')

    def test_an_unguarded_carlos_ctl_step_is_rejected(self):
        for step in ("init-config", "db-users", "db-apply-settings"):
            with self.subTest(step=step):
                self.rejected(f"carlos-ctl {step} || true\n")
                self.rejected(f"if ! carlos-ctl {step}; then\n    echo failed\nfi\n")

    def test_the_cutover_preflight_without_the_flag_is_rejected(self):
        self.rejected("if ! /usr/lib/carlos-emr/carlos-emr-config-preflight reconcile; then\n    exit 1\nfi\n")

    def test_a_writer_in_the_then_branch_of_a_lock_not_held_test_is_rejected(self):
        self.rejected('if [ "${PROVISION_LOCK_HELD}" = 0 ]; then\n    carlos-ctl init-config\nfi\n')

    def test_a_here_document_body_is_not_code(self):
        self.accepted("cat <<'EOF'\nsed -i 's/a/b/' \"${PROPERTIES}\"\nEOF\n")

    def test_guarded_writers_are_accepted(self):
        self.accepted('if [ "${PROVISION_LOCK_HELD}" = 1 ] && [ ! -e "${S}" ] && '
                      '[ "${PROPERTIES_RECOVERY_PENDING:-0}" = 0 ]; then\n'
                      '    if [ -f "${PROPERTIES}" ]; then\n        sed -i \'s/a/b/\' "${PROPERTIES}"\n    fi\nfi\n')
        self.accepted('if [ "${PROPERTIES_RECOVERY_PENDING:-0}" = 0 ] \\\n    && ! carlos-emr-config-preflight reconcile; then\n'
                      '    exit 1\nfi\n')
        self.accepted('if [ "${PROVISION_LOCK_HELD}" = 1 ]; then\n    carlos-ctl init-config || {\n        echo x\n    }\nfi\n')

    def test_a_later_branch_of_a_lock_not_held_chain_is_accepted(self):
        self.accepted('if [ "${PROVISION_LOCK_HELD}" = 0 ]; then\n    MIGRATION_OK=0\n'
                      'elif o19_import_in_progress; then\n    echo no\n'
                      'elif mariadb -e x; then\n    carlos-ctl db-users\nelse\n    carlos-ctl init-config\nfi\n')


if __name__ == "__main__":
    unittest.main()
