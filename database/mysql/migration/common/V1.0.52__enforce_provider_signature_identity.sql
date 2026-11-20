-- ProviderExt maps provider_no as its entity ID, but legacy schemas only index
-- that column non-uniquely. Repeated empty signature saves can create identical
-- rows and make Hibernate reject subsequent reads. Keep one exact copy for each
-- assigned provider and enforce the mapped identity. Preserve all unassigned
-- NULL-provider rows. Do not choose between conflicting signatures.
--
-- Run with all application nodes stopped (the normal upgrade requirement).
-- The temporary copy uses the source column definitions/collation. Its unique
-- index validates ALL values before the source is changed. BINARY DISTINCT is
-- deliberate: case/accent/space differences in signature text are not duplicates.
-- The staging key uses the column collation, so provider numbers differing only
-- in case or trailing spaces ('T099', 't099', 'T099 ') are one identity and are
-- refused as a conflict, even with equal signatures, rather than merged.
-- A duplicate-key failure here leaves providerExt untouched. Resolve conflicting
-- values from a backup with the provider, repair the failed Flyway entry using
-- the documented procedure, and retry. Never edit a published migration.
--
-- The repair rewrites rows through provider_no and signature only. An adopted
-- table with any other column would have that column's values replaced by
-- defaults, so refuse it before anything changes. The deliberately unknown
-- column name is the error an operator sees; MySQL cannot PREPARE a SIGNAL.
-- MySQL 8.0.30+ with sql_generate_invisible_primary_key adds an invisible
-- auto-increment my_row_id key to this key-less table. It carries no data, so
-- regenerating it on reinsert loses nothing and it is not counted.
SET @signature_unexpected_columns = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'providerExt'
        AND column_name NOT IN ('provider_no', 'signature')
        AND NOT (column_name = 'my_row_id' AND column_key = 'PRI'
            AND extra LIKE '%auto_increment%' AND extra LIKE '%INVISIBLE%')
);
SET @signature_column_guard = IF(@signature_unexpected_columns = 0, 'SELECT 1',
    'SELECT providerExt_has_unexpected_columns_resolve_before_signature_repair FROM providerExt');
PREPARE signature_column_statement FROM @signature_column_guard;
EXECUTE signature_column_statement;
DEALLOCATE PREPARE signature_column_statement;

-- The final step names its index providerExt_provider_no_uq. A site index
-- already holding that name but not enforcing the full provider_no identity
-- would make that CREATE fail only after the rows were rewritten and
-- committed, so refuse it here while providerExt is still untouched.
SET @signature_index_name_taken = (
    SELECT COUNT(*) FROM (
        SELECT index_name FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'providerExt'
            AND index_name = 'providerExt_provider_no_uq'
        GROUP BY index_name
        HAVING NOT (COUNT(*) = 1 AND MIN(non_unique) = 0
            AND MIN(column_name) = 'provider_no' AND MIN(sub_part) IS NULL)
    ) conflicting_indexes
);
SET @signature_index_name_guard = IF(@signature_index_name_taken = 0, 'SELECT 1',
    'SELECT providerExt_provider_no_uq_name_taken_resolve_before_signature_repair FROM providerExt');
PREPARE signature_index_name_statement FROM @signature_index_name_guard;
EXECUTE signature_index_name_statement;
DEALLOCATE PREPARE signature_index_name_statement;

CREATE TEMPORARY TABLE carlos_signature_identity_v1_0_52 LIKE providerExt;
ALTER TABLE carlos_signature_identity_v1_0_52
    ADD UNIQUE INDEX signature_identity_validation (provider_no);
INSERT INTO carlos_signature_identity_v1_0_52 (provider_no, signature)
SELECT DISTINCT BINARY provider_no, BINARY signature FROM providerExt
WHERE provider_no IS NOT NULL;
-- NULL provider IDs have no mapped identity and remain outside the unique rule.
-- Preserve their multiplicity, even when their signature values are identical.
INSERT INTO carlos_signature_identity_v1_0_52 (provider_no, signature)
SELECT provider_no, signature FROM providerExt
WHERE provider_no IS NULL;

START TRANSACTION;
DELETE FROM providerExt;
INSERT INTO providerExt (provider_no, signature)
SELECT provider_no, signature FROM carlos_signature_identity_v1_0_52;
COMMIT;
DROP TEMPORARY TABLE carlos_signature_identity_v1_0_52;

-- An adopted database may already enforce this identity under a different name.
SET @signature_identity_present = (
    SELECT COUNT(*) FROM (
        SELECT index_name FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'providerExt'
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MIN(non_unique) = 0
            AND MIN(column_name) = 'provider_no' AND MIN(sub_part) IS NULL
    ) unique_provider_indexes
);
SET @signature_identity_ddl = IF(@signature_identity_present > 0, 'SELECT 1',
    'CREATE UNIQUE INDEX providerExt_provider_no_uq ON providerExt (provider_no)');
PREPARE signature_identity_statement FROM @signature_identity_ddl;
EXECUTE signature_identity_statement;
DEALLOCATE PREPARE signature_identity_statement;
