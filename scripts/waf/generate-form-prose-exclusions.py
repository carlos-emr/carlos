#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""
Generate the per-form ModSecurity exclusions for clinician prose on the encounter forms.

Reads every form JSP under src/main/webapp/WEB-INF/jsp/form, works out which route each
form saves through and which `form_class` it posts, collects the names of its free-text
cells, and writes debian/assets/modsecurity/REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf:
one chained rule per (route, form_class) that removes the six CRS content-attack tag
families that misread prose (SQLi, RCE, PHP injection, protocol, LFI, RFI) from exactly
those argument names, on POST only. The XSS family stays on: it did not fire on any
measured prose shape, and legacy views still render some stored text raw.

Why generated: 136 form JSPs carry several hundred distinct free-text names and gain new
ones over time. A hand-written list silently sends a new field back to the front-door 403.
FormProseWafExclusionRegressionTest re-derives the same table from the JSPs and fails when
the committed file and the JSPs disagree, so regenerate after editing a form:

    python3 scripts/waf/generate-form-prose-exclusions.py

What counts as a free-text cell (the same rule the test applies):
  * every <textarea> — a textarea is prose by construction;
  * an <input type="text"> whose name matches PROSE_INPUT_NAME, a documented allow-list of
    name fragments that the forms use for narrative boxes (comments, notes, observations,
    history, plan, ...) and does not match NOT_PROSE_INPUT_NAME (date, id, phone, code,
    dose, score, ...). Dates, codes, numbers and everything else stay fully inspected.
Names that contain a JSP or EL expression, or characters a ctl target cannot carry, are
reported with a SKIPPED line naming each one and stay fully inspected. They fall in two
groups, and both are deliberate residuals rather than gaps in the generator:
  * the Vascular Tracker's map-backed value(<key>) cells: libmodsecurity 3.0.14 rejects
    "ARGS:value(subjective)" in a ctl action quoted, unquoted and backslash-escaped, so the
    only per-route spelling is impossible; a config-time SecRuleUpdateTargetByTag would
    accept an anchored regex but is NOT route-scoped, and this deployment's policy is one
    route, one argument. The form cannot submit on this line anyway (its Struts 2 migration
    is unfinished, see docs/ui-tests/deb-install-validation.md); when it is restored, give
    its cells names this per-route file can target;
  * row-indexed cells (comment_<%=i%>, descOther<%=i %>): a global ^comment_[0-9]+$ would
    unscore the same-named prescription comment (rx/prescribe.jsp posts comment_<rand>) and
    hand a forged POST to any route a rule-free parameter name. The clean fix is a per-form
    rename to fixed repeated names, which the save action (keyed by row index) must follow.

How a form is resolved:
  * the save route is the <form ... action="..."> whose path is under /form/;
  * the form_class is the hidden <input name="form_class"> — a literal value, or the JSP's
    `String formClass = "..."` assignment when the input renders that variable;
  * a JSP with prose cells but no <form> tag is a page that another JSP includes with
    <jsp:include page="..."/> (the multi-page Rourke forms); it inherits the route of the
    JSP that includes it, and the form_class posted by that page set is the hidden input
    found in the wrapper or in any page it includes.
"""
import os
import re
import sys
from collections import OrderedDict, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FORM_DIR = os.path.join(ROOT, "src", "main", "webapp", "WEB-INF", "jsp", "form")
OUTPUT = os.path.join(ROOT, "debian", "assets", "modsecurity",
                      "REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf")
FIRST_RULE_ID = 1200
# attack-xss is deliberately NOT here: the CRS XSS rules did not fire on any measured
# prose shape at paranoia level 1, and several legacy views still render stored text
# raw, so the XSS layer stays on every form cell as defence in depth.
CONTENT_ATTACK_TAGS = ("attack-sqli", "attack-rce", "attack-injection-php",
                       "attack-protocol", "attack-lfi", "attack-rfi")
# Name fragments that mark a single-line text input as a narrative box on these forms.
PROSE_INPUT_NAME = re.compile(
    r"comment|note|observ|remark|plan|reason|detail|finding|history|hx|other|desc|explain|"
    r"concern|summary|text|assess|impression|recommend|complaint|diagnos|problem|allerg|"
    r"medic|social|family|advice|counsel|consider", re.IGNORECASE)
# ...unless the name also says it holds a date, an identifier, a phone number or a
# measurement, which is never prose however it is labelled (medicationDate, allergyCode).
NOT_PROSE_INPUT_NAME = re.compile(
    r"date|time|dob|phone|fax|postal|hin\b|_no$|no$|id$|num$|code|weight|height|\bbp\b|"
    r"dose|units?$|qty|quantity|score|total|count", re.IGNORECASE)

# A tag's attribute list may embed a JSP scriptlet (<%= formClass %>) or an encoder tag
# (<carlos:encode .../>), both of which contain '>' — consume them whole so the tag does
# not end early.
TAG_RE = re.compile(r"<(textarea|input|form)\b((?:<%.*?%>|<carlos:encode\b[^>]*/>|[^>])*)>",
                    re.IGNORECASE | re.DOTALL)
ATTR_RE = re.compile(r"""([a-zA-Z_:-]+)\s*=\s*("([^"]*)"|'([^']*)')""", re.DOTALL)
DYNAMIC_RE = re.compile(r"<%|\$\{")
# The only characters libmodsecurity accepts in a literal ctl target name.
LITERAL_TARGET_RE = re.compile(r"^[A-Za-z0-9_.\-]+$")
FORM_CLASS_ASSIGN_RE = re.compile(r'^\s*String\s+formClass\s*=\s*"([^"]+)"', re.MULTILINE)
# Pages under the form directory whose prose does not post to a /form/ route, and where it
# goes instead. Listed so the report says what happened to them rather than "SKIPPED".
NON_FORM_ROUTE_PAGES = {
    "addRhInjection.jsp": "posts reason/reasonOtherText to /prevention/AddPrevention, "
                          "covered by rule 1117 in REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf",
    "formlabreqprint.jsp": "print view of the lab requisition; it has no form and posts nothing",
}
INCLUDE_RE = re.compile(r"""<jsp:include\s+page\s*=\s*["']([^"']+)["']|<%@\s*include\s+file\s*=\s*["']([^"']+)["']""",
                        re.IGNORECASE)


def attrs(raw):
    out = {}
    for m in ATTR_RE.finditer(raw):
        out[m.group(1).lower()] = m.group(3) if m.group(3) is not None else m.group(4)
    return out


def analyse(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    info = {"route": None, "form_class": None, "names": [], "dynamic": [], "has_form": False,
            "includes": [os.path.basename(m.group(1) or m.group(2)) for m in INCLUDE_RE.finditer(text)]}
    for m in TAG_RE.finditer(text):
        tag, a = m.group(1).lower(), attrs(m.group(2))
        if tag == "form":
            info["has_form"] = True
            action = a.get("action", "")
            r = re.search(r"/form/[A-Za-z0-9_]+", action)
            if r and info["route"] is None:
                info["route"] = "/carlos" + r.group(0)
            continue
        name = a.get("name")
        if not name:
            continue
        if tag == "input":
            if a.get("type", "").lower() == "hidden" and name == "form_class":
                value = a.get("value", "")
                if DYNAMIC_RE.search(value):
                    assign = FORM_CLASS_ASSIGN_RE.search(text)
                    info["form_class"] = assign.group(1) if assign else None
                else:
                    info["form_class"] = value
                continue
            if a.get("type", "").lower() != "text" or not PROSE_INPUT_NAME.search(name) \
                    or NOT_PROSE_INPUT_NAME.search(name):
                continue
        if DYNAMIC_RE.search(name) or not LITERAL_TARGET_RE.match(name):
            if name not in info["dynamic"]:
                info["dynamic"].append(name)
            continue
        if name not in info["names"]:
            info["names"].append(name)
    return info


def resolve(f, infos, includers, depth=0):
    """(route, form_class) a page's cells are posted under, following jsp:include upwards."""
    info = infos[f]
    if info["route"] is not None:
        form_class = info["form_class"]
        for page in info["includes"]:
            if form_class is None and page in infos:
                form_class = infos[page]["form_class"]
        return info["route"], form_class
    if not info["has_form"] and f in includers and depth < 5:
        return resolve(includers[f], infos, includers, depth + 1)
    return None, None


def collect():
    files = sorted(f for f in os.listdir(FORM_DIR) if f.endswith(".jsp"))
    infos = {f: analyse(os.path.join(FORM_DIR, f)) for f in files}
    includers = {}
    for f in files:
        for page in infos[f]["includes"]:
            if page in infos and infos[page]["route"] is None:
                includers[page] = f
    groups = OrderedDict()   # (route, form_class) -> ordered names
    report = []
    for f in files:
        info = infos[f]
        # Report first: a form whose prose cells are ALL dynamic has no names to group and
        # would otherwise vanish from the report while staying blocked by the WAF.
        for d in info["dynamic"]:
            report.append(f"SKIPPED {f}: name {d!r} cannot be a literal ctl target")
        if not info["names"]:
            continue
        route, form_class = resolve(f, infos, includers)
        if route is None:
            if f in NON_FORM_ROUTE_PAGES:
                report.append(f"NOTE {f}: {NON_FORM_ROUTE_PAGES[f]}")
            else:
                report.append(f"SKIPPED {f}: prose cells but no /form/ save route ({len(info['names'])} names)")
            continue
        groups.setdefault((route, form_class), [])
        for n in info["names"]:
            if n not in groups[(route, form_class)]:
                groups[(route, form_class)].append(n)
    ordered = OrderedDict(sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")))
    return ordered, report


def render(groups):
    out = []
    out.append("# SPDX-License-Identifier: AGPL-3.0-only\n# Copyright (C) 2026 CARLOS Contributors\n#")
    out.append("# GENERATED FILE — do not edit by hand. Regenerate with\n"
               "#     python3 scripts/waf/generate-form-prose-exclusions.py\n"
               "# after changing any JSP under src/main/webapp/WEB-INF/jsp/form.\n"
               "# FormProseWafExclusionRegressionTest fails the build when this file and\n"
               "# the form JSPs disagree.\n#")
    out.append("# Clinician free text on the encounter forms (ids 1200-1399)\n"
               "# ------------------------------------------------------------\n"
               "# The encounter forms (Rourke, antenatal, palliative care, mental health and\n"
               "# the rest) are the largest pool of clinician prose in CARLOS, and nearly all\n"
               "# of them save through one generic route, /form/formname, which reads every\n"
               "# posted parameter by name into that form's table. At CRS paranoia level 1\n"
               "# ordinary prose in those cells (a pasted link with \"&cmd=\", a note beginning\n"
               "# with an internal IP address, \"select ... and order\") scores as an attack\n"
               "# and the packaged front door answers 403 before the application sees the\n"
               "# save. See rule 1010 in REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf for the\n"
               "# rule ids and tag families involved; the treatment here is the same,\n"
               "# per argument, per route, and additionally per form.\n#\n"
               "# Each rule below is keyed on the save route, POST, and — for the generic\n"
               "# route — the form's own form_class parameter, so a Rourke field name is\n"
               "# exempt only when a Rourke form is being saved. The targets are the form's\n"
               "# <textarea> cells and the single-line inputs whose names mark them as\n"
               "# narrative boxes (see the generator's PROSE_INPUT_NAME); dates, codes,\n"
               "# numbers, ids and form_class itself stay fully inspected, as does every\n"
               "# other argument on these routes. Nothing here is request-wide. The XSS\n"
               "# family is NOT removed: the CRS XSS rules did not fire on any measured\n"
               "# prose shape at paranoia level 1, and legacy form views render stored\n"
               "# cells raw, so that layer stays on every cell as defence in depth.\n#\n"
               "# The form_class-keyed rules are phase 2 (the POST body is parsed there);\n"
               "# the CRS content-attack rules are phase 2 as well, and this file loads\n"
               "# before them, so the exclusions still apply first. Route-only rules stay\n"
               "# phase 1 like the rest of the BEFORE-CRS exclusions.")
    rule_id = FIRST_RULE_ID
    total = 0
    for (route, form_class), names in groups.items():
        total += len(names)
        label = f"{route} form_class={form_class}" if form_class else route
        out.append(f"\n# {rule_id}: {label} ({len(names)} free-text cells)")
        # A rule that reads ARGS:form_class from the POST body must run in phase 2, where
        # the body has been parsed; the CRS content-attack rules are phase 2 too and this
        # file is included before them, so the ctl actions still land first.
        phase = 2 if form_class else 1
        lines = [f'SecRule REQUEST_URI "@rx ^{re.escape(route)}(?:[;?]|$)" \\',
                 f'    "id:{rule_id},phase:{phase},pass,nolog,chain"',
                 '    SecRule REQUEST_METHOD "@streq POST" \\']
        if form_class:
            lines += ['        "chain"',
                      f'        SecRule ARGS:form_class "@streq {form_class}" \\',
                      '            "t:none,\\']
            indent = "            "
        else:
            lines += ['        "t:none,\\']
            indent = "        "
        ctl = [f"{indent}ctl:ruleRemoveTargetByTag={tag};ARGS:{name}"
               for name in names for tag in CONTENT_ATTACK_TAGS]
        lines += [c + ",\\" for c in ctl[:-1]] + [ctl[-1] + '"']
        out.append("\n".join(lines))
        rule_id += 1
    out.insert(3, f"# {len(groups)} rules, {total} free-text cells, ids {FIRST_RULE_ID}-{rule_id - 1}.")
    return "\n".join(out) + "\n"


def main():
    groups, report = collect()
    content = render(groups)
    if "--check" in sys.argv:
        current = open(OUTPUT, encoding="utf-8").read() if os.path.exists(OUTPUT) else ""
        for line in report:
            print(line, file=sys.stderr)
        if current != content:
            print(f"{OUTPUT} is stale; regenerate it", file=sys.stderr)
            sys.exit(1)
        print("form prose exclusions are up to date")
        return
    with open(OUTPUT, "w", encoding="utf-8") as fh:
        fh.write(content)
    for line in report:
        print(line, file=sys.stderr)
    print(f"wrote {os.path.relpath(OUTPUT, ROOT)}: {len(groups)} rules, "
          f"{sum(len(n) for n in groups.values())} free-text cells")
    for (route, fc), names in groups.items():
        print(f"  {route:32s} {str(fc):22s} {len(names):3d}")


if __name__ == "__main__":
    main()
