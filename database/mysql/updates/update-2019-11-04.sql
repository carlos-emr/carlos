-- USE WITH CAUTION ON AN EXISTING appointment_status TABLE
-- Modifying the appointment_status table requires a TOMCAT RESTART
-- this update also corrects all appointment status` misplaced after the last BILLING status.
-- backup appointment_status table
CREATE TABLE IF NOT EXISTS appointment_status_old LIKE appointment_status;
INSERT INTO appointment_status_old SELECT * FROM appointment_status;
-- drop old edit tables and create a new one.
DROP TABLE IF EXISTS appointment_status_edit;
-- copy the appointment_status table
CREATE TABLE appointment_status_edit LIKE appointment_status;
INSERT INTO appointment_status_edit SELECT * FROM appointment_status;
-- determine the row id for the BILLING status.  This needs to be last with the highest id
SELECT ao.`id` INTO @BILLING_STATUS_ROW FROM appointment_status_edit ao WHERE BINARY ao.`status` = 'B';
-- Calculate the number of new rows needed to move from the end of the list plus 1 row for the new confirmed status
SELECT (count(id) + 1) INTO @NUMBER_ROWS FROM appointment_status_edit WHERE id > @BILLING_STATUS_ROW;
-- remove all "ilegal rows" inserted after the BILLING status
DELETE FROM appointment_status_edit WHERE id > @BILLING_STATUS_ROW;
-- remove any potential for duplicate "h" confirmed status types
DELETE FROM appointment_status_edit WHERE `status` = 'h';
-- Calculate the begining INSERT_ID by subtracting the needed number of rows minus mandatory last 3 rows
SET @INSERT_ID := @BILLING_STATUS_ROW - 2;
-- increment the last 3 mandatory rows to make room for the new @NUMBER_ROWS
UPDATE appointment_status_edit SET id = (id + @NUMBER_ROWS) WHERE id >= @INSERT_ID ORDER BY id DESC;
-- insert the new "confirmed" status into the new row "INSERT_ID"
INSERT INTO appointment_status_edit (`id`, `status`, `description`, `color`, `icon`, `active`, `editable`, short_letter_colour, short_letters) values (@INSERT_ID, 'h', 'Confirmed', '#2fcccf', 'thumb.png', true, true, 0, 'CONF');
-- Add back in remaining status' found entered after the BILLING status.
-- Exclude the "confirmed" status if it already exists in the status table.
-- Increment the @INSERT_ID by 1 to accommodate the new confirmed status previously inserted
INSERT INTO appointment_status_edit
SELECT @ROW := @ROW + 1 AS id, a.`status`, a.description, a.color, a.icon, a.active, a.editable, a.short_letter_colour, a.short_letters FROM appointment_status a LEFT JOIN appointment_status_edit ao ON (a.`status` = ao.`status` AND a.`status` != 'h') CROSS JOIN (SELECT @ROW := (@INSERT_ID + 1)) r WHERE a.`status` != 'h' AND ao.id IS NULL ORDER BY a.`status`;
-- set missing short_color_letter values
UPDATE appointment_status_edit SET short_letter_colour = 0 WHERE short_letter_colour IS NULL;

-- Rebuild the appointment_status table
START TRANSACTION;
DROP TABLE IF EXISTS appointment_status;
CREATE TABLE appointment_status LIKE appointment_status_edit;
INSERT INTO appointment_status SELECT * FROM appointment_status_edit;
-- drop the edit table
DROP TABLE IF EXISTS appointment_status_edit;
COMMIT;

