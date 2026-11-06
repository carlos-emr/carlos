#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Require real locked Chromium to stop after audit timeout and SIGTERM/SIGINT.

Needs npm ci and the locked Playwright Chromium installation. This integration
oracle intentionally stops Chromium so SIGTERM cannot finish it, exercising the
forced-kill path across Playwright's separate browser session. It uses no EMR.
"""
import argparse
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
from junit_evidence import EvidenceError
import release_verification as gate

NODE = r'''
const fs=require('node:fs');
const {chromium}=require('playwright');
process.on('SIGTERM',()=>{});process.on('SIGINT',()=>{});
(async()=>{
  const server=await chromium.launchServer({headless:true,handleSIGTERM:false,handleSIGINT:false,
    ...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
  const browser=await chromium.connect(server.wsEndpoint());
  const page=await browser.newPage();await page.setContent('<p>FAKE process ownership oracle</p>');
  process.kill(server.process().pid,'SIGSTOP');
  fs.writeFileSync(process.argv[1],JSON.stringify({node:process.pid,browser:server.process().pid,version:browser.version()}));
  if(process.argv[2]!=='timeout'){
    const armed=setInterval(()=>{if(fs.existsSync(process.argv[3])){
      clearInterval(armed);process.kill(process.ppid,process.argv[2]);
    }},10);
  }
  setInterval(()=>{},1000);
})().catch(()=>{process.exitCode=1;});
'''


def validate_stopped(observed, remaining):
    if observed.get('separateGroup') is not True:
        raise EvidenceError('Oracle did not exercise a detached browser')
    owned = {observed['node'], observed['browser']}
    if not owned <= observed['pids'].keys():
        raise EvidenceError('Oracle missed the owned process inventory')
    while True:
        children = {pid for pid, row in observed['pids'].items() if row[0] in owned} - owned
        if not children: break
        owned |= children
    if any(pid in remaining and remaining[pid][2] == observed['pids'][pid][2]
           and remaining[pid][1] not in ('Z', 'X') for pid in owned):
        raise EvidenceError('Owned browser processes survived')
    return len(owned)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    rows = []
    for mode in ('timeout', 'SIGTERM', 'SIGINT'):
        with tempfile.TemporaryDirectory(prefix='carlos-browser-process-') as temporary:
            ready = Path(temporary) / 'ready.json'
            armed = Path(temporary) / 'armed'
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
                        armed.write_text('parent-observations-complete')
                    return super().wait(timeout=timeout)
            with patch.object(process_group.subprocess, 'Popen', ReadyProcess):
                try:
                    process_group.run_process_group(['node', '-e', NODE, str(ready), mode, str(armed)], cwd=gate.ROOT,
                        timeout=0.2 if mode == 'timeout' else 30, grace=0.2, track_descendants=True)
                except process_group.ProcessGroupFailure as failure:
                    if not failure.quiescent or ('timed out' if mode == 'timeout' else 'interrupted') not in str(failure):
                        raise
                else:
                    raise AssertionError('Browser interruption was accepted as success')
            gate.require_locked_chromium([observed['version']])
            stopped = validate_stopped(observed, processes())
            row = {'mode': mode, 'chromium': observed['version'], 'separateBrowserGroup': True,
                   'forcedKill': True, 'processesStopped': stopped, 'restorationVerified': False}
            rows.append(row)
            print(json.dumps(row))
    if args.output:
        observations = args.output.parent / 'browser-process-observations.json'
        gate.write_json(observations, {'schemaVersion': 1, 'observations': rows})
        gate.write_json(args.output, {'schemaVersion': 1, 'assertions': {
            'timeout-browser-stopped': any(row['mode'] == 'timeout' for row in rows),
            'sigterm-browser-stopped': any(row['mode'] == 'SIGTERM' for row in rows),
            'sigint-browser-stopped': any(row['mode'] == 'SIGINT' for row in rows)},
            'inputs': [{'path': observations.name, 'sha256': gate.digest(observations)}]})


if __name__ == '__main__':
    main()
