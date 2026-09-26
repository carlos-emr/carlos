#!/usr/bin/env python3
"""Intersect a Git Java diff with JaCoCo source-line hits for an audit.

Usage:
  changed_line_audit.py JACOCO_XML BASE HEAD [--path PREFIX ...] [--per-file] [--fail-under PCT]

``--path`` narrows the diff to production paths under the given prefixes (default: all of
src/main/java), so a feature PR can audit only the code it touched. ``--per-file`` lists every
changed file with its covered/missed counts and the missed line numbers. ``--fail-under`` exits
non-zero when changed-line coverage is below PCT percent, for use as a gate.
"""

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


def parse_diff(diff):
    """Map each changed file to the set of line numbers the diff adds or modifies."""
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
    """Return per-file (covered, missed, missed line numbers) plus the unmapped files."""
    files = {}
    unmapped = []
    for filename, lines in sorted(changed.items()):
        if filename not in source_lines:
            unmapped.append(filename)
            continue
        covered = 0
        missed_lines = []
        for number in sorted(lines):
            hits = source_lines[filename].get(number)
            if hits is not None and sum(hits) > 0:
                if hits[0] > 0:
                    covered += 1
                else:
                    missed_lines.append(number)
        files[filename] = (covered, len(missed_lines), missed_lines)
    return files, unmapped


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("jacoco_xml")
    parser.add_argument("base")
    parser.add_argument("head")
    parser.add_argument("--path", action="append", default=[],
                        help="restrict the diff to this src/main/java prefix (repeatable)")
    parser.add_argument("--per-file", action="store_true",
                        help="list every changed file with its missed line numbers")
    parser.add_argument("--fail-under", type=float, default=None, metavar="PCT",
                        help="exit 1 when changed-line coverage is below PCT percent")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    source_lines = read_jacoco_lines(args.jacoco_xml)
    paths = args.path or ["src/main/java"]
    for prefix in paths:
        if not prefix.startswith("src/main/java"):
            raise SystemExit(f"--path must be under src/main/java: {prefix}")

    diff = subprocess.check_output(
        ["git", "diff", "--no-ext-diff", "-U0", args.base, args.head, "--", *paths],
        text=True,
    )
    files, unmapped = audit(source_lines, parse_diff(diff))

    covered_total = sum(covered for covered, _, _ in files.values())
    missed_total = sum(missed for _, missed, _ in files.values())
    zero = sorted(((missed, name) for name, (covered, missed, _) in files.items()
                   if covered == 0 and missed > 0), reverse=True)

    denominator = covered_total + missed_total
    percentage = f"{covered_total / denominator:.1%}" if denominator else "n/a"
    print(f"Changed executable Java lines: {covered_total} covered / {denominator} ({percentage})")
    print(f"Changed Java files: {len(files) + len(unmapped)}; unmapped by JaCoCo: {len(unmapped)}")
    print(f"Files with zero covered changed lines: {len(zero)}")
    for missed, filename in zero:
        print(f"  {missed:3} missed  {filename}")
    for filename in unmapped:
        print(f"  UNMAPPED  {filename}")
    if args.per_file:
        print("Per file (covered / executable, missed lines):")
        for filename, (covered, missed, missed_lines) in files.items():
            if covered + missed == 0:
                continue
            listed = ",".join(str(n) for n in missed_lines) or "-"
            print(f"  {covered:3} / {covered + missed:3}  {filename}  missed: {listed}")

    if args.fail_under is not None and denominator and 100.0 * covered_total / denominator < args.fail_under:
        print(f"FAIL changed-line coverage {percentage} is below {args.fail_under:g}%")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
