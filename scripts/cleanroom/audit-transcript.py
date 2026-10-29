#!/usr/bin/env python3
#
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
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#
"""Check a clean-room implementer's Claude Code transcript for contact with the original.

Reads one or more session transcripts (JSONL) and lists every tool call that reached
outside the clean export: a path in the original repository, a web search or fetch,
a git history command, a network download, or a built-in file tool aimed outside the
export. It also reports a session whose working directory was not the export, since
relative paths there resolve somewhere else. Used by /cleanroom-spec:cleanroom-audit.

Usage:
  python3 scripts/cleanroom/audit-transcript.py --repo <original-repo> \
      --export <export-dir> <transcript.jsonl>...

Exit status: 0 = no findings, 1 = findings (each must be recorded and judged),
2 = bad input (e.g. a transcript with no tool calls, which proves nothing).
"""

import argparse
import json
import re
import sys

WEB_TOOLS = {"WebFetch", "WebSearch"}
# Built-in file tools and the input keys that hold their target path.
FILE_TOOLS = {"Read": "file_path", "Write": "file_path", "Edit": "file_path",
              "MultiEdit": "file_path", "NotebookEdit": "notebook_path",
              "Glob": "path", "Grep": "path"}
# History or remote access that could surface the original file's text.
RISKY_SHELL = re.compile(
    r"\bgit\s+(?:-C\s+\S+\s+)?(?:log|show|blame|diff|cat-file|checkout|restore|reflog|fetch|clone|pull|archive)\b"
    r"|\b(?:curl|wget)\b"
)


def tool_calls(path, cwds):
    """Yield (tool name, input dict) for every tool call in a transcript.

    Also collects every working directory the transcript records into ``cwds``.
    """
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("cwd"):
                cwds.add(entry["cwd"])
            message = entry.get("message") or {}
            content = message.get("content")
            if not isinstance(content, list):
                continue
            for block in content:
                if isinstance(block, dict) and block.get("type") == "tool_use":
                    yield block.get("name", "?"), block.get("input") or {}


def outside_export(path, export):
    """True for an absolute path that is not inside the export (relative paths resolve
    against the working directory, which is checked separately)."""
    if not path or not path.startswith("/"):
        return False
    path = path.rstrip("/")
    return path != export and not path.startswith(export + "/")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repo", required=True, help="absolute path of the original repository")
    parser.add_argument("--export", required=True, help="absolute path of the clean export")
    parser.add_argument("transcripts", nargs="+")
    args = parser.parse_args()

    repo = args.repo.rstrip("/")
    export = args.export.rstrip("/")
    findings = []
    total = 0
    for transcript in args.transcripts:
        cwds = set()
        for name, data in tool_calls(transcript, cwds):
            total += 1
            text = json.dumps(data, ensure_ascii=False)
            # Paths inside the export may share a prefix with the repo path; strip them first.
            outside = text.replace(export, "<export>")
            if name in WEB_TOOLS:
                findings.append((transcript, name, "web access", text))
            elif repo in outside:
                findings.append((transcript, name, "touched the original repository", text))
            elif name == "Bash" and RISKY_SHELL.search(data.get("command", "")):
                findings.append((transcript, name, "git history or network command", text))
            elif name in FILE_TOOLS and outside_export(data.get(FILE_TOOLS[name]), export):
                findings.append((transcript, name, "file access outside the export", text))
        # Relative paths resolve against the working directory, so a session that ran
        # anywhere but the export could have read the original without naming its path.
        for cwd in sorted(cwds):
            if cwd.rstrip("/") != export and not cwd.startswith(export + "/"):
                findings.append((transcript, "session", "ran outside the export", f"cwd={cwd}"))

    if total == 0:
        print("No tool calls found: wrong file, or not a Claude Code transcript.", file=sys.stderr)
        return 2
    print(f"Tool calls checked: {total}")
    print(f"Findings: {len(findings)}")
    for transcript, name, reason, text in findings:
        print(f"- [{reason}] {name}: {text[:300]}  ({transcript})")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
