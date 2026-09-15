-- V1.0.19 — performance indexes for British Columbia billing (`billingmaster`).
-- See common/V1.0.3 and common/V1.0.18 for the rationale: forward migration so both fresh installs
-- and baseline-stamped OpenO conversions receive it; idempotent because converted datadirs vary.
-- NOTE: `CREATE INDEX IF NOT EXISTS` is MariaDB-only DDL; CARLOS targets MariaDB 11.8
-- (see docs/database-schema-management.md). Both statements are online InnoDB index adds.
-- NUMBERING: the version line is global across common + the applied province; V1.0.17 is
-- common/V1.0.17__enable_digital_signatures_by_default.sql and V1.0.18 is
-- common/V1.0.18__performance_indexes_2.sql, so this file takes V1.0.19 and the next free number
-- for ANY location is V1.0.20.

-- `billingmaster` shipped with only its PK and `wcb_id` indexed. The BC billing DAO joins and
-- filters it by billing_no (with an optional billingstatus filter) and by demographic_no:
--   billing_no [+ billingstatus]  BillingmasterDAO.java:73,85,182,190(WCB),202,298 and the
--                                 BillingDaoImpl reconcile join (b.billing_no = bm.billing_no)
--   demographic_no                BillingmasterDAO.java:308 (+ billingCode/status), :333
CREATE INDEX IF NOT EXISTS `idx_billingmaster_billing_no_status` ON `billingmaster` (`billing_no`,`billingstatus`);
CREATE INDEX IF NOT EXISTS `idx_billingmaster_demographic_no` ON `billingmaster` (`demographic_no`);
