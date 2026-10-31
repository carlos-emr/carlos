#!/usr/bin/env python3
"""Exercise the demo replay against a NEW owned MariaDB schema, which is retained.

Set MYCARLOS_REPLAY_ADMIN_JSON to an explicitly authorized private JSON containing
username/password/port, and MYCARLOS_REPLAY_REPORT_DIR to a private evidence folder.
Admin access only creates a fresh schema and its scoped principal. All test SQL then
uses that principal. No existing schema is selected and no schema is dropped.
Run with HEAVY_SLOTS=1 through the shared heavy runner.
"""

import hashlib
import ipaddress
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[3]
SCRIPT = Path(__file__).with_name("reapply_security_migrations.sh")
MIGRATIONS = (
    "V1.0.81__mycarlos_accepted_device_approval.sql",
    "V1.0.82__mycarlos_sender_outbox.sql",
)
OBJECTS = "('_portal.mycarlos.device_approval','_mycarlos_delivery')"


@unittest.skipUnless(os.environ.get("MYCARLOS_REPLAY_ADMIN_JSON"),
                     "requires an explicitly authorized private MariaDB admin connection")
class SecurityReplayMariaDbTest(unittest.TestCase):
    def setUp(self):
        self.assertEqual(os.environ.get("HEAVY_SLOTS"), "1", "Use the shared heavy runner")
        os.umask(0o077)
        self.report = Path(os.environ["MYCARLOS_REPLAY_REPORT_DIR"])
        self.report.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.assertFalse((self.report / "started.json").exists(), "Retain an earlier attempt")
        self.host = os.environ.get("MYCARLOS_REPLAY_HOST", "localhost")
        self.assertIn(self.host, ("localhost", "127.0.0.1", "::1", "db"))
        admin = json.loads(Path(os.environ["MYCARLOS_REPLAY_ADMIN_JSON"]).read_text())
        suffix = uuid.uuid4().hex[:16]
        self.schema = "a3_security_replay_" + suffix
        user = "a3_replay_" + suffix
        self.credentials = {"username": user, "password": secrets.token_urlsafe(36),
                            "port": admin["port"]}
        self.assertEqual(self.sql("SELECT COUNT(*) FROM information_schema.SCHEMATA "
                                  f"WHERE SCHEMA_NAME='{self.schema}';", admin, None), "0")
        self.assertEqual(self.sql(f"SELECT COUNT(*) FROM mysql.user WHERE User='{user}';",
                                  admin, None), "0")
        client_host = self.sql("SELECT SUBSTRING_INDEX(USER(),'@',-1);", admin, None)
        ipaddress.ip_address(client_host)
        self.private_json("credentials.private.json", {**self.credentials, "schema": self.schema})
        self.private_json("started.json", {"schema": self.schema, "retained": True})
        self.sql(f"CREATE DATABASE `{self.schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;",
                 admin, None)
        self.sql(f"CREATE USER '{user}'@'{client_host}' IDENTIFIED BY "
                 f"'{self.credentials['password']}'; GRANT ALL PRIVILEGES ON `{self.schema}`.* "
                 f"TO '{user}'@'{client_host}';", admin, None)
        self.assertEqual(self.sql("SELECT DATABASE();"), self.schema)
        self.sql("SELECT 1;", database="carlos", success=False)
        self.assertTrue(b"Access denied" in self.last_stderr,
                        "The scoped principal must be denied access to the existing schema")

    def private_json(self, name, value):
        path = self.report / name
        path.write_text(json.dumps(value, indent=2) + "\n")
        path.chmod(0o600)

    def command(self, credentials=None, database=True):
        credentials = credentials or self.credentials
        args = [shutil.which("mariadb"), "--protocol=TCP", "--host=" + self.host,
                "--port=" + str(credentials["port"]), "--user=" + credentials["username"],
                "--batch", "--raw", "--skip-column-names"]
        if database is True:
            args += ["--database=" + self.schema]
        elif database is not None:
            args += ["--database=" + database]
        return args

    def sql(self, text, credentials=None, database=True, success=True):
        credentials = credentials or self.credentials
        done = subprocess.run(self.command(credentials, database), input=text.encode(),
                              capture_output=True, timeout=60,
                              env={**os.environ, "MYSQL_PWD": credentials["password"]})
        self.last_stderr = done.stderr
        if done.stderr:
            with (self.report / "sql-errors.private.log").open("ab") as log:
                os.chmod(log.name, 0o600)
                log.write(done.stderr)
        self.assertEqual(done.returncode == 0, success,
                         "Unexpected SQL result; inspect the private diagnostic")
        return done.stdout.decode().strip()

    def snapshot(self):
        tables = self.sql("SELECT TABLE_NAME FROM information_schema.TABLES "
                          "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;").splitlines()
        definitions, data = {}, {}
        for table in tables:
            self.assertTrue(table.replace("_", "").isalnum())
            definitions[table] = self.sql(f"SHOW CREATE TABLE `{table}`;")
            data[table] = sorted(self.sql(f"SELECT * FROM `{table}`;").splitlines())
        routines = self.sql("SELECT ROUTINE_NAME,ROUTINE_DEFINITION "
                            "FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE() "
                            "ORDER BY ROUTINE_NAME;")
        return definitions, data, routines

    def replay(self, directory, success=True):
        done = subprocess.run(["sh", str(SCRIPT), str(directory), self.schema]
                              + self.command(database=None)[1:], capture_output=True, timeout=60,
                              env={**os.environ, "MYSQL_PWD": self.credentials["password"]})
        with (self.report / "replay.private.log").open("ab") as log:
            os.chmod(log.name, 0o600)
            log.write(done.stdout + done.stderr)
        self.assertEqual(done.returncode == 0, success,
                         "Unexpected replay result; inspect the private diagnostic")
        return done

    def test_restore_security_without_replaying_schema_or_retained_data(self):
        self.sql("""
            CREATE TABLE demographic (demographic_no INT PRIMARY KEY) ENGINE=InnoDB;
            INSERT INTO demographic VALUES (6001);
            CREATE TABLE secObjectName (objectName VARCHAR(100) NOT NULL DEFAULT '',
              description VARCHAR(60),orgapplicable TINYINT(1) DEFAULT 0,
              PRIMARY KEY(objectName)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
              COLLATE=utf8mb4_general_ci;
            CREATE TABLE secObjPrivilege (roleUserGroup VARCHAR(30) NOT NULL DEFAULT '',
              objectName VARCHAR(100) NOT NULL DEFAULT '',
              privilege VARCHAR(100) NOT NULL DEFAULT '|0|',priority INT DEFAULT 0,
              provider_no VARCHAR(6),PRIMARY KEY(roleUserGroup,objectName)) ENGINE=InnoDB
              DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
            CREATE TABLE replay_sentinel (id INT PRIMARY KEY,note VARCHAR(40)) ENGINE=InnoDB;
            INSERT INTO replay_sentinel VALUES (1,'FAKE retained replay sentinel');
        """)
        sources = {name: ROOT / "database/mysql/migration/common" / name for name in MIGRATIONS}
        hashes = {name: hashlib.sha256(path.read_bytes()).hexdigest()
                  for name, path in sources.items()}
        for path in sources.values():
            self.sql(path.read_text())
        self.sql("""
            INSERT INTO mycarlos_patient_binding (clinic_id,demographic_no,recipient_id,
              public_portal_base_url,internal_portal_origin,config_binding_sha256,
              root_device_key_id,created_at,version) VALUES ('FAKE-replay-clinic',6001,
              'FAKE-replay-person','https://localhost/patient','https://localhost',
              UNHEX(REPEAT('11',32)),REPEAT('A',43),'2026-10-26 00:00:00',0);
            INSERT INTO mycarlos_delivery_original (clinic_id,delivery_id,original_sha256,
              encrypted_original,created_at) VALUES ('FAKE-replay-clinic',
              '39d10c50-6ddb-4164-8824-f4d540c1589c',UNHEX(REPEAT('22',32)),REPEAT('F',41),1);
        """)
        seeded = self.snapshot()
        self.assertEqual(self.sql("SELECT COUNT(*) FROM secObjectName;"), "2")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM secObjPrivilege;"), "2")
        # Every row removed here was inserted by this test in this newly created schema.
        self.sql(f"DELETE FROM secObjPrivilege WHERE objectName IN {OBJECTS}; "
                 f"DELETE FROM secObjectName WHERE objectName IN {OBJECTS};")
        self.assertEqual(self.sql("SELECT COUNT(*) FROM secObjectName;"), "0")
        with tempfile.TemporaryDirectory(prefix="a3-security-replay-") as folder:
            directory = Path(folder)
            (directory / "common").mkdir()
            (directory / "on").mkdir()
            for name, path in sources.items():
                shutil.copyfile(path, directory / "common" / name)
            self.replay(directory)
            self.assertEqual(self.snapshot(), seeded)
            self.replay(directory)
            self.assertEqual(self.snapshot(), seeded)
            # Existing customized security rows must remain authoritative.
            self.sql("UPDATE secObjectName SET description='FAKE custom description',"
                     "orgapplicable=1 WHERE objectName='_portal.mycarlos.device_approval'; "
                     "UPDATE secObjPrivilege SET privilege='r',priority=42,provider_no=NULL "
                     "WHERE objectName='_portal.mycarlos.device_approval';")
            customized = self.snapshot()
            self.replay(directory)
            self.assertEqual(self.snapshot(), customized)
            for name in MIGRATIONS:
                copy = directory / "common" / name
                copy.write_text(sources[name].read_text()
                                + "\nCREATE TABLE unexpected_replay_ddl (id INT);\n")
                refused = self.replay(directory, success=False)
                self.assertIn(b"Refusing changed myCarlos security seed layout", refused.stderr)
                self.assertEqual(self.snapshot(), customized)
                shutil.copyfile(sources[name], copy)
        # The original full migrations still reject existing tables; the adapter did not
        # weaken either preflight. Remove only the guard routines these failed calls create.
        for number, name in zip((81, 82), MIGRATIONS):
            self.sql(sources[name].read_text(), success=False)
            self.assertTrue(b"tables already exist; inspect partial DDL" in self.last_stderr,
                            "The full migration must refuse at its retained-table preflight")
            routine = ("migrate_mycarlos_approval_v81_preflight" if number == 81
                       else "migrate_mycarlos_sender_v82_preflight")
            self.sql(f"DROP PROCEDURE `{routine}`;")
            self.assertEqual(self.snapshot(), customized)
        self.assertEqual({name: hashlib.sha256(path.read_bytes()).hexdigest()
                          for name, path in sources.items()}, hashes)
        self.private_json("result.json", {"schema": self.schema, "retained": True,
                          "first_apply": True, "security_restored": True,
                          "repeat_replay_unchanged": True, "custom_grants_preserved": True,
                          "changed_tails_refused": 2, "full_ddl_replays_refused": 2,
                          "schema_and_retained_data_unchanged": True,
                          "migration_hashes_unchanged": hashes})


if __name__ == "__main__":
    unittest.main()
