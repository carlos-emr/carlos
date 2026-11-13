#!/usr/bin/env python3
# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Execute the reviewed matrix against a frozen, already-built tagged candidate."""
import argparse
import os
from pathlib import Path
from release_publication import candidate, checked_tag, collect_evidence, download_assets, required_ci, verify_provenance
from release_verification import DEFAULT_PLAN, load_plan, run_check, verify, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source = checked_tag(args.tag)
    args.output.mkdir(parents=True, exist_ok=False)
    assets = args.output.parent / "candidate-assets"
    download_assets(args.repo, args.tag, assets)
    frozen = candidate(assets, args.tag, source)
    verify_provenance(args.repo, source, assets)
    required_ci(args.repo, source["commit"])
    war = assets / f"carlos-{args.tag}.war"
    os.environ["CARLOS_RELEASE_CANDIDATE_WAR"] = str(war.resolve())
    os.environ["CARLOS_RELEASE_DEB_VERSION"] = args.tag.replace("-", "~")
    evidence = args.output.parent / "private-qualification-evidence"
    plan = load_plan(DEFAULT_PLAN)
    required = {name for row in plan["workstreams"] for name in row["requiredChecks"]}
    for check in plan["checks"]:
        if check["id"] not in required:
            continue
        for profile in check["profiles"]:
            for iteration in range(1, check.get("repetitions", 1) + 1):
                run_check(DEFAULT_PLAN, check["id"], evidence, profile, iteration, war)
    result = verify(DEFAULT_PLAN, evidence, war)
    if candidate(assets, args.tag, source) != frozen:
        raise SystemExit("Candidate changed during qualification")
    collect_evidence(evidence, args.output / "evidence")
    # Validate the sanitized upload itself before it can authorize publication.
    if verify(DEFAULT_PLAN, args.output / "evidence", war) != result:
        raise SystemExit("Collected qualification differs from the executed evidence")
    write_json(args.output / "readiness.json", result)
    write_json(args.output / "release-candidate.json", frozen)
    return 0 if result["releaseReady"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
