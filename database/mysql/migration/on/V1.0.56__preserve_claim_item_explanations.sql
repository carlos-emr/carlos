-- HX8 allows four 2-character codes and 55-character descriptions per item.
-- Include the code delimiters and separators, retaining already-wider columns
-- and their existing nullability, default, collation and comment.
SET @claim_exp_ddl = (
    SELECT IF(character_maximum_length < 255,
        CONCAT('ALTER TABLE billing_on_eareport MODIFY COLUMN exp VARCHAR(255) CHARACTER SET ',
               character_set_name, ' COLLATE ', collation_name,
               IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
               IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
               ' COMMENT ', QUOTE(column_comment)),
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'billing_on_eareport' AND column_name = 'exp'
);
PREPARE claim_exp_statement FROM @claim_exp_ddl;
EXECUTE claim_exp_statement;
DEALLOCATE PREPARE claim_exp_statement;

-- HXH and HXR each permit five 3-character claim errors. Keep all ten
-- with separators (39 characters), without shrinking adopted columns.
SET @claim_errors_ddl = (
    SELECT IF(character_maximum_length < 40,
        CONCAT('ALTER TABLE billing_on_eareport MODIFY COLUMN claim_error VARCHAR(40) CHARACTER SET ',
               character_set_name, ' COLLATE ', collation_name,
               IF(is_nullable = 'YES', ' NULL', ' NOT NULL'),
               IF(column_default IS NULL, '', CONCAT(' DEFAULT ', column_default)),
               ' COMMENT ', QUOTE(column_comment)),
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'billing_on_eareport' AND column_name = 'claim_error'
);
PREPARE claim_errors_statement FROM @claim_errors_ddl;
EXECUTE claim_errors_statement;
DEALLOCATE PREPARE claim_errors_statement;
