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
import dataclasses
import datetime as dt
import email
import lzma
import os
import shutil
import socket
import subprocess
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

# Fixture login for the fake EMRs. Placeholders, never real; named so no
# secret scanner mistakes a test file for a leaked credential pair.
EMR_USER = "labsvc"
EMR_PASSWORD = "unit-test-placeholder"
EMR_PIN = "1234"

# The tool logs its alerts at ERROR; without a handler Python's last-resort
# handler would print them into the test output. Keep the suite quiet.
import logging  # noqa: E402

logging.getLogger().addHandler(logging.NullHandler())

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
            "username": EMR_USER,
            "cpassword": EMR_PASSWORD,
            "pin": EMR_PIN,
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
{values.get("extra_excelleris", "")}

[carlos]
base_url = {values["base_url"]}
username = {values["username"]}
password = {values["cpassword"]}
pin = {values["pin"]}
service = {values["service"]}
{values.get("extra_carlos", "")}
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
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
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
        # codeql[py/weak-cryptographic-algorithm]: re-implements the Java receiver on purpose
        dec = Cipher(
            algorithms.AES(aes_key), modes.ECB()
        ).decryptor()  # codeql[py/weak-cryptographic-algorithm]
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
        self.assertEqual(masked["carlos_username"], EMR_USER)

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

    def test_ca_file_must_exist(self):
        text = self.conf.read_text().replace(
            "[carlos]\n", "[carlos]\nca_file = /nonexistent/ca.pem\n", 1
        )
        self.conf.write_text(text)
        with self.assertRaisesRegex(ep.ConfigError, "ca_file not found"):
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
        self.assertEqual(t.calls[0][2]["Accept"], "*/*")  # curl's default, as the script sent

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
            with self.assertRaisesRegex(ep.StepError, "Positive ack failed"):
                s.ack(True)

    def test_unrecognised_ack_reply_is_lenient_but_http_error_is_not(self):
        # The shell script only ever logged the ack reply; an unknown 200 body
        # must not alert on every run, but a non-200 is a real refusal.
        t = FakeTransport(self.script(**{"excelleris:ack:Negative": ok("OK")}))
        with ep.ExcellerisSession(self.cfg, t) as s:
            s.ack(False)  # no exception
        t = FakeTransport(self.script(**{"excelleris:ack:Negative": ok("", 500)}))
        with ep.ExcellerisSession(self.cfg, t) as s:
            with self.assertRaisesRegex(ep.StepError, "Negative ack rejected"):
                s.ack(False)

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
        self.assertEqual(urllib.parse.parse_qs(login[3].decode())["pin"], [EMR_PIN])
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
        self.assertFalse(ep.UploadOutcome(409, "").permanent)
        for status in (400, 403, 406):  # intrinsic to the request: never retried
            self.assertFalse(ep.UploadOutcome(status, "").accepted)
            self.assertTrue(ep.UploadOutcome(status, "").permanent, status)
        for status in (0, 302, 429, 500, 502, 503, 504):  # worth another run
            self.assertTrue(ep.UploadOutcome(status, "").transient, status)
            self.assertFalse(ep.UploadOutcome(status, "").permanent, status)

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
            "rejected the credentials: x": ok('{"success":false,"error":"x"}'),
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


class _OrchestrationBase(TempEnv):
    """Scripted-transport fixture: a CARLOS-flavour config and the labels helper."""

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


class OrchestrationTest(_OrchestrationBase):
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

    def test_failed_pull_sends_negative_ack_and_still_uploads_backlog(self):
        archive = ep.Archive(self.cfg)
        archive.save_inbox("backlog", PULL_WITH_RESULTS)
        self.script["excelleris:pull"] = ok("maintenance", 503)
        rc = ep.run(self.cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)
        labels = self.labels()
        self.assertIn("excelleris:ack:Negative", labels)
        self.assertEqual(labels.count("POST /carlos/lab/newLabUpload"), 1)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])

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


# ---------------------------------------------------------------------------
# End to end over real TLS: real HttpTransport, real client certificate,
# real crypto, against local HTTPS servers that impersonate both ends.
# ---------------------------------------------------------------------------

import http.server  # noqa: E402
import ssl  # noqa: E402
import threading  # noqa: E402
import urllib.parse as _up  # noqa: E402

from cryptography.hazmat.primitives.asymmetric import padding as _pad  # noqa: E402


def _self_signed(cn: str, san: str | None = None):
    key = rsa.generate_private_key(65537, 2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, cn)])
    now = dt.datetime.now(dt.timezone.utc)
    builder = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - dt.timedelta(days=1))
        .not_valid_after(now + dt.timedelta(days=30))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
    )
    if san:
        builder = builder.add_extension(
            x509.SubjectAlternativeName([x509.DNSName(san)]), critical=False
        )
    cert = builder.sign(key, hashes.SHA256())
    return key, cert


class _QuietHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_a):  # keep the test output clean
        pass

    def _reply(self, status: int, body: bytes = b"", headers: dict | None = None):
        self.send_response(status)
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class FakeExcellerisHandler(_QuietHandler):
    """Excelleris HL7 pull endpoint as the shell script and guide describe it."""

    def do_GET(self):  # noqa: N802
        srv = self.server
        q = _up.parse_qs(_up.urlsplit(self.path).query)
        srv.log.append(("GET", dict(q), self.headers.get("Cookie"), self.headers.get("User-Agent")))
        if "Logout" in q:
            return self._reply(200, b"")
        if q.get("Page") == ["Login"]:
            if q.get("UserID") == ["clinic"] and q.get("Password") == ["p&ss word%"]:
                return self._reply(
                    200,
                    ep._AUTH_GRANTED.encode(),
                    {"Set-Cookie": "ASP.NET_SessionId=live1; Path=/"},
                )
            return self._reply(200, ep._AUTH_DENIED.encode())
        if "live1" not in (self.headers.get("Cookie") or ""):
            return self._reply(403, b"no session")
        if "ACK" in q:
            srv.acks.append(q["ACK"][0])
            return self._reply(200, b"<HL7Messages/>")
        if q.get("Page") == ["HL7"] and q.get("Pending") == ["Yes"]:
            return self._reply(200, srv.next_pull)
        return self._reply(400, b"?")


class FakeCarlosHandler(_QuietHandler):
    """The four CARLOS routes the tool uses, with the real server-side checks:
    session cookie, Referer on /csrfguard, CSRF-TOKEN header on the upload,
    RSA unwrap + AES-ECB decrypt + MD5withRSA verify, and checksum dedupe."""

    def do_GET(self):  # noqa: N802
        srv = self.server
        path = _up.urlsplit(self.path).path
        if path == "/carlos/logout":
            return self._reply(405, b"POST only")
        if path == "/carlos/csrfguard":
            if "JSESSIONID=sess1" not in (self.headers.get("Cookie") or ""):
                return self._reply(302, b"", {"Location": "/carlos/index"})
            if not (self.headers.get("Referer") or "").startswith(srv.base_url):
                return self._reply(404, b"")
            return self._reply(200, b"(function(){ var masterTokenValue = 'LIVE-TOKEN'; })();")
        return self._reply(404, b"")

    def do_POST(self):  # noqa: N802
        srv = self.server
        path = _up.urlsplit(self.path).path
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length)
        if path == "/carlos/login":
            form = _up.parse_qs(body.decode())
            srv.log.append(("login", form.get("username"), form.get("pin")))
            good = (
                form.get("username") == [EMR_USER]
                and form.get("password") == [EMR_PASSWORD]
                and form.get("pin") == ["1234"]
            )
            if getattr(srv, "oscar19", False):
                # OSCAR 19 LoginAction honours ajaxResponse=true: HTTP 200 and a
                # text/x-json body either way, no redirect.
                if good:
                    return self._reply(
                        200,
                        b'{"success":true,"providerName":"Doc, Test","providerNo":"999998"}',
                        {
                            "Set-Cookie": "JSESSIONID=sess1; Path=/carlos",
                            "Content-Type": "text/x-json",
                        },
                    )
                return self._reply(
                    200,
                    b'{"success":false,"error":"Invalid username, password or PIN"}',
                    {"Content-Type": "text/x-json"},
                )
            if good:
                # CARLOS Login2Action: a provider account is always redirected.
                return self._reply(
                    302,
                    b"",
                    {
                        "Set-Cookie": "JSESSIONID=sess1; Path=/carlos",
                        "Location": "/carlos/provider/providercontrol?year=2026",
                    },
                )
            return self._reply(302, b"", {"Location": "/carlos/loginfailed?errormsg=x"})
        if path == "/carlos/logout":
            srv.log.append(("logout",))
            return self._reply(302, b"", {"Location": "/carlos/index"})
        if path == "/carlos/lab/newLabUpload":
            if not getattr(srv, "oscar19", False) and "JSESSIONID=sess1" not in (
                self.headers.get("Cookie") or ""
            ):
                return self._reply(302, b"", {"Location": "/carlos/index"})
            if not getattr(srv, "oscar19", False) and (
                self.headers.get("CSRF-TOKEN") != "LIVE-TOKEN"
                or "XMLHttpRequest" not in (self.headers.get("X-Requested-With") or "")
            ):
                return self._reply(403, b"csrf")
            msg = email.message_from_bytes(
                f"Content-Type: {self.headers['Content-Type']}\r\n\r\n".encode() + body
            )
            parts = {
                p.get_param("name", header="content-disposition"): p for p in msg.get_payload()
            }
            if parts.get("service", None) is None or parts["service"].get_payload() != "excelleris":
                return self._reply(403, b"unknown service")
            ciphertext = parts["importFile"].get_payload(decode=True)
            try:
                aes_key = srv.server_key.decrypt(
                    base64.b64decode(parts["key"].get_payload()), _pad.PKCS1v15()
                )
                # codeql[py/weak-cryptographic-algorithm]: the fake EMR decrypts as the real one does
                dec = Cipher(
                    algorithms.AES(aes_key), modes.ECB()
                ).decryptor()  # codeql[py/weak-cryptographic-algorithm]
                unpad = PKCS7(128).unpadder()
                plaintext = unpad.update(dec.update(ciphertext) + dec.finalize()) + unpad.finalize()
                srv.client_pub.verify(
                    base64.b64decode(parts["signature"].get_payload()),
                    plaintext,
                    _pad.PKCS1v15(),
                    hashes.MD5(),
                )
            except Exception:  # noqa: BLE001 - this is the server's rejection path
                return self._reply(406, b"validation failed")
            if plaintext in srv.seen:
                return self._reply(409, b"uploaded previously")
            if getattr(srv, "fail_next_import", False):
                srv.fail_next_import = False
                if getattr(srv, "oscar19", False):
                    # OSCAR 19: FileUploadCheck.addFile runs before the parse,
                    # so a failed import still leaves the checksum behind.
                    srv.seen.append(plaintext)
                srv.log.append(("upload-failed", parts["importFile"].get_filename()))
                return self._reply(500, b"")
            srv.seen.append(plaintext)
            srv.log.append(
                (
                    "upload",
                    parts["importFile"].get_filename(),
                    len(plaintext),
                    self.headers.get("CSRF-TOKEN"),
                    _up.urlsplit(self.path).path,
                )
            )
            return self._reply(200, b"")
        return self._reply(404, b"")


class FakeOscar19Handler(FakeCarlosHandler):
    """OSCAR 19 as read from oscaremr/oscar master: *.do routes behind a
    LoginFilter that bounces anything else to logout.jsp, no CSRF servlet,
    GET logout.jsp, and 406 for a bad signature."""

    def do_GET(self):  # noqa: N802
        path = _up.urlsplit(self.path).path
        if path == "/carlos/logout.jsp":
            self.server.log.append(("logout",))
            return self._reply(302, b"", {"Location": "/carlos/index.jsp"})
        return self._reply(404, b"")  # includes /csrfguard: no such servlet

    def do_POST(self):  # noqa: N802
        path = _up.urlsplit(self.path).path
        if not path.endswith(".do"):
            # LoginFilter: not exempt and no session -> logout.jsp
            self.rfile.read(int(self.headers.get("Content-Length") or 0))
            return self._reply(302, b"", {"Location": "/carlos/logout.jsp"})
        self.server.oscar19 = True
        self.path = self.path.replace(".do", "", 1)  # then the same action logic
        return super().do_POST()


def _tls_material(tmp: Path, pfx: Path) -> tuple[Path, Path, Path]:
    """Server key+cert PEM, the CA bundle a client trusts it through, and the
    clinic certificate (from the test PFX) a server requires of its client."""
    srv_key, srv_cert = _self_signed("localhost", san="localhost")
    server_pem = tmp / "server.pem"
    server_pem.write_bytes(
        srv_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
        + srv_cert.public_bytes(serialization.Encoding.PEM)
    )
    ca_pem = tmp / "ca.pem"
    ca_pem.write_bytes(srv_cert.public_bytes(serialization.Encoding.PEM))
    _, client_cert, _ = pkcs12.load_key_and_certificates(pfx.read_bytes(), b"pfx-secret")
    client_pem = tmp / "client-cert.pem"
    client_pem.write_bytes(client_cert.public_bytes(serialization.Encoding.PEM))
    return server_pem, ca_pem, client_pem


def _excelleris_server_context(server_pem: Path, client_pem: Path) -> ssl.SSLContext:
    """The Excelleris stand-in requires the clinic's client certificate."""
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(str(server_pem))
    ctx.verify_mode = ssl.CERT_REQUIRED
    ctx.load_verify_locations(cafile=str(client_pem))
    return ctx


def _serve(handler, server_ctx: ssl.SSLContext):
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    srv.socket = server_ctx.wrap_socket(srv.socket, server_side=True)
    srv.log, srv.acks, srv.seen = [], [], []
    t = threading.Thread(target=srv.serve_forever, daemon=True)
    t.start()
    return srv


class LiveServersTest(TempEnv):
    carlos_handler = FakeCarlosHandler
    flavour = "carlos"
    product = "CARLOS"

    def setUp(self):
        super().setUp()
        # urllib honours proxy variables; the container sets HTTPS_PROXY.
        self._env = {k: os.environ.get(k) for k in ("NO_PROXY", "no_proxy")}
        os.environ["NO_PROXY"] = os.environ["no_proxy"] = "localhost,127.0.0.1"

        self.server_pem, self.ca_pem, client_pem = _tls_material(self.tmp, self.pfx)
        ex_ctx = _excelleris_server_context(self.server_pem, client_pem)
        self.excelleris = _serve(FakeExcellerisHandler, ex_ctx)
        self.excelleris.next_pull = PULL_WITH_RESULTS

        ca_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ca_ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ca_ctx.load_cert_chain(str(self.server_pem))
        self.carlos = _serve(self.carlos_handler, ca_ctx)
        self.carlos.server_key = self.server_key
        self.carlos.client_pub = self.client_key.public_key()
        self.carlos.base_url = f"https://localhost:{self.carlos.server_address[1]}/carlos"

        _, _, client_b64, server_pub_b64 = (
            None,
            None,
            self.cfg.client_private_key,
            self.cfg.server_public_key,
        )
        self.write_conf(
            client_b64,
            server_pub_b64,
            url=f"https://localhost:{self.excelleris.server_address[1]}/hl7pull.aspx",
            base_url=self.carlos.base_url,
        )
        text = self.conf.read_text()
        text = text.replace(
            "[excelleris]\n",
            f"[excelleris]\nca_file = {self.ca_pem}\nproduct = {self.product}\n",
            1,
        )
        text = text.replace(
            "[carlos]\n", f"[carlos]\nca_file = {self.ca_pem}\nflavour = {self.flavour}\n", 1
        )
        self.conf.write_text(text)
        self.cfg = ep.load_config(self.conf)

    def tearDown(self):
        for srv in (self.excelleris, self.carlos):
            srv.shutdown()
            srv.server_close()
        for k, v in self._env.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v

    def test_full_pipeline_over_tls(self):
        rc = ep.run(self.cfg, ep.RunOptions())
        self.assertEqual(rc, ep.EXIT_OK)
        # Excelleris: client cert accepted, credentials decoded intact, UA ours, ack positive, logout.
        pages = [c[1].get("Page", c[1].get("Logout"))[0] for c in self.excelleris.log]
        self.assertEqual(pages, ["Login", "HL7", "HL7", "Yes"])
        self.assertEqual(self.excelleris.acks, ["Positive"])
        self.assertTrue(all(c[3] == ep.USER_AGENTS[self.product] for c in self.excelleris.log))
        # CARLOS: login, upload decrypted and verified, logout via POST.
        kinds = [c[0] for c in self.carlos.log]
        self.assertEqual(kinds, ["login", "upload", "logout"])
        self.assertEqual(self.carlos.log[1][2], len(PULL_WITH_RESULTS))
        self.assertEqual(self.carlos.seen, [PULL_WITH_RESULTS])
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 1)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])

    def test_duplicate_is_accepted_and_empty_pull_is_negative(self):
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_OK)
        # Re-pull the same results (as Excelleris would after a lost ack): CARLOS says 409, still clean.
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_OK)
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 2)
        self.excelleris.next_pull = b"<HL7Messages/>"
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_OK)
        self.assertEqual(self.excelleris.acks, ["Positive", "Positive", "Negative"])

    def test_wrong_carlos_credentials_keep_pull_for_retry(self):
        text = self.conf.read_text().replace(f"password = {EMR_PASSWORD}", "password = wrong")
        self.conf.write_text(text)
        cfg = ep.load_config(self.conf)
        self.assertEqual(ep.run(cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual(self.excelleris.acks, ["Positive"])  # Excelleris side completed
        self.assertEqual(len(list(cfg.inbox_dir.glob("*.xml"))), 1)  # kept for the next run
        self.assertEqual([c[0] for c in self.carlos.log], ["login"])

    def test_retry_after_failed_import(self):
        """CARLOS: the checksum rolls back with a failed import, so the retry
        imports and the file is archived."""
        self.carlos.fail_next_import = True
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)
        self.excelleris.next_pull = b"<HL7Messages/>"
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_OK)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 1)
        self.assertEqual(list(self.cfg.failed_dir.glob("*")), [])

    def test_untrusted_server_certificate_is_refused(self):
        text = self.conf.read_text().replace(f"ca_file = {self.ca_pem}\n", "")
        self.conf.write_text(text)
        cfg = ep.load_config(self.conf)
        self.assertEqual(ep.run(cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual(self.excelleris.log, [])  # handshake never completed

    def test_dry_run_live(self):
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(dry_run=True)), ep.EXIT_OK)
        self.assertEqual(
            [c[1].get("Page", c[1].get("Logout"))[0] for c in self.excelleris.log], ["Login", "Yes"]
        )
        self.assertEqual([c[0] for c in self.carlos.log], ["login", "logout"])
        self.assertEqual(self.excelleris.acks, [])


class ConfigFlavourTest(TempEnv):
    def test_defaults_and_validation(self):
        self.assertEqual(self.cfg.carlos_flavour, "carlos")
        self.assertEqual(self.cfg.excelleris_product, "CARLOS")
        _, _, c, srv = make_keys()
        self.write_conf(
            c, srv, extra_carlos="flavour = OSCAR19", extra_excelleris="product = OSCAR"
        )
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.carlos_flavour, "oscar19")
        self.assertEqual(cfg.excelleris_product, "OSCAR")
        self.write_conf(c, srv, extra_carlos="flavour = oscar18")
        with self.assertRaisesRegex(ep.ConfigError, "flavour must be one of"):
            ep.load_config(self.conf)
        self.write_conf(c, srv, extra_excelleris="product = LIFELABS")
        with self.assertRaisesRegex(ep.ConfigError, "product must be CARLOS or OSCAR"):
            ep.load_config(self.conf)

    def test_product_defaults_to_carlos_for_every_flavour(self):
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, extra_carlos="flavour = oscar19")
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.excelleris_product, "CARLOS")
        self.assertEqual(cfg.excelleris_user_agent, ep.USER_AGENT_CARLOS_SCRIPT)
        self.write_conf(
            c, srv, extra_carlos="flavour = oscar19", extra_excelleris="product = OSCAR"
        )
        self.assertEqual(ep.load_config(self.conf).excelleris_product, "OSCAR")

    def test_user_agent_shape(self):
        self.assertEqual(
            ep.user_agent("OSCAR"),
            "Mozilla\\/5.0 (Windows NT 10.0; OSCAR19; 1.0.4) Gecko\\/20100101 Firefox\\/128.0",
        )
        # Byte-exact with the two shell scripts, stray backslashes included.
        self.assertEqual(
            ep.user_agent("carlos"),
            "Mozilla\\/5.0 (Windows NT 6.2; CARLOS; 1.0.6) Gecko\\/20100101 Firefox\\/32.0",
        )


class ExcellerisProductHeaderTest(ExcellerisSessionTest):
    def test_product_is_sent_in_user_agent_regardless_of_flavour(self):
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, extra_excelleris="product = OSCAR", extra_carlos="flavour = carlos")
        cfg = ep.load_config(self.conf)
        t = FakeTransport(self.script())
        with ep.ExcellerisSession(cfg, t):
            pass
        for _m, _u, headers, _b in t.calls:
            # Exactly curl's default Accept plus the User-Agent, nothing else.
            self.assertEqual(headers, {"Accept": "*/*", "User-Agent": ep.USER_AGENT_OSCAR19_SCRIPT})


class Oscar19SessionTest(TempEnv):
    """Fake-transport view of the OSCAR 19 routing."""

    def setUp(self):
        super().setUp()
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, extra_carlos="flavour = oscar19")
        self.cfg = ep.load_config(self.conf)

    def script(self, upload_status=200, **over):
        s = {
            "POST /carlos/login.do": ok(
                "", 302, {"Location": "/carlos/provider/providercontrol.jsp"}
            ),
            "POST /carlos/lab/newLabUpload.do": ok("", upload_status),
            "GET /carlos/logout.jsp": ok("", 302, {"Location": "/carlos/index.jsp"}),
        }
        s.update(over)
        return s

    def session(self, t):
        return ep.CarlosSession(
            self.cfg,
            t,
            ep.LabUploadEnvelope(self.cfg.client_private_key, self.cfg.server_public_key),
        )

    def test_routes_and_no_csrf(self):
        t = FakeTransport(self.script())
        f = ep.Archive(self.cfg).save_inbox("r", PULL_WITH_RESULTS)
        with self.session(t) as s:
            self.assertFalse(s.uses_csrf)
            self.assertIsNone(s.csrf_token)
            outcome = s.upload(f)
        self.assertTrue(outcome.accepted)
        labels = [FakeTransport.label(m, u) for m, u, _, _ in t.calls]
        self.assertEqual(
            labels,
            ["POST /carlos/login.do", "POST /carlos/lab/newLabUpload.do", "GET /carlos/logout.jsp"],
        )
        upload_headers = t.calls[1][2]
        self.assertNotIn("CSRF-TOKEN", upload_headers)
        self.assertNotIn("X-Requested-With", upload_headers)

    def test_406_is_a_permanent_rejection(self):
        t = FakeTransport(self.script(upload_status=406))
        f = ep.Archive(self.cfg).save_inbox("r", PULL_WITH_RESULTS)
        with self.session(t) as s:
            outcome = s.upload(f)
        self.assertTrue(outcome.permanent)
        self.assertIn("signature validation failed", outcome.detail)

    def test_wrong_flavour_is_explained(self):
        # oscar19 flavour against a CARLOS: /login.do is unknown there.
        t = FakeTransport(self.script(**{"POST /carlos/login.do": ok("", 404)}))
        with self.assertRaisesRegex(ep.StepError, "unexpected reply HTTP 404"):
            self.session(t).login()
        # carlos flavour against an OSCAR 19: LoginFilter bounces /login to logout.jsp.
        bounced = ep.HttpResponse(302, {"Location": "/carlos/logout.jsp"}, b"")
        self.assertIn("check [carlos] flavour", ep.CarlosSession._explain_login_failure(bounced))


class LiveOscar19Test(LiveServersTest):
    """Every LiveServersTest scenario again, against the OSCAR 19 stand-in,
    with the product name forced to OSCAR."""

    carlos_handler = FakeOscar19Handler
    flavour = "oscar19"
    product = "OSCAR"

    def test_full_pipeline_over_tls(self):
        super().test_full_pipeline_over_tls()
        upload = self.carlos.log[1]
        self.assertIsNone(upload[3], "no CSRF header must reach OSCAR 19")
        self.assertEqual(upload[4], "/carlos/lab/newLabUpload")  # the fake stripped .do
        self.assertTrue(all(c[3] == ep.USER_AGENT_OSCAR19_SCRIPT for c in self.excelleris.log))

    def test_retry_after_failed_import(self):
        """OSCAR 19: the checksum was recorded before the failed parse, so the
        retry's 409 proves nothing; the file goes to failed/ for a person."""
        self.carlos.fail_next_import = True
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)
        self.excelleris.next_pull = b"<HL7Messages/>"
        self.assertEqual(ep.run(self.cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])
        self.assertEqual(list(self.cfg.done_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.failed_dir.glob("*.xml"))), 1)

    def test_carlos_flavour_against_oscar19_fails_loudly(self):
        text = self.conf.read_text().replace("flavour = oscar19", "flavour = carlos")
        self.conf.write_text(text)
        cfg = ep.load_config(self.conf)
        self.assertEqual(ep.run(cfg, ep.RunOptions()), ep.EXIT_FAILED)
        self.assertEqual([c[0] for c in self.carlos.log], [])  # never got past LoginFilter
        self.assertEqual(len(list(cfg.inbox_dir.glob("*.xml"))), 1)  # pull kept for retry


class TransportErrorClassificationTest(unittest.TestCase):
    def test_http_client_failures_are_transport_errors(self):
        import http.client

        t = ep.HttpTransport(timeout=5)
        for exc in (
            http.client.BadStatusLine("garbage"),
            http.client.IncompleteRead(b"partial"),
            http.client.RemoteDisconnected("closed"),
            ConnectionResetError(),
            TimeoutError(),
        ):
            t._opener.open = lambda *a, exc=exc, **k: (_ for _ in ()).throw(exc)
            with self.assertRaises(ep.TransportError, msg=type(exc).__name__):
                t.request("GET", "https://example.invalid/x?Password=secret")
            try:
                t.request("GET", "https://example.invalid/x?Password=secret")
            except ep.TransportError as caught:
                self.assertNotIn("secret", str(caught))  # query never leaks into the error


KEY_PAIR_TEMPLATE = (
    "-------- Service Name --------\n{service}\n------------------------------\n"
    "----- Client Private Key -----\n{priv}\n------------------------------\n"
    "------ Oscar Public Key ------\n{pub}\n------------------------------"
)


class CredentialRulesTest(TempEnv):
    def test_carlos_requires_all_three(self):
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, username="", cpassword="", pin="")
        with self.assertRaisesRegex(ep.ConfigError, "required for flavour=carlos"):
            ep.load_config(self.conf)

    def test_oscar19_credentials_optional_but_all_or_none(self):
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, username="", cpassword="", pin="", extra_carlos="flavour = oscar19")
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.carlos_username, "")
        self.write_conf(
            c, srv, username="svc", cpassword="", pin="", extra_carlos="flavour = oscar19"
        )
        with self.assertRaisesRegex(ep.ConfigError, "together, or none"):
            ep.load_config(self.conf)


class KeyPairFileTest(TempEnv):
    def test_reads_the_create_key_download(self):
        _, _, c, srv = make_keys()
        kp = self.tmp / "keyPair.key"
        kp.write_text(KEY_PAIR_TEMPLATE.format(service="mulesvc", priv=c, pub=srv))
        kp.chmod(0o600)
        self.assertEqual(ep.read_key_pair_file(kp), ("mulesvc", c, srv))
        # Config: service comes from the file, keys load, envelope builds.
        self.write_conf("", "", service="", extra_carlos=f"key_pair_file = {kp}")
        text = (
            self.conf.read_text()
            .replace("client_private_key = \n", "")
            .replace("server_public_key = \n", "")
        )
        self.conf.write_text(text)
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.carlos_service, "mulesvc")
        ep.LabUploadEnvelope(cfg.client_private_key, cfg.server_public_key)

    def test_rejects_combination_and_malformed(self):
        _, _, c, srv = make_keys()
        kp = self.tmp / "keyPair.key"
        kp.write_text(KEY_PAIR_TEMPLATE.format(service="x", priv=c, pub=srv))
        kp.chmod(0o600)
        self.write_conf(c, srv, extra_carlos=f"key_pair_file = {kp}")
        with self.assertRaisesRegex(ep.ConfigError, "cannot be combined"):
            ep.load_config(self.conf)
        kp.write_text("not a key pair file")
        with self.assertRaisesRegex(ep.ConfigError, "no 'Service Name' section"):
            ep.read_key_pair_file(kp)


class SessionlessOscar19Test(Oscar19SessionTest):
    def setUp(self):
        TempEnv.setUp(self)
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, username="", cpassword="", pin="", extra_carlos="flavour = oscar19")
        self.cfg = ep.load_config(self.conf)

    def test_routes_and_no_csrf(self):
        # Mule-identical: no login, no logout, just the signed upload.
        t = FakeTransport(self.script())
        f = ep.Archive(self.cfg).save_inbox("r", PULL_WITH_RESULTS)
        with self.session(t) as s:
            self.assertTrue(s.session_less)
            self.assertTrue(s.upload(f).accepted)
        labels = [FakeTransport.label(m, u) for m, u, _, _ in t.calls]
        self.assertEqual(labels, ["POST /carlos/lab/newLabUpload.do"])

    def test_wrong_flavour_is_explained(self):
        self.skipTest("login is not performed in session-less mode")


class OutcomeBodyTest(unittest.TestCase):
    """What counts as an upload result. A 200 is success only with an empty
    body (sendError with use_http_response_code) or <outcome>uploaded</outcome>
    (uploadComplete.jsp); the HTML page Struts renders when the multipart layer
    refuses the request also comes with HTTP 200 and must never be archived."""

    def body(self, outcome):
        return f'<?xml version="1.0"?><root><outcome>{outcome}</outcome><audit>success</audit></root>'.encode()

    def classify(self, status, body):
        return ep.CarlosSession._classify_reply(ep.HttpResponse(status, {}, body))[0]

    def test_outcome_xml_maps_to_status(self):
        for text, status in (
            ("uploaded", 200),
            ("uploaded previously", 409),
            ("validation failed", 406),
            ("failed to validate", 406),
            ("upload failed", 500),
            ("exception", 500),
        ):
            self.assertEqual(self.classify(200, self.body(text)), status, text)
        self.assertEqual(self.classify(200, self.body("something new")), 0)

    def test_bare_200_needs_an_empty_body(self):
        self.assertEqual(self.classify(200, b""), 200)
        self.assertEqual(self.classify(200, b"  \r\n"), 200)
        html = b"<html><body>Upload rejected: file too large</body></html>"
        status, detail = ep.CarlosSession._classify_reply(ep.HttpResponse(200, {}, html))
        self.assertEqual(status, 0)
        self.assertTrue(ep.UploadOutcome(status, detail).transient)
        self.assertIn("instead of an upload result", detail)

    def test_non_200_passes_through(self):
        self.assertEqual(self.classify(409, self.body("uploaded")), 409)
        self.assertEqual(self.classify(503, b"<html>maintenance</html>"), 503)


class LiveOscar19AsCarlosTest(LiveOscar19Test):
    """OSCAR 19 routes with the CARLOS script's User-Agent: the product
    setting is independent of the flavour on the wire, not only in config."""

    product = "CARLOS"

    def test_full_pipeline_over_tls(self):
        LiveServersTest.test_full_pipeline_over_tls(self)
        upload = self.carlos.log[1]
        self.assertIsNone(upload[3], "no CSRF header must reach OSCAR 19")
        self.assertEqual(upload[4], "/carlos/lab/newLabUpload")  # .do route, stripped by the fake
        self.assertTrue(all(c[3] == ep.USER_AGENT_CARLOS_SCRIPT for c in self.excelleris.log))


class LiveOscar19SessionlessTest(LiveOscar19Test):
    """The OSCAR 19 live run with no EMR credentials at all, as Mule ran, and
    with the default (CARLOS) User-Agent."""

    product = "CARLOS"

    def setUp(self):
        super().setUp()
        text = self.conf.read_text()
        for key in (f"username = {EMR_USER}", f"password = {EMR_PASSWORD}", f"pin = {EMR_PIN}"):
            assert key in text, key
            text = text.replace(key, key.split(" =")[0] + " =")
        self.conf.write_text(text)
        self.cfg = ep.load_config(self.conf)

    def test_full_pipeline_over_tls(self):
        rc = ep.run(self.cfg, ep.RunOptions())
        self.assertEqual(rc, ep.EXIT_OK)
        self.assertEqual([c[0] for c in self.carlos.log], ["upload"])  # no login, no logout
        self.assertEqual(self.carlos.seen, [PULL_WITH_RESULTS])
        self.assertEqual(self.excelleris.acks, ["Positive"])
        self.assertTrue(all(c[3] == ep.USER_AGENT_CARLOS_SCRIPT for c in self.excelleris.log))

    def test_dry_run_live(self):
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(dry_run=True)), ep.EXIT_OK)
        self.assertEqual([c[0] for c in self.carlos.log], [])  # nothing to prove without a login

    def test_wrong_carlos_credentials_keep_pull_for_retry(self):
        self.skipTest("no credentials in session-less mode")

    def test_carlos_flavour_against_oscar19_fails_loudly(self):
        self.skipTest("covered by LiveOscar19Test")


class _RawRecorder:
    """A TLS server that keeps the raw request head of every connection and
    answers like Excelleris, so two clients can be compared byte for byte."""

    def __init__(self, ctx: ssl.SSLContext):
        self.heads: list[bytes] = []
        self.sock = socket.socket()
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(8)
        self.port = self.sock.getsockname()[1]
        self.ctx = ctx
        threading.Thread(target=self._serve, daemon=True).start()

    @staticmethod
    def _reply(head: bytes) -> bytes:
        target = head.split(b" ", 2)[1].decode()
        q = _up.parse_qs(_up.urlsplit(target).query)
        if q.get("Page") == ["Login"]:
            body, extra = ep._AUTH_GRANTED.encode(), b"Set-Cookie: ASP.NET_SessionId=s1; path=/\r\n"
        elif "ACK" in q:
            body, extra = b'<HL7Messages ReturnCode="0"/>', b""
        elif "Logout" in q:
            body, extra = b"", b""
        else:
            body, extra = b"<HL7Messages/>", b""
        return (
            b"HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nConnection: close\r\n"
            + b"Content-Length: %d\r\n" % len(body)
            + extra
            + b"\r\n"
            + body
        )

    def _serve(self):
        while True:
            try:
                conn, _ = self.sock.accept()
            except OSError:
                return
            try:
                tls = self.ctx.wrap_socket(conn, server_side=True)
                data = b""
                while b"\r\n\r\n" not in data:
                    chunk = tls.recv(4096)
                    if not chunk:
                        break
                    data += chunk
                self.heads.append(data.split(b"\r\n\r\n", 1)[0])
                tls.sendall(self._reply(data))
                tls.close()
            except OSError:
                conn.close()

    def close(self):
        self.sock.close()


@unittest.skipUnless(shutil.which("curl"), "curl not installed")
class ShellScriptWireParityTest(TempEnv):
    """The Excelleris requests are the shell script's requests. Run curl with
    ExcellerisDownload.sh's exact flags and the tool's real transport against
    one recording server and compare the request heads."""

    def setUp(self):
        super().setUp()
        self._env = {k: os.environ.get(k) for k in ("NO_PROXY", "no_proxy")}
        os.environ["NO_PROXY"] = os.environ["no_proxy"] = "localhost,127.0.0.1"
        server_pem, self.ca_pem, client_pem = _tls_material(self.tmp, self.pfx)
        self.recorder = _RawRecorder(_excelleris_server_context(server_pem, client_pem))
        self.url = f"https://localhost:{self.recorder.port}/hl7pull.aspx"

    def tearDown(self):
        self.recorder.close()
        for k, v in self._env.items():
            if v is None:
                os.environ.pop(k, None)
            else:
                os.environ[k] = v
        super().tearDown()

    @staticmethod
    def _headers(head: bytes) -> dict[str, str]:
        lines = head.decode().split("\r\n")[1:]
        return {k.strip().lower(): v.strip() for k, v in (line.split(":", 1) for line in lines)}

    def test_requests_match_the_shell_script_byte_for_byte(self):
        _, _, c, srv = make_keys()
        # A password without reserved characters: the script sent the value raw,
        # the tool URL-encodes it, so only plain values can be compared.
        self.write_conf(c, srv, url=self.url, password="clinicpass")
        text = self.conf.read_text().replace(
            "[excelleris]\n", f"[excelleris]\nca_file = {self.ca_pem}\n", 1
        )
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
        cfg = ep.load_config(self.conf)
        with ep.ClientCertificate.from_config(cfg) as cert:
            transport = ep.default_transport(
                cfg.excelleris_timeout, cert.ssl_context(cfg.excelleris_ca_file), True
            )
            with ep.ExcellerisSession(cfg, transport) as session:
                session.pull()
                session.ack(False)
        tool = self.recorder.heads[:]
        del self.recorder.heads[:]

        # ExcellerisDownload.sh, step by step. --cacert and --noproxy only make the
        # stand-in reachable; they add nothing to the request.
        jar = self.tmp / "cookie.txt"
        common = [
            "curl", "-s", "-S", "-G", "-L", "-A", ep.USER_AGENT_CARLOS_SCRIPT,
            "--cert-type", "P12", "--cert", f"{self.pfx}:pfx-secret",
            "--cacert", str(self.ca_pem), "--noproxy", "*",
        ]  # fmt: skip
        user, password = cfg.excelleris_user_id, cfg.excelleris_password
        steps = [
            ["--cookie-jar", str(jar), "--data", f"Page=Login&Mode=Silent&UserID={user}&Password={password}"],
            ["--cookie", str(jar), "--data", "Page=HL7&Query=NewRequests&Pending=Yes"],
            ["--cookie", str(jar), "--data", "Page=HL7&ACK=Negative"],
            ["--cookie", str(jar), "--data", "Logout=Yes"],
        ]  # fmt: skip
        for extra in steps:
            run = subprocess.run(common + extra + [self.url], capture_output=True, timeout=30)
            self.assertEqual(run.returncode, 0, run.stderr.decode())
        script = self.recorder.heads[:]

        self.assertEqual(len(tool), 4)
        self.assertEqual(len(script), 4)
        for ours, theirs in zip(tool, script):
            # Request line: method, path and query string, parameter order included.
            self.assertEqual(ours.split(b"\r\n", 1)[0], theirs.split(b"\r\n", 1)[0])
            h_ours, h_theirs = self._headers(ours), self._headers(theirs)
            for name in ("host", "user-agent", "accept", "cookie"):
                self.assertEqual(h_ours.get(name), h_theirs.get(name), name)
            # urllib's connection handling adds exactly these two; curl adds none.
            self.assertEqual(set(h_ours) - set(h_theirs), {"accept-encoding", "connection"})
            self.assertEqual(h_ours["accept-encoding"], "identity")
            self.assertEqual(h_ours["connection"], "close")
            self.assertEqual(set(h_theirs) - set(h_ours), set())


def _pem_pair_from_pfx(pfx: Path, password: bytes, out_dir: Path):
    """What GoFetchRover's extract step leaves behind: an unencrypted key PEM
    and a certificate PEM, produced here with cryptography instead of openssl."""
    key, cert, extra = pkcs12.load_key_and_certificates(pfx.read_bytes(), password)
    key_pem = out_dir / "client_key.pem"
    cert_pem = out_dir / "client_certificate.pem"
    key_pem.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.TraditionalOpenSSL,
            serialization.NoEncryption(),
        )
    )
    key_pem.chmod(0o600)
    cert_pem.write_bytes(
        cert.public_bytes(serialization.Encoding.PEM)
        + b"".join(c.public_bytes(serialization.Encoding.PEM) for c in extra or [])
    )
    return cert_pem, key_pem


def _use_pem_pair(conf: Path, cert_pem: Path, key_pem: Path) -> None:
    text = conf.read_text()
    lines = [ln for ln in text.splitlines() if not ln.startswith(("pfx_file =", "pfx_password ="))]
    text = "\n".join(lines) + "\n"
    text = text.replace(
        "[excelleris]\n",
        f"[excelleris]\nclient_cert_file = {cert_pem}\nclient_key_file = {key_pem}\n",
        1,
    )
    conf.write_text(text)


class PemPairTest(TempEnv):
    def test_pem_pair_replaces_pfx(self):
        cert_pem, key_pem = _pem_pair_from_pfx(self.pfx, b"pfx-secret", self.tmp)
        _use_pem_pair(self.conf, cert_pem, key_pem)
        cfg = ep.load_config(self.conf)
        self.assertIsNone(cfg.pfx_file)
        with ep.ClientCertificate.from_config(cfg) as cert:
            self.assertIn(b"BEGIN PRIVATE KEY", cert.pem_path.read_bytes())
            self.assertIn(b"BEGIN CERTIFICATE", cert.pem_path.read_bytes())
            cert.ssl_context()
        self.assertEqual(ep.check_config(cfg), ep.EXIT_OK)

    def test_rules(self):
        cert_pem, key_pem = _pem_pair_from_pfx(self.pfx, b"pfx-secret", self.tmp)
        _, _, c, srv = make_keys()
        # both forms at once
        self.write_conf(
            c, srv, extra_excelleris=f"client_cert_file = {cert_pem}\nclient_key_file = {key_pem}"
        )
        with self.assertRaisesRegex(ep.ConfigError, "not both"):
            ep.load_config(self.conf)
        # key must be private (start from a clean config, then switch it to the pair)
        self.write_conf(c, srv)
        _use_pem_pair(self.conf, cert_pem, key_pem)
        key_pem.chmod(0o644)
        with self.assertRaisesRegex(ep.ConfigError, "client_key_file"):
            ep.load_config(self.conf)
        key_pem.chmod(0o600)
        # neither form
        text = "\n".join(
            ln
            for ln in self.conf.read_text().splitlines()
            if not ln.startswith(("client_cert_file", "client_key_file"))
        )
        self.conf.write_text(text + "\n")
        with self.assertRaisesRegex(ep.ConfigError, "is required"):
            ep.load_config(self.conf)

    def test_encrypted_key_is_explained(self):
        cert_pem, key_pem = _pem_pair_from_pfx(self.pfx, b"pfx-secret", self.tmp)
        key, _, _ = pkcs12.load_key_and_certificates(self.pfx.read_bytes(), b"pfx-secret")
        key_pem.write_bytes(
            key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.BestAvailableEncryption(b"x"),
            )
        )
        _use_pem_pair(self.conf, cert_pem, key_pem)
        cfg = ep.load_config(self.conf)
        with self.assertRaisesRegex(ep.ConfigError, "passphrase-protected"):
            with ep.ClientCertificate.from_config(cfg):
                pass


class UserAgentOverrideTest(TempEnv):
    GFR = "Mozilla/5.0 (Windows NT 6.2; GoFetchRover; 1.0.0-alpha) Gecko/20100101 Firefox/113.0"

    def test_override_is_sent_verbatim(self):
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, extra_excelleris=f"user_agent = {self.GFR}")
        cfg = ep.load_config(self.conf)
        self.assertEqual(cfg.excelleris_user_agent, self.GFR)
        t = FakeTransport({"excelleris:login": ok(ep._AUTH_GRANTED), "excelleris:logout": ok("")})
        with ep.ExcellerisSession(cfg, t):
            pass
        self.assertEqual(t.calls[0][2]["User-Agent"], self.GFR)

    def test_default_and_validation(self):
        self.assertEqual(self.cfg.excelleris_user_agent, ep.user_agent("CARLOS"))
        _, _, c, srv = make_keys()
        self.write_conf(c, srv, extra_excelleris="user_agent = bad\tagent")
        with self.assertRaisesRegex(ep.ConfigError, "user_agent must be"):
            ep.load_config(self.conf)


class LivePemPairTest(LiveServersTest):
    """The CARLOS live run again with the client certificate supplied as the
    extracted PEM pair instead of the PFX."""

    def setUp(self):
        super().setUp()
        cert_pem, key_pem = _pem_pair_from_pfx(self.pfx, b"pfx-secret", self.tmp)
        _use_pem_pair(self.conf, cert_pem, key_pem)
        self.cfg = ep.load_config(self.conf)


class RetryClassificationTest(_OrchestrationBase):
    """Transient EMR failures keep the pull in the inbox, up to a cap."""

    def test_transient_500_stays_in_inbox_until_the_cap(self):
        self.script["POST /carlos/lab/newLabUpload"] = ok("", 500)
        text = self.conf.read_text().replace("[carlos]\n", "[carlos]\nmax_upload_attempts = 3\n", 1)
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
        cfg = ep.load_config(self.conf)
        rc = ep.run(cfg, ep.RunOptions(), self.factory)
        self.assertEqual(rc, ep.EXIT_FAILED)
        self.assertEqual(len(list(cfg.inbox_dir.glob("*.xml"))), 1)  # kept
        self.assertEqual(len(list(cfg.inbox_dir.glob("*.attempts"))), 1)
        self.assertEqual(list(cfg.failed_dir.glob("*")), [])
        # Two more runs without a new pull: attempts 2 and 3; the third gives up.
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        ep.run(cfg, ep.RunOptions(), self.factory)
        self.assertEqual(len(list(cfg.inbox_dir.glob("*.xml"))), 1)
        ep.run(cfg, ep.RunOptions(), self.factory)
        self.assertEqual(list(cfg.inbox_dir.glob("*")), [])  # sidecar removed too
        self.assertEqual(len(list(cfg.failed_dir.glob("*.xml"))), 1)

    def test_recovery_clears_the_attempt_count(self):
        self.script["POST /carlos/lab/newLabUpload"] = ok("", 503)
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.script["POST /carlos/lab/newLabUpload"] = ok("", 200)
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_OK)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 1)

    def test_html_200_is_not_archived(self):
        self.script["POST /carlos/lab/newLabUpload"] = ok("<html><body>rejected</body></html>", 200)
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)
        self.assertEqual(list(self.cfg.done_dir.glob("*")), [])


def _replies(*responses):
    """A scripted handler answering each call with the next item; an exception
    instance is raised instead of returned."""
    queue = list(responses)

    def handler(_m, _u, _h, _b):
        item = queue.pop(0)
        if isinstance(item, Exception):
            raise item
        return item

    return handler


class Oscar19DuplicateAfterFailureTest(_OrchestrationBase):
    """OSCAR 19 records the checksum before it parses: a 409 that follows a
    failed attempt must not be archived as imported."""

    def setUp(self):
        super().setUp()
        text = self.conf.read_text().replace("[carlos]\n", "[carlos]\nflavour = oscar19\n", 1)
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
        self.cfg = ep.load_config(self.conf)
        self.script["POST /carlos/login.do"] = ok('{"success":true}')
        self.script["GET /carlos/logout.jsp"] = ok("")

    def test_409_after_a_500_goes_to_failed(self):
        self.script["POST /carlos/lab/newLabUpload.do"] = _replies(ok("", 500), ok("", 409))
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.xml"))), 1)
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*")), [])  # sidecar gone too
        self.assertEqual(list(self.cfg.done_dir.glob("*")), [])
        self.assertEqual(len(list(self.cfg.failed_dir.glob("*.xml"))), 1)

    def test_409_after_a_transport_error_goes_to_failed(self):
        # The upload request may have reached the EMR before the connection died.
        self.script["POST /carlos/lab/newLabUpload.do"] = _replies(
            ep.TransportError("connection reset"), ok("", 409)
        )
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.inbox_dir.glob("*.attempts"))), 1)
        self.assertEqual(list(self.cfg.inbox_dir.glob("*.tmp")), [])
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.assertEqual(len(list(self.cfg.failed_dir.glob("*.xml"))), 1)

    def test_plain_409_is_still_a_duplicate(self):
        self.script["POST /carlos/lab/newLabUpload.do"] = ok("", 409)
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_OK)
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 1)


class CarlosDuplicateAfterFailureTest(_OrchestrationBase):
    def test_409_after_a_500_is_an_import_on_carlos(self):
        # storeIfNew commits the checksum with the import, so a 409 is proof.
        self.script["POST /carlos/lab/newLabUpload"] = _replies(ok("", 500), ok("", 409))
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_FAILED)
        self.script["excelleris:pull"] = ok("<HL7Messages/>")
        self.assertEqual(ep.run(self.cfg, ep.RunOptions(), self.factory), ep.EXIT_OK)
        self.assertEqual(len(list(self.cfg.done_dir.glob("*.xz"))), 1)


class AlertHeaderTest(TempEnv):
    def test_line_breaks_in_alert_addresses_are_rejected(self):
        text = self.conf.read_text().replace(
            "[alerts]\nemail = \n",
            "[alerts]\nemail = it@example.test\n  Bcc: other@example.test\n",
            1,
        )
        assert "Bcc" in text
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
        with self.assertRaisesRegex(ep.ConfigError, "control characters"):
            ep.load_config(self.conf)

    def test_notifier_never_raises(self):
        # Even a header EmailMessage refuses must not turn the alert into a crash.
        cfg = dataclasses.replace(
            self.cfg, alert_email="it@example.test\nBcc: x", sendmail="/bin/true"
        )
        ep.Notifier(cfg).failure("run", "step", "detail")


class TrustAnchorTest(TempEnv):
    def test_ca_file_must_not_be_writable_by_others(self):
        ca = self.tmp / "ca.pem"
        ca.write_text("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n")
        ca.chmod(0o666)
        text = self.conf.read_text().replace("[carlos]\n", f"[carlos]\nca_file = {ca}\n", 1)
        self.conf.write_text(
            text
        )  # codeql[py/clear-text-storage-sensitive-data]: fixture placeholders
        with self.assertRaisesRegex(ep.ConfigError, "writable by group/other"):
            ep.load_config(self.conf)
        ca.chmod(0o644)  # world-readable is fine for a trust anchor
        self.assertEqual(ep.load_config(self.conf).carlos_ca_file, ca)


class CertificateCleanupTest(TempEnv):
    def test_failed_enter_leaves_no_temp_directory(self):
        cert = ep.ClientCertificate.from_config(self.cfg)
        original = ep.tempfile.mkdtemp
        made = []

        def mkdtemp(**kw):
            d = original(**kw)
            made.append(d)
            return d

        ep.tempfile.mkdtemp = mkdtemp
        try:
            cert.pfx_file = self.pfx  # fine
            # Force the PEM write to fail by making the temp dir read-only.
            real_open = ep.os.open

            def failing_open(path, *a, **k):
                if str(path).endswith("client.pem"):
                    raise OSError("disk full")
                return real_open(path, *a, **k)

            ep.os.open = failing_open
            try:
                with self.assertRaises(OSError):
                    cert.__enter__()
            finally:
                ep.os.open = real_open
        finally:
            ep.tempfile.mkdtemp = original
        self.assertEqual(len(made), 1)
        self.assertFalse(Path(made[0]).exists())
        self.assertIsNone(cert.pem_path)

    def test_cleanup_failure_is_an_alert_not_a_footnote(self):
        cert = ep.ClientCertificate.from_config(self.cfg)
        cert.__enter__()
        tmpdir = cert.pem_path.parent
        original = ep.shutil.rmtree

        def broken(path, onerror=None, onexc=None, **kw):
            # rmtree passes onexc from 3.12, onerror before.
            (onexc or onerror)(None, str(path), None)

        ep.shutil.rmtree = broken
        try:
            with self.assertRaisesRegex(ep.StepError, "could not be removed"):
                cert.__exit__(None, None, None)
        finally:
            ep.shutil.rmtree = original
            original(tmpdir, ignore_errors=True)


class PemBundleTest(unittest.TestCase):
    def test_loads_every_block_without_the_new_api(self):
        _, c1 = _self_signed("one")
        _, c2 = _self_signed("two")
        bundle = (
            c1.public_bytes(serialization.Encoding.PEM)
            + b"junk\n"
            + c2.public_bytes(serialization.Encoding.PEM)
        )
        certs = ep.load_pem_certificates(bundle)
        self.assertEqual([c.subject for c in certs], [c1.subject, c2.subject])
        with self.assertRaises(ValueError):
            ep.load_pem_certificates(b"not a bundle")


class DryRunExclusivityTest(unittest.TestCase):
    def test_dry_run_cannot_combine_with_partial_modes(self):
        for extra in ("--no-upload", "--upload-only"):
            with self.assertRaises(SystemExit):
                ep.parse_args(["--config", "x", "--dry-run", extra])


if __name__ == "__main__":
    unittest.main()
