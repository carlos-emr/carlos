#!/usr/bin/env python3
"""Intersect a Git Java diff with JaCoCo source-line hits for an audit."""

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


def main():
    source_lines = read_jacoco_lines(sys.argv[1])

    diff = subprocess.check_output(
        ["git", "diff", "--no-ext-diff", "-U0", sys.argv[2], sys.argv[3], "--", "src/main/java"],
        text=True,
    )
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

    totals = [0, 0]
    zero = []
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
        totals[0] += covered
        totals[1] += missed
        if covered == 0 and missed > 0:
            zero.append((missed, filename))

    denominator = sum(totals)
    print(f"Changed executable Java lines: {totals[0]} covered / {denominator} ({totals[0] / denominator:.1%})")
    print(f"Changed Java files: {len(changed)}; unmapped by JaCoCo: {len(unmapped)}")
    print(f"Files with zero covered changed lines: {len(zero)}")
    for missed, filename in sorted(zero, reverse=True):
        print(f"  {missed:3} missed  {filename}")
    for filename in unmapped:
        print(f"  UNMAPPED  {filename}")


if __name__ == "__main__":
    main()
