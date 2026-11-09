#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Retain bounded CodeFactor annotations with explicit completeness evidence."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

MAX_ANNOTATIONS = 20000
MAX_PAGE_BYTES = 8 << 20
MAX_REPORT_BYTES = 32 << 20
FIELDS = ("path", "start_line", "end_line", "start_column", "end_column",
          "annotation_level", "title", "message", "raw_details")


def collect(fetch, repository: str, commit: str, check: dict) -> dict:
    """Require completed, stable counts and every declared annotation page."""
    identity, expected = check.get("id"), check.get("output", {}).get("annotations_count")
    if (type(identity) is not int or identity <= 0 or type(expected) is not int
            or expected < 0 or expected > MAX_ANNOTATIONS or check.get("status") != "completed"
            or check.get("head_sha") != commit or check.get("name") != "CodeFactor"):
        raise ValueError("Invalid completed CodeFactor check")
    annotations = []
    size = 0
    incomplete = None
    for page in range(1, (expected + 99) // 100 + 1):
        batch = fetch(f"/repos/{repository}/check-runs/{identity}/annotations?per_page=100&page={page}")
        if not isinstance(batch, list) or len(batch) > 100:
            raise ValueError("Invalid CodeFactor annotation page")
        expected_page = min(100, expected - len(annotations))
        for item in batch:
            if not isinstance(item, dict):
                raise ValueError("Invalid CodeFactor annotation")
            selected = {key: item.get(key) for key in FIELDS}
            if any(value is not None and (type(value) not in (str, int)
                   or isinstance(value, str) and len(value) > 65536) for value in selected.values()):
                raise ValueError("Invalid CodeFactor annotation fields")
            size += len(json.dumps(selected, ensure_ascii=True).encode("ascii"))
            if size > MAX_REPORT_BYTES:
                raise ValueError("CodeFactor report exceeds bound")
            annotations.append(selected)
        if len(batch) != expected_page:
            incomplete = {"code": "INCOMPLETE_ANNOTATION_PAGE", "page": page,
                          "expectedPageCount": expected_page, "receivedPageCount": len(batch)}
            break
    current = fetch(f"/repos/{repository}/check-runs/{identity}")
    if (current.get("status") != "completed" or current.get("head_sha") != commit
            or current.get("output", {}).get("annotations_count") != expected
            or current.get("conclusion") != check.get("conclusion")):
        incomplete = {"code": "CHECK_CHANGED_DURING_READ"}
    digest = hashlib.sha256(json.dumps(annotations, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=True).encode("ascii")).hexdigest()
    return {"protocol": 1, "complete": incomplete is None, "commit": commit, "checkId": identity,
            "conclusion": check.get("conclusion"), "annotationCount": len(annotations),
            "declaredAnnotationCount": expected, "incomplete": incomplete,
            "annotationsSha256": digest, "annotations": annotations}


def latest_completed_check(fetch, repository: str, requested: str):
    """External apps may omit checks on CI-only commits; retain the actual analysis SHA."""
    commit = requested
    for _ in range(10):
        response = fetch(f"/repos/{repository}/commits/{commit}/check-runs?check_name=CodeFactor&filter=latest&per_page=100")
        matches = [check for check in response.get("check_runs", []) if check.get("name") == "CodeFactor"
                   and check.get("head_sha") == commit and check.get("status") == "completed"]
        if matches:
            return commit, max(matches, key=lambda check: check["id"])
        parents = fetch(f"/repos/{repository}/commits/{commit}").get("parents", [])
        if not parents:
            break
        commit = parents[0].get("sha")
        if not isinstance(commit, str) or not re.fullmatch(r"[a-f0-9]{40}", commit):
            raise ValueError("Invalid analysis ancestor")
    return None


def main() -> int:
    repository, commit = os.environ["REPORT_REPOSITORY"], os.environ["REPORT_COMMIT"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Invalid repository or commit")
    token = os.environ["GITHUB_TOKEN"]

    def fetch(path: str):
        request = Request("https://api.github.com" + path, headers={"Authorization": "Bearer " + token,
                          "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"})
        try:
            with urlopen(request, timeout=30) as response:
                body = response.read(MAX_PAGE_BYTES + 1)
        except HTTPError as error:
            raise RuntimeError(f"GitHub diagnostics request failed: HTTP {error.code}") from None
        if len(body) > MAX_PAGE_BYTES:
            raise ValueError("GitHub diagnostics page exceeds bound")
        # No credential may enter a retained report, including remote message text.
        return json.loads(body.decode("utf-8").replace(token, "[REDACTED]"))

    deadline = time.monotonic() + 900
    while True:
        selected = latest_completed_check(fetch, repository, commit)
        if selected is not None:
            analysis_commit, check = selected
            print(json.dumps({"analysisCommit": analysis_commit, "checkId": check["id"],
                              "declaredAnnotationCount": check["output"]["annotations_count"]}), flush=True)
            document = collect(fetch, repository, analysis_commit, check)
            document["requestedCommit"] = commit
            document["analysisMayPrecedeCurrentCommit"] = analysis_commit != commit
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Completed CodeFactor check unavailable within bounded history")
        time.sleep(15)
    output = Path(os.environ["REPORT_PATH"])
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(document, ensure_ascii=True, indent=2).encode("ascii") + b"\n"
    if len(payload) > 2 * MAX_REPORT_BYTES:
        raise ValueError("CodeFactor serialized report exceeds bound")
    output.write_bytes(payload)
    print(json.dumps({key: value for key, value in document.items() if key != "annotations"}))
    # The report is diagnostic; the original CodeFactor check remains authoritative.
    return 0 if document["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
