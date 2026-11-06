#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Require real locked Chromium to stop after audit timeout and SIGTERM/SIGINT.

Needs npm ci and the locked Playwright Chromium installation. This integration
oracle intentionally stops Chromium so SIGTERM cannot finish it, exercising the
forced-kill path across Playwright's separate browser session. It uses no EMR.
"""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
from unittest.mock import patch

import process_group
from owned_process_tree import processes
import release_verification as gate

NODE = r'''
const fs=require('node:fs');
const {chromium}=require('playwright');
process.on('SIGTERM',()=>{});process.on('SIGINT',()=>{});
(async()=>{
  const server=await chromium.launchServer({headless:true,handleSIGTERM:false,handleSIGINT:false});
  const browser=await chromium.connect(server.wsEndpoint());
  const page=await browser.newPage();await page.setContent('<p>FAKE process ownership oracle</p>');
  process.kill(server.process().pid,'SIGSTOP');
  fs.writeFileSync(process.argv[1],JSON.stringify({node:process.pid,browser:server.process().pid,version:browser.version()}));
  if(process.argv[2]!=='timeout')setTimeout(()=>process.kill(process.ppid,process.argv[2]),100);
  setInterval(()=>{},1000);
})().catch(()=>{process.exitCode=1;});
'''


def main():
    for mode in ('timeout', 'SIGTERM', 'SIGINT'):
        with tempfile.TemporaryDirectory(prefix='carlos-browser-process-') as temporary:
            ready = Path(temporary) / 'ready.json'
            observed = {}
            original = subprocess.Popen
            class ReadyProcess(original):
                def wait(self, timeout=None):
                    if not observed:
                        deadline = time.monotonic() + 30
                        while not ready.exists() and self.poll() is None and time.monotonic() < deadline:
                            time.sleep(0.02)
                        if not ready.exists():
                            raise OSError('Locked browser did not become ready')
                        observed.update(json.loads(ready.read_text()))
                        observed['pids'] = processes()
                        observed['separateGroup'] = os.getpgid(observed['browser']) != os.getpgid(observed['node'])
                    return super().wait(timeout=timeout)
            with patch.object(process_group.subprocess, 'Popen', ReadyProcess):
                try:
                    process_group.run_process_group(['node', '-e', NODE, str(ready), mode], cwd=gate.ROOT,
                        timeout=0.2 if mode == 'timeout' else 30, grace=0.2, track_descendants=True)
                except process_group.ProcessGroupFailure as failure:
                    if not failure.quiescent or ('timed out' if mode == 'timeout' else 'interrupted') not in str(failure):
                        raise
                else:
                    raise AssertionError('Browser interruption was accepted as success')
            gate.require_locked_chromium([observed['version']])
            assert observed['separateGroup'], 'Oracle did not exercise a detached browser'
            owned = {observed['node'], observed['browser']}
            while True:
                children = {pid for pid, row in observed['pids'].items() if row[0] in owned} - owned
                if not children: break
                owned |= children
            remaining = processes()
            assert all(pid not in remaining or remaining[pid][2] != observed['pids'][pid][2]
                       or remaining[pid][1] in ('Z', 'X') for pid in owned), 'Owned browser processes survived'
            print(json.dumps({'mode': mode, 'chromium': observed['version'], 'separateBrowserGroup': True,
                              'forcedKill': True, 'processesStopped': len(owned), 'restorationVerified': False}))


if __name__ == '__main__':
    main()
