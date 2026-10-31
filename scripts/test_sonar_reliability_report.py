"""Complete Sonar diagnostics must preserve security/reliability findings without leaking credentials."""
import base64
import contextlib
import io
import hashlib
import json
import os
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import Mock, patch

import sonar_reliability_report as sonar


class SonarReliabilityReportTest(unittest.TestCase):
    def test_preserves_bounded_flow_locations_without_source_values_or_messages(self):
        issue = {"key": "bug", "type": "BUG", "flows": [{"locations": [
            {"component": "project:caller.java", "textRange": {"startLine": 17},
             "msg": "synthetic-secret source value", "source": "never export"}]}]}
        selected, _ = sonar.collect_issues(lambda _: {"issues": [issue], "total": 1}, 5347)
        finding = sonar.report(selected, credentials=("synthetic-secret",))[0]
        self.assertEqual([{"flow": 0, "file": "project:caller.java", "line": 17}], finding["relatedLocations"])
        self.assertNotIn("synthetic-secret", json.dumps(finding))
        self.assertNotIn("never export", json.dumps(finding))

    def test_invalid_or_excessive_flow_locations_fail_closed(self):
        for locations in ([{"textRange": {"startLine": True}}],
                          [{"textRange": {"startLine": 1}}] * 129):
            issue = {"key": "bug", "type": "BUG", "flows": [{"locations": locations}]}
            with self.subTest(count=len(locations)), self.assertRaises(ValueError):
                sonar.collect_issues(lambda _: {"issues": [issue], "total": 1}, 5347)

    def test_reads_every_page_and_includes_classic_and_quality_impact_findings(self):
        calls = []

        def fetch(params):
            calls.append(params)
            if params["p"] == "1":
                return {"issues": [{"key": "bug", "type": "BUG"},
                                   {"key": "style", "type": "CODE_SMELL"}], "paging": {"total": 5}}
            return {"issues": [{"key": "impact", "type": "CODE_SMELL",
                                "impacts": [{"softwareQuality": "RELIABILITY"}]},
                               {"key": "vulnerability", "type": "VULNERABILITY"},
                               {"key": "security", "type": "CODE_SMELL",
                                "impacts": [{"softwareQuality": "SECURITY"}]}], "paging": {"total": 5}}

        self.assertEqual(["bug", "impact", "vulnerability", "security"],
                         [x["key"] for x in sonar.reliability_issues(fetch, 4902)])
        self.assertEqual(["1", "2"], [call["p"] for call in calls])
        self.assertTrue(all(call["pullRequest"] == "4902" for call in calls))

    def test_incomplete_page_cannot_report_an_empty_success(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            sonar.reliability_issues(lambda _: {"issues": [], "total": 1}, 4902)

    def test_missing_count_cannot_silently_drop_later_findings(self):
        with self.assertRaisesRegex(ValueError, "Invalid"):
            sonar.reliability_issues(lambda _: {"issues": []}, 4902)

    def test_repeated_page_is_not_mistaken_for_complete_results(self):
        with self.assertRaisesRegex(ValueError, "changed during pagination"):
            sonar.reliability_issues(lambda _: {"issues": [{"key": "same", "type": "BUG"}],
                                               "total": 2}, 4902)

    def test_empty_analysis_is_reported(self):
        self.assertEqual([], sonar.reliability_issues(lambda _: {"issues": [], "total": 0}, 4902))

    def test_fixed_https_peer_rejects_redirects_without_forwarding_credentials(self):
        response = Mock(status=302)
        connection = Mock()
        connection.getresponse.return_value = response
        with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "4902"}), \
                patch.object(sonar, "HTTPSConnection", return_value=connection) as peer:
            with self.assertRaises(sonar.HTTPError):
                sonar.main()
        peer.assert_called_once()
        self.assertEqual(("sonarcloud.io",), peer.call_args.args)
        self.assertEqual(30, peer.call_args.kwargs["timeout"])
        tls = peer.call_args.kwargs["context"]
        self.assertEqual(sonar.ssl.CERT_REQUIRED, tls.verify_mode)
        self.assertTrue(tls.check_hostname)
        connection.request.assert_called_once()
        self.assertEqual("GET", connection.request.call_args.args[0])
        self.assertTrue(connection.request.call_args.args[1].startswith("/api/issues/search?"))
        connection.close.assert_called_once()

    def test_command_boundary_fails_nonzero_with_a_safe_explicit_ci_error(self):
        response = Mock(status=401)
        response.read.return_value = b"synthetic-secret response must never be printed"
        connection = Mock()
        connection.getresponse.return_value = response
        output, errors = io.StringIO(), io.StringIO()
        with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "5347", "GITHUB_ACTIONS": "true"}), \
                patch("http.client.HTTPSConnection", return_value=connection), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            with self.assertRaises(SystemExit) as failure:
                runpy.run_path(sonar.__file__, run_name="__main__")
        self.assertEqual(1, failure.exception.code)
        self.assertIn("::error title=Sonar diagnostic report incomplete::", output.getvalue())
        self.assertIn("HTTP 401", errors.getvalue())
        self.assertNotIn("synthetic-secret", output.getvalue() + errors.getvalue())
        response.read.assert_not_called()

    def test_summary_and_notices_cannot_execute_source_markup_or_workflow_commands(self):
        payload = "</pre><script>unsafe</script>\n::error::forged %0A"
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            artifact = Path(directory) / "findings.json"
            body = io.StringIO('{"issues": ' + json.dumps([
                {"key": "bug", "type": "BUG", "message": payload}]) + ', "total": 1}')
            body.status = 200
            connection = Mock()
            connection.getresponse.return_value = body
            output = io.StringIO()
            with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "4902",
                                         "SONAR_REPORT_PATH": str(artifact),
                                         "GITHUB_STEP_SUMMARY": str(summary), "GITHUB_ACTIONS": "true"}), \
                    patch.object(sonar, "HTTPSConnection", return_value=connection), contextlib.redirect_stdout(output):
                sonar.main()
            self.assertNotIn("\n::error::forged", output.getvalue())
            self.assertNotIn("synthetic-secret", output.getvalue())
            self.assertIn("::notice title=Sonar security/reliability finding 1 of 1::", output.getvalue())
            self.assertIn("%250A", output.getvalue())
            self.assertNotIn("<script>", summary.read_text())
            self.assertNotIn(payload, summary.read_text())
            self.assertEqual(payload, json.loads(artifact.read_text())["findings"][0]["message"])

    def test_changed_totals_and_overshoot_cannot_publish_a_complete_report(self):
        for second in ({"issues": [{"key": "two"}], "total": 3},
                       {"issues": [{"key": "two"}, {"key": "three"}], "total": 2}):
            pages = iter([{"issues": [{"key": "one"}], "total": 2}, second])
            with self.assertRaises(ValueError):
                sonar.collect_issues(lambda _: next(pages), 5347)

    def test_counts_page_numbers_and_capacities_are_strict(self):
        for page in ({"issues": [], "total": True}, {"issues": [], "total": -1},
                     {"issues": [], "total": sonar.MAX_ISSUES + 1},
                     {"issues": [], "paging": {"total": 0, "pageIndex": 2}},
                     {"issues": [{"key": str(i)} for i in range(501)], "total": 501}):
            with self.subTest(page=str(page)[:80]), self.assertRaises(ValueError):
                sonar.collect_issues(lambda _: page, 5347)

    def test_duplicate_json_keys_and_oversized_http_responses_are_refused(self):
        for payload in ('{"issues":[],"total":0,"total":1}', "x" * (sonar.MAX_PAGE_BYTES + 1)):
            body = io.StringIO(payload)
            body.status = 200
            connection = Mock()
            connection.getresponse.return_value = body
            with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "5347"}), \
                    patch.object(sonar, "HTTPSConnection", return_value=connection), self.assertRaises(ValueError):
                sonar.main()
            connection.close.assert_called_once()

    def test_individual_notices_are_bounded_and_full_artifact_is_complete_and_hashed(self):
        token = "synthetic-secret"
        auth = base64.b64encode((token + ":").encode()).decode()
        source = [{"key": f"issue-{i}", "type": "BUG", "component": "project:path.py", "line": i + 1,
                   "rule": "rule:one", "message": f"{token} {auth} " + "é%0A\n::error::forged" * 300,
                   "authorization": token, "flows": [{"token": token}]} for i in range(25)]
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "report.json"
            summary = Path(directory) / "summary.md"
            pages = [io.StringIO(json.dumps({"issues": source[:10], "total": 25})),
                     io.StringIO(json.dumps({"issues": source[10:], "total": 25}))]
            for page in pages:
                page.status = 200
            connection = Mock()
            connection.getresponse.side_effect = pages
            output = io.StringIO()
            with patch.dict(os.environ, {"SONAR_TOKEN": token, "PR_NUMBER": "5347", "GITHUB_ACTIONS": "true",
                                         "REPORT_COMMIT": "a" * 40, "SONAR_REPORT_PATH": str(artifact),
                                         "GITHUB_STEP_SUMMARY": str(summary)}), \
                    patch.object(sonar, "HTTPSConnection", return_value=connection), contextlib.redirect_stdout(output):
                sonar.main()
            notices = [line for line in output.getvalue().splitlines() if line.startswith("::notice ")]
            self.assertEqual(25, len(notices))
            self.assertTrue(all(len(line.encode()) <= sonar.MAX_NOTICE_BYTES for line in notices))
            self.assertTrue(all("preview truncated" in line for line in notices))
            self.assertNotIn("\n::error::", output.getvalue())
            document = json.loads(artifact.read_text())
            self.assertTrue(document["complete"])
            self.assertTrue(document["analysisMayPrecedeCurrentCommit"])
            self.assertEqual("a" * 40, document["currentCommit"])
            self.assertEqual(25, document["scannedIssueCount"])
            self.assertEqual(25, document["findingCount"])
            self.assertEqual(25, len(document["findings"]))
            self.assertEqual(hashlib.sha256(sonar.canonical_findings(document["findings"])).hexdigest(), document["findingsSha256"])
            for unsafe in (token, auth, '"authorization"', '"flows"'):
                self.assertNotIn(unsafe, artifact.read_text())
                self.assertNotIn(unsafe, output.getvalue())
                self.assertNotIn(unsafe, summary.read_text())
            self.assertEqual(0o600, artifact.stat().st_mode & 0o777)
            self.assertIn("[REDACTED]", document["findings"][0]["message"])
            self.assertTrue(document["findings"][-1]["message"].endswith("é%0A\n::error::forged"))

    def test_incomplete_pagination_never_publishes_an_artifact_or_success_notice(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "report.json"
            body = io.StringIO('{"issues":[],"total":1}')
            body.status = 200
            connection = Mock()
            connection.getresponse.return_value = body
            output = io.StringIO()
            with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "5347",
                                         "GITHUB_ACTIONS": "true", "SONAR_REPORT_PATH": str(artifact)}), \
                    patch.object(sonar, "HTTPSConnection", return_value=connection), contextlib.redirect_stdout(output):
                with self.assertRaises(ValueError):
                    sonar.main()
            self.assertFalse(artifact.exists())
            self.assertEqual("", output.getvalue())

    def test_failed_atomic_publication_removes_partial_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "report.json"
            with patch.object(sonar.os, "replace", side_effect=OSError("synthetic write failure")), self.assertRaises(OSError):
                sonar.write_report(artifact, {"findings": []})
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_workflow_keeps_prefetch_independent_and_requires_post_analysis_artifacts(self):
        workflow = (Path(__file__).resolve().parents[1] / ".github/workflows/sonarcloud.yml").read_text()
        self.assertIn("${{ runner.temp }}/sonar-quality-latest.json", workflow)
        self.assertIn("${{ runner.temp }}/sonar-quality-after-analysis.json", workflow)
        self.assertIn("name: sonar-pr-quality-findings", workflow)
        self.assertIn("if-no-files-found: error", workflow)
        self.assertEqual(1, workflow.count("continue-on-error: true"))
        post_analysis = workflow.split("- name: Report PR security and reliability after server processing", 1)[1]
        self.assertNotIn("continue-on-error:", post_analysis)
        self.assertNotIn("if ! python3 scripts/sonar_reliability_report.py", workflow)


if __name__ == "__main__":
    unittest.main()
