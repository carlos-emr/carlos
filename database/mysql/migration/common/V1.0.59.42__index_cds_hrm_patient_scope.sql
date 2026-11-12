-- Keep the clinical owner string unchanged. The indexed virtual projection preserves
-- every positive INT patient alias accepted by CAST(owner AS UNSIGNED), including
-- leading whitespace, an optional plus, leading zeros and trailing nonnumeric text.
-- The source VARCHAR is at most 20 characters, so a negative value cannot wrap to a
-- positive INT patient. DECIMAL(20,0) holds every possible unsigned digit prefix
-- without overflow or SQL-mode-dependent truncation warnings on malformed text.
-- INVISIBLE preserves legacy positional INSERTs and SELECT * row shapes.
-- Guard reserved names before ALTER so partial-DDL replay cannot accept another shape.
SET @cds_hrm_source_ok = (
  SELECT COUNT(*)=1 AND MIN(column_type)='varchar(20)'
    AND MIN(is_nullable)='YES' AND COALESCE(MIN(generation_expression),'')=''
  FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='HRMDocumentToDemographic'
    AND column_name='demographicNo'
);
SET @cds_hrm_key_ok = (
  SELECT COUNT(*)=0 OR (COUNT(*)=1 AND MIN(column_type)='decimal(20,0)'
    AND MIN(is_nullable)='YES' AND MIN(extra)='VIRTUAL GENERATED, INVISIBLE'
    AND CAST(MIN(generation_expression) AS BINARY)=CAST(
      'cast(nullif(regexp_substr(`demographicNo`,''^[[:space:]]*[+]?[0-9]+''),'''') as decimal(20,0))' AS BINARY))
  FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='HRMDocumentToDemographic'
    AND column_name='cds_patient_key'
);
SET @cds_hrm_index_ok = (
  SELECT COUNT(*)=0 OR (COUNT(*)=1 AND MIN(non_unique)=1
    AND MIN(seq_in_index)=1 AND MIN(column_name)='cds_patient_key'
    AND MIN(index_type)='BTREE' AND MIN(sub_part) IS NULL)
  FROM information_schema.statistics
  WHERE table_schema=DATABASE() AND table_name='HRMDocumentToDemographic'
    AND index_name='idx_cds_hrm_patient_key'
);
SET @cds_hrm_guard = IF(@cds_hrm_source_ok AND @cds_hrm_key_ok AND @cds_hrm_index_ok,
  'SELECT 1', 'SELECT resolve_cds_hrm_patient_scope_shape_before_upgrade FROM HRMDocumentToDemographic');
PREPARE cds_hrm_scope_statement FROM @cds_hrm_guard;
EXECUTE cds_hrm_scope_statement;
DEALLOCATE PREPARE cds_hrm_scope_statement;

ALTER TABLE HRMDocumentToDemographic
  ADD COLUMN IF NOT EXISTS cds_patient_key DECIMAL(20,0)
    GENERATED ALWAYS AS (CAST(NULLIF(REGEXP_SUBSTR(demographicNo,
      '^[[:space:]]*[+]?[0-9]+'), '') AS DECIMAL(20,0))) VIRTUAL INVISIBLE,
  ADD INDEX IF NOT EXISTS idx_cds_hrm_patient_key (cds_patient_key);
