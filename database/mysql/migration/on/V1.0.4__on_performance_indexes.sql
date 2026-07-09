-- V1.0.4 — performance indexes for Ontario billing (see common/V1.0.3 for the rationale:
-- forward migration so both fresh installs and baseline-stamped OpenO conversions receive it;
-- idempotent because converted datadirs vary).
-- note, this create index if not exists only works in mariadb

-- Patient billing history: demographicNo + status!='D' ORDER BY billingDate DESC.
-- The single-column demographic_no index becomes a left-prefix shadow of the composite.
ALTER TABLE `billing_on_cheader1` DROP INDEX IF EXISTS `demographic_no`;
CREATE INDEX IF NOT EXISTS `idx_billing_on_cheader1_demo_date` ON `billing_on_cheader1` (`demographic_no`,`billing_date`);

-- Per-service-code billing queries and code-usage reporting.
CREATE INDEX IF NOT EXISTS `idx_billing_on_item_service_code` ON `billing_on_item` (`service_code`);
