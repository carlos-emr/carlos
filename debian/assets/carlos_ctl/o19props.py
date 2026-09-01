# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Props phase (P6) of the OSCAR 19 importer (experimental).

Translates the clinic's deployed oscar.properties into a REVIEWED fragment
for /etc/carlos-emr/carlos.properties. Never merged automatically — the
fragment sits in the import state dir until an operator reviews and applies
it (docs plan §8.1: this is deliberately the one non-automatic step).

Rules, in order:
 1. Baseline-diff: keys equal to the stock O19 default are ignored —
    CARLOS defaults win wherever the clinic never made a choice.
 2. Disposition from the generated manifest (o19map_props): exact KEYS
    first, then the ordered PREFIX_RULES; anything unmatched is `unknown`
    (reported for human classification, never silently carried/dropped).
 3. carry / carry-secret go into the fragment verbatim (secrets masked in
    the human report only); translate rewrites OscarDocument context paths
    onto the CARLOS tree and resolves drugref to the deployment's own
    endpoint; deploy-owned is refused with a note; dropped-flag is
    itemized by module advisory (ldap escalated already at preflight).
"""

import os
import re
import time
from typing import Dict, List, Optional, Tuple

from . import o19map_props
from .util import PROPERTIES, STATE, prop_get

DOCUMENTS_ROOT = os.path.join(STATE, "OscarDocument")
TARGET_CTX = "carlos"

MASK = "********"


# --------------------------------------------------------------------------
# parsing / dispatch (pure)
# --------------------------------------------------------------------------

def load_clinic_properties(path: str) -> List[Tuple[str, str]]:
    """Ordered active key=value pairs; a repeated key keeps its LAST value
    (java.util.Properties semantics) but its first position."""
    order: List[str] = []
    values: Dict[str, str] = {}
    with open(path, "rb") as fh:
        text = fh.read().decode("latin-1")
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line[0] in ("#", "!"):
            continue
        m = re.match(r"([A-Za-z0-9_.\-]+)\s*[=:]\s*(.*)$", line)
        if not m:
            continue
        key, value = m.group(1), m.group(2).strip()
        if key not in values:
            order.append(key)
        values[key] = value
    return [(k, values[k]) for k in order]


def disposition(key: str) -> dict:
    spec = o19map_props.KEYS.get(key)
    if spec is not None:
        return spec
    for prefix, pspec in o19map_props.PREFIX_RULES:
        if key.startswith(prefix):
            return pspec
    return {"d": "unknown"}


def translate_docpath(value: str,
                      documents_root: str = DOCUMENTS_ROOT
                      ) -> Optional[str]:
    """Rewrite any .../OscarDocument/<old-ctx>/<tail> path onto the CARLOS
    tree; None when the value carries no OscarDocument context path."""
    m = re.match(r"^(.*?OscarDocument)/([^/]+)(/.*)?$", value)
    if not m:
        return None
    tail = (m.group(3) or "/").lstrip("/")
    new = os.path.join(documents_root, TARGET_CTX, tail)
    if value.endswith("/") and not new.endswith("/"):
        new += "/"
    return new


# --------------------------------------------------------------------------
# translation driver (pure)
# --------------------------------------------------------------------------

def translate_all(clinic: List[Tuple[str, str]],
                  documents_root: str = DOCUMENTS_ROOT,
                  deployment_drugref: Optional[str] = None) -> dict:
    """Apply the full §8.1 pipeline. Returns
    {fragment: [(key, value)], rows: [(key, disposition, note)],
     advisories: {name: [keys]}, secrets: [keys], unknown: [keys]}."""
    defaults = o19map_props.O19_DEFAULTS
    fragment: List[Tuple[str, str]] = []
    rows: List[Tuple[str, str, str]] = []
    advisories: Dict[str, List[str]] = {}
    secrets: List[str] = []
    unknown: List[str] = []

    for key, value in clinic:
        if defaults.get(key) == value:
            continue  # untouched default — CARLOS's own default wins
        spec = disposition(key)
        d = spec["d"]
        if d == "carry":
            fragment.append((key, value))
            rows.append((key, "carry", ""))
        elif d == "carry-secret":
            fragment.append((key, value))
            secrets.append(key)
            rows.append((key, "carry-secret",
                         "imported credential — rotate/verify"))
        elif d == "translate":
            kind = spec.get("t")
            if kind == "docpath":
                new = translate_docpath(value, documents_root)
                if new is None:
                    rows.append((key, "needs-review",
                                 "no OscarDocument path recognized in "
                                 "'{0}' — not carried".format(value)))
                else:
                    fragment.append((key, new))
                    rows.append((key, "translate",
                                 "'{0}' -> '{1}'".format(value, new)))
            elif kind == "drugref":
                if deployment_drugref:
                    rows.append((key, "translate",
                                 "deployment drugref endpoint kept "
                                 "({0})".format(deployment_drugref)))
                else:
                    rows.append((key, "needs-review",
                                 "no deployment drugref_url found — "
                                 "configure it before go-live"))
            else:
                rows.append((key, "needs-review",
                             "unrecognized translator '{0}'".format(kind)))
        elif d == "deploy-owned":
            rows.append((key, "deploy-owned",
                         "refused — the deployment provisions this"))
        elif d == "dropped-flag":
            advisory = spec.get("advisory", "removed-modules")
            advisories.setdefault(advisory, []).append(key)
            rows.append((key, "dropped-flag",
                         "module removed ({0})".format(advisory)))
        else:
            unknown.append(key)
            rows.append((key, "unknown",
                         "no classification — needs a human decision; "
                         "NOT carried, NOT dropped silently"))
    return {"fragment": fragment, "rows": rows, "advisories": advisories,
            "secrets": secrets, "unknown": unknown}


def render_fragment(result: dict) -> str:
    lines = [
        "# Derived from the clinic's oscar.properties by carlos-ctl "
        "import-o19 (experimental).",
        "# REVIEW before applying: append the lines you approve to "
        "/etc/carlos-emr/carlos.properties",
        "# and run `carlos-ctl restart`. Never applied automatically.",
        "# Generated: " + time.strftime("%Y-%m-%d %H:%M:%S"),
        "# Props manifest: " + o19map_props.PROPS_MAP_VERSION,
        "",
    ]
    for key, value in result["fragment"]:
        lines.append("{0}={1}".format(key, value))
    return "\n".join(lines) + "\n"


def render_report(result: dict) -> str:
    lines = []
    secret_set = set(result["secrets"])
    by_d: Dict[str, List[str]] = {}
    for key, d, note in result["rows"]:
        display = note
        if key in secret_set:
            display = note  # note carries no value; values masked below
        by_d.setdefault(d, []).append(
            "{0}{1}".format(key, ("  [" + display + "]") if display else ""))
    for d in ("carry", "carry-secret", "translate", "deploy-owned",
              "dropped-flag", "needs-review", "unknown"):
        if d not in by_d:
            continue
        lines.append("{0} ({1}):".format(d, len(by_d[d])))
        lines.extend("  " + line for line in by_d[d])
    if result["secrets"]:
        lines.append("credentials imported — ROTATE/VERIFY before "
                     "go-live: " + ", ".join(result["secrets"])
                     + " (values in the fragment only, masked here: "
                     + MASK + ")")
    for advisory, keys in sorted(result["advisories"].items()):
        lines.append("advisory [{0}]: {1} key(s) not carried".format(
            advisory, len(keys)))
    if result["unknown"]:
        lines.append("UNKNOWN key(s) needing classification: "
                     + ", ".join(result["unknown"]))
    return "\n".join(lines)


# --------------------------------------------------------------------------
# P6 driver
# --------------------------------------------------------------------------

def run_props(ctx) -> None:
    from . import o19import
    state_dir = ctx["state_dir"]
    clinic = load_clinic_properties(ctx["properties"])
    result = translate_all(
        clinic,
        documents_root=ctx.get("documents_root", DOCUMENTS_ROOT),
        deployment_drugref=prop_get(
            ctx.get("deploy_properties", PROPERTIES), "drugref_url"))

    dry = bool(ctx.get("dry_run"))
    fragment_path = os.path.join(state_dir,
                                 "o19-derived-carlos.properties")
    text = render_fragment(result)
    if dry:
        text = "# DRY RUN — regenerate with the real import\n" + text
    with open(fragment_path, "w", encoding="latin-1") as fh:
        fh.write(text)
    os.chmod(fragment_path, 0o600)

    o19import.report_append(
        state_dir, "P6 props" + (" (dry run)" if dry else ""),
        render_report(result)
        + "\n\nfragment written to {0}\nOPERATOR STEP (never automatic): "
          "review it, append the approved lines to {1}, then run "
          "`carlos-ctl restart`.".format(
              fragment_path, ctx.get("deploy_properties", PROPERTIES)))
    if not dry:
        o19import.mark_done(state_dir, ctx["state"], "props",
                            fragment=os.path.basename(fragment_path),
                            carried=len(result["fragment"]),
                            unknown=len(result["unknown"]))
    from .util import log
    log("props: {0} key(s) in the fragment, {1} unknown — review {2}"
        .format(len(result["fragment"]), len(result["unknown"]),
                fragment_path))
