# SPDX-License-Identifier: GPL-2.0-or-later
"""The export source-catalogue gate must refuse drift even when Python strips asserts."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class OptimizedCatalogueGateTest(unittest.TestCase):
    def _tree(self, directory):
        target = Path(directory)
        (target / 'scripts').mkdir()
        shutil.copy2(ROOT / 'scripts/verify-cds-export-source-catalogue.py', target / 'scripts')
        shutil.copytree(ROOT / 'src/main/resources/cds-export', target / 'src/main/resources/cds-export')
        shutil.copytree(ROOT / 'database/mysql/schema-contract', target / 'database/mysql/schema-contract')
        return target

    def _run(self, tree):
        environment = dict(os.environ, PYTHONOPTIMIZE='1')
        return subprocess.run([sys.executable, '-O', str(tree / 'scripts/verify-cds-export-source-catalogue.py')],
                              capture_output=True, text=True, env=environment, check=False)

    def test_accepts_the_shipped_catalogue_under_python_optimize(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self._run(self._tree(directory))
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('explicit table rulings', result.stdout)

    def test_refuses_a_missing_table_ruling_under_python_optimize(self):
        with tempfile.TemporaryDirectory() as directory:
            tree = self._tree(directory)
            path = tree / 'src/main/resources/cds-export/source-catalogue.json'
            catalogue = json.loads(path.read_text())
            catalogue['tables'].pop(next(iter(catalogue['tables'])))
            path.write_text(json.dumps(catalogue, indent=2) + '\n')
            result = self._run(tree)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Every physical table requires its own explicit ruling', result.stderr)


if __name__ == '__main__':
    unittest.main()
