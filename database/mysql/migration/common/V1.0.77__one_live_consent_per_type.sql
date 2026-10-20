-- One live consent record per patient and consent type (#3845).
--
-- Consent had no unique key on (demographic_no, consent_type_id), and the single-row lookups
-- returned an arbitrary row when a patient had more than one live record for a type. The
-- application now picks one deciding record and retires the others when it saves (#3914). This
-- migration repairs the rows already stored and adds a key so duplicates cannot be written again.
--
-- The steps run in this order because each relies on the one before:
--   0. record, for every row a later step will change, the values it held before;
--   1. fill NULL flags, which the entity maps to primitive boolean and so cannot load;
--   2. retire the extra live rows, keeping the record the application treats as deciding;
--   3. make the flags NOT NULL;
--   4. add the unique key over live rows only.
-- A rerun after a partial apply is safe: the audit insert skips rows already recorded, the
-- updates match nothing the second time, MODIFY is repeatable, and the additions use
-- IF NOT EXISTS.
--
-- Nothing is hard-deleted. Step 2 changes only `deleted`. Step 1 also fills flags that were
-- NULL: those rows held no usable value, and the value each held before, NULL included, is kept
-- in `Consent_migration_audit`, so what a person recorded can always be told from what this
-- migration filled in.
--
-- Run this with the application stopped. DDL is not transactional on MariaDB: a duplicate
-- written between step 2 and step 4 would make step 4 fail, and the migration would need a
-- rerun.

-- 0. The audit trail. One row per Consent row this migration changes, with the values it held
-- before and why it was changed. The application's own retire and clear actions write to the
-- `log` table with the user who did them; nobody did these, so they are kept apart.
CREATE TABLE IF NOT EXISTS `Consent_migration_audit` (
    `consent_id` INT(10) NOT NULL,
    `migration` VARCHAR(64) NOT NULL,
    `reason` VARCHAR(32) NOT NULL,
    `prior_explicit` TINYINT(1) NULL,
    `prior_optout` TINYINT(1) NULL,
    `prior_deleted` TINYINT(1) NULL,
    `recorded_at` DATETIME NOT NULL,
    PRIMARY KEY (`consent_id`, `migration`, `reason`)
) COMMENT 'Values Consent rows held before a schema migration changed them';

INSERT IGNORE INTO `Consent_migration_audit`
    (`consent_id`, `migration`, `reason`, `prior_explicit`, `prior_optout`, `prior_deleted`, `recorded_at`)
SELECT `id`, 'one_live_consent_per_type', 'null_flag', `explicit`, `optout`, `deleted`, NOW()
FROM `Consent`
WHERE `deleted` IS NULL OR `optout` IS NULL OR `explicit` IS NULL;

-- Keep this ranking in step with step 2's ranking and filter below. It records, before anything
-- changes, exactly the rows step 2 will retire, and the two are written differently only because
-- steps 1a-1c run in between: here a NULL `explicit` ranks as 0 (step 1c fills it with 0 first),
-- and rows with a NULL `optout` are filtered out (step 1b retires the live ones first, so step 2
-- never sees them; a NULL `deleted` already fails `deleted = 0` in both). Changing one ranking
-- without the other, or reordering the steps, would let a retired row go without its
-- duplicate_retired entry, or record a row that stays live.
-- The OSCAR 19 import ranks the same way: o19etl.CONSENT_LIVE_ORDER, which
-- debian/assets/carlos_ctl/tests/test_consent_live_replay.py checks against both rankings here.
INSERT IGNORE INTO `Consent_migration_audit`
    (`consent_id`, `migration`, `reason`, `prior_explicit`, `prior_optout`, `prior_deleted`, `recorded_at`)
SELECT ranked.`id`, 'one_live_consent_per_type', 'duplicate_retired',
       ranked.`explicit`, ranked.`optout`, ranked.`deleted`, NOW()
FROM (
    SELECT `id`, `explicit`, `optout`, `deleted`,
           ROW_NUMBER() OVER (
               PARTITION BY `demographic_no`, `consent_type_id`
               ORDER BY `optout` DESC, IFNULL(`explicit`, 0) DESC, (NULLIF(`edit_date`, '0000-00-00 00:00:00') IS NULL), NULLIF(`edit_date`, '0000-00-00 00:00:00') DESC, `id` DESC
           ) AS `rn`
    FROM `Consent`
    WHERE `deleted` = 0 AND `optout` IS NOT NULL
      AND `demographic_no` IS NOT NULL AND `consent_type_id` IS NOT NULL
) ranked
WHERE ranked.`rn` > 1;

-- 1a. deleted: the live-row queries filter on deleted = false, which never matched NULL, so these
-- rows were already invisible to them. Recording them as deleted keeps that.
UPDATE `Consent` SET `deleted` = 1 WHERE `deleted` IS NULL;

-- 1b. optout: a live row with no recorded decision cannot be read as either answer. Retire it, so
-- the chart shows no decision rather than an opt-in or opt-out that nobody entered.
UPDATE `Consent` SET `deleted` = 1 WHERE `optout` IS NULL AND `deleted` = 0;
-- Only retired rows are left with a NULL optout, and the column is about to become NOT NULL, so
-- they need a value. It is 1 so that a row restored by hand later fails safe. This is a filler,
-- not a decision anyone recorded: `Consent_migration_audit.prior_optout` is NULL for these rows,
-- and `optout_date` stays NULL.
UPDATE `Consent` SET `optout` = 1 WHERE `optout` IS NULL;

-- 1c. explicit: NULL means nobody recorded that the patient consented directly, so the consent is
-- implied. For email and SMS this grants nothing: neither sends on implied consent (#3671). Other
-- readers do not look at `explicit`: a live opt-in whose `explicit` was NULL could not be loaded
-- before, and can be afterwards, so for those consent types it becomes a usable opt-in. Such rows
-- can only have been written outside the application, and each is listed in
-- `Consent_migration_audit` with reason 'null_flag'; review them if any exist.
UPDATE `Consent` SET `explicit` = 0 WHERE `explicit` IS NULL;

-- 2. Keep one live row per patient and type, chosen exactly as ConsentRecords.effective chooses
-- it: any opt-out wins; then a record the patient confirmed directly (explicit) wins over an
-- implied one, even a newer one; among the rest the latest edit_date wins, an undated row counts
-- as the oldest (a zero date, 0000-00-00, counts as undated, as the OSCAR 19 import treats it),
-- and a tie goes to the higher id. Rows missing the patient or the type are not
-- grouped, because the key below does not constrain them either.
UPDATE `Consent` c
JOIN (
    SELECT `id`,
           ROW_NUMBER() OVER (
               PARTITION BY `demographic_no`, `consent_type_id`
               ORDER BY `optout` DESC, `explicit` DESC, (NULLIF(`edit_date`, '0000-00-00 00:00:00') IS NULL), NULLIF(`edit_date`, '0000-00-00 00:00:00') DESC, `id` DESC
           ) AS `rn`
    FROM `Consent`
    WHERE `deleted` = 0 AND `demographic_no` IS NOT NULL AND `consent_type_id` IS NOT NULL
) ranked ON ranked.`id` = c.`id`
SET c.`deleted` = 1
WHERE ranked.`rn` > 1;

-- 3. The application always writes all three flags. The defaults are for a row inserted outside
-- the application that leaves one out: it is implied, opted out and live, which permits nothing.
ALTER TABLE `Consent`
    MODIFY `explicit` TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY `optout` TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY `deleted` TINYINT(1) NOT NULL DEFAULT 0;

-- 4. Deleted rows legitimately repeat (each cleared decision leaves one), so the key cannot cover
-- the table. MariaDB has no partial index; instead a generated column carries demographic_no for
-- live rows and NULL for deleted ones, and a unique key ignores NULLs. INVISIBLE keeps the column
-- out of SELECT * and out of INSERTs without a column list; Hibernate never names it.
ALTER TABLE `Consent`
    ADD COLUMN IF NOT EXISTS `live_demographic_no` INT(10)
        AS (IF(`deleted` = 0, `demographic_no`, NULL)) PERSISTENT INVISIBLE
        COMMENT 'demographic_no while the row is live, NULL once deleted; backs uq_consent_live_type';

ALTER TABLE `Consent`
    ADD UNIQUE INDEX IF NOT EXISTS `uq_consent_live_type` (`live_demographic_no`, `consent_type_id`);
