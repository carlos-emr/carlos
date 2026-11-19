#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Generates probe_setup.sql: one fixture table covering every column type CARLOS uses, with edge values.

Row k takes value[k % len(values)] for each column, so every value of every column appears at least once.
"""
import sys

LONG = "L" * 300

COLS = [
    # name, ddl, values (SQL literals)
    ("c_tinyint1", "tinyint(1)", ["1", "NULL", "0", "-1", "2", "127", "1", "0"]),
    ("c_tinyint1u", "tinyint(1) unsigned", ["1", "NULL", "0", "2", "255", "1", "0", "1"]),
    ("c_tinyint4", "tinyint(4)", ["5", "NULL", "0", "-1", "127", "-128", "1", "100"]),
    ("c_smallint", "smallint(6)", ["5", "NULL", "0", "-1", "32767", "-32768", "1", "100"]),
    ("c_mediumint", "mediumint(9)", ["5", "NULL", "0", "-1", "8388607", "-8388608", "1", "100"]),
    ("c_int", "int(11)", ["5", "NULL", "0", "-1", "2147483647", "-2147483648", "1", "100"]),
    ("c_intu", "int(10) unsigned", ["5", "NULL", "0", "4294967295", "2147483648", "1", "100", "7"]),
    ("c_bigint", "bigint(20)", ["5", "NULL", "0", "-1", "9223372036854775807", "-9223372036854775808", "1", "100"]),
    ("c_bigintu", "bigint(20) unsigned", ["5", "NULL", "0", "18446744073709551615", "9223372036854775808", "1"]),
    ("c_bit1", "bit(1)", ["b'1'", "NULL", "b'0'"]),
    ("c_bit8", "bit(8)", ["b'10100101'", "NULL", "b'0'", "b'1'"]),
    ("c_dec102", "decimal(10,2)", ["100.00", "NULL", "0.00", "-1.50", "12345678.99", "0.10", "1.00"]),
    ("c_dec100", "decimal(10,0)", ["100", "NULL", "0", "-1", "1234567890"]),
    ("c_float", "float", ["100", "NULL", "0", "-1.5", "1.1", "3.4e38", "1e-7", "1e10", "0.1", "123456.7", "1", "2.5"]),
    ("c_double", "double", ["100", "NULL", "0", "-1.5", "1.1", "1e10", "1e-7", "1.7976931348623157e308", "0.1",
                             "123456789.123", "1e7", "9999999", "10000000.5", "1", "2.5", "1e15", "1e16", "1e22", "0.001", "0.0001"]),
    ("c_date", "date", ["'2020-01-15'", "NULL", "'0000-00-00'", "'2020-00-00'", "'1899-12-31'", "'9999-12-31'",
                         "'2026-03-08'", "'0001-01-01'", "'1970-01-01'", "'2020-02-29'"]),
    ("c_datetime", "datetime", ["'2020-01-15 10:11:12'", "NULL", "'0000-00-00 00:00:00'", "'2020-00-00 00:00:00'",
                                 "'2026-03-08 02:30:00'", "'2026-11-01 01:30:00'", "'1899-12-31 23:59:59'",
                                 "'9999-12-31 23:59:59'", "'2020-01-15 00:00:00'", "'1970-01-01 00:00:00'",
                                 "'2020-07-15 10:11:12'"]),
    ("c_datetime6", "datetime(6)", ["'2020-01-15 10:11:12.123456'", "NULL", "'0000-00-00 00:00:00.000000'",
                                     "'2020-01-15 10:11:12.000000'", "'2020-01-15 10:11:12.100000'"]),
    ("c_timestamp", "timestamp NULL DEFAULT NULL", ["'2020-01-15 10:11:12'", "NULL", "'0000-00-00 00:00:00'",
                                                     "'2026-03-08 03:30:00'", "'2026-11-01 01:30:00'",
                                                     "'1970-01-01 00:00:01'", "'2020-07-15 10:11:12'"]),
    ("c_timestamp6", "timestamp(6) NULL DEFAULT NULL", ["'2020-01-15 10:11:12.123456'", "NULL", "'2020-01-15 10:11:12.000000'"]),
    ("c_time", "time", ["'10:11:12'", "NULL", "'00:00:00'", "'23:59:59'", "'-01:00:00'", "'25:00:00'", "'838:59:59'", "'12:00:00'"]),
    ("c_time6", "time(6)", ["'10:11:12.123456'", "NULL", "'00:00:00.000000'"]),
    ("c_year", "year", ["2020", "NULL", "0000", "1901", "2155"]),
    ("c_char1", "char(1)", ["'Y'", "NULL", "''", "'N'", "'1'", "'0'", "'é'", "'T'", "'F'", "' '", "'y'", "'n'"]),
    ("c_char10", "char(10)", ["'ab'", "NULL", "''", "'12'", "'2020-01-15'", "'10:11:12'"]),
    ("c_varchar", "varchar(255)", ["'hello'", "NULL", "''", "'12'", "'12.5'", "' 7 '", "'1e3'", "'abc'", "'true'",
                                    "'false'", "'Y'", "'N'", "'0'", "'-1'", "'2020-01-15'", "'2020-01-15 10:11:12'",
                                    "'0000-00-00'", "'20200115'", "'10:11:12'", "'héllo ✓ 😀'", "'+5'", "'1.0'", "'00012'",
                                    "'9223372036854775808'", "'yes'", "'no'", "'.5'", "'12abc'", "'1'", "'2020-01-15 10:11:12.5'",
                                    "'0000-00-00 00:00:00'", "'2020-13-45'", "'15/01/2020'", "'T'", "'F'", "'on'"]),
    ("c_tinytext", "tinytext", ["'text value'", "NULL", "''", "'42'"]),
    ("c_text", "text", ["'text value'", "NULL", "''", "'42'", "'héllo ✓ 😀'", "'" + LONG + "'", "'2020-01-15'", "'1.5'"]),
    ("c_mediumtext", "mediumtext", ["'text value'", "NULL", "''", "'42'"]),
    ("c_longtext", "longtext", ["'text value'", "NULL", "''", "'42'"]),
    ("c_tinyblob", "tinyblob", ["'blob text'", "NULL", "''", "'42'", "X'C3A9E29C93'", "X'FFFE00'"]),
    ("c_blob", "blob", ["'blob text'", "NULL", "''", "'42'", "X'C3A9E29C93'", "X'FFFE00'", "'SELECT * FROM demographic'", "'1.5'"]),
    ("c_mediumblob", "mediumblob", ["'blob text'", "NULL", "''", "X'FFFE00'"]),
    ("c_longblob", "longblob", ["'blob text'", "NULL", "''", "X'FFFE00'"]),
    ("c_binary4", "binary(4)", ["X'01020304'", "NULL", "'ab'", "X'00000000'", "'12'"]),
    ("c_varbinary", "varbinary(255)", ["'vb'", "NULL", "''", "X'FF00'", "'12'"]),
    ("c_enum", "enum('CONSULTATION','E_FORM','PRESCRIPTION')", ["'E_FORM'", "NULL", "'CONSULTATION'"]),
    ("c_set", "set('a','b','c')", ["'a,b'", "NULL", "''"]),
    ("c_json", "json", ["'{\"a\":1}'", "NULL", "'[1,2]'"]),
]

rows = max(len(v) for _, _, v in COLS)
out = []
out.append("SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;")
out.append("SET SESSION sql_mode='';")
out.append("DROP TABLE IF EXISTS zz_probe;")
out.append("CREATE TABLE zz_probe (\n  id int(11) NOT NULL PRIMARY KEY,\n" +
           ",\n".join("  %s %s" % (n, d) for n, d, _ in COLS) +
           "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;")
for k in range(rows):
    vals = [v[k % len(v)] for _, _, v in COLS]
    out.append("INSERT INTO zz_probe (id, %s) VALUES (%d, %s);" % (", ".join(n for n, _, _ in COLS), k + 1, ", ".join(vals)))
# scratch tables for write / statement probes
out.append("DROP TABLE IF EXISTS zz_write;")
out.append("""CREATE TABLE zz_write (
  id int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tag varchar(80) DEFAULT NULL,
  w_varchar varchar(255) DEFAULT NULL,
  w_char1 char(1) DEFAULT NULL,
  w_int int(11) DEFAULT NULL,
  w_tinyint1 tinyint(1) DEFAULT NULL,
  w_bit1 bit(1) DEFAULT NULL,
  w_date date DEFAULT NULL,
  w_datetime datetime DEFAULT NULL,
  w_timestamp timestamp NULL DEFAULT NULL,
  w_time time DEFAULT NULL,
  w_double double DEFAULT NULL,
  w_dec decimal(10,2) DEFAULT NULL,
  w_text text DEFAULT NULL,
  w_blob blob DEFAULT NULL,
  UNIQUE KEY uq_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;""")
out.append("DROP TABLE IF EXISTS zz_lock;")
out.append("CREATE TABLE zz_lock (id int NOT NULL PRIMARY KEY, v int NOT NULL DEFAULT 0, s varchar(5) DEFAULT NULL) ENGINE=InnoDB;")
out.append("INSERT INTO zz_lock VALUES (1,0,'a'),(2,0,'b'),(3,0,'c');")
out.append("DROP TABLE IF EXISTS zz_child; DROP TABLE IF EXISTS zz_parent;")
out.append("CREATE TABLE zz_parent (id int NOT NULL PRIMARY KEY) ENGINE=InnoDB;")
out.append("CREATE TABLE zz_child (id int NOT NULL PRIMARY KEY, pid int NOT NULL, CONSTRAINT fk_zz FOREIGN KEY (pid) REFERENCES zz_parent(id)) ENGINE=InnoDB;")
out.append("DROP PROCEDURE IF EXISTS zz_proc;")
out.append("CREATE PROCEDURE zz_proc() SELECT 1 AS one;")
sys.stdout.write("\n".join(out) + "\n")
sys.stderr.write("columns=%d rows=%d\n" % (len(COLS), rows))
