#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Build/verify the exact reviewed companion candidate without publishing a release."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from evidence_errors import EvidenceError
from lifecycle_baselines import candidate_cli

ROOT = Path(__file__).resolve().parents[2]


def specification():
    return candidate_cli(json.loads((ROOT / 'docs/testing/release-2026.08-lifecycle-baselines.json').read_text()))


def verify_package(path, spec):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size != spec['bytes']:
        raise EvidenceError('Candidate CLI package lacks its exact reviewed bytes')
    if hashlib.sha256(path.read_bytes()).hexdigest() != spec['sha256']:
        raise EvidenceError('Candidate CLI package differs from its reviewed SHA-256')
    metadata = subprocess.check_output(
        ['dpkg-deb', '-f', str(path), 'Package', 'Version', 'Architecture'], text=True)
    if metadata != 'Package: carlos-ctl\nVersion: ' + spec['version'] + '\nArchitecture: all\n':
        raise EvidenceError('Candidate CLI package control identity differs from its reviewed pin')
    return path


def git_text(source, *arguments):
    return subprocess.check_output(
        ['git', '-c', 'safe.directory=' + str(source), '-C', str(source), *arguments], text=True).strip()


def build_package(source, destination, spec):
    source = Path(source).resolve(strict=True)
    if spec.get('mode') != 'source-built':
        raise EvidenceError('Published CLI candidates must use the attested release fetcher')
    if (git_text(source, 'rev-parse', 'HEAD') != spec['sourceCommit']
            or git_text(source, 'status', '--porcelain', '--untracked-files=normal')):
        raise EvidenceError('Candidate CLI checkout differs from its exact clean source pin')
    subprocess.run(['dpkg-buildpackage', '-us', '-uc', '-b'], cwd=source, check=True)
    if git_text(source, 'diff', '--name-only', 'HEAD'):
        raise EvidenceError('Candidate CLI build changed tracked source')
    package = verify_package(source.parent / spec['name'], spec)
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / spec['name']
    if package.resolve() != target.resolve():
        shutil.copyfile(package, target)
    verify_package(target, spec)
    return target


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('describe', 'build', 'verify'))
    parser.add_argument('--github-output', type=Path)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--destination', type=Path)
    args = parser.parse_args(argv)
    spec = specification()
    if args.operation == 'describe':
        if args.github_output:
            with args.github_output.open('a') as output:
                output.write('source_built=' + str(spec.get('mode') == 'source-built').lower() + '\n')
                output.write('source_commit=' + spec.get('sourceCommit', '') + '\n')
                output.write('package_name=' + spec['name'] + '\n')
        print(json.dumps({'mode': spec.get('mode', 'published'), 'version': spec['version']}))
    elif args.destination is None:
        parser.error('--destination is required')
    elif args.operation == 'build':
        if args.source is None:
            parser.error('--source is required for build')
        build_package(args.source, args.destination, spec)
    else:
        verify_package(args.destination / spec['name'], spec)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
