-- Enable Rich Text Letter (RTL) directly via SQL
--
-- The RTLSettings2Action admin UI has been removed. Use this SQL to
-- enable or disable the Rich Text Letter e-form directly in the database.
--
-- The eform table's `status` column (mapped as `current` in the EForm entity)
-- controls whether a form is active. 1 = enabled, 0 = disabled.
--
-- Note: update-2022-03-24.sql changed the subject from 'Rich Text Letter Generator'
-- to 'Rich Text Letter Generator v2.1', so the WHERE clause uses LIKE to match both.

-- Enable Rich Text Letter (fails loudly if the eform row is missing)
DROP PROCEDURE IF EXISTS enable_rtl_eform;
DELIMITER //
CREATE PROCEDURE enable_rtl_eform()
BEGIN
    IF (SELECT COUNT(*) FROM eform WHERE form_name = 'Rich Text Letter') = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'MISSING RTL row: run update-2012-07-12.sql first to seed the Rich Text Letter eform';
    END IF;
    UPDATE eform
    SET status = 1
    WHERE form_name = 'Rich Text Letter'
      AND subject LIKE 'Rich Text Letter Generator%';
END //
DELIMITER ;
CALL enable_rtl_eform();
DROP PROCEDURE IF EXISTS enable_rtl_eform;

-- To disable Rich Text Letter, replace the UPDATE above with:
-- UPDATE eform
-- SET status = 0
-- WHERE form_name = 'Rich Text Letter'
--   AND subject LIKE 'Rich Text Letter Generator%';
