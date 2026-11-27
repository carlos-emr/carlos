"""Regression tests for preserving the genuine OSCAR19 dump completion record."""
import importlib.util
import io
from pathlib import Path
import unittest
from unittest.mock import patch

module_spec = importlib.util.spec_from_file_location(
    "float_export", Path(__file__).resolve().parents[1] / "export-medication-floats.py")
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
