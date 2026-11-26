# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Verify release assets, freeze candidates and replay requested qualification."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

from candidate_cli_package import specification as cli_specification, verify_package as verify_cli_package
from cds_war_manifest import build_manifest
from junit_evidence import EvidenceError
from release_deployment import PACKAGE_TRANSFORMS, package_contents
from release_verification import DEFAULT_PLAN, ROOT, digest, read_json, source_identity, verify, write_json

# Workflow filenames, rather than display names, identify the reviewed entry points.
# A successful workflow with all its jobs skipped is insufficient.
REQUIRED_WORKFLOWS = {
    "maven-project.yml": {"build", "tests", "MariaDB DAO integration tests", "encode-lint", "jspc"},
    "script-regressions.yml": {"Node Script Regressions"},
    "waf-policy.yml": {"nginx / ModSecurity / CRS regressions"},
    "debian-python-tests.yml": {"Debian Python regressions"},
    "cds-import-native.yml": {"native-contracts"},
    "spotbugs.yml": {"SpotBugs + Find Security Bugs"},
    "pmd.yml": {"PMD Static Analysis"},
    "semgrep.yml": {"Semgrep SAST Analysis"},
    "ci-validation.yml": {"ci-validation"},
}
TAG = re.compile(r"[0-9]{4}\.(?:0[1-9]|1[0-2])\.[0-9]+(?:-(?:alpha|beta|rc)[1-9][0-9]*)?")


def checked_tag(tag):
    if not TAG.fullmatch(tag):
        raise EvidenceError("Invalid release tag")
    if subprocess.check_output(["git", "cat-file", "-t", "refs/tags/" + tag], cwd=ROOT, text=True).strip() != "tag":
        raise EvidenceError("Release tag must be annotated")
    source = source_identity()
    commit = subprocess.check_output(["git", "rev-parse", tag + "^{commit}"], cwd=ROOT, text=True).strip()
    if not source["clean"] or source["commit"] != commit:
        raise EvidenceError("Release verification requires the clean tagged source")
    return source


def properties(data):
    result = {}
    for line in data.decode("utf-8").splitlines():
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if not separator or key in result:
            raise EvidenceError("Ambiguous release build identity")
        result[key] = value
    return result


def candidate(assets, tag, source, root=ROOT):
    """Bind both the archive and its final exploded Debian payload, after stamping."""
    if not TAG.fullmatch(tag) or assets.is_symlink():
        raise EvidenceError("Invalid release candidate")
    war = assets / f"carlos-{tag}.war"
    manifest = build_manifest(war)
    with zipfile.ZipFile(war) as archive:
        build = properties(archive.read("WEB-INF/classes/carlos-build.properties"))
        revision = properties(archive.read("WEB-INF/classes/buildNumber.properties"))
    version = tag.replace("-", "~")
    if (build.get("build.version") != tag or build.get("build.job") != "carlos-emr-deb"
            or build.get("build.number") != version or revision.get("git-sha-1") != source["commit"]):
        raise EvidenceError("Candidate identity differs from the exact tag and final Debian stamp")
    expected = {row["name"]: {"bytes": row["bytes"], "sha256": row["sha256"]}
                for row in manifest["entries"] if row["kind"] == "file"}
    if "WEB-INF/tomcat-web.xml" in expected or "WEB-INF/classes/jdbc-defaults.properties" not in expected:
        raise EvidenceError("Candidate lacks the reviewed package transform prerequisites")
    for name, filename in PACKAGE_TRANSFORMS.items():
        file = root / filename
        expected[name] = {"bytes": file.stat().st_size, "sha256": digest(file)}
    packages = set(re.findall(r"^Package:\s*(\S+)", (root / "debian/control").read_text(), re.M))
    ctl = cli_specification(root)
    verify_cli_package(assets / ctl['name'], ctl)
    packages.add("carlos-ctl")
    seen = set()
    payload_names = {war.name}
    emr_package_hash = None
    for deb in sorted(assets.glob("*.deb")):
        fields = subprocess.check_output(["dpkg-deb", "-f", str(deb), "Package", "Version", "Architecture"], text=True)
        fields = dict(line.split(": ", 1) for line in fields.splitlines())
        package = fields.get("Package")
        if package not in packages or package in seen or fields.get("Version") != (ctl['version'] if package == "carlos-ctl" else version):
            raise EvidenceError("Candidate package set has a duplicate, missing or incorrect version")
        if fields.get("Architecture") != ("amd64" if package == "carlos-emr" else "all"):
            raise EvidenceError("Candidate package architecture differs from the supported release")
        debian_name = f"{package}_{fields['Version']}_{fields['Architecture']}.deb"
        # GitHub stores a Debian prerelease separator (~) as a dot in asset names.
        if deb.name not in (debian_name, debian_name.replace("~", ".")):
            raise EvidenceError(f"Candidate package filename {deb.name!r} differs from its control metadata")
        seen.add(package)
        payload_names.add(deb.name)
        if package == "carlos-emr":
            emr_package_hash = digest(deb)
            if package_contents(deb, emr_package_hash)["entries"] != expected:
                raise EvidenceError("Final Debian application differs from the qualified WAR and reviewed transforms")
    if seen != packages:
        raise EvidenceError("Candidate is missing required Debian packages")
    sbom = assets / f"carlos-{tag}-cyclonedx.json"
    if read_json(sbom).get("bomFormat") != "CycloneDX":
        raise EvidenceError("Candidate is missing its CycloneDX SBOM")
    payload_names.add(sbom.name)
    expected_names = payload_names | {name + ".sha256" for name in payload_names}
    files = {}
    for file in sorted(assets.iterdir()):
        if file.is_symlink() or not file.is_file() or not re.fullmatch(r"[A-Za-z0-9_.~+-]+", file.name):
            raise EvidenceError("Candidate asset is not a regular named file")
        files[file.name] = digest(file)
    if set(files) != expected_names:
        raise EvidenceError("Candidate asset set includes an unexpected file or is incomplete")
    for name in payload_names:
        checksum = assets / (name + ".sha256")
        if checksum.read_text().strip() != files[name] + "  " + name.replace("~", "."):
            raise EvidenceError("Candidate checksum names different bytes")
    return {"schemaVersion": 1, "tag": tag, "source": source, "warSha256": manifest["warSha256"],
            "emrPackageSha256": emr_package_hash, "assets": files}


def api(endpoint):
    return json.loads(subprocess.check_output(["gh", "api", "--paginate", "--slurp", endpoint], text=True))


def trusted_run(run, repo, commit, workflow):
    if (run.get("head_sha") != commit or run.get("head_repository", {}).get("full_name", "").lower() != repo.lower()
            or run.get("path", "").split("@", 1)[0] != ".github/workflows/" + workflow
            or run.get("event") not in {"push", "workflow_dispatch"}
            or run.get("status") != "completed" or run.get("conclusion") != "success"
            or type(run.get("run_attempt")) is not int or run["run_attempt"] < 1):
        raise EvidenceError("Required workflow is missing, failed, skipped, untrusted or from another commit")


def successful_jobs(repo, run, required):
    jobs = [job for page in api(f"repos/{repo}/actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs?per_page=100")
            for job in page["jobs"]]
    for name in required:
        matches = [job for job in jobs if job.get("name") == name]
        if len(matches) != 1 or matches[0].get("conclusion") != "success" or matches[0].get("status") != "completed":
            raise EvidenceError("Required workflow job did not execute successfully: " + name)


def required_ci(repo, commit):
    for workflow, jobs in REQUIRED_WORKFLOWS.items():
        runs = [run for page in api(f"repos/{repo}/actions/workflows/{workflow}/runs?head_sha={commit}&per_page=100")
                for run in page["workflow_runs"] if run.get("event") in {"push", "workflow_dispatch"}]
        if not runs:
            raise EvidenceError("Required workflow has no execution for this commit: " + workflow)
        latest = max(runs, key=lambda run: run["id"])
        trusted_run(latest, repo, commit, workflow)
        successful_jobs(repo, latest, jobs)


def download_assets(repo, tag, destination):
    destination.mkdir(parents=True, exist_ok=False)
    subprocess.run(["gh", "release", "download", tag, "--repo", repo, "--dir", str(destination),
                    "--pattern", "*"], check=True)


def verify_draft_assets(repo, tag, verified_assets):
    """Recheck the server's draft immediately before publishing the verified bytes."""
    drafts = [release for page in api(f"repos/{repo}/releases?per_page=100")
              for release in page if release.get("tag_name") == tag and release.get("draft") is True]
    if len(drafts) != 1:
        raise EvidenceError("The verified release draft no longer exists")
    assets = [asset for page in api(f"repos/{repo}/releases/{drafts[0]['id']}/assets?per_page=100")
              for asset in page]
    expected = {file.name: "sha256:" + digest(file) for file in verified_assets.iterdir()}
    actual = {asset["name"]: asset.get("digest") for asset in assets if asset.get("state") == "uploaded"}
    if len(assets) != len(expected) or actual != expected:
        raise EvidenceError("Draft assets changed after verification")


def verify_provenance(repo, source, assets):
    """Metadata inside an archive alone cannot prove which source built it."""
    for file in sorted(assets.iterdir()):
        if file.name.endswith(".sha256"):
            continue
        if file.name.startswith("carlos-ctl_") and file.suffix == ".deb":
            command = ["gh", "attestation", "verify", str(file), "--repo", "carlos-emr/carlos-ctl"]
        else:
            workflow = "deb-packages.yml" if file.suffix == ".deb" else "publish-release.yml"
            command = ["gh", "attestation", "verify", str(file), "--repo", repo,
                       "--signer-workflow", repo + "/.github/workflows/" + workflow,
                       "--source-digest", source["commit"]]
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL)


def promotion(repo, tag, run_id, assets):
    if not re.fullmatch(r"[1-9][0-9]*", run_id):
        raise EvidenceError("A completed qualification workflow run is required")
    source = checked_tag(tag)
    run = api(f"repos/{repo}/actions/runs/{run_id}")[0]
    trusted_run(run, repo, source["commit"], "release-verification.yml")
    if run.get("event") != "workflow_dispatch":
        raise EvidenceError("Qualification must execute explicitly on the release tag")
    successful_jobs(repo, run, {"Release candidate qualification"})
    required_ci(repo, source["commit"])
    with tempfile.TemporaryDirectory(prefix="release-qualification-") as temporary:
        evidence = Path(temporary)
        subprocess.run(["gh", "run", "download", run_id, "--repo", repo, "--name",
                        f"release-qualification-{run_id}-{run['run_attempt']}", "--dir", str(evidence)], check=True)
        frozen = read_json(evidence / "release-candidate.json")
        if frozen != candidate(assets, tag, source):
            raise EvidenceError("Release assets changed after qualification")
        # Recompute from every raw report; a hand-written releaseReady=true is never sufficient.
        result = verify(DEFAULT_PLAN, evidence / "evidence", assets / f"carlos-{tag}.war",
                        target_package_hash=frozen["emrPackageSha256"])
        if result.get("releaseReady") is not True:
            raise EvidenceError("Exact tagged candidate is not release ready")
        if read_json(evidence / "readiness.json") != result:
            raise EvidenceError("Qualification summary disagrees with raw evidence")


def collect_evidence(source, destination):
    """Copy claimed evidence only. Never upload private logs, credentials or unclaimed output."""
    if source.is_symlink():
        raise EvidenceError("Qualification evidence root cannot be a symlink")
    destination.mkdir(parents=True, exist_ok=False)
    for receipt in sorted(source.glob("*/receipt.json")):
        if receipt.parent.is_symlink() or receipt.is_symlink():
            raise EvidenceError("Qualification receipt cannot follow a symlink")
        data = read_json(receipt)
        target = destination / receipt.parent.name
        target.mkdir()
        for name in ["receipt.json"] + [item["path"] for item in data.get("inputs", [])]:
            relative = Path(name)
            file = receipt.parent / relative
            if relative.is_absolute() or ".." in relative.parts or not file.is_file():
                raise EvidenceError("Qualification evidence escapes its receipt")
            if any((receipt.parent / parent).is_symlink() for parent in (relative, *relative.parents)):
                raise EvidenceError("Qualification evidence cannot follow a symlink")
            output = target / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, output)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("freeze", "promote", "verify-assets", "verify-draft"))
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--run", default="")
    parser.add_argument("--manifest", type=Path)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo):
        parser.error("Invalid repository")
    try:
        if args.operation == "freeze":
            manifest = args.manifest or args.assets.parent / "release-candidate.json"
            if manifest.resolve().is_relative_to(args.assets.resolve()):
                raise EvidenceError("Store the candidate manifest outside the payload directory")
            write_json(manifest, candidate(args.assets, args.tag, checked_tag(args.tag)))
        elif args.operation == "promote":
            promotion(args.repo, args.tag, args.run, args.assets)
        elif args.operation == "verify-assets":
            source = checked_tag(args.tag)
            candidate(args.assets, args.tag, source)
            verify_provenance(args.repo, source, args.assets)
        else:
            verify_draft_assets(args.repo, args.tag, args.assets)
        return 0
    except (EvidenceError, OSError, ValueError, KeyError, subprocess.SubprocessError) as failure:
        print("Release publication refused: " + (str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
