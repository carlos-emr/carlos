#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
"""Refuse a CARLOS WAR that bundles an API Tomcat 11 supplies itself.

Tomcat always loads jakarta.el, jakarta.servlet, jakarta.annotation and its own
org.apache.* packages from its lib/ directory, so a copy in WEB-INF/lib is never
used in the container. It only misleads what runs outside Tomcat: issue #5416
found the Expression Language 5.0.1 API shipping beside Tomcat 11's EL 6.0.
This check reads the built artifact itself, so it also catches a copy that
arrives inside another jar or under a different Maven coordinate.

The rules (scripts/fixtures/tomcat-container-packages.json) are shared with
ContainerApiPackagingUnitTest, which applies them to the dependency graph on
every build. See docs/container-provided-apis.md.

Usage:
    check-war-container-apis.py [--rules FILE] WAR_OR_EXPLODED_WEBAPP...

Exit status: 0 clean, 1 a container API or competing implementation is
bundled (or JSTL is missing), 2 the input or the rules file is unusable.
"""

import argparse
import io
import json
import os
import re
import sys
import zipfile
import zlib

DEFAULT_RULES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "fixtures", "tomcat-container-packages.json")
LIB = "WEB-INF/lib/"
CLASSES = "WEB-INF/classes/"
# A multi-release jar keeps per-JDK classes under META-INF/versions/<n>/; Tomcat
# resolves those to the same class names, so they are checked as such.
VERSIONED = re.compile(r"^META-INF/versions/[0-9]+/")
# What reading a damaged or unsupported archive member can raise: a bad header or CRC, a broken
# deflate stream, an unsupported compression method, an encrypted entry, a truncated file.
ARCHIVE_ERRORS = (zipfile.BadZipFile, zlib.error, NotImplementedError, RuntimeError, EOFError, OSError)


class UnusableInput(Exception):
    """The rules file or an artifact cannot be read; never treated as a pass."""


def load_rules(path):
    try:
        with open(path, encoding="utf-8") as handle:
            rules = json.load(handle)
    except (OSError, ValueError) as exc:
        raise UnusableInput(f"cannot read rules {path}: {exc}") from exc
    for key in ("containerPackages", "applicationPackages", "containerServices", "requiredClasses"):
        value = rules.get(key)
        if not isinstance(value, list) or not all(isinstance(item, str) and item for item in value):
            raise UnusableInput(f"rules {path}: '{key}' must be a list of non-empty strings")
    if not rules["containerPackages"] or not rules["requiredClasses"]:
        raise UnusableInput(f"rules {path}: containerPackages and requiredClasses must not be empty")
    for key in ("containerPackages", "applicationPackages"):
        for prefix in rules[key]:
            if not prefix.endswith("/") or prefix.startswith("/"):
                raise UnusableInput(f"rules {path}: '{prefix}' in {key} must be a relative package path ending in '/'")
    return rules


def container_package(entry, rules):
    """The container package an entry belongs to, or None for an application class."""
    if not entry.endswith(".class"):
        return None
    name = VERSIONED.sub("", entry)
    if name.endswith("module-info.class"):
        return None
    if any(name.startswith(prefix) for prefix in rules["applicationPackages"]):
        return None
    for prefix in rules["containerPackages"]:
        if name.startswith(prefix):
            return name.rsplit("/", 1)[0] + "/"
    return None


def inspect_entries(origin, entries, rules, findings, found_required):
    """Record container classes and competing service files among one archive's entries."""
    packages = set()
    for entry in entries:
        package = container_package(entry, rules)
        if package:
            packages.add(package)
        if entry in rules["containerServices"]:
            findings.append(f"{origin}: {entry} installs a competing container implementation")
        name = VERSIONED.sub("", entry)
        if name in rules["requiredClasses"]:
            found_required.add(name)
    for package in sorted(packages):
        findings.append(f"{origin}: bundles container package {package.rstrip('/').replace('/', '.')}")


def jar_entries(origin, data):
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            return jar.namelist()
    except ARCHIVE_ERRORS as exc:
        raise UnusableInput(f"{origin} is not a readable jar: {exc}") from exc


def read_member(war, path, name):
    try:
        return war.read(name)
    except ARCHIVE_ERRORS as exc:
        raise UnusableInput(f"cannot read {name} from {path}: {exc}") from exc


def check_war_file(path, rules):
    findings, found_required, jars = [], set(), 0
    try:
        war = zipfile.ZipFile(path)
    except ARCHIVE_ERRORS as exc:
        raise UnusableInput(f"cannot open {path}: {exc}") from exc
    with war:
        names = war.namelist()
        classes = [name[len(CLASSES):] for name in names if name.startswith(CLASSES) and not name.endswith("/")]
        inspect_entries(f"{path}!{CLASSES}", classes, rules, findings, found_required)
        for name in names:
            if name.startswith(LIB) and name.endswith(".jar") and "/" not in name[len(LIB):]:
                jars += 1
                data = read_member(war, path, name)
                inspect_entries(f"{path}!{name}", jar_entries(name, data), rules, findings, found_required)
    return findings, found_required, jars


def check_exploded(path, rules):
    findings, found_required, jars = [], set(), 0
    classes_dir = os.path.join(path, *CLASSES.rstrip("/").split("/"))
    lib_dir = os.path.join(path, *LIB.rstrip("/").split("/"))
    if not os.path.isdir(lib_dir):
        raise UnusableInput(f"{path} has no {LIB}: not an exploded web application")
    classes = []
    for root, _dirs, files in os.walk(classes_dir):
        for file in files:
            classes.append(os.path.relpath(os.path.join(root, file), classes_dir).replace(os.sep, "/"))
    inspect_entries(os.path.join(path, CLASSES), classes, rules, findings, found_required)
    for name in sorted(os.listdir(lib_dir)):
        if name.endswith(".jar"):
            jars += 1
            jar = os.path.join(lib_dir, name)
            try:
                with open(jar, "rb") as handle:
                    data = handle.read()
            except OSError as exc:
                raise UnusableInput(f"cannot read {jar}: {exc}") from exc
            inspect_entries(jar, jar_entries(jar, data), rules, findings, found_required)
    return findings, found_required, jars


def check(path, rules):
    if os.path.isdir(path):
        findings, found_required, jars = check_exploded(path, rules)
    elif os.path.isfile(path):
        findings, found_required, jars = check_war_file(path, rules)
    else:
        raise UnusableInput(f"{path} does not exist")
    if jars == 0:
        raise UnusableInput(f"{path} has no {LIB}*.jar: not a CARLOS web application")
    for required in rules["requiredClasses"]:
        if required not in found_required:
            findings.append(f"{path}: {required} is missing; Tomcat provides no JSTL, so the WAR must ship it")
    return findings, jars


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--rules", default=DEFAULT_RULES, help="rules JSON (default: %(default)s)")
    parser.add_argument("artifacts", nargs="+", metavar="WAR_OR_EXPLODED_WEBAPP")
    args = parser.parse_args(argv)
    status = 0
    try:
        rules = load_rules(args.rules)
        for artifact in args.artifacts:
            findings, jars = check(artifact, rules)
            if findings:
                status = 1
                for finding in findings:
                    print(f"FAIL {finding}", file=sys.stderr)
            else:
                print(f"PASS {artifact}: {jars} jars, no container-supplied API bundled, JSTL present")
    except UnusableInput as exc:
        print(f"ERROR {exc}", file=sys.stderr)
        return 2
    if status:
        print("Container APIs belong to Tomcat: give the dependency <scope>provided</scope> "
              "(see docs/container-provided-apis.md).", file=sys.stderr)
    return status


if __name__ == "__main__":
    sys.exit(main())
