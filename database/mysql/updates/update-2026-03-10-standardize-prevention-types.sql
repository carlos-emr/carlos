-- ============================================================================
-- Standardize prevention type names to Health Canada / OSCAR 19 standard
-- ============================================================================
-- This script migrates all legacy and non-standard prevention type names in the
-- `preventions` table to the canonical Health Canada immunization abbreviations.
--
-- IDEMPOTENT: Safe to run multiple times. WHERE clauses prevent duplicate updates.
--
-- Table: preventions (column: prevention_type)
-- Table: preventionsExt (columns: prevention_id, keyval, val)
--
-- Strategy:
--   1. Direct type mappings: known O19 / legacy names → canonical HC type
--   2. Catch-all: any remaining unrecognized types → OtherA
--      with original type preserved in preventionsExt comment
--
-- Health Canada abbreviation reference:
--   https://www.canada.ca/en/public-health/services/provincial-territorial-immunization-information.html
-- ============================================================================

-- ============================================================================
-- SECTION 1: DIRECT TYPE MAPPINGS
-- ============================================================================

-- Influenza
UPDATE preventions SET prevention_type = 'Inf' WHERE prevention_type = 'Flu';
UPDATE preventions SET prevention_type = 'Inf' WHERE prevention_type = 'H1N1';
UPDATE preventions SET prevention_type = 'Inf' WHERE prevention_type = 'Influenza';

-- Varicella (Chickenpox)
UPDATE preventions SET prevention_type = 'Var' WHERE prevention_type = 'VZ';
UPDATE preventions SET prevention_type = 'Var' WHERE prevention_type = 'Varicella';

-- MMR-Var (was MMRV)
UPDATE preventions SET prevention_type = 'MMR-Var' WHERE prevention_type = 'MMRV';

-- Rotavirus
UPDATE preventions SET prevention_type = 'Rota' WHERE prevention_type = 'Rot';
UPDATE preventions SET prevention_type = 'Rota' WHERE prevention_type = 'Rotavirus';

-- Tdap / Td / Td-IPV (Tetanus-Diphtheria-Pertussis variants)
UPDATE preventions SET prevention_type = 'Tdap' WHERE prevention_type = 'dTap';
UPDATE preventions SET prevention_type = 'Tdap' WHERE prevention_type = 'dTaP';
UPDATE preventions SET prevention_type = 'Td-IPV' WHERE prevention_type = 'TdP';
UPDATE preventions SET prevention_type = 'Td-IPV' WHERE prevention_type = 'TdP-IPV';

-- Hepatitis B
UPDATE preventions SET prevention_type = 'HB' WHERE prevention_type = 'HepB';

-- Hepatitis A
UPDATE preventions SET prevention_type = 'HA' WHERE prevention_type = 'HepA';

-- Hepatitis A+B
UPDATE preventions SET prevention_type = 'HAHB' WHERE prevention_type = 'HepAB';
UPDATE preventions SET prevention_type = 'HAHB' WHERE prevention_type = 'HepA+B';

-- Rabies
UPDATE preventions SET prevention_type = 'Rab' WHERE prevention_type = 'Rabies';

-- Typhoid
UPDATE preventions SET prevention_type = 'Typh-I' WHERE prevention_type = 'Typhoid';
UPDATE preventions SET prevention_type = 'Typh-I' WHERE prevention_type = 'Typhoid-I';
UPDATE preventions SET prevention_type = 'Typh-I' WHERE prevention_type = 'Typh';

-- Pneumococcal Polysaccharide
UPDATE preventions SET prevention_type = 'Pneu-P-23' WHERE prevention_type = 'Pneumovax';
UPDATE preventions SET prevention_type = 'Pneu-P-23' WHERE prevention_type = 'Pneumococcus';

-- Pneumococcal Conjugate (legacy valences)
-- Note: Pneu-C-7 records are kept as Pneu-C (generic) for older 7-valent entries
UPDATE preventions SET prevention_type = 'Pneu-C' WHERE prevention_type = 'Pneu-C-7';
UPDATE preventions SET prevention_type = 'Pneu-C' WHERE prevention_type = 'Pneu';

-- Meningococcal C Conjugate (MenC-C was non-standard CARLOS name)
UPDATE preventions SET prevention_type = 'Men-C-C' WHERE prevention_type = 'MenC-C';

-- HPV (generic HPV vaccine)
UPDATE preventions SET prevention_type = 'HPV' WHERE prevention_type = 'HPV Vaccine';

-- Zoster (Zostavax = LZV; Shingrix = RZV)
UPDATE preventions SET prevention_type = 'LZV' WHERE prevention_type = 'HZV';
UPDATE preventions SET prevention_type = 'LZV' WHERE prevention_type = 'Zos';
UPDATE preventions SET prevention_type = 'LZV' WHERE prevention_type = 'Zostavax';

-- DTaP-HB-IPV-Hib (was DTaP-HBV-IPV-Hib with erroneous V)
UPDATE preventions SET prevention_type = 'DTaP-HB-IPV-Hib' WHERE prevention_type = 'DTaP-HBV-IPV-Hib';

-- Cholera
UPDATE preventions SET prevention_type = 'Chol-O' WHERE prevention_type = 'CHOLERA';
UPDATE preventions SET prevention_type = 'Chol-O' WHERE prevention_type = 'Dukoral';

-- LDCT (was CTC - low-dose chest CT for lung cancer screening)
UPDATE preventions SET prevention_type = 'LDCT' WHERE prevention_type = 'CTC';

-- Measles monovalent
UPDATE preventions SET prevention_type = 'M' WHERE prevention_type = 'Measles';

-- Tetanus monovalent
UPDATE preventions SET prevention_type = 'T' WHERE prevention_type = 'Tetanus';

-- IPV / OPV (historical)
-- Note: OPV kept as-is since HC list includes it; consider OtherA if no clinical need
UPDATE preventions SET prevention_type = 'IPV' WHERE prevention_type = 'Poliovirus';
UPDATE preventions SET prevention_type = 'IPV' WHERE prevention_type = 'fIPV';

-- rMenB (Meningococcal B recombinant)
UPDATE preventions SET prevention_type = 'rMenB' WHERE prevention_type = 'Men-B';

-- ============================================================================
-- SECTION 2: CATCH-ALL - LEGACY AND UNRECOGNIZED TYPES → OtherA
-- ============================================================================
-- For any remaining prevention type not in the canonical list, preserve the
-- original type in a preventionsExt comment and migrate to OtherA.
--
-- The canonical set of recognized prevention types (do NOT migrate these):
--   Immunizations: Anth, BCG, Chol, Chol-Ecol-O, Chol-O, COVID-19, d, DT, DT-IPV,
--     DTaP, DTaP-HB-IPV, DTaP-HB-IPV-Hib, DTaP-Hib, DTaP-IPV, DTaP-IPV-Hib,
--     DTaP-IPV-HB, DTaP-IPV-Hib-HB, HA, HA-Typh-I, HAHB, HB, HBTmf, Hib,
--     HPV, HPV-2, HPV-4, HPV-9, H1N1, Inf, IPV, JE, LZV, M, Men-C-ACYW-135,
--     Men-C-C, Men-P-AC, Men-P-ACWY, Men-P-ACYW-135, MMR, MMR-Var, MR, OPV,
--     Pneu-C, Pneu-C-10, Pneu-C-13, Pneu-C-15, Pneu-C-20, Pneu-P-23,
--     Rab, rMenB, Rota, Rota-1, Rota-5, RSV, RSVAb, RZV, T, TBE, Td, Td-IPV,
--     Tdap, Tdap-IPV, TdP-IPV-Hib, Typh-I, Typh-O, Var, YF,
--     COVID-19, BCG, JE, TBE, Chol-Ecol-O, HA-Typh-I
--   Screenings: AAA, BMD, chlamydia, COLONOSCOPY, FOBT, ghonorrhea PCR,
--     HepB screen, HepC screen, HIV, HPV-CERVIX, LDCT, MAM, PAP, PHV, PSA,
--     Smoking, Tuberculosis, VDRL
--   Other: OtherA, OtherB
-- ============================================================================

-- Step 2a: For legacy records that have an existing comment in preventionsExt,
--          prepend the original type to that comment.
UPDATE preventionsExt pe
JOIN preventions p ON pe.prevention_id = p.id
SET pe.val = CONCAT('[Legacy prevention type: ', p.prevention_type, '] ', COALESCE(pe.val, ''))
WHERE p.prevention_type NOT IN (
    -- Immunizations
    'Anth', 'BCG', 'Chol', 'Chol-Ecol-O', 'Chol-O', 'COVID-19',
    'd', 'DT', 'DT-IPV', 'DTaP', 'DTaP-HB-IPV', 'DTaP-HB-IPV-Hib',
    'DTaP-Hib', 'DTaP-IPV', 'DTaP-IPV-Hib', 'DTaP-IPV-HB', 'DTaP-IPV-Hib-HB',
    'EZV', 'fIPV', 'HA', 'HA-Typh-I', 'HAHB', 'HB', 'HBTmf', 'Hib',
    'HPV', 'HPV-2', 'HPV-4', 'HPV-9', 'H1N1', 'Inf', 'IPV', 'JE',
    'LZV', 'M', 'Men-C-ACYW-135', 'Men-C-C', 'Men-P-AC', 'Men-P-ACWY',
    'Men-P-ACYW-135', 'MMR', 'MMR-Var', 'MR', 'OPV',
    'Pneu-C', 'Pneu-C-10', 'Pneu-C-13', 'Pneu-C-15', 'Pneu-C-20', 'Pneu-P-23',
    'Rab', 'rMenB', 'Rota', 'Rota-1', 'Rota-5', 'RSV', 'RSVAb', 'RZV',
    'T', 'TBE', 'Td', 'Td-IPV', 'Tdap', 'Tdap-IPV', 'TdP-IPV-Hib',
    'Typh-I', 'Typh-O', 'Var', 'YF',
    -- Screenings
    'AAA', 'BMD', 'chlamydia', 'COLONOSCOPY', 'FOBT', 'ghonorrhea PCR',
    'HepB screen', 'HepC screen', 'HIV', 'HPV-CERVIX', 'LDCT', 'MAM',
    'PAP', 'PHV', 'PSA', 'Smoking', 'Tuberculosis', 'VDRL',
    -- Other
    'OtherA', 'OtherB'
)
AND pe.keyval = 'comments';

-- Step 2b: For legacy records that do NOT yet have a comment, insert one with the type.
INSERT INTO preventionsExt (prevention_id, keyval, val)
SELECT p.id, 'comments', CONCAT('[Legacy prevention type: ', p.prevention_type, ']')
FROM preventions p
LEFT JOIN preventionsExt pe ON pe.prevention_id = p.id AND pe.keyval = 'comments'
WHERE p.prevention_type NOT IN (
    -- Immunizations
    'Anth', 'BCG', 'Chol', 'Chol-Ecol-O', 'Chol-O', 'COVID-19',
    'd', 'DT', 'DT-IPV', 'DTaP', 'DTaP-HB-IPV', 'DTaP-HB-IPV-Hib',
    'DTaP-Hib', 'DTaP-IPV', 'DTaP-IPV-Hib', 'DTaP-IPV-HB', 'DTaP-IPV-Hib-HB',
    'EZV', 'fIPV', 'HA', 'HA-Typh-I', 'HAHB', 'HB', 'HBTmf', 'Hib',
    'HPV', 'HPV-2', 'HPV-4', 'HPV-9', 'H1N1', 'Inf', 'IPV', 'JE',
    'LZV', 'M', 'Men-C-ACYW-135', 'Men-C-C', 'Men-P-AC', 'Men-P-ACWY',
    'Men-P-ACYW-135', 'MMR', 'MMR-Var', 'MR', 'OPV',
    'Pneu-C', 'Pneu-C-10', 'Pneu-C-13', 'Pneu-C-15', 'Pneu-C-20', 'Pneu-P-23',
    'Rab', 'rMenB', 'Rota', 'Rota-1', 'Rota-5', 'RSV', 'RSVAb', 'RZV',
    'T', 'TBE', 'Td', 'Td-IPV', 'Tdap', 'Tdap-IPV', 'TdP-IPV-Hib',
    'Typh-I', 'Typh-O', 'Var', 'YF',
    -- Screenings
    'AAA', 'BMD', 'chlamydia', 'COLONOSCOPY', 'FOBT', 'ghonorrhea PCR',
    'HepB screen', 'HepC screen', 'HIV', 'HPV-CERVIX', 'LDCT', 'MAM',
    'PAP', 'PHV', 'PSA', 'Smoking', 'Tuberculosis', 'VDRL',
    -- Other
    'OtherA', 'OtherB'
)
AND pe.id IS NULL;

-- Step 2c: Migrate all remaining unrecognized types to OtherA.
UPDATE preventions SET prevention_type = 'OtherA'
WHERE prevention_type NOT IN (
    -- Immunizations
    'Anth', 'BCG', 'Chol', 'Chol-Ecol-O', 'Chol-O', 'COVID-19',
    'd', 'DT', 'DT-IPV', 'DTaP', 'DTaP-HB-IPV', 'DTaP-HB-IPV-Hib',
    'DTaP-Hib', 'DTaP-IPV', 'DTaP-IPV-Hib', 'DTaP-IPV-HB', 'DTaP-IPV-Hib-HB',
    'EZV', 'fIPV', 'HA', 'HA-Typh-I', 'HAHB', 'HB', 'HBTmf', 'Hib',
    'HPV', 'HPV-2', 'HPV-4', 'HPV-9', 'H1N1', 'Inf', 'IPV', 'JE',
    'LZV', 'M', 'Men-C-ACYW-135', 'Men-C-C', 'Men-P-AC', 'Men-P-ACWY',
    'Men-P-ACYW-135', 'MMR', 'MMR-Var', 'MR', 'OPV',
    'Pneu-C', 'Pneu-C-10', 'Pneu-C-13', 'Pneu-C-15', 'Pneu-C-20', 'Pneu-P-23',
    'Rab', 'rMenB', 'Rota', 'Rota-1', 'Rota-5', 'RSV', 'RSVAb', 'RZV',
    'T', 'TBE', 'Td', 'Td-IPV', 'Tdap', 'Tdap-IPV', 'TdP-IPV-Hib',
    'Typh-I', 'Typh-O', 'Var', 'YF',
    -- Screenings
    'AAA', 'BMD', 'chlamydia', 'COLONOSCOPY', 'FOBT', 'ghonorrhea PCR',
    'HepB screen', 'HepC screen', 'HIV', 'HPV-CERVIX', 'LDCT', 'MAM',
    'PAP', 'PHV', 'PSA', 'Smoking', 'Tuberculosis', 'VDRL',
    -- Other
    'OtherA', 'OtherB'
);

-- ============================================================================
-- VERIFICATION QUERIES (optional, for post-migration review):
-- ============================================================================
-- Check for any remaining non-standard types:
-- SELECT DISTINCT prevention_type, COUNT(*) as cnt
-- FROM preventions
-- GROUP BY prevention_type
-- ORDER BY cnt DESC;
--
-- Review preserved legacy type comments:
-- SELECT p.id, p.prevention_type, pe.val
-- FROM preventions p
-- JOIN preventionsExt pe ON pe.prevention_id = p.id
-- WHERE pe.keyval = 'comments' AND pe.val LIKE '[Legacy%'
-- ORDER BY p.id DESC
-- LIMIT 100;
