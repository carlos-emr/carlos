#!/usr/bin/env python3
"""Regression tests for the devcontainer mail-capture protocol."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import fcntl
import os
from pathlib import Path
import pwd
import grp
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

    def test_clear_waits_for_the_delivery_lock(self) -> None:
        self.capture(b"Subject: Locked\n\nbody\n")
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


if __name__ == "__main__":
    unittest.main()
