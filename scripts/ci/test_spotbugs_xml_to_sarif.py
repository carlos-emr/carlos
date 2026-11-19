#!/usr/bin/env python3
"""Check the standalone converter's upload contract and untrusted XML handling."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name('spotbugs-xml-to-sarif.py').resolve()


class SpotBugsXmlToSarifTest(unittest.TestCase):
    def convert(self, xml):
        """Run the real CLI in a clean directory and collect both output artifacts."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / 'spotbugs.xml'
            report.write_text(xml, encoding='utf-8')
            result = subprocess.run(
                [sys.executable, str(SCRIPT)], cwd=root,
                env={**os.environ, 'REPORT': str(report)},
                capture_output=True, text=True, timeout=10,
            )
            sarif_file = root / 'spotbugs-security-report.sarif'
            metrics_file = root / 'spotbugs-security-metrics.env'
            sarif = json.loads(sarif_file.read_text()) if sarif_file.exists() else None
            metrics = dict(
                line.split('=', 1) for line in metrics_file.read_text().splitlines()
            ) if metrics_file.exists() else None
            return result, sarif, metrics

    def test_only_locatable_security_findings_are_uploaded_and_counted(self):
        result, sarif, metrics = self.convert('''<BugCollection>
          <BugInstance type="SQL_INJECTION" category=" security " priority="1">
            <LongMessage>Unsafe SQL &amp; input</LongMessage>
            <SourceLine sourcepath="example/Queries.java" start="12" end="14"/>
          </BugInstance>
          <BugInstance type="STYLE" category="STYLE" priority="2"/>
          <BugInstance type="NO_SOURCE" category="SECURITY" priority="2"/>
        </BugCollection>''')
        self.assertEqual(result.returncode, 0, result.stderr)
        run = sarif['runs'][0]
        self.assertEqual([finding['ruleId'] for finding in run['results']], ['SQL_INJECTION'])
        self.assertEqual(run['results'][0]['message']['text'], 'Unsafe SQL & input')
        location = run['results'][0]['locations'][0]['physicalLocation']
        self.assertEqual(location['artifactLocation'], {
            'uri': 'src/main/java/example/Queries.java', 'uriBaseId': '%SRCROOT%',
        })
        self.assertEqual(location['region'], {'startLine': 12, 'endLine': 14})
        self.assertEqual(metrics, {
            'TOTAL_FINDINGS': '3', 'SECURITY_FINDINGS_UPLOADED': '1',
            'SKIPPED_NON_SECURITY': '1', 'SKIPPED_NO_SOURCE': '1',
        })
        self.assertIn('Skipping SECURITY finding without source location: NO_SOURCE', result.stderr)

    def test_direct_and_nested_sources_keep_priority_and_highest_rule_severity(self):
        result, sarif, _ = self.convert('''<BugCollection>
          <BugInstance type="SHARED_RULE" category="SECURITY" priority="3">
            <ShortMessage>Low priority</ShortMessage>
            <Class><SourceLine sourcepath="example/Low.java" start="3" end="3"/></Class>
          </BugInstance>
          <BugInstance type="SHARED_RULE" category="SECURITY" priority="1">
            <SourceLine sourcepath="example/High.java" start="7" end="8"/>
          </BugInstance>
          <BugInstance type="OTHER_RULE" category="SECURITY" priority="2">
            <Method><SourceLine sourcepath="example/Medium.java" start="9" end="9"/></Method>
          </BugInstance>
        </BugCollection>''')
        self.assertEqual(result.returncode, 0, result.stderr)
        run = sarif['runs'][0]
        self.assertEqual([finding['level'] for finding in run['results']], ['note', 'error', 'warning'])
        self.assertEqual([finding['message']['text'] for finding in run['results']],
                         ['Low priority', 'SHARED_RULE', 'OTHER_RULE'])
        self.assertEqual([
            finding['locations'][0]['physicalLocation']['artifactLocation']['uri']
            for finding in run['results']
        ], ['src/main/java/example/Low.java', 'src/main/java/example/High.java',
            'src/main/java/example/Medium.java'])
        rules = {rule['id']: rule for rule in run['tool']['driver']['rules']}
        self.assertEqual(len(rules), 2)
        self.assertEqual(rules['SHARED_RULE']['properties']['security-severity'], '8.0')
        self.assertEqual(rules['OTHER_RULE']['properties']['security-severity'], '5.0')
        self.assertEqual(rules['SHARED_RULE']['properties']['tags'], ['security'])

    def test_no_findings_produces_an_empty_sarif_run_and_zero_metrics(self):
        result, sarif, metrics = self.convert('<BugCollection/>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(sarif['version'], '2.1.0')
        self.assertEqual(len(sarif['runs']), 1)
        self.assertEqual(sarif['runs'][0]['results'], [])
        self.assertEqual(sarif['runs'][0]['tool']['driver']['rules'], [])
        self.assertTrue(metrics)
        self.assertEqual(set(metrics.values()), {'0'})

    def test_malformed_xml_fails_without_upload_artifacts(self):
        result, sarif, metrics = self.convert('<BugCollection><BugInstance></BugCollection>')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Failed to parse SpotBugs XML', result.stderr)
        self.assertIsNone(sarif)
        self.assertIsNone(metrics)

    def test_entity_expansion_fails_without_upload_artifacts(self):
        result, sarif, metrics = self.convert('''<!DOCTYPE BugCollection [
          <!ENTITY small "expanded">
          <!ENTITY large "&small;&small;&small;&small;">
        ]><BugCollection>&large;</BugCollection>''')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('EntitiesForbidden', result.stderr)
        self.assertIsNone(sarif)
        self.assertIsNone(metrics)

    def test_external_entity_fails_without_reading_local_file(self):
        with tempfile.TemporaryDirectory() as directory:
            private_file = Path(directory) / 'sentinel.txt'
            sentinel = 'synthetic-local-file-content-must-not-be-exposed'
            private_file.write_text(sentinel)
            result, sarif, metrics = self.convert(f'''<!DOCTYPE BugCollection [
              <!ENTITY external SYSTEM "{private_file.as_uri()}">
            ]><BugCollection>&external;</BugCollection>''')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('EntitiesForbidden', result.stderr)
        self.assertNotIn(sentinel, result.stdout + result.stderr)
        self.assertIsNone(sarif)
        self.assertIsNone(metrics)


if __name__ == '__main__':
    unittest.main()
