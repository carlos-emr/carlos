# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The import's closing report: what arrived, what did not, and where the
rest went.

`report.txt` is the running phase log -- appended to as each phase
finishes, ordered by when things happened, and (before this) the only
artifact an operator had. It is a good log and a poor report: no header,
no ordering by importance, no machine-readable twin, and per-table row
counts written only when they were WRONG, so a clean import discarded the
very numbers a reviewer needs.

This module renders the other thing: one document, built from structured
data at the end of verification, that a human can use to decide whether
the migration is sound. It deliberately mirrors `o19_preflight`'s report
model -- a header, a verdict line, sections, then findings ordered by
severity -- because an operator meets the preflight report first and
should not have to learn a second shape.

Nothing here queries the database or reads the filesystem: the caller
supplies the facts, which is what makes the whole document testable.
"""

import json
from typing import Dict, List, Optional, Sequence, Tuple

#: Severity vocabulary, most serious first. `failure` is what failed
#: verification; `advisory` is what a human must look at before go-live
#: (a rotated credential, a dangling reference the source already had);
#: `info` is what was decided and is worth recording.
SEVERITIES = ("failure", "advisory", "info")

WIDTH = 72


def finding(severity: str, title: str,
            lines: Sequence[str] = ()) -> Dict:
    """One entry of the findings block.

    `lines` are the itemised detail (one table, one column, one patient
    count); they are rendered indented under the title and kept as a list
    in the JSON twin so a reviewer can diff two imports."""
    if severity not in SEVERITIES:
        raise ValueError("unknown severity: {0}".format(severity))
    return {"severity": severity, "title": title, "lines": list(lines)}


def section(title: str, lines: Sequence[str],
            empty: Optional[str] = None) -> Optional[Dict]:
    """One section of the body, or None when there is nothing to say and
    no `empty` placeholder was given.

    A section that would render as a heading over nothing is worse than
    no section: it reads as an omission."""
    lines = [ln for ln in lines if ln]
    if not lines:
        if empty is None:
            return None
        lines = [empty]
    return {"title": title, "lines": list(lines)}


def build(header: Dict, verdict: str, sections: Sequence[Optional[Dict]],
          findings: Sequence[Dict],
          next_steps: Sequence[str] = ()) -> Dict:
    """The structured report both renderings are made from."""
    return {
        "kind": "carlos-o19-import-report",
        "version": 1,
        "header": dict(header),
        "verdict": verdict,
        "sections": [s for s in sections if s],
        "findings": sorted(findings,
                           key=lambda f: SEVERITIES.index(f["severity"])),
        "next_steps": list(next_steps),
    }


def render_text(report: Dict) -> str:
    """The human-readable rendering. The JSON twin is `render_json`."""
    out = ["OSCAR 19 -> CARLOS import report (experimental)", "=" * WIDTH]
    for key, value in _header_pairs(report["header"]):
        out.append("{0:<22}{1}".format(key + ":", value))
    out.append("-" * WIDTH)
    out.append("VERDICT: " + report["verdict"])
    for sec in report["sections"]:
        out.append("")
        out.append(sec["title"])
        out.append("-" * len(sec["title"]))
        for line in sec["lines"]:
            out.append("  " + line)
    if report["findings"]:
        out.append("")
        out.append("FINDINGS")
        out.append("-" * len("FINDINGS"))
        for f in report["findings"]:
            out.append("[{0}] {1}".format(f["severity"].upper(),
                                          f["title"]))
            for line in f["lines"]:
                out.append("    " + line)
    if report["next_steps"]:
        out.append("")
        out.append("NEXT STEPS")
        out.append("-" * len("NEXT STEPS"))
        for i, step in enumerate(report["next_steps"], 1):
            out.append("{0}. {1}".format(i, step))
    out.append("")
    out.append("Migration output must receive a technical review before "
               "clinical use.")
    return "\n".join(out) + "\n"


def render_json(report: Dict) -> str:
    """The machine-readable twin: stable key order, trailing newline."""
    return json.dumps(report, indent=2, sort_keys=True,
                      ensure_ascii=False) + "\n"


#: header keys in the order an operator reads them, with their labels
HEADER_ORDER = (
    ("target_db", "target schema"),
    ("province", "province"),
    ("manifest", "schema manifest"),
    ("manifest_props", "properties manifest"),
    ("o19_source_commit", "manifest built from"),
    ("dump_sha256", "dump sha256"),
    ("tool_version", "carlos-ctl"),
    ("started", "import started"),
    ("finished", "report written"),
)


def _header_pairs(header: Dict) -> List[Tuple[str, str]]:
    """(label, value) for every header field that has a value, in reading
    order; anything the caller adds beyond the known keys follows."""
    out = [(label, header[key]) for key, label in HEADER_ORDER
           if header.get(key)]
    known = {key for key, _label in HEADER_ORDER}
    out.extend((k, header[k]) for k in sorted(header)
               if k not in known and header[k])
    return out
