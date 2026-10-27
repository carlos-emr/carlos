-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
-- Exact source-provider attribution must remain bounded as patient/medication counts grow.
-- Ordinary equality narrows by these nonunique indexes; DAO binary rechecks retain exact identity.
-- A colliding custom index is an explicit upgrade blocker, never silently ignored or replaced.
DROP PROCEDURE IF EXISTS carlos_cds_provider_index68;
DELIMITER $$
CREATE PROCEDURE carlos_cds_provider_index68(
    IN expected_index VARCHAR(64), IN required_columns TEXT, IN expected_signature TEXT,
    IN index_definition TEXT, IN apply_change BOOLEAN)
SQL SECURITY INVOKER
body: BEGIN
    DECLARE actual_signature TEXT;
    DECLARE column_count INTEGER;
    DECLARE error_message VARCHAR(128);
    SELECT COUNT(*) INTO column_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='provider'
        AND FIND_IN_SET(column_name, required_columns)>0;
    IF column_count <> 1 + LENGTH(required_columns) - LENGTH(REPLACE(required_columns, ',', '')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve missing provider lookup columns before CDS upgrade';
    END IF;
    SELECT CONCAT(MIN(non_unique), ':', MIN(index_type), ':', MIN(ignored), ':',
        GROUP_CONCAT(CONCAT(HEX(column_name), ':', COALESCE(sub_part,0), ':', COALESCE(collation,'-'))
                     ORDER BY seq_in_index SEPARATOR ',')) INTO actual_signature
      FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='provider' AND index_name=expected_index;
    IF actual_signature IS NOT NULL AND BINARY(actual_signature) <> BINARY(expected_signature) THEN
        SET error_message=CONCAT('Review existing provider index ',expected_index,' before CDS upgrade');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=error_message;
    END IF;
    IF actual_signature IS NULL AND apply_change THEN
        SET @cds_provider_index_ddl=CONCAT('ALTER TABLE `provider` ADD INDEX `',
            REPLACE(expected_index,'`','``'),'` ',index_definition);
        PREPARE cds_provider_index_statement FROM @cds_provider_index_ddl;
        EXECUTE cds_provider_index_statement;
        DEALLOCATE PREPARE cds_provider_index_statement;
    END IF;
END$$
DELIMITER ;

-- Check every index before the first DDL change.
CALL carlos_cds_provider_index68('cds_provider_ohip','ohip_no,provider_no','1:BTREE:NO:6F6869705F6E6F:0:A,70726F76696465725F6E6F:0:A','(`ohip_no`,`provider_no`) USING BTREE',0);
CALL carlos_cds_provider_index68('cds_provider_cpso','practitionerNoType,practitionerNo,provider_no','1:BTREE:NO:70726163746974696F6E65724E6F54797065:0:A,70726163746974696F6E65724E6F:0:A,70726F76696465725F6E6F:0:A','(`practitionerNoType`,`practitionerNo`,`provider_no`) USING BTREE',0);
CALL carlos_cds_provider_index68('cds_provider_name','first_name,last_name,provider_no','1:BTREE:NO:66697273745F6E616D65:0:A,6C6173745F6E616D65:0:A,70726F76696465725F6E6F:0:A','(`first_name`,`last_name`,`provider_no`) USING BTREE',0);

-- Two complete source name components plus one separator are 101 characters, never 100.
-- Preserve wider storage and the actual adopted column metadata.
SET @cds_provider_name_ddl=(
    SELECT IF(data_type IN ('text','mediumtext','longtext') OR character_maximum_length >= 101, 'SELECT 1',
        CONCAT('ALTER TABLE `drugs` MODIFY COLUMN `outside_provider_name` VARCHAR(101)',
            ' CHARACTER SET ',character_set_name,' COLLATE ',collation_name,
            IF(is_nullable='YES',' NULL',' NOT NULL'),
            IF(column_default IS NULL,'',CONCAT(' DEFAULT ',column_default)),
            ' COMMENT ',QUOTE(column_comment)))
      FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='drugs' AND column_name='outside_provider_name'
);
PREPARE cds_provider_name_statement FROM @cds_provider_name_ddl;
EXECUTE cds_provider_name_statement;
DEALLOCATE PREPARE cds_provider_name_statement;

CALL carlos_cds_provider_index68('cds_provider_ohip','ohip_no,provider_no','1:BTREE:NO:6F6869705F6E6F:0:A,70726F76696465725F6E6F:0:A','(`ohip_no`,`provider_no`) USING BTREE',1);
CALL carlos_cds_provider_index68('cds_provider_cpso','practitionerNoType,practitionerNo,provider_no','1:BTREE:NO:70726163746974696F6E65724E6F54797065:0:A,70726163746974696F6E65724E6F:0:A,70726F76696465725F6E6F:0:A','(`practitionerNoType`,`practitionerNo`,`provider_no`) USING BTREE',1);
CALL carlos_cds_provider_index68('cds_provider_name','first_name,last_name,provider_no','1:BTREE:NO:66697273745F6E616D65:0:A,6C6173745F6E616D65:0:A,70726F76696465725F6E6F:0:A','(`first_name`,`last_name`,`provider_no`) USING BTREE',1);

DROP PROCEDURE carlos_cds_provider_index68;
SET @cds_provider_index_ddl=NULL;
SET @cds_provider_name_ddl=NULL;
