#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Check importer subprocess transport on a disposable MariaDB server.

Usage: python3 scripts/migration/o19/verify_client_transport.py \
    --socket=/tmp/disposable-mariadb.sock

Requires local root database access without a password. Creates and removes
ONLY the o19_import schema/account; refuses if either already exists.
Controls execute a harmless echo locally and show why the former options
were unsafe. Never point this development check at a clinic's server.
"""

import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "debian/assets"))
from carlos_ctl import (o19docs, o19host, o19import,            # noqa: E402
                        o19_preflight)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--socket", required=True)
    args = parser.parse_args()
    base = ["mariadb", "--no-defaults", "--socket=" + args.socket, "-uroot"]
    query = o19import.make_query(base[1:])
    if query("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA "
             "WHERE SCHEMA_NAME='o19_import'") or query(
                 "SELECT User FROM mysql.user WHERE User='o19_import'"):
        parser.error("o19_import schema/account already exists; use a "
                     "fresh disposable server")

    sql = ("SELECT CONCAT('before',CHAR(13),'after'), "
           "CONCAT('é',CHAR(9),CHAR(10)), NULL;")
    expected = [["before\rafter", "é\t\n", "NULL"]]
    assert query(sql) == expected, "buffered read changed clinic values"
    assert o19import.make_etl_query(base)(sql) == expected, "ETL read changed"
    assert list(o19import.make_row_stream(base[1:])(sql)) == expected
    raw_args = base[1:] + ["--raw"]
    assert o19import.make_query(raw_args)(sql) == expected
    assert list(o19import.make_row_stream(raw_args)(sql)) == expected
    assert o19_preflight.make_cli_query("mariadb", raw_args, "mysql")(
        sql) == expected
    old = subprocess.run(base + ["-N", "-B"], input=sql, text=True,
                         capture_output=True, check=True)
    assert o19import.batch_rows(old.stdout) != expected, "control failed"
    print("PASS: buffered/ETL/streaming readers agree; old transport fails")

    query("CREATE DATABASE o19_import")
    with tempfile.TemporaryDirectory(prefix="o19-client-check-") as work:
        cnf = os.path.join(work, "client.cnf")
        try:
            env = o19import.grant_staging_account(query, cnf)
            host = o19host.Host()
            # A deployment's inherited --force must not mask SQL errors.
            argv = host.staging_client_argv(
                ["mariadb", "--socket=" + args.socket, "--force"], cnf)

            def restore(command, sql_bytes):
                return subprocess.run(command, input=sql_bytes,
                                      capture_output=True,
                                      env=dict(os.environ, **env))

            marker = b"O19_HARMLESS_CLIENT_COMMAND_CONTROL"
            local = b"\\! echo " + marker + b"\nSELECT 1;\n"
            old_argv = [a for a in argv if a not in
                        ("--binary-mode", "--skip-force")]
            control = restore(old_argv, local)
            assert marker in control.stdout, "local command control failed"
            hardened = restore(argv, local)
            assert hardened.returncode != 0, "local command was accepted"
            assert marker not in hardened.stdout, "local command executed"
            print("PASS: local dump command refused; old client executes it")

            bad = b"INSERT INTO definitely_absent VALUES (1);\nSELECT 1;\n"
            assert restore(old_argv, bad).returncode == 0, "force control"
            assert restore(argv, bad).returncode != 0, "SQL failure masked"
            print("PASS: SQL errors fail despite inherited force")

            # Binary mode still accepts normal dump syntax, including
            # escaped NUL, multiline routines, and Windows line endings.
            normal = (b"CREATE TABLE payload (v LONGBLOB);\r\n"
                      b"INSERT INTO payload VALUES ('a\\0b\\r\\nc');\r\n"
                      b"DELIMITER ;;\nCREATE PROCEDURE check_payload()\n"
                      b"BEGIN SELECT HEX(v) FROM payload; END;;\n"
                      b"DELIMITER ;\nCALL check_payload();\n")
            cp = restore(argv, normal)
            assert cp.returncode == 0, cp.stderr.decode("utf-8", "replace")
            assert query("SELECT HEX(v) FROM o19_import.payload") == [
                ["6100620D0A63"]], "restored bytes changed"
            print("PASS: ordinary dump, delimiters and binary values restore")
            original_packet = query("SELECT @@GLOBAL.max_allowed_packet")[0][0]
            try:
                query("SET GLOBAL max_allowed_packet=16777216")
                query("TRUNCATE o19_import.payload")
                query("INSERT INTO o19_import.payload "
                      "VALUES (REPEAT('x', 9*1024*1024))")
                # On 11.8 the rendering, but not the stored blob, is NULL.
                refused = query("SELECT HEX(v) IS NULL, v IS NULL "
                                "FROM o19_import.payload") == [["1", "0"]]
                out = os.path.join(work, "csv")
                if refused:
                    try:
                        o19docs.export_archive_csv(query, "o19_import", out)
                    except SystemExit as exc:
                        assert exc.code != 0
                    else:
                        raise AssertionError("failed HEX was exported")
                    print("PASS: oversized HEX is refused, not exported "
                          "as NULL text")
                else:
                    print("INFO: server does not bound HEX; see unit "
                          "regression for the MariaDB 11.8 refusal")
                query("SET GLOBAL max_allowed_packet=67108864")
                o19docs.export_archive_csv(query, "o19_import", out)
                data = Path(out, "payload.csv").read_bytes()
                assert data.count(b"78") == 9 * 1024 * 1024
                print("PASS: larger packet limit exports every blob byte")
            finally:
                query("SET GLOBAL max_allowed_packet=" + original_packet)
        finally:
            try:
                o19import.revoke_staging_account(query, cnf)
            finally:
                query("DROP DATABASE o19_import")
    return 0


if __name__ == "__main__":
    sys.exit(main())
