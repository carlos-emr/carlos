#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Verify deployed classes, dependency jars and locale bundles against the qualified WAR."""
import hashlib
import json
from pathlib import Path
import sys
import subprocess
import tarfile
import zipfile


def prepare(war_path, package_path, output_path):
    """Account for the two explicit Debian packaging rewrites using actual package bytes."""
    files = {}
    with zipfile.ZipFile(war_path) as archive:
        for item in archive.infolist():
            name = item.filename
            if not item.is_dir() and (name.startswith("WEB-INF/classes/")
                    or name.startswith("WEB-INF/lib/") and name.endswith(".jar")
                    or name == "WEB-INF/web.xml" or name.endswith(".tld")):
                files[name] = hashlib.sha256(archive.read(name)).hexdigest()
        properties = archive.read("WEB-INF/classes/buildNumber.properties").decode().splitlines()
        revision = next(line.split("=", 1)[1] for line in properties if line.startswith("git-sha-1="))
    wanted = {"WEB-INF/classes/carlos-build.properties", "WEB-INF/classes/jdbc-defaults.properties"}
    overrides = {}
    process = subprocess.Popen(["dpkg-deb", "--fsys-tarfile", str(package_path)], stdout=subprocess.PIPE)
    with tarfile.open(fileobj=process.stdout, mode="r|") as archive:
        for member in archive:
            relative = member.name.removeprefix("./usr/share/carlos-emr/webapp/carlos/")
            if member.isfile() and relative in wanted:
                digest = hashlib.sha256(archive.extractfile(member).read()).hexdigest()
                overrides[relative] = {"warSha256": files[relative], "packageSha256": digest}
                files[relative] = digest
    if process.wait() != 0 or set(overrides) != wanted:
        raise SystemExit("FAIL qualified package does not contain the declared configuration files")
    with war_path.open("rb") as stream:
        war_digest = hashlib.file_digest(stream, "sha256").hexdigest()
    with package_path.open("rb") as stream:
        package_digest = hashlib.file_digest(stream, "sha256").hexdigest()
    result = {"runtimeRevision": revision, "warSha256": war_digest, "packageSha256": package_digest,
              "files": files, "packagingOverrides": overrides}
    output_path.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print("PASS prepared qualified runtime manifest:", len(files), "files")


if len(sys.argv) == 5 and sys.argv[1] == "--prepare":
    prepare(*map(Path, sys.argv[2:]))
    raise SystemExit(0)

expected_path, deployed_path, output_path = map(Path, sys.argv[1:])
expected = json.loads(expected_path.read_text())
root = deployed_path.resolve()
failures = []
for relative, digest in expected['files'].items():
    candidate = (root / relative).resolve()
    if not candidate.is_relative_to(root) or not candidate.is_file():
        failures.append(relative + ': absent or outside deployed root')
    elif hashlib.sha256(candidate.read_bytes()).hexdigest() != digest:
        failures.append(relative + ': content mismatch')
expected_classes = {name for name in expected['files'] if name.endswith('.class')}
actual_classes = {str(path.relative_to(root)) for path in (root / 'WEB-INF/classes').rglob('*.class')}
expected_jars = {name for name in expected['files'] if name.endswith('.jar')}
actual_jars = {str(path.relative_to(root)) for path in (root / 'WEB-INF/lib').glob('*.jar')}
for extra in sorted((actual_classes - expected_classes) | (actual_jars - expected_jars)):
    failures.append(extra + ': unexpected deployed class/jar')
receipt = {'runtimeRevision': expected['runtimeRevision'], 'warSha256': expected['warSha256'],
           'expectedManifestSha256': hashlib.sha256(expected_path.read_bytes()).hexdigest(),
           'packageSha256': expected.get('packageSha256'), 'packagingOverrides': expected.get('packagingOverrides', {}),
           'verifiedFiles': len(expected['files']), 'verifiedClasses': len(expected_classes),
           'verifiedJars': len(expected_jars), 'failures': failures}
output_path.write_text(json.dumps(receipt, indent=2) + '\n')
if failures:
    raise SystemExit('FAIL deployed runtime differs from the qualified artifact; see receipt')
print('PASS deployed runtime matches the qualified WAR:', len(expected_classes), 'classes,', len(expected_jars), 'jars')
