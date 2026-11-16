# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Candidate builder guards before expensive builds and before publishing bytes."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("build_release_war", Path(__file__).with_name("build-release-war.py"))
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


class CandidateWarBuildTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name) / "candidate.war"

    def build(self, version="2026.08.0~test1", count=1):
        def command(argv, **options):
            if argv[0] == "mvn":
                target = options["cwd"] / "target"
                target.mkdir(parents=True)
                for number in range(count):
                    (target / f"candidate-{number}.war").write_bytes(b"synthetic candidate bytes")
        with patch.object(builder.subprocess, "run", side_effect=command) as run, patch.object(
                builder.subprocess, "check_output", return_value="a" * 40):
            builder.main(["--output", str(self.output), "--package-version", version])
        return run

    def test_invalid_versions_are_refused_before_starting_any_subprocess(self):
        for version in ("", "-1", "1/../../other", "1\n2", "1;echo injected", "1" * 129):
            with self.subTest(version=version), patch.object(builder.subprocess, "run") as run, patch.object(
                    builder.subprocess, "check_output") as revision:
                with self.assertRaisesRegex(SystemExit, "Debian version characters"):
                    builder.main(["--output", str(self.output), "--package-version", version])
                run.assert_not_called()
                revision.assert_not_called()
                self.assertFalse(self.output.exists())

    def test_existing_output_is_preserved_without_building(self):
        self.output.write_bytes(b"existing candidate")
        with patch.object(builder.subprocess, "check_output") as revision:
            with self.assertRaisesRegex(SystemExit, "must not already exist"):
                self.build()
            revision.assert_not_called()
        self.assertEqual(self.output.read_bytes(), b"existing candidate")

    def test_zero_or_multiple_wars_are_refused_before_publishing(self):
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaisesRegex(SystemExit, "exactly one"):
                self.build(count=count)
            self.assertFalse(self.output.exists())

    def test_one_war_is_stamped_and_published_with_the_validated_version(self):
        run = self.build()
        stamp = run.call_args_list[-1]
        self.assertEqual(stamp.args[0][-2:], ["carlos-emr-deb", "2026.08.0~test1"])
        self.assertEqual(self.output.read_bytes(), b"synthetic candidate bytes")


if __name__ == "__main__":
    unittest.main()
