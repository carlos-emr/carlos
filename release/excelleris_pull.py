#!/usr/bin/env python3
# excelleris_pull.py
#
# Copyright (C) 2026 CARLOS Contributors
#
# This program is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the
# Free Software Foundation; either version 2 of the License, or (at your
# option) any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General
# Public License for more details.
#
# Lineage: functional replacement for release/ExcellerisDownload.sh (Peter
# Hutten-Czapski, after Tom Le and Muki) and for the Mule 1.3.3 +
# hl7_file_management bridge that script handed its files to.
"""
Pull lab results from Excelleris and upload them straight into CARLOS EMR.

WHAT THIS DOES
==============

One run, normally started by cron or a systemd timer, performs:

  1. Upload step (retry):  any file still waiting in <state_dir>/inbox from an
     earlier run is uploaded to CARLOS first, so a CARLOS outage never loses a
     pull that Excelleris has already been told we received.
  2. Pull step:            log in to the Excelleris HL7 pull endpoint with the
     clinic's client certificate, pull pending results, write them to the
     inbox (fsync'd, mode 0600), and only THEN send Excelleris a positive
     acknowledgment. Anything else (empty pull, error body, failed write) gets
     a negative acknowledgment so Excelleris keeps the results pending.
  3. Upload step:          upload the new pull to CARLOS.
  4. Housekeeping:         compress uploaded files into <state_dir>/done and
     purge those older than the retention window.

The upload uses the lab-upload route CARLOS already exposes for external lab
senders (``/lab/newLabUpload``, handled by ``LabUpload2Action``). That route is
what the retired Mule bridge spoke, so no change to CARLOS is required. CARLOS
checksums every upload and answers 409 for a file it has already imported,
which is what makes the retry in step 1 safe.

Flow per message file:  Excelleris --(mTLS GET)--> inbox/ --(signed, encrypted
multipart POST)--> CARLOS --(200/409)--> done/*.xml.xz

ONE-TIME SETUP IN CARLOS (no code change)
=========================================

  a. Create a dedicated provider login for this tool. Give it ONLY the ``_lab``
     security object with write access. Do not enrol it in MFA and do not give
     it a forced password reset: both would stop a scripted login.
  b. Administration > Key Manager > Create Key. Name = the value you will put in
     ``[carlos] service`` below. Type = "OTHER", then type ``ExcellerisON`` in
     the box (that selects the Excelleris Ontario upload handler; a BC site uses
     the handler its Excelleris feed is parsed with, normally ``PATHL7``).
  c. As an administrator fetch the key pair CARLOS generated:
     ``<base_url>/admin/keygen/getPublicKey?id=<service>`` and copy the
     ``base64EncodedPrivateKey`` value into ``[carlos] client_private_key``.
     (That endpoint writes an audit log entry each time it is read.)
  d. From the Key Manager page copy the server's public key (the long block
     at the top of the page) into ``[carlos] server_public_key``.

RUNNING AGAINST OSCAR 19 INSTEAD
================================

  Set ``[carlos] flavour = oscar19``. OSCAR 19 (Bitbucket oscaremr/oscar,
  master) has the same upload action with the same parameters and crypto; it
  differs only in Struts 1 ``*.do`` routes, a GET ``logout.jsp``, and having
  no CSRF layer, and the flavour switch covers exactly those. Its Key Manager
  is at ``admin/keygen/`` and the private key comes from
  ``admin/keygen/getPublicKey.json?id=<service>``. A site that ran the Mule
  bridge already has a registered service key: reuse that name and type (on
  stock OSCAR 19 the Excelleris type is ``PATHL7``; ``ExcellerisON`` does not
  exist there). ``[excelleris] product`` sets the product name Excelleris sees
  in the User-Agent ("CARLOS" or "OSCAR") independently of the flavour.

ONE-TIME SETUP ON THE HOST
==========================

  - Install ``python3-cryptography`` (Debian/Ubuntu). Everything else is the
    Python standard library.
  - Create a service user, e.g. ``carlos-excelleris``, and run this tool as
    that user, never as root.
  - Put the config file (see ``excelleris_pull.conf.example``) somewhere only
    that user can read, mode 0600. The tool refuses to start otherwise.
  - Put the Excelleris PFX next to it, also mode 0600.
  - ``excelleris_pull.py --config /etc/carlos-excelleris/pull.conf --check-config``
    validates the file, loads the keys and the PFX, and prints the config with
    secrets masked, without touching the network.
  - ``--dry-run`` logs in and out of both Excelleris and CARLOS without pulling
    or uploading, to prove credentials, certificate and network path.
  - Then schedule ``excelleris_pull.py --config ...`` every N minutes.

SECURITY NOTES
==============

  - Excelleris mandates credentials in the query string of a GET. That is
    their protocol, not a choice made here. The URL is built in memory, sent
    over mutual TLS, and never written to a log or shown on a command line.
  - The PFX is unpacked to a PEM in a private temporary directory for the
    lifetime of one run, because Python's ssl module can only load a client
    certificate from a file. The file is 0600 and deleted in a ``finally``.
  - The CARLOS upload envelope (AES-128-ECB payload, RSA PKCS#1 v1.5 wrapped
    key, MD5withRSA signature) is the legacy format ``LabUpload2Action``
    decrypts. It is dated; CARLOS tracks its replacement in issue #3413. It is
    kept in one class (``LabUploadEnvelope``) so it can be swapped later.
  - Lab result files are PHI. Directories are 0700, files 0600, nothing from a
    result file is ever logged, and ``--verbose`` only adds request metadata.

EXIT CODES
==========

  0  success (including "nothing to do")
  1  a step failed; an alert email was sent if one is configured
  2  configuration or usage error
  3  another run still holds the lock (not an error; nothing was done)
"""

from __future__ import annotations

import argparse
import base64
import configparser
import dataclasses
import fcntl
import http.client
import http.cookiejar
import logging
import lzma
import os
import re
import secrets
import shutil
import socket
import ssl
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from email.message import EmailMessage
from pathlib import Path
from typing import Callable, Optional

try:
    from cryptography.exceptions import UnsupportedAlgorithm
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    from cryptography.hazmat.primitives.padding import PKCS7
    from cryptography.hazmat.primitives.serialization import pkcs12
except ImportError:  # pragma: no cover - exercised only on a mis-provisioned host
    sys.stderr.write(
        "excelleris_pull: the 'cryptography' package is required "
        "(apt install python3-cryptography)\n"
    )
    sys.exit(2)

VERSION = "2.0.0"

# Excelleris requires a User-Agent that identifies the destination software.
# Keep the browser-shaped form the guide-conformant OSCAR scripts have always
# sent, with the product and version in the parenthesised comment, so nothing
# on the Excelleris side that pattern-matches the header sees a change. The
# shell script's value was built with "\/" escapes that bash left in verbatim,
# so what it actually sent contained literal backslashes; this one does not.


def user_agent(product: str = "CARLOS") -> str:
    """The header Excelleris sees. ``product`` is what the clinic registered
    with Excelleris as its EMR ("CARLOS" or "OSCAR"); it is configurable so a
    site can keep the name on file with Excelleris regardless of which EMR
    generation this tool is feeding."""
    return f"Mozilla/5.0 (Windows NT 6.2; {product}; {VERSION}) Gecko/20100101 Firefox/32.0"


USER_AGENT = user_agent()

# The two EMR generations this tool can upload to. They share the upload
# action, its parameters and its crypto; they differ only in routing and in
# whether a CSRF token is required. See CarlosSession for the exact routes.
FLAVOUR_CARLOS = "carlos"
FLAVOUR_OSCAR19 = "oscar19"
FLAVOURS = (FLAVOUR_CARLOS, FLAVOUR_OSCAR19)

EXIT_OK = 0
EXIT_FAILED = 1
EXIT_CONFIG = 2
EXIT_LOCKED = 3

# A pull is a bounded XML document; anything larger than this is not a lab
# result file and would only serve to exhaust memory.
MAX_RESPONSE_BYTES = 256 * 1024 * 1024

# CARLOS refuses multipart requests above struts.multipart.maxSize (struts.xml).
CARLOS_MULTIPART_MAX_BYTES = 50 * 1024 * 1024

log = logging.getLogger("excelleris_pull")


# ---------------------------------------------------------------------------
# Errors
# ---------------------------------------------------------------------------


class ConfigError(Exception):
    """The configuration file is missing, unreadable, insecure or incomplete."""


class TransportError(Exception):
    """The network layer failed: DNS, TCP, TLS, timeout, or a truncated body.

    Distinct from an HTTP error status, which the caller interprets itself:
    a 409 from CARLOS is good news and a 403 is a definitive rejection, neither
    of which is a transport problem.
    """


class StepError(Exception):
    """A pipeline step failed in a way that must alert an operator."""

    def __init__(self, step: str, detail: str):
        super().__init__(f"{step}: {detail}")
        self.step = step
        self.detail = detail


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------


@dataclasses.dataclass(frozen=True)
class Config:
    """Validated configuration. Secrets live here and nowhere else."""

    # [excelleris]
    excelleris_context: str
    excelleris_url: str
    excelleris_user_id: str
    excelleris_password: str
    pfx_file: Path
    pfx_password: str
    excelleris_timeout: int
    excelleris_ca_file: Optional[Path]  # extra trust anchor; None = system CA store
    excelleris_product: str  # product name placed in the User-Agent: CARLOS or OSCAR
    # [carlos]
    carlos_base_url: str
    carlos_username: str
    carlos_password: str
    carlos_pin: str
    carlos_service: str
    client_private_key: str  # base64 PKCS#8 DER, as served by admin/keygen/getPublicKey
    server_public_key: str  # base64 X.509 SubjectPublicKeyInfo DER, from the Key Manager page
    carlos_timeout: int
    carlos_ca_file: Optional[Path]  # for a CARLOS behind a private CA; None = system store
    carlos_flavour: str  # FLAVOUR_CARLOS or FLAVOUR_OSCAR19: selects routes and CSRF
    # [paths]
    state_dir: Path
    log_file: Path
    retention_days: int
    # [alerts]
    alert_email: str
    alert_from: str
    sendmail: str

    @property
    def inbox_dir(self) -> Path:
        return self.state_dir / "inbox"

    @property
    def done_dir(self) -> Path:
        return self.state_dir / "done"

    @property
    def failed_dir(self) -> Path:
        return self.state_dir / "failed"

    @property
    def lock_file(self) -> Path:
        return self.state_dir / "run.lock"

    def masked(self) -> dict[str, str]:
        """Config as printable key/value pairs with every secret masked."""
        hidden = {
            "excelleris_password",
            "pfx_password",
            "carlos_password",
            "carlos_pin",
            "client_private_key",
            "server_public_key",
        }
        out = {}
        for field in dataclasses.fields(self):
            value = getattr(self, field.name)
            out[field.name] = "********" if field.name in hidden else str(value)
        return out


def _require_private_file(path: Path, what: str) -> None:
    """Refuse a secret-bearing file that other users could read or alter.

    The shell script sourced its config as root with no such check, which made
    a world-writable config file a root shell. Group/other bits must be clear
    and the owner must be the running user (or root, for a root-owned file
    deployed by configuration management and read by the service user).
    """
    try:
        st = path.stat()
    except FileNotFoundError:
        raise ConfigError(f"{what} not found: {path}") from None
    except OSError as exc:  # permission denied, dangling symlink, I/O error
        raise ConfigError(f"{what} {path}: {exc}") from exc
    if not stat.S_ISREG(st.st_mode):
        raise ConfigError(f"{what} is not a regular file: {path}")
    if st.st_mode & 0o077:
        raise ConfigError(
            f"{what} {path} is readable by group/other (mode {stat.S_IMODE(st.st_mode):04o}); "
            "chmod 600 it"
        )
    if st.st_uid not in (os.geteuid(), 0):
        raise ConfigError(f"{what} {path} is not owned by the running user or root")


def _read_key_material(section: configparser.SectionProxy, key: str) -> str:
    """Accept either ``<key> = <base64>`` inline or ``<key>_file = <path>``.

    Pasting a 1600-character base64 blob into an INI value works but is easy to
    get wrong; a file is friendlier. Whitespace and PEM armour are tolerated
    because operators copy these out of a web page.
    """
    inline = section.get(key, "").strip()
    file_ref = section.get(f"{key}_file", "").strip()
    if inline and file_ref:
        raise ConfigError(f"[{section.name}] set either {key} or {key}_file, not both")
    if file_ref:
        path = Path(file_ref)
        _require_private_file(path, f"[{section.name}] {key}_file")
        try:
            inline = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            raise ConfigError(f"[{section.name}] {key}_file {path}: {exc}") from exc
    if not inline:
        raise ConfigError(f"[{section.name}] {key} (or {key}_file) is required")
    return _strip_key_armour(inline)


def _strip_key_armour(text: str) -> str:
    """Reduce PEM-or-bare-base64 text to one bare base64 string."""
    lines = [ln.strip() for ln in text.strip().splitlines()]
    lines = [ln for ln in lines if ln and not ln.startswith("-----")]
    return "".join(lines)


def load_config(path: Path) -> Config:
    """Parse and validate the INI config; raise ConfigError on any problem.

    Interpolation is off so a password containing ``%`` is taken literally.
    """
    _require_private_file(path, "config file")
    parser = configparser.ConfigParser(interpolation=None)
    try:
        with path.open(encoding="utf-8") as fh:
            parser.read_file(fh)
    except (OSError, configparser.Error) as exc:
        raise ConfigError(f"cannot parse {path}: {exc}") from exc

    def need(section: str, key: str) -> str:
        if not parser.has_section(section):
            raise ConfigError(f"missing [{section}] section")
        value = parser.get(section, key, fallback="").strip()
        if not value:
            raise ConfigError(f"[{section}] {key} is required")
        return value

    def optional(section: str, key: str, default: str) -> str:
        return (
            parser.get(section, key, fallback=default).strip()
            if parser.has_section(section)
            else default
        )

    def positive_int(section: str, key: str, default: str, minimum: int) -> int:
        raw = optional(section, key, default)
        try:
            value = int(raw)
        except ValueError:
            raise ConfigError(f"[{section}] {key} must be an integer, got {raw!r}") from None
        if value < minimum:
            raise ConfigError(f"[{section}] {key} must be >= {minimum}")
        return value

    excelleris_url = need("excelleris", "url")
    carlos_base_url = need("carlos", "base_url").rstrip("/")
    for label, url in (
        ("[excelleris] url", excelleris_url),
        ("[carlos] base_url", carlos_base_url),
    ):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise ConfigError(f"{label} must be an https:// URL")
        if parsed.username or parsed.password:
            raise ConfigError(f"{label} must not embed credentials")

    pin = need("carlos", "pin")
    if not re.fullmatch(r"[0-9]{4}", pin):
        # Login2Action rejects anything else before checking the password, so
        # catch it here where the message can say what is wrong.
        raise ConfigError("[carlos] pin must be exactly four digits")
    username = need("carlos", "username")
    if not re.fullmatch(r"[a-zA-Z0-9]{1,30}", username):
        raise ConfigError("[carlos] username must be 1-30 letters/digits (Login2Action rule)")

    pfx_file = Path(need("excelleris", "pfx_file"))
    _require_private_file(pfx_file, "[excelleris] pfx_file")

    def ca_file(section: str) -> Optional[Path]:
        """Optional PEM bundle to trust in addition to the system store."""
        raw = optional(section, "ca_file", "")
        if not raw:
            return None
        path = Path(raw)
        if not path.is_file():
            raise ConfigError(f"[{section}] ca_file not found: {path}")
        return path

    flavour = optional("carlos", "flavour", FLAVOUR_CARLOS).lower()
    if flavour not in FLAVOURS:
        raise ConfigError(f"[carlos] flavour must be one of {', '.join(FLAVOURS)}; got {flavour!r}")
    # Unless the clinic sets it, the name Excelleris sees follows the EMR
    # generation being fed; an explicit value always wins.
    product = optional("excelleris", "product", "") or (
        "OSCAR" if flavour == FLAVOUR_OSCAR19 else "CARLOS"
    )
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}", product):
        # It is spliced into the User-Agent comment; keep it header-safe.
        raise ConfigError(
            "[excelleris] product must be 1-40 letters, digits, spaces, dots, underscores or dashes"
        )

    cfg = Config(
        excelleris_context=optional("excelleris", "context", ""),
        excelleris_url=excelleris_url,
        excelleris_user_id=need("excelleris", "user_id"),
        excelleris_password=need("excelleris", "password"),
        pfx_file=pfx_file,
        pfx_password=optional("excelleris", "pfx_password", ""),
        excelleris_timeout=positive_int("excelleris", "timeout_seconds", "60", 5),
        excelleris_ca_file=ca_file("excelleris"),
        excelleris_product=product,
        carlos_base_url=carlos_base_url,
        carlos_username=username,
        carlos_password=need("carlos", "password"),
        carlos_pin=pin,
        carlos_service=need("carlos", "service"),
        client_private_key=_read_key_material(parser["carlos"], "client_private_key"),
        server_public_key=_read_key_material(parser["carlos"], "server_public_key"),
        carlos_timeout=positive_int("carlos", "timeout_seconds", "120", 5),
        carlos_ca_file=ca_file("carlos"),
        carlos_flavour=flavour,
        state_dir=Path(need("paths", "state_dir")),
        log_file=Path(need("paths", "log_file")),
        retention_days=positive_int("paths", "retention_days", "90", 0),
        alert_email=optional("alerts", "email", ""),
        alert_from=optional("alerts", "from", f"carlos-excelleris@{socket.gethostname()}"),
        sendmail=optional("alerts", "sendmail", "/usr/sbin/sendmail"),
    )
    if not cfg.state_dir.is_absolute() or not cfg.log_file.is_absolute():
        raise ConfigError("[paths] state_dir and log_file must be absolute paths")
    return cfg


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------


def setup_logging(log_file: Path, verbose: bool) -> None:
    """Log everything to the file; echo only warnings to stderr unless verbose.

    cron mails any output, so keeping stderr quiet on a healthy run is what
    makes a cron mail meaningful. Nothing logged anywhere ever includes result
    content or credentials; see the SECURITY NOTES in the module docstring.
    """
    log_file.parent.mkdir(parents=True, exist_ok=True, mode=0o750)
    fmt = logging.Formatter("%(asctime)s %(levelname)-7s %(message)s", "%Y-%m-%d %H:%M:%S")
    root = logging.getLogger()
    root.setLevel(logging.DEBUG if verbose else logging.INFO)

    file_handler = logging.FileHandler(log_file, encoding="utf-8")
    file_handler.setFormatter(fmt)
    root.addHandler(file_handler)
    os.chmod(log_file, 0o640)

    stderr_handler = logging.StreamHandler(sys.stderr)
    stderr_handler.setFormatter(fmt)
    stderr_handler.setLevel(logging.DEBUG if verbose else logging.WARNING)
    root.addHandler(stderr_handler)


# ---------------------------------------------------------------------------
# Single-instance lock
# ---------------------------------------------------------------------------


class RunLock:
    """Non-blocking exclusive lock so overlapping cron runs cannot double-pull.

    The shell script waited ten seconds for its lock and then emailed "stopped
    working". A legitimately slow previous run is not a failure, so this one
    reports "still running" and exits 3 without an alert.
    """

    def __init__(self, path: Path):
        self.path = path
        self._fd: Optional[int] = None

    def acquire(self) -> bool:
        self._fd = os.open(self.path, os.O_WRONLY | os.O_CREAT, 0o600)
        try:
            fcntl.flock(self._fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(self._fd)
            self._fd = None
            return False
        os.ftruncate(self._fd, 0)
        os.write(self._fd, f"{os.getpid()}\n".encode())
        return True

    def release(self) -> None:
        if self._fd is not None:
            fcntl.flock(self._fd, fcntl.LOCK_UN)
            os.close(self._fd)
            self._fd = None


# ---------------------------------------------------------------------------
# HTTP transport (stdlib only)
# ---------------------------------------------------------------------------


@dataclasses.dataclass
class HttpResponse:
    status: int
    headers: dict[str, str]
    body: bytes

    def text(self) -> str:
        return self.body.decode("utf-8", errors="replace")

    def header(self, name: str) -> str:
        """Case-insensitive header lookup: proxies and HTTP/2 lower-case names."""
        wanted = name.lower()
        for key, value in self.headers.items():
            if key.lower() == wanted:
                return value
        return ""


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Make urllib surface 3xx as a response instead of following it.

    The CARLOS login route answers with a redirect in both the success case
    (to the provider schedule) and most failure cases (forced password reset,
    facility choice, MFA, lockout). The Location header is the diagnosis, so
    it must be returned to the caller rather than followed and lost.
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401
        return None


class HttpTransport:
    """Thin wrapper over urllib with a cookie jar, timeouts and a size cap.

    Both remote sessions are built on this so the unit tests can substitute a
    fake with the same ``request`` signature and never open a socket.
    """

    def __init__(
        self,
        timeout: int,
        ssl_context: Optional[ssl.SSLContext] = None,
        follow_redirects: bool = True,
    ):
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        handlers: list[urllib.request.BaseHandler] = [
            urllib.request.HTTPSHandler(context=ssl_context or ssl.create_default_context()),
            urllib.request.HTTPCookieProcessor(self.cookies),
        ]
        if not follow_redirects:
            handlers.append(_NoRedirect())
        self._opener = urllib.request.build_opener(*handlers)

    def request(
        self,
        method: str,
        url: str,
        headers: Optional[dict[str, str]] = None,
        body: Optional[bytes] = None,
    ) -> HttpResponse:
        req = urllib.request.Request(url, data=body, method=method)
        req.add_header("User-Agent", USER_AGENT)
        for name, value in (headers or {}).items():
            req.add_header(name, value)
        try:
            with self._opener.open(req, timeout=self.timeout) as resp:
                return HttpResponse(resp.status, dict(resp.headers), _read_capped(resp))
        except urllib.error.HTTPError as exc:
            # An HTTP error status (and, with _NoRedirect, a 3xx) is an answer,
            # not a transport failure. HTTPError has no body stream when the
            # server sent none, hence the guard rather than a plain read().
            try:
                body = _read_capped(exc) if getattr(exc, "fp", None) is not None else b""
            finally:
                if getattr(exc, "fp", None) is not None:
                    exc.close()
            return HttpResponse(exc.code, dict(exc.headers or {}), body)
        except (
            urllib.error.URLError,
            OSError,
            TimeoutError,
            http.client.HTTPException,
        ) as exc:
            # URLError wraps socket/TLS failures; OSError covers connection
            # resets and timeouts; HTTPException covers a malformed status
            # line or a body cut off mid-read, which are not OSErrors.
            raise TransportError(f"{method} {_safe_url(url)}: {exc}") from exc


def _read_capped(resp) -> bytes:
    data = resp.read(MAX_RESPONSE_BYTES + 1)
    if len(data) > MAX_RESPONSE_BYTES:
        raise TransportError("response exceeded size cap")
    return data


def _safe_url(url: str) -> str:
    """A URL with its query string removed, safe to put in logs or errors.

    The Excelleris login query carries the password; it must never leak into
    an exception message that ends up in a log or an alert email.
    """
    parts = urllib.parse.urlsplit(url)
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))


# ---------------------------------------------------------------------------
# Client certificate handling
# ---------------------------------------------------------------------------


class ClientCertificate:
    """Unpack the Excelleris PFX into a PEM that ssl can load, for one run.

    Context manager: the PEM lives in a fresh 0700 temp directory as a 0600
    file and is removed on exit, even on failure.
    """

    def __init__(self, pfx_file: Path, pfx_password: str):
        self.pfx_file = pfx_file
        self.pfx_password = pfx_password
        self._tmpdir: Optional[str] = None
        self.pem_path: Optional[Path] = None

    def __enter__(self) -> "ClientCertificate":
        try:
            raw = self.pfx_file.read_bytes()
        except OSError as exc:
            raise ConfigError(f"cannot read PFX {self.pfx_file}: {exc}") from exc
        password = self.pfx_password.encode("utf-8") if self.pfx_password else None
        try:
            key, cert, extra = pkcs12.load_key_and_certificates(raw, password)
        except UnsupportedAlgorithm as exc:
            # PFX files from older Windows tooling are often sealed with RC2-40
            # or similar ciphers that OpenSSL 3 only serves from its "legacy"
            # provider. Re-exporting once fixes it without touching the key.
            raise ConfigError(
                f"PFX {self.pfx_file} uses a cipher this OpenSSL does not enable ({exc}); "
                "re-export it with: openssl pkcs12 -legacy -in old.pfx -nodes | "
                "openssl pkcs12 -export -out new.pfx"
            ) from exc
        except ValueError as exc:
            raise ConfigError(
                f"cannot open PFX {self.pfx_file}: {exc} (wrong passphrase, or a legacy "
                "cipher: see the re-export hint in --help/docstring)"
            ) from exc
        if key is None or cert is None:
            raise ConfigError(f"PFX {self.pfx_file} does not contain both a key and a certificate")
        pem = key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
        pem += cert.public_bytes(serialization.Encoding.PEM)
        for chain_cert in extra or []:
            pem += chain_cert.public_bytes(serialization.Encoding.PEM)

        self._tmpdir = tempfile.mkdtemp(prefix="excelleris-cert-")  # mkdtemp is 0700
        self.pem_path = Path(self._tmpdir) / "client.pem"
        fd = os.open(self.pem_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as fh:
            fh.write(pem)
        return self

    def __exit__(self, *_exc) -> None:
        if self._tmpdir:
            shutil.rmtree(self._tmpdir, ignore_errors=True)
            self._tmpdir = None
            self.pem_path = None

    def ssl_context(self, ca_file: Optional[Path] = None) -> ssl.SSLContext:
        ctx = server_verifying_context(ca_file)
        ctx.load_cert_chain(certfile=str(self.pem_path))
        return ctx


def server_verifying_context(ca_file: Optional[Path] = None) -> ssl.SSLContext:
    """A context that verifies the peer (no "-k"), optionally trusting one
    extra PEM bundle on top of the system store for sites behind a private CA."""
    ctx = ssl.create_default_context()
    if ca_file is not None:
        ctx.load_verify_locations(cafile=str(ca_file))
    return ctx


# ---------------------------------------------------------------------------
# Excelleris side
# ---------------------------------------------------------------------------

# Request shapes from the Excelleris EMR Interface Guide, as the shell script
# used them. All four are GETs against one endpoint; the Page/Query parameters
# select the operation and the session cookie carries authentication.
_AUTH_GRANTED = "<Authentication>AccessGranted</Authentication>"
_AUTH_DENIED = "<Authentication>AccessDenied</Authentication>"


class ExcellerisAuthError(StepError):
    pass


@dataclasses.dataclass(frozen=True)
class PullSummary:
    """What a pull body turned out to be, without retaining any of it."""

    message_count: int
    return_code: Optional[str]  # set when Excelleris answered with an error document
    problem: Optional[str]  # set when the body is not an HL7Messages document at all

    @property
    def has_results(self) -> bool:
        return self.message_count > 0 and self.return_code is None and self.problem is None


def inspect_pull(body: bytes) -> PullSummary:
    """Classify a pull response by parsing it, not by grepping its first line.

    The shell script looked for ``<Message `` on line one, which is only true
    while Excelleris emits single-line XML. Parsing finds the elements wherever
    the line breaks fall and also recognises an error document.

    ElementTree is used because the input arrives over mutual TLS from one
    known peer and the parser does not resolve external entities; the
    cross-check against ``MessageCount`` is logged, never trusted over the
    actual element count.
    """
    try:
        root = ET.fromstring(body)
    except ET.ParseError as exc:
        return PullSummary(0, None, f"not well-formed XML: {exc}")
    if root.tag != "HL7Messages":
        return PullSummary(0, None, f"unexpected root element <{root.tag}>")
    return_code = root.get("ReturnCode")
    if return_code not in (None, "0"):
        return PullSummary(0, return_code, None)
    count = sum(1 for child in root if child.tag == "Message")
    declared = root.get("MessageCount")
    if declared is not None and declared.isdigit() and int(declared) != count:
        log.warning(
            "pull declares MessageCount=%s but contains %d Message elements", declared, count
        )
    return PullSummary(count, None, None)


class ExcellerisSession:
    """Login / pull / ack / logout against the Excelleris HL7 pull endpoint.

    ``transport`` is anything with ``request(method, url, headers, body)``.
    Use as a context manager: ``__enter__`` logs in, ``__exit__`` logs out on
    a best-effort basis so a failure mid-run still releases the remote session.
    """

    def __init__(self, cfg: Config, transport):
        self.cfg = cfg
        self.transport = transport

    def _get(self, params: dict[str, str], what: str) -> HttpResponse:
        # urlencode handles the characters the shell script did not (& + % #
        # and spaces in a password broke it). The query is never logged.
        url = f"{self.cfg.excelleris_url}?{urllib.parse.urlencode(params)}"
        log.debug("excelleris %s", what)
        return self.transport.request(
            "GET",
            url,
            headers={
                "Accept": "text/xml, */*",
                "User-Agent": user_agent(self.cfg.excelleris_product),
            },
        )

    def __enter__(self) -> "ExcellerisSession":
        resp = self._get(
            {
                "Page": "Login",
                "Mode": "Silent",
                "UserID": self.cfg.excelleris_user_id,
                "Password": self.cfg.excelleris_password,
            },
            "login",
        )
        body = resp.text().strip()
        if resp.status == 200 and body == _AUTH_GRANTED:
            log.info("excelleris: authenticated")
            return self
        if body == _AUTH_DENIED:
            raise ExcellerisAuthError(
                "excelleris login", "access denied (check user id / password)"
            )
        raise ExcellerisAuthError(
            "excelleris login", f"unexpected reply (HTTP {resp.status}, {len(resp.body)} bytes)"
        )

    def __exit__(self, *_exc) -> None:
        try:
            self._get({"Logout": "Yes"}, "logout")
            log.info("excelleris: logged out")
        except TransportError as exc:
            log.warning("excelleris logout failed (ignored): %s", exc)

    def pull(self) -> bytes:
        """Fetch pending results. ``Pending=Yes`` is required by the guide."""
        resp = self._get({"Page": "HL7", "Query": "NewRequests", "Pending": "Yes"}, "pull")
        if resp.status != 200:
            raise StepError("excelleris pull", f"HTTP {resp.status}")
        log.info("excelleris: pull returned %d bytes", len(resp.body))
        return resp.body

    def ack(self, positive: bool) -> None:
        """Acknowledge the pull. A positive ack tells Excelleris to stop
        offering these results; a negative one keeps them pending.

        Both ``<HL7Messages/>`` (what the guide describes) and
        ``<HL7Messages ReturnCode="0"/>`` (what the shell script tested for)
        are accepted as success. ``ReturnCode="1"`` is a failed ack and is
        raised so the operator hears about it: after a failed positive ack
        Excelleris will resend, which CARLOS will refuse as a duplicate only if
        the resent file is byte-identical.
        """
        value = "Positive" if positive else "Negative"
        resp = self._get({"Page": "HL7", "ACK": value}, f"ack {value}")
        if resp.status != 200:
            raise StepError("excelleris ack", f"{value} ack rejected (HTTP {resp.status})")
        body = resp.text().strip()
        try:
            root = ET.fromstring(body) if body else None
        except ET.ParseError:
            root = None
        if root is not None and root.tag == "HL7Messages":
            code = root.get("ReturnCode")
            if code not in (None, "0"):
                raise StepError("excelleris ack", f"{value} ack failed (ReturnCode={code})")
            log.info("excelleris: %s acknowledgment accepted", value.lower())
            return
        # HTTP 200 with a body neither the guide nor the shell script describes.
        # The shell script treated this as success by only ever logging; keep
        # that leniency (no false alert every run) but make it visible.
        log.warning(
            "excelleris: %s acknowledgment returned an unrecognised %d-byte reply; "
            "treating as accepted",
            value.lower(),
            len(resp.body),
        )


# ---------------------------------------------------------------------------
# Local archive (inbox -> done / failed)
# ---------------------------------------------------------------------------


class Archive:
    """Private on-disk state: inbox/ (awaiting upload), done/ (compressed),
    failed/ (rejected by CARLOS, for an operator to look at).

    Every directory is created 0700 and every file written 0600; the shell
    script left both to the umask, which on most hosts meant world-readable
    lab results under the script's own directory.
    """

    def __init__(self, cfg: Config):
        self.cfg = cfg
        for d in (cfg.state_dir, cfg.inbox_dir, cfg.done_dir, cfg.failed_dir):
            d.mkdir(parents=True, exist_ok=True, mode=0o700)
            os.chmod(d, 0o700)

    def save_inbox(self, run_id: str, data: bytes) -> Path:
        """Write a pull atomically: temp file, fsync, rename, directory fsync.

        Only after this returns is it safe to send Excelleris a positive ack,
        because only then does a crash or power loss leave the file behind.
        """
        target = self.cfg.inbox_dir / f"{run_id}.xml"
        suffix = 0
        while target.exists():  # two runs in one second is prevented by the lock, but be safe
            suffix += 1
            target = self.cfg.inbox_dir / f"{run_id}-{suffix}.xml"
        tmp = target.with_suffix(".xml.part")
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, "wb") as fh:
                fh.write(data)
                fh.flush()
                os.fsync(fh.fileno())
            os.rename(tmp, target)
            dir_fd = os.open(self.cfg.inbox_dir, os.O_RDONLY)
            try:
                os.fsync(dir_fd)
            finally:
                os.close(dir_fd)
        except BaseException:
            tmp.unlink(missing_ok=True)
            raise
        return target

    def inbox_files(self) -> list[Path]:
        """Oldest first, so a backlog is uploaded in the order it was pulled."""
        return sorted(p for p in self.cfg.inbox_dir.glob("*.xml") if p.is_file())

    def _unique(self, directory: Path, name: str) -> Path:
        """A path in ``directory`` that does not exist yet.

        Run ids have one-second resolution, so a retried backlog file and a
        fresh pull in the same second can share a name; never overwrite.
        """
        candidate = directory / name
        suffix = 0
        while candidate.exists():
            suffix += 1
            stem, dot, ext = name.partition(".")
            candidate = directory / f"{stem}-{suffix}{dot}{ext}"
        return candidate

    def mark_done(self, path: Path) -> Path:
        """Compress into done/ and remove the inbox copy."""
        dest = self._unique(self.cfg.done_dir, path.name + ".xz")
        fd = os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(fd, "wb") as raw, lzma.open(raw, "wb") as out, path.open("rb") as src:
                shutil.copyfileobj(src, out)
        except BaseException:
            # Never leave a truncated archive behind; the inbox copy stays and
            # is re-sent next run (CARLOS answers 409, so that is harmless).
            dest.unlink(missing_ok=True)
            raise
        path.unlink()
        return dest

    def mark_failed(self, path: Path) -> Path:
        """Move a file CARLOS definitively rejected out of the retry path."""
        dest = self._unique(self.cfg.failed_dir, path.name)
        os.rename(path, dest)
        return dest

    def purge(self) -> int:
        """Delete done/ archives older than the retention window. 0 = keep all."""
        days = self.cfg.retention_days
        if days == 0:
            log.warning(
                "retention_days is 0: compressed results in %s are kept forever", self.cfg.done_dir
            )
            return 0
        cutoff = time.time() - days * 86400
        removed = 0
        for p in self.cfg.done_dir.glob("*.xz"):
            if p.is_file() and p.stat().st_mtime < cutoff:
                p.unlink()
                removed += 1
        if removed:
            log.info("purged %d archived result file(s) older than %d days", removed, days)
        return removed


# ---------------------------------------------------------------------------
# CARLOS side
# ---------------------------------------------------------------------------


class LabUploadEnvelope:
    """Client half of the envelope ``LabUpload2Action`` opens.

    Server side (``LabUpload2Action.decryptMessage`` / ``validateSignature``):

      key       = Base64( RSA/ECB/PKCS1Padding( server private key, AES key ) )
      payload   = Cipher "AES" with the AES key, which in Java means
                  AES/ECB/PKCS5Padding (PKCS#5 and PKCS#7 coincide at 16 bytes)
      signature = Base64( MD5withRSA( client private key, plaintext ) )

    This is a legacy construction (no authenticated encryption, MD5, PKCS#1
    v1.5). It is reproduced exactly because the receiver only decrypts it, and
    its replacement is tracked in CARLOS issue #3413. Keep all of it here so
    that swap touches one class.
    """

    def __init__(self, client_private_key_b64: str, server_public_key_b64: str):
        self.client_key = self._load_private(client_private_key_b64)
        self.server_key = self._load_public(server_public_key_b64)

    @staticmethod
    def _decode(b64: str, what: str) -> bytes:
        try:
            return base64.b64decode(b64, validate=True)
        except ValueError as exc:
            raise ConfigError(f"{what} is not valid base64") from exc

    def _load_private(self, b64: str) -> rsa.RSAPrivateKey:
        der = self._decode(b64, "[carlos] client_private_key")
        try:
            key = serialization.load_der_private_key(der, password=None)
        except ValueError as exc:
            raise ConfigError(
                f"[carlos] client_private_key is not a PKCS#8 DER RSA key: {exc}"
            ) from exc
        if not isinstance(key, rsa.RSAPrivateKey):
            raise ConfigError("[carlos] client_private_key is not an RSA key")
        return key

    def _load_public(self, b64: str) -> rsa.RSAPublicKey:
        der = self._decode(b64, "[carlos] server_public_key")
        try:
            key = serialization.load_der_public_key(der)
        except ValueError as exc:
            raise ConfigError(
                f"[carlos] server_public_key is not an X.509 DER RSA key: {exc}"
            ) from exc
        if not isinstance(key, rsa.RSAPublicKey):
            raise ConfigError("[carlos] server_public_key is not an RSA key")
        return key

    def seal(self, plaintext: bytes) -> tuple[bytes, str, str]:
        """Return (ciphertext, key_b64, signature_b64) for one file."""
        aes_key = secrets.token_bytes(16)
        padder = PKCS7(128).padder()
        padded = padder.update(plaintext) + padder.finalize()
        encryptor = Cipher(algorithms.AES(aes_key), modes.ECB()).encryptor()  # see class docstring
        ciphertext = encryptor.update(padded) + encryptor.finalize()
        wrapped = self.server_key.encrypt(aes_key, padding.PKCS1v15())
        signature = self.client_key.sign(plaintext, padding.PKCS1v15(), hashes.MD5())
        return (
            ciphertext,
            base64.b64encode(wrapped).decode("ascii"),
            base64.b64encode(signature).decode("ascii"),
        )


def encode_multipart(
    fields: dict[str, str], file_field: str, filename: str, content: bytes
) -> tuple[str, bytes]:
    """Build a multipart/form-data body; the stdlib has no helper for this."""
    boundary = "----carlos-excelleris-" + secrets.token_hex(16)
    parts: list[bytes] = []
    for name, value in fields.items():
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
        )
    parts.append(
        (
            f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; '
            f'filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n'
        ).encode()
        + content
        + b"\r\n"
    )
    parts.append(f"--{boundary}--\r\n".encode())
    return f"multipart/form-data; boundary={boundary}", b"".join(parts)


@dataclasses.dataclass(frozen=True)
class UploadOutcome:
    status: int
    detail: str

    @property
    def accepted(self) -> bool:
        # 200: imported now. 409: CARLOS already holds a byte-identical file
        # (FileUploadCheck); that is the retry path working as designed.
        return self.status in (200, 409)

    @property
    def definitive(self) -> bool:
        """CARLOS answered and said no. Retrying the same bytes will not help."""
        return 400 <= self.status < 600 and self.status != 409


class CarlosSession:
    """Scripted login, CSRF token fetch, signed upload, logout, against either
    CARLOS or OSCAR 19 (``[carlos] flavour``).

    Routes come from the two code bases, not from the retired Mule bridge.
    The upload action, its parameters and its crypto are the same in both;
    only routing and the CSRF layer differ.

      flavour = carlos  (Struts 7, extensionless routes, CSRFGuard 4.5)
        POST /login              username, password, pin, ajaxResponse=true
                                 -> 302 to /provider/providercontrol (normal
                                 account) or JSON {"success": ...}; CSRF-exempt
        GET  /csrfguard          same-domain Referer -> JS containing
                                 masterTokenValue = '<token>' (session-wide)
        POST /lab/newLabUpload   multipart: service, key, signature,
                                 use_http_response_code, one file;
                                 CSRF-TOKEN header + X-Requested-With
        POST /logout             (GET gets a 405; CSRF-exempt)

      flavour = oscar19 (Struts 1, *.do routes, no CSRF layer at all)
        POST /login.do           same fields -> 302 to
                                 /provider/providercontrol.jsp
        (no token step)
        POST /lab/newLabUpload.do same multipart, no CSRF headers
        GET  /logout.jsp

    Both report the upload outcome as the HTTP status when
    use_http_response_code is set: 200 uploaded, 409 uploaded previously,
    500 import failed. A signature failure is 403 on CARLOS, 406 on OSCAR 19.
    """

    _TOKEN_RE = re.compile(r"""masterTokenValue\s*=\s*["']([^"']+)["']""")
    _ROUTES: dict[str, dict[str, Optional[str]]] = {
        FLAVOUR_CARLOS: {"login": "/login", "upload": "/lab/newLabUpload", "csrf": "/csrfguard"},
        FLAVOUR_OSCAR19: {"login": "/login.do", "upload": "/lab/newLabUpload.do", "csrf": None},
    }

    def __init__(self, cfg: Config, transport, envelope: LabUploadEnvelope):
        self.cfg = cfg
        self.transport = transport
        self.envelope = envelope
        self.flavour = cfg.carlos_flavour
        self.routes = self._ROUTES[self.flavour]
        self.csrf_token: Optional[str] = None

    @property
    def uses_csrf(self) -> bool:
        return self.routes["csrf"] is not None

    def _url(self, route: str) -> str:
        return f"{self.cfg.carlos_base_url}{route}"

    def __enter__(self) -> "CarlosSession":
        self.login()
        if self.uses_csrf:
            self.fetch_csrf_token()
        return self

    def __exit__(self, *_exc) -> None:
        try:
            if self.flavour == FLAVOUR_OSCAR19:
                # OSCAR 19 logs out by rendering logout.jsp, which invalidates
                # the session and redirects to index.jsp.
                self.transport.request("GET", self._url("/logout.jsp"))
            else:
                # Logout2Action answers 405 to anything but POST (it has side
                # effects it must not run on a link pre-fetch). The route is
                # CSRF-exempt, so no token is needed.
                self.transport.request(
                    "POST",
                    self._url("/logout"),
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                    body=b"",
                )
            log.info("%s: logged out", self.flavour)
        except TransportError as exc:
            log.warning("%s logout failed (ignored): %s", self.flavour, exc)

    def login(self) -> None:
        body = urllib.parse.urlencode(
            {
                "username": self.cfg.carlos_username,
                "password": self.cfg.carlos_password,
                "pin": self.cfg.carlos_pin,
                "ajaxResponse": "true",
            }
        ).encode()
        resp = self.transport.request(
            "POST",
            self._url(self.routes["login"] or ""),
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "Accept": "application/json",
            },
            body=body,
        )
        # Success looks like one of two things. A plain provider account is
        # redirected to its schedule page BEFORE the action gets to its JSON
        # branch (CARLOS Login2Action and OSCAR 19 LoginAction alike), so
        # ajaxResponse=true still yields a 302 for the normal case: Location
        # /provider/providercontrol on CARLOS, /provider/providercontrol.jsp
        # on OSCAR 19. The JSON {"success":true} only comes back for
        # CAISI-style accounts.
        location = self._location(resp)
        if resp.status in (301, 302, 303) and "/provider/providercontrol" in location:
            log.info("%s: authenticated as %s", self.flavour, self.cfg.carlos_username)
            return
        if resp.status == 200 and '"success":true' in resp.text().replace(" ", ""):
            log.info("%s: authenticated as %s", self.flavour, self.cfg.carlos_username)
            return
        raise StepError(f"{self.flavour} login", self._explain_login_failure(resp))

    @staticmethod
    def _location(resp: HttpResponse) -> str:
        return resp.header("Location")

    @classmethod
    def _explain_login_failure(cls, resp: HttpResponse) -> str:
        """Turn the login route's redirect targets into an operator message."""
        location = cls._location(resp)
        if "forcepasswordreset" in location:
            return "the service account has a forced password reset pending; clear it in the EMR"
        if "select_facility" in location:
            return "the service account belongs to more than one facility; a scripted login cannot choose one"
        if "mfa" in location.lower():
            return (
                "the service account is enrolled in MFA; a scripted login cannot answer a challenge"
            )
        if "loginfailed" in location or "login=failed" in location:
            return "the EMR rejected the credentials"
        if "logout" in location:
            # LoginFilter bounced the request: the route is wrong for this
            # EMR generation (check [carlos] flavour) or the session was lost.
            return "the EMR redirected the login to its logout page; check [carlos] flavour and base_url"
        if resp.status == 200 and b"mfa" in resp.body.lower():
            # The MFA challenge renders as a 200 HTML page, not a redirect.
            return (
                "the service account is enrolled in MFA; a scripted login cannot answer a challenge"
            )
        if resp.status == 200:
            return "the EMR rejected the credentials (invalid username, password or PIN)"
        return f"unexpected reply HTTP {resp.status}" + (f" -> {location}" if location else "")

    def fetch_csrf_token(self) -> str:
        route = self.routes["csrf"]
        if route is None:
            raise StepError(f"{self.flavour} csrf", "this flavour has no CSRF token endpoint")
        # CSRFGuard serves its script only to a request whose Referer matches
        # the host (or carries no Referer); send one so this never depends on
        # that leniency.
        resp = self.transport.request(
            "GET",
            self._url(route),
            headers={"Referer": self.cfg.carlos_base_url + "/", "Accept": "*/*"},
        )
        match = self._TOKEN_RE.search(resp.text()) if resp.status == 200 else None
        if not match:
            raise StepError(
                f"{self.flavour} csrf", f"could not obtain a CSRF token (HTTP {resp.status})"
            )
        self.csrf_token = match.group(1)
        log.debug("%s: csrf token obtained", self.flavour)
        return self.csrf_token

    def upload(self, path: Path) -> UploadOutcome:
        """Upload one pull file exactly as stored.

        The bytes must be sent untouched: the Excelleris and PATHL7 upload
        handlers walk the DOM as ``firstChild`` / ``childNodes`` with no
        whitespace handling, so reformatting or pretty-printing the XML would
        break the import.
        """
        if self.uses_csrf and not self.csrf_token:
            self.fetch_csrf_token()
        plaintext = path.read_bytes()
        if len(plaintext) > CARLOS_MULTIPART_MAX_BYTES:
            # struts.multipart.maxSize in CARLOS' struts.xml (OSCAR 19 allows
            # 100 MB). The request is refused before the action runs; say why
            # in advance.
            log.warning(
                "%s is %d bytes, above the %d-byte multipart limit; expect a rejection",
                path.name,
                len(plaintext),
                CARLOS_MULTIPART_MAX_BYTES,
            )
        ciphertext, key_b64, sig_b64 = self.envelope.seal(plaintext)
        content_type, body = encode_multipart(
            {
                "service": self.cfg.carlos_service,
                "key": key_b64,
                "signature": sig_b64,
                "use_http_response_code": "true",
            },
            "importFile",
            path.name,
            ciphertext,
        )
        headers = {
            "Content-Type": content_type,
            "Referer": self.cfg.carlos_base_url + "/",
            "Accept": "*/*",
        }
        if self.uses_csrf:
            # CSRFGuard validates the header, not the body, for AJAX-marked
            # requests; CARLOS reads X-Requested-With as a list, so one value
            # is fine. OSCAR 19 has no CSRF layer and gets neither header.
            headers["CSRF-TOKEN"] = self.csrf_token or ""
            headers["X-Requested-With"] = "XMLHttpRequest"
        resp = self.transport.request(
            "POST", self._url(self.routes["upload"] or ""), headers=headers, body=body
        )
        detail = {
            200: "uploaded",
            400: "bad request (no file received)",
            403: "signature validation failed (service name / client key mismatch)",
            406: "signature validation failed (service name / client key mismatch)",
            409: "uploaded previously (duplicate, already imported)",
            500: "the EMR could not import the file (see its log)",
        }.get(resp.status, f"HTTP {resp.status}")
        return UploadOutcome(resp.status, detail)


# ---------------------------------------------------------------------------
# Alerts
# ---------------------------------------------------------------------------


class Notifier:
    """Send a failure email through sendmail. Never includes result content."""

    def __init__(self, cfg: Config):
        self.cfg = cfg

    def failure(self, run_id: str, step: str, detail: str) -> None:
        host = socket.gethostname()
        log.error("ALERT run %s step '%s': %s", run_id, step, detail)
        if not self.cfg.alert_email:
            log.warning("no [alerts] email configured; alert not sent")
            return
        msg = EmailMessage()
        msg["From"] = self.cfg.alert_from
        msg["To"] = self.cfg.alert_email
        msg["Subject"] = f"CARLOS Excelleris pull FAILED on {host}: {step}"
        msg.set_content(
            f"The Excelleris lab pull on {host} failed.\n\n"
            f"Run:    {run_id}\n"
            f"Clinic: {self.cfg.excelleris_context or '(context not set)'}\n"
            f"Step:   {step}\n"
            f"Detail: {detail}\n\n"
            f"Log:    {self.cfg.log_file}\n"
            f"Inbox:  {self.cfg.inbox_dir} (files here have been pulled but not yet imported)\n"
            f"Failed: {self.cfg.failed_dir} (files CARLOS rejected; need a person)\n"
        )
        try:
            subprocess.run(
                [self.cfg.sendmail, "-t", "-oi"],
                input=msg.as_bytes(),
                check=True,
                timeout=60,
                capture_output=True,
            )
            log.info("alert emailed to %s", self.cfg.alert_email)
        except (OSError, subprocess.SubprocessError) as exc:
            # The alert channel failing must not mask the original failure.
            log.error("could not send alert email via %s: %s", self.cfg.sendmail, exc)


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------


@dataclasses.dataclass
class RunOptions:
    dry_run: bool = False
    no_upload: bool = False
    upload_only: bool = False


# Factories so tests can inject fake transports without monkeypatching globals.
TransportFactory = Callable[[int, Optional[ssl.SSLContext], bool], object]


def default_transport(timeout: int, ssl_context: Optional[ssl.SSLContext], follow_redirects: bool):
    return HttpTransport(timeout, ssl_context, follow_redirects)


def pull_step(
    cfg: Config, archive: Archive, run_id: str, opts: RunOptions, make_transport=default_transport
) -> Optional[Path]:
    """Excelleris login -> pull -> store -> ack -> logout. Returns the inbox file or None."""
    with ClientCertificate(cfg.pfx_file, cfg.pfx_password) as cert:
        transport = make_transport(
            cfg.excelleris_timeout, cert.ssl_context(cfg.excelleris_ca_file), True
        )
        try:
            with ExcellerisSession(cfg, transport) as session:
                if opts.dry_run:
                    log.info("dry run: skipping pull")
                    return None
                try:
                    body = session.pull()
                except StepError:
                    # The shell script sent a negative ack when the download
                    # itself failed; keep that so Excelleris sees the session
                    # end the same way. Best effort: the pull error is the one
                    # to report.
                    try:
                        session.ack(False)
                    except StepError as ack_exc:
                        log.warning("excelleris: negative ack after failed pull: %s", ack_exc)
                    raise
                summary = inspect_pull(body)
                if summary.problem or summary.return_code is not None:
                    # Not a results document: keep them pending at Excelleris
                    # and tell someone, because this is not the normal "empty".
                    # Logged before the ack so a failing ack cannot hide it.
                    detail = (
                        f"unexpected pull body: {summary.problem}"
                        if summary.problem
                        else f"Excelleris returned ReturnCode={summary.return_code}"
                    )
                    log.error("excelleris: %s", detail)
                    session.ack(False)
                    raise StepError("excelleris pull", detail)
                if not summary.has_results:
                    log.info("excelleris: no results pending")
                    session.ack(False)
                    return None
                try:
                    stored = archive.save_inbox(run_id, body)
                except OSError as exc:
                    # Nothing durable on disk, so do not claim receipt.
                    session.ack(False)
                    raise StepError("store pull", f"could not write inbox file: {exc}") from exc
                log.info("stored %d message(s) as %s", summary.message_count, stored.name)
                session.ack(True)
                return stored
        except TransportError as exc:
            raise StepError("excelleris transport", str(exc)) from exc


def upload_step(
    cfg: Config, archive: Archive, opts: RunOptions, make_transport=default_transport
) -> list[str]:
    """Upload every inbox file to CARLOS. Returns a list of failure descriptions."""
    files = archive.inbox_files()
    if opts.dry_run:
        files = []
    elif not files:
        log.info("inbox empty; nothing to upload")
        return []
    failures: list[str] = []
    envelope = LabUploadEnvelope(cfg.client_private_key, cfg.server_public_key)
    transport = make_transport(
        cfg.carlos_timeout, server_verifying_context(cfg.carlos_ca_file), False
    )
    try:
        with CarlosSession(cfg, transport, envelope) as session:
            if opts.dry_run:
                log.info("dry run: CARLOS login and CSRF token verified; skipping upload")
            for path in files:
                outcome = session.upload(path)
                if outcome.accepted:
                    dest = archive.mark_done(path)
                    log.info("carlos: %s -> %s (%s)", path.name, dest.name, outcome.detail)
                elif outcome.definitive:
                    dest = archive.mark_failed(path)
                    failures.append(f"{path.name}: {outcome.detail}; moved to {dest}")
                    log.error("carlos: %s rejected: %s", path.name, outcome.detail)
                else:
                    failures.append(f"{path.name}: {outcome.detail}; left in inbox for retry")
                    log.error("carlos: %s not uploaded: %s", path.name, outcome.detail)
    except TransportError as exc:
        # The session is gone; whatever is still in the inbox is retried next run.
        failures.append(f"CARLOS unreachable: {exc}; inbox files kept for retry")
    return failures


def run(cfg: Config, opts: RunOptions, make_transport=default_transport) -> int:
    """One complete run. Every failure path ends in an alert and a non-zero
    exit; the only quiet early exit is lock contention."""
    notifier = Notifier(cfg)
    run_id = time.strftime("%Y%m%d-%H%M%S")
    lock: Optional[RunLock] = None
    try:
        # State directory and lock come first and inside the try: a permission
        # problem here must produce an alert, not a bare traceback.
        archive = Archive(cfg)
        lock = RunLock(cfg.lock_file)
        if not lock.acquire():
            lock = None
            log.warning("another excelleris_pull run still holds %s; exiting", cfg.lock_file)
            return EXIT_LOCKED
        log.info(
            ">>>>> run %s started (excelleris_pull %s, clinic %r)",
            run_id,
            VERSION,
            cfg.excelleris_context,
        )
        failures: list[str] = []
        # Retry first: a backlog from a CARLOS outage goes in before new work.
        if not opts.no_upload and not opts.dry_run:
            failures += upload_step(cfg, archive, opts, make_transport)
        if not opts.upload_only:
            try:
                pull_step(cfg, archive, run_id, opts, make_transport)
            except StepError as exc:
                # The pull failing must not strand what is already in the inbox
                # (including a pull stored just before its ack failed), so
                # record it and still run the upload below.
                failures.append(f"{exc.step}: {exc.detail}")
        if not opts.no_upload:
            failures += upload_step(cfg, archive, opts, make_transport)
        archive.purge()
        if failures:
            notifier.failure(run_id, "run", "; ".join(failures))
            log.error("<<<<< run %s finished WITH ERRORS", run_id)
            return EXIT_FAILED
        log.info("<<<<< run %s finished cleanly", run_id)
        return EXIT_OK
    except StepError as exc:
        notifier.failure(run_id, exc.step, exc.detail)
        log.error("<<<<< run %s finished WITH ERRORS", run_id)
        return EXIT_FAILED
    except ConfigError as exc:
        # Key or PFX problems surface here (they are only parsed when used).
        notifier.failure(run_id, "configuration", str(exc))
        return EXIT_CONFIG
    except Exception as exc:  # noqa: BLE001 - last resort: alert rather than die silently
        log.exception("unhandled error")
        notifier.failure(run_id, "internal error", f"{type(exc).__name__}: {exc}")
        return EXIT_FAILED
    finally:
        if lock is not None:
            lock.release()


# ---------------------------------------------------------------------------
# Command line
# ---------------------------------------------------------------------------


def check_config(cfg: Config) -> int:
    """Validate keys and PFX offline and print the masked configuration."""
    LabUploadEnvelope(cfg.client_private_key, cfg.server_public_key)
    with ClientCertificate(cfg.pfx_file, cfg.pfx_password) as cert:
        # Also proves the optional ca_file bundles parse, so a bad PEM is found
        # here rather than on the first scheduled run.
        try:
            cert.ssl_context(cfg.excelleris_ca_file)
            server_verifying_context(cfg.carlos_ca_file)
        except ssl.SSLError as exc:
            raise ConfigError(f"ca_file / certificate could not be loaded: {exc}") from exc
    for key, value in cfg.masked().items():
        print(f"{key:22s} = {value}")
    print("keys and PFX load correctly")
    return EXIT_OK


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="excelleris_pull",
        description="Pull Excelleris lab results and upload them to CARLOS EMR.",
    )
    parser.add_argument(
        "--config", required=True, type=Path, help="path to the INI config (mode 0600)"
    )
    parser.add_argument(
        "--check-config", action="store_true", help="validate config, keys and PFX; no network"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="log in and out of both systems; no pull, no upload"
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--no-upload",
        action="store_true",
        help="pull and acknowledge only; leave files in the inbox",
    )
    mode.add_argument(
        "--upload-only", action="store_true", help="upload whatever is in the inbox; do not pull"
    )
    parser.add_argument(
        "-v", "--verbose", action="store_true", help="debug logging on stderr and in the log file"
    )
    parser.add_argument("--version", action="version", version=f"%(prog)s {VERSION}")
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    if os.geteuid() == 0:
        # Nothing here needs root, and root would turn a bad config file into
        # a bigger problem than a missed lab pull.
        sys.stderr.write("excelleris_pull: refusing to run as root; use a service user\n")
        return EXIT_CONFIG
    try:
        cfg = load_config(args.config)
    except ConfigError as exc:
        sys.stderr.write(f"excelleris_pull: config error: {exc}\n")
        return EXIT_CONFIG
    if args.check_config:
        try:
            return check_config(cfg)
        except ConfigError as exc:
            sys.stderr.write(f"excelleris_pull: config error: {exc}\n")
            return EXIT_CONFIG
    try:
        setup_logging(cfg.log_file, args.verbose)
    except OSError as exc:
        sys.stderr.write(f"excelleris_pull: cannot open log file {cfg.log_file}: {exc}\n")
        return EXIT_CONFIG
    opts = RunOptions(dry_run=args.dry_run, no_upload=args.no_upload, upload_only=args.upload_only)
    return run(cfg, opts)


if __name__ == "__main__":
    sys.exit(main())
