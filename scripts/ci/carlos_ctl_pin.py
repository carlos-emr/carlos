#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Check out the exact carlos-ctl source named by the Debian release pin."""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile

from git_environment import separate_checkout_environment


PIN = Path(__file__).resolve().parents[2] / "debian/carlos-ctl.pin"
REPOSITORY = "https://github.com/carlos-emr/carlos-ctl.git"


def read_pin():
    fields = {}
    for line in PIN.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition("=")
        if key not in {"tag", "fallback"} or key in fields:
            raise ValueError(f"{PIN} must contain one tag and one fallback commit")
        fields[key] = value
    tag = fields.get("tag", "")
    commit = fields.get("fallback", "")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?", tag):
        raise ValueError(f"{PIN} must name a carlos-ctl release tag")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError(f"{PIN} must name a full carlos-ctl commit in fallback=")
    return tag, commit


def git(source, *args, check=True):
    return subprocess.run(["git", *args], cwd=source, env=separate_checkout_environment(),
                          check=check, stdout=subprocess.PIPE, text=True)


def verify_commit(source, ref, expected):
    actual = git(source, "rev-parse", "--verify", f"{ref}^{{commit}}").stdout.strip()
    if actual != expected:
        raise ValueError(f"carlos-ctl {ref} is at {actual}. {PIN.name} requires {expected}.")


def verify_checkout(source):
    tag, expected = read_pin()
    tag_ref = "refs/tags/" + tag
    verify_commit(source, "HEAD", expected)
    local_tag = git(source, "show-ref", "--verify", "--quiet", tag_ref, check=False)
    if local_tag.returncode != 1:
        local_tag.check_returncode()
        verify_commit(source, tag_ref, expected)


def checkout(destination, repository):
    tag, expected = read_pin()
    tag_ref = "refs/tags/" + tag
    if destination.exists() or destination.is_symlink():
        raise ValueError(f"carlos-ctl checkout destination already exists: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="carlos-ctl-", dir=destination.parent) as temporary:
        source = Path(temporary) / "source"
        source.mkdir()
        git(source, "init", "--quiet")
        git(source, "remote", "add", "origin", repository)
        published = git(source, "ls-remote", "--exit-code", "--refs", "--tags", "origin",
                        tag_ref, check=False)
        if published.returncode == 2:
            git(source, "fetch", "--quiet", "--depth=1", "--no-tags", "origin", expected)
            verify_commit(source, "FETCH_HEAD", expected)
        else:
            published.check_returncode()
            git(source, "fetch", "--quiet", "--depth=1", "--no-tags", "origin", f"{tag_ref}:{tag_ref}")
            verify_commit(source, tag_ref, expected)
        git(source, "checkout", "--quiet", "--detach", expected)
        source.rename(destination)

    if published.returncode == 2:
        print(f"::warning::carlos-ctl tag {tag} is unpublished. Testing pinned commit {expected}.")
    else:
        print(f"carlos-ctl {tag} verified at {expected}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        checkout(args.destination, REPOSITORY)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"::error::{error}\n")


if __name__ == "__main__":
    main()
