#!/usr/bin/env python3
"""Intersect a Git Java diff with JaCoCo source-line hits for an audit."""

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path


def main():
    report = ET.parse(sys.argv[1]).getroot()
    source_lines = {}
    for package in report.findall("package"):
        prefix = package.attrib["name"]
        for source in package.findall("sourcefile"):
            key = f"src/main/java/{prefix}/{source.attrib['name']}"
            source_lines[key] = {
                int(line.attrib["nr"]): (int(line.attrib["ci"]), int(line.attrib["mi"]))
                for line in source.findall("line")
            }

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
