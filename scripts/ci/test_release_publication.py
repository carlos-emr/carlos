# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Publication refuses stale, skipped, changed, forged or incomplete qualification."""
from functools import partial
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import release_publication as gate
from cds_war_manifest import REQUIRED_CLASSES, REQUIRED_MIGRATION


class ReleasePublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = {"commit": "a" * 40, "tree": "b" * 40, "clean": True}
        self.run = {"id": 123, "run_attempt": 2, "head_sha": "a" * 40,
                    "head_repository": {"full_name": "org/repo"},
                    "path": ".github/workflows/release-verification.yml", "event": "workflow_dispatch",
                    "status": "completed", "conclusion": "success"}

    def test_untrusted_stale_incomplete_and_skipped_runs_cannot_authorize_publication(self):
        gate.trusted_run(self.run, "org/repo", "a" * 40, "release-verification.yml")
        for key, value in (("head_sha", "c" * 40), ("head_repository", {"full_name": "attacker/repo"}),
                           ("event", "pull_request"), ("path", ".github/workflows/other.yml"),
                           ("status", "in_progress"), ("conclusion", "failure"), ("conclusion", "skipped"),
                           ("conclusion", "cancelled"), ("run_attempt", True), ("run_attempt", 0)):
            with self.subTest(key=key, value=value), self.assertRaises(gate.EvidenceError):
                gate.trusted_run({**self.run, key: value}, "org/repo", "a" * 40, "release-verification.yml")

    def test_workflow_success_does_not_hide_a_skipped_missing_or_duplicate_required_job(self):
        for jobs in ([], [{"name": "required", "status": "completed", "conclusion": "skipped"}],
                     [{"name": "required", "status": "completed", "conclusion": "success"}] * 2):
            with self.subTest(jobs=jobs), patch.object(gate, "api", return_value=[{"jobs": jobs}]), self.assertRaises(gate.EvidenceError):
                gate.successful_jobs("org/repo", self.run, {"required"})

    def test_failed_newer_run_cannot_fall_back_to_older_success(self):
        good = {**self.run, "path": ".github/workflows/required.yml"}
        bad = {**good, "id": 124, "conclusion": "failure"}
        with patch.object(gate, "REQUIRED_WORKFLOWS", {"required.yml": {"required"}}), patch.object(
                gate, "api", return_value=[{"workflow_runs": [good, bad]}]), self.assertRaises(gate.EvidenceError):
            gate.required_ci("org/repo", "a" * 40)

    def test_provenance_binds_war_and_final_packages_to_their_builder_and_commit(self):
        for name in ("carlos.war", "carlos-cyclonedx.json", "carlos-emr_1_amd64.deb", "carlos-ctl_1_all.deb", "carlos.war.sha256"):
            (self.root / name).touch()
        with patch.object(gate.subprocess, "run") as run:
            gate.verify_provenance("org/repo", self.source, self.root)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertEqual(len(commands), 4)
        for command in commands:
            if "carlos-ctl_" in command[3]:
                self.assertEqual(command[-1], "carlos-emr/carlos-ctl")
            else:
                self.assertIn("--source-digest", command)
                self.assertEqual(command[-1], "a" * 40)
                self.assertIn("org/repo/.github/workflows/" + ("deb-packages.yml" if command[3].endswith('.deb') else "publish-release.yml"), command)

    def test_collected_evidence_excludes_unclaimed_logs_and_credentials(self):
        source = self.root / "private"; run = source / "run1"; run.mkdir(parents=True)
        (run / "receipt.json").write_text(json.dumps({"inputs": [{"path": "reports/result.xml"}]}))
        (run / "reports").mkdir(); (run / "reports/result.xml").write_text("synthetic report")
        (run / "private.log").write_text("FAKE-private-canary")
        (run / "credentials").write_text("FAKE-secret")
        target = self.root / "upload"
        gate.collect_evidence(source, target)
        self.assertEqual(sorted(str(p.relative_to(target)) for p in target.rglob("*") if p.is_file()),
                         ["run1/receipt.json", "run1/reports/result.xml"])
        (run / "receipt.json").write_text(json.dumps({"inputs": [{"path": "../escape"}]}))
        with self.assertRaises(gate.EvidenceError):
            gate.collect_evidence(source, self.root / "escaped")

    def test_collected_evidence_refuses_symlinked_directories(self):
        source = self.root / "private"
        run = source / "run1"
        run.mkdir(parents=True)
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "result.xml").write_text("synthetic report")
        (run / "reports").symlink_to(outside, target_is_directory=True)
        (run / "receipt.json").write_text(json.dumps({"inputs": [{"path": "reports/result.xml"}]}))

        with self.assertRaisesRegex(gate.EvidenceError, "symlink"):
            gate.collect_evidence(source, self.root / "upload")

    def make_candidate(self):
        assets = self.root / "assets"; assets.mkdir()
        (self.root / "debian").mkdir()
        (self.root / "debian/control").write_text("Package: carlos-emr\nArchitecture: amd64\n\nPackage: carlos-emr-drugref\nArchitecture: all\n")
        (self.root / "debian/carlos-ctl.pin").write_text("tag=1.3.2\n")
        tag = "2026.08.0-rc1"; version = "2026.08.0~rc1"
        entries = {name: b"synthetic class" for name in REQUIRED_CLASSES}
        entries.update({REQUIRED_MIGRATION: b"synthetic SQL", "WEB-INF/classes/jdbc-defaults.properties": b"standalone",
                        "WEB-INF/lib/synthetic.jar": b"synthetic dependency",
                        "WEB-INF/classes/buildNumber.properties": b"git-sha-1=" + b"a" * 40 + b"\n",
                        "WEB-INF/classes/carlos-build.properties":
                            b"build.version=2026.08.0-rc1\nbuild.job=carlos-emr-deb\nbuild.number=2026.08.0~rc1\n"})
        war = assets / f"carlos-{tag}.war"
        with zipfile.ZipFile(war, "w") as archive:
            for name, value in entries.items(): archive.writestr(name, value)
        for name, filename in gate.PACKAGE_TRANSFORMS.items():
            path = self.root / filename; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"packaged")
            entries[name] = b"packaged"
        for name, arch, deb_version in (("carlos-emr", "amd64", version), ("carlos-emr-drugref", "all", version), ("carlos-ctl", "all", "1.3.2")):
            staging = self.root / name; (staging / "DEBIAN").mkdir(parents=True)
            (staging / "DEBIAN").chmod(0o755)
            (staging / "DEBIAN/control").write_text(f"Package: {name}\nVersion: {deb_version}\nArchitecture: {arch}\nMaintainer: Synthetic <fake@example.invalid>\nDescription: synthetic fixture\n")
            if name == "carlos-emr":
                for filename, value in entries.items():
                    path = staging / "usr/share/carlos-emr/webapp/carlos" / filename
                    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(value)
            subprocess.run(["dpkg-deb", "--build", "--root-owner-group", str(staging),
                            str(assets / f"{name}_{deb_version.replace('~', '.')}_{arch}.deb")], check=True, stdout=subprocess.DEVNULL)
        cli = assets / "carlos-ctl_1.3.2_all.deb"
        manifest = self.root / "docs/testing/release-2026.08-lifecycle-baselines.json"
        manifest.parent.mkdir(parents=True)
        manifest.write_text(json.dumps({"candidateCli": {"tag": "1.3.2", "version": "1.3.2", "name": cli.name,
            "bytes": cli.stat().st_size, "sha256": gate.digest(cli),
            "url": "https://github.com/carlos-emr/carlos-ctl/releases/download/1.3.2/" + cli.name}}))
        (assets / f"carlos-{tag}-cyclonedx.json").write_text('{"bomFormat":"CycloneDX"}')
        for file in list(assets.iterdir()):
            (assets / (file.name + ".sha256")).write_text(gate.digest(file) + "  " + file.name + "\n")
        return assets, tag

    def test_same_version_rebuilt_cli_with_matching_adjacent_checksum_cannot_qualify(self):
        assets, tag = self.make_candidate()
        cli = assets / "carlos-ctl_1.3.2_all.deb"
        staging = self.root / "carlos-ctl"
        (staging / "different-payload").write_bytes(b"different package contents")
        subprocess.run(["dpkg-deb", "--build", "--root-owner-group", str(staging), str(cli)],
                       check=True, stdout=subprocess.DEVNULL)
        (assets / (cli.name + ".sha256")).write_text(gate.digest(cli) + "  " + cli.name + "\n")
        with self.assertRaisesRegex(gate.EvidenceError, "reviewed bytes|reviewed SHA-256"):
            gate.candidate(assets, tag, self.source, self.root)

    def test_final_payload_and_checksums_are_bound_after_all_stamping(self):
        assets, tag = self.make_candidate()
        frozen = gate.candidate(assets, tag, self.source, self.root)
        self.assertEqual(len(frozen["assets"]), 10)
        self.assertEqual(frozen["warSha256"], gate.digest(assets / f"carlos-{tag}.war"))
        checksum = assets / f"carlos-{tag}.war.sha256"; checksum.write_text("0" * 64 + "  wrong.war\n")
        with self.assertRaisesRegex(gate.EvidenceError, "checksum"):
            gate.candidate(assets, tag, self.source, self.root)

    def test_local_debian_prerelease_names_with_published_checksum_names_are_valid(self):
        assets, tag = self.make_candidate()
        for published in sorted(assets.glob("carlos-emr*.deb")):
            local = published.with_name(published.name.replace(".rc1_", "~rc1_"))
            published.rename(local)
            published.with_suffix(".deb.sha256").rename(local.with_suffix(".deb.sha256"))

        frozen = gate.candidate(assets, tag, self.source, self.root)
        self.assertIn("carlos-emr_2026.08.0~rc1_amd64.deb", frozen["assets"])
        self.assertIn("carlos-emr-drugref_2026.08.0~rc1_all.deb", frozen["assets"])

    def test_renamed_packages_with_correct_bytes_and_checksums_are_refused(self):
        assets, tag = self.make_candidate()
        cases = (
            ("carlos-emr_2026.08.0.rc1_amd64.deb", "unrelated.deb"),
            ("carlos-emr_2026.08.0.rc1_amd64.deb", "carlos-emr_2026.08.0.rc2_amd64.deb"),
            ("carlos-emr-drugref_2026.08.0.rc1_all.deb", "carlos-emr-drugref_2026.08.0.rc1_amd64.deb"),
        )
        for original, renamed in cases:
            with self.subTest(renamed=renamed), tempfile.TemporaryDirectory() as temporary:
                files = Path(temporary) / "assets"
                shutil.copytree(assets, files)
                (files / original).rename(files / renamed)
                (files / (original + ".sha256")).unlink()
                (files / (renamed + ".sha256")).write_text(gate.digest(files / renamed) + "  " + renamed + "\n")
                with self.assertRaisesRegex(gate.EvidenceError, "filename"):
                    gate.candidate(files, tag, self.source, self.root)

    def test_unqualified_draft_asset_is_refused(self):
        assets, tag = self.make_candidate()
        (assets / "unreviewed.txt").write_text("not a qualified release asset")
        with self.assertRaisesRegex(gate.EvidenceError, "unexpected file"):
            gate.candidate(assets, tag, self.source, self.root)

    def test_verify_assets_validates_the_complete_tagged_download_and_its_provenance(self):
        assets, tag = self.make_candidate()
        with patch.object(gate, "checked_tag", return_value=self.source) as checked, \
             patch.object(gate, "candidate", side_effect=partial(gate.candidate, root=self.root)) as candidate, \
             patch.object(gate, "verify_provenance") as provenance, \
             patch.object(gate, "required_ci") as qualification_ci, patch.object(gate, "promotion") as promotion:
            self.assertEqual(gate.main(["verify-assets", "--repo", "org/repo", "--tag", tag, "--assets", str(assets)]), 0)
        checked.assert_called_once_with(tag)
        candidate.assert_called_once_with(assets, tag, self.source)
        provenance.assert_called_once_with("org/repo", self.source, assets)
        qualification_ci.assert_not_called()
        promotion.assert_not_called()

    def test_verify_assets_fails_on_wrong_tag_incomplete_download_or_bad_attestation(self):
        assets, tag = self.make_candidate()
        command = ["verify-assets", "--repo", "org/repo", "--tag", tag, "--assets", str(assets)]
        with patch.object(gate, "checked_tag", side_effect=gate.EvidenceError("Wrong source")), \
             patch.object(gate, "candidate") as candidate, patch.object(gate, "verify_provenance") as provenance, \
             patch("builtins.print"):
            self.assertEqual(gate.main(command), 1)
            candidate.assert_not_called()
            provenance.assert_not_called()

        with patch.object(gate, "checked_tag", return_value=self.source), \
             patch.object(gate, "candidate", side_effect=partial(gate.candidate, root=self.root)), \
             patch.object(gate, "verify_provenance", side_effect=subprocess.CalledProcessError(1, "gh")) as provenance, \
             patch("builtins.print"):
            self.assertEqual(gate.main(command), 1)
            provenance.assert_called_once_with("org/repo", self.source, assets)
            (assets / f"carlos-{tag}.war.sha256").unlink()
            provenance.reset_mock()
            self.assertEqual(gate.main(command), 1)
            provenance.assert_not_called()

    def test_publication_rechecks_draft_assets_after_replay(self):
        tag = "2026.08.0-rc1"
        draft = {"id": 45, "draft": True, "tag_name": tag}
        path = self.root / "candidate.war"
        path.write_text("qualified bytes")
        correct = {"name": path.name, "digest": "sha256:" + gate.digest(path), "state": "uploaded"}
        with patch.object(gate, "api", side_effect=[[[draft]], [[correct]]]):
            gate.verify_draft_assets("org/repo", tag, self.root)
        modified = {**correct, "digest": "sha256:" + "0" * 64}
        with patch.object(gate, "api", side_effect=[[[draft]], [[modified]]]), self.assertRaisesRegex(
                gate.EvidenceError, "changed after verification"):
            gate.verify_draft_assets("org/repo", tag, self.root)

    def test_different_package_identity_or_final_payload_is_refused(self):
        assets, tag = self.make_candidate()
        with zipfile.ZipFile(assets / f"carlos-{tag}.war", "a") as archive:
            archive.writestr("unqualified-new-entry", b"changed after package build")
        with self.assertRaisesRegex(gate.EvidenceError, "Final Debian application differs"):
            gate.candidate(assets, tag, self.source, self.root)

    def test_forged_readiness_summary_cannot_replace_raw_verification(self):
        assets = self.root / "assets"; assets.mkdir()
        def download(argv, **options):
            target = Path(argv[-1]); (target / "release-candidate.json").write_text('{"candidate":"frozen","emrPackageSha256":"test-package-digest"}')
            (target / "readiness.json").write_text('{"releaseReady":true}')
        with patch.object(gate, "checked_tag", return_value=self.source), patch.object(gate, "api", return_value=[self.run]), \
             patch.object(gate, "successful_jobs"), patch.object(gate, "required_ci"), \
             patch.object(gate.subprocess, "run", side_effect=download), patch.object(gate, "candidate", return_value={"candidate":"frozen","emrPackageSha256":"test-package-digest"}), \
             patch.object(gate, "verify", return_value={"releaseReady":False}), self.assertRaisesRegex(gate.EvidenceError, "not release ready"):
            gate.promotion("org/repo", "2026.08.0-rc1", "123", assets)

    def test_only_unchanged_candidate_with_recomputed_passing_evidence_can_promote(self):
        assets = self.root / "assets"; assets.mkdir()
        def download(argv, **options):
            target = Path(argv[-1]); (target / "release-candidate.json").write_text('{"candidate":"frozen","emrPackageSha256":"test-package-digest"}')
            (target / "readiness.json").write_text('{"releaseReady":true}')
        for candidate in ({"candidate":"frozen","emrPackageSha256":"test-package-digest"}, {"candidate":"changed"}):
            with self.subTest(candidate=candidate), patch.object(gate, "checked_tag", return_value=self.source), \
                 patch.object(gate, "api", return_value=[self.run]), patch.object(gate, "successful_jobs"), \
                 patch.object(gate, "required_ci"), patch.object(gate.subprocess, "run", side_effect=download), \
                 patch.object(gate, "candidate", return_value=candidate), patch.object(gate, "verify", return_value={"releaseReady":True}) as verify:
                if candidate["candidate"] == "changed":
                    with self.assertRaisesRegex(gate.EvidenceError, "changed after qualification"):
                        gate.promotion("org/repo", "2026.08.0-rc1", "123", assets)
                    verify.assert_not_called()
                else:
                    gate.promotion("org/repo", "2026.08.0-rc1", "123", assets)
                    self.assertEqual(verify.call_args.kwargs, {"target_package_hash": "test-package-digest"})


if __name__ == "__main__":
    unittest.main()
