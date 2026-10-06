-- V1.0.18 — performance indexes, second pass (common schema).
--
-- Delta review on top of V1.0.3. Every index below is justified by an actual DAO predicate
-- (WHERE/JOIN/ORDER BY mined from the code; the full justification table, the candidates that
-- were evaluated and rejected, and the query-shape findings that indexes cannot fix are in
-- docs/database-index-review-2026-09-04.md). Same shape and safety rules as V1.0.3:
--   * Forward migration so BOTH populations receive it: fresh installs (V1 executes, then this
--     applies) and OpenO/oscar19 conversions (V1 is baseline-STAMPED without executing, then this
--     applies). Converted datadirs are heterogeneous, hence every statement is idempotent.
--   * `CREATE INDEX IF NOT EXISTS` / `DROP INDEX IF EXISTS` are MariaDB-only DDL; CARLOS targets
--     MariaDB 11.8 (see docs/database-schema-management.md).
--   * Covering composites are created BEFORE the shadow drops that depend on them: MariaDB DDL is
--     not transactional, so drop-first would leave a crash window with the column unindexed.
--   * Every statement is an InnoDB secondary-index add/drop — online (INPLACE, no table rebuild).
-- CAVEAT (shared with V1.0.3): `CREATE INDEX IF NOT EXISTS` is a no-op when an index of the SAME
-- NAME already exists, even with different columns. All names below are new `idx_*` names verified
-- absent from every migration in the repo; a site-local index reusing one of them would silently
-- win. Check `SHOW INDEX` after migrating a converted datadir.

-- ---- Guard composites BEFORE the shadow drops. Each is a real query shape in its own right (see
-- ---- the per-line notes) AND the superset of a single-column index dropped below.
-- BC/legacy billing: provider_no + billing_date BETWEEN, ORDER BY billing_date
-- (BillingDaoImpl.java:525,563,582,601). `billing` carried 8 single-column indexes but no
-- composite for the range shape the DAO actually issues.
CREATE INDEX IF NOT EXISTS `idx_billing_provider_date` ON `billing` (`provider_no`,`billing_date`);
-- Note-count reports: provider_no + observation_date BETWEEN (CaseManagementNoteDAOImpl.java:585,621).
CREATE INDEX IF NOT EXISTS `idx_casemgmt_note_provider_observation` ON `casemgmt_note` (`provider_no`,`observation_date`);
-- Messenger inbox / unread badge: provider_no + status (MessageListDaoImpl.java:111,119).
CREATE INDEX IF NOT EXISTS `idx_messagelisttbl_provider_status` ON `messagelisttbl` (`provider_no`,`status`);

-- ---- Drop redundant indexes (left-prefix shadows of a composite created above, or an exact PK
-- ---- duplicate). InnoDB secondary indexes carry the PK implicitly, so these drops are
-- ---- read-equivalent and save pure write amplification.
ALTER TABLE `billing` DROP INDEX IF EXISTS `provider_no`;                    -- prefix of idx_billing_provider_date (created above)
ALTER TABLE `casemgmt_note` DROP INDEX IF EXISTS `FKA8D537806CCA0FC`;        -- Hibernate-named (provider_no); prefix of idx_casemgmt_note_provider_observation (created above). casemgmt_note declares no FOREIGN KEY on provider_no, so no constraint depends on it.
ALTER TABLE `messagelisttbl` DROP INDEX IF EXISTS `provider_no`;             -- prefix of idx_messagelisttbl_provider_status (created above)
ALTER TABLE `eform` DROP INDEX IF EXISTS `id`;                               -- UNIQUE duplicate of the PK (fid); sibling of the eform_data.id drop in V1.0.3

-- ---- Patient record history: demographicArchive was PK-only, so every demographic edit /
-- ---- history view scanned the whole (ever-growing) archive
-- ---- (DemographicArchiveDaoImpl.java:59,75,114,128 — all four queries filter demographic_no).
CREATE INDEX IF NOT EXISTS `idx_demographicArchive_demographic_no` ON `demographicArchive` (`demographic_no`);

-- ---- Patient search by chart number: chart_no LIKE (DemographicDaoImpl.java:1377,1887,1996 and the
-- ---- wildcard branch of the main search, :2774/:2813) plus the chart_no sort mode (:2861).
-- ---- The default main-search operator is REGEXP, which no B-tree index can serve — see the review.
CREATE INDEX IF NOT EXISTS `idx_demographic_chart_no` ON `demographic` (`chart_no`);

-- ---- Provider signature lookups: providerExt has no PRIMARY KEY and no index at all, yet the
-- ---- ProviderExt entity maps provider_no as @Id (find() on every signature render).
-- ---- A plain index rather than a PK: provider_no is nullable and converted datadirs may carry
-- ---- duplicate rows; adding a PK needs a data-quality pass first (tracked in the review doc).
CREATE INDEX IF NOT EXISTS `idx_providerExt_provider_no` ON `providerExt` (`provider_no`);

-- ---- Integrator remote attachments per patient: demographic_no [+ messageid]
-- ---- (RemoteAttachmentsDaoImpl.java:51,58); table was PK-only.
CREATE INDEX IF NOT EXISTS `idx_remoteAttachments_demo_message` ON `remoteAttachments` (`demographic_no`,`messageid`);

-- ---- Lab inbox: obr_date range predicates and ORDER BY obr_date DESC with offset pagination
-- ---- (Hl7TextInfoDaoImpl, inbox builder ~lines 320-500). obr_date is varchar(20) holding HL7
-- ---- timestamps, which sort lexically, so the index is valid as-is.
CREATE INDEX IF NOT EXISTS `idx_hl7TextInfo_obr_date` ON `hl7TextInfo` (`obr_date`);

-- ---- Documents: patient document list ORDER BY observationdate DESC (DocumentDaoImpl.java:527)
-- ---- and the per-provider observationdate BETWEEN report (:206).
CREATE INDEX IF NOT EXISTS `idx_document_observationdate` ON `document` (`observationdate`);
