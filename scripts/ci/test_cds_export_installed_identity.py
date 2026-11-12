#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""Protect the root setup boundary when the disposable runtime defaults to carlos."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    "cds_installed_identity", Path(__file__).with_name("run-cds-export-installed-ci.py"))
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class InstalledRuntimeIdentityTest(unittest.TestCase):
    def test_foreground_and_detached_setup_retain_root_identity_and_literal_arguments(self):
        # Simulate the runtime image's USER carlos default. The real fixture refuses
        # that identity before it can start its public reference server.
        def start_in_owned_runtime(argv):
            index = argv.index("exec") + 1
            user, directory, detached = "carlos", "/", False
            while argv[index].startswith("--"):
                option = argv[index]
                index += 1
                if option == "--detach":
                    detached = True
                elif option == "--user":
                    user = argv[index]
                    index += 1
                elif option == "--workdir":
                    directory = argv[index]
                    index += 1
                else:
                    self.fail("Unreviewed runtime option")
            if user != "0":
                raise PermissionError("Disposable root container required")
            self.assertEqual(directory, "/workspace")
            self.assertEqual(argv[index], "owned-runtime")
            return detached, argv[index + 1:]

        payload = ["java", "fixture", "drugref", "private path;literal.json"]
        prefix = ["docker", "--host=unix:///var/run/docker.sock"]
        with self.assertRaises(PermissionError):
            start_in_owned_runtime(prefix + ["exec", "--detach", "owned-runtime", *payload])
        for detached in (False, True):
            with self.subTest(detached=detached):
                actual = HARNESS.runtime_command(prefix, "owned-runtime", payload, detached=detached)
                self.assertEqual(start_in_owned_runtime(actual), (detached, payload))


if __name__ == "__main__":
    unittest.main()
