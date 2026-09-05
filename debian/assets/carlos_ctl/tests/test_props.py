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
    return o19props.translate_all(
        clinic, documents_root=ROOT,
        deployment_drugref="http://127.0.0.1:8080/drugref")


# the `carry` keys whose CARLOS default says something different from the
# O19 one: baseline-diff keeps them out of the fragment, so the clinic's
# behaviour flips at cutover and the report row is the only warning
DIVERGENT_CARRY_DEFAULTS = (
    "CONSULTATION_AUTO_INCLUDE_ALLERGIES",
    "CONSULTATION_AUTO_INCLUDE_MEDICATIONS",
    "CONSULTATION_LOCK_REFERRAL_DATE",
    "DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM",
    "ECHART_SIGN_LINE",
    "ECHART_VERSIGN_LINE",
    "FORMS_PROMOTEXT",
    "HL7TEXT_LABS",
    "NEW_CONTACTS_UI",
    "NEW_CONTACTS_UI_EXTERNAL_CONTACT",
    "confidentiality_statement.v1",
    "consultation_signature_enabled",
    "faxPollInterval",
    "save_as_xml",
)


class TestBaselineDiff(unittest.TestCase):

    """Clinic values equal to the stock defaults are not clinic values."""
    def test_untouched_defaults_are_ignored(self):
        result = fixture_result()
        fragment = dict(result["fragment"])
        by_key = {k: d for k, d, _ in result["rows"]}
        # billregion=ON and HL7TEXT_LABS=no equal the stock defaults, so
        # neither reaches the fragment. billregion means the same thing in
        # both products and stays entirely silent; HL7TEXT_LABS does not
        # (CARLOS defaults to yes), so it is reported rather than dropped
        self.assertEqual(o19map_props.O19_DEFAULTS["billregion"], "ON")
        self.assertNotIn("billregion", by_key)
        self.assertNotIn("billregion", fragment)
        self.assertEqual(by_key.get("HL7TEXT_LABS"), "carlos-default")
        self.assertNotIn("HL7TEXT_LABS", fragment)

    def test_project_home_default_with_spaces_is_ignored(self):
        result = fixture_result()
        self.assertNotIn("project_home",
                         {k for k, _, _ in result["rows"]})


class TestDivergentCarlosDefaults(unittest.TestCase):

    """A stock O19 value CARLOS does not agree with.

    Baseline-diff drops it so CARLOS's default wins (plan §8.1 rule 1)
    -- correct, but for these keys that silently changes what the clinic
    sees after cutover, and the key used to leave no trace at all in
    report.txt."""

    def test_the_manifest_lists_exactly_the_divergent_carry_keys(self):
        self.assertEqual(sorted(o19map_props.CARLOS_DEFAULTS),
                         sorted(DIVERGENT_CARRY_DEFAULTS))
        for key, carlos_value in o19map_props.CARLOS_DEFAULTS.items():
            # only `carry` keys qualify: a translate/deploy-owned key is
            # never copied verbatim, so CARLOS owning it is the intent
            self.assertEqual(o19props.disposition(key)["d"], "carry", key)
            self.assertNotEqual(o19map_props.O19_DEFAULTS[key],
                                carlos_value, key)

    def test_a_stock_value_is_reported_when_the_carlos_default_differs(self):
        result = o19props.translate_all(
            [("CONSULTATION_AUTO_INCLUDE_ALLERGIES", "true"),
             ("consultation_signature_enabled", "true"),
             ("billregion", "ON")],
            documents_root=ROOT)
        # nothing is carried: the fragment is unchanged by this rule
        self.assertEqual(result["fragment"], [])
        rows = {k: (d, n) for k, d, n in result["rows"]}
        self.assertEqual(rows["CONSULTATION_AUTO_INCLUDE_ALLERGIES"][0],
                         "carlos-default")
        self.assertIn("untouched O19 default 'true'",
                      rows["CONSULTATION_AUTO_INCLUDE_ALLERGIES"][1])
        self.assertIn("CARLOS's default is 'false'",
                      rows["CONSULTATION_AUTO_INCLUDE_ALLERGIES"][1])
        # a key both products default the same way stays silent
        self.assertNotIn("billregion", rows)
        report = o19props.render_report(result)
        self.assertIn("carlos-default (2):", report)
        self.assertIn("consultation_signature_enabled", report)

    def test_a_clinic_value_that_differs_still_carries(self):
        # the rule fires only on the untouched stock value; a clinic that
        # actually changed the key keeps its choice
        result = o19props.translate_all(
            [("CONSULTATION_AUTO_INCLUDE_ALLERGIES", "false")],
            documents_root=ROOT)
        self.assertEqual(
            result["fragment"],
            [("CONSULTATION_AUTO_INCLUDE_ALLERGIES", "false")])
        self.assertEqual(result["rows"][0][1], "carry")


class TestEveryStockKeyIsClassified(unittest.TestCase):

    """No key of the stock properties file is left unclassified."""
    def test_no_stock_key_is_left_unknown(self):
        # every key the stock O19 file ships (secrets included) has a
        # curated disposition: an "unknown" would only ever mean the
        # overlay drifted from the vendored properties file
        from carlos_ctl import o19map_props
        keys = set(o19map_props.O19_DEFAULTS) | set(
            o19map_props.SECRET_DEFAULT_KEYS)
        unknown = sorted(k for k in keys
                         if o19props.disposition(k)["d"] == "unknown")
        self.assertEqual(unknown, [])
        # the credential-bearing HRM user name is dropped, never carried
        self.assertEqual(o19props.disposition("OMD_HRM_USER")["d"],
                         "dropped-flag")
        self.assertEqual(o19props.disposition("FILTER_ON_FACILITY")["d"],
                         "carry")
        self.assertEqual(
            o19props.disposition("WKHTMLTOPDF_COMMAND")["d"], "deploy-owned")


class TestDispositions(unittest.TestCase):

    """What happens to each class of property key.

    Carried, translated onto the CARLOS tree, carried as a secret,
    dropped, or refused because the deployment owns it -- and a
    vendor-fork key is reported as unknown rather than passing
    silently."""
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


class TestFaxKeys(unittest.TestCase):
    """CARLOS kept fax (SRFax transport); only the old middleware
    transport's settings are gone. Dropping the per-feature switches
    turned the clinic's Rx and consultation fax buttons off at cutover
    and told the operator the module had been removed."""

    def test_the_switches_carlos_reads_are_carried(self):
        for key in ("faxPollInterval", "RXFAX", "rx_fax_enabled",
                    "consultation_fax_enabled", "eform_fax_enabled"):
            self.assertEqual(o19map_props.KEYS[key]["d"], "carry", key)

    def test_the_middleware_transport_settings_are_still_dropped(self):
        for key in ("faxURI", "faxIdentifier", "faxKeystore"):
            self.assertEqual(o19map_props.KEYS[key]["d"], "dropped-flag",
                             key)

    def test_faxEnable_is_carried_under_the_name_carlos_reads(self):
        self.assertEqual(o19map_props.KEYS["faxEnable"],
                         {"d": "carry", "as": "enableFax"})
        result = o19props.translate_all([("faxEnable", "true")],
                                        documents_root=ROOT)
        fragment = dict(result["fragment"])
        self.assertEqual(fragment.get("enableFax"), "true")
        self.assertNotIn("faxEnable", fragment)
        note = [n for k, d, n in result["rows"] if k == "faxEnable"][0]
        self.assertIn("enableFax", note)


class TestRendering(unittest.TestCase):

    """The fragment an operator reviews, and the report beside it.

    The fragment must be valid java.util.Properties text; the report
    masks secrets."""
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

    """Mapping an OSCAR 19 document path onto the CARLOS tree."""
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

    """Java's own properties semantics, not Python's.

    Separators, continuations, escapes and line terminators all follow
    java.util.Properties, because CARLOS will read the same file with
    that parser."""
    def parse(self, text):
        return dict(o19props.parse_properties_text(text))

    def test_trailing_whitespace_in_values_is_preserved(self):
        self.assertEqual(self.parse("k=secret  \n"), {"k": "secret  "})
        self.assertEqual(self.parse("k = v\n"), {"k": "v"})

    def test_whitespace_and_colon_separators(self):
        self.assertEqual(self.parse("a b\nc:d\ne  =  f\n"),
                         {"a": "b", "c": "d", "e": "f"})

    def test_line_terminators_and_whitespace_match_java(self):
        # java.util.Properties ends a line at \n, \r, \r\n only and strips
        # space, tab and form feed: a Windows-1252 ellipsis (0x85 through
        # latin-1) or a form feed is value text, NBSP is key text
        pairs = dict(o19props.parse_properties_text(
            "Support_Contact=Call us\x85 ext 12\r\nk2=a\x0cb\r\n"
            "\xa0odd=1\n"))
        self.assertEqual(pairs["Support_Contact"], "Call us\x85 ext 12")
        self.assertEqual(pairs["k2"], "a\x0cb")
        self.assertIn("\xa0odd", pairs)
        self.assertNotIn("ext", pairs)

    def test_trailing_continuation_at_eof_keeps_the_record(self):
        self.assertEqual(o19props.parse_properties_text("a=1\nk=v\\"),
                         [("a", "1"), ("k", "v")])

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

    """Stock credentials, and the ones a clinic actually changed."""
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


class TestTheReportCannotBeForged(unittest.TestCase):

    """A clinic property key is attacker-influenced text that lands in
    the operator's validation report.

    `java.util.Properties` lets a key carry an escaped line break, and
    `parse_properties_text` decodes it -- so a crafted oscar.properties
    could write its own lines into `report.txt`, up to a plausible
    `carry-secret (0):` heading that hides a real carried credential
    from the reviewer. The fragment writer has escaped keys since it was
    written; the report writer did not."""

    def report(self, rows, secrets=(), unknown=()):
        return o19props.render_report({
            "rows": rows, "secrets": list(secrets), "advisories": {},
            "unknown": list(unknown), "fragment": []})

    def test_the_decoded_key_really_does_carry_a_line_break(self):
        # the premise, asserted rather than assumed: without this the
        # test below could pass because the parser rejected the key
        # an unescaped space ends the key, so the injected heading is
        # spelled without one -- the point stands either way: the key
        # the parser hands back contains a real line break
        parsed = o19props.parse_properties_text(
            "real.key\\ncarry-secret\\: = v\n")
        self.assertEqual(parsed[0][0], "real.key\ncarry-secret:")

    def test_a_key_carrying_a_line_break_cannot_add_a_report_line(self):
        forged = "real.key\ncarry-secret:"
        body = self.report([(forged, "dropped-flag", "")])
        self.assertIn("real.key\\ncarry-secret:", body)
        self.assertEqual(
            [ln for ln in body.splitlines()
             if ln.startswith("carry-secret")], [],
            "a clinic key forged a heading in the report:\n" + body)

    def test_a_note_is_escaped_too(self):
        body = self.report([("k", "dropped-flag", "a\nb")])
        self.assertNotIn("\nb]", body)
        self.assertIn("a\\nb", body)

    def test_an_ordinary_key_is_rendered_unchanged(self):
        body = self.report([("drugref.url", "carry", "")])
        self.assertIn("drugref.url", body)
        self.assertNotIn("\\", body)

    def test_a_secret_name_cannot_add_a_report_line(self):
        # the ROTATE/VERIFY line joined result["secrets"] raw while every
        # other clinic-supplied name went through report_safe, so the one
        # line naming carried credentials was the forgeable one
        forged = "db.password\ncarry-secret (0):"
        body = self.report([], secrets=[forged])
        self.assertIn("db.password\\ncarry-secret", body)
        self.assertEqual(
            [ln for ln in body.splitlines()
             if ln.startswith("carry-secret")], [],
            "a credential key forged a heading in the report:\n" + body)

    def test_an_unknown_key_cannot_add_a_report_line(self):
        forged = "some.new.key\nUNKNOWN key(s) needing classification: none"
        body = self.report([], unknown=[forged])
        self.assertEqual(
            len([ln for ln in body.splitlines()
                 if ln.startswith("UNKNOWN key(s)")]), 1,
            "an unknown key forged a second summary line:\n" + body)

    def test_a_control_character_is_shown_as_an_escape(self):
        self.assertEqual(o19props.report_safe("a\x07b"), "a\\u0007b")


if __name__ == "__main__":
    unittest.main()
