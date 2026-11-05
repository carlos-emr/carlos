#!/usr/bin/env python3
"""Intersect a Git Java diff with JaCoCo source-line hits for an audit.

Usage:
  changed_line_audit.py JACOCO_XML BASE [HEAD] [--path PREFIX ...] [--per-file] [--fail-under PCT]

With HEAD omitted the working tree is compared with BASE, so a branch can be audited before it is
committed (new files need ``git add -N`` to appear in the diff).

``--path`` narrows the diff to production paths under the given prefixes (default: all of
src/main/java), so a feature PR can audit only the code it touched. ``--per-file`` lists every
changed file with its covered/missed counts and the missed line numbers. ``--fail-under`` exits
non-zero when changed-line coverage is below PCT percent, for use as a gate.
"""

import argparse
import codecs
import math
import json
from pathlib import Path
import re
import subprocess
import sys
from collections import defaultdict
from xml.parsers import expat


def read_jacoco_lines(filename, include_branches=False):
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
            counters = tuple(int(attrs.get(key, "0")) for key in ("ci", "mi", "cb", "mb"))
            if any(value < 0 for value in counters) or int(attrs["nr"]) < 1:
                raise ValueError("JaCoCo line counters must be nonnegative")
            source_lines[source][int(attrs["nr"])] = counters if include_branches else counters[:2]

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
            path = None
        elif line.startswith("+++ "):
            destination = line[4:]
            if destination.startswith('"'):
                quoted = re.match(r'^"((?:[^"\\]|\\.)*)"', destination)
                if quoted is None:
                    raise ValueError("Malformed quoted Git destination path")
                # Git quotes UTF-8 bytes with C/octal escapes, not Unicode code points.
                destination = codecs.escape_decode(quoted.group(1).encode("utf-8"))[0].decode(
                    "utf-8", errors="surrogateescape")
            else:
                destination = destination.split("\t", 1)[0]
            path = destination[2:] if destination.startswith("b/") else None
        elif line.startswith("@@ ") and path is not None:
            match = re.search(r"\+(\d+)(?:,(\d+))?", line)
            if match:
                start = int(match.group(1))
                length = int(match.group(2)) if match.group(2) is not None else 1
                changed[path].update(range(start, start + length))
    return changed


# Older name kept for callers written against the provider-linking audit.
parse_diff = changed_lines


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
            if hits is not None and sum(hits[:2]) > 0:
                if hits[0] > 0:
                    covered += 1
                else:
                    missed_lines.append(number)
        files[filename] = (covered, len(missed_lines), missed_lines)
    return files, unmapped


def audit_details(source_lines, changed):
    """Keep partial instructions and missed branches visible beside canonical line hits."""
    details = {}
    for filename, numbers in sorted(changed.items()):
        if filename not in source_lines:
            continue
        result = dict(covered_instructions=0, missed_instructions=0,
                      covered_branches=0, missed_branches=0,
                      partial_lines=[], missed_branch_lines=[])
        for number in sorted(numbers):
            counters = source_lines[filename].get(number)
            if counters is None:
                continue
            ci, mi, cb, mb = (*counters[:2], *(counters[2:] if len(counters) == 4 else (0, 0)))
            for field, count in zip(("covered_instructions", "missed_instructions",
                                     "covered_branches", "missed_branches"), (ci, mi, cb, mb)):
                result[field] += count
            if ci and mi:
                result["partial_lines"].append(number)
            if mb:
                result["missed_branch_lines"].append(number)
        details[filename] = result
    return details


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        epilog="With HEAD omitted the working tree is compared with BASE, so a branch can be "
               "audited before it is committed (new files need `git add -N` to appear in the diff).",
    )
    parser.add_argument("jacoco_xml", help="JaCoCo XML report, e.g. target/site/jacoco/jacoco.xml")
    parser.add_argument("base", help="base revision, e.g. origin/release/2026.08")
    parser.add_argument("head", nargs="?", help="head revision; omit to audit the working tree")
    parser.add_argument("--path", action="append", default=[],
                        help="restrict the diff to this src/main/java prefix (repeatable)")
    parser.add_argument("--per-file", action="store_true",
                        help="list every changed file with its missed line numbers")
    parser.add_argument("--fail-under", type=float, default=None, metavar="PCT",
                        help="exit 1 when changed-line coverage is below PCT percent")
    parser.add_argument("--fail-under-branches", type=float, default=None, metavar="PCT")
    parser.add_argument("--fail-under-instructions", type=float, default=None, metavar="PCT")
    parser.add_argument("--json", type=Path, help="write metadata-only per-file coverage evidence")
    args = parser.parse_args(argv)
    for flag in ("fail_under", "fail_under_branches", "fail_under_instructions"):
        value = getattr(args, flag)
        if value is not None and (not math.isfinite(value) or not 0 <= value <= 100):
            parser.error("--" + flag.replace("_", "-") + " must be a finite percentage from 0 to 100")
    return args


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    source_lines = read_jacoco_lines(args.jacoco_xml, include_branches=True)
    paths = args.path or ["src/main/java"]
    for prefix in paths:
        if (prefix != "src/main/java" and not prefix.startswith("src/main/java/")) or ".." in prefix.split("/"):
            raise SystemExit(f"--path must be under src/main/java: {prefix}")

    # Explicit prefixes keep parsing stable under diff.noprefix / diff.mnemonicPrefix configs.
    revisions = [args.base] + ([args.head] if args.head else [])
    diff = subprocess.check_output(
        ["git", "diff", "--no-ext-diff", "--src-prefix=a/", "--dst-prefix=b/", "-U0", *revisions, "--", *paths],
        text=True,
    )
    changed = changed_lines(diff)
    files, unmapped = audit(source_lines, changed)
    details = audit_details(source_lines, changed)

    covered_total = sum(covered for covered, _, _ in files.values())
    missed_total = sum(missed for _, missed, _ in files.values())
    zero = sorted(((missed, name) for name, (covered, missed, _) in files.items()
                   if covered == 0 and missed > 0), reverse=True)

    denominator = covered_total + missed_total
    percentage = f"{covered_total / denominator:.1%}" if denominator else "n/a"
    print(f"Changed executable Java lines: {covered_total} covered / {denominator} ({percentage})")
    print(f"Changed Java files: {len(files) + len(unmapped)}; unmapped by JaCoCo: {len(unmapped)}")
    print(f"Files with zero covered changed lines: {len(zero)}")
    totals = {field: sum(row[field] for row in details.values()) for field in (
        "covered_instructions", "missed_instructions", "covered_branches", "missed_branches")}
    print("Changed instructions: {covered_instructions} covered, {missed_instructions} missed; "
          "branches: {covered_branches} covered, {missed_branches} missed".format(**totals))
    for missed, filename in zero:
        print(f"  {missed:3} missed  {filename}")
    for filename in unmapped:
        print(f"  UNMAPPED  {filename}")
    if args.per_file:
        print("Per file (covered / executable, missed lines):")
        for filename, (covered, missed, missed_lines) in files.items():
            listed = ",".join(str(n) for n in missed_lines) or "-"
            print(f"  {covered:3} / {covered + missed:3}  {filename}  missed: {listed}")
            detail = details[filename]
            if detail["partial_lines"] or detail["missed_branch_lines"]:
                print(f"    partial instructions: {detail['partial_lines']}; missed branches: {detail['missed_branch_lines']}")

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(dict(base=args.base, head=args.head, covered_lines=covered_total,
                                            missed_lines=missed_total, totals=totals,
                                            unmapped=unmapped, files=details), indent=2) + "\n")

    thresholds = (args.fail_under, args.fail_under_branches, args.fail_under_instructions)
    if any(value is not None for value in thresholds) and unmapped:
        print("FAIL changed Java files are missing from the JaCoCo report")
        return 1
    if args.fail_under is not None and denominator and 100.0 * covered_total / denominator < args.fail_under:
        print(f"FAIL changed-line coverage {percentage} is below {args.fail_under:g}%")
        return 1
    for field, threshold in (("branches", args.fail_under_branches), ("instructions", args.fail_under_instructions)):
        covered = totals["covered_" + field]
        total = covered + totals["missed_" + field]
        if threshold is not None and total and 100 * covered / total < threshold:
            print(f"FAIL changed-{field} coverage {covered / total:.1%} is below {threshold:g}%")
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
