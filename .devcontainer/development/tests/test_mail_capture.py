#!/usr/bin/env python3
"""Regression tests for the devcontainer mail-capture protocol."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import fcntl
import os
from pathlib import Path
import pwd
import grp
import shutil
import stat
import subprocess
import tempfile
import time
import unittest


DEVELOPMENT_DIR = Path(__file__).resolve().parents[1]
WRITER = DEVELOPMENT_DIR / "scripts" / "postfix-capture-mail"
INBOX = DEVELOPMENT_DIR / "scripts" / "mail-capture-inbox"
MAIL = DEVELOPMENT_DIR / "scripts" / "mail"


class MailCaptureTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.capture_directory = Path(self.temporary_directory.name)
        self.capture_file = self.capture_directory / "messages.eml"
        self.capture_lock = self.capture_directory / ".lock"
        self.spool_directory = self.capture_directory / ".spool"
        self.capture_map = self.capture_directory / "capture-map"
        self.send_allowlist = self.capture_directory / "send-allowlist"
        self.effective_send_allowlist = (
            self.capture_directory / "send-allowlist.effective"
        )
        self.capture_map.write_text("/.*/ devcapture:\n")
        self.send_allowlist.write_text("# empty\n")
        self.capture_file.touch()
        self.capture_lock.touch()
        self.spool_directory.mkdir()
        if os.geteuid() == 0:
            writer_account = pwd.getpwnam("nobody")
            self.writer_uid = writer_account.pw_uid
            self.writer_gid = writer_account.pw_gid
            os.chown(self.capture_directory, 0, self.writer_gid)
            self.capture_directory.chmod(0o750)
            for writer_path in (
                self.capture_file,
                self.capture_lock,
                self.spool_directory,
            ):
                os.chown(writer_path, self.writer_uid, self.writer_gid)
            self.capture_file.chmod(0o660)
            self.capture_lock.chmod(0o660)
            self.spool_directory.chmod(0o770)
        else:
            self.writer_uid = os.getuid()
            self.writer_gid = os.getgid()
        self.environment = os.environ | {
            "CARLOS_MAIL_CAPTURE_DIR": str(self.capture_directory),
            "CARLOS_MAIL_CAPTURE_FILE": str(self.capture_file),
            "CARLOS_MAIL_CAPTURE_LOCK": str(self.capture_lock),
            "CARLOS_MAIL_CAPTURE_SPOOL_DIR": str(self.spool_directory),
            "CARLOS_MAIL_CAPTURE_INBOX": str(INBOX),
            "CARLOS_MAIL_CAPTURE_USER": pwd.getpwuid(self.writer_uid).pw_name,
            "CARLOS_MAIL_CAPTURE_GROUP": grp.getgrgid(self.writer_gid).gr_name,
            "CARLOS_MAIL_CAPTURE_MAP": str(self.capture_map),
            "CARLOS_MAIL_SEND_ALLOWLIST": str(self.send_allowlist),
            "CARLOS_MAIL_EFFECTIVE_SEND_ALLOWLIST": str(
                self.effective_send_allowlist
            ),
        }

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def use_delivery_identity(self) -> None:
        os.setgid(self.writer_gid)
        os.setuid(self.writer_uid)

    def capture(self, raw_message: bytes, recipient: str = "patient@example.test") -> None:
        subprocess.run(
            [WRITER, "sender@example.test", recipient],
            input=raw_message,
            env=self.environment,
            preexec_fn=self.use_delivery_identity if os.geteuid() == 0 else None,
            check=True,
        )

    def inbox(self, *arguments: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            [INBOX, self.capture_file, *arguments],
            capture_output=True,
            check=True,
        )

    def mail(self, *arguments: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            [MAIL, *arguments],
            env=self.environment,
            capture_output=True,
            check=True,
        )

    def append_incomplete_record(self) -> bytes:
        incomplete_record = (
            b"===== CARLOS DEV EMAIL CAPTURE v2 =====\n"
            b"Captured-At: interrupted\n"
            b"Envelope-From: sender@example.test\n"
            b"Envelope-To: patient@example.test\n"
            b"Raw-Length: 1000\n\n"
            b"partial payload"
        )
        with self.capture_file.open("ab") as capture_stream:
            capture_stream.write(incomplete_record)
        return incomplete_record

    def test_marker_lines_in_raw_mime_do_not_split_records(self) -> None:
        first_message = (
            b"From: sender@example.test\r\n"
            b"To: patient@example.test\r\n"
            b"Subject: First message\r\n\r\n"
            b"===== CARLOS DEV EMAIL CAPTURE =====\r\n"
            b"===== CARLOS DEV EMAIL CAPTURE v2 =====\n"
            b"body without a final newline"
        )
        second_message = b"Subject: Second message\n\nsecond body\n"

        self.capture(first_message)
        self.capture(second_message, "second@example.test")

        self.assertEqual(self.inbox("count").stdout, b"2\n")
        listing = self.mail("list").stdout
        self.assertIn(b"First message", listing)
        self.assertIn(b"Second message", listing)
        first_read = self.mail("read", "1").stdout
        self.assertIn(first_message, first_read)
        self.assertNotIn(second_message, first_read)

    def test_root_helper_recreates_a_deleted_capture_file(self) -> None:
        self.capture(b"Subject: Before deletion\n\nbody\n")
        self.capture_file.unlink()

        replacement = b"Subject: After deletion\n\nreplacement body\n"
        failed_delivery = subprocess.run(
            [WRITER, "sender@example.test", "patient@example.test"],
            input=replacement,
            env=self.environment,
            capture_output=True,
            check=False,
            preexec_fn=self.use_delivery_identity if os.geteuid() == 0 else None,
        )
        self.assertEqual(failed_delivery.returncode, 75)

        self.mail("list")
        self.capture(replacement)

        self.assertEqual(self.inbox("count").stdout, b"1\n")
        self.assertIn(replacement, self.inbox("read", "1").stdout)
        self.assertEqual(stat.S_IMODE(self.capture_file.stat().st_mode), 0o660)

    def test_mail_list_escapes_terminal_controls_in_summary_fields(self) -> None:
        self.capture(
            b"Subject: copy\x1b]52;c;YXR0YWNrZXI=\x07\n\nbody\n",
            "patient\x1b[31m@example.test",
        )

        listing = self.mail("list").stdout

        self.assertNotIn(b"\x1b", listing)
        self.assertNotIn(b"\x07", listing)
        self.assertIn(b"copy\\x1b]52;c;YXR0YWNrZXI=\\x07", listing)
        self.assertIn(b"patient\\x1b[31m@example.test", listing)

    def test_terminal_sanitizer_escapes_controls_but_keeps_unicode(self) -> None:
        result = subprocess.run(
            [INBOX, "/dev/stdin", "sanitize"],
            input="line\nRésumé \x1b[31mred\x1b[0m\x07\rspoof\n".encode(),
            capture_output=True,
            check=True,
        )

        self.assertEqual(
            result.stdout,
            "line\nRésumé \\x1b[31mred\\x1b[0m\\x07\\x0dspoof\n".encode(),
        )

    def test_delivery_refuses_a_symlink_capture_spool(self) -> None:
        self.spool_directory.rmdir()
        spool_target = self.capture_directory / "outside-spool"
        spool_target.mkdir()
        if os.geteuid() == 0:
            os.chown(spool_target, self.writer_uid, self.writer_gid)
        spool_target.chmod(0o770)
        self.spool_directory.symlink_to(spool_target, target_is_directory=True)

        result = subprocess.run(
            [WRITER, "sender@example.test", "patient@example.test"],
            input=b"Subject: Must not spool through a symlink\n\nbody\n",
            env=self.environment,
            capture_output=True,
            check=False,
            preexec_fn=self.use_delivery_identity if os.geteuid() == 0 else None,
        )

        self.assertEqual(result.returncode, 75)
        self.assertIn(b"capture spool is missing or not writable", result.stderr)
        self.assertEqual(list(spool_target.iterdir()), [])

    def test_root_helper_refuses_a_symlink_capture_lock(self) -> None:
        victim = self.capture_directory / "unrelated-file"
        victim.write_text("must remain unchanged")
        victim.chmod(0o600)
        original_stat = victim.stat()
        self.capture_lock.unlink()
        self.capture_lock.symlink_to(victim)

        result = subprocess.run(
            [MAIL, "list"],
            env=self.environment,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"Refusing non-regular capture lock", result.stderr)
        self.assertEqual(victim.read_text(), "must remain unchanged")
        current_stat = victim.stat()
        self.assertEqual(current_stat.st_uid, original_stat.st_uid)
        self.assertEqual(current_stat.st_gid, original_stat.st_gid)
        self.assertEqual(
            stat.S_IMODE(current_stat.st_mode),
            stat.S_IMODE(original_stat.st_mode),
        )

    def test_concurrent_deliveries_remain_complete_records(self) -> None:
        def deliver(index: int) -> subprocess.CompletedProcess[bytes]:
            raw_message = (
                f"Subject: Concurrent {index}\n\n".encode()
                + f"payload-{index}-".encode() * 10_000
            )
            return subprocess.run(
                [WRITER, "sender@example.test", f"patient-{index}@example.test"],
                input=raw_message,
                capture_output=True,
                env=self.environment,
                check=False,
                preexec_fn=(
                    self.use_delivery_identity if os.geteuid() == 0 else None
                ),
            )

        with ThreadPoolExecutor(max_workers=5) as executor:
            results = list(executor.map(deliver, range(5)))
        for result in results:
            self.assertEqual(result.returncode, 0, result.stderr.decode())

        self.assertEqual(self.inbox("count").stdout, b"5\n")
        listing = self.mail("list").stdout
        for index in range(5):
            self.assertIn(f"Concurrent {index}".encode(), listing)

    def test_mail_list_repairs_an_incomplete_trailing_record(self) -> None:
        complete_message = b"Subject: Before crash\n\ncomplete body\n"
        self.capture(complete_message)
        incomplete_record = self.append_incomplete_record()

        listing = self.mail("list")

        self.assertIn(b"Before crash", listing.stdout)
        self.assertIn(b"removed", listing.stderr)
        self.assertNotIn(incomplete_record, self.capture_file.read_bytes())
        self.assertEqual(self.inbox("count").stdout, b"1\n")

    def test_delivery_repairs_an_incomplete_trailing_record(self) -> None:
        first_message = b"Subject: Before delivery repair\n\nfirst body\n"
        second_message = b"Subject: After delivery repair\n\nsecond body\n"
        self.capture(first_message)
        self.append_incomplete_record()

        self.capture(second_message)

        self.assertEqual(self.inbox("count").stdout, b"2\n")
        self.assertIn(first_message, self.inbox("read", "1").stdout)
        self.assertIn(second_message, self.inbox("read", "2").stdout)

    def test_repair_does_not_discard_invalid_data(self) -> None:
        invalid_capture = b"not a capture record\n"
        self.capture_file.write_bytes(invalid_capture)

        result = subprocess.run(
            [INBOX, self.capture_file, "repair"],
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 1)
        self.assertIn(b"Invalid mail capture record marker", result.stderr)
        self.assertEqual(self.capture_file.read_bytes(), invalid_capture)

    def test_clear_remains_available_for_invalid_data(self) -> None:
        self.capture_file.write_bytes(b"not a capture record\n")

        result = self.mail("clear")

        self.assertIn(b"Cleared", result.stdout)
        self.assertEqual(self.capture_file.read_bytes(), b"")

    def test_clear_waits_for_the_delivery_lock(self) -> None:
        self.capture(b"Subject: Locked\n\nbody\n")
        orphan_spool = self.spool_directory / ".raw-message.orphaned"
        orphan_spool.write_bytes(b"sensitive unfinished message")
        with self.capture_lock.open("a+b") as lock_stream:
            fcntl.flock(lock_stream, fcntl.LOCK_EX)
            clear_process = subprocess.Popen(
                [MAIL, "clear"],
                env=self.environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            time.sleep(0.2)
            self.assertIsNone(clear_process.poll())
            fcntl.flock(lock_stream, fcntl.LOCK_UN)

        stdout, stderr = clear_process.communicate(timeout=5)
        self.assertEqual(clear_process.returncode, 0, stderr.decode())
        self.assertIn(b"Cleared", stdout)
        self.assertEqual(self.capture_file.read_bytes(), b"")
        self.assertFalse(orphan_spool.exists())

    def test_clear_holds_one_lock_across_file_preparation_and_truncation(self) -> None:
        self.capture(b"Subject: Clear snapshot\n\nbody\n")
        fake_binary_directory = self.capture_directory / "clear-flock-bin"
        fake_binary_directory.mkdir()
        flock_log = self.capture_directory / "clear-flock.log"
        recording_flock = fake_binary_directory / "flock"
        recording_flock.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$FLOCK_LOG\"\n"
            "exec \"$REAL_FLOCK\" \"$@\"\n"
        )
        recording_flock.chmod(0o755)
        clear_environment = self.environment | {
            "FLOCK_LOG": str(flock_log),
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
            "REAL_FLOCK": shutil.which("flock", path="/usr/bin:/bin")
            or "/usr/bin/flock",
        }

        result = subprocess.run(
            [MAIL, "clear"],
            env=clear_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(flock_log.read_text().splitlines(), ["9"])
        self.assertEqual(self.capture_file.read_bytes(), b"")

    def test_capture_file_recreation_waits_for_the_delivery_lock(self) -> None:
        self.capture(b"Subject: Create lock\n\nbody\n")
        self.capture_file.unlink()
        with self.capture_lock.open("a+b") as lock_stream:
            fcntl.flock(lock_stream, fcntl.LOCK_EX)
            list_process = subprocess.Popen(
                [MAIL, "list"],
                env=self.environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            time.sleep(0.2)
            self.assertIsNone(list_process.poll())
            self.assertFalse(self.capture_file.exists())
            fcntl.flock(lock_stream, fcntl.LOCK_UN)

        stdout, stderr = list_process.communicate(timeout=5)
        self.assertEqual(list_process.returncode, 0, stderr.decode())
        self.assertEqual(stdout, b"No captured messages.\n")

    def test_oversized_raw_length_is_reported_without_a_traceback(self) -> None:
        self.capture_file.write_bytes(
            b"===== CARLOS DEV EMAIL CAPTURE v2 =====\n"
            b"Captured-At: now\n"
            b"Envelope-From: sender@example.test\n"
            b"Envelope-To: patient@example.test\n"
            b"Raw-Length: 999999999999999999999999999999999999\n\n"
        )

        result = subprocess.run(
            [INBOX, self.capture_file, "count"],
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 1)
        self.assertIn(b"Incomplete raw MIME payload", result.stderr)
        self.assertNotIn(b"Traceback", result.stderr)

    def test_status_preserves_the_postfix_service_exit_code(self) -> None:
        fake_binary_directory = self.capture_directory / "status-bin"
        fake_binary_directory.mkdir()
        stopped_service = fake_binary_directory / "service"
        stopped_service.write_text("#!/bin/sh\necho 'postfix is not running'\nexit 3\n")
        stopped_service.chmod(0o755)
        status_environment = self.environment | {
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
        }

        result = subprocess.run(
            [MAIL, "status"],
            env=status_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 3)
        self.assertIn(b"postfix is not running", result.stdout)
        self.assertIn(b"Capture file:", result.stdout)

    def test_status_recognizes_configured_effective_allowlist_path(self) -> None:
        self.send_allowlist.write_text("/^source-only@example\\.test$/ smtp:\n")
        self.effective_send_allowlist.write_text(
            "/^actually-active@example\\.test$/ smtp:\n"
        )
        fake_binary_directory = self.capture_directory / "send-status-bin"
        fake_binary_directory.mkdir()
        fake_service = fake_binary_directory / "service"
        fake_service.write_text("#!/bin/sh\nexit 0\n")
        fake_service.chmod(0o755)
        fake_postconf = fake_binary_directory / "postconf"
        fake_postconf.write_text(
            "#!/bin/sh\n"
            "if [ \"$1\" = -h ] && [ \"$2\" = transport_maps ]; then\n"
            "  printf 'regexp:%s, regexp:%s\\n' "
            "\"$CARLOS_MAIL_EFFECTIVE_SEND_ALLOWLIST\" "
            "\"$CARLOS_MAIL_CAPTURE_MAP\"\n"
            "fi\n"
        )
        fake_postconf.chmod(0o755)
        status_environment = self.environment | {
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
        }

        result = subprocess.run(
            [MAIL, "status"],
            env=status_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertIn(b"REAL SEND enabled", result.stdout)
        self.assertIn(b"actually-active@example", result.stdout)
        self.assertNotIn(b"source-only@example", result.stdout)

    def test_start_reloads_an_already_running_postfix_instance(self) -> None:
        self.capture_file.unlink()
        fake_binary_directory = self.capture_directory / "start-bin"
        fake_binary_directory.mkdir()
        service_log = self.capture_directory / "service.log"
        fake_service = fake_binary_directory / "service"
        fake_service.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$SERVICE_LOG\"\n"
        )
        fake_service.chmod(0o755)
        fake_postconf = fake_binary_directory / "postconf"
        fake_postconf.write_text("#!/bin/sh\nexit 0\n")
        fake_postconf.chmod(0o755)
        start_environment = self.environment | {
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
            "SERVICE_LOG": str(service_log),
        }

        result = subprocess.run(
            [MAIL, "start"],
            env=start_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(
            service_log.read_text().splitlines(),
            ["postfix start", "postfix reload"],
        )
        self.assertTrue(self.capture_file.is_file())
        self.assertEqual(stat.S_IMODE(self.capture_file.stat().st_mode), 0o660)

    def test_relayhost_is_applied_to_bare_smtp_allowlist_entries(self) -> None:
        self.send_allowlist.write_text(
            "/^relayed@example\\.test$/ smtp:\n"
            "/^direct@example\\.test$/ smtp:[direct.example.test]:2525\n"
        )
        fake_binary_directory = self.capture_directory / "relay-bin"
        fake_binary_directory.mkdir()
        postconf_log = self.capture_directory / "postconf.log"
        fake_postconf = fake_binary_directory / "postconf"
        fake_postconf.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$POSTCONF_LOG\"\n"
        )
        fake_postconf.chmod(0o755)
        fake_service = fake_binary_directory / "service"
        fake_service.write_text("#!/bin/sh\nexit 0\n")
        fake_service.chmod(0o755)
        start_environment = self.environment | {
            "CARLOS_MAIL_ALLOW_SEND": "1",
            "CARLOS_MAIL_RELAYHOST": "[relay.example.test]:587",
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
            "POSTCONF_LOG": str(postconf_log),
        }

        result = subprocess.run(
            [MAIL, "start"],
            env=start_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertEqual(
            self.effective_send_allowlist.read_text().splitlines(),
            [
                "/^relayed@example\\.test$/ smtp:[relay.example.test]:587",
                "/^direct@example\\.test$/ smtp:[direct.example.test]:2525",
            ],
        )
        self.assertIn(
            "transport_maps="
            f"regexp:{self.effective_send_allowlist}, regexp:{self.capture_map}",
            postconf_log.read_text(),
        )

    def test_read_holds_one_shared_lock_across_count_and_output(self) -> None:
        raw_message = b"Subject: Stable snapshot\n\nbody\n"
        self.capture(raw_message)
        fake_binary_directory = self.capture_directory / "flock-bin"
        fake_binary_directory.mkdir()
        flock_log = self.capture_directory / "flock.log"
        recording_flock = fake_binary_directory / "flock"
        recording_flock.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$FLOCK_LOG\"\n"
            "exec \"$REAL_FLOCK\" \"$@\"\n"
        )
        recording_flock.chmod(0o755)
        read_environment = self.environment | {
            "FLOCK_LOG": str(flock_log),
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
            "REAL_FLOCK": (
                shutil.which("flock", path="/usr/bin:/bin") or "/usr/bin/flock"
            ),
        }

        result = subprocess.run(
            [MAIL, "read", "latest"],
            env=read_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr.decode())
        self.assertIn(raw_message, result.stdout)
        self.assertEqual(flock_log.read_text().splitlines(), ["9", "-s 9"])

    def test_capture_failure_returns_postfix_tempfail(self) -> None:
        failure_environment = self.environment | {
            "CARLOS_MAIL_CAPTURE_FILE": "/dev/full",
        }
        result = subprocess.run(
            [WRITER, "sender@example.test", "patient@example.test"],
            input=b"Subject: Must retry\n\nbody\n",
            env=failure_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 75)
        self.assertIn(b"4.3.0 Temporary CARLOS development mail capture failure", result.stderr)

    def test_capture_failure_rolls_back_partial_record(self) -> None:
        original_message = b"Subject: Existing message\n\nexisting body\n"
        self.capture(original_message)
        original_capture = self.capture_file.read_bytes()
        fake_binary_directory = self.capture_directory / "fake-bin"
        fake_binary_directory.mkdir()
        truncate_lock_log = self.capture_directory / "truncate-lock.log"
        failing_chmod = fake_binary_directory / "chmod"
        failing_chmod.write_text("#!/bin/sh\nexit 1\n")
        failing_chmod.chmod(0o755)
        recording_truncate = fake_binary_directory / "truncate"
        recording_truncate.write_text(
            "#!/bin/sh\n"
            "if \"$REAL_FLOCK\" -n \"$CARLOS_MAIL_CAPTURE_LOCK\" true; then\n"
            "  echo unlocked >> \"$TRUNCATE_LOCK_LOG\"\n"
            "else\n"
            "  echo locked >> \"$TRUNCATE_LOCK_LOG\"\n"
            "fi\n"
            "exec \"$REAL_TRUNCATE\" \"$@\"\n"
        )
        recording_truncate.chmod(0o755)
        failure_environment = self.environment | {
            "PATH": f"{fake_binary_directory}:/usr/bin:/bin",
            "REAL_FLOCK": shutil.which("flock", path="/usr/bin:/bin")
            or "/usr/bin/flock",
            "REAL_TRUNCATE": shutil.which("truncate", path="/usr/bin:/bin")
            or "/usr/bin/truncate",
            "TRUNCATE_LOCK_LOG": str(truncate_lock_log),
        }

        result = subprocess.run(
            [WRITER, "sender@example.test", "patient@example.test"],
            input=b"Subject: Rolled back\n\nnew body\n",
            env=failure_environment,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 75)
        self.assertEqual(truncate_lock_log.read_text(), "locked\n")
        self.assertEqual(self.capture_file.read_bytes(), original_capture)
        self.assertEqual(self.inbox("count").stdout, b"1\n")
        self.assertIn(original_message, self.inbox("read", "1").stdout)


if __name__ == "__main__":
    unittest.main()
