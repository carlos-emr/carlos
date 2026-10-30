-- SPDX-License-Identifier: GPL-2.0-or-later
-- Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
-- Synthetic isolated-schema regression data; never load into a clinic schema.
-- Used by verify_sql_semantics.py. Hex avoids dependence on terminal encoding.
CREATE TABLE drugs (
  drugid INT PRIMARY KEY,
  special TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  comment TEXT CHARACTER SET utf8mb4
);
CREATE TABLE expected_drug_special (
  drugid INT PRIMARY KEY, expected_hex TEXT, must_block BOOLEAN NOT NULL
);
INSERT INTO drugs VALUES
  (101, CONVERT(UNHEX('C3A2C280C293') USING utf8mb4), 'unchanged'),
  (102, CONVERT(UNHEX('C3A2C289C2A4') USING utf8mb4), 'unchanged'),
  (103, CONVERT(UNHEX('436166C3A920E6BCA2E5AD9720E2809320E289A4') USING utf8mb4), 'unchanged'),
  (104, NULL, 'unchanged'),
  (105, '', 'unchanged'),
  (106, '1,800', 'unchanged'),
  (107, CONVERT(UNHEX('436166C383C2A9') USING utf8mb4), 'unchanged'),
  (108, CONVERT(UNHEX('C3A2C280C294') USING utf8mb4), 'unchanged'),
  (109, CONVERT(UNHEX('436166C383C2A920C3A2C280C293') USING utf8mb4), 'unchanged'),
  (110, CONVERT(UNHEX('C383C2A2C382E282ACC382E2809C') USING utf8mb4), 'unchanged'),
  (111, CONVERT(UNHEX('C3A2C280') USING utf8mb4), 'unchanged');
INSERT INTO expected_drug_special VALUES
  (101, 'E28093', 0), (102, 'E289A4', 0),
  (103, '436166C3A920E6BCA2E5AD9720E2809320E289A4', 0),
  (104, NULL, 0), (105, '', 0), (106, '312C383030', 0),
  (107, '436166C3A9', 0),
  (108, NULL, 1), (109, NULL, 1), (110, NULL, 1), (111, NULL, 1);
