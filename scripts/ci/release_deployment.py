# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Compare the installed application to every candidate WAR file, without outputting contents."""
import hashlib
import os
from pathlib import Path
import stat
from urllib.parse import urlsplit

from cds_war_manifest import build_manifest, identity
from junit_evidence import EvidenceError

INSTALLED_WEBAPP = Path("/usr/share/carlos-emr/webapp/carlos")
SOURCE_ROOT = Path(__file__).resolve().parents[2]
PACKAGE_TRANSFORMS = {
    "WEB-INF/tomcat-web.xml": "debian/assets/tomcat/tomcat-web.xml",
    "WEB-INF/classes/jdbc-defaults.properties": "debian/assets/jdbc-defaults.properties",
}


def local_target(base_url):
    try:
        url = urlsplit(base_url)
        return (url.scheme in ("http", "https") and url.hostname in ("localhost", "127.0.0.1", "::1")
                and url.username is None and url.password is None and not url.query and not url.fragment)
    except ValueError:
        return False


def attest(war, installed=INSTALLED_WEBAPP, base_url="", root=SOURCE_ROOT):
    if not local_target(base_url):
        raise EvidenceError("Packaged qualification must run beside its loopback deployment")
    if installed.is_symlink() or not installed.is_dir():
        raise EvidenceError("Installed package application tree is unavailable")
    manifest = build_manifest(war)
    expected = {row["name"]: row for row in manifest["entries"] if row["kind"] == "file"}
    # Apply exactly the reviewed debian/rules transforms, including their WAR
    # prerequisites. Everything else remains bound to the candidate bytes.
    if "WEB-INF/tomcat-web.xml" in expected or "WEB-INF/classes/jdbc-defaults.properties" not in expected:
        raise EvidenceError("Candidate does not satisfy package transform prerequisites")
    for name, source in PACKAGE_TRANSFORMS.items():
        data = (root / source).read_bytes()
        expected[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    actual = set()
    total = 0
    combined = hashlib.sha256()
    def unreadable(_failure):
        raise EvidenceError("Installed application directory cannot be read")

    for directory, subdirectories, files in os.walk(installed, followlinks=False, onerror=unreadable):
        parent = Path(directory)
        if any((parent / name).is_symlink() for name in subdirectories):
            raise EvidenceError("Installed application contains an unbound directory symlink")
        for name in files:
            file = parent / name
            relative = file.relative_to(installed).as_posix()
            if relative not in expected:
                raise EvidenceError("Installed application contains files outside the candidate")
            descriptor = os.open(file, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
            with os.fdopen(descriptor, "rb") as stream:
                info = os.fstat(stream.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_size != expected[relative]["bytes"]:
                    raise EvidenceError("Installed application file differs from candidate")
                os.set_blocking(stream.fileno(), True)
                value = hashlib.file_digest(stream, "sha256").hexdigest()
                if value != expected[relative]["sha256"]:
                    raise EvidenceError("Installed application bytes differ from candidate")
                if identity(info) != identity(os.fstat(stream.fileno())) or identity(info) != identity(file.lstat()):
                    raise EvidenceError("Installed application changed during byte verification")
            total += info.st_size
            actual.add(relative)
    if actual != expected.keys():
        raise EvidenceError("Installed application is missing candidate files")
    for name in sorted(actual):
        combined.update(name.encode("utf-8") + b"\0" + expected[name]["sha256"].encode("ascii") + b"\n")
    return {"warSha256": manifest["warSha256"], "files": len(actual), "bytes": total,
            "contentSha256": combined.hexdigest(), "scope": "installed-package-application"}
