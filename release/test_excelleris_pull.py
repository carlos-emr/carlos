#!/usr/bin/env python3
# Copyright (C) 2026 CARLOS Contributors
# SPDX-License-Identifier: GPL-2.0-or-later
"""
Unit tests for release/excelleris_pull.py. No network: both remote systems are
replaced by an in-memory fake transport with the same ``request`` signature.

Run from the repository root:

    python3 -m unittest release/test_excelleris_pull.py -v

Requires python3-cryptography, like the tool itself.
"""

from __future__ import annotations

import base64
import datetime as dt
import email
import lzma
import os
import shutil
import stat
import sys
import tempfile
import time
import unittest
import urllib.parse
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.padding import PKCS7
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

sys.path.insert(0, str(Path(__file__).resolve().parent))
import excelleris_pull as ep  # noqa: E402

# A pull body in the single-line shape the Excelleris guide describes and the
# old shell script matched on. Content is synthetic; it is not a real result.
PULL_WITH_RESULTS = (
    b'<HL7Messages MessageFormat="HL7" MessageCount="2" Version="2.3">'
    b'<Message MsgID="1"><![CDATA[MSH|^~\\&|EXCELLERIS|ON|CARLOS|TEST|202610010900||ORU^R01|1|P|2.3\r'
    b"PID|1||FAKE0001||TEST^PATIENT^A||19700101|F]]></Message>"
    b'<Message MsgID="2"><![CDATA[MSH|^~\\&|EXCELLERIS|ON|CARLOS|TEST|202610010901||ORU^R01|2|P|2.3\r'
    b"PID|1||FAKE0002||TEST^PATIENT^B||19700102|M]]></Message>"
    b"</HL7Messages>"
)


# ---------------------------------------------------------------------------
# Test helpers
# ---------------------------------------------------------------------------


class FakeResponse(ep.HttpResponse):
    pass


class FakeTransport:
    """Scripted stand-in for HttpTransport. ``script`` maps a label derived
    from the request (Excelleris Page/ACK/Logout or CARLOS route) to a
    response; every call is recorded for assertions."""

    def __init__(self, script):
        self.script = script
        self.calls: list[tuple[str, str, dict, bytes | None]] = []
        self.cookies = None

    @staticmethod
    def label(method: str, url: str) -> str:
        parts = urllib.parse.urlsplit(url)
        if parts.path.endswith("hl7pull.aspx"):
            q = urllib.parse.parse_qs(parts.query)
            if "Logout" in q:
                return "excelleris:logout"
            if q.get("Page") == ["Login"]:
                return "excelleris:login"
            if "ACK" in q:
                return f"excelleris:ack:{q['ACK'][0]}"
            return "excelleris:pull"
        return f"{method} {parts.path}"

    def request(self, method, url, headers=None, body=None):
        self.calls.append((method, url, headers or {}, body))
        handler = self.script[self.label(method, url)]
        if isinstance(handler, Exception):
            raise handler
        if callable(handler):
            return handler(method, url, headers or {}, body)
        return handler


def ok(body: bytes | str, status: int = 200, headers=None) -> FakeResponse:
    if isinstance(body, str):
        body = body.encode()
    return FakeResponse(status, headers or {}, body)


def make_keys():
    client = rsa.generate_private_key(65537, 2048)
    server = rsa.generate_private_key(65537, 2048)
    client_b64 = base64.b64encode(
        client.private_bytes(
            serialization.Encoding.DER,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    ).decode()
    server_pub_b64 = base64.b64encode(
        server.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
        )
    ).decode()
    return client, server, client_b64, server_pub_b64


def make_pfx(path: Path, password: bytes | None) -> None:
    """A self-signed certificate in a PFX, standing in for the Excelleris one."""
    key = rsa.generate_private_key(65537, 2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "QA Test Clinic")])
    now = dt.datetime.now(dt.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - dt.timedelta(days=1))
        .not_valid_after(now + dt.timedelta(days=365))
        .sign(key, hashes.SHA256())
    )
    enc = (
        serialization.BestAvailableEncryption(password)
        if password
        else serialization.NoEncryption()
    )
    path.write_bytes(pkcs12.serialize_key_and_certificates(b"clinic", key, cert, None, enc))
    path.chmod(0o600)


class TempEnv(unittest.TestCase):
    """Base: a temp tree with a valid config, PFX and keys."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="excelleris-test-"))
        self.addCleanup(shutil.rmtree, self.tmp, True)
        self.client_key, self.server_key, client_b64, server_pub_b64 = make_keys()
        self.pfx = self.tmp / "clinic.pfx"
        make_pfx(self.pfx, b"pfx-secret")
        self.state = self.tmp / "state"
        self.conf = self.tmp / "pull.conf"
        self.write_conf(client_b64=client_b64, server_pub_b64=server_pub_b64)
        self.cfg = ep.load_config(self.conf)

    def write_conf(self, client_b64: str, server_pub_b64: str, **overrides) -> None:
        values = {
            "url": "https://api.ontest.excelleris.com/hl7pull.aspx",
            "user_id": "clinic",
            "password": "p&ss word%",
            "pfx_password": "pfx-secret",
            "base_url": "https://emr.example.test/carlos",
            "username": "excelleris",
            "cpassword": "Secret#1",
            "pin": "1234",
            "service": "excelleris",
            "retention_days": "30",
            "email": "",
        }
        values.update(overrides)
        text = f"""
[excelleris]
context = Test Clinic
url = {values["url"]}
user_id = {values["user_id"]}
password = {values["password"]}
pfx_file = {self.pfx}
pfx_password = {values["pfx_password"]}

[carlos]
base_url = {values["base_url"]}
username = {values["username"]}
password = {values["cpassword"]}
pin = {values["pin"]}
service = {values["service"]}
client_private_key = {client_b64}
server_public_key = {server_pub_b64}

[paths]
state_dir = {self.state}
log_file = {self.tmp / "log" / "pull.log"}
retention_days = {values["retention_days"]}

[alerts]
email = {values["email"]}
sendmail = /bin/false
"""
        self.conf.write_text(text)
        self.conf.chmod(0o600)


# ---------------------------------------------------------------------------
# inspect_pull
# ---------------------------------------------------------------------------


class InspectPullTest(unittest.TestCase):
    def test_counts_messages_in_single_line_document(self):
        s = ep.inspect_pull(PULL_WITH_RESULTS)
        self.assertEqual(s.message_count, 2)
        self.assertTrue(s.has_results)

    def test_counts_messages_when_pretty_printed(self):
        body = b'<HL7Messages MessageCount="1">\n  <Message MsgID="1"><![CDATA[MSH|x]]></Message>\n</HL7Messages>\n'
        self.assertEqual(ep.inspect_pull(body).message_count, 1)

    def test_empty_forms_have_no_results(self):
        for body in (
            b"<HL7Messages/>",
            b'<HL7Messages MessageCount="0"/>',
            b'<HL7Messages ReturnCode="0"/>',
        ):
            s = ep.inspect_pull(body)
            self.assertFalse(s.has_results, body)
            self.assertIsNone(s.problem, body)
            self.assertIsNone(s.return_code, body)

    def test_error_document_is_reported(self):
        s = ep.inspect_pull(b'<HL7Messages ReturnCode="1"/>')
        self.assertEqual(s.return_code, "1")
        self.assertFalse(s.has_results)

    def test_non_xml_and_wrong_root_are_problems(self):
        self.assertIn("not well-formed", ep.inspect_pull(b"<html>maintenance</html").problem)
        self.assertIn("unexpected root", ep.inspect_pull(b"<html><body/></html>").problem)


# ---------------------------------------------------------------------------
# LabUploadEnvelope: must open exactly the way LabUpload2Action opens it
# ---------------------------------------------------------------------------


class EnvelopeTest(unittest.TestCase):
    def test_round_trip_matches_java_receiver(self):
        client, server, client_b64, server_pub_b64 = make_keys()
        env = ep.LabUploadEnvelope(client_b64, server_pub_b64)
        plaintext = PULL_WITH_RESULTS
        ciphertext, key_b64, sig_b64 = env.seal(plaintext)

        # decryptMessage: RSA/ECB/PKCS1Padding unwrap, then Cipher "AES" = ECB/PKCS5
        aes_key = server.decrypt(base64.b64decode(key_b64), padding.PKCS1v15())
        self.assertEqual(len(aes_key), 16)
        dec = Cipher(algorithms.AES(aes_key), modes.ECB()).decryptor()
        padded = dec.update(ciphertext) + dec.finalize()
        unpad = PKCS7(128).unpadder()
        self.assertEqual(unpad.update(padded) + unpad.finalize(), plaintext)

        # validateSignature: MD5withRSA over the plaintext with the client public key
        client.public_key().verify(
            base64.b64decode(sig_b64), plaintext, padding.PKCS1v15(), hashes.MD5()
        )

    def test_fresh_aes_key_per_file(self):
        _, _, client_b64, server_pub_b64 = make_keys()
        env = ep.LabUploadEnvelope(client_b64, server_pub_b64)
        self.assertNotEqual(env.seal(b"a")[1], env.seal(b"a")[1])

    def test_rejects_bad_key_material(self):
        _, _, client_b64, server_pub_b64 = make_keys()
        with self.assertRaises(ep.ConfigError):
            ep.LabUploadEnvelope("not base64!", server_pub_b64)
        with self.assertRaises(ep.ConfigError):
            ep.LabUploadEnvelope(client_b64, base64.b64encode(b"junk").decode())
        with self.assertRaises(ep.ConfigError):
            # swapping the two is a likely operator mistake
            ep.LabUploadEnvelope(server_pub_b64, client_b64)

    def test_strip_key_armour_accepts_pem_and_wrapped_base64(self):
        self.assertEqual(
            ep._strip_key_armour("-----BEGIN X-----\nAB\nCD\n-----END X-----\n"), "ABCD"
        )
        self.assertEqual(ep._strip_key_armour("  AB\n  CD  "), "ABCD")


class MultipartTest(unittest.TestCase):
    def test_body_parses_back_with_fields_and_file(self):
        ctype, body = ep.encode_multipart(
            {"service": "x", "key": "k"}, "importFile", "a.xml", b"\x00\xffbin"
        )
        msg = email.message_from_bytes(f"Content-Type: {ctype}\r\n\r\n".encode() + body)
        parts = {p.get_param("name", header="content-disposition"): p for p in msg.get_payload()}
        self.assertEqual(parts["service"].get_payload(), "x")
        self.assertEqual(parts["key"].get_payload(), "k")
        self.assertEqual(parts["importFile"].get_filename(), "a.xml")
        self.assertEqual(parts["importFile"].get_payload(decode=True), b"\x00\xffbin")


# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------


class ConfigTest(TempEnv):
    def test_loads_and_masks(self):
        self.assertEqual(self.cfg.excelleris_password, "p&ss word%")
        self.assertEqual(self.cfg.carlos_base_url, "https://emr.example.test/carlos")
        masked = self.cfg.masked()
        self.assertEqual(masked["excelleris_password"], "********")
        self.assertEqual(masked["client_private_key"], "********")
        self.assertEqual(masked["carlos_username"], "excelleris")

    def test_refuses_world_readable_config(self):
        self.conf.chmod(0o644)
        with self.assertRaisesRegex(ep.ConfigError, "readable by group/other"):
            ep.load_config(self.conf)

    def test_refuses_world_readable_pfx(self):
        self.pfx.chmod(0o640)
        with self.assertRaisesRegex(ep.ConfigError, "pfx_file"):
            ep.load_config(self.conf)

    def test_validates_pin_username_and_urls(self):
        _, _, c, s = make_keys()
        self.write_conf(c, s, pin="12")
        with self.assertRaisesRegex(ep.ConfigError, "pin"):
            ep.load_config(self.conf)
        self.write_conf(c, s, username="bad user")
        with self.assertRaisesRegex(ep.ConfigError, "username"):
            ep.load_config(self.conf)
        self.write_conf(c, s, base_url="http://emr.example.test/carlos")
        with self.assertRaisesRegex(ep.ConfigError, "https"):
            ep.load_config(self.conf)
        self.write_conf(c, s, url="https://user:pw@api.on.excelleris.com/hl7pull.aspx")
        with self.assertRaisesRegex(ep.ConfigError, "embed credentials"):
            ep.load_config(self.conf)

    def test_missing_required_key(self):
        _, _, c, s = make_keys()
        self.write_conf(c, s, user_id="")
        with self.assertRaisesRegex(ep.ConfigError, r"\[excelleris\] user_id"):
            ep.load_config(self.conf)

    def test_key_file_variant(self):
        _, _, c, s = make_keys()
        keyfile = self.tmp / "client.b64"
        keyfile.write_text("-----BEGIN PRIVATE KEY-----\n" + c + "\n-----END PRIVATE KEY-----\n")
        keyfile.chmod(0o600)
        self.write_conf("", s)
        text = self.conf.read_text().replace(
            "client_private_key = \n", f"client_private_key_file = {keyfile}\n"
        )
        self.conf.write_text(text)
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.client_private_key, c)

    def test_check_config_loads_keys_and_pfx(self):
        import io
        from contextlib import redirect_stdout

        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(ep.check_config(self.cfg), ep.EXIT_OK)
        self.assertIn("********", out.getvalue())
        self.assertNotIn("p&ss word%", out.getvalue())


class ClientCertificateTest(TempEnv):
    def test_pem_is_private_and_removed_afterwards(self):
        with ep.ClientCertificate(self.pfx, "pfx-secret") as cert:
            pem_path = cert.pem_path
            self.assertTrue(pem_path.exists())
            self.assertEqual(stat.S_IMODE(pem_path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(pem_path.parent.stat().st_mode), 0o700)
            self.assertIn(b"BEGIN PRIVATE KEY", pem_path.read_bytes())
            ctx = cert.ssl_context()
            self.assertTrue(ctx.check_hostname)
        self.assertFalse(pem_path.exists())
        self.assertFalse(pem_path.parent.exists())

    def test_wrong_pfx_password_is_a_config_error(self):
        with self.assertRaisesRegex(ep.ConfigError, "cannot open PFX"):
            with ep.ClientCertificate(self.pfx, "wrong"):
                pass


# ---------------------------------------------------------------------------
# Archive
# ---------------------------------------------------------------------------


class ArchiveTest(TempEnv):
    def test_directories_and_files_are_private(self):
        archive = ep.Archive(self.cfg)
        for d in (self.cfg.state_dir, self.cfg.inbox_dir, self.cfg.done_dir, self.cfg.failed_dir):
            self.assertEqual(stat.S_IMODE(d.stat().st_mode), 0o700, d)
        p = archive.save_inbox("20261001-090000", b"<HL7Messages/>")
        self.assertEqual(stat.S_IMODE(p.stat().st_mode), 0o600)
        self.assertEqual(p.read_bytes(), b"<HL7Messages/>")
        self.assertFalse(list(self.cfg.inbox_dir.glob("*.part")))

    def test_same_run_id_twice_does_not_overwrite(self):
        archive = ep.Archive(self.cfg)
        a = archive.save_inbox("r", b"1")
        b = archive.save_inbox("r", b"2")
        self.assertNotEqual(a, b)
        self.assertEqual(a.read_bytes(), b"1")

    def test_mark_done_compresses_and_mark_failed_moves(self):
        archive = ep.Archive(self.cfg)
        p = archive.save_inbox("r1", PULL_WITH_RESULTS)
        done = archive.mark_done(p)
        self.assertFalse(p.exists())
        self.assertEqual(lzma.open(done).read(), PULL_WITH_RESULTS)
        self.assertEqual(stat.S_IMODE(done.stat().st_mode), 0o600)
        q = archive.save_inbox("r2", b"<HL7Messages/>")
        failed = archive.mark_failed(q)
        self.assertEqual(failed.parent, self.cfg.failed_dir)
        self.assertEqual(archive.inbox_files(), [])

    def test_inbox_files_oldest_first(self):
        archive = ep.Archive(self.cfg)
        archive.save_inbox("20261001-090100", b"<a/>")
        archive.save_inbox("20261001-090000", b"<a/>")
        self.assertEqual(
            [p.name for p in archive.inbox_files()], ["20261001-090000.xml", "20261001-090100.xml"]
        )

    def test_purge_respects_retention(self):
        archive = ep.Archive(self.cfg)
        old = archive.mark_done(archive.save_inbox("old", b"<a/>"))
        new = archive.mark_done(archive.save_inbox("new", b"<a/>"))
        past = time.time() - 31 * 86400
        os.utime(old, (past, past))
        self.assertEqual(archive.purge(), 1)
        self.assertFalse(old.exists())
        self.assertTrue(new.exists())


# ---------------------------------------------------------------------------
# Excelleris session
# ---------------------------------------------------------------------------


class ExcellerisSessionTest(TempEnv):
    def script(self, **over):
        s = {
            "excelleris:login": ok(ep._AUTH_GRANTED),
            "excelleris:pull": ok(PULL_WITH_RESULTS),
            "excelleris:ack:Positive": ok("<HL7Messages/>"),
            "excelleris:ack:Negative": ok('<HL7Messages ReturnCode="0"/>'),
            "excelleris:logout": ok(""),
        }
        s.update(over)
        return s

    def test_login_pull_ack_logout_sequence_and_encoding(self):
        t = FakeTransport(self.script())
        with ep.ExcellerisSession(self.cfg, t) as s:
            self.assertEqual(s.pull(), PULL_WITH_RESULTS)
            s.ack(True)
        labels = [FakeTransport.label(m, u) for m, u, _, _ in t.calls]
        self.assertEqual(
            labels,
            ["excelleris:login", "excelleris:pull", "excelleris:ack:Positive", "excelleris:logout"],
        )
        login_url = t.calls[0][1]
        q = urllib.parse.parse_qs(urllib.parse.urlsplit(login_url).query)
        self.assertEqual(q["Password"], ["p&ss word%"])  # survived & space % by being encoded
        self.assertEqual(q["Mode"], ["Silent"])
        pull_q = urllib.parse.parse_qs(urllib.parse.urlsplit(t.calls[1][1]).query)
        self.assertEqual(pull_q, {"Page": ["HL7"], "Query": ["NewRequests"], "Pending": ["Yes"]})
        self.assertEqual(t.calls[0][2]["Accept"], "text/xml, */*")

    def test_access_denied_and_garbage_login_replies(self):
        with self.assertRaisesRegex(ep.StepError, "access denied"):
            with ep.ExcellerisSession(
                self.cfg, FakeTransport(self.script(**{"excelleris:login": ok(ep._AUTH_DENIED)}))
            ):
                pass
        with self.assertRaisesRegex(ep.StepError, "unexpected reply"):
            with ep.ExcellerisSession(
                self.cfg, FakeTransport(self.script(**{"excelleris:login": ok("<html/>", 503)}))
            ):
                pass

    def test_failed_ack_raises(self):
        t = FakeTransport(
            self.script(**{"excelleris:ack:Positive": ok('<HL7Messages ReturnCode="1"/>')})
        )
        with ep.ExcellerisSession(self.cfg, t) as s:
            with self.assertRaisesRegex(ep.StepError, "Positive ack not accepted"):
                s.ack(True)

    def test_logout_failure_is_swallowed(self):
        t = FakeTransport(self.script(**{"excelleris:logout": ep.TransportError("boom")}))
        with ep.ExcellerisSession(self.cfg, t):
            pass  # no exception escapes __exit__

    def test_safe_url_drops_query(self):
        self.assertEqual(ep._safe_url("https://h/p.aspx?Password=x"), "https://h/p.aspx")


# ---------------------------------------------------------------------------
# CARLOS session
# ---------------------------------------------------------------------------


class CarlosSessionTest(TempEnv):
    def script(self, upload_status=200, **over):
        s = {
            # What a normal provider account gets: a redirect to its schedule.
            "POST /carlos/login": ok(
                "", 302, {"Location": "/carlos/provider/providercontrol?year=2026"}
            ),
            "GET /carlos/csrfguard": ok("var x; masterTokenValue = 'TOKEN-123'; var y;"),
            "POST /carlos/lab/newLabUpload": ok("", upload_status),
            "POST /carlos/logout": ok("", 302, {"Location": "/carlos/index"}),
        }
        s.update(over)
        return s

    def session(self, t):
        return ep.CarlosSession(
            self.cfg,
            t,
            ep.LabUploadEnvelope(self.cfg.client_private_key, self.cfg.server_public_key),
        )

    def test_login_csrf_upload_logout(self):
        t = FakeTransport(self.script())
        archive = ep.Archive(self.cfg)
        f = archive.save_inbox("r", PULL_WITH_RESULTS)
        with self.session(t) as s:
            self.assertEqual(s.csrf_token, "TOKEN-123")
            outcome = s.upload(f)
        self.assertTrue(outcome.accepted)
        login = t.calls[0]
        self.assertEqual(urllib.parse.parse_qs(login[3].decode())["ajaxResponse"], ["true"])
        self.assertEqual(urllib.parse.parse_qs(login[3].decode())["pin"], ["1234"])
        csrf = t.calls[1]
        self.assertEqual(csrf[2]["Referer"], "https://emr.example.test/carlos/")
        method, url, headers, body = t.calls[2]
        self.assertEqual(url, "https://emr.example.test/carlos/lab/newLabUpload")
        self.assertEqual(headers["CSRF-TOKEN"], "TOKEN-123")
        self.assertEqual(headers["X-Requested-With"], "XMLHttpRequest")
        msg = email.message_from_bytes(
            f"Content-Type: {headers['Content-Type']}\r\n\r\n".encode() + body
        )
        parts = {p.get_param("name", header="content-disposition"): p for p in msg.get_payload()}
        self.assertEqual(parts["service"].get_payload(), "excelleris")
        self.assertEqual(parts["use_http_response_code"].get_payload(), "true")
        self.assertEqual(parts["importFile"].get_filename(), "r.xml")
        # the file field is the ciphertext, not the plaintext
        self.assertNotEqual(parts["importFile"].get_payload(decode=True), PULL_WITH_RESULTS)
        # and the signature is over the plaintext, checkable with the client public key
        self.client_key.public_key().verify(
            base64.b64decode(parts["signature"].get_payload()),
            PULL_WITH_RESULTS,
            padding.PKCS1v15(),
            hashes.MD5(),
        )
        self.assertEqual(t.calls[3][1], "https://emr.example.test/carlos/logout")

    def test_outcome_classification(self):
        self.assertTrue(ep.UploadOutcome(409, "").accepted)
        self.assertFalse(ep.UploadOutcome(409, "").definitive)
        for status in (400, 403, 500):
            self.assertFalse(ep.UploadOutcome(status, "").accepted)
            self.assertTrue(ep.UploadOutcome(status, "").definitive, status)

    def test_json_success_is_also_accepted(self):
        # CAISI-style accounts reach the JSON branch instead of the redirect.
        t = FakeTransport(
            self.script(**{"POST /carlos/login": ok('{"success": true, "providerNo": "999998"}')})
        )
        self.session(t).login()

    def test_login_failures_are_explained(self):
        cases = {
            "forced password reset": ok("", 302, {"Location": "/carlos/forcepasswordreset"}),
            "more than one facility": ok(
                "", 302, {"Location": "/carlos/select_facility?nextPage=provider"}
            ),
            "MFA": ok("", 302, {"Location": "/carlos/login?mfa=1"}),
            "rejected the credentials": ok('{"success":false,"error":"x"}'),
            "unexpected reply HTTP 503": ok("", 503),
        }
        for expected, resp in cases.items():
            t = FakeTransport(self.script(**{"POST /carlos/login": resp}))
            with self.assertRaisesRegex(ep.StepError, expected):
                self.session(t).login()

    def test_missing_csrf_token_is_an_error(self):
        t = FakeTransport(self.script(**{"GET /carlos/csrfguard": ok("<html>login page</html>")}))
        with self.assertRaisesRegex(ep.StepError, "CSRF token"):
            with self.session(t):
                pass


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


class OrchestrationTest(TempEnv):
    def setUp(self):
        super().setUp()
        self.script = {
            "excelleris:login": ok(ep._AUTH_GRANTED),
            "excelleris:pull": ok(PULL_WITH_RESULTS),
            "excelleris:ack:Positive": ok("<HL7Messages/>"),
            "excelleris:ack:Negative": ok("<HL7Messages/>"),
            "excelleris:logout": ok(""),
            "POST /carlos/login": ok(
                "", 302, {"Location": "/carlos/provider/providercontrol?year=2026"}
            ),
            "GET /carlos/csrfguard": ok("masterTokenValue='T'"),
            "POST /carlos/lab/newLabUpload": ok("", 200),
            "POST /carlos/logout": ok(""),
        }
        self.transports: list[FakeTransport] = []

    def factory(self, timeout, ssl_context, follow_redirects):
        t = FakeTransport(self.script)
        t.timeout, t.ssl_context, t.follow = timeout, ssl_context, follow_redirects
        self.transports.append(t)
        return t

    def labels(self):
        return [FakeTransport.label(m, u) for t in self.transports for m, u, _, _ in t.calls]

    def test_full_run_pulls_acks_uploads_and_archives(self):
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_OK)
        self.assertEqual(
            self.labels(),
            [
                "excelleris:login",
                "excelleris:pull",
                "excelleris:ack:Positive",
                "excelleris:logout",
                "POST /carlos/login",
                "GET /carlos/csrfguard",
                "POST /carlos/lab/newLabUpload",
                "POST /carlos/logout",
            ],
        )
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xml.xz"))), 1)
        # Excelleris transport got the client cert context and follows redirects;
        # the CARLOS transport must not follow them (login diagnosis).
        self.assertIsNotNone(self.transports[0].ssl_context)
        self.assertTrue(self.transports[0].follow)
        self.assertFalse(self.transports[1].follow)

    def test_positive_ack_only_after_file_is_on_disk(self):
        seen = {}

        def ack(method, url, headers, body):
            seen["inbox_at_ack"] = [p.name for p in self.cfg.inbox_dir.glob("*.xml")]
            return ok("<HL7Messages/>")

        self.script["excelleris:ack:Positive"] = ack
        ep.run(self.cfg, ep.RunOptions(no_upload=True), self.factory)
        self.assertEqual(len(seen["inbox_at_ack"]), 1)

    def test_empty_pull_sends_negative_ack_and_stores_nothing(self):
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_OK)
        self.assertIn("excelleris:ack:Negative", self.labels())
        self.assertNotIn("excelleris:ack:Positive", self.labels())
        self.assertNotIn(
            "POST /carlos/login", self.labels()
        )  # nothing to upload, no CARLOS session
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])

    def test_error_document_sends_negative_ack_and_fails(self):
        self.script["excelleris:pull"] = ok('<HL7Messages ReturnCode="1"/>')
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)
        self.assertIn("excelleris:ack:Negative", self.labels())

    def test_store_failure_sends_negative_ack(self):
        original = ep.Archive.save_inbox

        def broken(self_, run_id, data):
            raise OSError("disk full")

        ep.Archive.save_inbox = broken
        try:
            rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        finally:
            ep.Archive.save_inbox = original
        self.assertEqual(rc, ep.EXIT_FAILED)
        self.assertIn("excelleris:ack:Negative", self.labels())
        self.assertNotIn("excelleris:ack:Positive", self.labels())

    def test_excelleris_down_is_a_reported_failure_not_a_crash(self):
        self.script["excelleris:login"] = ep.TransportError("connect timed out")
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)

    def test_rejected_upload_goes_to_failed_and_duplicate_is_fine(self):
        self.script["POST /carlos/lab/newLabUpload"] = ok("", 403)
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.failed_dir.glob("*.xml"))), 1)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])

        self.script["POST /carlos/lab/newLabUpload"] = ok("", 409)
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_OK)

    def test_carlos_down_keeps_file_for_retry_then_retries_first(self):
        self.script["POST /carlos/login"] = ep.TransportError("connection refused")
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)

        # Next run: CARLOS is back. The backlog is uploaded BEFORE the new pull.
        self.transports.clear()
        self.script["POST /carlos/login"] = ok('{"success":true}')
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_OK)
        labels = self.labels()
        self.assertLess(
            labels.index("POST /carlos/lab/newLabUpload"), labels.index("excelleris:login")
        )
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 2)

    def test_dry_run_touches_no_data(self):
        rc = ep.run(self.cfg, ep.RunOptions(dry_run=True), self.factory)
        self.assertEqual(rc, ep.EXIT_OK)
        self.assertEqual(
            self.labels(),
            [
                "excelleris:login",
                "excelleris:logout",
                "POST /carlos/login",
                "GET /carlos/csrfguard",
                "POST /carlos/logout",
            ],
        )

    def test_upload_only_and_no_upload_modes(self):
        ep.run(self.cfg, ep.RunOptions(no_upload=True), self.factory)
        self.assertNotIn("POST /carlos/login", self.labels())
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)
        self.transports.clear()
        ep.run(self.cfg, ep.RunOptions(upload_only=True), self.factory)
        self.assertNotIn("excelleris:login", self.labels())
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])

    def test_lock_contention_exits_3_without_work(self):
        ep.Archive(self.cfg)
        holder = ep.RunLock(self.cfg.lock_file)
        self.assertTrue(holder.acquire())
        try:
            rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        finally:
            holder.release()
        self.assertEqual(rc, ep.EXIT_LOCKED)
        self.assertEqual(self.transports, [])


class CliTest(TempEnv):
    def test_parse_args_modes_are_exclusive(self):
        with self.assertRaises(SystemExit):
            ep.parse_args(["--config", "x", "--no-upload", "--upload-only"])
        ns = ep.parse_args(["--config", str(self.conf), "--dry-run", "-v"])
        self.assertTrue(ns.dry_run and ns.verbose)

    def test_main_reports_config_error(self):
        if os.geteuid() == 0:
            self.skipTest("main() refuses root; cannot drive it from a root test run")
        rc = ep.main(["--config", str(self.tmp / "missing.conf")])
        self.assertEqual(rc, ep.EXIT_CONFIG)


if __name__ == "__main__":
    unittest.main()
