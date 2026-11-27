-- SPDX-License-Identifier: GPL-2.0-or-later
-- Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
--
-- This software is published under the GPL GNU General Public License.
-- This program is free software; you can redistribute it and/or
-- modify it under the terms of the GNU General Public License
-- as published by the Free Software Foundation; either version 2
-- of the License, or (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with this program; if not, write to the Free Software
-- Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
--
-- CARLOS EMR Project
-- https://github.com/carlos-emr/carlos
--
-- Synthetic isolated-schema regression data; never load into a clinic schema.
-- Used by verify_sql_semantics.py. Hex avoids dependence on terminal encoding.
CREATE TABLE drugs (
  drugid INT PRIMARY KEY,
  special TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  comment TEXT CHARACTER SET utf8mb4,
  quantity VARCHAR(80), unitName VARCHAR(40), dosage VARCHAR(255),
  unit VARCHAR(40), drug_form VARCHAR(40), takemin FLOAT, takemax FLOAT,
  custom_instructions TINYINT(1)
);
CREATE TABLE expected_drug_special (
  drugid INT PRIMARY KEY, expected_hex TEXT, must_block BOOLEAN NOT NULL
);
INSERT INTO drugs (drugid, special, comment) VALUES
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

-- Controls distinguish NULL, a genuine zero and a stored float approximation.
UPDATE drugs SET quantity='.50', unitName='', takemin=0.0625, takemax=0.3125 WHERE drugid=103;
UPDATE drugs SET takemin=0, takemax=0, custom_instructions=0 WHERE drugid=104;
INSERT INTO drugs (drugid, special, quantity, unitName, dosage, unit, drug_form,
                   takemin, takemax, custom_instructions)
VALUES (112, CONCAT('Take 1/3 tablet', CHAR(13,10), 'Then hold'),
        '30.0', 'mL', '250.0 mg / 5 mL', 'tablet', 'TAB', 1/3, 1/3, 1);
INSERT INTO expected_drug_special VALUES (112, '54616B6520312F33207461626C65740D0A5468656E20686F6C64', 0);

CREATE TABLE favorites (
  favoriteid INT PRIMARY KEY, takemin FLOAT, takemax FLOAT,
  custom_instructions TINYINT(1), `repeat` TINYINT
);
INSERT INTO favorites VALUES (1, NULL, NULL, NULL, NULL), (2, 0, 0, 0, 0), (3, .0625, 1/3, 1, 2);
