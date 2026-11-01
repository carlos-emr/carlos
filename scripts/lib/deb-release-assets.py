#!/usr/bin/env python3
"""Download the exact published package set and verify GitHub's SHA-256 digests."""
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import hashlib
import json
import os
import pathlib
import re
import sys
import urllib.request


def select_packages(release, tag):
    if release.get("tag_name") != tag or release.get("draft"):
        raise ValueError("release identity does not match the requested published tag")
    result = []
    for pattern in (r"carlos-emr_[A-Za-z0-9.+~_-]+_amd64\.deb", r"carlos-emr-drugref_[A-Za-z0-9.+~_-]+_all\.deb", r"carlos-ctl_[A-Za-z0-9.+~_-]+_all\.deb"):
        candidates = [a for a in release["assets"] if re.fullmatch(pattern, a["name"])]
        if len(candidates) != 1:
            raise ValueError("release must contain exactly one package of each required type")
        asset = candidates[0]
        expected_url = f"https://github.com/carlos-emr/carlos/releases/download/{tag}/{asset['name']}"
        if asset.get("browser_download_url") != expected_url or not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest", "")):
            raise ValueError("release package URL or SHA-256 digest is invalid")
        result.append(asset)
    return result


def main():
    tag, destination = sys.argv[1:]
    if not re.fullmatch(r"2026\.08\.\d+(?:-[A-Za-z0-9.]+)?", tag):
        raise ValueError("from-version must be a 2026.08 release tag")
    directory = pathlib.Path(destination)
    directory.mkdir(parents=True, exist_ok=True)
    if os.environ.get("RELEASE_METADATA"):
        release = json.loads(pathlib.Path(os.environ["RELEASE_METADATA"]).read_text())
    else:
        with urllib.request.urlopen(f"https://api.github.com/repos/carlos-emr/carlos/releases/tags/{tag}", timeout=60) as response:
            release = json.load(response)
    for asset in select_packages(release, tag):
        target = directory / asset["name"]
        expected = asset["digest"].split(":", 1)[1]
        if target.exists() and hashlib.file_digest(target.open("rb"), "sha256").hexdigest() == expected:
            continue
        temporary = target.with_suffix(".partial")
        try:
            digest = hashlib.sha256()
            with urllib.request.urlopen(asset["browser_download_url"], timeout=120) as source, temporary.open("wb") as output:
                while block := source.read(1 << 20):
                    output.write(block)
                    digest.update(block)
            if digest.hexdigest() != expected:
                raise ValueError(f"SHA-256 mismatch: {asset['name']}")
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        print(f"verified {asset['name']}")
    (directory / "release.json").write_text(json.dumps(release, indent=2) + "\n")


if __name__ == "__main__":
    main()
