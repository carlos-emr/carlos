# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Props-phase contracts against the committed clinic-example fixture:
baseline-diff, every disposition family, docpath translation, deploy-owned
refusal, secret masking, unknown reporting.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import unittest

from carlos_ctl import o19map_props, o19props

FIXTURE = os.path.join(
    os.path.dirname(__file__), "..", "..", "..", "..", "scripts",
    "migration", "o19", "fixtures", "properties",
    "oscar-clinic-example.properties")

ROOT = "/var/lib/carlos-emr/OscarDocument"


def fixture_result():
    clinic = o19props.load_clinic_properties(os.path.abspath(FIXTURE))
    return o19props.translate_all(clinic, documents_root=ROOT,
                                  deployment_drugref="http://127.0.0.1:8080/drugref")


class TestBaselineDiff(unittest.TestCase):

    def test_untouched_defaults_are_ignored(self):
        result = fixture_result()
        touched = {k for k, _, _ in result["rows"]}
        # billregion=ON and HL7TEXT_LABS=no equal the stock defaults
        self.assertEqual(o19map_props.O19_DEFAULTS["billregion"], "ON")
        self.assertNotIn("billregion", touched)
        self.assertNotIn("HL7TEXT_LABS", touched)

    def test_project_home_default_with_spaces_is_ignored(self):
        result = fixture_result()
        self.assertNotIn("project_home",
                         {k for k, _, _ in result["rows"]})


class TestDispositions(unittest.TestCase):

    def setUp(self):
        self.result = fixture_result()
        self.fragment = dict(self.result["fragment"])
        self.by_key = {k: d for k, d, _ in self.result["rows"]}

    def test_clinic_identity_carries(self):
        for key in ("clinic_no", "phoneprefix", "Support_Contact",
                    "DX_QUICK_LIST_DEFAULT", "lab.handler.CML.enabled",
                    "label.1no", "password_min_length", "tickler_warn_period"):
            self.assertEqual(self.by_key.get(key), "carry", key)
            self.assertIn(key, self.fragment)

    def test_credentials_carry_as_secret(self):
        for key in ("mcedt.service.pass", "email.password",
                    "hcv.service.pass"):
            self.assertEqual(self.by_key.get(key), "carry-secret", key)
            self.assertIn(key, self.fragment)
            self.assertIn(key, self.result["secrets"])

    def test_docpaths_translate_onto_the_carlos_tree(self):
        self.assertEqual(
            self.fragment["ONEDT_INBOX"],
            ROOT + "/carlos/onEDTDocs/inbox/")
        self.assertEqual(
            self.fragment["INVOICE_DIR"],
            ROOT + "/carlos/billing/download/")
        self.assertEqual(
            self.fragment["eform_image"],
            ROOT + "/carlos/eform/images/")

    def test_drugref_keeps_the_deployment_endpoint(self):
        self.assertEqual(self.by_key.get("drugref_url"), "translate")
        self.assertNotIn("drugref_url", self.fragment)

    def test_deploy_owned_keys_are_refused(self):
        for key in ("db_uri", "db_password", "tomcat_path",
                    "BASE_DOCUMENT_DIR"):
            self.assertEqual(self.by_key.get(key), "deploy-owned", key)
            self.assertNotIn(key, self.fragment)

    def test_removed_modules_group_into_advisories(self):
        adv = self.result["advisories"]
        self.assertIn("born_sftp_password",
                      adv.get("removed-modules", []))
        self.assertIn("olis_request_url", adv.get("olis", []))
        self.assertIn("util.erx.enabled", adv.get("erx", []))
        self.assertIn("faxURI", adv.get("fax", []))
        self.assertIn("ldap.enabled", adv.get("ldap", []))
        for keys in adv.values():
            for key in keys:
                self.assertNotIn(key, self.fragment)

    def test_vendor_fork_key_is_unknown_not_silent(self):
        self.assertIn("acme_ehr_bridge.endpoint", self.result["unknown"])
        self.assertNotIn("acme_ehr_bridge.endpoint", self.fragment)


class TestRendering(unittest.TestCase):

    def test_fragment_is_reviewable_properties_text(self):
        result = fixture_result()
        text = o19props.render_fragment(result)
        self.assertIn("REVIEW before applying", text)
        self.assertIn("clinic_no=9999", text)
        self.assertIn("mcedt.service.pass=fake-mcedt-secret", text)

    def test_report_masks_secret_values(self):
        result = fixture_result()
        report = o19props.render_report(result)
        self.assertNotIn("fake-mcedt-secret", report)
        self.assertNotIn("fake-mail-secret", report)
        self.assertIn("ROTATE/VERIFY", report)
        self.assertIn("acme_ehr_bridge.endpoint", report)


class TestTranslateDocpath(unittest.TestCase):

    def test_handles_both_o19_layout_roots(self):
        for value in (
                "/var/lib/OscarDocument/oscar_mcmaster/billing/download/",
                "/usr/local/tomcat/webapps/OscarDocument/oscar_mcmaster/"
                "billing/download/"):
            self.assertEqual(
                o19props.translate_docpath(value, ROOT),
                ROOT + "/carlos/billing/download/")

    def test_non_document_path_returns_none(self):
        self.assertIsNone(
            o19props.translate_docpath("/opt/somewhere/else", ROOT))


if __name__ == "__main__":
    unittest.main()
