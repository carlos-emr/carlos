#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
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
Names a ctl target cannot carry, and that are specific enough to exempt globally, go to a
second generated file, debian/assets/modsecurity/RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf,
as config-time SecRuleUpdateTargetByTag lines with an ANCHORED regex target, which
libmodsecurity 3.0.14 accepts there (it rejects both a regex and "ARGS:value(subjective)",
quoted or not, inside a ctl action). One shape qualifies, derived, never hand-listed:
  * a literal name with one parenthesised segment, value(subjective) (the Vascular Tracker's
    map-backed cells): every non-alphanumeric character is bracketed, value[(]subjective[)].
A config-time update is not route-scoped — it applies to an argument of exactly that NAME on
any route — so only names that no other route reads and that are not generic go here. The
value(<key>) names are read solely by the form-save actions.

A row-indexed name (comment_<%=i%>, descOther<%=i %>) could be written as ^comment_[0-9]+$
but is deliberately left as a reported residual: the prefix is generic, rx/prescribe.jsp
posts the prescription comment as comment_<rand>, and a global ^comment_[0-9]+$ would unscore
that field and hand a forged POST to any route a rule-free parameter name. Those cells stay
fully inspected (they answer 403 on scored prose); the clean fix is a per-form rename to
fixed repeated names the 901 file can target by literal ARGS. Any other non-literal name is
reported the same way.

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
AFTER_OUTPUT = os.path.join(ROOT, "debian", "assets", "modsecurity",
                            "RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf")
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
# A name whose only non-literal part is one scriptlet printing a variable: prefix, the
# variable, suffix. It qualifies for an anchored pattern only when the JSP declares that
# variable as an int loop counter (see anchored_pattern).
ROW_INDEX_NAME_RE = re.compile(r"^([A-Za-z0-9_.\-]*)<%=\s*([A-Za-z_][A-Za-z0-9_]*)\s*%>([A-Za-z0-9_.\-]*)$")
# A literal name with one parenthesised segment: the Struts map-backed value(key) cells.
PAREN_NAME_RE = re.compile(r"^[A-Za-z0-9_.\-]+\([A-Za-z0-9_.\-]+\)$")
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
    info = {"route": None, "form_class": None, "names": [], "patterns": [], "dynamic": [],
            "has_form": False,
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
            pattern = anchored_pattern(name, text)
            if pattern is None:
                if name not in info["dynamic"]:
                    info["dynamic"].append(name)
            elif (name, pattern) not in info["patterns"]:
                info["patterns"].append((name, pattern))
            continue
        if name not in info["names"]:
            info["names"].append(name)
    return info


def regex_literal(s):
    """Spell a literal name as a libmodsecurity target regex: every character that is not a
    letter, digit, underscore or hyphen is put in its own bracket class ([.] [(] [)]), which
    keeps the target free of unbracketed metacharacters and of the '/' that ends it."""
    return "".join(ch if (ch.isalnum() or ch in "_-") else "[" + ch + "]" for ch in s)


def anchored_pattern(name, text):
    """The anchored regex target for a name a ctl action cannot carry, or None.

    Only the parenthesised map-backed shape (value(subjective)) qualifies: it is spelled
    literally with each metacharacter bracketed. A config-time SecRuleUpdateTargetByTag is
    NOT route-scoped, so it exempts an argument of exactly that NAME on any route, and these
    value(<key>) names are read only by the form-save actions and are not generic.

    A row-indexed name (comment_<%=i%> -> ^comment_[0-9]+$) is deliberately NOT expressed
    here even though it could be: the prefix is generic. rx/prescribe.jsp posts the
    prescription comment as comment_<rand> and RxWriteScript persists it, so a global
    ^comment_[0-9]+$ would unscore that field too and, worse, hand a forged POST to any
    route a rule-free parameter name. Such names stay reported as a residual and keep the
    full rule set; the clean fix is a per-form rename to fixed names the 901 file can target
    by literal ARGS. `text` is unused now but kept for the row-index detection the residual
    reporting still does."""
    if PAREN_NAME_RE.match(name):
        return "^" + regex_literal(name) + "$"
    return None


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
    patterns = OrderedDict()  # anchored regex -> ordered "name: file -> route" labels
    report = []
    for f in files:
        info = infos[f]
        # Report first: a form whose prose cells are ALL dynamic has no names to group and
        # would otherwise vanish from the report while staying blocked by the WAF.
        for d in info["dynamic"]:
            report.append(f"SKIPPED {f}: name {d!r} cannot be a literal ctl target")
        if not info["names"] and not info["patterns"]:
            continue
        route, form_class = resolve(f, infos, includers)
        if route is None:
            if f in NON_FORM_ROUTE_PAGES:
                report.append(f"NOTE {f}: {NON_FORM_ROUTE_PAGES[f]}")
            else:
                report.append(f"SKIPPED {f}: prose cells but no /form/ save route "
                              f"({len(info['names']) + len(info['patterns'])} names)")
            continue
        label = f"{route} form_class={form_class}" if form_class else route
        for name, pattern in info["patterns"]:
            patterns.setdefault(pattern, [])
            entry = f"{name}: {f} -> {label}"
            if entry not in patterns[pattern]:
                patterns[pattern].append(entry)
        if not info["names"]:
            continue
        groups.setdefault((route, form_class), [])
        for n in info["names"]:
            if n not in groups[(route, form_class)]:
                groups[(route, form_class)].append(n)
    ordered = OrderedDict(sorted(groups.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")))
    return ordered, OrderedDict(sorted(patterns.items())), report


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


def render_after(patterns):
    out = []
    out.append("# SPDX-License-Identifier: AGPL-3.0-only\n# Copyright (C) 2026 CARLOS Contributors\n#")
    out.append("# GENERATED FILE — do not edit by hand. Regenerate with\n"
               "#     python3 scripts/waf/generate-form-prose-exclusions.py\n"
               "# after changing any JSP under src/main/webapp/WEB-INF/jsp/form.\n"
               "# FormProseWafExclusionRegressionTest fails the build when this file and\n"
               "# the form JSPs disagree.\n#")
    out.append("# Clinician free text on the encounter forms whose cell names a ctl target\n"
               "# cannot carry\n"
               "# ------------------------------------------------------------------------\n"
               "# REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf exempts each form's\n"
               "# narrative cells per route, per form_class and per literal argument name.\n"
               "# The names below cannot be written that way: libmodsecurity 3.0.14 rejects\n"
               "# a regex target inside a ctl action and rejects parentheses in a literal one\n"
               "# (\"ARGS:value(subjective)\" fails nginx -t, quoted or not). It does accept\n"
               "# an anchored regex in a config-time SecRuleUpdateTargetByTag, so these cells\n"
               "# are exempted here, the same way RESPONSE-999-EXCLUSION-RULES-AFTER-CRS.conf\n"
               "# handles the measurement, manual-lab, contact and waiting-list rows.\n#\n"
               "# One shape, derived from the JSPs by the generator (never hand-listed):\n"
               "#   value(subjective)   the Vascular Tracker's map-backed cells, spelled\n"
               "#                       literally with each metacharacter bracketed\n#\n"
               "# A config-time update is NOT route-scoped: an argument of exactly that name is\n"
               "# exempt on any route. That is accepted here only because these value(<key>)\n"
               "# names are read solely by the form-save actions and are not generic. Every\n"
               "# pattern is anchored at both ends. Row-indexed cells (comment_<n>,\n"
               "# descOther<n>) are deliberately NOT here: their prefix is generic and, e.g.,\n"
               "# rx/prescribe.jsp posts the prescription comment as comment_<rand>, so a\n"
               "# global ^comment_[0-9]+$ would unscore that field and give a forged POST a\n"
               "# rule-free name on any route. They stay fully inspected. The XSS family is\n"
               "# deliberately NOT removed, here or in the BEFORE-CRS file: the CRS XSS rules\n"
               "# did not fire on any measured prose shape at paranoia level 1 and the legacy\n"
               "# form views render stored cells raw, so that layer stays on as defence in\n"
               "# depth. Nothing here is request-wide.")
    total = 0
    for pattern, labels in patterns.items():
        total += 1
        out.append("\n# " + "\n# ".join(labels))
        out.append("\n".join(f'SecRuleUpdateTargetByTag "{tag}"' + " " * (22 - len(tag)) + f'"!ARGS:/{pattern}/"'
                             for tag in CONTENT_ATTACK_TAGS))
    out.insert(3, f"# {total} anchored patterns, {total * len(CONTENT_ATTACK_TAGS)} lines.")
    return "\n".join(out) + "\n"


def main():
    groups, patterns, report = collect()
    outputs = ((OUTPUT, render(groups)), (AFTER_OUTPUT, render_after(patterns)))
    if "--check" in sys.argv:
        for line in report:
            print(line, file=sys.stderr)
        stale = False
        for path, content in outputs:
            current = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
            if current != content:
                print(f"{path} is stale; regenerate it", file=sys.stderr)
                stale = True
        if stale:
            sys.exit(1)
        print("form prose exclusions are up to date")
        return
    for path, content in outputs:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(content)
    for line in report:
        print(line, file=sys.stderr)
    print(f"wrote {os.path.relpath(OUTPUT, ROOT)}: {len(groups)} rules, "
          f"{sum(len(n) for n in groups.values())} free-text cells")
    for (route, fc), names in groups.items():
        print(f"  {route:32s} {str(fc):22s} {len(names):3d}")
    print(f"wrote {os.path.relpath(AFTER_OUTPUT, ROOT)}: {len(patterns)} anchored patterns")
    for pattern, labels in patterns.items():
        print(f"  {pattern:44s} {labels[0]}")


if __name__ == "__main__":
    main()
