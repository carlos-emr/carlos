# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Run the release workflow's final Bash and server digest check against a local draft."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import textwrap
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REPO = "test/release"
PUBLISH = "Publish the release"
SKIP = "Skip if the release is already finalized"


def steps(filename):
    source = (ROOT / ".github/workflows" / filename).read_text()
    return dict(re.findall(r"^      - name: ([^\n]+)\n(.*?)(?=^      - |\Z)", source, re.M | re.S))


def fake_gh(args):
    state_file = Path(os.environ["TEST_RELEASE_STATE"])
    state = json.loads(state_file.read_text())
    endpoint = f"repos/{REPO}/releases"
    if args == ["api", "--paginate", "--slurp", endpoint + "?per_page=100"]:
        print(json.dumps([[{"id": 23, "tag_name": state["tag"], "draft": state["draft"]}]]))
    elif args == ["api", "--paginate", "--slurp", endpoint + "/23/assets?per_page=100"]:
        print(json.dumps([state["assets"]]))
    elif (args[:4] == ["api", "--paginate", endpoint + "?per_page=100", "--jq"]
          and len(args) == 5):
        print(*state["stable"], sep="\n")
    elif args[:5] == ["release", "view", state["tag"], "--repo", REPO]:
        print(str(state["draft"]).lower())
        for asset in state["assets"]:
            print(asset["name"])
    elif args[:5] == ["release", "edit", state["tag"], "--repo", REPO] and state["draft"]:
        state["edits"].append(args[5:])
        state["draft"] = False
        state_file.write_text(json.dumps(state))
    else:
        raise SystemExit("Unexpected CLI request to the local release fixture: " + repr(args))


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.folder = Path(temporary.name)
        self.assets = self.folder / "download"
        self.assets.mkdir()
        self.state = self.folder / "state.json"
        self.envfile = self.folder / "github-env"
        gh = self.folder / "gh"
        gh.write_text("#!/bin/sh\nexec " + shlex.join([sys.executable, str(Path(__file__).resolve()), "--fake-gh"]) + ' "$@"\n')
        gh.chmod(0o700)
        self.workflow = steps("deb-packages.yml")

    def draft(self, tag, stable=()):
        version = tag.replace("-", ".")
        pin = next(line[4:] for line in (ROOT / "debian/carlos-ctl.pin").read_text().splitlines() if line.startswith("tag="))
        names = [f"carlos-{tag}.war", f"carlos-{tag}-cyclonedx.json", f"carlos-emr_{version}_amd64.deb",
                 f"carlos-emr-drugref_{version}_all.deb", f"carlos-ctl_{pin.replace('-', '.')}_all.deb"]
        remote = []
        for name in names:
            contents = f"synthetic {name}\n".encode()
            checksum = hashlib.sha256(contents).hexdigest()
            for path, data in ((name, contents), (name + ".sha256", f"{checksum}  {name}\n".encode())):
                (self.assets / path).write_bytes(data)
                remote.append({"name": path, "digest": "sha256:" + hashlib.sha256(data).hexdigest(), "state": "uploaded"})
        state = {"tag": tag, "draft": True, "assets": remote, "stable": list(stable), "edits": []}
        self.state.write_text(json.dumps(state))
        return state

    def run_step(self, name, tag):
        block = self.workflow[name].split("        run: |\n", 1)[1]
        command = textwrap.dedent(block).replace("/tmp/release-gate/bin/python", shlex.quote(sys.executable))
        command = command.replace("/tmp/promotion-assets", shlex.quote(str(self.assets)))
        env = {**os.environ, "PATH": str(self.folder) + os.pathsep + os.environ["PATH"],
               "RELEASE_TAG": tag, "REPO": REPO, "GITHUB_ENV": str(self.envfile), "TEST_RELEASE_STATE": str(self.state)}
        return subprocess.run(["bash", "-c", command], cwd=ROOT, env=env, capture_output=True, text=True, timeout=30)

    def test_war_is_stamped_and_hashed_in_writable_release_staging(self):
        tag = "2030.08.0-beta1"
        commit = "a" * 40
        target = self.folder / "target"
        target.mkdir()
        war_name = f"carlos-{tag}.war"
        source = target / war_name
        properties = "WEB-INF/classes/carlos-build.properties"
        with zipfile.ZipFile(source, "w") as archive:
            archive.writestr(properties, "build.job=standalone\nbuild.number=1\n")
            archive.writestr("WEB-INF/classes/buildNumber.properties", f"git-sha-1={commit}\n")
        original = source.read_bytes()
        sbom_name = f"carlos-{tag}-cyclonedx.json"
        (target / sbom_name).write_text('{"bomFormat":"CycloneDX"}\n')
        target.chmod(0o555)
        self.addCleanup(target.chmod, 0o755)
        (self.folder / "debian").symlink_to(ROOT / "debian", target_is_directory=True)
        (self.folder / "pom.xml").write_text(
            f'<project xmlns="http://maven.apache.org/POM/4.0.0"><version>{tag}</version></project>')

        block = steps("publish-release.yml")["Build the WAR and CycloneDX SBOM from a clean checkout"]
        shell = textwrap.dedent(block.split("        run: |\n", 1)[1])
        # The read-only target above stands in for the completed container build.
        result = subprocess.run(["bash", "-c", "docker() { return 0; }\n" + shell], cwd=self.folder,
                                env={**os.environ, "RELEASE_TAG": tag, "TAG_COMMIT": commit,
                                     "GITHUB_WORKSPACE": str(self.folder)}, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(source.read_bytes(), original)
        staged = self.folder / "release-assets"
        with zipfile.ZipFile(staged / war_name) as archive:
            self.assertEqual(archive.read(properties).decode().splitlines(),
                             ["build.job=carlos-emr-deb", "build.number=2030.08.0~beta1"])
        for name in (war_name, sbom_name):
            checksum = hashlib.sha256((staged / name).read_bytes()).hexdigest()
            self.assertEqual((staged / (name + ".sha256")).read_text(), f"{checksum}  {name}\n")

    def test_normal_dispatch_builds_and_checks_every_asset_before_shared_publication(self):
        upstream = steps("publish-release.yml")
        create = "Create or resume the draft release"
        upload = "Upload and verify release assets"
        dispatch = "Dispatch the Debian package build that finalizes the release"
        self.assertEqual([name for name in upstream if name in (create, upload, dispatch)], [create, upload, dispatch])
        self.assertIn("--draft\n", upstream[create])
        self.assertIn("gh workflow run deb-packages.yml", upstream[dispatch])
        self.assertIn('--ref "refs/tags/$RELEASE_TAG"', upstream[dispatch])
        self.assertNotIn("qualification_run_id=", upstream[dispatch])

        stages = ["Download and verify the release WAR", "Build the packages", "Lintian gate", "Attest package provenance",
                  "Add the pinned carlos-ctl package to the release assets", "Upload and verify the release assets",
                  "Verify every completed draft asset before publication"]
        qualified = "Verify readiness before final publication (also on direct dispatch and retries)"
        self.assertEqual([name for name in self.workflow if name in (*stages, PUBLISH)], [*stages, PUBLISH])
        for name in stages:
            with self.subTest(step=name):
                self.assertIn("        if: env.SKIP != 'true' && env.QUALIFICATION_RUN == ''\n", self.workflow[name])
        self.assertIn("release_publication.py verify-assets", self.workflow[stages[-1]])
        self.assertIn("        if: env.SKIP != 'true' && env.QUALIFICATION_RUN != ''\n", self.workflow[qualified])
        self.assertIn("release_publication.py promote", self.workflow[qualified])
        self.assertLess(list(self.workflow).index(qualified), list(self.workflow).index(PUBLISH))
        self.assertIn("        if: env.SKIP != 'true'\n", self.workflow[PUBLISH])

    def test_prerelease_publishes_once_and_retry_detects_immutable_release(self):
        tag = "2030.08.0-beta1"
        self.draft(tag)
        result = self.run_step(PUBLISH, tag)
        self.assertEqual(result.returncode, 0, result.stderr)
        state = json.loads(self.state.read_text())
        self.assertFalse(state["draft"])
        self.assertEqual(state["edits"], [["--draft=false", "--prerelease=true", "--latest=false"]])
        result = self.run_step(SKIP, tag)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.envfile.read_text().strip(), "SKIP=true")
        self.assertEqual(json.loads(self.state.read_text())["edits"], state["edits"])

    def test_late_missing_or_changed_draft_asset_prevents_publication_until_a_valid_retry(self):
        tag = "2030.08.0-rc1"
        original = self.draft(tag)
        for change in ("missing", "changed"):
            with self.subTest(change=change):
                state = json.loads(json.dumps(original))
                if change == "missing":
                    state["assets"].pop()
                else:
                    state["assets"][0]["digest"] = "sha256:" + "0" * 64
                self.state.write_text(json.dumps(state))
                result = self.run_step(PUBLISH, tag)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Draft assets changed", result.stdout)
                self.assertEqual(json.loads(self.state.read_text()), state)
        self.state.write_text(json.dumps(original))
        result = self.run_step(PUBLISH, tag)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(json.loads(self.state.read_text())["draft"])

    def test_stable_latest_pointer_moves_only_for_the_newest_line(self):
        tag = "2030.08.1"
        for published, flag in (("2030.07.5", "--latest"), ("2030.09.0", "--latest=false")):
            with self.subTest(published=published):
                self.draft(tag, [published])
                result = self.run_step(PUBLISH, tag)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(self.state.read_text())["edits"], [["--draft=false", "--prerelease=false", flag]])


if __name__ == "__main__":
    if sys.argv[1:2] == ["--fake-gh"]:
        fake_gh(sys.argv[2:])
    else:
        unittest.main()
