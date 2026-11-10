# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import unittest


class FlywayDownloadTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        content = b'#!/bin/sh\nprintf "fixture CLI\\n"\n'
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
            entry = tarfile.TarInfo('flyway-13.12.0/flyway')
            entry.size = len(content)
            entry.mode = 0o755
            archive.addfile(entry, io.BytesIO(content))
        self.payload = self.root / 'owned-distribution.tgz'
        self.payload.write_bytes(buffer.getvalue())
        self.script = self.root / 'fetch-flyway.sh'
        source = Path(__file__).with_name('fetch-flyway.sh').read_text()
        self.script.write_text(re.sub(r'checksum=[0-9a-f]{64}',
            'checksum=' + hashlib.sha256(buffer.getvalue()).hexdigest(), source))
        binary = self.root / 'bin'
        binary.mkdir()
        curl = binary / 'curl'
        curl.write_text('#!/usr/bin/env python3\nimport json,os,sys\nfrom pathlib import Path\n'
            'Path(os.environ["CAPTURE"]).write_text(json.dumps(sys.argv[1:]))\n'
            'Path(sys.argv[sys.argv.index("-o")+1]).write_bytes(Path(os.environ["PAYLOAD"]).read_bytes())\n'
            'sys.exit(int(os.environ.get("DOWNLOAD_EXIT","0")))\n')
        curl.chmod(0o755)
        self.capture = self.root / 'download.json'
        self.destination = self.root / 'fresh CLI'
        self.environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
            CAPTURE=str(self.capture), PAYLOAD=str(self.payload), DOWNLOAD_EXIT='0')

    def run_script(self, archive=None, **environment):
        command = ['bash', str(self.script), str(self.destination)]
        if archive is not None:
            command.append(str(archive))
        return subprocess.run(command,
            env={**self.environment, **environment}, capture_output=True, text=True, timeout=10)

    def test_verified_distribution_is_extracted_and_download_scratch_is_removed(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(os.access(self.destination / 'flyway', os.X_OK))
        self.assertIn('fixture CLI', (self.destination / 'flyway').read_text())
        arguments = json.loads(self.capture.read_text())
        self.assertFalse(Path(arguments[arguments.index('-o') + 1]).parent.exists())

    def test_corrupt_download_cannot_publish_a_cli(self):
        self.payload.write_bytes(b'partial unverified archive')
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('FAILED', result.stderr)
        self.assertFalse(self.destination.exists())

    def test_failed_download_propagates_its_status_without_publishing_partial_bytes(self):
        result = self.run_script(DOWNLOAD_EXIT='37')
        self.assertEqual(result.returncode, 37)
        self.assertFalse(self.destination.exists())

    def test_existing_or_dangling_destination_cannot_reuse_an_old_cli(self):
        for dangling in (False, True):
            with self.subTest(dangling=dangling):
                if dangling:
                    self.destination.symlink_to(self.root / 'missing')
                else:
                    self.destination.mkdir()
                    (self.destination / 'flyway').write_text('old CLI')
                result = self.run_script()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('destination must be fresh', result.stderr)
                self.assertFalse(self.capture.exists())
                if dangling:
                    self.destination.unlink()
                else:
                    self.assertEqual((self.destination / 'flyway').read_text(), 'old CLI')
                    shutil.rmtree(self.destination)

    def test_verified_cached_archive_is_used_without_another_download(self):
        result = self.run_script(archive=self.payload)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(os.access(self.destination / 'flyway', os.X_OK))
        self.assertFalse(self.capture.exists())
        self.assertTrue(self.payload.exists())

    def test_corrupt_cache_cannot_publish_a_cli(self):
        self.payload.write_bytes(b'corrupt cached archive')
        result = self.run_script(archive=self.payload)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('FAILED', result.stderr)
        self.assertFalse(self.destination.exists())
        self.assertFalse(self.capture.exists())

    def test_fresh_download_populates_the_cache_only_after_verification(self):
        cache = self.root / 'cached archive.tgz'
        result = self.run_script(archive=cache, GITHUB_RUN_ID='6102', GITHUB_RUN_ATTEMPT='2')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(cache.read_bytes(), self.payload.read_bytes())
        arguments = json.loads(self.capture.read_text())
        self.assertTrue(any('?download=1&run=6102-2' in argument for argument in arguments))

    def test_corrupt_download_is_not_added_to_the_cache(self):
        self.payload.write_bytes(b'partial unverified archive')
        cache = self.root / 'cached archive.tgz'
        result = self.run_script(archive=cache)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(cache.exists())
        self.assertFalse(self.destination.exists())
