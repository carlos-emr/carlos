# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Props phase (P6) of the OSCAR 19 importer (experimental).

Translates the clinic's deployed oscar.properties into a REVIEWED fragment
for /etc/carlos-emr/carlos.properties. Never merged automatically — the
fragment sits in the import state dir until an operator reviews and applies
it (docs plan §8.1: this is deliberately the one non-automatic step).

Rules, in order:
 1. Baseline-diff: keys equal to the stock O19 default are ignored —
    CARLOS defaults win wherever the clinic never made a choice. Where
    CARLOS's default DIFFERS from the O19 one (manifest CARLOS_DEFAULTS)
    that silently changes behaviour at cutover, so the key still earns a
    `carlos-default` report row naming both values; nothing is carried.
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

_UNESCAPE = {"n": "\n", "t": "\t", "r": "\r", "f": "\f"}


def _unescape_property(text: str) -> str:
    """java.util.Properties escape handling for a key or value."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == "\\" and i + 1 < n:
            nxt = text[i + 1]
            if nxt == "u":
                if not re.match(r"[0-9A-Fa-f]{4}$", text[i + 2:i + 6]):
                    # java.util.Properties rejects the file outright;
                    # silently "fixing" it would carry a changed value
                    raise ValueError("malformed \\uXXXX escape in "
                                     "properties text")
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append(_UNESCAPE.get(nxt, nxt))
            i += 2
            continue
        out.append(c)
        i += 1
    return _join_surrogates("".join(out))


def _join_surrogates(text: str) -> str:
    """Two adjacent \\uXXXX escapes forming a UTF-16 pair decode to one
    character (the same rule java.util.Properties applies)."""
    if not any(0xD800 <= ord(c) <= 0xDFFF for c in text):
        return text
    # pair only an adjacent high+low surrogate; an unpaired surrogate is
    # kept as-is (java.util.Properties preserves it too) rather than
    # aborting the whole properties file
    out = []
    i = 0
    while i < len(text):
        cp = ord(text[i])
        if (0xD800 <= cp <= 0xDBFF and i + 1 < len(text)
                and 0xDC00 <= ord(text[i + 1]) <= 0xDFFF):
            out.append(chr(0x10000 + ((cp - 0xD800) << 10)
                           + ord(text[i + 1]) - 0xDC00))
            i += 2
        else:
            out.append(text[i])
            i += 1
    return "".join(out)


def parse_properties_text(text: str) -> List[Tuple[str, str]]:
    """Ordered active key/value pairs with java.util.Properties semantics:
    `=`, `:` or whitespace separate key and value; leading whitespace is
    skipped but TRAILING whitespace in a value is significant (a
    credential ending in a space must survive); a trailing unescaped
    backslash continues the logical line; `\\n`, `\\t`, `\\uXXXX` and
    `\\x` escapes are decoded. A repeated key keeps its LAST value but its
    first position."""
    order: List[str] = []
    values: Dict[str, str] = {}
    logical: List[str] = []
    # java.util.Properties ends a line at \n, \r or \r\n only (never at
    # \f, \x85 — the Windows-1252 ellipsis read through latin-1 — or the
    # other characters str.splitlines() honours) and strips only space,
    # tab and form feed; a continuation backslash on the last line still
    # yields the record, hence the empty sentinel line
    physical = re.split(r"\r\n|\r|\n", text) + [""]
    i = 0
    while i < len(physical):
        line = physical[i].lstrip(" \t\f")
        i += 1
        if not logical and (not line or line[0] in ("#", "!")):
            continue
        # continuation: an odd number of trailing backslashes — the LAST
        # one continues the line, the others are escaped backslashes that
        # belong to the value
        if (len(line) - len(line.rstrip("\\"))) % 2 == 1:
            logical.append(line[:-1])
            continue
        logical.append(line)
        full = "".join(logical)
        logical = []
        # key ends at the first unescaped '=', ':' or whitespace
        j = 0
        key_chars: List[str] = []
        while j < len(full):
            c = full[j]
            if c == "\\" and j + 1 < len(full):
                key_chars.append(full[j:j + 2])
                j += 2
                continue
            if c in "=: \t\f":
                break
            key_chars.append(c)
            j += 1
        key = _unescape_property("".join(key_chars))
        if not key:
            continue
        rest = full[j:]
        rest = rest.lstrip(" \t\f")
        if rest[:1] in ("=", ":"):
            rest = rest[1:].lstrip(" \t\f")
        value = _unescape_property(rest)
        if key not in values:
            order.append(key)
        values[key] = value
    return [(k, values[k]) for k in order]


def _escape_non_latin1(text: str) -> str:
    """Characters outside Latin-1 as \\uXXXX, exactly as Properties.store
    writes them — the fragment is a Latin-1 file. Code points above the
    BMP become a UTF-16 surrogate pair (two 4-digit escapes), which the
    Java reader and parse_properties_text both reassemble."""
    out = []
    for c in text:
        cp = ord(c)
        if cp <= 0xFF:
            out.append(c)
        elif cp <= 0xFFFF:
            out.append("\\u{0:04x}".format(cp))
        else:
            cp -= 0x10000
            out.append("\\u{0:04x}\\u{1:04x}".format(
                0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF)))
    return "".join(out)


_URL_FORBIDDEN = set("'\"<>\\`") | set(chr(i) for i in range(0x21))


def safe_url(value: str) -> bool:
    """A plain absolute http(s) URL and nothing else. CARLOS interpolates
    some carried URLs into JavaScript string literals (the provider menu's
    resource link), so a value carrying quotes, angle brackets, whitespace
    or control characters is refused at import rather than carried."""
    from urllib.parse import urlsplit
    if not value or any(c in _URL_FORBIDDEN for c in value):
        return False
    try:
        parts = urlsplit(value)
    except ValueError:
        return False
    return parts.scheme in ("http", "https") and bool(parts.netloc)


def escape_property_value(value: str) -> str:
    """Inverse of the value decoding above, so a carried value round-trips
    through the fragment exactly (backslashes, line breaks, tabs, a
    leading space and non-Latin-1 characters are what java.util.Properties
    would otherwise misread or the Latin-1 file could not hold)."""
    out = (value.replace("\\", "\\\\").replace("\n", "\\n")
           .replace("\r", "\\r").replace("\t", "\\t").replace("\f", "\\f"))
    if out[:1] == " ":
        out = "\\" + out
    return _escape_non_latin1(out)


def escape_property_key(key: str) -> str:
    """Keys are escaped too: a decoded key may hold '=', ':', whitespace or
    a line break (an escaped separator in the clinic file), which written
    raw would split into a different key or inject a second line."""
    out = escape_property_value(key)
    for ch in ("=", ":", "#", "!", " "):
        out = out.replace(ch, "\\" + ch)
    return out


def report_safe(text: str) -> str:
    """A clinic-supplied key or note rendered so it cannot forge lines.

    `render_report` writes key NAMES into `report.txt` and, through it,
    into the operator's validation report. A java.util.Properties key
    may carry an escaped line break, and `parse_properties_text` decodes
    it -- so a crafted oscar.properties could write its own lines into
    the report, up to and including a plausible `carry-secret (0):`
    heading that hides a real carried credential from the reviewer. The
    report is the artifact a human uses to decide the migration is
    sound; forging it is not cosmetic.

    Control characters become their Java escapes, the way the fragment
    already renders them, so the reader still sees what the key was."""
    out = (text.replace("\\", "\\\\").replace("\n", "\\n")
           .replace("\r", "\\r").replace("\t", "\\t")
           .replace("\f", "\\f"))
    return "".join(c if c.isprintable() or c == " "
                   else "\\u{0:04x}".format(ord(c)) for c in out)


def load_clinic_properties(path: str) -> List[Tuple[str, str]]:
    """Ordered active key=value pairs of a deployed oscar.properties
    (see parse_properties_text for the java.util.Properties semantics)."""
    with open(path, "rb") as fh:
        text = fh.read().decode("latin-1")
    return parse_properties_text(text)


def disposition(key: str) -> dict:
    """What the manifest says to do with one property key: its exact entry
    if there is one, else the first matching prefix rule, else
    `unknown` (surfaced for review, never carried silently)."""
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
    ctx_root = os.path.join(documents_root, TARGET_CTX)
    new = os.path.normpath(os.path.join(ctx_root, tail))
    # a tail with '..' components would carry a setting that points outside
    # the CARLOS document tree — refuse it (the caller reports needs-review)
    if new != ctx_root and not new.startswith(ctx_root + os.sep):
        return None
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
    # secret-bearing stock defaults are deliberately NOT shipped in the
    # manifest: such a key is always surfaced for review, so a clinic still
    # running an O19 stock credential sees it flagged rather than silently
    # inheriting CARLOS's default
    secret_defaults = set(getattr(o19map_props, "SECRET_DEFAULT_KEYS", ()))
    # CARLOS's own default for the carried keys where it differs from the
    # O19 one — the baseline skip below is behaviour-neutral only while
    # the two products agree
    carlos_defaults = getattr(o19map_props, "CARLOS_DEFAULTS", {})
    fragment: List[Tuple[str, str]] = []
    rows: List[Tuple[str, str, str]] = []
    advisories: Dict[str, List[str]] = {}
    secrets: List[str] = []
    unknown: List[str] = []

    for key, value in clinic:
        if defaults.get(key) == value:
            # untouched default — CARLOS's own default wins (plan §8.1
            # rule 1). Nothing is carried, but where CARLOS ships a
            # DIFFERENT default the clinic's behaviour changes at
            # cutover (consultation auto-include, lab display, the
            # contacts UI), so the operator is told rather than the key
            # leaving no trace anywhere in report.txt
            if key in carlos_defaults:
                rows.append((key, "carlos-default",
                             "untouched O19 default '{0}'; CARLOS's "
                             "default is '{1}' and wins".format(
                                 value, carlos_defaults[key])))
            continue
        if key in secret_defaults and value == "":
            continue  # an empty credential is "not configured", not a secret
        spec = disposition(key)
        d = spec["d"]
        if d == "carry":
            if spec.get("validate") == "url" and not safe_url(value):
                rows.append((key, "refused-invalid",
                             "not a plain http(s) URL — not carried "
                             "(CARLOS renders this value into script)"))
                continue
            # CARLOS may read the same setting under a different key
            # (faxEnable -> enableFax): the fragment carries the key
            # CARLOS honours
            target_key = spec.get("as", key)
            fragment.append((target_key, value))
            rows.append((key, "carry",
                         "" if target_key == key
                         else "carried as {0}".format(target_key)))
        elif d == "carry-secret":
            fragment.append((key, value))
            secrets.append(key)
            note = "imported credential — rotate/verify"
            if key in secret_defaults:
                note += (" (O19 ships a stock value for this key that is "
                         "not compared here — confirm it is not the stock "
                         "default)")
            rows.append((key, "carry-secret", note))
        elif d == "translate":
            kind = spec.get("t")
            if kind == "docpath":
                new = translate_docpath(value, documents_root)
                # CARLOS may read the setting under a different key
                # (eform_image -> EFORM_IMAGES_DIR); the fragment carries
                # the key CARLOS honours
                target_key = spec.get("as", key)
                if new is None:
                    rows.append((key, "needs-review",
                                 "no OscarDocument path recognized in "
                                 "'{0}' — not carried".format(value)))
                else:
                    fragment.append((target_key, new))
                    rows.append((key, "translate",
                                 "'{0}' -> {1}'{2}'".format(
                                     value,
                                     "" if target_key == key
                                     else target_key + "=",
                                     new)))
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
    """The `o19-derived-carlos.properties` text: the keys this import would
    carry, as comments-plus-values for an operator to review and append
    by hand. Never applied automatically."""
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
        lines.append("{0}={1}".format(escape_property_key(key),
                                      escape_property_value(value)))
    return "\n".join(lines) + "\n"


def render_report(result: dict) -> str:
    """The properties section of `report.txt`, grouped by disposition.
    Key NAMES only -- a carried secret's value never reaches this file."""
    lines = []
    secret_set = set(result["secrets"])
    by_d: Dict[str, List[str]] = {}
    for key, d, note in result["rows"]:
        display = note
        if key in secret_set:
            display = note  # note carries no value; values masked below
        by_d.setdefault(d, []).append(
            "{0}{1}".format(report_safe(key),
                            ("  [" + report_safe(display) + "]")
                            if display else ""))
    for d in ("carry", "carry-secret", "translate", "deploy-owned",
              "dropped-flag", "carlos-default", "refused-invalid",
              "needs-review", "unknown"):
        if d not in by_d:
            continue
        lines.append("{0} ({1}):".format(d, len(by_d[d])))
        lines.extend("  " + line for line in by_d[d])
    if result["secrets"]:
        # report_safe, like every other clinic-supplied name on this
        # page: a key carrying an escaped line break would otherwise
        # write its own lines into the operator's validation report
        lines.append("credentials imported — ROTATE/VERIFY before "
                     "go-live: "
                     + ", ".join(report_safe(k) for k in result["secrets"])
                     + " (values in the fragment only, masked here: "
                     + MASK + ")")
    for advisory, keys in sorted(result["advisories"].items()):
        lines.append("advisory [{0}]: {1} key(s) not carried".format(
            advisory, len(keys)))
    if result["unknown"]:
        lines.append("UNKNOWN key(s) needing classification: "
                     + ", ".join(report_safe(k)
                                 for k in result["unknown"]))
    return "\n".join(lines)


# --------------------------------------------------------------------------
# P6 driver
# --------------------------------------------------------------------------

def write_fragment(path: str, text: str) -> None:
    """Write the reviewable fragment with mode 0600 from the first byte:
    it holds carried credentials in clear, so no umask window may expose
    it, and a pre-existing file (a rerun) keeps its possibly wider mode
    across O_TRUNC — so the mode is tightened on the open descriptor
    BEFORE any secret is written."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    # latin-1 is the java.util.Properties file encoding; values are
    # already \\uXXXX-escaped by _escape_non_latin1, and the backslash
    # replacement is a backstop so a stray character in a header comment
    # cannot abort the write
    with os.fdopen(fd, "w", encoding="latin-1",
                   errors="backslashreplace") as fh:
        os.fchmod(fh.fileno(), 0o600)
        fh.write(text)


def run_props(ctx) -> None:
    """P6 -- parse the clinic's `oscar.properties`, classify every key
    against the props manifest, and write the derived fragment and the
    report section.

    Produces a fragment for review; it changes no deployed
    configuration. A dry run renders to the `.dry-run` twin instead."""
    from . import o19import
    from .util import die
    state_dir = ctx["state_dir"]
    try:
        clinic = load_clinic_properties(ctx["properties"])
    except ValueError as exc:
        die("cannot parse the clinic's oscar.properties: {0}".format(exc))
    result = translate_all(
        clinic,
        documents_root=ctx.get("documents_root", DOCUMENTS_ROOT),
        deployment_drugref=prop_get(
            ctx.get("deploy_properties", PROPERTIES), "drugref_url"))

    dry = bool(ctx.get("dry_run"))
    # a dry run never overwrites the reviewed fragment of a real run
    fragment_path = os.path.join(
        state_dir,
        "o19-derived-carlos.properties" + (".dry-run" if dry else ""))
    text = render_fragment(result)
    if dry:
        # ASCII only: the fragment is written as ISO-8859-1 (what
        # java.util.Properties reads), and an em dash here crashed every
        # --dry-run before it could report anything
        text = "# DRY RUN - regenerate with the real import\n" + text
    write_fragment(fragment_path, text)

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
