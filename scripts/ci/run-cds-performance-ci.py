#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Own an ephemeral installed CLI/runtime/database for one mandatory resource profile.

Never point this harness at an existing runtime or database. Large accepted
sources remain intact until the entire owned CI environment is retired. Only
whitelisted hashes/counts/resource evidence are copied to the public artifact.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import uuid
import zipfile

DATABASE_IMAGE = "mariadb:11.8@sha256:f16d7a344413e519dff518f27bdb221388f84b0d274c97c4fb5f2cb0dc1b34fa"


def disk_retirement_allowed(attempted: bool, proof: dict | None, archive_verified: bool) -> bool:
    if not attempted:
        return True
    # A partial/unknown outcome is not a zero-acceptance fixture. Preserve its
    # database as well as the source volume, even if copying its files succeeded.
    ledger=proof.get('afterLedger') if isinstance(proof,dict) else None
    required={'cds_import_run','cds_import_patient','cds_import_unit','cds_import_checkpoint','cds_import_event',
              'cds_import_lineage','maintenanceActive','reservedUploadBytes'}
    return bool(archive_verified and isinstance(proof,dict) and proof.get('status')=='PASSED' and proof.get('acceptedRuns')==0
                and isinstance(ledger,dict) and set(ledger)==required and all(value==0 for value in ledger.values()))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=("scaling", "aggregate20", "zip64", "disk-reserve", "disk-enospc"), required=True)
    parser.add_argument("--cli-source", type=Path, required=True)
    parser.add_argument("--build-container", default="carlos-tomcat-dev")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    nonce = uuid.uuid4().hex
    base = repository / "target" / ("cds-performance-" + nonce)
    base.mkdir(mode=0o700, parents=True)
    private, public, state, webapp, context = (base / key for key in ("private", "public", "state", "webapp", "context"))
    for folder in (private, public, context):
        folder.mkdir(mode=0o700)
    for folder in (state, webapp):
        folder.mkdir(mode=0o755)
    runtime, database, network = ("cds-perf-" + nonce + suffix for suffix in ("-runtime", "-db", "-net"))
    image = "carlos-cds-perf:" + nonce
    docker = ["docker", "--host=unix:///var/run/docker.sock"]
    created_runtime = created_database = created_network = build_connected = False
    disk_attempted=archive_verified=False
    profile_proof=None
    password = None
    status = {"protocol": 1, "profile": args.profile, "status": "RUNNING"}
    log_path = private / "harness.log"
    def save():
        (public / "ci-proof.json").write_text(json.dumps(status, sort_keys=True, indent=2))
    save()
    try:
        with log_path.open("w") as log:
            def execute(command, *, capture=False, check=True):
                return subprocess.run(command, text=True, stdout=subprocess.PIPE if capture else log,
                                      stderr=log, check=check)
            def build(command, **kw):
                return execute(docker + ["exec", "--workdir", "/workspace", args.build_container, *command], **kw)
            actual_cli = execute(["git", "-C", str(args.cli_source), "rev-parse", "HEAD"], capture=True).stdout.strip()
            pin = dict(line.split("=", 1) for line in (repository / "debian/carlos-ctl.pin").read_text().splitlines()
                       if line and not line.startswith("#"))
            expected_ref = pin.get("fallback")
            if actual_cli != expected_ref:
                raise RuntimeError("companion checkout does not match this candidate's exact pinned fallback")
            status.update(cliCommit=actual_cli, sourceCommit=execute(["git", "rev-parse", "HEAD"], capture=True).stdout.strip())
            shutil.copytree(args.cli_source, context / "cli", ignore=shutil.ignore_patterns(".git", "__pycache__", ".pytest_cache"))
            shutil.copyfile(repository / "scripts/ci/cds-performance-runtime.Dockerfile", context / "Dockerfile")
            status["stage"] = "RUNTIME_PACKAGE_BUILD"
            execute(docker + ["build", "-t", image, str(context)])
            # One Maven writer and a real WAR: no substitutions of test classes in the installed engine.
            status["stage"] = "CANDIDATE_WAR_BUILD"
            build(["mvn", "-B", "-DskipTests", "package"])
            wars = list((repository / "target").glob("carlos-*.war"))
            if len(wars) != 1 or wars[0].is_symlink():
                raise RuntimeError("one exact packaged candidate WAR is required")
            war = wars[0]
            with war.open("rb") as original_war:
                status["warSha256"] = hashlib.file_digest(original_war, "sha256").hexdigest()
            with zipfile.ZipFile(war) as archive:
                for member in archive.infolist():
                    target = webapp / member.filename
                    if member.filename.startswith("/") or ".." in Path(member.filename).parts or not target.resolve().is_relative_to(webapp.resolve()):
                        raise RuntimeError("candidate WAR contains an unsafe member")
                    if member.is_dir():
                        target.mkdir(parents=True, exist_ok=True, mode=0o755)
                    else:
                        target.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
                        with archive.open(member) as source, target.open("xb") as output:
                            shutil.copyfileobj(source, output, 65536)
                        target.chmod(0o644)
            status["productionClasses"] = sum(1 for _ in (webapp / "WEB-INF/classes").rglob("*.class"))
            status["productionJars"] = len(list((webapp / "WEB-INF/lib").glob("*.jar")))
            disk_fault = args.profile.startswith('disk-')
            required = (4 if args.profile == "scaling" or disk_fault else 50 if args.profile == "aggregate20" else 20) * (1 << 30)
            available = shutil.disk_usage(base).free
            status["freeBeforeRunBytes"], status["requiredFreeBytes"] = available, required
            status["stage"] = "STORAGE_PREREQUISITE"
            save()
            if available < required:
                raise RuntimeError("real runner storage prerequisite failed; a larger ephemeral runner is required")
            password = secrets.token_hex(32)
            status["stage"] = "OWNED_DATABASE_BOOTSTRAP"
            for name, value in {
                "password": password,
                "client.cnf": "[client]\nuser=root\npassword=" + password + "\n",
                "jdbc.properties": "jdbcUrl=jdbc:mysql://" + database + ":3306/carlos_cds_test\njdbcUser=root\njdbcPassword=" + password + "\n"
            }.items():
                descriptor = os.open(private / name, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                with os.fdopen(descriptor, "w") as output:
                    output.write(value)
            execute(docker + ["network", "create", "--internal", network]); created_network = True
            execute(docker + ["network", "connect", network, args.build_container]); build_connected = True
            execute(docker + ["run", "-d", "--name", database, "--network", network, "--memory", "2g", "--cpus", "2",
                "--tmpfs", "/var/lib/mysql:rw,nosuid,noexec,size=1536m", "--mount", "type=bind,src="+str(private)+",dst=/run/cds-test,readonly",
                "-e", "MARIADB_ROOT_PASSWORD_FILE=/run/cds-test/password", "-e", "MARIADB_ROOT_HOST=%", DATABASE_IMAGE,
                "--innodb-page-size=32768", "--innodb-snapshot-isolation=ON", "--character-set-server=utf8mb4",
                "--collation-server=utf8mb4_general_ci", "--innodb-strict-mode=OFF", "--innodb-default-row-format=dynamic",
                "--max-allowed-packet=64M", "--innodb-buffer-pool-size=128M", "--performance-schema=ON",
                "--performance-schema-digests-size=10000"]); created_database = True
            mysql = docker + ["exec", database, "mariadb", "--defaults-extra-file=/run/cds-test/client.cnf"]
            deadline = time.monotonic()+90
            while execute(mysql+["-e", "SELECT 1"], check=False).returncode:
                if time.monotonic() > deadline:
                    raise RuntimeError("owned database did not become ready")
                time.sleep(1)
            execute(mysql+["-e", "CREATE DATABASE carlos_cds_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"])
            cp_file = "/workspace/target/cds-performance-dependencies.txt"
            build(["mvn", "-B", "-q", "dependency:build-classpath", "-Dmdep.outputFile="+cp_file])
            cp = "/workspace/target/classes:"+(repository/"target/cds-performance-dependencies.txt").read_text().strip()
            helper = "/workspace/target/"+base.name+"/bootstrap"
            build(["mkdir", "-p", helper])
            build(["javac", "-cp", cp, "-d", helper, "scripts/ci/CdsImportMariaDbBootstrap.java", "scripts/ci/CdsWorkloadValidator.java"])
            credentials = "/workspace/target/"+base.name+"/private/jdbc.properties"
            build(["java", "-cp", helper+":"+cp, "CdsImportMariaDbBootstrap", credentials, "database/mysql/migration", "on"])
            # Publicly traversable fixture mount ancestors; private credentials remain0700/0600.
            base.chmod(0o755)
            state_mount = ["--tmpfs","/var/lib/carlos-emr:rw,nosuid,nodev,noexec,size=64m,mode=0755,uid=0,gid=0"] if disk_fault else [
                "--mount", "type=bind,src="+str(state)+",dst=/var/lib/carlos-emr"]
            execute(docker+["run", "-d", "--name", runtime, "--network", network, "--memory", "1536m", "--cpus", "2",
                "--mount", "type=bind,src="+str(repository)+",dst=/workspace,readonly",
                "--mount", "type=bind,src="+str(webapp)+",dst=/usr/share/carlos-emr/webapp/carlos,readonly",
                *state_mount,
                "--mount", "type=bind,src="+str(private/"jdbc.properties")+",dst=/run/source-jdbc.properties,readonly", image]); created_runtime = True
            # Only the owned fixture driver starts as root: the installed CLI must
            # exercise its real root-to-service delegation, whose JVM UID is checked.
            runtime_exec = docker+["exec", "--user", "0", "--workdir", "/workspace", runtime]
            execute(runtime_exec+["install", "-d", "-m", "0700", "/run/cds-test"])
            execute(runtime_exec+["install", "-m", "0600", "/run/source-jdbc.properties", "/run/cds-test/jdbc.properties"])
            runtime_cp = helper+":/workspace/target/test-classes:/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes:/usr/share/carlos-emr/webapp/carlos/WEB-INF/lib/*:/usr/share/tomcat11/lib/*"
            root = "/var/lib/carlos-emr/cds-cli-engine-check"
            execute(runtime_exec+["java", "-Xmx128m", "-cp", runtime_cp,
                "io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture", "prepare", "/run/cds-test/jdbc.properties", root])
            execute(runtime_exec+["python3", "-m", "unittest", "discover", "-s", "scripts/ci", "-p", "test_run_cds_performance.py", "-v"])
            status["stage"] = "INSTALLED_PROFILE"
            if disk_fault:
                if args.profile=='disk-enospc':
                    # Explicit fault-only policy, frozen BEFORE the first submission.
                    # The dedicated64MiB tmpfs cannot affect any other state filesystem.
                    execute(runtime_exec+["python3","-c",
                        "import pathlib;p=pathlib.Path('/etc/carlos-emr/carlos.properties');s=p.read_text();"
                        "assert 'cds.import.minimumFreeDiskBytes' not in s;"
                        "p.open('a').write('\\ncds.import.minimumFreeDiskBytes=0\\n')"])
                execute(runtime_exec+["python3","-m","unittest","discover","-s","scripts/ci","-p","test_run_cds_disk_failures.py","-v"])
                disk_attempted=True
                executed=execute(runtime_exec+["python3","scripts/ci/run-cds-disk-failures.py","--disposable",
                    "--scenario",args.profile.removeprefix('disk-'),"--fixture-metadata",root+"/fixture.json",
                    "--jdbc-properties","/run/cds-test/jdbc.properties","--evidence","/run/cds-disk-evidence",
                    "--classpath",runtime_cp,"--candidate-sha256",status['warSha256']],check=False)
                proof_path='/run/cds-disk-evidence/proof.json'
            else:
                executed = execute(runtime_exec+["python3", "scripts/ci/run-cds-performance.py", "--disposable", "--exclusive-server",
                    "--fixture-metadata", root+"/fixture.json", "--jdbc-properties", "/run/cds-test/jdbc.properties",
                    "--root", root+"/source/performance", "--classpath", runtime_cp, "--candidate-sha256", status["warSha256"],
                    "--expected-memory-mib","1536","--expected-cpus","2","--profile", args.profile], check=False)
                proof_path=root+"/source/performance/evidence/proof.json"
            # The runtime owns mode0700 evidence. Read it as that root, with a bound, not through host permissions.
            copied = execute(runtime_exec+["python3", "-c",
                "import pathlib,sys;p=pathlib.Path(sys.argv[1]);assert p.is_file() and not p.is_symlink() and p.stat().st_size<(1<<20);sys.stdout.write(p.read_text())",
                proof_path], capture=True).stdout
            proof = json.loads(copied)
            profile_proof=proof
            (public/"performance-proof.json").write_text(json.dumps(proof,sort_keys=True,indent=2))
            if disk_fault and proof.get('retainedStateArchive'):
                retained=public/'retained-preaccept.tar.gz'
                execute(docker+['cp',runtime+':/run/cds-disk-evidence/retained-preaccept.tar.gz',str(retained)])
                with retained.open('rb') as original:
                    retained_digest=hashlib.file_digest(original,'sha256').hexdigest()
                if retained.stat().st_size!=proof['retainedStateArchive']['bytes'] or retained_digest!=proof['retainedStateArchive']['sha256']:
                    raise RuntimeError('retained failed tmpfs state artifact hash/size differs')
                archive_verified=True
            if disk_fault and proof.get('rawRegisterOnlySyscallTrace'):
                syscall=public/'service-failed-syscalls.log'
                execute(docker+['cp',runtime+':/run/cds-disk-evidence/service-failed-syscalls.log',str(syscall)])
                with syscall.open('rb') as trace_source:
                    trace_digest=hashlib.file_digest(trace_source,'sha256').hexdigest()
                if syscall.stat().st_size!=proof['rawRegisterOnlySyscallTrace']['bytes'] or trace_digest!=proof['rawRegisterOnlySyscallTrace']['sha256']:
                    raise RuntimeError('raw-register-only kernel proof artifact hash/size differs')
            if proof.get("status") != "PASSED":
                raise RuntimeError("profile evidence did not pass")
            if executed.returncode:
                raise RuntimeError("installed resource profile failed")
            status["status"] = "PASSED"
            save()
        print("PASS installed CDS performance profile:",args.profile,"evidence:",public)
        return 0
    except Exception as failure:
        status["status"],status["failureClass"]="FAILED",type(failure).__name__
        save()
        print("CDS performance CI failed at",status.get("stage","SETUP"),"(",type(failure).__name__,
              "); inspect sanitized harness and prerequisite evidence")
        return 1
    finally:
        cleanup = []
        retire=disk_retirement_allowed(disk_attempted,profile_proof,archive_verified)
        if not retire:
            status.update(status='FAILED',retirementBlocked=True,retentionIncomplete=not archive_verified,
                          outcomeRequiresReconciliation=not disk_retirement_allowed(disk_attempted,profile_proof,True),
                          runtimeRetainedForReconciliation=runtime,databaseRetainedForReconciliation=database,
                          networkRetainedForReconciliation=network,protectedPrerequisitesRetained=True)
            save()
        with (private/"cleanup.log").open("w") as log:
            for owned, command in ((created_runtime,["rm","--force","--volumes",runtime]),
                                   (created_database,["rm","--force","--volumes",database]),
                                   (build_connected,["network","disconnect",network,args.build_container]),
                                   (created_network,["network","rm",network])):
                if owned and retire and subprocess.run(docker+command,stdout=log,stderr=log).returncode:
                    cleanup.append(command[0])
        if retire:
            for name in ("password","client.cnf","jdbc.properties"):
                (private/name).unlink(missing_ok=True)
        if log_path.exists():
            # All inputs are owned synthetic fixtures. Remove the sole fresh secret before publishing a bounded diagnostic tail.
            with log_path.open("rb") as source:
                source.seek(max(0,log_path.stat().st_size-(2<<20)))
                safe=source.read().decode("utf-8",errors="replace")
            if password:
                safe=safe.replace(password,"[REDACTED]")
            (public/"harness-sanitized.log").write_text(safe)
        if cleanup:
            status["status"],status["cleanupFailed"]="FAILED",True
            save()
            raise RuntimeError("owned CI environment retirement failed")


if __name__=="__main__":
    raise SystemExit(main())
