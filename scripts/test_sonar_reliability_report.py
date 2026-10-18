"""CI diagnostics must retain all reliability findings and escape source-derived text."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import sonar_reliability_report as sonar


class SonarReliabilityReportTest(unittest.TestCase):
    def test_reads_every_page_and_includes_classic_and_quality_impact_findings(self):
        calls = []

        def fetch(params):
            calls.append(params)
            if params["p"] == "1":
                return {"issues": [{"key": "bug", "type": "BUG"},
                                   {"key": "style", "type": "CODE_SMELL"}], "paging": {"total": 3}}
            return {"issues": [{"key": "impact", "type": "CODE_SMELL",
                                "impacts": [{"softwareQuality": "RELIABILITY"}]}], "paging": {"total": 3}}

        self.assertEqual(["bug", "impact"], [x["key"] for x in sonar.reliability_issues(fetch, 4902)])
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

    def test_summary_escapes_markup_and_logs_escape_workflow_commands(self):
        payload = "</pre><script>unsafe</script>\n::error::forged %0A"
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary.md"
            body = io.StringIO('{"issues": ' + json.dumps([
                {"key": "bug", "type": "BUG", "message": payload}]) + ', "total": 1}')
            body.status = 200
            connection = Mock()
            connection.getresponse.return_value = body
            output = io.StringIO()
            with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-secret", "PR_NUMBER": "4902",
                                         "GITHUB_STEP_SUMMARY": str(summary), "GITHUB_ACTIONS": "true"}), \
                    patch.object(sonar, "HTTPSConnection", return_value=connection), contextlib.redirect_stdout(output):
                sonar.main()
            self.assertNotIn("\n::error::forged", output.getvalue())
            self.assertNotIn("synthetic-secret", output.getvalue())
            self.assertIn("::notice title=Latest available Sonar reliability findings::", output.getvalue())
            self.assertIn("%250A", output.getvalue())
            self.assertNotIn("<script>", summary.read_text())
            self.assertIn("&lt;script&gt;", summary.read_text())


if __name__ == "__main__":
    unittest.main()
