-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Missing/date-only source note timing must not become a fabricated midnight/current-time event.
-- Keep the deployed temporal precision and comment; retire the legacy zero-date default.
SET @cds_note_observation_ddl = (
    SELECT IF(data_type IN ('datetime','timestamp') AND COALESCE(generation_expression,'')=''
            AND LOWER(extra) NOT LIKE '%on update%',
        CONCAT('ALTER TABLE casemgmt_note MODIFY COLUMN observation_date ', column_type, ' NULL',
            CASE
                WHEN column_default IS NULL OR UPPER(column_default)='NULL'
                    OR REPLACE(column_default,'''','') LIKE '0000-00-00%' THEN ' DEFAULT NULL'
                WHEN UPPER(column_default) LIKE 'CURRENT_TIMESTAMP%' THEN CONCAT(' DEFAULT ',column_default)
                WHEN LEFT(column_default,1)='''' THEN CONCAT(' DEFAULT ',column_default)
                ELSE CONCAT(' DEFAULT ',QUOTE(column_default))
            END,
            IF(LOWER(extra) LIKE '%on update%',CONCAT(' ',SUBSTRING(extra,LOCATE('on update',LOWER(extra)))) ,''),
            ' COMMENT ',QUOTE(column_comment)),
        'SELECT cds_note_observation_shape_requires_review FROM casemgmt_note')
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='casemgmt_note' AND column_name='observation_date'
);
PREPARE cds_note_observation_statement FROM @cds_note_observation_ddl;
EXECUTE cds_note_observation_statement;
DEALLOCATE PREPARE cds_note_observation_statement;

-- Existing administrators retain access. Other roles require an explicit, auditable migration grant.
INSERT INTO secObjectName (objectName,description,orgapplicable)
SELECT '_cds_import','Run approved clinical data migrations',0
WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName='_cds_import');
