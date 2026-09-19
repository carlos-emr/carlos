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
CREATE TEMPORARY TABLE carlos_signature_identity_v1_0_23_1 LIKE providerExt;
ALTER TABLE carlos_signature_identity_v1_0_23_1
    ADD UNIQUE INDEX signature_identity_validation (provider_no);
INSERT INTO carlos_signature_identity_v1_0_23_1 (provider_no, signature)
SELECT DISTINCT BINARY provider_no, BINARY signature FROM providerExt
WHERE provider_no IS NOT NULL;
-- NULL provider IDs have no mapped identity and remain outside the unique rule.
-- Preserve their multiplicity, even when their signature values are identical.
INSERT INTO carlos_signature_identity_v1_0_23_1 (provider_no, signature)
SELECT provider_no, signature FROM providerExt WHERE provider_no IS NULL;

START TRANSACTION;
DELETE FROM providerExt;
INSERT INTO providerExt (provider_no, signature)
SELECT provider_no, signature FROM carlos_signature_identity_v1_0_23_1;
COMMIT;
DROP TEMPORARY TABLE carlos_signature_identity_v1_0_23_1;

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
