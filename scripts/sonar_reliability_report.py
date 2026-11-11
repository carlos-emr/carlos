#!/usr/bin/env python3
"""Persist complete bounded Sonar PR security/reliability diagnostics; never change the gate."""

import base64
from datetime import datetime, timezone
import hashlib
from http.client import HTTPSConnection
import json
import os
import re
import ssl
from pathlib import Path
import sys
import tempfile
from urllib.error import HTTPError
from urllib.parse import urlencode

PROJECT = "carlos-emr_carlos"
API = "https://sonarcloud.io/api/issues/search"
MAX_ISSUES = 10000
MAX_PAGE_BYTES = 8 * 1024 * 1024
MAX_REPORT_BYTES = 32 * 1024 * 1024
MAX_NOTICE_BYTES = 3000
MAX_RELATED_LOCATIONS = 128


def related_locations(issue):
    """Retain only bounded source coordinates, never flow messages or source values."""
    flows = issue.get("flows", [])
    if not isinstance(flows, list) or len(flows) > 32:
        raise ValueError("Invalid Sonar issue flows")
    result = []
    for index, flow in enumerate(flows):
        if not isinstance(flow, dict):
            raise ValueError("Invalid Sonar issue flow")
        locations = flow.get("locations", [])
        if not isinstance(locations, list) or len(result) + len(locations) > MAX_RELATED_LOCATIONS:
            raise ValueError("Sonar related location capacity exceeded")
        for location in locations:
            if not isinstance(location, dict) or not isinstance(location.get("textRange", {}), dict):
                raise ValueError("Invalid Sonar related location")
            component = location.get("component")
            line = location.get("textRange", {}).get("startLine")
            if component is not None and (not isinstance(component, str) or len(component) > 65536):
                raise ValueError("Invalid Sonar related component")
            if line is not None and (type(line) is not int or line < 1 or line > 2147483647):
                raise ValueError("Invalid Sonar related line")
            result.append({"flow": index, "component": component, "line": line})
    return result


def collect_issues(fetch_page, pull_request):
    """Require an exact, stable, complete workset before publishing any findings."""
    issues = []
    seen = set()
    page = 1
    expected_total = None
    report_bytes = 0
    while True:
        data = fetch_page({"componentKeys": PROJECT, "pullRequest": str(pull_request),
                           "resolved": "false", "ps": "500", "p": str(page)})
        if not isinstance(data, dict) or not isinstance(data.get("paging", {}), dict):
            raise ValueError("Invalid Sonar issue page")
        batch = data.get("issues")
        paging = data.get("paging", {})
        total = paging.get("total", data.get("total"))
        if (not isinstance(batch, list) or len(batch) > 500 or type(total) is not int
                or total < 0 or total > MAX_ISSUES):
            raise ValueError("Invalid Sonar issue page")
        if expected_total is None:
            expected_total = total
        if total != expected_total or paging.get("pageIndex", page) != page:
            raise ValueError("Sonar issues changed during pagination")
        for issue in batch:
            if not isinstance(issue, dict):
                raise ValueError("Invalid Sonar issue")
            key = issue.get("key")
            if not isinstance(key, str) or not key or len(key) > 256:
                raise ValueError("Invalid Sonar issue key")
            if key in seen:
                raise ValueError("Sonar issues changed during pagination")
            seen.add(key)
            impacts = issue.get("impacts", [])
            if not isinstance(impacts, list) or len(impacts) > 16 or any(not isinstance(x, dict) for x in impacts):
                raise ValueError("Invalid Sonar issue impacts")
            if issue.get("type") in {"BUG", "VULNERABILITY"} or any(
                    impact.get("softwareQuality") in {"RELIABILITY", "SECURITY"} for impact in impacts):
                # Drop arbitrary metadata, flow messages/source values and authentication fields.
                selected = {field: issue.get(field) for field in (
                    "key", "rule", "component", "line", "type", "severity", "message")}
                selected["impacts"] = [{"softwareQuality": x.get("softwareQuality"),
                                        "severity": x.get("severity")} for x in impacts]
                locations = related_locations(issue)
                if locations:
                    selected["relatedLocations"] = locations
                report_bytes += len(json.dumps(selected, ensure_ascii=True).encode("ascii"))
                if report_bytes > MAX_REPORT_BYTES:
                    raise ValueError("Sonar report capacity exceeded")
                issues.append(selected)
        if len(seen) > expected_total:
            raise ValueError("Sonar issue count exceeded the declared total")
        if len(seen) == expected_total:
            return issues, expected_total
        if not batch or page >= 20:
            raise ValueError("Incomplete Sonar issue pagination")
        page += 1


def reliability_issues(fetch_page, pull_request):
    """Compatibility entry point; includes classic and impact-based security findings too."""
    return collect_issues(fetch_page, pull_request)[0]


def report(issues, credentials=()):
    def text(value):
        if value is not None and (not isinstance(value, str) or len(value) > 65536):
            raise ValueError("Invalid Sonar finding text")
        if value is not None:
            for credential in credentials:
                if credential:
                    value = value.replace(credential, "[REDACTED]")
        return value

    findings = []
    for issue in issues:
        line = issue.get("line")
        if line is not None and (type(line) is not int or line < 1 or line > 2147483647):
            raise ValueError("Invalid Sonar finding line")
        findings.append({"key": text(issue.get("key")), "rule": text(issue.get("rule")),
                         "file": text(issue.get("component")), "line": line, "type": text(issue.get("type")),
                         "severity": text(issue.get("severity")),
                         "impacts": [{"softwareQuality": text(x.get("softwareQuality")),
                                      "severity": text(x.get("severity"))} for x in issue.get("impacts", [])],
                         "message": text(issue.get("message"))})
        if issue.get("relatedLocations"):
            findings[-1]["relatedLocations"] = [
                {"flow": location["flow"], "file": text(location["component"]), "line": location["line"]}
                for location in issue["relatedLocations"]]
    return findings


def canonical_findings(findings):
    return json.dumps(findings, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")


def write_report(path, document):
    payload = json.dumps(document, indent=2, ensure_ascii=True).encode("ascii") + b"\n"
    if len(payload) > 2 * MAX_REPORT_BYTES:
        raise ValueError("Sonar artifact capacity exceeded")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".sonar-report-", delete=False) as output:
            temporary = Path(output.name)
            os.chmod(temporary, 0o600)
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def notice(finding, index, count):
    preview = json.dumps(finding, ensure_ascii=True, separators=(",", ":")).replace("%", "%25")
    prefix = f"::notice title=Sonar security/reliability finding {index} of {count}::"
    allowance = MAX_NOTICE_BYTES - len(prefix) - 100
    if len(preview) > allowance:
        preview = preview[:allowance]
        if "%" in preview[-2:]:
            preview = preview[:preview.rfind("%")]
        preview += "... [preview truncated; complete finding in Sonar JSON artifact]"
    return prefix + preview


def strict_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("Duplicate Sonar JSON key")
        value[key] = item
    return value


def report_failure(category):
    # Callers supply only a status number or exception class, never an exception message or response body.
    message = "Sonar security/reliability report failed: " + category
    print(message, file=sys.stderr)
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print("::error title=Sonar diagnostic report incomplete::" + message)


def main():
    token = os.environ.get("SONAR_TOKEN", "")
    pull_request = os.environ.get("PR_NUMBER", "")
    if not token or not pull_request.isascii() or not pull_request.isdecimal() or len(pull_request) > 10:
        raise ValueError("SONAR_TOKEN and numeric PR_NUMBER are required")
    auth = base64.b64encode((token + ":").encode()).decode()

    def fetch_page(params):
        # A fixed HTTPS peer and path prevent file-scheme access and cross-host
        # redirects from forwarding the Sonar token. Only query values are dynamic.
        # The CI runtime is modern Python. Explicit default context requires both
        # certificate-chain and hostname verification; the fixed peer cannot redirect.
        # nosemgrep: python.lang.security.audit.httpsconnection-detected.httpsconnection-detected
        connection = HTTPSConnection("sonarcloud.io", timeout=30, context=ssl.create_default_context())
        try:
            connection.request("GET", "/api/issues/search?" + urlencode(params), headers={
                "Authorization": "Basic " + auth, "Accept": "application/json"})
            response = connection.getresponse()
            if response.status != 200:
                raise HTTPError(API, response.status, "Sonar issue request failed", {}, None)
            payload = response.read(MAX_PAGE_BYTES + 1)
            if len(payload) > MAX_PAGE_BYTES:
                raise ValueError("Sonar response capacity exceeded")
            return json.loads(payload, object_pairs_hook=strict_object)
        finally:
            connection.close()

    issues, scanned = collect_issues(fetch_page, pull_request)
    findings = report(issues, credentials=(token, auth))
    commit = os.environ.get("REPORT_COMMIT", os.environ.get("GITHUB_SHA", ""))
    if not re.fullmatch(r"[0-9a-fA-F]{40}(?:[0-9a-fA-F]{24})?", commit):
        commit = "UNKNOWN"
    digest = hashlib.sha256(canonical_findings(findings)).hexdigest()
    document = {"formatVersion": 1, "project": PROJECT, "pullRequest": int(pull_request),
                "currentCommit": commit, "analysisMayPrecedeCurrentCommit": True,
                "analysisLabel": "Latest available PR analysis; not the current quality-gate result",
                "generatedAt": datetime.now(timezone.utc).isoformat(), "complete": True,
                "scannedIssueCount": scanned, "findingCount": len(findings),
                "findingsSha256": digest, "findings": findings}
    location = os.environ.get("SONAR_REPORT_PATH")
    if not location:
        raise ValueError("SONAR_REPORT_PATH is required")
    write_report(Path(location), document)
    print(f"Latest available Sonar PR security/reliability findings: {len(findings)} of {scanned} unresolved issues.")
    print(f"Complete JSON findings SHA256: {digest}; analysis may precede commit {commit}.")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        for index, finding in enumerate(findings, 1):
            print(notice(finding, index, len(findings)))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as output:
            output.write("## Sonar security and reliability diagnostics\n\n"
                         "Latest available PR analysis; it can precede this commit. "
                         "The SonarCloud Code Analysis check remains the quality-gate result.\n\n"
                         f"Current commit: `{commit}`. Scanned unresolved issues: {scanned}. "
                         f"Security/reliability findings: {len(findings)}.\n\n"
                         "Complete sanitized JSON is in the `sonar-pr-quality-findings` artifact. "
                         f"Canonical findings SHA256: `{digest}`.\n")


if __name__ == "__main__":
    try:
        main()
    except HTTPError as error:
        # Never print response bodies or request headers containing authentication material.
        report_failure(f"HTTP {error.code}")
        sys.exit(1)
    except Exception as error:
        report_failure(type(error).__name__)
        sys.exit(1)
