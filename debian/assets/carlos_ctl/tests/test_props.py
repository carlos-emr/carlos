# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Props-phase contracts against the committed clinic-example fixture:
baseline-diff, every disposition family, docpath translation, deploy-owned
refusal, secret masking, unknown reporting.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
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
        # CARLOS reads the eForm image directory as EFORM_IMAGES_DIR: the
        # fragment carries the key CARLOS honours, never the O19 spelling
        self.assertEqual(
            self.fragment["EFORM_IMAGES_DIR"],
            ROOT + "/carlos/eform/images/")
        self.assertNotIn("eform_image", self.fragment)
        self.assertEqual(self.by_key.get("eform_image"), "translate")

    def test_settings_carlos_still_reads_carry_verbatim(self):
        for key in ("login_local_ip", "resource_base_url"):
            self.assertEqual(self.by_key.get(key), "carry", key)
            self.assertIn(key, self.fragment)

    def test_resource_url_must_be_a_plain_http_url(self):
        # the provider JSPs place this value inside a JavaScript string:
        # anything but a plain http(s) URL is refused at import
        for bad in ("javascript:alert(1)", "https://x.example/'+alert(1)+'",
                    "https://x.example/<script>", "ftp://x.example/",
                    "https://x.example/a b", "not a url"):
            self.assertFalse(o19props.safe_url(bad), bad)
        self.assertTrue(o19props.safe_url("https://intranet.example/res/"))
        result = o19props.translate_all(
            [("resource_base_url", "https://x.example/'+alert(1)+'")])
        self.assertEqual(dict((k, v) for k, v in result["fragment"]), {})
        self.assertEqual(result["rows"][0][1], "refused-invalid")
        self.assertIn("refused-invalid", o19props.render_report(result))

    def test_readerless_paths_are_dropped_not_translated(self):
        for key in ("faxLogo", "oscarMeasurement_css"):
            self.assertEqual(self.by_key.get(key), "dropped-flag", key)
            self.assertNotIn(key, self.fragment)

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

    def test_report_masks_secrets_and_lists_unknown_keys(self):
        result = fixture_result()
        report = o19props.render_report(result)
        self.assertNotIn("fake-mcedt-secret", report)
        self.assertNotIn("fake-mail-secret", report)
        self.assertIn("ROTATE/VERIFY", report)
        # the vendor-fork key surfaces by name for human classification
        self.assertIn("acme_ehr_bridge.endpoint", report)

    def test_fragment_escapes_keys_and_non_latin1_values(self):
        result = {"fragment": [("odd key=1", "caf\u00e9 \u2014 \u4e2d"),
                               ("plain", "x")],
                  "rows": [], "secrets": [], "advisories": {}}
        text = o19props.render_fragment(result)
        self.assertIn("odd\\ key\\=1=", text)
        self.assertIn("caf\u00e9 \\u2014 \\u4e2d", text)
        # a non-BMP character is two 4-digit escapes (a surrogate pair)
        emoji = o19props.escape_property_value("\U0001f600")
        self.assertEqual(emoji, "\\ud83d\\ude00")
        # an unpaired surrogate escape is preserved, not a parse failure
        parsed = dict(o19props.parse_properties_text("k=a\\ud800b\n"))
        self.assertEqual(parsed["k"], "a\ud800b")
        # and the escaped line decodes back to the original pair
        parsed = dict(o19props.parse_properties_text(text))
        self.assertEqual(parsed["odd key=1"], "caf\u00e9 \u2014 \u4e2d")


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

    def test_traversal_tail_is_refused(self):
        self.assertIsNone(o19props.translate_docpath(
            "/var/lib/OscarDocument/oscar/../../../etc/", ROOT))
        self.assertIsNone(o19props.translate_docpath(
            "/var/lib/OscarDocument/oscar/billing/../../..", ROOT))
        # a normalising tail that STAYS inside is fine
        self.assertEqual(o19props.translate_docpath(
            "/var/lib/OscarDocument/oscar/billing/./download/", ROOT),
            ROOT + "/carlos/billing/download/")


class TestJavaPropertiesParser(unittest.TestCase):

    def parse(self, text):
        return dict(o19props.parse_properties_text(text))

    def test_trailing_whitespace_in_values_is_preserved(self):
        self.assertEqual(self.parse("k=secret  \n"), {"k": "secret  "})
        self.assertEqual(self.parse("k = v\n"), {"k": "v"})

    def test_whitespace_and_colon_separators(self):
        self.assertEqual(self.parse("a b\nc:d\ne  =  f\n"),
                         {"a": "b", "c": "d", "e": "f"})

    def test_line_continuation(self):
        text = "key=first \\\n    second\nnext=1\n"
        self.assertEqual(self.parse(text), {"key": "first second",
                                            "next": "1"})
        # an ESCAPED backslash at the end does not continue
        self.assertEqual(self.parse("k=a\\\\\nn=1\n"),
                         {"k": "a\\", "n": "1"})

    def test_malformed_unicode_escape_is_an_error_not_silent_garbage(self):
        for text in ("k=\\u00zz\n", "k=\\u12\n", "k=abc\\u\n"):
            with self.assertRaises(ValueError):
                self.parse(text)

    def test_escapes_are_decoded(self):
        text = "k=a\\tb\\nc\\\\d\\u00e9\\=x\n"
        self.assertEqual(self.parse(text), {"k": "a\tb\nc\\d\u00e9=x"})
        self.assertEqual(self.parse("password_group_special = \\! @\\#$\n"),
                         {"password_group_special": "! @#$"})

    def test_comments_blank_lines_and_last_wins(self):
        text = "# c\n! d\n\nk=1\nk=2\n"
        parsed = o19props.parse_properties_text(text)
        self.assertEqual(parsed, [("k", "2")])

    def test_fragment_round_trips_special_values(self):
        for value in ("a\\b", "tab\there", " lead", "trail ", "x=y:z",
                      "multi\nline", "caf\u00e9 \u2014 \U0001f600"):
            text = "k=" + o19props.escape_property_value(value) + "\n"
            self.assertEqual(self.parse(text), {"k": value}, repr(value))


class TestSecretDefaultsAndDispositions(unittest.TestCase):

    def test_secret_default_keys_are_not_in_the_baseline(self):
        self.assertTrue(o19map_props.SECRET_DEFAULT_KEYS)
        for key in o19map_props.SECRET_DEFAULT_KEYS:
            self.assertNotIn(key, o19map_props.O19_DEFAULTS)
        self.assertIn("hcv.service.pass", o19map_props.SECRET_DEFAULT_KEYS)
        self.assertIn("db_password", o19map_props.SECRET_DEFAULT_KEYS)

    def test_stock_credential_is_always_surfaced(self):
        # even a value identical to the O19 stock default cannot be
        # baseline-skipped: the default is not shipped, so it is flagged
        result = o19props.translate_all(
            [("hcv.service.pass", "Password0!")], documents_root=ROOT)
        rows = {k: (d, n) for k, d, n in result["rows"]}
        self.assertEqual(rows["hcv.service.pass"][0], "carry-secret")
        self.assertIn("stock", rows["hcv.service.pass"][1])
        self.assertIn(("hcv.service.pass", "Password0!"), result["fragment"])

    def test_empty_credential_is_not_carried(self):
        result = o19props.translate_all([("email.password", "")],
                                        documents_root=ROOT)
        self.assertEqual(result["fragment"], [])
        self.assertEqual(result["rows"], [])

    def test_recyclebin_boolean_and_mcedt_checkpoint_carry(self):
        result = o19props.translate_all(
            [("INCOMINGDOCUMENT_RECYCLEBIN", "false"),
             ("mcedt.last.downloadedID.file", ".clinicCheckpoint")],
            documents_root=ROOT)
        fragment = dict(result["fragment"])
        self.assertEqual(fragment["INCOMINGDOCUMENT_RECYCLEBIN"], "false")
        self.assertEqual(fragment["mcedt.last.downloadedID.file"],
                         ".clinicCheckpoint")


if __name__ == "__main__":
    unittest.main()


class TestFragmentFile(unittest.TestCase):
    """The fragment carries credentials in clear: its mode is 0600 from
    the first byte, also when a rerun overwrites a wider pre-existing
    file."""

    def test_wider_pre_existing_file_is_tightened_before_the_write(self):
        import os
        import stat
        import tempfile
        tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, tmp)
        path = os.path.join(tmp, "o19-derived-carlos.properties")
        with open(path, "w") as fh:
            fh.write("old\n")
        os.chmod(path, 0o644)
        o19props.write_fragment(path, "hcv.service.pass=secret\n")
        self.assertEqual(stat.S_IMODE(os.stat(path).st_mode), 0o600)
        with open(path) as fh:
            self.assertEqual(fh.read(), "hcv.service.pass=secret\n")

    def test_fresh_file_is_created_private(self):
        import os
        import stat
        import tempfile
        tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, tmp)
        path = os.path.join(tmp, "fragment")
        old_umask = os.umask(0o000)
        try:
            o19props.write_fragment(path, "k=v\n")
        finally:
            os.umask(old_umask)
        self.assertEqual(stat.S_IMODE(os.stat(path).st_mode), 0o600)

