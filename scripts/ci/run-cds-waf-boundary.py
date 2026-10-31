#!/usr/bin/env python3
#
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
#/
"""Exercise the actual blocking WAF through a disposable, inert HTTP backend.

The proxy must mark every response with its task-owned nonce. Successful
requests must reach an explicitly inert backend; this script accepts no patient,
login, session or source-file input and never submits an import.
"""
from __future__ import annotations

import argparse
import hashlib
import http.client
import ipaddress
import json
import os
from pathlib import Path
import re
import time
import urllib.parse
import uuid


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--disposable", action="store_true")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--proxy-token", required=True, help="non-secret task-owned proxy nonce")
    parser.add_argument("--audit-log", type=Path, required=True, help="private actual ModSecurity JSON audit log")
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    base = urllib.parse.urlsplit(args.base_url)
    if not args.disposable or base.scheme != "http" or base.username or base.password or base.path not in ("", "/"):
        parser.error("requires an explicit disposable local HTTP proxy without credentials or a path")
    try:
        local = ipaddress.ip_address(base.hostname or "").is_private
    except ValueError:
        local = base.hostname in {"localhost", "carlos-cds-build"}
    if not local or not base.port or not re.fullmatch(r"[a-f0-9]{32}", args.proxy_token):
        parser.error("requires a private local listener and its exact non-secret proof nonce")
    if not args.audit_log.is_file() or args.audit_log.is_symlink() or args.evidence.exists() or not args.evidence.is_absolute():
        parser.error("requires the real audit log and an absent absolute evidence directory")
    args.evidence.mkdir(mode=0o700)
    results = []
    journal = os.fdopen(os.open(args.evidence / "rows.jsonl", os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600),
                        "w", encoding="utf-8")
    payload = b"synthetic\x00binary\xffchunk"
    query = "apiVersion=2&operation=chunk&uploadId=00000000-0000-4000-8000-000000000001&offset=0&bytes=" \
        + str(len(payload)) + "&sha256=" + hashlib.sha256(payload).hexdigest()
    canonical = "/carlos/form/cdsUpload?" + query

    def audit_rule_ids(trace: str) -> list[str]:
        def contains(value: object) -> bool:
            if isinstance(value, dict):
                return any(contains(item) for item in value.values())
            if isinstance(value, list):
                return any(contains(item) for item in value)
            return value == trace

        def collect(value: object, found: set[str]) -> None:
            if isinstance(value, dict):
                for key, item in value.items():
                    if key.lower() in {"ruleid", "rule_id"} and str(item).isdigit():
                        found.add(str(item))
                    else:
                        collect(item, found)
            elif isinstance(value, list):
                for item in value:
                    collect(item, found)

        found: set[str] = set()
        with args.audit_log.open(encoding="utf-8") as log:
            while True:
                line = log.readline(2 * 1024 * 1024 + 1)
                if not line:
                    break
                if len(line) > 2 * 1024 * 1024:
                    raise RuntimeError("audit row exceeded the bounded diagnostic envelope")
                row = json.loads(line)
                if contains(row):
                    collect(row, found)
        return sorted(found)

    def check(label: str, path: str, expected: int, *, method: str = "POST",
              content_type: str = "application/octet-stream", size: int | None = None,
              headers_only: bool = False, cookie: str | None = None, attack: bool = False,
              data: bytes | None = None, chunked: bool = False) -> None:
        trace = uuid.uuid4().hex
        content = payload if data is None else data
        total = len(content) if size is None else size
        if path == canonical and size is not None:
            digest = hashlib.sha256()
            left = total
            while left:
                block = b"x" * min(left, 65536)
                digest.update(block)
                left -= len(block)
            path = "/carlos/form/cdsUpload?apiVersion=2&operation=chunk&uploadId=00000000-0000-4000-8000-000000000001&offset=0&bytes=" \
                + str(total) + "&sha256=" + digest.hexdigest()
        headers = {"Content-Type": content_type, "Content-Length": str(total),
                   "X-Requested-With": "XMLHttpRequest", "CSRF-TOKEN": "synthetic-diagnostic-token",
                   "X-Carlos-Waf-Probe": trace}
        if chunked:
            del headers["Content-Length"]
            headers["Transfer-Encoding"] = "chunked"
        if cookie:
            headers["Cookie"] = cookie
        connection = http.client.HTTPConnection(base.hostname, base.port, timeout=20)
        try:
            connection.putrequest(method, path)
            for name, value in headers.items():
                connection.putheader(name, value)
            connection.endheaders()
            if not headers_only:
                left = total
                while left:
                    block = content[:left] if size is None else b"x" * min(left, 65536)
                    try:
                        connection.send((format(len(block), "x").encode() + b"\r\n" + block + b"\r\n") if chunked else block)
                    except (BrokenPipeError, ConnectionResetError):
                        break
                    left -= len(block)
                if chunked:
                    try:
                        connection.send(b"0\r\n\r\n")
                    except (BrokenPipeError, ConnectionResetError):
                        pass
            response = connection.getresponse()
            status = response.status
            proxy = response.getheader("X-Carlos-Cds-Test-Proxy") == args.proxy_token
            inert = response.getheader("X-Carlos-Test-Inert-Upstream") == "true"
            body = response.read(65537)
            if len(body) > 65536:
                raise RuntimeError("response exceeded the bounded probe envelope")
        finally:
            connection.close()
        ids = audit_rule_ids(trace)
        if attack:
            deadline = time.monotonic() + 1
            while not ids and time.monotonic() < deadline:
                time.sleep(0.01)
                ids = audit_rule_ids(trace)
        row = {"case": label, "status": status, "proxyProofMatched": proxy,
               "inertUpstreamReached": inert, "ruleIds": ids}
        results.append(row)
        journal.write(json.dumps(row, separators=(",", ":")) + "\n")
        journal.flush()
        os.fsync(journal.fileno())
        if expected == 200:
            received = json.loads(body)
            wanted = hashlib.sha256(content).hexdigest() if size is None else digest.hexdigest()
            if received.get("bytes") != total or received.get("sha256") != wanted:
                raise RuntimeError("proxy changed or omitted bytes from the synthetic chunk")
        if status != expected or not proxy or (expected == 200 and not inert) or (expected != 200 and inert):
            raise RuntimeError("actual WAF boundary result differed from the expected route/method/type policy")
        if attack and ("920420" in ids or not any(value.startswith(("941", "942", "932")) for value in ids)):
            raise RuntimeError("other CRS attack detection did not remain active on the permitted chunk transaction")

    check("canonical-binary-chunk", canonical, 200)
    check("case-insensitive-MIME", canonical, 200, content_type="APPLICATION/OCTET-STREAM")
    check("four-MiB-chunk", canonical, 200, size=4 * 1024 * 1024)
    check("chunked-four-MiB", canonical, 200, size=4 * 1024 * 1024, chunked=True)
    check("absolute-max-sixteen-MiB", canonical, 200, size=16 * 1024 * 1024)
    for label, path in {
        "different-route": "/carlos/form/cdsImportSubmit?" + query,
        "route-suffix": "/carlos/form/cdsUploadExtra?" + query,
        "do-alias": "/carlos/form/cdsUpload.do?" + query,
        "action-alias": "/carlos/form/cdsUpload.action?" + query,
        "matrix-path": "/carlos/form/cdsUpload;x?" + query,
        "encoded-path": "/carlos/form/%63dsUpload?" + query,
        "dot-segment-path": "/carlos/other/../form/cdsUpload?" + query,
        "missing-operation": "/carlos/form/cdsUpload?offset=0",
        "wrong-operation": canonical.replace("operation=chunk", "operation=begin"),
        "encoded-operation": canonical.replace("operation=chunk", "operation=ch%75nk"),
        "repeated-operation": canonical + "&operation=chunk",
        "conflicting-operation": canonical + "&operation=finish",
        "encoded-conflicting-name": canonical + "&operati%6fn=finish",
    }.items():
        check(label, path, 403)
    for method in ("GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS"):
        check("method-" + method, canonical, 403, method=method)
    for content_type in ("text/plain", "application/pdf", "application/octet-stream;charset=UTF-8"):
        check("MIME-" + content_type, canonical, 403, content_type=content_type)
    check("ordinary-JSON-policy-unchanged", "/carlos/ordinary", 200, content_type="application/json", data=b"{}")
    check("SQLi-still-inspected", canonical, 403, cookie="injection=' or 1=1 --", attack=True)
    check("global-50-MiB-limit", canonical, 413, size=50 * 1024 * 1024 + 1, headers_only=True)
    check("opaque-chunk-16-MiB-limit", canonical, 413, size=16 * 1024 * 1024 + 1)
    check("chunked-opaque-16-MiB-limit", canonical, 413, size=16 * 1024 * 1024 + 1, chunked=True)
    journal.close()
    descriptor = os.open(args.evidence / "results.json", os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump({"outcome": "PASS", "cases": results}, output, separators=(",", ":"))
    print(json.dumps({"gate": "actual-CDS-WAF-boundary", "outcome": "PASS", "cases": len(results),
                      "clinicalBackendUsed": False}, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, http.client.HTTPException):
        raise SystemExit("CDS WAF boundary failed; inspect its private diagnostic evidence") from None
