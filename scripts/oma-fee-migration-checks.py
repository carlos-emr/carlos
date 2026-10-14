#!/usr/bin/env python3
"""Exercise the OMA migration against an isolated MariaDB database.

Run from a checkout with a MariaDB account allowed to create/drop test databases:
  python3 scripts/oma-fee-migration-checks.py
Set MYSQL_DEFAULTS_FILE to an existing client option file when credentials are needed.
Only a newly created, randomly named database is changed; it is removed on completion.
"""
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent
DATABASE = "carlos_oma_test_" + uuid.uuid4().hex
CLIENT = ["mariadb"]
if os.environ.get("MYSQL_DEFAULTS_FILE"):
    CLIENT.append("--defaults-extra-file=" + os.environ["MYSQL_DEFAULTS_FILE"])
CLIENT.extend(["--batch", "--skip-column-names"])


def query(sql, database=None):
    result = subprocess.run(CLIENT + ([database] if database else []), input=sql,
                            text=True, capture_output=True, check=True)
    return result.stdout.strip()


def expect(sql, expected):
    actual = query(sql, DATABASE)
    assert actual == expected, f"Expected {expected!r}, got {actual!r}: {sql}"


def main():
    schema = (ROOT / "database/mysql/migration/common/V1__baseline_schema.sql").read_text()
    migration = (ROOT / "database/mysql/migration/on/V1.0.34__add_oma_uninsured_service_fees.sql").read_text()
    query(f"CREATE DATABASE `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
    try:
        for table in ("billingservice", "ctl_billingservice"):
            ddl = re.search(r"CREATE TABLE `" + table + r"` \(.*?;", schema, re.S).group(0)
            query(ddl, DATABASE)
        query("""
INSERT INTO ctl_billingservice
 (servicetype_name,servicetype,service_code,service_group_name,service_group,status,service_order) VALUES
 ('PRIVATE','PRI','A007A',' Group 1 Name','Group1','A',1),
 ('PRIVATE','PRI','A007A',' Group 2 Name','Group2','A',1),
 ('PRIVATE','PRI','A007A',' Group 3 Name','Group3','A',1),
 ('CUSTOM','PRI','A007A',' Group 1 Name','Group1','A',1),
 ('PRIVATE','PRI','A007A',' Group 1 Name','Custom','A',1),
 ('PRIVATE','PRI','A007A',' Group 1 Name','Group1','A',2),
 ('PRIVATE','PRI','A007A','Clinic assessment','Group1','A',1),
 ('PRIVATE','PRI','_OMA_A003','Custom','Custom','I',99);
INSERT INTO billingservice
 (service_code,description,value,billingservice_date,gstFlag,sliFlag,displaystyle) VALUES
 ('_OMA_A003','stale','1.00','2026-01-01',1,1,3),
 ('_OMA_A003','duplicate stale','2.00','2026-01-01',1,1,3),
 ('_OMA_A003','Clinic later rate','999.00','2026-02-01',1,1,3);
""", DATABASE)
        query(migration, DATABASE)
        expect("SELECT COUNT(DISTINCT service_code) FROM billingservice", "34")
        expect("SELECT COUNT(*) FROM billingservice", "36")
        expect("SELECT COUNT(*) FROM ctl_billingservice WHERE service_code='A007A' AND status='I'", "3")
        expect("SELECT COUNT(*) FROM ctl_billingservice WHERE service_code='A007A' AND status='A'", "4")
        expect("SELECT COUNT(*) FROM ctl_billingservice WHERE service_code='_OMA_A003' AND status='I' AND service_order=99 AND service_group='Custom'", "1")
        expect("SELECT COUNT(*) FROM billingservice WHERE service_code='_OMA_A003' AND billingservice_date='2026-01-01' AND value='253.35' AND gstFlag=1 AND sliFlag=1 AND displaystyle=3", "2")
        expect("SELECT value FROM billingservice WHERE service_code='_OMA_A003' AND billingservice_date='2026-02-01'", "999.00")
        for code, amount in (("F08", "160.00"), ("F18", "497.00"), ("F19", "497.00"), ("G010", "7.70")):
            expect(f"SELECT value FROM billingservice WHERE service_code='_OMA_{code}'", amount)
        before = query("SELECT * FROM billingservice ORDER BY billingservice_no; SELECT * FROM ctl_billingservice ORDER BY id", DATABASE)
        query(migration, DATABASE)
        after = query("SELECT * FROM billingservice ORDER BY billingservice_no; SELECT * FROM ctl_billingservice ORDER BY id", DATABASE)
        assert before == after, "Rerun changed the resulting data"
        print("PASS: 34 fees, corrected amounts, exact placeholder cleanup, duplicate correction, clinic overrides, flags and rerun idempotence")
    finally:
        query(f"DROP DATABASE `{DATABASE}`")


if __name__ == "__main__":
    main()
