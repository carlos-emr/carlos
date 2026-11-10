# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class UpgradeManifestContractTest(unittest.TestCase):
    def setUp(self):
        script = (ROOT / 'scripts/deb-upgrade-verify.sh').read_text()
        programs = re.findall(r"<<'O19_PACKAGE_MANIFEST'\n(.*?)\nO19_PACKAGE_MANIFEST", script, re.S)
        self.assertEqual(len(programs), 1)
        self.program = programs[0]
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / 'manifest with spaces.json'

    def check(self, document, name, expected):
        self.path.write_text(json.dumps(document))
        result = subprocess.run([sys.executable, '-c', self.program, str(self.path), name],
                                capture_output=True, timeout=10)
        self.assertEqual(result.returncode, expected, result.stderr)
        self.assertEqual(result.stderr, b'', 'Contract refusal must not rely on an unrelated exception')

    def test_current_shipped_manifests_match_the_installed_cli_contract(self):
        paths = {path.stem: path for path in (ROOT / 'debian/assets/o19-manifest').glob('*.json')}
        self.assertEqual(set(paths), {'o19map_schema', 'o19map_props', 'o19_preflight'})
        for path in paths.values():
            with self.subTest(manifest=path.name):
                self.check(json.loads(path.read_text()), path.stem, 0)

    def test_schema_format_one_cannot_qualify_the_billing_helper_manifest(self):
        self.check({'kind': 'o19map-schema', 'format': 1}, 'o19map_schema', 1)

    def test_schema_format_two_does_not_change_other_envelopes(self):
        for name, kind in [('o19map_props', 'o19map-props'), ('o19_preflight', 'o19-preflight')]:
            with self.subTest(manifest=name):
                self.check({'kind': kind, 'format': 2}, name, 1)

    def test_boolean_string_float_and_missing_formats_cannot_qualify(self):
        for value in [True, False, '1', '2', 1.0, 2.0, None]:
            for name, kind in [('o19map_schema', 'o19map-schema'), ('o19map_props', 'o19map-props')]:
                with self.subTest(value=value, manifest=name):
                    self.check({'kind': kind, 'format': value}, name, 1)

    def test_unknown_formats_cannot_qualify(self):
        for value in [-1, 0, 3]:
            with self.subTest(value=value):
                self.check({'kind': 'o19map-schema', 'format': value}, 'o19map_schema', 1)

    def test_wrong_or_missing_kind_cannot_substitute_another_manifest(self):
        for kind in ['o19map-props', 'o19-preflight', '', None]:
            with self.subTest(kind=kind):
                self.check({'kind': kind, 'format': 2}, 'o19map_schema', 1)

    def test_nonobject_malformed_and_missing_files_cannot_qualify(self):
        for value in [[], None, 2, 'manifest']:
            with self.subTest(value=value):
                self.check(value, 'o19map_schema', 1)
        self.path.write_text('{incomplete')
        result = subprocess.run([sys.executable, '-c', self.program, str(self.path), 'o19map_schema'],
                                capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stderr, b'')
        self.path.unlink()
        result = subprocess.run([sys.executable, '-c', self.program, str(self.path), 'o19map_schema'],
                                capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stderr, b'')

    def test_unknown_manifest_name_cannot_qualify(self):
        self.check({'kind': 'o19map-schema', 'format': 2}, 'unregistered', 1)
