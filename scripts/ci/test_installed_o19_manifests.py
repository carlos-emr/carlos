# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

import installed_o19_manifests as manifests

ASSETS = Path(__file__).resolve().parents[2] / 'debian/assets/o19-manifest'


class InstalledO19ManifestTest(unittest.TestCase):
    def test_current_packaged_headers_pass_the_real_command(self):
        result = subprocess.run([sys.executable, manifests.__file__, str(ASSETS)],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {'ready': True, 'problems': [], 'formats': {
            'o19map_schema.json': 2, 'o19map_props.json': 1, 'o19_preflight.json': 1}})

    def test_missing_corrupt_or_legacy_headers_cannot_qualify(self):
        invalid = [('o19map_schema.json', 'format', value) for value in [1, True, '2', 2.0, None]]
        invalid += [('o19map_props.json', 'format', True), ('o19map_props.json', 'format', 2),
                    ('o19_preflight.json', 'kind', 'o19map-schema')]
        for name, key, value in invalid:
            with self.subTest(name=name, key=key, value=value), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                for source in ASSETS.glob('*.json'): shutil.copy2(source, root / source.name)
                path = root / name; document = json.loads(path.read_text()); document[key] = value
                path.write_text(json.dumps(document))
                result = manifests.verify(root)
                self.assertFalse(result['ready'])
                self.assertEqual([row['file'] for row in result['problems']], [name])
        for content in ['{', '[]', '{"kind":"o19map-schema","format":1,"format":2}']:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                for source in ASSETS.glob('*.json'): shutil.copy2(source, root / source.name)
                (root / 'o19map_schema.json').write_text(content)
                self.assertFalse(manifests.verify(root)['ready'])

    def test_missing_manifests_return_failure_instead_of_silent_success(self):
        with tempfile.TemporaryDirectory() as temporary:
            result = subprocess.run([sys.executable, manifests.__file__, temporary],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 1, result.stderr)
            report = json.loads(result.stdout)
            self.assertFalse(report['ready'])
            self.assertEqual({row['file'] for row in report['problems']}, set(manifests.CONTRACTS))
