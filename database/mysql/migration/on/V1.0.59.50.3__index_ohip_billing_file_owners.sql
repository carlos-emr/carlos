-- SPDX-License-Identifier: GPL-2.0-or-later
-- Ontario checks both legacy filenames and every provider row on a disk before
-- serving a billing file. Reuse any full leading key, including clinic indexes.
DELIMITER $$
BEGIN NOT ATOMIC
    DECLARE has_ohip_key BOOLEAN;
    DECLARE has_html_key BOOLEAN;
    DECLARE limited BOOLEAN DEFAULT FALSE;
    DECLARE CONTINUE HANDLER FOR 1069, 1071, 1709 SET limited = TRUE;

    SELECT COUNT(CASE WHEN s.column_name = 'ohipfilename' THEN 1 END) > 0,
           COUNT(CASE WHEN s.column_name = 'htmlfilename' THEN 1 END) > 0
      INTO has_ohip_key, has_html_key
      FROM information_schema.statistics AS s
     WHERE s.table_schema = DATABASE() AND s.table_name = 'billactivity'
       AND s.seq_in_index = 1 AND s.column_name IN ('ohipfilename', 'htmlfilename')
       AND s.index_type = 'BTREE' AND s.ignored = 'NO' AND s.sub_part IS NULL;
    -- Build both missing indexes in one pass. MariaDB assigns unused names
    -- when a clinic already uses the column name for a different key.
    IF NOT has_ohip_key AND NOT has_html_key THEN
        ALTER TABLE billactivity ADD INDEX (ohipfilename), ADD INDEX (htmlfilename);
    ELSEIF NOT has_ohip_key THEN
        ALTER TABLE billactivity ADD INDEX (ohipfilename);
    ELSEIF NOT has_html_key THEN
        ALTER TABLE billactivity ADD INDEX (htmlfilename);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics AS s
         WHERE s.table_schema = DATABASE() AND s.table_name = 'billing_on_filename'
           AND s.seq_in_index = 1 AND s.column_name = 'disk_id'
           AND s.index_type = 'BTREE' AND s.ignored = 'NO' AND s.sub_part IS NULL) THEN
        ALTER TABLE billing_on_filename ADD INDEX (disk_id);
    END IF;

    IF limited THEN
        SIGNAL SQLSTATE '01000' SET MESSAGE_TEXT =
            'OHIP ownership lookup indexes exceed local index limits; review billactivity and billing_on_filename indexes';
    END IF;
END$$
DELIMITER ;
