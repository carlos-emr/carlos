#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""Exercise the real consent reconciliation SQL on owned disposable schemas, including non-strict refusals."""

import argparse
import json
from pathlib import Path
import re

# Invoke only the real MariaDB client with controlled fixture SQL over stdin and no shell.
import subprocess  # nosec B404
import shutil
import uuid

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "database/mysql/migration/common"
REFUSAL = "carlos_consent_audit_shape_requires_review_before_upgrade"


def create_statement(name, marker):
    source = (MIGRATIONS / name).read_text()
    start = source.index(marker)
    return source[start : source.index(";", start) + 1]


def execute(client, statement, success=True):
    # The executable is resolved and the disposable host is validated; arguments never enter a shell.
    result = subprocess.run(client, input=statement, text=True, capture_output=True)  # nosec B603
    if success and result.returncode:
        raise RuntimeError("CONSENT_FORWARD_SQL_EXECUTION_FAILED")
    return result


def snapshot(client, database, fresh):
    query = (
        "SELECT JSON_ARRAY(consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at,id,lastUpdateUser,lastUpdateDate) FROM Consent_migration_audit ORDER BY consent_id;"
        if fresh
        else "SELECT JSON_ARRAY(consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at) FROM Consent_migration_audit ORDER BY consent_id;"
    )
    return execute(client + ["--database=" + database], query).stdout


def metadata(client, database):
    query = (
        "SELECT JSON_ARRAY(COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,ORDINAL_POSITION) "
        "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Consent_migration_audit' ORDER BY ORDINAL_POSITION; "
        "SELECT JSON_ARRAY(INDEX_NAME,COLUMN_NAME,NON_UNIQUE,SEQ_IN_INDEX) FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='Consent_migration_audit' ORDER BY INDEX_NAME,SEQ_IN_INDEX;"
    )
    return execute(client + ["--database=" + database], query).stdout


def verify(client, sql, fresh, adverse):
    database = "cds_consent_guard_" + uuid.uuid4().hex
    execute(
        client,
        "CREATE DATABASE "
        + database
        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;",
    )
    try:
        table = (
            create_statement(
                "V1.0.59.30__one_live_consent_per_type.sql",
                "CREATE TABLE IF NOT EXISTS Consent_migration_audit",
            )
            if fresh
            else create_statement(
                "V1.0.77__one_live_consent_per_type.sql",
                "CREATE TABLE IF NOT EXISTS `Consent_migration_audit`",
            )
        )
        setup = (
            "USE "
            + database
            + "; CREATE TABLE Consent(demographic_no INT,deleted TINYINT(1) NOT NULL DEFAULT 0,"
            "live_demographic_no INT GENERATED ALWAYS AS (IF(deleted = 0, demographic_no, NULL)) STORED) ENGINE=InnoDB;\n"
            + table
            + "\n"
        )
        if adverse == "identity":
            setup += "ALTER TABLE Consent_migration_audit MODIFY consent_id BIGINT NOT NULL;\n"
        if adverse == "timestamp":
            setup += "ALTER TABLE Consent_migration_audit MODIFY recorded_at DATETIME(6) NOT NULL;\n"
        if adverse == "primary":
            setup += "ALTER TABLE Consent_migration_audit DROP PRIMARY KEY, ADD PRIMARY KEY(consent_id);\n"
        identity = "2147483648" if adverse == "identity" else "7"
        timestamp = (
            "2004-01-02 03:04:05.123456"
            if adverse == "timestamp"
            else "2004-01-02 03:04:05"
        )
        setup += (
            "INSERT INTO Consent_migration_audit(consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at) VALUES("
            + identity
            + ",'synthetic_forward','preserve',NULL,1,NULL,'"
            + timestamp
            + "');"
        )
        execute(client, setup)
        before, shape = snapshot(client, database, fresh), metadata(client, database)
        result = execute(
            client,
            "USE " + database + "; SET SESSION sql_mode='';\n" + sql,
            success=False,
        )
        if adverse:
            if result.returncode == 0 or REFUSAL not in result.stderr:
                raise RuntimeError("CONSENT_FORWARD_UNREVIEWED_SHAPE_ACCEPTED")
            if metadata(client, database) != shape:
                raise RuntimeError("CONSENT_FORWARD_REFUSAL_CHANGED_METADATA")
        elif result.returncode:
            raise RuntimeError("CONSENT_FORWARD_REVIEWED_SHAPE_REFUSED")
        if snapshot(client, database, fresh) != before:
            raise RuntimeError("CONSENT_FORWARD_CHANGED_RECORDED_VALUES")
    finally:
        execute(client, "DROP DATABASE " + database + ";")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--settings", type=Path, required=True)
    parser.add_argument("--client-defaults", type=Path, required=True)
    parser.add_argument("--proof", type=Path, required=True)
    args = parser.parse_args()
    settings = dict(
        line.split("=", 1)
        for line in args.settings.read_text().splitlines()
        if "=" in line
    )
    match = re.fullmatch(
        r"jdbc:mysql://(cds-native-[0-9a-f]{32}):3306/cds_export_test(?:\?.*)?",
        settings.get("jdbcUrl", ""),
    )
    if settings.get("ownedDisposableDatabase") != "true" or not match:
        raise RuntimeError("CONSENT_FORWARD_OWNED_DATABASE_REQUIRED")
    executable = shutil.which("mariadb")
    if not executable or not Path(executable).is_absolute():
        raise RuntimeError("CONSENT_FORWARD_CLIENT_MISSING")
    client = [
        executable,
        "--defaults-extra-file=" + str(args.client_defaults),
        "--host=" + match[1],
        "--batch",
        "--raw",
        "--skip-column-names",
    ]
    sql = (
        MIGRATIONS / "V1.0.86__reconcile_forward_merged_consent_audit.sql"
    ).read_text()
    for fresh, adverse in (
        (False, None),
        (True, None),
        (False, "identity"),
        (False, "timestamp"),
        (False, "primary"),
    ):
        verify(client, sql, fresh, adverse)
    args.proof.write_text(
        json.dumps(
            {
                "status": "PASSED",
                "cases": 5,
                "validShapes": 2,
                "nonStrictModeRefusals": 3,
                "rowsPreserved": 5,
            },
            sort_keys=True,
        )
        + "\n"
    )
    print(
        "Consent forward SQL passed: two valid shapes; three non-strict refusals; all recorded values preserved."
    )


if __name__ == "__main__":
    main()
