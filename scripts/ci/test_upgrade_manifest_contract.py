# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import json
from pathlib import Path
import tempfile
import unittest

from installed_o19_manifests import verify_manifest


ROOT = Path(__file__).resolve().parents[2]


class UpgradeManifestContractTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / 'manifest with spaces.json'

    def check(self, document, name, expected):
        self.path.write_text(json.dumps(document))
        self.assertEqual(0 if verify_manifest(self.path, name + '.json') is None else 1, expected)

    def test_current_shipped_manifests_match_the_installed_cli_contract(self):
        paths = {path.stem: path for path in (ROOT / 'debian/assets/o19-manifest').glob('*.json')}
        self.assertEqual(set(paths), {'o19map_schema', 'o19map_props', 'o19_preflight', 'stock-eform-assets'})
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
        for value in [True, False, '1', '2', '3', 1.0, 2.0, 3.0, None]:
            for name, kind in [('o19map_schema', 'o19map-schema'), ('o19map_props', 'o19map-props')]:
                with self.subTest(value=value, manifest=name):
                    self.check({'kind': kind, 'format': value}, name, 1)

    def test_unknown_formats_cannot_qualify(self):
        # 2 is the superseded schema envelope; 4 is not defined.
        for value in [-1, 0, 2, 4]:
            with self.subTest(value=value):
                self.check({'kind': 'o19map-schema', 'format': value}, 'o19map_schema', 1)

    def test_wrong_or_missing_kind_cannot_substitute_another_manifest(self):
        for kind in ['o19map-props', 'o19-preflight', '', None]:
            with self.subTest(kind=kind):
                self.check({'kind': kind, 'format': 3}, 'o19map_schema', 1)

    def test_nonobject_malformed_and_missing_files_cannot_qualify(self):
        for value in [[], None, 2, 'manifest']:
            with self.subTest(value=value):
                self.check(value, 'o19map_schema', 1)
        self.path.write_text('{incomplete')
        self.assertIsNotNone(verify_manifest(self.path, 'o19map_schema.json'))
        self.path.unlink()
        self.assertIsNotNone(verify_manifest(self.path, 'o19map_schema.json'))

    def test_unknown_manifest_name_cannot_qualify(self):
        self.check({'kind': 'o19map-schema', 'format': 2}, 'unregistered', 1)
