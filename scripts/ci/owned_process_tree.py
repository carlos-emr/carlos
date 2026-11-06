# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Linux subreaper for one serialized browser command, including detached sessions.

The caller must not launch unrelated children concurrently. Existing children and
their descendants are excluded. Do not use this for package installation: service
daemons intentionally survive their installer.
"""
import ctypes
import os
from pathlib import Path
import signal


def processes():
    rows = {}
    for path in Path('/proc').glob('[0-9]*/stat'):
        try:
            fields = path.read_text().rsplit(')', 1)[1].split()
            rows[int(path.parent.name)] = (int(fields[1]), fields[0], int(fields[19]))
        except (FileNotFoundError, ProcessLookupError):
            continue
    return rows


class OwnedProcessTree:
    def __init__(self):
        self.owner = os.getpid()
        self.libc = ctypes.CDLL(None, use_errno=True)
        self.previous = ctypes.c_int()
        if self.libc.prctl(37, ctypes.byref(self.previous), 0, 0, 0):
            raise OSError(ctypes.get_errno(), 'Cannot inspect child subreaper')
        self.excluded = {pid: row[2] for pid, row in processes().items() if row[0] == self.owner}
        if self.libc.prctl(36, 1, 0, 0, 0):
            raise OSError(ctypes.get_errno(), 'Cannot establish child subreaper')

    def close(self):
        if self.libc.prctl(36, self.previous.value, 0, 0, 0):
            raise OSError(ctypes.get_errno(), 'Cannot restore child subreaper')

    def children(self, direct_pid):
        rows = processes()
        owned = {pid for pid, row in rows.items() if row[0] == self.owner
                 and self.excluded.get(pid) != row[2]}
        while True:
            added = {pid for pid, row in rows.items() if row[0] in owned} - owned
            if not added:
                break
            owned |= added
        live = {}
        for pid in owned:
            parent, state, started = rows[pid]
            if state not in ('Z', 'X'):
                live[pid] = started
            elif parent == self.owner and pid != direct_pid:
                # Reap adopted children only; Popen owns its direct child's status.
                try:
                    os.waitpid(pid, os.WNOHANG)
                except ChildProcessError:
                    pass
        return live

    @staticmethod
    def send(pid, started, signum):
        # A pidfd plus start-time check prevents signalling a reused PID.
        try:
            descriptor = os.pidfd_open(pid)
        except ProcessLookupError:
            return
        try:
            current = processes().get(pid)
            if current is not None and current[2] == started:
                signal.pidfd_send_signal(descriptor, signum)
        except ProcessLookupError:
            pass
        finally:
            os.close(descriptor)
