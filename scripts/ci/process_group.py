# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Bound an owned subprocess tree, including children outliving their parent."""
import os
from pathlib import Path
import signal
import subprocess
import time

from junit_evidence import EvidenceError
from owned_process_tree import OwnedProcessTree


class ProcessGroupFailure(EvidenceError):
    def __init__(self, reason, quiescent, *, interrupted=False):
        super().__init__(reason)
        self.quiescent = quiescent
        self.interrupted = interrupted


def group_alive(group):
    # Zombies cannot mutate the fixture. Linux /proc also lets us distinguish
    # them from living grandchildren after the direct child has been reaped.
    if Path('/proc/self/stat').is_file():
        for path in Path('/proc').glob('[0-9]*/stat'):
            try:
                fields = path.read_text().rsplit(')', 1)[1].split()
                if int(fields[2]) == group and fields[0] not in ('Z', 'X'):
                    return True
            except (FileNotFoundError, ProcessLookupError):
                continue
        return False
    try:
        os.killpg(group, 0)
        return True
    except ProcessLookupError:
        return False


def stop_group(process, grace):
    for signum, seconds in ((signal.SIGTERM, grace), (signal.SIGKILL, 2)):
        try:
            os.killpg(process.pid, signum)
        except ProcessLookupError:
            pass
        deadline = time.monotonic() + seconds
        while True:
            process.poll()  # Reap the direct child even when descendants remain.
            if not group_alive(process.pid):
                process.wait(timeout=2)
                return True
            if time.monotonic() >= deadline:
                break
            time.sleep(0.05)
    return False


def stop_tree(process, tree, grace):
    for signum, seconds in ((signal.SIGTERM, grace), (signal.SIGKILL, 2)):
        sent = set()
        deadline = time.monotonic() + seconds
        while True:
            process.poll()
            children = tree.children(process.pid)
            if not children:
                process.wait(timeout=2)
                return True
            for pid, started in children.items():
                if (pid, started) not in sent:
                    tree.send(pid, started, signum)
                    sent.add((pid, started))
            if time.monotonic() >= deadline:
                break
            time.sleep(0.05)
    return False


def run_process_group(command, *, timeout, grace=10, track_descendants=False, **kwargs):
    """Bound a command; browser callers also own detached descendants on Linux."""
    pending = False
    starting = True
    def interrupted(_signum, _frame):
        nonlocal pending
        pending = True
        # Popen may have forked without returning its handle. Defer until it does.
        if not starting:
            raise KeyboardInterrupt
    previous = {number: signal.signal(number, interrupted) for number in (signal.SIGINT, signal.SIGTERM)}
    process = None
    tree = None
    try:
        try:
            if track_descendants:
                tree = OwnedProcessTree()
            process = subprocess.Popen(command, start_new_session=True, **kwargs)
            starting = False
            if pending:
                raise KeyboardInterrupt
            code = process.wait(timeout=timeout)
            alive = tree.children(process.pid) if tree else group_alive(process.pid)
            if alive:
                raise ProcessGroupFailure('Child processes outlived their owner', False)
            return subprocess.CompletedProcess(command, code)
        except (subprocess.TimeoutExpired, KeyboardInterrupt, ProcessGroupFailure, OSError) as failure:
            # Do not let a second signal interrupt the bounded cleanup window.
            old_int = signal.signal(signal.SIGINT, signal.SIG_IGN)
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            try:
                try:
                    quiescent = (stop_tree(process, tree, grace) if tree else stop_group(process, grace)) if process else False
                except (OSError, subprocess.TimeoutExpired):
                    quiescent = False
            finally:
                signal.signal(signal.SIGINT, old_int)
            reason = ('Process group timed out' if isinstance(failure, subprocess.TimeoutExpired)
                      else 'Process group interrupted' if isinstance(failure, KeyboardInterrupt)
                      else 'Process group state could not be established' if isinstance(failure, OSError)
                      else str(failure))
            raise ProcessGroupFailure(reason + '; fixture restoration is unverified', quiescent,
                                      interrupted=isinstance(failure, KeyboardInterrupt)) from failure
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)
        if tree:
            try:
                tree.close()
            except OSError as failure:
                raise ProcessGroupFailure('Child supervision could not be restored; fixture restoration is unverified', False) from failure
