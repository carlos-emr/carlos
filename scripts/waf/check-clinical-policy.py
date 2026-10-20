#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Real nginx/ModSecurity/CRS regression checks for issues #4571--4575 and the
add-prevention free text of PR #4742.

Run from any directory (Python >= 3.10, Node for the shared prose corpus):
  python3 scripts/waf/check-clinical-policy.py
  python3 scripts/waf/check-clinical-policy.py --evidence-dir /tmp/waf-evidence
  python3 scripts/waf/check-clinical-policy.py --check-config
  python3 scripts/waf/check-clinical-policy.py --self-test

No root, production listener, service command, application, or database is used.
The shipped policy, host CRS and setup are snapshotted into a private temporary
nginx prefix; ONLY filesystem/include paths are relocated. Blocking, scores,
MIME inspection and audit settings are untouched. Two ephemeral IPv4 loopback
ports are used. The nginx port allocation has the usual close/bind race: a
collision fails startup, never connects the tests to an unidentified listener.

Allowed requests must reach the Python fixture upstream with identical method,
raw target and body, then return its exact response and per-request marker.
Denied requests require a correlated signature AND anomaly evaluation; an nginx
404, timeout, unrelated audit entry or application error cannot pass. Phase-4
denials may be 403 or a truncated/disconnected 200 depending on the connector,
but even a truncated response carrying a sensitive fixture signature fails.
This tests policy, not application integration; the real browser/save/storage
assertions belong in Playwright.

Numeric Rx names follow the existing global AFTER-CRS pattern precedent. Route
and method negatives therefore target literal fields; malformed numeric names,
nonprose fields and XSS pin the dynamic-name boundaries. Rx comments use the
rxComment_<n> namespace; legacy comment_<n> remains inspected on Rx and growth
form submissions, where that name is also used.

Exit: 0 all selected checks passed; 1 regression; 2 setup/configuration error;
130 interrupted. --evidence-dir must be absent or empty; it retains snapshots,
source SHA-256s, requests/results, upstream receipts, and nginx/audit logs.
Without it, all runtime artifacts are removed, including after failures.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import http.client
import io
import json
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from types import SimpleNamespace
from urllib.parse import urlencode
import uuid


ROOT = Path(__file__).resolve().parents[2]
LETTER = "/carlos/eform/addEForm"
RX = "/carlos/rx/WriteScript"
TICKLERS = "/carlos/tickler/ListTicklers"
ENCOUNTER = "/carlos/encounter/IncomingEncounter"
VITALS = "/carlos/encounter/Measurements"
FORWARD = "/carlos/casemgmt/ViewForward"
ENTRY = "/carlos/CaseManagementEntry"
PREVENTION = "/carlos/prevention/AddPrevention"
ERROR_LOG = "/carlos/eform/logEformError"
# What a browser hands window.onerror for a broken eForm script, plus the two fixed reports CARLOS
# itself sends. The first was answered 403 on a packaged install (932130 reads jQuery's "$(...)"
# callee abbreviation as shell command substitution); the rest are the message shapes Chromium,
# Firefox and Safari produce for the errors legacy eForms commonly throw.
ERROR_MESSAGES = (
    "Uncaught TypeError: $(...).error is not a function",
    "Uncaught TypeError: $(...).datepicker is not a function",
    "Uncaught TypeError: jQuery(...).dialog is not a function",
    "TypeError: $(...).tooltip is not a function",
    "TypeError: $(\"#Letter\").error is not a function. (In '$(\"#Letter\").error(function(){})', "
    "'$(\"#Letter\").error' is undefined)",
    "Uncaught TypeError: Cannot read properties of null (reading 'value')",
    "Uncaught TypeError: Cannot set properties of null (setting 'innerHTML')",
    "TypeError: can't access property \"value\", document.getElementById(...) is null",
    "TypeError: document.getElementById(...).parentNode is null",
    "Uncaught ReferenceError: $ is not defined",
    "Uncaught SyntaxError: Unexpected token '<'",
    "Uncaught SyntaxError: Unexpected token 'o', \"[object Object]\" is not valid JSON",
    "Uncaught SyntaxError: Failed to execute 'querySelector' on 'Document': 'input[name=1]' is not a valid selector.",
    "Uncaught Error: Syntax error, unrecognized expression: input[name=subject]:checked,select[name=x]",
    "Uncaught SecurityError: Failed to read a named property 'document' from 'Window': Blocked a frame "
    "with origin \"https://127.0.0.1\" from accessing a cross-origin frame.",
    "Uncaught Error: Failed to execute 'write' on 'Document': It isn't possible to write into a document "
    "from an asynchronously-loaded external script unless it is explicitly opened.",
    "Uncaught Error: cannot call methods on dialog prior to initialization; attempted to call method 'open'",
    "Uncaught TypeError: e.target.value.replace(...).toUpperCase is not a function",
    "Uncaught SyntaxError: missing ) after argument list",
    "Script error.",
    "RTL editor never left loading state; save blocked",
    "lab decision-support stub invoked (not deployed): autoLabReqPop",
)
RFI = "http://10.0.0.5/pacs/study"
TRAVERSAL = "../../private/record"
XSS = "<script>alert(1)</script>"
HEADER = "X-Waf-Harness-Id"
MARKER = "X-Waf-Harness-Upstream"
EVALUATION = {"949110", "949111", "959100", "980130"}
OUTBOUND_SENTINELS = {
    "clinical": (b"<?",),
    "sql": (b"You have an error in your SQL syntax", b"MySQL server version"),
    "java": (b"javax.servlet", b"java.lang.NullPointerException"),
}


@dataclass(frozen=True)
class Case:
    name: str
    issue: int
    method: str
    target: str
    body: str = ""
    expect: str = "allow"
    signatures: tuple[str, ...] = ()
    response: str = "ok"
    mime: str = "application/json"
    text: str = ""
    response_status: int = 200


def request_case(name, issue, route, field, value, method="POST", extra=(), **kw):
    pairs = [*extra, (field, value)]
    encoded = urlencode(pairs)
    if method == "GET":
        return Case(name, issue, method, route + ("&" if "?" in route else "?") + encoded, **kw)
    return Case(name, issue, method, route, encoded, **kw)


def load_corpus(node, root):
    source = root / "scripts/lib/clinical-prose-corpus.js"
    # Operator-selected local Node/corpus paths; fixed script and argv, no shell or fixture HTTP input.
    result = subprocess.run(  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-audit
        [node, "-e", "process.stdout.write(JSON.stringify(require(process.argv[1]).PROSE_CORPUS))", str(source)],
        capture_output=True, text=True, timeout=10, check=True, shell=False,
    )
    phrases = json.loads(result.stdout)
    if not phrases or not all(isinstance(p.get("text"), str) for p in phrases):
        raise ValueError("shared PROSE_CORPUS has no valid phrases")
    ordered = [p for p in phrases if p.get("leading")] + [p for p in phrases if not p.get("leading")]
    corpus = "\n".join(p["text"] for p in ordered)
    if not corpus.startswith("http://10.0.0.5/") or "../" not in corpus or "<?" not in corpus:
        raise ValueError("shared corpus no longer exercises the three issue payload shapes")
    return phrases, corpus


def build_cases(phrases, corpus):
    cases = []
    # Preserve diagnostic case names while the submitted Rx comment name is
    # distinct from comment_<n> used by the unrelated growth forms.
    rx_fields = {"instructions": "instructions", "drugName": "drugName", "comment": "rxComment"}
    # Every phrase separately diagnoses a failure hidden by a combined score;
    # the combined case proves the complete corpus survives in one field.
    variants = [("full", corpus)] + [(f"phrase-{i + 1}", p["text"]) for i, p in enumerate(phrases)]
    fields = [
        ("letter", 4571, LETTER, "Letter", "POST", ()),
        ("custom-name", 4572, RX, "customName", "POST", (("parameterValue", "saveCustomName"), ("randomId", "522730"))),
        *[(f"rx-{name}", 4573, RX + "?parameterValue=updateSaveAllDrugs", field + "_522730", "POST", ())
          for name, field in rx_fields.items()],
        ("tickler-search", 4575, TICKLERS, "search[value]", "GET", ()),
        ("encounter-reason", 4575, ENCOUNTER, "reason", "GET", ()),
        ("encounter-forward", 4575, FORWARD, "reason", "GET", ()),
        ("encounter-setup", 4575, ENTRY, "reason", "GET", (("method", "setUpMainEncounter"),)),
    ]
    for name, issue, route, field, method, extra in fields:
        cases.append(request_case(f"{name}-baseline", issue, route, field, "Routine follow up.", method, extra))
        for variant, value in variants:
            # saveRTL() entity-escapes the editor's HTML, with the URL first.
            if field == "Letter":
                value = html.escape(value, quote=True)
            cases.append(request_case(f"{name}-{variant}", issue, route, field, value, method, extra))

    inbound = {"expect": "inbound", "signatures": ("931100",)}
    for name, issue, route, field in (("letter", 4571, LETTER, "Letter"), ("custom-name", 4572, RX, "customName")):
        for suffix in ("Evil", "/child"):
            cases.append(request_case(f"{name}-route-{suffix.replace('/', '-')}", issue, route + suffix, field, RFI, **inbound))
        for method in ("GET", "PUT"):
            cases.append(request_case(f"{name}-wrong-{method}", issue, route, field, RFI, method, **inbound))
        cases.append(request_case(f"{name}-other-argument", issue, route, "unrelatedField", RFI, **inbound))
    cases.append(request_case("custom-name-xss", 4572, RX, "customName", XSS,
                              expect="inbound", signatures=("941100", "941110")))

    for name, field in rx_fields.items():
        for number in ("0", "17", "987654321"):
            cases.append(request_case(f"{name}-numeric-{number}", 4573, RX + "?parameterValue=updateSaveAllDrugs",
                                      f"{field}_{number}", corpus))
        # Both anchoring edges, a missing suffix, signs, decimals and letters.
        for i, malformed in enumerate((f"{field}_", f"{field}_abc", f"{field}_1x", f"x{field}_12",
                                       f"{field}_-1", f"{field}_1.2", f"{field}_12_extra",
                                       f"{field}_1\n", f"{field.upper()}_1")):
            cases.append(request_case(f"{name}-malformed-{i + 1}", 4573, RX, malformed, RFI, **inbound))
        for i, route in enumerate((RX, RX + "Evil", "/carlos/unrelated")):
            cases.append(request_case(f"{name}-xss-{i + 1}", 4573, route, field + "_522730", XSS,
                                      expect="inbound", signatures=("941100", "941110")))
        cases.append(request_case(f"{name}-sqli-still-inspected", 4573, RX, field + "_522730", "1' OR 1=1--",
                                  expect="inbound", signatures=("942100",)))
    cases.append(Case("rx-all-three-names", 4573, "POST", RX + "?parameterValue=updateSaveAllDrugs",
                      urlencode([(f"{field}_522730", corpus) for field in rx_fields.values()])))
    cases.append(request_case("rx-nonprose-random-id", 4573, RX, "randomId", RFI, **inbound))
    for field in ("quantity_522730", "repeats_522730"):
        cases.append(request_case(f"rx-nonprose-{field}", 4573, RX, field, RFI, **inbound))

    # Both growth JSPs POST to form/formname with their form_class/form_link
    # selectors; testing the page URL would miss a global numeric-name bypass.
    legacy_comments = [
        ("growth-0-36-comment", "/carlos/form/formname", "comment_1",
         (("form_class", "Growth0_36"), ("form_link", "formGrowth0_36.jsp"), ("submit", "save"))),
        ("growth-chart-comment", "/carlos/form/formname", "comment_1",
         (("form_class", "GrowthChart"), ("form_link", "formGrowthChart.jsp"), ("submit", "save"))),
        ("rx-legacy-comment", RX + "?parameterValue=updateSaveAllDrugs", "comment_522730", ()),
    ]
    for name, route, field, extra in legacy_comments:
        cases.append(request_case(f"{name}-plain", 4573, route, field, "Routine follow up.", extra=extra))
        for family, value, signatures in (
            ("rfi", RFI, ("931100",)),
            ("lfi", TRAVERSAL, ("930100", "930110")),
            ("rce", "x;cat /private", ("932100", "932110")),
            ("php", "<?php echo 1;", ("933100",)),
        ):
            cases.append(request_case(f"{name}-{family}", 4573, route, field, value, extra=extra,
                                      expect="inbound", signatures=signatures))

    for name, extra in (("missing-method", ()), ("save", (("method", "save"),)),
                        ("duplicate-method", (("method", "setUpMainEncounter"), ("method", "save"))),
                        ("duplicate-setup", (("method", "setUpMainEncounter"), ("method", "setUpMainEncounter")))):
        cases.append(request_case(f"encounter-setup-{name}", 4575, ENTRY, "reason", RFI, "GET", extra, **inbound))

    lfi = {"expect": "inbound", "signatures": ("930100", "930110")}
    for name, route, field in (("tickler", TICKLERS, "search[value]"), ("encounter", ENCOUNTER, "reason"),
                               ("forward", FORWARD, "reason"), ("setup", ENTRY, "reason")):
        # Final encounter setup is selected by a GET operation argument.
        operation = "method=setUpMainEncounter&" if route == ENTRY else ""
        raw = "See+scanned+report+../../images/ecg.png+for+the+tracing."
        cases.append(Case(f"{name}-raw-query-prose", 4575, "GET", route + "?" + operation + urlencode({field: ""}) + raw))
        for other in ("file", "encType"):
            cases.append(request_case(f"{name}-other-{other}", 4575, route, field, corpus, "GET",
                                      (("method", "setUpMainEncounter"), (other, TRAVERSAL)) if operation else ((other, TRAVERSAL),), **lfi))
        cases.append(Case(f"{name}-raw-other-argument", 4575, "GET",
                          route + "?" + operation + urlencode({field: "Routine"}) + "&file=" + TRAVERSAL, **lfi))
        for i, param in enumerate(("../../private/record", "%2e%2e%2f%2e%2e%2fprivate")):
            cases.append(Case(f"{name}-argument-name-traversal-{i + 1}", 4575, "GET",
                              route + "?" + operation + urlencode({field: "Routine"}) + "&" + param + "=plain", **lfi))
        # The semicolon variants still match the route's accepted delimiter:
        # they detect indiscriminate removal of the full URI traversal targets.
        for i, suffix in enumerate(("/../private", "/%2e%2e/private", ";slot=/../private",
                                   ";slot=/%2e%2e%2fprivate", ";slot=/%252e%252e%252fprivate",
                                   ";slot=%253f/../../private")):
            cases.append(Case(f"{name}-path-traversal-{i + 1}", 4575, "GET",
                              route + suffix + "?" + operation + urlencode({field: "Routine"}), **lfi))
        # 930110's URL decoding and cmdLine transformation remove the quote;
        # 930100 alone cannot detect this path. Pin the decoded-path target.
        cases.append(Case(f"{name}-path-traversal-decoded-quote", 4575, "GET",
                          route + ";slot=/.%27./private?" + operation + urlencode({field: "Routine"}),
                          expect="inbound", signatures=("930110",)))
        # Capturing REQUEST_URI instead of REQUEST_URI_RAW decodes %3f before
        # splitting off the query, hiding the traversal after the false '?'.
        cases.append(Case(f"{name}-path-traversal-encoded-question", 4575, "GET",
                          route + ";slot=%3f/../../private?" + operation + urlencode({field: "Routine"}), **lfi))
        # REQUEST_URI is decoded once by the connector before 930110 applies
        # urlDecodeUni. A replacement path must retain both decoding stages,
        # including percent characters whose hexadecimal digits are encoded.
        for variant, segment in (("all-digits", "%25%32%65%25%32%65%25%32%66"),
                                 ("some-digits", "%25%32e%25%32e%25%32f")):
            cases.append(Case(f"{name}-path-traversal-nested-{variant}", 4575, "GET",
                              route + ";slot=/" + segment + "private?" + operation + urlencode({field: "Routine"}),
                              expect="inbound", signatures=("930110",)))
        cases.append(request_case(f"{name}-prefix-query-traversal", 4575, route + "Evil", field, TRAVERSAL, "GET", **lfi))
        cases.append(request_case(f"{name}-wrong-method-traversal", 4575, route, field, TRAVERSAL, "POST", **lfi))
        cases.append(request_case(f"{name}-xss-still-inspected", 4575, route, field, XSS, "GET",
                                  expect="inbound", signatures=("941100", "941110")))

    for name, route, method in (("vitals", VITALS, "POST"), ("tickler", TICKLERS, "GET")):
        for mime_index, mime in enumerate(("application/json", "application/json; charset=UTF-8")):
            for variant, value in variants:
                cases.append(Case(f"{name}-json-{mime_index}-{variant}", 4574, method, route,
                                  response="clinical", mime=mime, text=value))
        outbound = {"expect": "outbound", "signatures": ("953120",), "response": "clinical", "text": "Wound <?> follow up."}
        for suffix in ("Evil", "/child"):
            cases.append(Case(f"{name}-json-wrong-route-{suffix.replace('/', '-')}", 4574, method, route + suffix, **outbound))
        for wrong_method in (("GET", "PUT") if method == "POST" else ("POST", "PUT")):
            cases.append(Case(f"{name}-json-wrong-method-{wrong_method}", 4574, wrong_method, route, **outbound))
        for mime in ("text/plain", "text/html", "text/xml"):
            cases.append(Case(f"{name}-json-wrong-mime-{mime.split('/')[1]}", 4574, method, route, mime=mime, **outbound))
        for status in (201, 500):
            cases.append(Case(f"{name}-json-wrong-status-{status}", 4574, method, route, response_status=status, **outbound))
        cases.append(Case(f"{name}-sql-leak", 4574, method, route, expect="outbound", signatures=("951230",), response="sql"))
        # Each Java signature scores 3; together they cross the unchanged 4.
        cases.append(Case(f"{name}-java-leak", 4574, method, route, expect="outbound", signatures=("952100", "952110"), response="java"))
    # Exclusion 1117: every free-text box the add-prevention and Rh-injection pages post. The
    # vaccine's lot and the XSS family stay inspected, as does the same text on another route
    # or method.
    for field in ("comments", "reason", "reasonOtherText", "neverReason", "location2", "providerName"):
        cases.append(request_case(f"prevention-{field}-baseline", 4742, PREVENTION, field, "Routine follow up."))
        for variant, value in variants:
            cases.append(request_case(f"prevention-{field}-{variant}", 4742, PREVENTION, field, value))
    cases.append(request_case("prevention-route-Evil", 4742, PREVENTION + "Evil", "neverReason", RFI, **inbound))
    cases.append(request_case("prevention-wrong-GET", 4742, PREVENTION, "neverReason", RFI, "GET", **inbound))
    cases.append(request_case("prevention-nonprose-lot", 4742, PREVENTION, "lot", RFI, **inbound))
    cases.append(request_case("prevention-xss-still-inspected", 4742, PREVENTION, "providerName", XSS,
                              expect="inbound", signatures=("941100", "941110")))

    # Exclusion 1075: the eForm script-error report (toolbar window.onerror, lab-stub and RTL
    # reports) posts formId plus the browser's error message. Only ARGS:error on POST to this one
    # route is exempt, and only from the attack-rce/xss/sqli tags that fire on real messages;
    # formId, other routes and methods, and the remaining families on the message stay inspected.
    shell = "Uncaught TypeError: $(...).error is not a function"
    rce = {"expect": "inbound", "signatures": ("932130",)}
    form_id = (("formId", "37"),)
    for i, message in enumerate(ERROR_MESSAGES):
        cases.append(request_case(f"eform-error-message-{i + 1}", 0, ERROR_LOG, "error", message, extra=form_id))
    cases.append(request_case("eform-error-route-Evil", 0, ERROR_LOG + "Evil", "error", shell, extra=form_id, **rce))
    cases.append(request_case("eform-error-route-child", 0, ERROR_LOG + "/child", "error", shell, extra=form_id, **rce))
    cases.append(request_case("eform-error-wrong-GET", 0, ERROR_LOG, "error", shell, "GET", form_id, **rce))
    cases.append(request_case("eform-error-wrong-PUT", 0, ERROR_LOG, "error", shell, "PUT", form_id, **rce))
    # formId is a number the page renders; every exempted family still scores it.
    for family, value, signatures in (("rce", "$(id)", ("932130",)),
                                      ("xss", XSS, ("941100", "941110")),
                                      ("sqli", "1' OR 1=1--", ("942100",))):
        cases.append(request_case(f"eform-error-formid-{family}", 0, ERROR_LOG, "formId", value,
                                  extra=(("error", "x"),), expect="inbound", signatures=signatures))
    # The families that never fired on a browser message stay on ARGS:error itself.
    for family, value, signatures in (("lfi", TRAVERSAL, ("930100", "930110")),
                                      ("rfi", RFI, ("931100",)),
                                      ("php", "<?php echo 1;", ("933100",))):
        cases.append(request_case(f"eform-error-{family}-still-inspected", 0, ERROR_LOG, "error", value,
                                  extra=form_id, expect="inbound", signatures=signatures))
    cases.append(request_case("eform-error-xss-other-route", 0, ERROR_LOG + "Evil", "error", XSS, extra=form_id,
                              expect="inbound", signatures=("941100", "941110")))

    if len({case.name for case in cases}) != len(cases):
        raise ValueError("duplicate case names")
    return cases


def response_body(case, token):
    body = {"probe": token, "ok": True}
    if case.response == "clinical":
        if case.target.startswith(VITALS):
            body["encounterText"] = case.text
        else:
            body["data"] = [{"message": case.text}]
    elif case.response == "sql":
        body["error"] = "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version"
    elif case.response == "java":
        body["error"] = "javax.servlet.ServletException: java.lang.NullPointerException"
    return json.dumps(body, ensure_ascii=False).encode("utf-8")


class FixtureServer(ThreadingHTTPServer):
    daemon_threads = True
    block_on_close = False

    def __init__(self, timeout):
        self.fixtures = {}
        self.receipts = {}
        self.request_timeout = timeout
        super().__init__(("127.0.0.1", 0), FixtureHandler)

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(self.request_timeout)
        return connection, address


class FixtureHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def handle_fixture(self):
        token = self.headers.get(HEADER, "")
        if not re.fullmatch(r"[A-Za-z0-9-]{1,80}", token):
            self.send_error(418, "Invalid harness fixture token")
            return
        case = self.server.fixtures.get(token)
        if case is None:
            self.send_error(418, "Unknown harness fixture")
            return
        length = self.headers.get("Content-Length", "0")
        try:
            if not re.fullmatch(r"[0-9]+", length):
                raise ValueError("Content-Length must be a nonnegative decimal integer")
            size = int(length)
        except ValueError:
            self.send_error(400, "Invalid Content-Length")
            return
        if size > 1024 * 1024:
            self.send_error(413)
            return
        data = self.rfile.read(size)
        receipt = {"method": self.command, "target": self.path,
                   "body": data.decode("ascii", errors="backslashreplace")}
        if not data.isascii():
            # Escaped byte diagnostics must not compare equal to a request
            # containing the same literal ASCII backslash sequences.
            receipt["body_non_ascii"] = True
        self.server.receipts.setdefault(token, []).append(receipt)
        body = response_body(case, token)
        self.send_response(case.response_status)
        self.send_header("Content-Type", case.mime)
        self.send_header("Content-Length", str(len(body)))
        # Keep explicit CR/LF removal at the header sink as defense in depth,
        # even though validation above rejects them before fixture lookup.
        self.send_header(MARKER, token.replace("\r", "").replace("\n", ""))
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass  # nginx may close its upstream after a phase-4 intervention.
        self.close_connection = True

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = handle_fixture


def quoted_path(path):
    text = str(path)
    if any(c in text for c in '\n\r\x00"$\\'):
        raise ValueError(f"unsupported configuration path: {text!r}")
    return '"' + text + '"'


def relocate(text, mappings):
    """Change only the path argument of known filesystem directives."""
    pattern = re.compile(r"^(\s*(?:Include|SecTmpDir|SecDataDir|SecUploadDir|SecUnicodeMapFile|SecAuditLog|SecDebugLog)\s+)(\S+)(.*)$")
    lines = []
    for line in text.splitlines(keepends=True):
        match = pattern.match(line.rstrip("\n"))
        if match:
            old = match[2].strip('"')
            for source, destination in mappings:
                if old == source or old.startswith(source + "/"):
                    relocated = str(destination) + old[len(source):]
                    # SecUnicodeMapFile in libmodsecurity3 rejects quotes;
                    # private temporary prefixes must therefore have no spaces.
                    quoted_path(relocated)  # Validate configuration metacharacters.
                    if any(c.isspace() for c in relocated):
                        raise ValueError("ModSecurity temporary paths must not contain whitespace")
                    line = match[1] + relocated + match[3] + ("\n" if line.endswith("\n") else "")
                    break
            else:
                raise ValueError(f"unmapped policy filesystem directive: {line.strip()}")
        lines.append(line)
    return "".join(lines)


def prepare_prefix(prefix, args):
    for name in ("logs", "state/tmp", "state/data", "nginx-body", "nginx-proxy", "nginx-fastcgi", "nginx-uwsgi", "nginx-scgi"):
        (prefix / name).mkdir(parents=True)
    sources = [
        (args.repo / "debian/assets/modsecurity", prefix / "policy"),
        (args.crs_dir, prefix / "crs"),
    ]
    hashes = {}
    for source, destination in sources:
        shutil.copytree(source, destination)
        for snapshot in sorted(destination.rglob("*")):
            if snapshot.is_file():
                hashes[str(source / snapshot.relative_to(destination))] = hashlib.sha256(snapshot.read_bytes()).hexdigest()
    for source, destination in ((args.crs_setup, prefix / "crs-setup.conf"), (args.unicode_map, prefix / "unicode.mapping")):
        shutil.copyfile(source, destination)
        hashes[str(source)] = hashlib.sha256(destination.read_bytes()).hexdigest()
    corpus_file = args.repo / "scripts/lib/clinical-prose-corpus.js"
    hashes[str(corpus_file)] = hashlib.sha256(corpus_file.read_bytes()).hexdigest()
    shutil.copyfile(corpus_file, prefix / "clinical-prose-corpus.js")
    mappings = [
        ("/etc/carlos-emr/modsecurity", prefix / "policy"),
        ("/usr/share/modsecurity-crs", prefix / "crs"),
        ("/etc/modsecurity/crs/crs-setup.conf", prefix / "crs-setup.conf"),
        ("/etc/nginx/unicode.mapping", prefix / "unicode.mapping"),
        ("/var/lib/carlos-emr/modsecurity", prefix / "state"),
        ("/var/log/carlos-emr/modsec", prefix / "logs"),
    ]
    # Only files in the actual Include graph need relocation. CRS also ships
    # unused Apache/honeypot examples with deliberately unrelated log paths.
    for conf in [*(prefix / "policy").glob("*.conf"), prefix / "crs-setup.conf", *(prefix / "crs/rules").glob("*.conf")]:
        conf.write_text(relocate(conf.read_text(), mappings))
    main = (prefix / "policy/main.conf").read_text()
    if re.findall(r"(?mi)^\s*SecRuleEngine\s+(\S+)", main) != ["On"]:
        raise ValueError("shipped main.conf must have exactly SecRuleEngine On")
    return {"source_sha256": hashes, "relocations": {s: str(d) for s, d in mappings}}


def nginx_config(prefix, args, port, upstream):
    q = lambda name: quoted_path(prefix / name)
    text = f"""load_module {quoted_path(args.module)};
daemon off;
master_process off;
pid {q('nginx.pid')};
error_log {q('logs/error.log')} info;
events {{ worker_connections 64; }}
http {{
    log_format harness '$http_x_waf_harness_id $status';
    access_log {q('logs/access.log')} harness;
    client_body_temp_path {q('nginx-body')};
    proxy_temp_path {q('nginx-proxy')};
    fastcgi_temp_path {q('nginx-fastcgi')};
    uwsgi_temp_path {q('nginx-uwsgi')};
    scgi_temp_path {q('nginx-scgi')};
    client_header_timeout {args.request_timeout}s;
    client_body_timeout {args.request_timeout}s;
    send_timeout {args.request_timeout}s;
    keepalive_timeout 0;
    server {{
        listen 127.0.0.1:{port};
        server_name harness.invalid;
        modsecurity on;
        modsecurity_transaction_id "$http_x_waf_harness_id";
        modsecurity_rules_file {q('policy/main.conf')};
        location / {{
            proxy_pass http://127.0.0.1:{upstream};
            proxy_http_version 1.1;
            proxy_set_header Host harness.invalid;
            proxy_set_header Connection close;
            proxy_connect_timeout {args.request_timeout}s;
            proxy_read_timeout {args.request_timeout}s;
            proxy_send_timeout {args.request_timeout}s;
        }}
    }}
}}
"""
    (prefix / "nginx.conf").write_text(text)


def read_since(path, offset=0):
    if not path.exists():
        return ""
    with path.open("rb") as stream:
        stream.seek(offset)
        return stream.read().decode("utf-8", errors="replace")


def audit_rules(audit, errors, token):
    rules = set()
    transactions = []
    decoder = json.JSONDecoder()
    # ModSecurity's serial JSON audit log writes one transaction per line. A
    # previous transaction may still be flushing when run_case snapshots its
    # byte offset: the next read then begins in that record, sometimes at a
    # quoted field that raw_decode accepts as a standalone string. Discard an
    # incomplete/malformed line, not all later complete records. Every poll
    # rereads from the same offset, so an unfinished final record is retried.
    # Keep support for concatenated complete documents on a single line too.
    for line in audit.splitlines():
        remaining = line.strip()
        while remaining:
            try:
                document, end = decoder.raw_decode(remaining)
            except ValueError:
                break
            remaining = remaining[end:].lstrip()
            if not isinstance(document, dict):
                continue
            tx = document.get("transaction")
            if not isinstance(tx, dict):
                continue
            request = tx.get("request", {})
            headers = request.get("headers", {}) if isinstance(request, dict) else {}
            identified = tx.get("unique_id") == token or (
                isinstance(headers, dict) and any(
                    isinstance(k, str) and k.lower() == HEADER.lower() and v == token
                    for k, v in headers.items()))
            if identified:
                transactions.append(tx)
                messages = tx.get("messages", [])
                if isinstance(messages, list):
                    for message in messages:
                        details = message.get("details", {}) if isinstance(message, dict) else {}
                        rule = details.get("ruleId") if isinstance(details, dict) else None
                        if rule is not None and str(rule).isdigit():
                            rules.add(str(rule))
    # Some connectors lose the audit response during phase-4 interruption.
    # Their error log is useful only with this exact transaction id.
    correlated = "\n".join(line for line in errors.splitlines() if f'[unique_id "{token}"]' in line)
    rules.update(re.findall(r'\[id "(\d+)"\]', correlated))
    return rules, transactions, correlated


def assess(case, token, status, body, marker, error, receipts, rules):
    reasons = []
    expected = response_body(case, token)
    expected_receipt = {"method": case.method, "target": case.target, "body": case.body}
    if case.expect in ("allow", "outbound") and receipts != [expected_receipt]:
        reasons.append("upstream did not receive the exact request once")
    if case.expect == "allow":
        if status != 200 or error or marker != token or body != expected:
            reasons.append("expected complete, exact upstream response with its unique marker")
        if rules & EVALUATION - {"980130"}:
            reasons.append("unexpected anomaly blocking evaluation")
    else:
        if not rules.intersection(case.signatures):
            reasons.append("missing expected signature: " + " or ".join(case.signatures))
        if case.expect == "inbound":
            if status != 403 or error or receipts:
                reasons.append("expected inbound 403 before reaching upstream")
            if "949110" not in rules:
                reasons.append("missing inbound anomaly block 949110")
        else:
            if "959100" not in rules:
                reasons.append("missing outbound anomaly block 959100")
            sentinels = OUTBOUND_SENTINELS.get(case.response, ())
            if not sentinels or not all(sentinel in expected for sentinel in sentinels):
                reasons.append("outbound fixture lacks its expected sensitive signatures")
            # A dropped connection is insufficient if the sensitive content
            # already arrived, even when only the final JSON brace is missing.
            if any(sentinel in body for sentinel in sentinels):
                reasons.append("sensitive outbound fixture content reached the client")
            if body == expected or (status == 200 and not error):
                reasons.append("outbound payload was delivered without interruption")
    return reasons


def perform_request(port, case, token, timeout):
    status, body, marker, error = None, b"", "", ""
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
    headers = {"Host": "harness.invalid", HEADER: token, "User-Agent": "clinical-policy-harness/1.0",
               "Accept": "application/json", "Connection": "close"}
    payload = None
    if case.method != "GET":
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        payload = case.body.encode("ascii")
    try:
        # http.client preserves literal /../ and percent escapes in the target.
        conn.request(case.method, case.target, payload, headers)
        response = conn.getresponse()
        status = response.status
        marker = response.getheader(MARKER, "")
        body = response.read()
    except http.client.IncompleteRead as exc:
        body, error = exc.partial, f"IncompleteRead: {exc}"
    except (OSError, http.client.HTTPException) as exc:
        error = f"{type(exc).__name__}: {exc}"
    finally:
        conn.close()
    return status, body, marker, error


def run_case(prefix, server, port, case, token, args):
    server.fixtures[token] = case
    paths = {key: prefix / "logs" / name for key, name in
             (("audit", "modsec_audit.log"), ("error", "error.log"), ("access", "access.log"))}
    offsets = {key: path.stat().st_size if path.exists() else 0 for key, path in paths.items()}
    start = time.monotonic()
    status, body, marker, error = perform_request(port, case, token, args.request_timeout)
    deadline = time.monotonic() + args.evidence_timeout
    while True:
        logs = {key: read_since(path, offsets[key]) for key, path in paths.items()}
        rules, transactions, correlated = audit_rules(logs["audit"], logs["error"], token)
        finished = any(line.startswith(token + " ") for line in logs["access"].splitlines())
        needs_block = case.expect != "allow" and not rules.intersection({"949110", "959100"})
        if (finished and not needs_block) or time.monotonic() >= deadline:
            break
        time.sleep(0.02)
    receipts = server.receipts.get(token, [])
    reasons = assess(case, token, status, body, marker, error, receipts, rules)
    if not finished:
        reasons.append("missing correlated nginx access-log completion")
    return {
        "case": asdict(case), "transaction_id": token, "passed": not reasons, "reasons": reasons,
        "status": status, "transport_error": error, "rules": sorted(rules),
        "response_body": body.decode("utf-8", errors="replace"), "upstream_marker": marker,
        "upstream_receipts": receipts, "audit_transactions": transactions,
        "correlated_error_log": correlated, "elapsed_seconds": round(time.monotonic() - start, 3),
    }


def stop_process(process):
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)


def run_engine(prefix, args, cases, report):
    process = None
    server = None
    thread = None
    try:
        if args.check_config:
            port, upstream = 18080, 18081  # nginx -t does not open listeners.
        else:
            server = FixtureServer(args.request_timeout)
            upstream = server.server_port
            with socket.socket() as reservation:
                reservation.bind(("127.0.0.1", 0))
                port = reservation.getsockname()[1]
        report["ports"] = {"nginx": port, "upstream": upstream}
        nginx_config(prefix, args, port, upstream)
        command = [args.nginx, "-p", str(prefix) + "/", "-c", str(prefix / "nginx.conf"), "-e", str(prefix / "logs/error.log")]
        # Operator-selected nginx and private config paths; argv only, no shell or fixture HTTP input.
        result = subprocess.run(command + ["-t"], capture_output=True, text=True, timeout=args.startup_timeout, shell=False)  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-audit
        (prefix / "logs/config-test.log").write_text(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError("nginx -t failed:\n" + result.stdout + result.stderr)
        if args.check_config:
            print("PASS nginx -t (shipped policy, filesystem/include relocation only; no listeners)")
            return 0
        thread = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.05}, daemon=True)
        thread.start()
        with (prefix / "logs/process.log").open("w") as output:
            # Same operator-selected executable/private config argv; no shell or fixture HTTP input.
            process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, shell=False)  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-audit
        report["nginx_pid"] = process.pid
        readiness = Case("readiness", 0, "GET", "/carlos/harness-ready")
        token = "ready-" + uuid.uuid4().hex
        server.fixtures[token] = readiness
        deadline = time.monotonic() + args.startup_timeout
        while True:
            if process.poll() is not None:
                raise RuntimeError("nginx exited at startup:\n" + read_since(prefix / "logs/process.log") + read_since(prefix / "logs/error.log"))
            status, body, marker, error = perform_request(port, readiness, token, min(0.5, args.request_timeout))
            if status == 200 and marker == token and body == response_body(readiness, token) and not error:
                break
            if time.monotonic() >= deadline:
                raise RuntimeError(f"nginx readiness timed out: status={status}, error={error}\n" + read_since(prefix / "logs/error.log"))
            time.sleep(0.05)
        print(f"nginx pid={process.pid}, loopback ports nginx={port} upstream={upstream}; {len(cases)} cases", flush=True)
        run_id = uuid.uuid4().hex[:12]
        for number, case in enumerate(cases, 1):
            if process.poll() is not None:
                raise RuntimeError("nginx exited during the test run")
            result = run_case(prefix, server, port, case, f"waf-{run_id}-{number:04}", args)
            report["results"].append(result)
            label = "PASS" if result["passed"] else "FAIL"
            print(f"{label} {case.name}: status={result['status']} rules={','.join(result['rules']) or '-'}"
                  + ("; " + "; ".join(result["reasons"]) if result["reasons"] else ""), flush=True)
        failures = sum(not result["passed"] for result in report["results"])
        print(f"{len(cases) - failures}/{len(cases)} passed; {failures} failed. Policy harness only; no application integration tested.")
        return int(failures > 0)
    finally:
        stop_process(process)
        if server is not None:
            if thread is not None:
                server.shutdown()
                thread.join(timeout=2)
            server.server_close()
        report["cleanup"] = {"nginx_stopped": process is None or process.poll() is not None,
                             "upstream_stopped": thread is None or not thread.is_alive()}


class MechanicsTests(unittest.TestCase):
    """Harness integrity tests, not substitutes for live security checks."""

    @staticmethod
    def fixture_exchange(token, fixtures, *, method="GET", body=b"", content_length=None):
        # Exercise the real HTTP parser and response writer without listeners.
        handler = FixtureHandler.__new__(FixtureHandler)
        headers = f"{method} /fixture HTTP/1.1\r\nHost: harness.invalid\r\n{HEADER}: {token}\r\n"
        if content_length is not None:
            headers += f"Content-Length: {content_length}\r\n"
        handler.rfile = io.BytesIO((headers + "\r\n").encode("ascii") + body)
        handler.wfile = io.BytesIO()
        handler.server = SimpleNamespace(fixtures=fixtures, receipts={})
        handler.handle_one_request()
        return handler.wfile.getvalue(), handler

    def test_fixture_marker_preserves_valid_registered_tokens(self):
        case = Case("fixture", 0, "GET", "/fixture")
        for token in ("ready-" + "a" * 32, "waf-0123456789ab-0001"):
            with self.subTest(token=token):
                response, handler = self.fixture_exchange(token, {token: case})
                headers, body = response.split(b"\r\n\r\n", 1)
                self.assertTrue(headers.startswith(b"HTTP/1.1 200 "))
                self.assertIn(f"{MARKER}: {token}".encode("ascii"), headers.split(b"\r\n"))
                self.assertEqual(body, response_body(case, token))
                self.assertEqual(handler.server.receipts, {
                    token: [{"method": "GET", "target": "/fixture", "body": ""}],
                })
        response, handler = self.fixture_exchange("unknown-token", {})
        self.assertTrue(response.startswith(b"HTTP/1.1 418 "))
        self.assertNotIn(MARKER.encode("ascii"), response)
        self.assertEqual(handler.server.receipts, {})

    def test_fixture_marker_rejects_hostile_and_folded_headers(self):
        case = Case("fixture", 0, "GET", "/fixture")
        tokens = ("", "waf_test", "waf test", "a" * 81, "waf-test\x00", "waf-test\x7f",
                  "waf-test\r\n X-Injected: yes", "waf-test\r\n\tX-Injected: yes",
                  "waf-test\n X-Injected: yes", "waf-test\r X-Injected: yes")
        for token in tokens:
            with self.subTest(token=token):
                # Register even the hostile key: membership alone must not
                # allow a folded header to reach the response or a receipt.
                response, handler = self.fixture_exchange(token, {token: case})
                self.assertEqual(handler.headers.get(HEADER), token)
                self.assertTrue(response.startswith(b"HTTP/1.1 418 "))
                self.assertNotIn(MARKER.encode("ascii"), response)
                self.assertNotIn(b"X-Injected", response)
                self.assertEqual(handler.server.receipts, {})

    def test_fixture_rejects_invalid_and_oversized_content_lengths(self):
        case = Case("fixture", 0, "POST", "/fixture")
        for length, status in (("invalid", 400), ("", 400), ("-1", 400), ("1.5", 400),
                               ("+1", 400), ("1_0", 400), ("1e3", 400), ("1048577", 413)):
            with self.subTest(length=length):
                response, handler = self.fixture_exchange(
                    "fixture-id", {"fixture-id": case}, method="POST",
                    content_length=length, body=b"not-consumed",
                )
                self.assertTrue(response.startswith(f"HTTP/1.1 {status} ".encode("ascii")))
                self.assertNotIn(MARKER.encode("ascii"), response)
                self.assertEqual(handler.server.receipts, {})
                self.assertEqual(handler.rfile.read(), b"not-consumed")

    def test_non_ascii_receipt_cannot_match_literal_ascii_escapes(self):
        token = "fixture-id"
        for payload in (b"comment=\xff", "comment=café".encode("utf-8")):
            with self.subTest(payload=payload):
                escaped = payload.decode("ascii", errors="backslashreplace")
                case = Case("fixture", 0, "POST", "/fixture", body=escaped)
                response, handler = self.fixture_exchange(
                    token, {token: case}, method="POST", body=payload, content_length=str(len(payload)),
                )
                self.assertTrue(response.startswith(b"HTTP/1.1 200 "))
                self.assertEqual(response.split(b"\r\n\r\n", 1)[1], response_body(case, token))
                receipts = handler.server.receipts[token]
                self.assertEqual(receipts[0]["body"], escaped)
                reasons = assess(case, token, 200, response_body(case, token), token, "", receipts, set())
                self.assertIn("upstream did not receive the exact request once", reasons)
                literal = escaped.encode("ascii")
                response, handler = self.fixture_exchange(
                    token, {token: case}, method="POST", body=literal, content_length=str(len(literal)),
                )
                self.assertTrue(response.startswith(b"HTTP/1.1 200 "))
                self.assertNotEqual(receipts, handler.server.receipts[token])
                self.assertFalse(assess(case, token, 200, response_body(case, token), token, "",
                                        handler.server.receipts[token], set()))

    def test_relocation_changes_only_filesystem_arguments(self):
        source = '# Include /etc/site/nope\nSecRuleEngine On\nInclude /etc/site/rules.conf\nSecUnicodeMapFile /etc/site/map 20127\nSecAction "id:1,setvar:tx.score=5"\n'
        relocated = relocate(source, [("/etc/site", Path("/tmp/snapshot"))])
        self.assertEqual(relocated, source.replace('Include /etc/site/rules.conf', 'Include /tmp/snapshot/rules.conf').replace('SecUnicodeMapFile /etc/site/map', 'SecUnicodeMapFile /tmp/snapshot/map'))
        with self.assertRaises(ValueError):
            relocate("SecAuditLog /production/audit\n", [])

    def test_audit_and_error_evidence_are_transaction_scoped(self):
        def tx(token, rule):
            return json.dumps({"transaction": {"request": {"headers": {HEADER: token}}, "messages": [{"details": {"ruleId": rule}}]}})
        audit = tx("old", "931100") + "\n" + tx("current", "941100")
        errors = '[id "949110"] [unique_id "old"]\n[id "959100"] [unique_id "current"]'
        rules, transactions, _ = audit_rules(audit, errors, "current")
        self.assertEqual(rules, {"941100", "959100"})
        self.assertEqual(len(transactions), 1)

    def test_audit_offset_inside_previous_record_does_not_hide_current_transaction(self):
        old = json.dumps({"transaction": {"unique_id": "old", "messages": [
            {"details": {"ruleId": "931100"}}], "request": {"headers": {HEADER: "old"}}}})
        current = json.dumps({"transaction": {"unique_id": "current", "messages": [
            {"details": {"ruleId": "941100"}}]}})
        for offset in range(len(old) + 1):
            with self.subTest(offset=offset):
                rules, transactions, _ = audit_rules(old[offset:] + "\n" + current + "\n", "", "current")
                self.assertEqual(rules, {"941100"})
                self.assertEqual([tx["unique_id"] for tx in transactions], ["current"])

    def test_partial_audit_tail_is_retried_without_borrowing_unrelated_rules(self):
        current = json.dumps({"transaction": {"unique_id": "current", "messages": [
            {"details": {"ruleId": "941100"}}]}})
        for end in range(1, len(current)):
            with self.subTest(end=end):
                self.assertEqual(audit_rules(current[:end], '[id "949110"] [unique_id "old"]', "current")[:2],
                                 (set(), []))
        rules, transactions, _ = audit_rules(current + "\n", "", "current")
        self.assertEqual(rules, {"941100"})
        self.assertEqual(len(transactions), 1)

    def test_nonobject_and_malformed_audit_envelopes_cannot_supply_rule_evidence(self):
        malformed = ["field", None, 42, [], {"transaction": "current"},
                     {"transaction": {"request": "current", "messages": [{"details": {"ruleId": "931100"}}]}},
                     {"transaction": {"request": {"headers": [HEADER, "current"]},
                                      "messages": [{"details": {"ruleId": "931100"}}]}}]
        audit = "\n".join(json.dumps(document) for document in malformed)
        rules, transactions, _ = audit_rules(audit, '[id "949110"] [unique_id "current-other"]', "current")
        self.assertEqual(rules, set())
        self.assertEqual(transactions, [])
        # Invalid message shapes on a correlated record must not crash or turn
        # arbitrary values into signature ids that could make a denial pass.
        for messages in ("931100", [{"details": "931100"}], ["931100"], [{"details": {"ruleId": {"id": "931100"}}}]):
            with self.subTest(messages=messages):
                malformed_tx = json.dumps({"transaction": {"unique_id": "current", "messages": messages}})
                self.assertEqual(audit_rules(malformed_tx, "", "current")[0], set())

    def test_concatenated_audit_documents_keep_exact_transaction_correlation(self):
        documents = [json.dumps({"transaction": {"unique_id": token, "messages": [
            {"details": {"ruleId": rule}}]}}) for token, rule in (("old", "931100"), ("current", "941100"))]
        rules, transactions, _ = audit_rules("".join(documents), "", "current")
        self.assertEqual(rules, {"941100"})
        self.assertEqual(len(transactions), 1)

    def test_false_block_and_unrelated_signature_cannot_pass(self):
        case = Case("blocked", 0, "POST", RX, expect="inbound", signatures=("931100",))
        self.assertTrue(assess(case, "id", 404, b"", "", "", [], {"931100", "949110"}))
        self.assertTrue(assess(case, "id", 403, b"", "", "", [], {"941100", "949110"}))
        self.assertTrue(assess(case, "id", 403, b"", "", "", [], {"931100"}))
        self.assertFalse(assess(case, "id", 403, b"", "", "", [], {"931100", "949110"}))

    def test_phase_four_requires_block_and_missing_payload(self):
        case = Case("outbound", 0, "GET", TICKLERS, expect="outbound", signatures=("953120",), response="clinical", text="<?")
        receipt = [{"method": "GET", "target": TICKLERS, "body": ""}]
        rules = {"953120", "959100"}
        self.assertTrue(assess(case, "id", 200, response_body(case, "id"), "id", "", receipt, rules))
        self.assertTrue(assess(case, "id", None, b"", "", "timeout", receipt, set()))
        self.assertFalse(assess(case, "id", 200, b"", "id", "IncompleteRead", receipt, rules))

    def test_truncated_sensitive_response_cannot_pass(self):
        receipt = [{"method": "POST", "target": VITALS, "body": ""}]
        for response, signature in (("clinical", "953120"), ("sql", "951230"), ("java", "952110")):
            case = Case("outbound", 0, "POST", VITALS, expect="outbound", signatures=(signature,),
                        response=response, text="Wound <?> follow up.")
            rules = {signature, "959100"}
            # The old check accepted this: almost all of the fixture arrived,
            # with a transport interruption and otherwise valid WAF evidence.
            fragments = [response_body(case, "id")[:-1], *OUTBOUND_SENTINELS[response]]
            for fragment in fragments:
                with self.subTest(response=response, fragment=fragment):
                    reasons = assess(case, "id", 200, fragment, "id", "IncompleteRead", receipt, rules)
                    self.assertIn("sensitive outbound fixture content reached the client", reasons)

    def test_outbound_requires_exact_request_once(self):
        case = Case("outbound", 0, "POST", VITALS, body="comment=Routine", expect="outbound",
                    signatures=("953120",), response="clinical", text="<?")
        receipt = {"method": "POST", "target": VITALS, "body": "comment=Routine"}
        rules = {"953120", "959100"}
        self.assertFalse(assess(case, "id", 200, b"", "id", "IncompleteRead", [receipt], rules))
        wrong = [[], [receipt, receipt], [{**receipt, "method": "GET"}],
                 [{**receipt, "target": VITALS + "Evil"}], [{**receipt, "body": "comment=Changed"}]]
        for receipts in wrong:
            with self.subTest(receipts=receipts):
                reasons = assess(case, "id", 200, b"", "id", "IncompleteRead", receipts, rules)
                self.assertIn("upstream did not receive the exact request once", reasons)

    def test_outbound_fixture_must_exercise_sensitive_signature(self):
        case = Case("empty-php-fixture", 0, "GET", TICKLERS, expect="outbound", signatures=("953120",),
                    response="clinical", text="Routine follow up.")
        receipt = [{"method": "GET", "target": TICKLERS, "body": ""}]
        reasons = assess(case, "id", 200, b"", "id", "IncompleteRead", receipt, {"953120", "959100"})
        self.assertIn("outbound fixture lacks its expected sensitive signatures", reasons)

    def test_allow_requires_exact_upstream_and_response(self):
        case = Case("ok", 0, "GET", TICKLERS + "?q=..%2F")
        receipt = [{"method": "GET", "target": case.target, "body": ""}]
        self.assertFalse(assess(case, "id", 200, response_body(case, "id"), "id", "", receipt, set()))
        self.assertTrue(assess(case, "id", 200, response_body(case, "id"), "id", "", [], set()))


def positive_int(value):
    number = int(value)
    if not 1 <= number <= 60:
        raise argparse.ArgumentTypeError("must be an integer from 1 through 60")
    return number


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--self-test", action="store_true", help="test harness mechanics without listeners or host dependencies")
    mode.add_argument("--check-config", action="store_true", help="run isolated nginx -t without listeners")
    mode.add_argument("--list-cases", action="store_true", help="list the selected cases without listeners")
    parser.add_argument("--repo", type=Path, default=ROOT)
    parser.add_argument("--nginx", default=shutil.which("nginx") or "/usr/sbin/nginx")
    parser.add_argument("--node", default=shutil.which("node") or "node")
    parser.add_argument("--module", type=Path, default=Path("/usr/lib/nginx/modules/ngx_http_modsecurity_module.so"))
    parser.add_argument("--crs-dir", type=Path, default=Path("/usr/share/modsecurity-crs"))
    parser.add_argument("--crs-setup", type=Path, default=Path("/etc/modsecurity/crs/crs-setup.conf"))
    parser.add_argument("--unicode-map", type=Path, default=Path("/etc/nginx/unicode.mapping"))
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--case", action="append", default=[], help="run names containing this text; repeat for OR (default: all)")
    parser.add_argument("--request-timeout", type=positive_int, default=3)
    parser.add_argument("--startup-timeout", type=positive_int, default=15)
    parser.add_argument("--evidence-timeout", type=positive_int, default=1)
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    if args.self_test:
        suite = unittest.defaultTestLoader.loadTestsFromTestCase(MechanicsTests)
        return 0 if unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() else 1
    for name in ("repo", "module", "crs_dir", "crs_setup", "unicode_map", "evidence_dir"):
        path = getattr(args, name)
        if path is not None:
            setattr(args, name, path.resolve())
    report = {"results": [], "scope": "nginx/ModSecurity/CRS policy against a fixture upstream; no real application", "filters": args.case}
    exit_code = 2
    try:
        if args.evidence_dir:
            args.evidence_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
            if any(args.evidence_dir.iterdir()):
                raise ValueError("--evidence-dir must be empty (existing evidence is never overwritten)")
        phrases, corpus = load_corpus(args.node, args.repo)
        cases = build_cases(phrases, corpus)
        if args.case:
            cases = [case for case in cases if any(fragment in case.name for fragment in args.case)]
        if not cases:
            raise ValueError("no cases selected")
        if args.list_cases:
            for case in cases:
                print(f"{case.name}\t#{case.issue}\t{case.expect}\t{case.method} {case.target.split('?')[0]}")
            print(f"{len(cases)} cases")
            return 0
        with tempfile.TemporaryDirectory(prefix="carlos-waf-policy-") as temporary:
            prefix = Path(temporary)
            try:
                report.update(prepare_prefix(prefix, args))
                # Operator-selected local nginx executable with a fixed flag; no shell or fixture HTTP input.
                version = subprocess.run([args.nginx, "-V"], capture_output=True, text=True, timeout=5, shell=False)  # nosemgrep: python.lang.security.audit.dangerous-subprocess-use-audit
                report["nginx_version"] = version.stdout + version.stderr
                report["module"] = {"path": str(args.module), "sha256": hashlib.sha256(args.module.read_bytes()).hexdigest()}
                report["corpus"] = phrases
                exit_code = run_engine(prefix, args, cases, report)
            except KeyboardInterrupt:
                report["error"] = "interrupted"
                exit_code = 130
            except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as exc:
                report["error"] = str(exc)
                print(f"ERROR: {exc}", file=sys.stderr)
                exit_code = 2
            finally:
                report["exit_code"] = exit_code
                (prefix / "report.json").write_text(json.dumps(report, indent=2) + "\n")
                if args.evidence_dir:
                    shutil.copytree(prefix, args.evidence_dir, dirs_exist_ok=True)
                    print(f"Evidence: {args.evidence_dir}", flush=True)
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return exit_code


if __name__ == "__main__":
    def interrupt(_signum, _frame):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, interrupt)
    sys.exit(main())
