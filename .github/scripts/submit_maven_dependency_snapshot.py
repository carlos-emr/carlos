#!/usr/bin/env python3
"""Generate and submit a Maven dependency snapshot without a Node action wrapper."""

from __future__ import annotations

import datetime
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path


DEPGRAPH_MAVEN_PLUGIN_VERSION = "4.0.3"
DEPGRAPH_FILENAME = "maven-dependency-submission-action-depgraph.json"
DETECTOR_NAME = "maven-dependency-submission-action"
DETECTOR_VERSION = "5.0.0-local"
DETECTOR_URL = "https://github.com/advanced-security/maven-dependency-submission-action"


def main() -> int:
    workspace = Path(os.environ.get("GITHUB_WORKSPACE", ".")).resolve()
    if os.environ.get("SKIP_MAVEN_DEPGRAPH") != "true":
        generate_dependency_graphs(workspace)

    snapshot = build_snapshot(workspace)
    output_path = workspace / "target" / "dependency-snapshot.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(snapshot, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote dependency snapshot to {output_path}")

    if os.environ.get("DEPENDENCY_SNAPSHOT_DRY_RUN") == "true":
        print("Dry run requested; skipping dependency snapshot submission.")
        return 0

    submit_snapshot(snapshot)
    return 0


def generate_dependency_graphs(workspace: Path) -> None:
    command = [
        "mvn",
        "-B",
        f"com.github.ferstl:depgraph-maven-plugin:{DEPGRAPH_MAVEN_PLUGIN_VERSION}:graph",
        "-DgraphFormat=json",
        f"-DoutputFileName={DEPGRAPH_FILENAME}",
    ]
    print("Generating Maven dependency graph...", flush=True)
    subprocess.run(command, cwd=workspace, check=True)


def build_snapshot(workspace: Path) -> dict:
    graph_files = sorted(workspace.rglob(DEPGRAPH_FILENAME))
    if not graph_files:
        raise RuntimeError(f"No Maven dependency graph files named {DEPGRAPH_FILENAME} were generated")

    manifests = {}
    for graph_file in graph_files:
        manifest = build_manifest(workspace, graph_file)
        key = unique_manifest_key(manifests, manifest["name"])
        manifests[key] = manifest

    return {
        "version": 0,
        "sha": require_env("GITHUB_SHA"),
        "ref": require_env("GITHUB_REF"),
        "job": build_job(),
        "detector": {
            "name": DETECTOR_NAME,
            "version": DETECTOR_VERSION,
            "url": DETECTOR_URL,
        },
        "scanned": datetime.datetime.now(datetime.UTC).isoformat().replace("+00:00", "Z"),
        "manifests": manifests,
    }


def build_manifest(workspace: Path, graph_file: Path) -> dict:
    depgraph = json.loads(graph_file.read_text(encoding="utf-8"))
    graph_name = depgraph.get("graphName") or "maven"
    artifacts = depgraph.get("artifacts") or []
    dependencies = depgraph.get("dependencies") or []

    artifacts_by_id = {artifact["id"]: artifact for artifact in artifacts}
    purls_by_id = {
        artifact_id: artifact_to_package_url(artifact)
        for artifact_id, artifact in artifacts_by_id.items()
    }
    children_by_parent = defaultdict(list)
    child_ids = set()
    for dependency in dependencies:
        parent_id = dependency["from"]
        child_id = dependency["to"]
        children_by_parent[parent_id].append(child_id)
        child_ids.add(child_id)

    root_ids = [artifact["id"] for artifact in artifacts if artifact["id"] not in child_ids]
    direct_dependency_ids = []
    for root_id in root_ids:
        for dependency_id in children_by_parent.get(root_id, []):
            if dependency_id not in direct_dependency_ids:
                direct_dependency_ids.append(dependency_id)

    resolved = {}
    for dependency_id in direct_dependency_ids:
        add_dependency(resolved, dependency_id, "direct", artifacts_by_id, purls_by_id, children_by_parent, set())

    return {
        "name": graph_name,
        "file": {"source_location": repository_relative_pom_path(workspace, graph_file)},
        "resolved": resolved,
    }


def add_dependency(
        resolved: dict,
        dependency_id: str,
        relationship: str,
        artifacts_by_id: dict,
        purls_by_id: dict,
        children_by_parent: dict,
        seen: set,
) -> None:
    if dependency_id in seen:
        return
    if dependency_id not in artifacts_by_id:
        raise RuntimeError(f"Dependency graph references unknown artifact: {dependency_id}")

    package_url = purls_by_id[dependency_id]
    child_package_urls = [
        purls_by_id[child_id]
        for child_id in children_by_parent.get(dependency_id, [])
        if child_id in purls_by_id
    ]
    existing = resolved.get(package_url)
    if existing is None or relationship == "direct":
        resolved[package_url] = {
            "package_url": package_url,
            "relationship": relationship,
            "scope": dependency_scope(artifacts_by_id[dependency_id].get("scopes")),
            "dependencies": child_package_urls,
        }
    else:
        existing["dependencies"] = child_package_urls

    next_seen = seen | {dependency_id}
    for child_id in children_by_parent.get(dependency_id, []):
        add_dependency(resolved, child_id, "indirect", artifacts_by_id, purls_by_id, children_by_parent, next_seen)


def artifact_to_package_url(artifact: dict) -> str:
    namespace = quote_purl_part(artifact["groupId"])
    name = quote_purl_part(artifact["artifactId"])
    version = quote_purl_part(artifact["version"])
    package_url = f"pkg:maven/{namespace}/{name}@{version}"

    qualifiers = {}
    types = artifact.get("types") or []
    classifiers = artifact.get("classifiers") or []
    if types:
        qualifiers["type"] = types[0]
    if classifiers:
        qualifiers["classifier"] = classifiers[0]
    if qualifiers:
        package_url = f"{package_url}?{urllib.parse.urlencode(qualifiers)}"
    return package_url


def quote_purl_part(value: str) -> str:
    return urllib.parse.quote(value, safe=".-_")


def dependency_scope(scopes: list[str] | None) -> str:
    if scopes and "test" in scopes:
        return "development"
    return "runtime"


def repository_relative_pom_path(workspace: Path, graph_file: Path) -> str:
    pom_path = graph_file.parent.parent / "pom.xml" if graph_file.parent.name == "target" else graph_file
    return pom_path.resolve().relative_to(workspace).as_posix()


def unique_manifest_key(manifests: dict, name: str) -> str:
    if name not in manifests:
        return name
    suffix = 2
    while f"{name}-{suffix}" in manifests:
        suffix += 1
    return f"{name}-{suffix}"


def build_job() -> dict:
    job = {
        "correlator": require_env("GITHUB_JOB"),
        "id": require_env("GITHUB_RUN_ID"),
    }
    server_url = os.environ.get("GITHUB_SERVER_URL")
    repository = os.environ.get("GITHUB_REPOSITORY")
    run_id = os.environ.get("GITHUB_RUN_ID")
    if server_url and repository and run_id:
        job["html_url"] = f"{server_url}/{repository}/actions/runs/{run_id}"
    return job


def submit_snapshot(snapshot: dict) -> None:
    repository = require_env("GITHUB_REPOSITORY")
    token = require_env("GITHUB_TOKEN")
    api_url = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    url = f"{api_url}/repos/{repository}/dependency-graph/snapshots"
    data = json.dumps(snapshot).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )

    print("Submitting dependency snapshot...")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            response_body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Dependency snapshot submission failed: HTTP {error.code}: {body}") from error

    result = response_body.get("result")
    if result not in {"SUCCESS", "ACCEPTED"}:
        raise RuntimeError(f"Dependency snapshot submission failed: {response_body}")
    print(f"Snapshot created: {response_body.get('message', result)}")


def require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Required environment variable is missing: {name}")
    return value


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
