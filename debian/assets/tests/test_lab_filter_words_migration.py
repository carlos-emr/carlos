# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The postinst's one-time rewrite of the #4613 stock HL7_LAB_MEASUREMENT_FILTER (#6121).

The #4613 stock filter matched each of its words at the START of a result
(``^\\\\b(word)\\\\b`` run with find()), so Hl7textResultsData filed "No organisms
seen" as "No", "N/A" as "N" and "A positive" as "A", and its dates anywhere, so
"Negative 2026-10-09" was filed as the date. The stock line now accepts a word
or a date only as the whole result (``^(word)$``, ``^date$``). install_once copied the #4613
line into /etc/carlos-emr/carlos.properties, the override that wins over the
WAR default, so the postinst rewrites that exact line once.

The block is cut out of the real postinst and run under ``sh`` against a
fixture file, so these tests exercise the shipped shell, not a copy of it.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import errno
import fcntl
import os
import re
import stat
import struct
import subprocess
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from test_lab_filter_migration import locked_rewrite_function

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"
POSTRM = ROOT / "debian" / "carlos-emr.postrm"
SHIPPED = ROOT / "src" / "main" / "resources" / "carlos.properties"
SENTINEL = ".lab-measurement-filter-words-migrated"
KEY = "HL7_LAB_MEASUREMENT_FILTER="


def migration_block(package_retry=False):
    """The postinst's whole-word block, from its sentinel to its closing fi."""
    text = POSTINST.read_text(encoding="utf-8")
    m = re.search(
        r'^        LAB_FILTER_WORDS_SENTINEL=.*?^        fi\n', text, re.S | re.M)
    if not m:
        raise AssertionError(
            "no LAB_FILTER_WORDS_SENTINEL block in carlos-emr.postinst -- this "
            "contract has lost its subject")
    startup = ""
    if package_retry:
        recovery = re.search(r'^        # Recover a stopped lab-filter write.*?^        fi\n', text, re.S | re.M)
        normalizer = re.search(r'^        # Normalise the legacy yes/no spellings.*?^        fi\n', text, re.S | re.M)
        if not recovery or not normalizer or recovery.end() >= normalizer.start():
            raise AssertionError("lab-filter recovery must precede the other configuration migrations")
        startup = ('acquire_provision_lock() { [ "${PROVISION_LOCK_HELD}" = 1 ]; }\n'
                   + recovery.group(0) + normalizer.group(0))
    return locked_rewrite_function(text) + startup + m.group(0)


def old_line():
    m = re.search(r"^\s*LAB_FILTER_WORDS_OLD='([^']*)'$", migration_block(), re.M)
    if not m:
        raise AssertionError("the block does not state LAB_FILTER_WORDS_OLD")
    return m.group(1)


def shipped_line():
    lines = [line for line in SHIPPED.read_text(encoding="utf-8").splitlines()
             if line.startswith(KEY)]
    if len(lines) != 1:
        raise AssertionError(
            "carlos.properties defines HL7_LAB_MEASUREMENT_FILTER "
            "{0} times, not once".format(len(lines)))
    return lines[0]


def alternatives(line):
    """The top-level alternatives of a filter line, as written in the file."""
    out, depth, current = [], 0, ""
    for ch in line[len(KEY):]:
        depth += ch == "("
        depth -= ch == ")"
        if ch == "|" and depth == 0:
            out.append(current)
            current = ""
        else:
            current += ch
    return out + [current]


class TestLabFilterWordsMigration(unittest.TestCase):

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

    def run_block(self, lock_held="1", *, extra_env=None, package_retry=False):
        env = dict(os.environ, PROVISION_LOCK_HELD=lock_held,
                   STATE=str(self.state), SHARE=str(self.share),
                   PROPERTIES=str(self.properties))
        if extra_env:
            env.update(extra_env)
        return subprocess.run(["sh", "-c", "set -e\n" + migration_block(package_retry)],
                              env=env, capture_output=True, text=True,
                              check=False, timeout=10)

    def write_properties(self, *lines):
        self.properties.write_text("".join(l + "\n" for l in lines),
                                   encoding="utf-8")
        self.properties.chmod(0o640)

    def test_shipped_line_is_the_4613_line_with_whole_value_words_and_dates(self):
        old, new = alternatives(old_line()), alternatives(shipped_line())
        self.assertEqual(len(old), len(new))
        # The two date alternatives must be the whole result; the leading number skips a result that
        # starts with a 19xx or 20xx date and goes on, and is otherwise unchanged. Each date form
        # uses the same separator twice, so a number such as "6.5 2026-10-09" is still filed as 6.5.
        self.assertEqual(new[:2], ["^" + date + "$" for date in old[:2]])
        year, part, dot = "(?:19|20)[0-9]{2}", "[0-9]{1,2}", "\\\\."
        sep = "(?:-" + part + "-|/" + part + "/|" + dot + part + dot + ")"
        month = ("(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?"
                 "|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)")
        skip = ("(?!" + year + sep + part + "[^0-9]"
                "|" + part + sep + year + "[^0-9]"
                "|" + year + " " + part + " " + part + "(?:[^0-9]|$)"
                "|" + part + " " + part + " " + year + "(?:[^0-9]|$)"
                "|" + part + "[-/. ]" + month + "[-/. ]" + year + ")")
        self.assertTrue(old[2].startswith("^"), old[2])
        self.assertEqual(new[2], "^" + skip + old[2][1:])
        for before, after in zip(old[3:], new[3:]):
            with self.subTest(word=before):
                self.assertTrue(before.startswith("^\\\\b("), before)
                body = before[len("^\\\\b"):]
                if body.endswith(")\\\\b"):
                    body = body[:-len("\\\\b")]
                self.assertEqual(after, "^" + body + "$")
        self.assertIn("|^\\\\b(no)\\\\b|", old_line())
        self.assertNotIn("^\\\\b(", shipped_line())

    def test_rewrites_the_4613_stock_line_to_the_skeleton_line(self):
        self.write_properties("before=1", old_line(), "after=2")

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         "before=1\n{0}\nafter=2\n".format(shipped_line()))
        self.assertIn("updated the stock HL7_LAB_MEASUREMENT_FILTER", result.stdout)
        self.assertIn("#6121", result.stdout)
        self.assertNotIn("WARNING", result.stderr)
        self.assertEqual(stat.S_IMODE(self.properties.stat().st_mode), 0o640)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")

    def test_preserves_access_acl_and_extended_attributes_on_the_rewritten_file(self):
        self.write_properties(old_line())
        # Linux ACL format 2. The owning group gets no permissions, while a named user has read access.
        acl = struct.pack("<I", 2) + b"".join(struct.pack("<HHI", *entry) for entry in (
            (1, 6, 0xffffffff), (2, 4, os.getuid()), (4, 0, 0xffffffff),
            (16, 4, 0xffffffff), (32, 0, 0xffffffff),
        ))
        try:
            os.setxattr(self.properties, "system.posix_acl_access", acl)
            os.setxattr(self.properties, "user.carlos-fixture", b"keep this metadata")
        except OSError as failure:
            if failure.errno == errno.ENOTSUP:
                self.skipTest("the temporary filesystem does not support file ACLs and extended attributes")
            raise

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"), shipped_line() + "\n")
        self.assertEqual(os.getxattr(self.properties, "system.posix_acl_access"), acl)
        self.assertEqual(os.getxattr(self.properties, "user.carlos-fixture"), b"keep this metadata")


    def test_leaves_a_key_the_operator_defined_twice(self):
        self.write_properties(old_line(), KEY + ".*")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertNotIn("WARNING", result.stderr)

    def test_leaves_a_value_the_operator_chose(self):
        self.write_properties("before=1", KEY + "^\\\\d+")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertNotIn("WARNING", result.stderr)

    def test_warns_when_an_edited_copy_still_matches_word_starts(self):
        # Trailing blanks make it the operator's line, not the stock one: it is
        # left alone and named instead.
        self.write_properties(old_line() + "  ")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("contains an alternative that starts", result.stderr)
        self.assertIn("#6121", result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_warns_when_an_edited_line_keeps_any_word_start_alternative(self):
        # A clinic's own line without the stock "no" alternative still files
        # "Negative for malignancy" as "Negative": any ^\\b(word) is named.
        self.write_properties(KEY + "^\\\\b(negative)\\\\b|^[0-9.]+")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("contains an alternative that starts", result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_describes_the_detected_syntax_without_claiming_what_the_filter_does(self):
        # An effective Java pattern ^\\b(no)\\b$ matches "No" and REJECTS "No organisms seen": the
        # prefix check cannot tell it from the stock ^\\b(no)\\b, so the warning must only say that
        # the syntax is there and ask the operator to inspect it, never that a result is filed as a word.
        self.write_properties(KEY + "^\\\\b(no)\\\\b$")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("contains an alternative that starts", result.stderr)
        self.assertIn("inspect it", result.stderr)
        self.assertIn("Whether yours does depends on how the whole expression ends", result.stderr)
        for claim in ('files "No organisms seen"', 'as "No"', "files lab results", "may match dates anywhere"):
            self.assertNotIn(claim, result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_warns_about_a_word_alternative_on_a_continued_property(self):
        self.write_properties("before=1", "HL7_LAB_MEASUREMENT_FILTER=^([0-9]+)$|\\",
                              r"    ^\\b(negative)\\b", "after=2")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("contains an alternative that starts", result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_keeps_a_stock_looking_physical_line_inside_another_property(self):
        self.write_properties("other=prefix\\", old_line(), "HL7_LAB_MEASUREMENT_\\",
                              "  FILTER=" + old_line()[len(KEY):], "after=kept")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertIn("contains an alternative that starts", result.stderr)
        self.assertTrue((self.state / SENTINEL).exists())

    def test_restores_the_inode_contents_when_sync_fails(self):
        fixture = Path(self.tmp.name) / "python-fixture"
        fixture.mkdir()
        (fixture / "sitecustomize.py").write_text(
            "import os\n"
            "configuration = os.stat(os.environ['PROPERTIES'])\n"
            "native_fsync = os.fsync\n"
            "def fail_once(fd):\n"
            "    if os.path.samestat(configuration, os.fstat(fd)):\n"
            "        os.fsync = native_fsync\n"
            "        raise OSError('synthetic sync failure')\n"
            "    return native_fsync(fd)\n"
            "os.fsync = fail_once\n",
            encoding="utf-8",
        )
        self.write_properties("before=1", old_line())
        before = self.properties.read_bytes()
        opened = self.properties.stat()

        result = self.run_block(extra_env={"PYTHONPATH": str(fixture)})

        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("could not rewrite HL7_LAB_MEASUREMENT_FILTER", result.stderr)
        self.assertIn("synthetic sync failure", result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue(os.path.samestat(opened, self.properties.stat()))
        self.assertEqual(list(self.etc.iterdir()), [self.properties],
                         "the temporary file is left behind")
        self.assertFalse((self.state / SENTINEL).exists())

    def interrupt_configuration_write(self):
        fixture = Path(self.tmp.name) / "kill-fixture"
        fixture.mkdir()
        (fixture / "sitecustomize.py").write_text(
            "import os\n"
            "import signal\n"
            "configuration = os.stat(os.environ['PROPERTIES'])\n"
            "native_fdopen = os.fdopen\n"
            "class InterruptedWrite:\n"
            "    def __init__(self, stream): self.stream = stream\n"
            "    def __enter__(self): return self\n"
            "    def __exit__(self, *args): return self.stream.__exit__(*args)\n"
            "    def __getattr__(self, name): return getattr(self.stream, name)\n"
            "    def write(self, content):\n"
            "        self.stream.write(content[:len(content) // 2])\n"
            "        self.stream.flush()\n"
            "        os.fsync(self.stream.fileno())\n"
            "        os.kill(os.getpid(), signal.SIGKILL)\n"
            "def interrupt_target(fd, *args, **kwargs):\n"
            "    stream = native_fdopen(fd, *args, **kwargs)\n"
            "    if os.path.samestat(configuration, os.fstat(fd)):\n"
            "        return InterruptedWrite(stream)\n"
            "    return stream\n"
            "os.fdopen = interrupt_target\n",
            encoding="utf-8",
        )
        return self.run_block(extra_env={"PYTHONPATH": str(fixture)})

    def test_recovers_a_partial_write_after_the_process_is_killed(self):
        self.write_properties("before=1", old_line(), "after=retain-this-setting")
        before = self.properties.read_bytes()
        alias = self.etc / "hardlinked.properties"
        os.link(self.properties, alias)
        opened = self.properties.stat()

        interrupted = self.interrupt_configuration_write()

        self.assertEqual(interrupted.returncode, 1, interrupted.stderr)
        self.assertNotEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())
        recovery = self.properties.with_suffix(".properties.lab-filter-recovery")
        self.assertEqual(recovery.stat().st_mode & 0o777, 0o600)

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(alias.read_text(encoding="utf-8"),
                         f"before=1\n{shipped_line()}\nafter=retain-this-setting\n")
        self.assertTrue(os.path.samestat(opened, self.properties.stat()))
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertFalse(recovery.exists())

    def test_recovers_before_a_package_retry_can_replace_the_inode(self):
        self.write_properties("consultation_fax_enabled=yes", old_line(), "after=keep")
        failed = self.interrupt_configuration_write()
        self.assertEqual(failed.returncode, 1, failed.stderr)
        self.assertFalse((self.state / SENTINEL).exists())

        retry = self.run_block(package_retry=True)

        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"),
                         f"consultation_fax_enabled=true\n{shipped_line()}\nafter=keep\n")
        self.assertEqual(list(self.etc.iterdir()), [self.properties])
        self.assertTrue((self.state / SENTINEL).exists())

    def interrupted_with_recovery_record(self):
        self.write_properties("before=1", old_line(), "after=retain-this-setting")
        failed = self.interrupt_configuration_write()
        self.assertEqual(failed.returncode, 1, failed.stderr)
        return self.properties.with_suffix(".properties.lab-filter-recovery")

    def assert_recovery_refused(self, recovery, reason, kept):
        """The retry stops, says why and how to get out, and changes nothing."""
        retry = self.run_block(package_retry=True)

        self.assertEqual(retry.returncode, 1, retry.stderr)
        self.assertIn(reason, retry.stderr)
        self.assertIn("could not recover the pending lab-filter update", retry.stderr)
        # The way out: name the file to check, the record to remove, and the command to run.
        self.assertIn(str(self.properties), retry.stderr)
        self.assertIn(f"remove {recovery}", retry.stderr)
        self.assertIn("dpkg --configure -a", retry.stderr)
        self.assertEqual(self.properties.read_bytes(), kept)
        self.assertTrue(recovery.exists())
        self.assertFalse((self.state / SENTINEL).exists())

    def test_defers_the_recovery_while_an_oscar_19_import_holds_provisioning(self):
        # acquire_provision_lock answers 2 for a held import: not a failure, the next configure retries.
        recovery = self.interrupted_with_recovery_record()
        kept = self.properties.read_bytes()
        block = migration_block(package_retry=True)
        stub = 'acquire_provision_lock() { [ "${PROVISION_LOCK_HELD}" = 1 ]; }'
        self.assertIn(stub, block)
        # As in the postinst after status 2: the lock is not held, so the migrations below do not run.
        env = dict(os.environ, PROVISION_LOCK_HELD="0", STATE=str(self.state), SHARE=str(self.share),
                   PROPERTIES=str(self.properties))
        result = subprocess.run(["sh", "-c", "set -e\n" + block.replace(stub, "acquire_provision_lock() { return 2; }")],
                                env=env, capture_output=True, text=True, check=False, timeout=10)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("an OSCAR 19 import holds provisioning", result.stderr)
        # The configure ended successfully, so the package is configured: the retry is dpkg-reconfigure.
        self.assertIn("sudo dpkg-reconfigure carlos-emr", result.stderr)
        self.assertNotIn("dpkg --configure -a", result.stderr)
        self.assertNotIn("ERROR", result.stderr)
        self.assertEqual(self.properties.read_bytes(), kept)
        self.assertTrue(recovery.exists())

    def test_writes_nothing_to_the_properties_while_a_recovery_is_deferred(self):
        # Even if the later migrations could take the lock (the import finished in between), none may
        # touch the possibly torn file: a sed -i would replace its inode and break the later recovery.
        self.write_properties("consultation_fax_enabled=yes", old_line(), "after=retain-this-setting")
        failed = self.interrupt_configuration_write()
        self.assertEqual(failed.returncode, 1, failed.stderr)
        recovery = self.properties.with_suffix(".properties.lab-filter-recovery")
        kept = self.properties.read_bytes()
        inode = self.properties.stat().st_ino
        block = migration_block(package_retry=True)
        stub = 'acquire_provision_lock() { [ "${PROVISION_LOCK_HELD}" = 1 ]; }'
        env = dict(os.environ, PROVISION_LOCK_HELD="1", STATE=str(self.state), SHARE=str(self.share),
                   PROPERTIES=str(self.properties))
        # Status 2 for the early recovery only; the words block still sees the lock as held.
        result = subprocess.run(["sh", "-c", "set -e\n" + block.replace(stub, "acquire_provision_lock() { return 2; }")],
                                env=env, capture_output=True, text=True, check=False, timeout=10)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), kept)
        self.assertEqual(self.properties.stat().st_ino, inode)
        self.assertTrue(recovery.exists())
        self.assertFalse((self.state / SENTINEL).exists(), "the migration must run again after the recovery")
        self.assertEqual(sorted(p.name for p in self.etc.iterdir()),
                         sorted(["carlos.properties", "carlos.properties.lab-filter-recovery"]))

    def test_refuses_to_recover_a_configuration_edited_after_the_interruption(self):
        recovery = self.interrupted_with_recovery_record()
        torn = self.properties.read_bytes()
        edited = b"#" * len(torn)
        self.properties.write_bytes(edited)

        self.assert_recovery_refused(recovery, "edited after an interrupted lab filter migration", edited)

    def test_refuses_to_recover_a_configuration_restored_from_a_backup(self):
        recovery = self.interrupted_with_recovery_record()
        restored = self.etc / "restored.properties"
        restored.write_bytes(b"restored=from-backup\n")
        restored.replace(self.properties)

        self.assert_recovery_refused(recovery, "describes another or read-only configuration",
                                     b"restored=from-backup\n")

    def test_refuses_a_recovery_record_other_users_could_read(self):
        recovery = self.interrupted_with_recovery_record()
        recovery.chmod(0o644)
        kept = self.properties.read_bytes()

        self.assert_recovery_refused(recovery, "not a private regular file", kept)

    def test_refuses_an_invalid_recovery_record(self):
        recovery = self.interrupted_with_recovery_record()
        recovery.write_text("not a recovery record", encoding="ascii")
        recovery.chmod(0o600)
        kept = self.properties.read_bytes()

        self.assert_recovery_refused(recovery, "the lab filter recovery file is invalid", kept)

    def test_the_unit_refuses_a_start_on_a_pending_recovery_record_and_says_why(self):
        # A rewrite killed mid-write can leave carlos.properties neither version. The unit's
        # ExecCondition runs a guard that skips the start AND writes the remedy to the journal;
        # it must name the record the helper writes, and the package must ship it.
        guard = ROOT / "debian" / "assets" / "bin" / "carlos-emr-config-recovery-guard"
        unit = (ROOT / "debian" / "carlos-emr.carlos-emr.service").read_text(encoding="utf-8")
        postinst = POSTINST.read_text(encoding="utf-8")
        rules = (ROOT / "debian" / "rules").read_text(encoding="utf-8")
        self.assertIn("ExecCondition=+/usr/lib/carlos-emr/carlos-emr-config-recovery-guard", unit)
        # Not also a [Unit] condition: systemd checks those BEFORE ExecCondition=, so a record would skip the
        # start silently and the guard's message would never reach the journal.
        self.assertNotIn("lab-filter-recovery", "".join(line for line in unit.splitlines(True)
                                                        if line.startswith("Condition")))
        self.assertNotIn("ConditionPathExists=!/etc/carlos-emr", unit)
        # The OSCAR 19 guard answers first.
        self.assertLess(unit.index("carlos-emr-o19-guard\n"), unit.index("carlos-emr-config-recovery-guard\n"))
        self.assertIn("carlos-emr-config-recovery-guard $(STAGE)/$(LIBDIR)/carlos-emr-config-recovery-guard", rules)
        self.assertTrue(os.access(guard, os.X_OK))
        self.assertIn('RECORD="${1:-/etc/carlos-emr/carlos.properties.lab-filter-recovery}"',
                      guard.read_text(encoding="utf-8"))
        self.assertIn('PROPERTIES="${CONF_DIR}/carlos.properties"', postinst)
        self.assertIn("CONF_DIR=/etc/carlos-emr", postinst)
        self.assertIn('recovery = path + b".lab-filter-recovery"', postinst)

        record = self.properties.with_suffix(".properties.lab-filter-recovery")
        clear = subprocess.run(["sh", str(guard), str(record)], capture_output=True, text=True)
        self.assertEqual((clear.returncode, clear.stderr), (0, ""))

        record.write_bytes(b"[]")
        refused = subprocess.run(["sh", str(guard), str(record)], capture_output=True, text=True)
        self.assertEqual(refused.returncode, 1)
        self.assertIn("NOT starting", refused.stderr)
        self.assertIn("sudo dpkg --configure -a", refused.stderr)
        # A deferred recovery leaves the package configured: dpkg --configure -a does nothing then.
        self.assertIn("sudo dpkg-reconfigure carlos-emr", refused.stderr)
        # The guard says what the CLI does NOT do: carlos-ctl 1.3.5's start and restart do not relay it, so
        # the header must not promise that, and the release notes tell the operator where to look.
        header = guard.read_text(encoding="utf-8")
        self.assertNotIn("start|restart relay", header)
        self.assertIn("do NOT relay it", header)
        notes = (ROOT / "docs" / "release-notes-2026.08.md").read_text(encoding="utf-8")
        self.assertIn("`carlos-ctl start` and `restart`", notes)
        self.assertIn("rather than relying on their exit status", notes)
        self.assertIn(str(record), refused.stderr)
        self.assertIn("remove the", refused.stderr)

        record.unlink()
        record.symlink_to("missing-target")
        dangling = subprocess.run(["sh", str(guard), str(record)], capture_output=True, text=True)
        self.assertEqual(dangling.returncode, 1, "a dangling link is still something left behind")

    def test_the_boot_provisioner_leaves_the_file_and_record_untouched_until_recovery_finishes(self):
        # carlos-emr-provision.service is pulled in by carlos-emr.service and finishes before it; its
        # `carlos-ctl finish-install --boot` runs init-config, whose first property write replaces the
        # file's inode (a recovery record describes the OLD inode and would then refuse for good).
        # Evaluate the shipped unit the way systemd does: each ExecCondition= must exit 0, else the unit
        # is skipped before ExecStart=. The fake carlos-ctl replaces the file as init-config does.
        unit = (ROOT / "debian" / "carlos-emr.carlos-emr-provision.service").read_text(encoding="utf-8")
        service = unit.split("[Service]", 1)[1].split("[Install]", 1)[0]
        conditions = [line.split("=", 1)[1] for line in service.splitlines() if line.startswith("ExecCondition=")]
        starts = [line.split("=", 1)[1] for line in service.splitlines() if line.startswith("ExecStart=")]
        guard_path = "/usr/lib/carlos-emr/carlos-emr-config-recovery-guard"
        self.assertEqual(conditions, ["+" + guard_path], "the provisioner must run the same guard as the service")
        self.assertEqual(starts, ["/usr/sbin/carlos-ctl finish-install --boot"])
        # A [Unit] condition would skip silently before the guard could say why.
        self.assertNotIn("lab-filter-recovery", "".join(line for line in unit.splitlines(True)
                                                        if line.startswith("Condition")))
        guard = ROOT / "debian" / "assets" / "bin" / "carlos-emr-config-recovery-guard"

        bin_dir = Path(self.tmp.name) / "bin"
        bin_dir.mkdir()
        called = Path(self.tmp.name) / "carlos-ctl-called"
        fake = bin_dir / "carlos-ctl"
        fake.write_text(
            "#!/bin/sh\n"
            ": > '{called}'\n"
            # What init-config's first prop_set does: write a temp file and rename it over the path.
            "printf 'rewritten\\n' > '{props}.tmp' && mv '{props}.tmp' '{props}'\n".format(
                called=called, props=self.properties),
            encoding="ascii")
        fake.chmod(0o755)
        marker = self.state / ".install-incomplete"
        record = self.properties.with_suffix(".properties.lab-filter-recovery")

        def boot(record_present):
            """Run the unit as systemd would: the Unit conditions, then each ExecCondition, then ExecStart."""
            if not marker.exists():                      # ConditionPathExists=/var/lib/carlos-emr/.install-incomplete
                return "skipped (no marker)", None
            for condition in conditions:
                self.assertTrue(condition.startswith("+"))
                self.assertEqual(condition[1:], guard_path)
                guarded = subprocess.run(["sh", str(guard), str(record)], capture_output=True, text=True)
                if guarded.returncode != 0:
                    # 1..254: "condition failed", the unit is skipped and is not failed.
                    self.assertEqual(guarded.returncode, 1)
                    return "skipped (condition)", guarded
            subprocess.run(["sh", "-c", starts[0].replace("/usr/sbin/carlos-ctl", str(fake))], check=True)
            return "ran", None

        self.write_properties("kept=as-it-was")
        self.properties.chmod(0o640)
        marker.write_text("", encoding="ascii")
        record.write_bytes(b"[]")
        record.chmod(0o600)
        before = (self.properties.read_bytes(), self.properties.stat().st_ino, record.read_bytes(), record.stat().st_ino)

        outcome, guarded = boot(record_present=True)

        self.assertEqual(outcome, "skipped (condition)")
        self.assertIn("NOT starting", guarded.stderr)
        self.assertFalse(called.exists(), "carlos-ctl finish-install ran on a file awaiting recovery")
        self.assertEqual(before, (self.properties.read_bytes(), self.properties.stat().st_ino,
                                  record.read_bytes(), record.stat().st_ino))
        self.assertTrue(marker.exists(), "the marker stays, so the next boot after the recovery provisions")

        # The recovery finished (the record is gone): the same boot now provisions, so the fake is
        # really reachable through the shipped ExecStart (the test above is not vacuous).
        record.unlink()
        outcome, _ = boot(record_present=False)
        self.assertEqual(outcome, "ran")
        self.assertTrue(called.exists())
        self.assertEqual(self.properties.read_bytes(), b"rewritten\n")

    def test_removes_the_private_staging_file_from_an_interrupted_journal_write(self):
        self.write_properties(KEY + "^custom$")
        before = self.properties.read_bytes()
        staged = self.properties.with_suffix(".properties.lab-filter-recovery.tmp")
        staged.write_bytes(b"unfinished recovery record")
        staged.chmod(0o600)

        retry = self.run_block(package_retry=True)

        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertEqual(list(self.etc.iterdir()), [self.properties])

    def test_updates_the_target_of_a_managed_symlink(self):
        self.write_properties(old_line())
        managed = self.etc / "clinic.properties"
        self.properties.rename(managed)
        self.properties.symlink_to(managed.name)
        opened = managed.stat()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.readlink(), Path(managed.name))
        self.assertTrue(os.path.samestat(opened, managed.stat()))
        self.assertEqual(managed.read_text(encoding="utf-8"), shipped_line() + "\n")
        self.assertTrue((self.state / SENTINEL).exists())

    def run_install_once_then_block(self):
        """The postinst's skeleton installation, then this migration, as one configure runs them."""
        text = POSTINST.read_text(encoding="utf-8")
        function = re.search(r'^install_once\(\) \{.*?^\}\n', text, re.S | re.M)
        self.assertIsNotNone(function, "no install_once() in carlos-emr.postinst")
        owner = "{0}:{1}".format(os.getuid(), os.getgid())
        script = ("set -e\n" + function.group(0)
                  + 'install_once "${SHARE}/skel/carlos.properties" "${PROPERTIES}" 0640 ' + owner + "\n"
                  + migration_block())
        env = dict(os.environ, PROVISION_LOCK_HELD="1", STATE=str(self.state), SHARE=str(self.share),
                   PROPERTIES=str(self.properties))
        return subprocess.run(["sh", "-c", script], env=env, capture_output=True, text=True,
                              check=False, timeout=10)

    def test_leaves_a_dangling_managed_link_eligible_for_retry(self):
        # The earlier install_once call is part of the case: on an upgrade whose managed target is
        # briefly unavailable, `[ ! -e dest ]` used to read the link as a fresh install, GNU install
        # replaced it with the stock skeleton, and the migration then wrote its completion marker.
        self.properties.symlink_to("missing-clinic.properties")

        result = self.run_install_once_then_block()

        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("fix the configuration target", result.stderr)
        self.assertIn("left as it is", result.stderr)
        self.assertTrue(self.properties.is_symlink(), "the managed link was replaced by the skeleton")
        self.assertEqual(self.properties.readlink(), Path("missing-clinic.properties"))
        self.assertFalse((self.etc / "missing-clinic.properties").exists())
        self.assertFalse((self.state / SENTINEL).exists())

        # The target comes back holding the #4613 line: the retry migrates it through the link.
        target = self.etc / "missing-clinic.properties"
        target.write_text(old_line() + "\n", encoding="utf-8")
        target.chmod(0o640)

        retry = self.run_install_once_then_block()

        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assertTrue(self.properties.is_symlink())
        self.assertEqual(target.read_text(encoding="utf-8"), shipped_line() + "\n")
        self.assertTrue((self.state / SENTINEL).exists())

    def init_config_run(self, new_content, *, exit_status=0, replace=True):
        """The postinst's init-config step with a fake carlos-ctl that writes ``new_content`` the way
        carlos-ctl 1.3.5's property writer does: a temporary file renamed over the path."""
        text = POSTINST.read_text(encoding="utf-8")
        function = re.search(r'^run_init_config_keeping_link\(\) \{.*?^\}\n', text, re.S | re.M)
        self.assertIsNotNone(function, "no run_init_config_keeping_link() in carlos-emr.postinst")
        bin_dir = Path(self.tmp.name) / "init-config-bin"
        bin_dir.mkdir(exist_ok=True)
        fake = bin_dir / "carlos-ctl"
        if replace:
            body = ("printf '%s' '{c}' > '{p}.tmp' && mv '{p}.tmp' '{p}'\n").format(c=new_content, p=self.properties)
        else:
            # A carlos-ctl that writes through the link keeps it.
            body = "printf '%s' '{c}' > '{p}'\n".format(c=new_content, p=self.properties)
        fake.write_text("#!/bin/sh\n" + body + "exit {0}\n".format(exit_status), encoding="ascii")
        fake.chmod(0o755)
        env = dict(os.environ, PATH="{0}:{1}".format(bin_dir, os.environ["PATH"]), PROPERTIES=str(self.properties))
        return subprocess.run(["sh", "-c", function.group(0) + "run_init_config_keeping_link\necho status=$?\n"],
                              env=env, capture_output=True, text=True, check=False, timeout=20)

    def test_init_config_keeps_a_managed_symlink_its_target_inode_and_attributes(self):
        # The normal configure path: postinst calls `carlos-ctl init-config`, whose first property write
        # replaces the live path with a temporary file. The managed target must still get the new
        # settings in place, with its inode, mode and extended attribute, and the link must come back.
        managed = self.etc / "clinic.properties"
        managed.write_text("old=settings\n", encoding="utf-8")
        managed.chmod(0o640)
        attribute = None
        try:
            os.setxattr(managed, "user.mycarlos-test", b"kept")
            attribute = "user.mycarlos-test"
        except OSError:
            pass                                           # a filesystem without user xattrs: the rest still runs
        self.properties.symlink_to(managed.name)
        opened = managed.stat()

        result = self.init_config_run("new=settings\n")

        self.assertIn("status=0", result.stdout, result.stderr)
        self.assertTrue(self.properties.is_symlink(), "init-config left a regular file where the link was")
        self.assertEqual(self.properties.readlink(), Path(managed.name))
        self.assertTrue(os.path.samestat(opened, managed.stat()), "the managed target's inode changed")
        self.assertEqual(managed.read_text(encoding="utf-8"), "new=settings\n")
        self.assertEqual(stat.S_IMODE(managed.stat().st_mode), 0o640)
        if attribute:
            self.assertEqual(os.getxattr(managed, attribute), b"kept")
        self.assertEqual(sorted(path.name for path in self.etc.iterdir()),
                         ["carlos.properties", "clinic.properties"], "a temporary file was left behind")

    def test_init_config_passes_its_exit_status_on_and_still_keeps_the_link(self):
        managed = self.etc / "clinic.properties"
        managed.write_text("old=settings\n", encoding="utf-8")
        self.properties.symlink_to(managed.name)

        failed = self.init_config_run("partial=settings\n", exit_status=3)

        self.assertIn("status=3", failed.stdout, failed.stderr)
        self.assertTrue(self.properties.is_symlink())
        self.assertEqual(managed.read_text(encoding="utf-8"), "partial=settings\n")

    def test_init_config_leaves_a_regular_file_and_a_link_it_did_not_break_alone(self):
        # A regular file: nothing to carry anywhere.
        self.properties.write_text("old=settings\n", encoding="utf-8")
        inode = self.properties.stat().st_ino
        regular = self.init_config_run("new=settings\n", replace=False)
        self.assertIn("status=0", regular.stdout, regular.stderr)
        self.assertEqual(self.properties.read_text(encoding="utf-8"), "new=settings\n")
        self.assertEqual(self.properties.stat().st_ino, inode)
        self.assertFalse(self.properties.is_symlink())
        self.assertEqual(regular.stdout.count("carried"), 0)
        # A link a (fixed) carlos-ctl wrote through: untouched, and no message about carrying.
        self.properties.unlink()
        managed = self.etc / "clinic.properties"
        managed.write_text("old=settings\n", encoding="utf-8")
        self.properties.symlink_to(managed.name)
        through = self.init_config_run("through=link\n", replace=False)
        self.assertIn("status=0", through.stdout, through.stderr)
        self.assertTrue(self.properties.is_symlink())
        self.assertEqual(managed.read_text(encoding="utf-8"), "through=link\n")
        self.assertNotIn("carried", through.stdout)
        # A dangling link is not ours to touch (the steps that need the file refuse it).
        managed.unlink()
        dangling = self.init_config_run("x=y\n", replace=False)
        self.assertIn("status=", dangling.stdout)

    def test_init_config_says_so_when_the_managed_target_cannot_be_written(self):
        managed = self.etc / "clinic.properties"
        managed.write_text("old=settings\n", encoding="utf-8")
        managed.chmod(0o440)
        self.properties.symlink_to(managed.name)
        if os.access(managed, os.W_OK):
            self.skipTest("running as a user that can write a read-only file (root)")

        result = self.init_config_run("new=settings\n")

        self.assertIn("status=0", result.stdout, result.stderr)
        self.assertIn("could not be written into", result.stderr)
        # The live settings are not lost: they stay in the regular file the writer made.
        self.assertFalse(self.properties.is_symlink())
        self.assertEqual(self.properties.read_text(encoding="utf-8"), "new=settings\n")
        self.assertEqual(managed.read_text(encoding="utf-8"), "old=settings\n")

    def test_install_once_still_installs_a_missing_file_and_keeps_an_existing_one(self):
        text = POSTINST.read_text(encoding="utf-8")
        function = re.search(r'^install_once\(\) \{.*?^\}\n', text, re.S | re.M).group(0)
        owner = "{0}:{1}".format(os.getuid(), os.getgid())
        skel, dest = self.share / "skel" / "carlos.properties", self.etc / "installed.properties"

        def run():
            return subprocess.run(["sh", "-c", "set -e\n" + function + 'install_once "{0}" "{1}" 0640 {2}\n'.format(
                skel, dest, owner)], capture_output=True, text=True, check=False, timeout=10)

        fresh = run()
        self.assertEqual(fresh.returncode, 0, fresh.stderr)
        self.assertEqual(dest.read_bytes(), skel.read_bytes())
        self.assertEqual(stat.S_IMODE(dest.stat().st_mode), 0o640)
        dest.write_text("operator=edit\n", encoding="utf-8")
        again = run()
        self.assertEqual(again.returncode, 0, again.stderr)
        self.assertEqual(dest.read_text(encoding="utf-8"), "operator=edit\n")
        # A link to a file that is there is also kept (its target's mode is applied, never replaced).
        link = self.etc / "linked.properties"
        link.symlink_to(dest.name)
        subprocess.run(["sh", "-c", "set -e\n" + function + 'install_once "{0}" "{1}" 0600 {2}\n'.format(
            skel, link, owner)], check=True, timeout=10)
        self.assertTrue(link.is_symlink())
        self.assertEqual(dest.read_text(encoding="utf-8"), "operator=edit\n")

    def test_keeps_writes_from_a_process_that_holds_the_configuration_lock(self):
        self.write_properties(old_line())
        with ThreadPoolExecutor(max_workers=1) as pool, self.properties.open("r+b") as writer:
            fcntl.lockf(writer, fcntl.LOCK_EX)
            try:
                pending = pool.submit(self.run_block)
                with self.assertRaises(TimeoutError):
                    pending.result(timeout=0.25)
                writer.seek(0, os.SEEK_END)
                writer.write(b"concurrent.setting=kept\n")
                writer.flush()
            finally:
                fcntl.lockf(writer, fcntl.LOCK_UN)
            result = pending.result(timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(os.path.samestat(os.fstat(writer.fileno()), self.properties.stat()))
            writer.seek(0)
            self.assertEqual(writer.read().decode("utf-8"),
                             shipped_line() + "\nconcurrent.setting=kept\n")
        self.assertTrue((self.state / SENTINEL).exists())

    def test_runs_only_once(self):
        self.write_properties(old_line())
        (self.state / SENTINEL).write_text("", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)

    def test_waits_for_the_provisioning_lock(self):
        self.write_properties(old_line())
        before = self.properties.read_bytes()

        result = self.run_block(lock_held="0")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())

    def test_retries_when_the_skeleton_lacks_the_key(self):
        self.write_properties(old_line())
        self.skel.write_text("other=stock\n", encoding="utf-8")
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertFalse((self.state / SENTINEL).exists())
        self.assertIn("does not define one non-empty HL7_LAB_MEASUREMENT_FILTER",
                      result.stderr)

    def test_writes_the_sentinel_on_a_fresh_install(self):
        self.write_properties(shipped_line())
        before = self.properties.read_bytes()

        result = self.run_block()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.properties.read_bytes(), before)
        self.assertTrue((self.state / SENTINEL).exists())
        self.assertNotIn("WARNING", result.stderr)

    def test_runs_after_the_4613_block(self):
        # An install still on the pre-#4613 line is moved straight to the
        # skeleton's line by the earlier block, so this one must come after it.
        text = POSTINST.read_text(encoding="utf-8")
        self.assertLess(text.index('        LAB_FILTER_SENTINEL='),
                        text.index('        LAB_FILTER_WORDS_SENTINEL='))

    @staticmethod
    def purge_removes_sentinel(text):
        """True when the purge branch of `text` has an EXECUTED top-level `rm` naming the sentinel.

        Reads the branch's logical commands (a trailing backslash continues one), tokenises each the way a
        shell does (quotes removed, `#` starts a comment, `;` `&&` `||` end the command) and requires the
        command to start with `rm`, to sit at the branch's own indent (not inside an `if` or a loop) and to
        name `${STATE}/<sentinel>` among the words before its first separator."""
        import shlex
        start = text.index("\n    purge)\n")
        end = text.index("\n        ;;", start)
        commands, current = [], None
        for line in text[start:end].splitlines()[1:]:
            if current is not None:
                current += " " + line.strip()
            elif line.strip():
                current = line
            else:
                continue
            if current.endswith("\\"):
                current = current[:-1]
                continue
            commands.append(current)
            current = None
        target = "${STATE}/" + SENTINEL
        for command in commands:
            indent = len(command) - len(command.lstrip(" "))
            if indent != 8 or command.lstrip().startswith("#"):
                continue
            lexer = shlex.shlex(command, posix=True, punctuation_chars=True)
            lexer.commenters = "#"
            lexer.whitespace_split = True
            try:
                tokens = list(lexer)
            except ValueError:  # an unterminated quote
                continue
            words = []
            for token in tokens:
                if token in (";", "&&", "||", ";;", "&"):
                    break
                words.append(token)
            if words[:1] == ["rm"] and target in words:
                return True
        return False

    def test_purge_removes_the_sentinel(self):
        # purge keeps carlos.properties, so a reinstall must migrate again. The removal must be an
        # EXECUTED rm in the purge branch: a mention in a comment, or in another branch, is not one.
        self.assertTrue(self.purge_removes_sentinel(POSTRM.read_text(encoding="utf-8")),
                        "the purge branch of postrm must rm the lab filter sentinel")

    def test_the_purge_check_rejects_every_way_of_not_running_the_removal(self):
        real = POSTRM.read_text(encoding="utf-8")
        line = '              "${STATE}/' + SENTINEL + '" \\\n'
        self.assertIn(line, real)
        without = real.replace(line, "", 1)
        last = '              "${STATE}/.install-incomplete"\n'
        self.assertIn(last, without)
        named = '"${STATE}/' + SENTINEL + '"'
        mutants = {
            # M1: the name stays only in a comment line
            "comment line": without.replace(last, last + "        # " + named + "\n", 1),
            # M2: an inline trailing comment on the last line of the rm
            "inline comment": without.replace(last, last.rstrip("\n") + " # " + named + "\n", 1),
            # M3: the rm inside a block that never runs
            "dead block": without.replace(
                last, last + "        if false; then\n            rm -f " + named + "\n        fi\n", 1),
            # M5: the name after a `;`, as an argument of another command
            "after a separator": without.replace(
                last, last + "        rm -f /nonexistent; echo " + named + "\n", 1),
            # the name only in an echo, not an rm
            "echo": without.replace(last, last + "        echo " + named + "\n", 1),
            # the removal gone altogether
            "absent": without,
        }
        for name, text in mutants.items():
            self.assertNotEqual(text, real, name)
            self.assertFalse(self.purge_removes_sentinel(text), name)
        # and the same removal on a line of its own, executed, is accepted
        accepted = without.replace(last, last + "        rm -f " + named + "\n", 1)
        self.assertTrue(self.purge_removes_sentinel(accepted))


if __name__ == "__main__":
    unittest.main()
