-- Add versioned, auditable patient consent history.
--
-- This migration keeps the legacy Consent table as the current-state lookup
-- used by existing code, while adding append-only history tables for future
-- audit workflows.
--
-- Idempotent: safe to run multiple times.

CREATE TABLE IF NOT EXISTS `ConsentTypeVersion` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `consent_type_id` int(15) NOT NULL,
  `version` int(11) NOT NULL DEFAULT 1,
  `title` varchar(255) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `full_text` text,
  `jurisdiction` varchar(50) DEFAULT NULL,
  `effective_from` datetime DEFAULT NULL,
  `effective_to` datetime DEFAULT NULL,
  `created_by` varchar(25) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `ConsentTypeVersion_type_version_UIDX`
  ON `ConsentTypeVersion` (`consent_type_id`, `version`);

CREATE INDEX IF NOT EXISTS `ConsentTypeVersion_type_active_IDX`
  ON `ConsentTypeVersion` (`consent_type_id`, `active`);

CREATE TABLE IF NOT EXISTS `ConsentEvidence` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `evidence_type` varchar(50) DEFAULT NULL,
  `storage_uri` varchar(1000) DEFAULT NULL,
  `content_hash` varchar(255) DEFAULT NULL,
  `captured_text` text,
  `created_by` varchar(25) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `ConsentEvidence_type_IDX`
  ON `ConsentEvidence` (`evidence_type`);

CREATE TABLE IF NOT EXISTS `ConsentEvent` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `consent_id` int(11) DEFAULT NULL,
  `demographic_no` int(10) DEFAULT NULL,
  `consent_type_id` int(15) DEFAULT NULL,
  `consent_type_version_id` int(11) DEFAULT NULL,
  `event_type` varchar(50) NOT NULL,
  `previous_status` varchar(20) DEFAULT NULL,
  `new_status` varchar(20) DEFAULT NULL,
  `explicit` tinyint(1) DEFAULT NULL,
  `actor_provider_no` varchar(25) DEFAULT NULL,
  `actor_type` varchar(50) DEFAULT NULL,
  `event_at` datetime NOT NULL,
  `source` varchar(50) DEFAULT NULL,
  `jurisdiction` varchar(50) DEFAULT NULL,
  `reason` varchar(1000) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `evidence_id` int(11) DEFAULT NULL,
  `metadata_json` text,
  PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `ConsentEvent_consent_IDX`
  ON `ConsentEvent` (`consent_id`);

CREATE INDEX IF NOT EXISTS `ConsentEvent_demographic_type_date_IDX`
  ON `ConsentEvent` (`demographic_no`, `consent_type_id`, `event_at`);

CREATE INDEX IF NOT EXISTS `ConsentEvent_type_version_IDX`
  ON `ConsentEvent` (`consent_type_version_id`);

CREATE INDEX IF NOT EXISTS `ConsentEvent_evidence_IDX`
  ON `ConsentEvent` (`evidence_id`);

ALTER TABLE `Consent`
  ADD COLUMN IF NOT EXISTS `current_consent_type_version_id` int(11) DEFAULT NULL AFTER `consent_type_id`,
  ADD COLUMN IF NOT EXISTS `status` varchar(20) DEFAULT NULL AFTER `current_consent_type_version_id`,
  ADD COLUMN IF NOT EXISTS `effective_at` datetime DEFAULT NULL AFTER `status`,
  ADD COLUMN IF NOT EXISTS `expires_at` datetime DEFAULT NULL AFTER `effective_at`,
  ADD COLUMN IF NOT EXISTS `last_event_id` int(11) DEFAULT NULL AFTER `expires_at`,
  ADD COLUMN IF NOT EXISTS `updated_at` datetime DEFAULT NULL AFTER `last_event_id`;

CREATE INDEX IF NOT EXISTS `Consent_demo_type_status_IDX`
  ON `Consent` (`demographic_no`, `consent_type_id`, `status`);

CREATE INDEX IF NOT EXISTS `Consent_type_version_IDX`
  ON `Consent` (`current_consent_type_version_id`);

CREATE INDEX IF NOT EXISTS `Consent_last_event_IDX`
  ON `Consent` (`last_event_id`);

-- Seed version 1 for every existing consent type. Legacy ConsentType.description
-- is copied into both description and full_text so old consent rows can point to
-- stable wording even before the UI supports richer consent text management.
INSERT INTO `ConsentTypeVersion`
  (`consent_type_id`, `version`, `title`, `description`, `full_text`,
   `jurisdiction`, `effective_from`, `created_by`, `created_at`, `active`)
SELECT
  ct.`id`,
  1,
  ct.`name`,
  ct.`description`,
  ct.`description`,
  'CA',
  NOW(),
  'migration',
  NOW(),
  IFNULL(ct.`active`, 1)
FROM `consentType` ct
WHERE NOT EXISTS (
  SELECT 1
  FROM `ConsentTypeVersion` ctv
  WHERE ctv.`consent_type_id` = ct.`id`
    AND ctv.`version` = 1
);

-- Populate the new current-state columns from the legacy optout/deleted flags.
UPDATE `Consent` c
LEFT JOIN `ConsentTypeVersion` ctv
  ON ctv.`consent_type_id` = c.`consent_type_id`
 AND ctv.`version` = 1
SET
  c.`current_consent_type_version_id` = IFNULL(c.`current_consent_type_version_id`, ctv.`id`),
  c.`status` = CASE
    WHEN IFNULL(c.`deleted`, 0) = 1 THEN 'CLEARED'
    WHEN IFNULL(c.`optout`, 0) = 1 THEN 'WITHDRAWN'
    ELSE 'GRANTED'
  END,
  c.`effective_at` = IFNULL(
    c.`effective_at`,
    CASE
      WHEN IFNULL(c.`optout`, 0) = 1 THEN COALESCE(c.`optout_date`, c.`edit_date`, c.`consent_date`, NOW())
      ELSE COALESCE(c.`consent_date`, c.`edit_date`, c.`optout_date`, NOW())
    END
  ),
  c.`updated_at` = IFNULL(c.`updated_at`, COALESCE(c.`edit_date`, c.`optout_date`, c.`consent_date`, NOW()));

-- Backfill one migration event for each legacy Consent row. Deleted rows are
-- included as CLEARED so historical clear state is not lost during migration.
INSERT INTO `ConsentEvent`
  (`consent_id`, `demographic_no`, `consent_type_id`, `consent_type_version_id`,
   `event_type`, `previous_status`, `new_status`, `explicit`,
   `actor_provider_no`, `actor_type`, `event_at`, `source`, `jurisdiction`,
   `reason`, `metadata_json`)
SELECT
  c.`id`,
  c.`demographic_no`,
  c.`consent_type_id`,
  c.`current_consent_type_version_id`,
  'MIGRATION',
  NULL,
  c.`status`,
  c.`explicit`,
  c.`last_entered_by`,
  'SYSTEM',
  COALESCE(c.`updated_at`, c.`edit_date`, c.`optout_date`, c.`consent_date`, NOW()),
  'MIGRATION',
  'CA',
  'Backfilled from legacy Consent row during consent audit history migration.',
  CONCAT(
    '{',
    '\"legacy_optout\":', IF(IFNULL(c.`optout`, 0) = 1, 'true', 'false'), ',',
    '\"legacy_deleted\":', IF(IFNULL(c.`deleted`, 0) = 1, 'true', 'false'),
    '}'
  )
FROM `Consent` c
WHERE NOT EXISTS (
  SELECT 1
  FROM `ConsentEvent` ce
  WHERE ce.`consent_id` = c.`id`
    AND ce.`event_type` = 'MIGRATION'
);

-- Link each Consent row to the latest event known for that row. At migration
-- time this will normally be the MIGRATION event; future code should keep this
-- column pointed at the event that last changed the current state.
UPDATE `Consent` c
JOIN (
  SELECT `consent_id`, MAX(`id`) AS `last_event_id`
  FROM `ConsentEvent`
  WHERE `consent_id` IS NOT NULL
  GROUP BY `consent_id`
) latest
  ON latest.`consent_id` = c.`id`
SET c.`last_event_id` = latest.`last_event_id`
WHERE c.`last_event_id` IS NULL
   OR c.`last_event_id` <> latest.`last_event_id`;
