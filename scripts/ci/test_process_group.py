# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from process_group import ProcessGroupFailure, group_alive, run_process_group


class ProcessGroupTest(unittest.TestCase):
    def test_cleanup_os_error_remains_a_failure_with_unverified_quiescence(self):
        child = Mock(pid=123456)
        child.wait.side_effect = subprocess.TimeoutExpired(['synthetic'], 1)
        with patch('process_group.subprocess.Popen', return_value=child), \
                patch('process_group.stop_group', side_effect=PermissionError('synthetic cleanup failure')):
            with self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group(['synthetic'], timeout=1)
        self.assertFalse(failed.exception.quiescent)
        self.assertIn('restoration is unverified', str(failed.exception))

    def test_timeout_stops_resistant_grandchild_after_its_parent_exits_on_term(self):
        with tempfile.TemporaryDirectory() as temporary:
            ready = Path(temporary) / 'ready'
            grandchild = ('import pathlib,signal,sys,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); '
                          'pathlib.Path(sys.argv[1]).write_text("ready"); time.sleep(60)')
            script = ("import os,pathlib,signal,subprocess,sys,time\n"
                "signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))\n"
                "subprocess.Popen([sys.executable,'-c'," + repr(grandchild) + ",sys.argv[1]])\n"
                "time.sleep(60)\n")
            original = subprocess.Popen
            launched = []
            def launch(*args, **kwargs):
                child = original(*args, **kwargs)
                launched.append(child)
                deadline = time.monotonic() + 15
                while not ready.exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                if not ready.exists():
                    os.killpg(child.pid, signal.SIGKILL); child.wait()
                    self.fail('Grandchild did not acknowledge its installed signal handler')
                return child
            with patch('process_group.subprocess.Popen', side_effect=launch), self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group([sys.executable, '-c', script, str(ready)], timeout=0.1, grace=0.1)
            self.assertTrue(failed.exception.quiescent)
            self.assertIn('timed out', str(failed.exception))
            self.assertFalse(group_alive(launched[0].pid))

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

    def test_signals_during_popen_are_deferred_until_the_child_handle_is_available(self):
        original = subprocess.Popen
        for signum in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signal=signum):
                launched = []
                def launch(*args, **kwargs):
                    child = original(*args, **kwargs)
                    launched.append(child)
                    os.kill(os.getpid(), signum)
                    return child
                with patch('process_group.subprocess.Popen', side_effect=launch), self.assertRaises(ProcessGroupFailure) as failed:
                    run_process_group([sys.executable, '-c', 'import time; time.sleep(60)'], timeout=30, grace=0.1)
                self.assertTrue(failed.exception.quiescent)
                self.assertIn('interrupted', str(failed.exception))
                self.assertFalse(group_alive(launched[0].pid))

    def test_detached_orphan_is_stopped_after_its_parent_exits(self):
        with tempfile.TemporaryDirectory() as temporary:
            pid = Path(temporary) / 'detached'
            child = ("import os,pathlib,signal,sys,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); "
                     "pathlib.Path(sys.argv[1]).write_text(str(os.getpid())); time.sleep(60)")
            owner = ("import pathlib,subprocess,sys,time\n"
                     "subprocess.Popen([sys.executable,'-c',sys.argv[1],sys.argv[2]],start_new_session=True)\n"
                     "deadline=time.monotonic()+10\n"
                     "while not pathlib.Path(sys.argv[2]).exists() and time.monotonic()<deadline: time.sleep(.01)\n")
            with self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group([sys.executable, '-c', owner, child, str(pid)], timeout=15,
                                  grace=0.1, track_descendants=True)
            self.assertTrue(failed.exception.quiescent)
            self.assertFalse(group_alive(int(pid.read_text())))

    def test_existing_child_tree_is_refused_before_adoption_or_browser_start(self):
        parent = subprocess.Popen([sys.executable, '-c',
            "import subprocess,sys; child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
            "print(child.pid,flush=True); sys.stdin.readline()"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        descendant = int(parent.stdout.readline())
        try:
            with patch('process_group.subprocess.Popen') as launch, self.assertRaises(ProcessGroupFailure) as failed:
                run_process_group(['must-not-run'], timeout=1, track_descendants=True)
            launch.assert_not_called()
            self.assertFalse(failed.exception.quiescent)
            # Even after its original parent exits, the unrelated child survives.
            parent.stdin.write('\n'); parent.stdin.flush(); parent.wait(timeout=5)
            os.kill(descendant, 0)
        finally:
            parent.stdin.close(); parent.stdout.close()
            if parent.poll() is None: parent.kill(); parent.wait()
            os.kill(descendant, signal.SIGKILL)
