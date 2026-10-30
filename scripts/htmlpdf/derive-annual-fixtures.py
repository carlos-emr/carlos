#!/usr/bin/env python3
"""
Derives the Annual form fixtures of the HTML-to-PDF tests from the form JSPs.

The real documents the PDF module prints are the pages the form JSPs render for a saved form: checked
boxes and field values are server-rendered attributes, the text areas hold their text, the hidden fields
are present, and the page links the real `annualStyle.css` and `print.css`. This script produces such a
page without a running server by evaluating the few JSP constructs these two files use:

  * directives, scriptlets, JSP comments and the security wrapper are dropped;
  * `<fmt:message key='...'/>` becomes the English text of `oscarResources_en.properties`;
  * `props.getProperty("name", ...)` becomes a FAKE- sample value, `checked="checked"` for the
    checkboxes in TICKED, or the text of a text area;
  * `request.getContextPath()` and the page's base URL become empty / a localhost URL.

Only FAKE- names appear. The stylesheet links stay relative (`form/annualStyle.css`), so a test points the
render's base directory at a folder that holds the real `src/main/webapp/form` stylesheets.

Usage:  python3 scripts/htmlpdf/derive-annual-fixtures.py [OUTPUT_DIR]
        (default OUTPUT_DIR: src/test/resources/htmlpdf/annual)

`scripts/htmlpdf/capture-annual-fixtures.js` captures the same pages from a running devcontainer instead;
use it to refresh the fixtures from a real server.
"""
import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JSP_DIR = ROOT / "src/main/webapp/WEB-INF/jsp/form"
BUNDLE = ROOT / "src/main/resources/oscarResources_en.properties"

# Checkboxes ticked in each fixture: at least ten of each, spread over the page.
TICKED = {
    "female": {
        "currentConcernsYes", "headN", "respN", "cardioAbN", "giN", "skinAbN", "endocrinN", "yesGtpalRevisions",
        "papSmearsN", "drugs", "allergies", "familyHistory", "smokingNo", "alcoholYes", "exerciseYes", "nutritionNo",
        "dentalYes", "femaleCardiac", "mammogram",
    },
    "male": {
        "currentConcernsNo", "headN", "respAbN", "cardioN", "giN", "guAbN", "mskN", "endocrinN", "drugs", "allergies",
        "familyHistory", "smokingYes", "alcoholNo", "exerciseYes", "nutritionYes", "maleCardiac", "rectal",
    },
}

TEXT_VALUES = {
    "pName": "FAKE-Patient, Alice",
    "age": "42",
    "formDate": "2026/10/29",
    "head": "normal exam",
    "resp": "clear, no wheeze",
    "cardio": "regular rhythm",
    "gi": "FAKE-GI note",
    "gu": "no complaints",
    "skin": "mole on left arm, évaluer",
    "msk": "full range of motion",
    "endocrin": "TSH reviewed",
    "lmp": "2026/09/30",
    "menopause": "12",
    "papSmears": "2025/03/14 normal",
    "bprTop": "120", "bprBottom": "80", "bplTop": "118", "bplBottom": "78",
    "pulse": "72", "rhythm": "regular", "height": "170", "weight": "68", "urine": "negative",
    "signature": "Dr. FAKE-Provider",
    "smoking": "never", "alcohol": "2 drinks per week", "exercise": "walks 30 min daily", "nutrition": "balanced diet",
    "dental": "last visit 2026/05", "driving": "no concerns", "travel": "none planned", "occupational": "office work",
    "relationship": "stable", "sexuality": "no concerns", "otc": "none", "otherLifestyle": "none",
    "femaleCardiac": "", "femaleImmunization": "tetanus 2024", "maleImmunization": "tetanus 2024",
    "breast": "no masses", "pap": "up to date", "mammogram": "2025/11", "osteoporosis": "not due",
    "precontraceptive": "n/a", "femaleOther1": "FAKE-other screening 1", "femaleOther2": "",
    "maleOther1": "FAKE-other screening 1", "maleOther2": "", "rectal": "declined",
    "demographic_no": "1", "ID": "1", "formCreated": "2026/10/29",
}

TEXTAREA_VALUES = {
    "currentConcerns": "FAKE-concern one: fatigue since August\nFAKE-concern two: knee pain, left side",
    "other": "FAKE-other finding line one\nFAKE-other finding line two",
    "physicalSigns": "General: well appearing\nChest: clear bilaterally, ≥ normal range",
    "assessment": "FAKE-assessment line one\nFAKE-assessment line two",
    "plan": "FAKE-plan line one\nFAKE-plan line two (follow up in 3 months)",
}


def load_messages():
    messages = {}
    for line in BUNDLE.read_text(encoding="utf-8").splitlines():
        if not line or line[0] in "#!" or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), value)
        messages[key.strip()] = value.strip()
    return messages


def derive(kind, messages):
    source = (JSP_DIR / f"formannual{kind}.jsp").read_text(encoding="utf-8")
    ticked = TICKED[kind]
    text = source

    text = re.sub(r"<%--.*?--%>", "", text, flags=re.S)
    text = re.sub(r"<security:oscarSec.*?</security:oscarSec>", "", text, flags=re.S)
    text = re.sub(r"<%@.*?%>", "", text, flags=re.S)
    text = re.sub(r"<fmt:setBundle[^>]*/>", "", text)

    # Messages, escaped for text and attribute use alike.
    def message(match):
        key = match.group(1)
        return html.escape(messages.get(key, key), quote=True)

    text = re.sub(r"<fmt:message\s+key=['\"]([^'\"]+)['\"]\s*/>", message, text)

    # Hidden-field values that the JSP encodes with the CARLOS tag (its value is itself an expression).
    text = re.sub(r"<%=\s*StringUtils\.noNull\(request\.getParameter\(\"provNo\"\)\)\s*%>", "999998", text)
    text = re.sub(r"<carlos:encode[^>]*?/>", "999998", text)

    # Scriptlets: the page's bean set-up and the security redirect only.
    text = re.sub(r"<%(?![=@])(?!--).*?%>", "", text, flags=re.S)

    # Page expressions.
    # Links relative to the webapp root: the render's base directory is the folder that holds `form/`.
    text = text.replace("${pageContext.request.contextPath}/", "")
    text = re.sub(r"<%=\s*request\.getContextPath\(\)\s*%>/", "", text)
    text = re.sub(r"<%=\s*request\.getScheme\(\).*?%>", "http://localhost:8080/carlos/", text, flags=re.S)
    text = re.sub(r"<%=\s*formClass\s*%>", "Annual", text)
    text = re.sub(r"<%=\s*formLink\s*%>", f"formannual{kind}.jsp", text)
    text = re.sub(r"<%=\s*(demoNo|formId|provNo)\s*%>", "1", text)

    # Form values.
    def value_of(name, default):
        if name in TEXT_VALUES:
            return TEXT_VALUES[name]
        return default

    # textarea content
    def textarea(match):
        name = re.search(r'name="(\w+)"', match.group(1)).group(1)
        body = TEXTAREA_VALUES.get(name, "")
        return f"<textarea{match.group(1)}>{html.escape(body, quote=False)}</textarea>"

    text = re.sub(
        r"<textarea([^>]*)>\s*<%=\s*props\.getProperty\(\"\w+\"(?:\s*,\s*\"[^\"]*\")?\)\s*%>\s*</textarea>", textarea, text)

    # value="..." attributes
    def value_attribute(match):
        name, default = match.group(1), match.group(2) or ""
        return 'value="' + html.escape(value_of(name, default), quote=True) + '"'

    text = re.sub(r'value="<%=\s*props\.getProperty\("(\w+)"(?:\s*,\s*"([^"]*)")?\)\s*%>"', value_attribute, text)

    # bare attributes inside input tags: the checked state of a checkbox
    def input_tag(match):
        tag = match.group(0)
        if 'type="checkbox"' not in tag:
            return tag
        name = re.search(r'name="(\w+)"', tag).group(1)
        checked = 'checked="checked"' if name in ticked else ""
        return re.sub(r"<%=\s*props\.getProperty\(\"\w+\"(?:\s*,\s*\"[^\"]*\")?\)\s*%>", checked, tag)

    text = re.sub(r"<input\b[^>]*>", input_tag, text, flags=re.S)

    leftovers = re.findall(r"<%.*?%>", text, flags=re.S)
    if leftovers:
        raise SystemExit(f"formannual{kind}.jsp: unevaluated JSP left: {leftovers[:3]}")

    # A scripted page: keep a trimmed script block so the render's script removal is exercised.
    text = re.sub(r"<script\b[^>]*src=[^>]*></script>", "", text)
    text = re.sub(r"<script\b.*?</script>", "", text, flags=re.S)
    text = text.replace("</head>", '<script type="text/javascript">function onPrint() { window.print(); }</script>\n</head>', 1)
    # The icon and the absolute base link are not part of a printed page's resources.
    text = re.sub(r"<link rel=\"icon\"[^>]*>", "", text)
    text = re.sub(r"<base\b[^>]*>", "", text)

    return "<!DOCTYPE html>\n" + text.strip() + "\n"


def main():
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "src/test/resources/htmlpdf/annual"
    output.mkdir(parents=True, exist_ok=True)
    messages = load_messages()
    for kind in ("female", "male"):
        document = derive(kind, messages)
        (output / f"{kind}.html").write_text(document, encoding="utf-8")
        print(f"{output / (kind + '.html')}: {len(document)} characters")


if __name__ == "__main__":
    main()
