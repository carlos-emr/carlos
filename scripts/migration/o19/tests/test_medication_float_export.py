"""Regression tests for preserving the genuine OSCAR19 dump completion record and importer gating."""
import importlib.util
import io
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).resolve().parents[1]
module_spec = importlib.util.spec_from_file_location(
    "float_export", SCRIPT_DIR / "export-medication-floats.py")
float_export = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(float_export)


class DumpCompletionTest(unittest.TestCase):
    def test_a_missing_completion_record_stops_before_reading_clinical_values(self):
        source = io.BytesIO(b"-- Dump completed on a previous section\nINSERT INTO drugs VALUES (1);\n")
        with patch.object(float_export, "export") as exporter:
            with self.assertRaisesRegex(ValueError, "completion record"):
                float_export.complete_dump(source, io.BytesIO(), [])
            exporter.assert_not_called()

    def test_long_binary_dump_preserves_the_original_trailer_after_the_supplement(self):
        body = b"-- SQL\n" + b"x" * (1024 * 1024 + 4097) + b"\xff;\n"
        trailer = b"-- Dump completed on 2026-01-31 20:00:00\r\n"
        output = io.BytesIO()
        with patch.object(float_export, "export", side_effect=lambda _, writer: writer.write("-- values\n")):
            float_export.complete_dump(io.BytesIO(body + trailer), output, [])
        self.assertEqual(output.getvalue(), body + b"-- values\n" + trailer)


class FixtureImporterGateTest(unittest.TestCase):
    def _make_oscar_stub(self, root: Path) -> Path:
        sql_dir = root / "oscar" / "database" / "mysql"
        sql_dir.mkdir(parents=True)
        (sql_dir / "oscarinit.sql").write_text("-- stub\n", encoding="utf-8")
        (sql_dir / "oscarinit_on.sql").write_text("-- stub\n", encoding="utf-8")
        return root / "oscar"

    def _make_mysql_stub(self, root: Path, marker: Path) -> Path:
        stub = root / "fake-mariadb"
        stub.write_text(f"#!/usr/bin/env bash\necho called > '{marker}'\nexit 99\n", encoding="utf-8")
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
        return stub

    def _run_fixture(self, oscar_src: Path, out_dir: Path, mysql_cmd: Path, ctl_dir: Path):
        env = dict(os.environ)
        env.pop("CARLOS_CTL_SRC", None)
        return subprocess.run(
            [
                "bash",
                str(SCRIPT_DIR / "build-o19-fixture.sh"),
                "--oscar-src", str(oscar_src),
                "--out", str(out_dir),
                "--carlos-ctl-dir", str(ctl_dir),
                "--mysql-cmd", str(mysql_cmd),
            ],
            capture_output=True,
            text=True,
            env=env,
            check=False,
        )

    def test_refuses_incompatible_format3_importer_before_touching_db_or_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            oscar_src = self._make_oscar_stub(root)
            out_dir = root / "bundle"
            marker = root / "mysql-called"
            mysql_stub = self._make_mysql_stub(root, marker)
            ctl = root / "ctl-136" / "carlos_ctl"
            ctl.mkdir(parents=True)
            (ctl / "o19digest.py").write_text("DIGEST_FORMAT = 3\n", encoding="utf-8")
            (ctl / "o19sources.py").write_text("def inventory():\n    pass\n", encoding="utf-8")

            result = self._run_fixture(oscar_src, out_dir, mysql_stub, root / "ctl-136")

            self.assertEqual(result.returncode, 1)
            self.assertIn("#6420", result.stderr)
            self.assertIn("DIGEST_FORMAT=3", result.stderr)
            self.assertFalse(out_dir.exists())
            self.assertFalse(marker.exists())

    def test_refuses_missing_importer_before_touching_db_or_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            oscar_src = self._make_oscar_stub(root)
            out_dir = root / "bundle"
            marker = root / "mysql-called"
            mysql_stub = self._make_mysql_stub(root, marker)

            result = self._run_fixture(oscar_src, out_dir, mysql_stub, root / "does-not-exist")

            self.assertEqual(result.returncode, 1)
            self.assertIn("no compatible carlos-ctl importer found", result.stderr)
            self.assertFalse(out_dir.exists())
            self.assertFalse(marker.exists())

    def test_passes_gate_for_format4_importer_with_float_supplement_support(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            oscar_src = self._make_oscar_stub(root)
            out_dir = root / "bundle"
            marker = root / "mysql-called"
            mysql_stub = self._make_mysql_stub(root, marker)
            ctl = root / "ctl-next" / "carlos_ctl"
            ctl.mkdir(parents=True)
            (ctl / "o19digest.py").write_text("DIGEST_FORMAT = 4\n", encoding="utf-8")
            (ctl / "o19sources.py").write_text("def medication_float_table(statement):\n    return None\n", encoding="utf-8")

            result = self._run_fixture(oscar_src, out_dir, mysql_stub, root / "ctl-next")

            self.assertEqual(result.returncode, 99)
            self.assertTrue(marker.exists())

