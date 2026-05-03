-- Preflight: existing duplicate filenames make the unique indexes fail with
-- an opaque "Duplicate entry" DDL error. Fail first with the table/column that
-- needs cleanup. Diagnostic queries for operators:
--
--   SELECT ohipfilename, COUNT(*)
--   FROM billing_on_diskname
--   WHERE ohipfilename IS NOT NULL AND ohipfilename <> ''
--   GROUP BY ohipfilename
--   HAVING COUNT(*) > 1;
--
--   SELECT htmlfilename, COUNT(*)
--   FROM billing_on_filename
--   WHERE htmlfilename IS NOT NULL AND htmlfilename <> ''
--   GROUP BY htmlfilename
--   HAVING COUNT(*) > 1;

DROP PROCEDURE IF EXISTS assert_no_duplicate_billing_disk_filenames;
DELIMITER //
CREATE PROCEDURE assert_no_duplicate_billing_disk_filenames()
BEGIN
  IF EXISTS (
    SELECT 1 FROM (
      SELECT ohipfilename
      FROM billing_on_diskname
      WHERE ohipfilename IS NOT NULL AND ohipfilename <> ''
      GROUP BY ohipfilename
      HAVING COUNT(*) > 1
    ) duplicate_ohip_filenames
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Duplicate billing_on_diskname.ohipfilename values exist; clean them before adding the unique index';
  END IF;

  IF EXISTS (
    SELECT 1 FROM (
      SELECT htmlfilename
      FROM billing_on_filename
      WHERE htmlfilename IS NOT NULL AND htmlfilename <> ''
      GROUP BY htmlfilename
      HAVING COUNT(*) > 1
    ) duplicate_html_filenames
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Duplicate billing_on_filename.htmlfilename values exist; clean them before adding the unique index';
  END IF;
END //
DELIMITER ;
CALL assert_no_duplicate_billing_disk_filenames();
DROP PROCEDURE IF EXISTS assert_no_duplicate_billing_disk_filenames;

ALTER TABLE billing_on_diskname
  ADD UNIQUE KEY billing_on_diskname_ohipfilename_uq (ohipfilename);

ALTER TABLE billing_on_filename
  ADD UNIQUE KEY billing_on_filename_htmlfilename_uq (htmlfilename);
