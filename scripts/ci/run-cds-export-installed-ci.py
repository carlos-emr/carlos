#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Qualify the exact packaged export through the installed CLI and authenticated TLS browser.

Only fresh CI-owned containers/databases are accepted. Production classes come
from one immutable WAR; standalone fixture/oracle helpers never enter its classpath.
"""
from __future__ import annotations
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import ssl
import subprocess
import time
import urllib.request
import uuid
import zipfile

SPEC = importlib.util.spec_from_file_location("cds_war_manifest", Path(__file__).with_name("cds_war_manifest.py"))
WAR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WAR)
DATABASE_IMAGE = "mariadb:11.8@sha256:f16d7a344413e519dff518f27bdb221388f84b0d274c97c4fb5f2cb0dc1b34fa"
WEB = "/usr/share/carlos-emr/webapp/carlos"
OWNED = "/opt/cds-native-export-ci"
PRIVATE = "/run/cds-export-ci"
FIXTURE = "io.github.carlos_emr.carlos.login.CdsExportInstalledFixture"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cli-source", type=Path, required=True)
    parser.add_argument("--playwright-module", type=Path, required=True)
    parser.add_argument("--build-container", default="carlos-tomcat-dev")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    nonce = uuid.uuid4().hex
    base = repo / "target" / ("cds-export-installed-" + nonce)
    base.mkdir(parents=True, mode=0o755)
    private, public, context = (base / key for key in ("private", "public", "context"))
    for path in (private, public, context):
        path.mkdir(mode=0o700)
    database, runtime, network = ("cds-export-ci-" + nonce + suffix for suffix in ("-db", "-runtime", "-net"))
    image = "carlos-cds-export-ci:" + nonce
    docker = ["docker", "--host=unix:///var/run/docker.sock"]
    owned_db = owned_runtime = owned_network = build_connected = False
    status = {"protocol": 1, "status": "RUNNING", "stage": "SETUP"}
    log_path = private / "harness.log"

    def save():
        (public / "ci-proof.json").write_text(json.dumps(status, sort_keys=True, indent=2))

    def stage(name):
        status["stage"] = name
        save()
        print("CDS installed export stage:", name, flush=True)

    def private_json(path, value):
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as out:
            json.dump(value, out)

    save()
    try:
        with log_path.open("w") as log:
            def execute(command, *, capture=False, check=True, timeout=1800):
                return subprocess.run(command, text=True, stdout=subprocess.PIPE if capture else log,
                                      stderr=log, check=check, timeout=timeout)

            def build(command, **kw):
                return execute(docker + ["exec", "--workdir", "/workspace", args.build_container, *command], **kw)

            def run(command, **kw):
                return execute(docker + ["exec", "--user", "0", "--workdir", "/workspace", runtime, *command], **kw)

            def read_json(path):
                result = run(["python3", "-c",
                    "import pathlib,sys;p=pathlib.Path(sys.argv[1]);assert p.is_file() and not p.is_symlink() and p.stat().st_size<(1<<20);sys.stdout.write(p.read_text())",
                    path], capture=True)
                return json.loads(result.stdout)

            def json_result(result):
                rows = []
                for line in result.stdout.splitlines():
                    if line.startswith("{"):
                        rows.append(json.loads(line))
                if not rows:
                    raise RuntimeError("Structured operation evidence absent")
                return rows[-1]

            pin = dict(line.split("=", 1) for line in (repo / "debian/carlos-ctl.pin").read_text().splitlines()
                       if line and not line.startswith("#"))
            actual_cli = execute(["git", "-C", str(args.cli_source), "rev-parse", "HEAD"], capture=True).stdout.strip()
            if actual_cli != pin.get("fallback") or not re.fullmatch("[0-9a-f]{40}", actual_cli):
                raise RuntimeError("Exact paired CLI checkout required")
            status["cliCommit"] = actual_cli
            status["sourceCommit"] = execute(["git", "rev-parse", "HEAD"], capture=True).stdout.strip()
            stage("BUILD_ACTUAL_CLI_PACKAGE_AND_RUNTIME")
            shutil.copytree(args.cli_source, context / "cli", ignore=shutil.ignore_patterns(".git", "__pycache__", ".pytest_cache"))
            shutil.copyfile(repo / "scripts/ci/cds-export-installed-runtime.Dockerfile", context / "Dockerfile")
            execute(docker + ["build", "-t", image, str(context)])
            stage("BUILD_AND_FREEZE_CANDIDATE_WAR")
            build(["mvn", "-B", "-DskipTests", "package"])
            wars = list((repo / "target").glob("carlos-*.war"))
            if len(wars) != 1 or wars[0].is_symlink():
                raise RuntimeError("One exact packaged WAR required")
            war = wars[0]
            status["warContentManifest"] = WAR.publish_manifest(war, public / "candidate-war-content-manifest.json")
            status["warSha256"] = status["warContentManifest"]["warSha256"]
            webapp = base / "webapp"
            webapp.mkdir(mode=0o755)
            with zipfile.ZipFile(war) as archive:
                for member in archive.infolist():
                    target = webapp / member.filename
                    if member.filename.startswith("/") or ".." in Path(member.filename).parts or not target.resolve().is_relative_to(webapp.resolve()):
                        raise RuntimeError("Unsafe WAR member")
                    if member.is_dir():
                        target.mkdir(mode=0o755, parents=True, exist_ok=True)
                    else:
                        target.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
                        with archive.open(member) as source, target.open("xb") as output:
                            shutil.copyfileobj(source, output, 65536)
                        target.chmod(0o644)
            WAR.verify_archive_binding(war, status["warSha256"])
            password = secrets.token_hex(32)
            for name, value in {
                "password": password,
                "client.cnf": "[client]\nuser=root\npassword=" + password + "\n",
                "jdbc.properties": "jdbcUrl=jdbc:mysql://" + database + ":3306/carlos_cds_test\njdbcUser=root\njdbcPassword=" + password + "\n",
            }.items():
                with os.fdopen(os.open(private / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as out:
                    out.write(value)
            stage("FRESH_OWNED_DATABASE")
            execute(docker + ["network", "create", "--internal", network]); owned_network = True
            execute(docker + ["network", "connect", network, args.build_container]); build_connected = True
            execute(docker + ["run", "-d", "--name", database, "--network", network, "--memory", "2g",
                "--tmpfs", "/var/lib/mysql:rw,nosuid,noexec,size=1536m",
                "--mount", "type=bind,src=" + str(private) + ",dst=/run/cds-ci,readonly",
                "-e", "MARIADB_ROOT_PASSWORD_FILE=/run/cds-ci/password", "-e", "MARIADB_ROOT_HOST=%", DATABASE_IMAGE,
                "--innodb-page-size=32768", "--innodb-snapshot-isolation=ON", "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_general_ci", "--innodb-strict-mode=OFF", "--innodb-default-row-format=dynamic",
                "--innodb-buffer-pool-size=128M", "--wait-timeout=600"]); owned_db = True
            mysql = docker + ["exec", database, "mariadb", "--defaults-extra-file=/run/cds-ci/client.cnf"]
            deadline = time.monotonic() + 90
            while execute(mysql + ["-e", "SELECT 1"], check=False).returncode:
                if time.monotonic() > deadline:
                    raise RuntimeError("Owned MariaDB startup timeout")
                time.sleep(1)
            execute(mysql + ["-e", "CREATE DATABASE carlos_cds_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"])
            helper = "/workspace/target/" + base.name + "/bootstrap"
            build(["mvn", "-B", "-q", "dependency:build-classpath", "-Dmdep.outputFile=target/cds-export-installed-dependencies.txt"])
            cp = "/workspace/target/classes:" + (repo / "target/cds-export-installed-dependencies.txt").read_text().strip()
            build(["mkdir", "-p", helper])
            build(["javac", "-cp", cp, "-d", helper, "scripts/ci/CdsImportMariaDbBootstrap.java"])
            build(["java", "-cp", helper + ":" + cp, "CdsImportMariaDbBootstrap",
                "/workspace/target/" + base.name + "/private/jdbc.properties", "database/mysql/migration", "on"])
            stage("INSTALL_IMMUTABLE_WAR_AND_COMPILE_EXTERNAL_HELPER")
            execute(docker + ["run", "-d", "--name", runtime, "--network", network, "--memory", "3g", "--cpus", "2",
                "--publish", "127.0.0.1::8443",
                "--mount", "type=bind,src=" + str(repo) + ",dst=/workspace,readonly", image]); owned_runtime = True
            run(["install", "-d", "-m", "0755", WEB, "/opt/cds-ci-helper"])
            execute(docker + ["cp", str(webapp) + "/.", runtime + ":" + WEB + "/"])
            run(["chown", "-R", "0:0", WEB])
            run(["chmod", "-R", "a-w", WEB])
            manifest_path = "/opt/cds-ci-helper/candidate-war-content-manifest.json"
            execute(docker + ["cp", str(public / "candidate-war-content-manifest.json"), runtime + ":" + manifest_path])
            installed_check = """import hashlib,json,pathlib,sys
root=pathlib.Path(sys.argv[1]);manifest=json.loads(pathlib.Path(sys.argv[2]).read_text())
expected={row['name']:row for row in manifest['entries'] if row['kind']=='file'}
actual={p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file()}
assert set(expected)==set(actual),'Installed WAR file inventory differs'
for name,row in expected.items():
 p=actual[name]
 assert not p.is_symlink() and p.stat().st_size==row['bytes'],'Installed WAR member differs'
 with p.open('rb') as source: digest=hashlib.file_digest(source,'sha256').hexdigest()
 assert digest==row['sha256'],'Installed WAR bytes differ'
print(json.dumps({'verified':True,'files':len(expected),'warSha256':manifest['warSha256']}))
"""
            def verify_installed():
                proof = json_result(run(["python3", "-c", installed_check, WEB, manifest_path], capture=True))
                if proof.get("verified") is not True or proof.get("warSha256") != status["warSha256"]:
                    raise RuntimeError("Installed WAR binding differs")
                return proof
            status["installedWarBefore"] = verify_installed()
            runtime_cp = "/opt/cds-ci-helper:" + WEB + "/WEB-INF/classes:" + WEB + "/WEB-INF/lib/*:/usr/share/tomcat11/lib/*"
            run(["javac", "-cp", runtime_cp, "-d", "/opt/cds-ci-helper", "scripts/ci/CdsExportInstalledFixture.java"])
            run(["install", "-d", "-m", "0700", "/run/cds-ci"])
            execute(docker + ["cp", str(private / "jdbc.properties"), runtime + ":/run/cds-ci/jdbc.properties"])
            run(["chmod", "0600", "/run/cds-ci/jdbc.properties"])
            fixture_command = ["java", "-Xmx256m", "-cp", runtime_cp, FIXTURE]
            stage("ORDINARY_NATIVE_CHART_AND_GUARDED_FRESH_KEYS")
            prepared = json_result(run(fixture_command + ["prepare", "/run/cds-ci/jdbc.properties"], capture=True))
            if prepared.get("prepared") is not True or prepared.get("guardedFreshKeys") is not True:
                raise RuntimeError("Native fixture preparation incomplete")
            fixture = read_json(PRIVATE + "/fixture.json")
            cli = ["carlos-ctl", "cds-export", "--provider", fixture["provider"]]
            stage("ACTUAL_ROOT_DELEGATED_CLINICAL_CLI")
            submitted = json_result(run(cli + ["--patients-file", OWNED + "/selection/patients.json", "--profile", "omd-5.2",
                "--categories", "Demographics", "--recipient", "native", "--audit-plan", "native",
                "--submission-key", "owned-native-cli-" + nonce], capture=True))
            cli_run = submitted.get("runId")
            if submitted.get("state") != "READY" or submitted.get("complete") is not True or not cli_run:
                raise RuntimeError("Actual clinical CLI did not complete")
            observed = json_result(run(cli + ["--status", cli_run], capture=True))
            recovered = json_result(run(cli + ["--status-submission", "owned-native-cli-" + nonce], capture=True))
            if observed.get("complete") is not True or recovered.get("runId") != cli_run:
                raise RuntimeError("Independent CLI observation or recovery differs")
            destination = OWNED + "/delivery/cli.bin"
            run(cli + ["--download", cli_run, "--artifact", "package", "--output", destination])
            before = run(["sha256sum", destination], capture=True).stdout.split()[0]
            refused = run(cli + ["--download", cli_run, "--artifact", "package", "--output", destination], check=False)
            after = run(["sha256sum", destination], capture=True).stdout.split()[0]
            if refused.returncode == 0 or before != after:
                raise RuntimeError("CLI replaced an existing delivery destination")
            run(["cp", destination, PRIVATE + "/cli.bin"])
            json_result(run(fixture_command + ["decrypt", "/run/cds-ci/jdbc.properties", PRIVATE + "/cli.bin", PRIVATE + "/cli.zip"], capture=True))
            oracle = ["python3", "scripts/ci/run-cds-export-installed-oracle.py", "--fixture", PRIVATE + "/fixture.json",
                      "--schema", WEB + "/WEB-INF/classes/cds-import/schema/EMR_Data_Migration_Schema.xsd"]
            run(oracle + ["--archive", PRIVATE + "/cli.zip", "--proof", PRIVATE + "/cli-oracle.json"])
            status["cliOracle"] = read_json(PRIVATE + "/cli-oracle.json")
            status["cli"] = {"rootDelegation": True, "foregroundReady": True, "independentStatus": True,
                             "submissionRecovery": True, "authorizedDownload": True, "existingOutputRefusedUnchanged": True,
                             "ciphertextSha256": before}
            stage("REAL_TLS_TOMCAT_STARTUP")
            # Setup owns a fresh Catalina base. It does not alter a packaged class or WEB-INF file.
            run(["python3", "scripts/ci/setup-cds-export-installed-tomcat.py"])
            mappings = execute(docker + ["port", runtime, "8443/tcp"], capture=True).stdout.strip()
            if not re.fullmatch(r"127\.0\.0\.1:[0-9]+", mappings):
                raise RuntimeError("Loopback-only test TLS listener required")
            base_url = "https://" + mappings + "/carlos"
            execute(docker + ["cp", runtime + ":/opt/cds-tomcat-ci/tls.crt", str(private / "tls.crt")])
            tls = read_json("/opt/cds-tomcat-ci/tls-info.json")
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),
                urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=str(private / "tls.crt"))))
            deadline = time.monotonic() + 300
            while True:
                try:
                    with opener.open(base_url + "/index", timeout=10) as response:
                        page = response.read(1 << 20)
                        if response.status == 200 and b'id="username"' in page:
                            break
                except (OSError, urllib.error.URLError):
                    pass
                if time.monotonic() > deadline:
                    raise RuntimeError("Full authenticated application startup did not become ready")
                time.sleep(2)
            stage("ACTUAL_AUTHENTICATED_ASYNCHRONOUS_BROWSER")
            browser_options = private / "browser-options.json"
            private_json(browser_options, {**fixture, "base": base_url, "tlsSpki": tls["tlsSpkiSha256"], "download": str(private / "browser.bin"),
                                          "proof": str(public / "browser-proof.json")})
            browser_env = dict(os.environ)
            browser_env["CDS_BROWSER_PLAYWRIGHT"] = str(args.playwright_module.resolve())
            browser_env["NODE_EXTRA_CA_CERTS"] = str(private / "tls.crt")
            subprocess.run(["node", str(repo / "scripts/ci/run-cds-export-installed-browser.js"), str(browser_options)],
                           env=browser_env, text=True, stdout=log, stderr=log, timeout=480, check=True)
            browser = json.loads((public / "browser-proof.json").read_text())
            if browser.get("status") != "PASSED":
                raise RuntimeError("Browser evidence incomplete")
            execute(docker + ["cp", str(private / "browser.bin"), runtime + ":" + PRIVATE + "/browser.bin"])
            decrypted = json_result(run(fixture_command + ["decrypt", "/run/cds-ci/jdbc.properties", PRIVATE + "/browser.bin", PRIVATE + "/browser.zip"], capture=True))
            run(oracle + ["--archive", PRIVATE + "/browser.zip", "--proof", PRIVATE + "/browser-oracle.json"])
            status["browserOracle"] = read_json(PRIVATE + "/browser-oracle.json")
            status["browserRecipientDecryption"] = decrypted
            stage("INDEPENDENT_LEDGER_AND_NATIVE_SOURCE_VERIFICATION")
            verification = json_result(run(fixture_command + ["verify", "/run/cds-ci/jdbc.properties", cli_run, browser["runId"]], capture=True))
            if verification.get("verified") is not True:
                raise RuntimeError("Independent ledger/source verification failed")
            status["ledgerAndSource"] = verification
            # Check retirement of root attestations without removing another process's tickets.
            tickets = run(["python3", "-c",
                "import pathlib;p=pathlib.Path('/run/carlos-cds-export-delegations');print(sum(1 for f in p.iterdir()) if p.exists() else 0)"], capture=True)
            if int(tickets.stdout.strip()) != 0:
                raise RuntimeError("Root export attestation was not retired")
            WAR.verify_archive_binding(war, status["warSha256"])
            status["installedWarAfter"] = verify_installed()
            status["status"] = "PASSED"
            stage("QUALIFIED_EXACT_CANDIDATE")
            print("PASS exact installed CLI and authenticated asynchronous CDS export; evidence:", public)
            return 0
    except Exception as failure:
        status["status"] = "FAILED"
        status["failureClass"] = type(failure).__name__
        save()
        print("CDS installed export failed at", status["stage"], "(" + type(failure).__name__ + ")")
        return 1
    finally:
        # Fixtures and keys exist only in these newly created, exclusively owned containers.
        # No existing install/database is accepted; public evidence omits all secret/source bytes.
        if owned_runtime:
            diagnostic = subprocess.run(docker + ["exec", "--user", "0", runtime, "python3", "-c",
                "import json,pathlib,re;p=pathlib.Path('/opt/cds-tomcat-ci/logs/catalina.log');s=p.read_text(errors='replace')[-2000000:] if p.is_file() else '';"
                "print(json.dumps({'exceptionClasses':list(dict.fromkeys(re.findall(r'\\b(?:[a-zA-Z_$][a-zA-Z0-9_$]*\\.)*[a-zA-Z_$][a-zA-Z0-9_$]*(?:Exception|Error)\\b',s)))[-100:],"
                "'applicationFrames':list(dict.fromkeys(re.findall(r'\\bat (?:io\\.github\\.carlos_emr\\.[A-Za-z0-9_.$]+)\\([A-Za-z0-9_.]+:\\d+\\)',s)))[-100:]}))"],
                text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30)
            if diagnostic.returncode == 0:
                (public / "application-startup-codes.json").write_text(diagnostic.stdout)
        cleanup = []
        with (private / "cleanup.log").open("w") as out:
            for owned, command in (
                (owned_runtime, ["rm", "--force", "--volumes", runtime]),
                (owned_db, ["rm", "--force", "--volumes", database]),
                (build_connected, ["network", "disconnect", network, args.build_container]),
                (owned_network, ["network", "rm", network]),
            ):
                if owned and subprocess.run(docker + command, stdout=out, stderr=out).returncode:
                    cleanup.append(command[0])
        if log_path.exists():
            data = log_path.read_text(errors="replace")
            codes = re.findall(r"\bCDS_[A-Z0-9_]+(?::[A-Za-z0-9_.:-]+)*", data)
            classes = re.findall(r"\b(?:[a-zA-Z_$][a-zA-Z0-9_$]*\.)*[a-zA-Z_$][a-zA-Z0-9_$]*(?:Exception|Error)\b", data)
            frames = re.findall(r"\bat (?:io\.github\.carlos_emr\.[A-Za-z0-9_.$]+)\([A-Za-z0-9_.]+:\d+\)", data)
            compile_locations = re.findall(r"[\w/.-]+\.java:\d+(?::\d+)?", data)
            (public / "failure-codes.json").write_text(json.dumps({
                "codes": list(dict.fromkeys(codes))[-100:], "exceptionClasses": list(dict.fromkeys(classes))[-100:],
                "applicationFrames": list(dict.fromkeys(frames))[-100:], "compileLocations": list(dict.fromkeys(compile_locations))[-50:]
            }, indent=2))
        # Remove only credentials owned by this harness. No secrets or traces are artifact inputs.
        for name in ("password", "client.cnf", "jdbc.properties", "browser-options.json", "browser.bin", "tls.crt"):
            (private / name).unlink(missing_ok=True)
        if cleanup:
            status.update(status="FAILED", cleanupFailed=True)
            save()
            raise RuntimeError("Owned CI environment cleanup failed")


if __name__ == "__main__":
    raise SystemExit(main())
