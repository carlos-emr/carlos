# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

from process_group import ProcessGroupFailure, group_alive, run_process_group


class ProcessGroupTest(unittest.TestCase):
    def test_timeout_stops_resistant_grandchild_after_its_parent_exits_on_term(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid = Path(temporary) / 'group'
            script = ("import os,pathlib,signal,subprocess,sys,time\n"
                "signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))\n"
                "subprocess.Popen([sys.executable,'-c',"
                "'import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(60)'])\n"
                "pathlib.Path(sys.argv[1]).write_text(str(os.getpgrp()))\ntime.sleep(60)\n")
            with self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group([sys.executable, '-c', script, str(pid)], timeout=1, grace=0.1)
            self.assertTrue(failed.exception.quiescent)
            self.assertIn('timed out', str(failed.exception))
            self.assertFalse(group_alive(int(pid.read_text())))

    def test_successful_parent_cannot_leave_a_mutating_child_running(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid = Path(temporary) / 'group'
            script = ("import os,pathlib,subprocess,sys\n"
                "subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)'])\n"
                "pathlib.Path(sys.argv[1]).write_text(str(os.getpgrp()))\n")
            with self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group([sys.executable, '-c', script, str(pid)], timeout=5, grace=0.1)
            self.assertTrue(failed.exception.quiescent)
            self.assertFalse(group_alive(int(pid.read_text())))

    def test_outer_termination_is_forwarded_to_the_separate_child_session(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid = Path(temporary) / 'group'
            child = ("import os,pathlib,signal,sys,time; "
                "signal.signal(signal.SIGTERM,signal.SIG_IGN); "
                "pathlib.Path(sys.argv[1]).write_text(str(os.getpgrp())); time.sleep(60)")
            wrapper = ("import sys\nfrom process_group import run_process_group,ProcessGroupFailure\n"
                "try: run_process_group([sys.executable,'-c',sys.argv[1],sys.argv[2]],timeout=60,grace=0.1)\n"
                "except ProcessGroupFailure as failure: sys.exit(7 if failure.quiescent else 8)\n")
            process = subprocess.Popen([sys.executable, '-c', wrapper, child, str(pid)],
                                       cwd=Path(__file__).parent)
            try:
                deadline = time.monotonic() + 5
                while not pid.exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue(pid.exists())
                process.send_signal(signal.SIGTERM)
                self.assertEqual(process.wait(timeout=5), 7)
                self.assertFalse(group_alive(int(pid.read_text())))
            finally:
                if process.poll() is None:
                    process.kill(); process.wait()
                if pid.exists() and group_alive(int(pid.read_text())):
                    os.killpg(int(pid.read_text()), signal.SIGKILL)
