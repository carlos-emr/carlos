#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Content parity evidence must be complete, bounded and incapable of manufacturing a pass."""
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

SPEC = importlib.util.spec_from_file_location("war_manifest_tested", Path(__file__).with_name("cds_war_manifest.py"))
MANIFEST = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MANIFEST
SPEC.loader.exec_module(MANIFEST)


class WarManifestTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.members = {name: b"class" for name in MANIFEST.REQUIRED_CLASSES}
        self.members.update({MANIFEST.REQUIRED_MIGRATION: b"migration", "WEB-INF/lib/dependency.jar": b"dependency",
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n", "WEB-INF/classes/application.properties": b"unpublished-fixture-payload",
            "static/\u00e9-\u60a3\u8005.txt": b"text", "empty/": b""})

    def war(self, members=None, *, name="candidate.war", reverse=False, compression=zipfile.ZIP_STORED):
        target = self.root / name
        rows = list((self.members if members is None else members).items())
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(target, "w", compression=compression) as output:
                for entry, content in reversed(rows) if reverse else rows:
                    output.writestr(entry, content)
        return target

    def test_every_entry_size_hash_kind_classification_and_raw_archive_binding(self):
        war = self.war(compression=zipfile.ZIP_DEFLATED)
        result = MANIFEST.build_manifest(war)
        self.assertEqual(result["warSha256"], hashlib.sha256(war.read_bytes()).hexdigest())
        self.assertEqual(result["entryCount"], len(self.members))
        self.assertEqual(result["fileCount"], len(self.members) - 1)
        self.assertEqual(result["directoryCount"], 1)
        self.assertEqual([row["name"] for row in result["entries"]], sorted(self.members))
        for row in result["entries"]:
            content = self.members[row["name"]]
            self.assertEqual(row["bytes"], len(content))
            self.assertEqual(row["sha256"], hashlib.sha256(content).hexdigest())
            self.assertEqual(row["kind"], "directory" if row["name"].endswith("/") else "file")
        self.assertEqual(set(result["countsByClassification"]),
            {"production-class", "dependency-jar", "migration", "resource", "build-metadata", "directory"})
        self.assertNotIn("unpublished-fixture-payload", json.dumps(result))

    def test_archive_container_order_changes_raw_hash_but_never_normalizes_payload(self):
        first = MANIFEST.build_manifest(self.war(name="first.war"))
        second = MANIFEST.build_manifest(self.war(name="second.war", reverse=True))
        self.assertNotEqual(first["warSha256"], second["warSha256"])
        self.assertEqual(first["entries"], second["entries"])
        self.assertEqual(MANIFEST.build_manifest(self.root / "first.war"), first)
        for index, name in enumerate([next(iter(MANIFEST.REQUIRED_CLASSES)), "WEB-INF/lib/dependency.jar",
                MANIFEST.REQUIRED_MIGRATION, "WEB-INF/classes/application.properties", "META-INF/MANIFEST.MF"]):
            with self.subTest(name=name):
                changed = dict(self.members); changed[name] += b"changed"
                result = MANIFEST.build_manifest(self.war(changed, name=f"changed-{index}.war"))
                self.assertNotEqual(result["entries"], first["entries"], "No category grants a content exemption")

    def test_unsafe_names_duplicate_nodes_and_file_ancestor_collisions_fail_closed(self):
        additions = [[("../escape", b"x")], [("/absolute", b"x")], [("a\\b", b"x")],
            [("a//b", b"x")], [("a/./b", b"x")], [("a:stream", b"x")], [("a\x01b", b"x")],
            [("prefix", b"x"), ("prefix/child", b"y")], [("node", b"x"), ("node/", b"")]]
        for index, additions_for_case in enumerate(additions):
            with self.subTest(index=index):
                members = dict(self.members); members.update(additions_for_case)
                with self.assertRaises(MANIFEST.ManifestError):
                    MANIFEST.build_manifest(self.war(members, name=f"unsafe-{index}.war"))
        war = self.war(name="duplicate.war")
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(war, "a") as output:
                output.writestr("empty/", b"")
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(war)

    def test_original_nul_name_is_rejected_before_zipinfo_can_truncate_it(self):
        members = dict(self.members); members["unsafe_name"] = b"x"
        war = self.war(members)
        raw = war.read_bytes(); self.assertEqual(raw.count(b"unsafe_name"), 2)
        war.write_bytes(raw.replace(b"unsafe_name", b"unsafe\x00name"))
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(war)

    def test_symlink_special_directory_payload_and_unsupported_compression_reject(self):
        for index, mode in enumerate([stat.S_IFLNK, stat.S_IFIFO, stat.S_IFCHR]):
            war = self.war(name=f"special-{index}.war")
            with zipfile.ZipFile(war, "a") as output:
                entry = zipfile.ZipInfo("special"); entry.create_system = 3
                entry.external_attr = (mode | 0o600) << 16
                output.writestr(entry, b"target")
            with self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.build_manifest(war)
        members = dict(self.members); members["payload/"] = b"not-empty"
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(self.war(members, name="directory-payload.war"))
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(self.war(name="lzma.war", compression=zipfile.ZIP_LZMA))

    def test_crc_corruption_declared_size_truncation_and_directory_crc_reject(self):
        members = dict(self.members); members["crc.bin"] = b"CRC_PAYLOAD_UNIQUE"
        war = self.war(members)
        original = war.read_bytes(); data = bytearray(original); position = data.index(b"CRC_PAYLOAD_UNIQUE")
        data[position] ^= 1; war.write_bytes(data)
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(war)
        war.write_bytes(original[:-8])
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(war)
        for field_offset, value, member in [(24, 1000, b"crc.bin"), (16, 1, b"empty/")]:
            data = bytearray(original)
            cursor = data.index(b"PK\x01\x02")
            while data[cursor + 46:cursor + 46 + struct.unpack_from("<H", data, cursor + 28)[0]] != member:
                lengths = struct.unpack_from("<3H", data, cursor + 28)
                cursor += 46 + sum(lengths)
            struct.pack_into("<I", data, cursor + field_offset, value); war.write_bytes(data)
            with self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.build_manifest(war)

    def test_encryption_and_forged_directory_count_reject_before_zipfile_allocation(self):
        war = self.war(); original = war.read_bytes()
        for forged_count in [False, True]:
            data = bytearray(original)
            if forged_count:
                end = data.rfind(b"PK\x05\x06")
                struct.pack_into("<2H", data, end + 8, 1, 1)
                limits = MANIFEST.Limits(entries=2)
            else:
                central = data.index(b"PK\x01\x02")
                struct.pack_into("<H", data, central + 8, 1)
                limits = MANIFEST.Limits()
            war.write_bytes(data)
            with patch.object(MANIFEST.zipfile, "ZipFile") as constructed:
                with self.assertRaises(MANIFEST.ManifestError):
                    MANIFEST.build_manifest(war, limits)
                constructed.assert_not_called()
        for offset, value in [(6, 1), (8, zipfile.ZIP_LZMA)]:
            data = bytearray(original)
            struct.pack_into("<H", data, data.index(b"PK\x03\x04") + offset, value)
            war.write_bytes(data)
            with self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.build_manifest(war)

    def test_missing_required_classes_libraries_migration_and_empty_class_reject(self):
        targets = [*MANIFEST.REQUIRED_CLASSES, MANIFEST.REQUIRED_MIGRATION, "WEB-INF/lib/dependency.jar"]
        for index, target in enumerate(targets):
            with self.subTest(target=target):
                members = dict(self.members); del members[target]
                with self.assertRaises(MANIFEST.ManifestError):
                    MANIFEST.build_manifest(self.war(members, name=f"missing-{index}.war"))
        members = dict(self.members); members[next(iter(MANIFEST.REQUIRED_CLASSES))] = b""
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(self.war(members, name="empty-class.war"))

    def test_entry_name_raw_central_expanded_member_output_and_deadline_bounds(self):
        war = self.war()
        for limits in [MANIFEST.Limits(entries=1), MANIFEST.Limits(name_bytes=1), MANIFEST.Limits(raw_bytes=1),
                MANIFEST.Limits(central_bytes=1), MANIFEST.Limits(expanded_bytes=1),
                MANIFEST.Limits(entry_bytes=1), MANIFEST.Limits(manifest_bytes=100)]:
            with self.subTest(limits=limits), self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.build_manifest(war, limits)
        with patch.object(MANIFEST.time, "monotonic", side_effect=[0, 1000]):
            with self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.build_manifest(war)

    def test_local_and_end_record_zip64_are_supported_with_identical_content_evidence(self):
        war = self.war()
        with zipfile.ZipFile(war, "a") as output:
            with output.open("forced-zip64.txt", "w", force_zip64=True) as stream:
                stream.write(b"small ZIP64 member")
        first = MANIFEST.build_manifest(war)
        raw = war.read_bytes(); position = raw.rfind(b"PK\x05\x06")
        end = list(struct.unpack_from("<4s4H2LH", raw, position))
        record = struct.pack("<4sQ2H2L4Q", b"PK\x06\x06", 44, 45, 45, 0, 0, end[4], end[4], end[5], end[6])
        locator = struct.pack("<4sLQL", b"PK\x06\x07", 0, position, 1)
        end[3] = end[4] = 65535; end[5] = end[6] = 0xffffffff
        war.write_bytes(raw[:position] + record + locator + struct.pack("<4s4H2LH", *end))
        second = MANIFEST.build_manifest(war)
        self.assertEqual(first["entries"], second["entries"])

    def test_streamed_content_reads_are_fixed_buffers(self):
        war = self.war(); actual = MANIFEST.zipfile.ZipExtFile.read; requests = []
        def checked(source, size=-1):
            requests.append(size)
            self.assertEqual(size, 65536)
            return actual(source, size)
        with patch.object(MANIFEST.zipfile.ZipExtFile, "read", checked):
            MANIFEST.build_manifest(war)
        self.assertGreater(len(requests), len(self.members))

    def test_atomic_publication_hash_binding_private_mode_and_failure_cleanup(self):
        war = self.war(); output = self.root / "candidate-war-content-manifest.json"
        receipt = MANIFEST.publish_manifest(war, output)
        self.assertEqual(receipt["sha256"], hashlib.sha256(output.read_bytes()).hexdigest())
        self.assertEqual(receipt["bytes"], output.stat().st_size)
        self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
        self.assertEqual(json.loads(output.read_bytes())["warSha256"], receipt["warSha256"])
        original = output.read_bytes()
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.publish_manifest(war, output)
        self.assertEqual(output.read_bytes(), original)
        failed = self.root / "failed.json"
        with patch.object(MANIFEST.os, "fsync", side_effect=OSError("fixture")):
            with self.assertRaises(OSError):
                MANIFEST.publish_manifest(war, failed)
        self.assertFalse(failed.exists())
        self.assertEqual(list(self.root.glob(".war-manifest-*.pending")), [])

    def test_changed_archive_cannot_be_published_and_symlink_input_rejects(self):
        war = self.war(); original = MANIFEST.build_manifest
        def changed(*args, **kwargs):
            result = original(*args, **kwargs)
            with war.open("ab") as output:
                output.write(b"changed-after-evidence")
            return result
        destination = self.root / "never.json"
        with patch.object(MANIFEST, "build_manifest", side_effect=changed):
            with self.assertRaises(MANIFEST.ManifestError):
                MANIFEST.publish_manifest(war, destination)
        self.assertFalse(destination.exists())
        link = self.root / "link.war"; link.symlink_to(war)
        with self.assertRaises(MANIFEST.ManifestError):
            MANIFEST.build_manifest(link)

    def test_post_publication_fsync_failure_removes_only_owned_inode_and_preserves_error(self):
        war = self.war(); destination = self.root / "durability-failed.json"
        error = OSError("synthetic directory fsync failure")
        with patch.object(MANIFEST.os, "fsync", side_effect=[None, error, None]):
            with self.assertRaises(OSError) as raised:
                MANIFEST.publish_manifest(war, destination)
        self.assertIs(raised.exception, error)
        self.assertFalse(destination.exists())
        self.assertEqual(list(self.root.glob(".war-manifest-*.pending")), [])
        cleanup_error = OSError("synthetic rollback directory fsync failure")
        with patch.object(MANIFEST.os, "fsync", side_effect=[None, error, cleanup_error]):
            with self.assertRaises(OSError) as raised:
                MANIFEST.publish_manifest(war, destination)
        self.assertIs(raised.exception, error)
        self.assertFalse(destination.exists())
        self.assertIn("WAR_MANIFEST_PUBLICATION_ROLLBACK_FAILED", error.__notes__)
        calls = 0
        def replace_then_fail(descriptor):
            nonlocal calls
            calls += 1
            if calls == 2:
                replacement = self.root / "race-winner"
                replacement.write_bytes(b"independently-created-evidence")
                replacement.replace(destination)
                raise error
        with patch.object(MANIFEST.os, "fsync", side_effect=replace_then_fail):
            with self.assertRaises(OSError) as raised:
                MANIFEST.publish_manifest(war, destination)
        self.assertIs(raised.exception, error)
        self.assertIn("WAR_MANIFEST_PUBLICATION_ROLLBACK_FAILED", error.__notes__)
        self.assertEqual(destination.read_bytes(), b"independently-created-evidence")

    def test_ci_publishes_before_database_import_windows_and_existing_upload_retains_it(self):
        source = Path(__file__).with_name("run-cds-performance-ci.py").read_text()
        self.assertLess(source.index("WAR_MANIFEST.publish_manifest"), source.index('"OWNED_DATABASE_BOOTSTRAP"'))
        self.assertIn('WAR_MANIFEST.verify_archive_binding(war, status["warSha256"])', source)
        workflow = Path(__file__).resolve().parents[2] / ".github/workflows/cds-import-performance.yml"
        text = workflow.read_text()
        self.assertIn("if: always()", text)
        self.assertIn("path: target/cds-performance-*/public/*", text)
        self.assertIn("'scripts/ci/cds_war_manifest.py'", text)
        self.assertIn("'scripts/ci/test_cds_war_manifest.py'", text)
        self.assertLess(text.index("run: python3 scripts/ci/test_cds_war_manifest.py"),
                        text.index("name: Start Java25 build container"))

    def test_ci_failure_json_preserves_only_typed_finite_manifest_code_and_nonzero(self):
        spec = importlib.util.spec_from_file_location("war_manifest_ci_tested", Path(__file__).with_name("run-cds-performance-ci.py"))
        runner = importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
        cases = [(runner.WAR_MANIFEST.ManifestError("WAR_MANIFEST_ENTRY_CRC"), "WAR_MANIFEST_ENTRY_CRC"),
                 (runner.WAR_MANIFEST.ManifestError("private-fixture-exception-text"), None),
                 (RuntimeError("private-fixture-exception-text"), None)]
        for index, (failure, expected) in enumerate(cases):
            with self.subTest(index=index):
                repository = self.root / f"mock-repository-{index}"
                scripts = repository / "scripts/ci"; scripts.mkdir(parents=True)
                (scripts / "cds-performance-runtime.Dockerfile").write_text("FROM fixture-only\n")
                (repository / "debian").mkdir(); (repository / "debian/carlos-ctl.pin").write_text("fallback=" + "a" * 40 + "\n")
                (repository / "target").mkdir(); (repository / "target/carlos-fixture.war").write_bytes(b"mock-only")
                cli = repository / "cli"; cli.mkdir()
                def execute(command, **kwargs):
                    return subprocess.CompletedProcess(command, 0, stdout="a" * 40 + "\n")
                with patch.object(runner, "__file__", str(scripts / "run-cds-performance-ci.py")), \
                        patch.object(sys, "argv", ["run-cds-performance-ci.py", "--profile", "zip64", "--cli-source", str(cli)]), \
                        patch.object(runner.subprocess, "run", side_effect=execute), \
                        patch.object(runner.WAR_MANIFEST, "publish_manifest", side_effect=failure):
                    self.assertEqual(runner.main(), 1)
                proofs = list(repository.glob("target/cds-performance-*/public/ci-proof.json"))
                self.assertEqual(len(proofs), 1)
                record = json.loads(proofs[0].read_bytes())
                self.assertEqual(record["status"], "FAILED")
                self.assertEqual(record["stage"], "CANDIDATE_WAR_CONTENT_MANIFEST")
                self.assertEqual(record.get("warManifestFailureCode"), expected)
                self.assertNotIn("private-fixture-exception-text", proofs[0].read_text())


if __name__ == "__main__":
    unittest.main()
