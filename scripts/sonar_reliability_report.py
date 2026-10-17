#!/usr/bin/env python3
"""Report the latest available Sonar PR reliability findings, without changing the gate."""

import base64
import html
from http.client import HTTPSConnection
import json
import os
import ssl
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode

PROJECT = "carlos-emr_carlos"
API = "https://sonarcloud.io/api/issues/search"


def reliability_issues(fetch_page, pull_request):
    """Read every page; support both classic bugs and software-quality impacts."""
    issues = []
    seen = set()
    page = 1
    while True:
        data = fetch_page({"componentKeys": PROJECT, "pullRequest": str(pull_request),
                           "resolved": "false", "ps": "500", "p": str(page)})
        batch = data["issues"]
        total = data.get("paging", {}).get("total", data.get("total"))
        if not isinstance(batch, list) or not isinstance(total, int) or total < 0:
            raise ValueError("Invalid Sonar issue page")
        for issue in batch:
            key = issue["key"]
            if key in seen:
                raise ValueError("Sonar issues changed during pagination")
            seen.add(key)
            issues.append(issue)
        if len(issues) >= total:
            break
        if not batch or page >= 100:
            raise ValueError("Incomplete Sonar issue pagination")
        page += 1
    return [issue for issue in issues if issue.get("type") == "BUG"
            or any(impact.get("softwareQuality") == "RELIABILITY"
                   for impact in issue.get("impacts", []))]


def report(issues):
    return [{"key": issue.get("key"), "rule": issue.get("rule"),
             "file": issue.get("component"), "line": issue.get("line"),
             "severity": issue.get("severity"), "impacts": issue.get("impacts", []),
             "message": issue.get("message")} for issue in issues]


def main():
    token = os.environ.get("SONAR_TOKEN", "")
    pull_request = os.environ.get("PR_NUMBER", "")
    if not token or not pull_request.isascii() or not pull_request.isdecimal():
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
            return json.load(response)
        finally:
            connection.close()

    findings = report(reliability_issues(fetch_page, pull_request))
    text = json.dumps(findings, indent=2, ensure_ascii=True)
    # JSON escapes source-derived control characters instead of letting them become CI commands.
    print("Latest available Sonar PR reliability findings (not a quality-gate result):")
    print(text)
    if os.environ.get("GITHUB_ACTIONS") == "true":
        # A notice is readable before the long analysis job completes. Keep the complete
        # reliability list in one annotation rather than mixing it with 50 style findings.
        compact = json.dumps(findings, ensure_ascii=True).replace("%", "%25")
        print("::notice title=Latest available Sonar reliability findings::" + compact)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as output:
            output.write("## Sonar reliability diagnostics\n\n"
                         "Latest available PR analysis; it can precede this commit. "
                         "Use the SonarCloud Code Analysis check for the current gate.\n\n"
                         "<pre>" + html.escape(text) + "</pre>\n")


if __name__ == "__main__":
    try:
        main()
    except HTTPError as error:
        # Never print response bodies or request headers containing authentication material.
        print(f"Sonar reliability report failed: HTTP {error.code}", file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        print(f"Sonar reliability report failed: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
