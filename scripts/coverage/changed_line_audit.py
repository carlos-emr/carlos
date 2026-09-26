#!/usr/bin/env python3
"""Intersect a Git Java diff with JaCoCo source-line hits for an audit."""

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from xml.parsers import expat


def read_jacoco_lines(filename):
    """Stream JaCoCo lines while rejecting DTDs and external entities."""
    source_lines = {}
    parser = expat.ParserCreate()
    package = source = None

    def start_element(name, attrs):
        nonlocal package, source
        if name == "package":
            package = attrs["name"]
        elif name == "sourcefile" and package is not None:
            source = f"src/main/java/{package}/{attrs['name']}"
            source_lines[source] = {}
        elif name == "line" and source is not None:
            source_lines[source][int(attrs["nr"])] = (int(attrs["ci"]), int(attrs["mi"]))

    def end_element(name):
        nonlocal package, source
        if name == "sourcefile":
            source = None
        elif name == "package":
            package = None

    def check_doctype(name, system_id, public_id, has_internal_subset):
        if (name, system_id, public_id, has_internal_subset) != (
                "report", "report.dtd", "-//JACOCO//DTD Report 1.1//EN", 0):
            raise ValueError("JaCoCo XML contains an unexpected DTD")

    def reject_entity(*_args):
        raise ValueError("JaCoCo XML must not contain entity declarations or external entities")

    parser.StartElementHandler = start_element
    parser.EndElementHandler = end_element
    parser.StartDoctypeDeclHandler = check_doctype
    parser.EntityDeclHandler = reject_entity
    parser.ExternalEntityRefHandler = reject_entity
    with open(filename, "rb") as stream:
        parser.ParseFile(stream)
    return source_lines


def changed_lines(diff):
    """Map each file in a unified -U0 diff to the new-side line numbers it adds or changes."""
    changed = defaultdict(set)
    path = None
    for line in diff.splitlines():
        if line.startswith("diff --git "):
            path = line.split(" b/", 1)[1]
        elif line.startswith("@@ ") and path is not None:
            match = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if match:
                start = int(match.group(1))
                length = int(match.group(2)) if match.group(2) is not None else 1
                changed[path].update(range(start, start + length))
    return changed


def audit(source_lines, changed):
    """Count covered/missed changed lines per file; lines JaCoCo did not instrument are ignored."""
    per_file = {}
    unmapped = []
    for filename, lines in sorted(changed.items()):
        if filename not in source_lines:
            unmapped.append(filename)
            continue
        covered = missed = 0
        for number in lines:
            hits = source_lines[filename].get(number)
            if hits is not None and sum(hits) > 0:
                if hits[0] > 0:
                    covered += 1
                else:
                    missed += 1
        per_file[filename] = (covered, missed)
    return per_file, unmapped


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Intersect a Git Java diff with JaCoCo source-line hits.",
        epilog="With HEAD omitted the working tree is compared with BASE, so a branch can be "
               "audited before it is committed (new files need `git add -N` to appear in the diff).",
    )
    parser.add_argument("jacoco_xml", help="JaCoCo XML report, e.g. target/site/jacoco/jacoco.xml")
    parser.add_argument("base", help="base revision, e.g. origin/release/2026.08")
    parser.add_argument("head", nargs="?", help="head revision; omit to audit the working tree")
    parser.add_argument("--per-file", action="store_true",
                        help="print covered/total for every changed file, not only fully missed ones")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    source_lines = read_jacoco_lines(args.jacoco_xml)

    revisions = [args.base] + ([args.head] if args.head else [])
    diff = subprocess.check_output(
        ["git", "diff", "--no-ext-diff", "-U0", *revisions, "--", "src/main/java"],
        text=True,
    )
    changed = changed_lines(diff)
    per_file, unmapped = audit(source_lines, changed)

    covered_total = sum(covered for covered, _ in per_file.values())
    missed_total = sum(missed for _, missed in per_file.values())
    zero = [(missed, filename) for filename, (covered, missed) in per_file.items()
            if covered == 0 and missed > 0]

    denominator = covered_total + missed_total
    percentage = f"{covered_total / denominator:.1%}" if denominator else "n/a"
    print(f"Changed executable Java lines: {covered_total} covered / {denominator} ({percentage})")
    print(f"Changed Java files: {len(changed)}; unmapped by JaCoCo: {len(unmapped)}")
    if args.per_file:
        for filename, (covered, missed) in per_file.items():
            print(f"  {covered:4} / {covered + missed:<4} {filename}")
    print(f"Files with zero covered changed lines: {len(zero)}")
    for missed, filename in sorted(zero, reverse=True):
        print(f"  {missed:3} missed  {filename}")
    for filename in unmapped:
        print(f"  UNMAPPED  {filename}")


if __name__ == "__main__":
    main()
