-- Tickler attachments through the shared document attachment picker (#3984).
--
-- `ticklerdocs` mirrors `consultdocs` / `EFormDocs` and replaces `tickler_link` as
-- the tickler attachment store. A tickler can now carry any number of the patient's
-- documents (D), labs (L), eForms (E), encounter forms (F) and HRM reports (H).
--
-- Differences from the parallel-fork schema, deliberately:
--   * `lab_type` keeps the originating lab source code (HL7 / MDS / CML / BCP) so a
--     lab attachment can be opened in the right viewer without re-resolving it from
--     the routing tables, and so the legacy `tickler_link.table_name` is not lost.
--   * The backfill copies WHO attached (the tickler's creator) and WHEN (the tickler's
--     creation date) instead of writing an empty provider and today's date, into
--     `provider_no` / `attach_date` and into the repository's audit pair
--     `lastUpdateUser` / `lastUpdateDate`, which every write of the row restamps.
--   * Only `tickler_link.table_name` values with a known meaning are migrated. Anything
--     else stays in `tickler_link` untouched rather than being guessed as a lab.
--   * A legacy link is only migrated when the item belongs to the tickler's patient
--     (`ctl_document`, `HRMDocumentToDemographic`, `patientLabRouting` under the link's
--     own lab source). The legacy forward parameters were request-controlled, while the
--     `ticklerdocs` readers trust the row after type-privilege checks only; a cross-patient
--     legacy row therefore stays quarantined in `tickler_link` instead of surfacing another
--     patient's identifier or name after the upgrade.
--
-- Re-runnable: the DDL is guarded and the backfill matches on (tickler, document,
-- type, lab source) while ignoring `deleted`, so an attachment that was backfilled
-- and later detached is never resurrected by a second run, and a lab under one
-- source never suppresses the same segment id under another. `tickler_link` is
-- kept read-only for one release; its readers now use `ticklerdocs`.
CREATE TABLE IF NOT EXISTS `ticklerdocs` (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `tickler_id` int(10) NOT NULL,
  `document_no` int(10) NOT NULL,
  `doctype` char(1) NOT NULL,
  `lab_type` varchar(10) DEFAULT NULL,
  `deleted` char(1) DEFAULT NULL,
  `attach_date` date DEFAULT NULL,
  `provider_no` varchar(6) NOT NULL,
  `lastUpdateUser` varchar(100) NOT NULL DEFAULT 'system',
  `lastUpdateDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticklerdocs_tickler_id` (`tickler_id`),
  KEY `idx_ticklerdocs_document` (`doctype`, `document_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Legacy `tickler_link.table_name` (char(3)) -> `ticklerdocs.doctype` / `lab_type`:
--   DOC                -> D, lab_type NULL
--   HRM                -> H, lab_type NULL
--   HL7, MDS, CML, BCP -> L, lab_type = the original code
INSERT INTO `ticklerdocs` (`tickler_id`, `document_no`, `doctype`, `lab_type`, `deleted`, `attach_date`, `provider_no`,
                           `lastUpdateUser`, `lastUpdateDate`)
SELECT
  src.`tickler_no`,
  src.`table_id`,
  src.`doctype`,
  src.`lab_type`,
  NULL,
  src.`attach_date`,
  src.`provider_no`,
  src.`last_update_user`,
  src.`last_update_date`
FROM (
  SELECT DISTINCT
    tl.`tickler_no`,
    tl.`table_id`,
    CASE
      WHEN tl.`table_name` = 'DOC' THEN 'D'
      WHEN tl.`table_name` = 'HRM' THEN 'H'
      ELSE 'L'
    END AS `doctype`,
    CASE
      WHEN tl.`table_name` IN ('DOC', 'HRM') THEN NULL
      ELSE tl.`table_name`
    END AS `lab_type`,
    COALESCE(DATE(t.`creation_date`), DATE(t.`update_date`), CURDATE()) AS `attach_date`,
    COALESCE(t.`creator`, '') AS `provider_no`,
    -- The audit pair records who last wrote the row and when: for a backfilled row that is
    -- the tickler's creator at the tickler's creation, the only write the link ever had.
    COALESCE(NULLIF(t.`creator`, ''), 'system') AS `last_update_user`,
    COALESCE(t.`creation_date`, t.`update_date`, CURRENT_TIMESTAMP) AS `last_update_date`
  FROM `tickler_link` tl
  JOIN `tickler` t ON t.`tickler_no` = tl.`tickler_no`
  WHERE tl.`table_name` IN ('DOC', 'HRM', 'HL7', 'MDS', 'CML', 'BCP')
    AND (
      (tl.`table_name` = 'DOC' AND EXISTS (
        SELECT 1 FROM `ctl_document` cd
        WHERE cd.`document_no` = tl.`table_id`
          AND cd.`module` = 'demographic'
          AND cd.`module_id` = t.`demographic_no`))
      OR (tl.`table_name` = 'HRM' AND EXISTS (
        SELECT 1 FROM `HRMDocumentToDemographic` hd
        WHERE hd.`hrmDocumentId` = CAST(tl.`table_id` AS CHAR)
          AND hd.`demographicNo` = CAST(t.`demographic_no` AS CHAR)))
      OR (tl.`table_name` IN ('HL7', 'MDS', 'CML', 'BCP') AND EXISTS (
        SELECT 1 FROM `patientLabRouting` plr
        WHERE plr.`lab_no` = tl.`table_id`
          AND plr.`lab_type` = tl.`table_name`
          AND plr.`demographic_no` = t.`demographic_no`))
    )
) src
WHERE NOT EXISTS (
  SELECT 1
  FROM `ticklerdocs` td
  WHERE td.`tickler_id`  = src.`tickler_no`
    AND td.`document_no` = src.`table_id`
    AND td.`doctype`     = src.`doctype`
    -- Lab identity is source-qualified: HL7 123 and MDS 123 are two attachments.
    AND td.`lab_type`    <=> src.`lab_type`
);
