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
    # This password protects only a new disposable test identity. No deployed key is copied.
    subprocess.run(["keytool", "-genkeypair", "-alias", "synthetic-ci", "-keyalg", "RSA", "-keysize", "2048",
        "-storetype", "PKCS12", "-keystore", str(BASE / "conf/tls.p12"), "-storepass", "public-synthetic-tls-only",
        "-keypass", "public-synthetic-tls-only", "-validity", "7", "-dname", "CN=localhost,OU=SYNTHETIC,O=example.invalid",
        "-ext", "SAN=IP:127.0.0.1,DNS:localhost", "-noprompt"], check=True)
    key = BASE / "conf/tls.p12"
    os.chown(key, 0, account.pw_gid)
    key.chmod(0o640)
    certificate = BASE / "tls.crt"
    subprocess.run(["keytool", "-exportcert", "-rfc", "-alias", "synthetic-ci", "-keystore", str(key),
        "-storepass", "public-synthetic-tls-only", "-file", str(certificate)], check=True)
    certificate.chmod(0o644)
    public_pem = subprocess.run(["openssl", "x509", "-in", str(certificate), "-pubkey", "-noout"], check=True, capture_output=True).stdout
    public_der = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "DER"], input=public_pem, capture_output=True, check=True).stdout
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
    (BASE / "conf/Catalina/localhost/carlos.xml").write_text(
        '<Context docBase="/usr/share/carlos-emr/webapp/carlos" reloadable="false"/>')
    command = ["java", "-Xmx1536m", "-Duser.timezone=America/Toronto",
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
                                                   "tlsSpkiSha256": spki}))
    print("PASS new TLS Tomcat started as service user with exact installed WAR")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
