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
-- A duplicate-key failure here leaves providerExt untouched. Resolve conflicting
-- values from a backup with the provider, repair the failed Flyway entry using
-- the documented procedure, and retry. Never edit a published migration.
--
-- The repair rewrites rows through provider_no and signature only. An adopted
-- table with any other column would have that column's values replaced by
-- defaults, so refuse it before anything changes. The deliberately unknown
-- column name is the error an operator sees; MySQL cannot PREPARE a SIGNAL.
SET @signature_unexpected_columns = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'providerExt'
        AND column_name NOT IN ('provider_no', 'signature')
);
SET @signature_column_guard = IF(@signature_unexpected_columns = 0, 'SELECT 1',
    'SELECT providerExt_has_unexpected_columns_resolve_before_signature_repair FROM providerExt');
PREPARE signature_column_statement FROM @signature_column_guard;
EXECUTE signature_column_statement;
DEALLOCATE PREPARE signature_column_statement;

CREATE TEMPORARY TABLE carlos_signature_identity_v1_0_40 LIKE providerExt;
ALTER TABLE carlos_signature_identity_v1_0_40
    ADD UNIQUE INDEX signature_identity_validation (provider_no);
INSERT INTO carlos_signature_identity_v1_0_40 (provider_no, signature)
SELECT DISTINCT BINARY provider_no, BINARY signature FROM providerExt
WHERE provider_no IS NOT NULL;
-- NULL provider IDs have no mapped identity and remain outside the unique rule.
-- Preserve their multiplicity, even when their signature values are identical.
INSERT INTO carlos_signature_identity_v1_0_40 (provider_no, signature)
SELECT provider_no, signature FROM providerExt
WHERE provider_no IS NULL;

START TRANSACTION;
DELETE FROM providerExt;
INSERT INTO providerExt (provider_no, signature)
SELECT provider_no, signature FROM carlos_signature_identity_v1_0_40;
COMMIT;
DROP TEMPORARY TABLE carlos_signature_identity_v1_0_40;

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
