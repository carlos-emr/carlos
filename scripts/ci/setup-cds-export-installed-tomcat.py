#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Start an exclusively owned full Tomcat with one untouched installed WAR and a new test TLS identity."""
from __future__ import annotations
import base64
import hashlib
import json
import os
from pathlib import Path
import pwd
import shutil
import subprocess
from defusedxml import ElementTree as XML

BASE = Path("/opt/cds-tomcat-ci")
WEB = Path("/usr/share/carlos-emr/webapp/carlos")


def main() -> int:
    if os.geteuid() != 0 or not Path("/.dockerenv").is_file() or BASE.exists() or not (WEB / "WEB-INF/web.xml").is_file():
        raise RuntimeError("New owned installed container required")
    account = pwd.getpwnam("carlos")
    BASE.mkdir(mode=0o755)
    for child in ("conf", "conf/Catalina", "conf/Catalina/localhost", "lib", "webapps"):
        (BASE / child).mkdir(mode=0o755)
    for child in ("logs", "temp", "work"):
        path = BASE / child
        path.mkdir(mode=0o700)
        os.chown(path, account.pw_uid, account.pw_gid)
    for name in ("web.xml", "catalina.properties", "logging.properties"):
        source = Path("/etc/tomcat11") / name
        if not source.is_file():
            raise RuntimeError("Actual packaged Tomcat configuration absent")
        shutil.copyfile(source, BASE / "conf" / name)
    # A proper disposable CA and server chain keep strict Python/Node validation
    # enabled. Only the public CA and the exact server SPKI leave this container.
    authority_key = BASE / "conf/tls-ca.key"
    server_key = BASE / "conf/tls-server.key"
    certificate = BASE / "tls.crt"
    server_certificate = BASE / "conf/tls-server.crt"
    request = BASE / "conf/tls-server.csr"
    extensions = BASE / "conf/tls-server.ext"
    key = BASE / "conf/tls.p12"
    previous_umask = os.umask(0o077)
    try:
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-sha256",
            "-keyout", str(authority_key), "-out", str(certificate), "-days", "8",
            "-subj", "/CN=SYNTHETIC-CDS-CI-CA/O=example.invalid",
            "-addext", "basicConstraints=critical,CA:TRUE",
            "-addext", "keyUsage=critical,keyCertSign,cRLSign",
            "-addext", "subjectKeyIdentifier=hash"], check=True)
        subprocess.run(["openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-sha256",
            "-keyout", str(server_key), "-out", str(request),
            "-subj", "/CN=localhost/OU=SYNTHETIC/O=example.invalid"], check=True)
        extensions.write_text("basicConstraints=critical,CA:FALSE\n"
            "keyUsage=critical,digitalSignature,keyEncipherment\n"
            "extendedKeyUsage=serverAuth\nsubjectAltName=IP:127.0.0.1,DNS:localhost\n"
            "subjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid:always\n")
        subprocess.run(["openssl", "x509", "-req", "-in", str(request), "-CA", str(certificate),
            "-CAkey", str(authority_key), "-CAcreateserial", "-out", str(server_certificate),
            "-days", "7", "-sha256", "-extfile", str(extensions)], check=True)
        subprocess.run(["openssl", "verify", "-x509_strict", "-purpose", "sslserver", "-verify_ip", "127.0.0.1",
            "-CAfile", str(certificate), str(server_certificate)], check=True)
        subprocess.run(["openssl", "pkcs12", "-export", "-out", str(key), "-inkey", str(server_key),
            "-in", str(server_certificate), "-certfile", str(certificate), "-name", "synthetic-ci",
            "-passout", "pass:public-synthetic-tls-only"], check=True)
    finally:
        os.umask(previous_umask)
        for path in (authority_key, server_key, request, extensions, certificate.with_suffix(".srl")):
            path.unlink(missing_ok=True)
    os.chown(key, 0, account.pw_gid)
    key.chmod(0o640)
    certificate.chmod(0o644)
    public_pem = subprocess.run(["openssl", "x509", "-in", str(server_certificate), "-pubkey", "-noout"],
        check=True, capture_output=True).stdout
    public_der = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "DER"], input=public_pem,
        capture_output=True, check=True).stdout
    spki = base64.b64encode(hashlib.sha256(public_der).digest()).decode("ascii")
    (BASE / "conf/server.xml").write_text("""<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
  <Service name="CDSNativeCI">
    <Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
               SSLEnabled="true" scheme="https" secure="true" maxThreads="40">
      <SSLHostConfig>
        <Certificate certificateKeystoreFile="/opt/cds-tomcat-ci/conf/tls.p12"
                     certificateKeystorePassword="public-synthetic-tls-only" certificateKeystoreType="PKCS12" type="RSA"/>
      </SSLHostConfig>
    </Connector>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="false" autoDeploy="false"/>
    </Engine>
  </Service>
</Server>
""")
    # Use the complete packaged context. A replacement fragment loses Tomcat's
    # load-bearing forward/wrapper setting and silently yields empty HTTP 200s.
    descriptor = Path("/workspace/debian/assets/tomcat/Catalina/localhost/carlos.xml")
    if descriptor.is_symlink() or not descriptor.is_file() or descriptor.stat().st_size > (1 << 20):
        raise RuntimeError("The actual regular packaged context descriptor is required")
    context_bytes = descriptor.read_bytes()
    context = XML.fromstring(context_bytes)
    if (context.tag != "Context" or context.get("docBase") != str(WEB)
            or context.get("suspendWrappedResponseAfterForward") != "false"
            or context.get("reloadable") != "false"):
        raise RuntimeError("The actual immutable packaged application context is required")
    installed_context = BASE / "conf/Catalina/localhost/carlos.xml"
    shutil.copyfile(descriptor, installed_context)
    if installed_context.read_bytes() != context_bytes:
        raise RuntimeError("Packaged context bytes changed during installation")
    context_sha256 = hashlib.sha256(context_bytes).hexdigest()
    # Share the container JVM default with the actual installed CLI. The explicit
    # clinical zone remains America/Toronto in both application configurations.
    # The shared runtime fingerprint deliberately binds the JVM default zone too.
    command = ["java", "-Xmx1536m",
               "-Dcarlos_override_properties=/etc/carlos-emr/carlos.properties",
               "-Dcatalina.home=/usr/share/tomcat11", "-Dcatalina.base=" + str(BASE),
               "-Djava.io.tmpdir=" + str(BASE / "temp"),
               "-cp", "/usr/share/tomcat11/bin/bootstrap.jar:/usr/share/tomcat11/bin/tomcat-juli.jar",
               "org.apache.catalina.startup.Bootstrap", "start"]
    with (BASE / "logs/catalina.log").open("xb") as log:
        os.fchown(log.fileno(), account.pw_uid, account.pw_gid)
        os.fchmod(log.fileno(), 0o600)
        started = subprocess.Popen(["runuser", "--user", "carlos", "--", *command], stdout=log, stderr=log, start_new_session=True)
    (BASE / "tls-info.json").write_text(json.dumps({"protocol": 1, "pid": started.pid, "serviceUid": account.pw_uid,
                                                   "tlsSpkiSha256": spki, "packagedContextSha256": context_sha256}))
    print("PASS new TLS Tomcat started as service user with exact installed WAR")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
