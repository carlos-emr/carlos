alter table tickler add creation_date timestamp not null;
UPDATE tickler
    JOIN (
        SELECT max(t.tickler_no) as tickler_no, max(tu.update_date) as update_date
        FROM tickler t
                 JOIN tickler_update tu
                      ON(t.tickler_no = tu.tickler_no)
        GROUP BY t.tickler_no HAVING count(t.tickler_no) > -1
    ) ticklers
    ON (ticklers.tickler_no = tickler.tickler_no)
SET tickler.creation_date = ticklers.update_date;

ALTER TABLE `hl7TextInfo`
    MODIFY COLUMN `report_status` varchar(25) CHARACTER SET latin1 COLLATE latin1_swedish_ci NULL DEFAULT NULL AFTER `first_name`;