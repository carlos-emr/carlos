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
        if os.geteuid() == 0:
            writer_account = pwd.getpwnam("nobody")
            self.writer_uid = writer_account.pw_uid
            self.writer_gid = writer_account.pw_gid
            os.chown(self.capture_directory, self.writer_uid, self.writer_gid)
            self.capture_directory.chmod(0o770)
        else:
            self.writer_uid = os.getuid()
            self.writer_gid = os.getgid()
        self.environment = os.environ | {
            "CARLOS_MAIL_CAPTURE_DIR": str(self.capture_directory),
            "CARLOS_MAIL_CAPTURE_FILE": str(self.capture_file),
            "CARLOS_MAIL_CAPTURE_LOCK": str(self.capture_lock),
            "CARLOS_MAIL_CAPTURE_INBOX": str(INBOX),
            "CARLOS_MAIL_CAPTURE_USER": pwd.getpwuid(self.writer_uid).pw_name,
            "CARLOS_MAIL_CAPTURE_GROUP": grp.getgrgid(self.writer_gid).gr_name,
        }

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def capture(self, raw_message: bytes, recipient: str = "patient@example.test") -> None:
        def use_delivery_identity() -> None:
            os.setgid(self.writer_gid)
            os.setuid(self.writer_uid)

        subprocess.run(
            [WRITER, "sender@example.test", recipient],
            input=raw_message,
            env=self.environment,
            preexec_fn=use_delivery_identity if os.geteuid() == 0 else None,
            check=True,
        )

    def inbox(self, *arguments: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            [INBOX, self.capture_file, *arguments],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )

    def mail(self, *arguments: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            [MAIL, *arguments],
            env=self.environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
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

    def test_delivery_recreates_deleted_capture_file_with_restricted_mode(self) -> None:
        self.capture(b"Subject: Before deletion\n\nbody\n")
        self.capture_file.unlink()

        replacement = b"Subject: After deletion\n\nreplacement body\n"
        self.capture(replacement)

        self.assertEqual(self.inbox("count").stdout, b"1\n")
        self.assertIn(replacement, self.inbox("read", "1").stdout)
        self.assertEqual(stat.S_IMODE(self.capture_file.stat().st_mode), 0o660)

    def test_concurrent_deliveries_remain_complete_records(self) -> None:
        def deliver(index: int) -> subprocess.CompletedProcess[bytes]:
            raw_message = (
                f"Subject: Concurrent {index}\n\n".encode()
                + f"payload-{index}-".encode() * 10_000
            )
            return subprocess.run(
                [WRITER, "sender@example.test", f"patient-{index}@example.test"],
                input=raw_message,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=self.environment,
                check=False,
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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
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
        orphan_spool = self.capture_directory / ".raw-message.orphaned"
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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual(result.returncode, 3)
        self.assertIn(b"postfix is not running", result.stdout)
        self.assertIn(b"Capture file:", result.stdout)

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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
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
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

        self.assertEqual(result.returncode, 75)
        self.assertEqual(truncate_lock_log.read_text(), "locked\n")
        self.assertEqual(self.capture_file.read_bytes(), original_capture)
        self.assertEqual(self.inbox("count").stdout, b"1\n")
        self.assertIn(original_message, self.inbox("read", "1").stdout)


if __name__ == "__main__":
    unittest.main()
