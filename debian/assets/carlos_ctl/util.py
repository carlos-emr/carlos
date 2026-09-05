# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Shared plumbing: logging, process execution, credentials, properties I/O.

Counterpart of carlos-podman carlos_ctl/util.py, trimmed to what a single-host
deployment needs.
"""

import os
import re
import secrets
import shutil
import string
import subprocess
import sys
import tempfile
from typing import List, Optional

CONF_DIR = "/etc/carlos-emr"
ENV_FILE = os.path.join(CONF_DIR, "carlos-emr.env")
PROPERTIES = os.path.join(CONF_DIR, "carlos.properties")
DRUGREF_PROPERTIES = os.path.join(CONF_DIR, "drugref2.properties")
BACKUP_ENV = os.path.join(CONF_DIR, "backup.env")
SHARE = "/usr/share/carlos-emr"
LIB = "/usr/lib/carlos-emr"
WEBAPP = os.path.join(SHARE, "webapp", "carlos")
STATE = "/var/lib/carlos-emr"

_TTY = sys.stdout.isatty()
RED, GREEN, YELLOW, RESET = (
    ("\033[31m", "\033[32m", "\033[33m", "\033[0m") if _TTY
    else ("", "", "", "")
)


def log(msg: str) -> None:
    print(f"carlos-ctl: {msg}")


def warn(msg: str) -> None:
    print(f"carlos-ctl: {YELLOW}WARNING{RESET}: {msg}", file=sys.stderr)


def die(msg: str, code: int = 1) -> "SystemExit":
    print(f"carlos-ctl: {RED}ERROR{RESET}: {msg}", file=sys.stderr)
    raise SystemExit(code)


def need_root(verb: str) -> None:
    if os.geteuid() != 0:
        die(f"this command needs root (try: sudo carlos-ctl {verb})")


def run(cmd: List[str], **kw) -> subprocess.CompletedProcess:
    """subprocess.run with sane defaults; callers opt into capture/check.

    INJECTION CONTRACT (this is what a scanner auditing "subprocess without a
    static string" needs to know): every call site passes an argv LIST and
    never sets shell=True, so no string is ever parsed by a shell. The only
    non-constant argv elements are (a) values validated as plain identifiers
    at settings load (the database name), (b) generated alphanumeric
    credentials, and (c) operator CLI arguments on verbs whose entire purpose
    is pass-through (db, logs, backup restic) — where the operator is already
    root. Secrets travel via stdin or the environment, never argv.
    """
    kw.setdefault("text", True)
    # NOT the ambient locale: text=True without an encoding uses
    # locale.getpreferredencoding(), and this seam carries clinic data —
    # client output, tar listings, server errors. Under LANG=C that
    # decodes UTF-8 as mojibake (silently, into the archive CSVs) or
    # raises UnicodeDecodeError on a document name. The client is pinned
    # to utf8mb4, so UTF-8 is the known wire encoding.
    if kw.get("text", True) and "encoding" not in kw:
        kw["encoding"] = "utf-8"
        kw.setdefault("errors", "replace")
    return subprocess.run(cmd, **kw)  # nosec B603


def out(cmd: List[str]) -> str:
    """Command stdout, stripped; empty string on failure."""
    cp = run(cmd, capture_output=True)
    return cp.stdout.strip() if cp.returncode == 0 else ""


def genrandom(length: int, alphabet: str) -> str:
    return "".join(secrets.choice(alphabet) for _ in range(length))


def genpw() -> str:
    # Alphanumeric only: these values end up in a Java properties file, a
    # systemd EnvironmentFile, a shell-sourced env file and SQL DDL, and
    # losing a few bits of entropy is a far better trade than a quoting bug
    # in one of the four formats.
    return genrandom(32, string.ascii_letters + string.digits)


# --- SQL --------------------------------------------------------------------
# Why this tool builds SQL text at all, rather than binding parameters through
# a driver: every database path it owns already needs the mariadb CLIENT, which
# is a hard dependency of this package (see debian/control). Restoring a
# clinic's mysqldump is a client job; so is running the DELIMITER-bearing fixup
# scripts the roles step replays, and so is handing credentials over via
# --defaults-extra-file instead of argv. Adding python3-pymysql on top would
# not remove any of that -- it would add a second way to reach the database,
# and a second set of connection and credential handling to get right, for
# paths that must stay identical to what an operator can reproduce by hand from
# the run's report. So the client stays, and interpolation is escaped here.
#
# ONE implementation, because four had already drifted: o19_preflight's copy
# (which must stay separate -- that file is copied alone to a 2014-era OSCAR 19
# server and may import nothing) had lost the NUL case. test_sql_escape_
# contract.py now pins the two against each other.


def sql_escape(value: str) -> str:
    """Escape `value` for a single-quoted MySQL string literal.

    Backslash, quote and NUL are the whole set that matters here, and the
    reasons the rest do not are conditions this tool enforces rather than
    assumes:

    * ``"`` needs no escaping inside a single-quoted literal, and ANSI_QUOTES
      -- which would change that -- is refused by the ETL pre-checks before
      the first write (etl_precheck_problems), as is NO_BACKSLASH_ESCAPES,
      which would break the backslash form entirely.
    * newline and Ctrl-Z are legal inside a literal and survive this
      tool's transport intact (measured); only the Windows client treats
      Ctrl-Z specially, and this runs on Debian.
    * CR is escaped, and NOT because the server minds it. Every statement
      here is fed to the mariadb CLI on stdin, and the client strips the
      CR of a CRLF as a line terminator BEFORE the server parses the
      statement -- inside a quoted literal too. Measured on 10.11:
      `'a\r\nb'` stored as `a\nb`, while a bare CR and a lone LF both
      survived. The clinic values that reach a hand-built literal are
      role names and secObjPrivilege.objectName, and a role name silently
      losing a byte is written into secObjPrivilege under a spelling that
      no longer matches secUserRole.role_name -- grants that exist and
      grant nothing, the exact drift `role_spelling_drift_sql` exists to
      catch.
    * NUL is escaped because the client refuses a raw NUL in a statement at
      all, and values decoded from its batch output can carry one.

    Identifiers do NOT come through here -- they are backtick-quoted by
    o19etl.ident(), which is a different escape with a different rule.
    """
    return (value.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\0", "\\0").replace("\r", "\\r"))


# --- Java .properties files -------------------------------------------------
# Not configparser: a properties file is not INI (no sections, different
# escape rules), and these files carry credentials, so round-tripping must
# preserve every line we do not own.

def prop_escape(value: str) -> str:
    """Backslashes double on the way into a properties value."""
    return value.replace("\\", "\\\\")


def prop_unescape(value: str) -> str:
    return value.replace("\\\\", "\\")


# The application loads carlos.properties with java.util.Properties.load(
# InputStream), which decodes ISO-8859-1 — so that is the encoding these
# helpers use. latin-1 also maps every byte 1:1, so a rewrite can never
# corrupt a value it does not touch (utf-8 with errors='replace' silently
# and irreversibly mangled migrated Latin-1 bytes like 'Santé').
PROPERTIES_ENCODING = "latin-1"


def prop_get(path: str, key: str) -> Optional[str]:
    """Last active occurrence wins, mirroring java.util.Properties."""
    found = None
    try:
        with open(path, encoding=PROPERTIES_ENCODING) as fh:
            for line in fh:
                m = re.match(rf"^\s*{re.escape(key)}\s*=\s*(.*)$",
                             line.rstrip("\n"))
                if m:
                    found = m.group(1)
    except OSError:
        return None
    return found


def _rewrite_preserving(path: str, new_lines: List[str]) -> None:
    """Write lines back preserving the file's owner and mode: these files hold
    credentials, and a rewrite that reset them to 0644 would be a silent
    disclosure."""
    st = os.stat(path)
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path))
    try:
        with os.fdopen(fd, "w", encoding=PROPERTIES_ENCODING) as fh:
            fh.write("\n".join(new_lines) + "\n")
        os.chmod(tmp, st.st_mode & 0o7777)
        os.chown(tmp, st.st_uid, st.st_gid)
        os.replace(tmp, path)
    except BaseException:
        with contextlib_suppress(OSError):
            os.unlink(tmp)
        raise


def prop_set(path: str, key: str, value: str) -> None:
    """Replace the first active occurrence (drop any later duplicates of the
    same key), or append. The value is written verbatim — callers escape."""
    with open(path, encoding=PROPERTIES_ENCODING) as fh:
        lines = fh.read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    out_lines, done = [], False
    pat = re.compile(rf"^\s*{re.escape(key)}\s*=")
    for line in lines:
        if pat.match(line):
            if not done:
                out_lines.append(f"{key} = {value}")
                done = True
            continue
        out_lines.append(line)
    if not done:
        out_lines.append(f"{key} = {value}")
    _rewrite_preserving(path, out_lines)


def prop_comment(path: str, key: str) -> None:
    """Comment out every active occurrence of a key. For properties whose code
    path is "if set, use it": commenting restores the application's own
    null-handling, which a present-but-bogus example value defeats."""
    with open(path, encoding=PROPERTIES_ENCODING) as fh:
        lines = fh.read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    pat = re.compile(rf"^(\s*{re.escape(key)}\s*=)")
    changed = False
    out_lines = []
    for line in lines:
        if pat.match(line):
            out_lines.append("#" + line)
            changed = True
        else:
            out_lines.append(line)
    if changed:
        _rewrite_preserving(path, out_lines)


# --- env files (KEY=value read by systemd and by shell) ---------------------

def env_get(path: str, key: str) -> Optional[str]:
    """LAST occurrence wins — matching both real consumers of these files:
    shell sourcing and systemd's EnvironmentFile both take the final
    assignment, and first-match here made carlos-ctl disagree with what the
    services actually run with when an operator appended an override."""
    found = None
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                m = re.match(rf"^{re.escape(key)}=(.*)$", line.rstrip("\n"))
                if m:
                    found = m.group(1).strip('"')
    except OSError:
        return None
    return found


def env_set(path: str, key: str, value: str) -> None:
    """Replace or append KEY=value. No shell quoting games: values written by
    this tool are plain identifiers/ports/hostnames."""
    with open(path, encoding=PROPERTIES_ENCODING) as fh:
        lines = fh.read().split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    out_lines, done = [], False
    for line in lines:
        if line.startswith(f"{key}="):
            if not done:
                out_lines.append(f"{key}={value}")
                done = True
            continue
        out_lines.append(line)
    if not done:
        out_lines.append(f"{key}={value}")
    _rewrite_preserving(path, out_lines)


class contextlib_suppress:
    """Tiny local suppress() so util has no imports beyond the stdlib base."""

    def __init__(self, *exceptions):
        self.exceptions = exceptions

    def __enter__(self):
        return self

    def __exit__(self, exctype, exc, tb):
        return exctype is not None and issubclass(exctype, self.exceptions)


def which(name: str) -> Optional[str]:
    return shutil.which(name)
