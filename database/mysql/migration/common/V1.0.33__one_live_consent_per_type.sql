-- One live consent record per patient and consent type (#3845).
--
-- Consent had no unique key on (demographic_no, consent_type_id), and the single-row lookups
-- returned an arbitrary row when a patient had more than one live record for a type. The
-- application now picks one deciding record and retires the others when it saves (#3914). This
-- migration repairs the rows already stored and adds a key so duplicates cannot be written again.
--
-- The steps run in this order because each relies on the one before:
--   1. fill NULL flags, which the entity maps to primitive boolean and so cannot load;
--   2. retire the extra live rows, keeping the record the application treats as deciding;
--   3. make the flags NOT NULL;
--   4. add the unique key over live rows only.
-- A rerun after a partial apply is safe: the updates match nothing the second time, MODIFY is
-- repeatable, and the additions use IF NOT EXISTS.
--
-- Nothing is hard-deleted. Retired rows keep every value except `deleted`, so the original
-- decision, its dates and who entered it stay in the table.

-- 1a. deleted: the live-row queries filter on deleted = false, which never matched NULL, so these
-- rows were already invisible to them. Recording them as deleted keeps that.
UPDATE `Consent` SET `deleted` = 1 WHERE `deleted` IS NULL;

-- 1b. optout: a live row with no recorded decision cannot be read as either answer. Retire it, so
-- the chart shows no decision rather than an opt-in or opt-out that nobody entered.
UPDATE `Consent` SET `deleted` = 1 WHERE `optout` IS NULL AND `deleted` = 0;
-- Only retired rows are left with a NULL optout. Recording them as opt-outs means that a row
-- restored by hand later fails safe.
UPDATE `Consent` SET `optout` = 1 WHERE `optout` IS NULL;

-- 1c. explicit: NULL means nobody recorded that the patient consented directly, so the consent is
-- implied. Implied email consent does not permit a send (#3671), so this grants nothing new.
UPDATE `Consent` SET `explicit` = 0 WHERE `explicit` IS NULL;

-- 2. Keep one live row per patient and type, chosen exactly as ConsentRecords.effective chooses
-- it: any opt-out wins; among the candidates the latest edit_date wins, an undated row counts as
-- the oldest, and a tie goes to the higher id. Rows missing the patient or the type are not
-- grouped, because the key below does not constrain them either.
UPDATE `Consent` c
JOIN (
    SELECT `id`,
           ROW_NUMBER() OVER (
               PARTITION BY `demographic_no`, `consent_type_id`
               ORDER BY `optout` DESC, (`edit_date` IS NULL), `edit_date` DESC, `id` DESC
           ) AS `rn`
    FROM `Consent`
    WHERE `deleted` = 0 AND `demographic_no` IS NOT NULL AND `consent_type_id` IS NOT NULL
) ranked ON ranked.`id` = c.`id`
SET c.`deleted` = 1
WHERE ranked.`rn` > 1;

-- 3. The application always writes all three flags. optout has no default on purpose: a row
-- inserted outside the application must state the decision.
ALTER TABLE `Consent`
    MODIFY `explicit` TINYINT(1) NOT NULL DEFAULT 0,
    MODIFY `optout` TINYINT(1) NOT NULL,
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
