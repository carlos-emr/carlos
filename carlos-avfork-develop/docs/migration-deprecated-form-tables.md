# Migration Guide: Deprecated Form Tables

**Date:** 2026-03-25
**Migration Script:** `database/mysql/updates/update-2026-03-25-remove-deprecated-form-tables.sql`

## Overview

Several legacy medical form tables have been deprecated and their associated JSP pages
removed from the CARLOS EMR codebase. The migration script drops these tables and removes
their `encounterForm` registry entries.

**This migration is NOT run automatically.** It must be executed manually after verifying
that no patient data will be lost.

## Affected Tables

| Table | Form Name | Description |
|-------|-----------|-------------|
| `formAR` | AR | Legacy antenatal record form |
| `formintakeb` | (none) | Legacy intake form B (generic intake module) |
| `formONAR` | AR2005 | Ontario Antenatal Record form |
| `formONAREnhancedRecord` | ON AR Enhanced | Enhanced Ontario Antenatal Record |
| `formONAREnhancedRecordExt1` | (extension) | Extension table 1 for ON AR Enhanced |
| `formONAREnhancedRecordExt2` | (extension) | Extension table 2 for ON AR Enhanced |
| `formType2Diabetes` | T2Diabetes | Type 2 Diabetes form (replaced by newer forms) |
| `formAdf` | ADF | ADF form (replaced by formAdfV2) |
| `formIntakeHx` | Student Intake Hx | Student Intake History form |

## Pre-Migration Checklist

Before running the migration, verify each table for existing patient data:

```sql
-- Check row counts for all affected tables
SELECT 'formAR' AS table_name, COUNT(*) AS row_count FROM formAR
UNION ALL SELECT 'formintakeb', COUNT(*) FROM formintakeb
UNION ALL SELECT 'formONAR', COUNT(*) FROM formONAR
UNION ALL SELECT 'formONAREnhancedRecord', COUNT(*) FROM formONAREnhancedRecord
UNION ALL SELECT 'formONAREnhancedRecordExt1', COUNT(*) FROM formONAREnhancedRecordExt1
UNION ALL SELECT 'formONAREnhancedRecordExt2', COUNT(*) FROM formONAREnhancedRecordExt2
UNION ALL SELECT 'formType2Diabetes', COUNT(*) FROM formType2Diabetes
UNION ALL SELECT 'formAdf', COUNT(*) FROM formAdf
UNION ALL SELECT 'formIntakeHx', COUNT(*) FROM formIntakeHx;
```

If any table returns a non-zero count, that table contained patient-entered form data.

## Data Loss Warning

**Once the migration script runs, data in these tables is permanently deleted.** The JSP
pages that rendered this data have been removed from the codebase, so the data is no longer
viewable through the application even before migration. However, the raw data still exists
in the database tables until the migration drops them.

If any of these forms were actively used at your site:

1. **Export the data first** -- Back up the affected tables before running the migration:
   ```sql
   -- Example: export formONAR data
   SELECT * FROM formONAR INTO OUTFILE '/tmp/formONAR_backup.csv'
   FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\n';
   ```
   Or use `mysqldump`:
   ```bash
   mysqldump -u root -p oscar formONAR formONAREnhancedRecord \
     formONAREnhancedRecordExt1 formONAREnhancedRecordExt2 > onar_backup.sql
   ```

2. **Assess clinical impact** -- Determine if any active patients have data only in these
   forms. Cross-reference with `demographic_no` columns to identify affected patients.

3. **Document the decision** -- Record which tables had data, how many records, and whether
   the data was exported or deemed no longer clinically relevant.

## Running the Migration

After completing the pre-migration checklist:

```bash
mysql -u root -p oscar < database/mysql/updates/update-2026-03-25-remove-deprecated-form-tables.sql
```

The script is idempotent (`DROP TABLE IF EXISTS`) and safe to run multiple times.

## Why These Forms Were Removed

- **formAR / formONAR / formONAREnhancedRecord**: Ontario-specific antenatal record forms.
  The JSP pages were removed as part of form deprecation in PR #727. Several of these tables
  caused `ERROR 1118: Row size too large > 8126` on fresh installations due to excessive
  column counts.
- **formType2Diabetes / formAdf**: Replaced by newer form versions (formAdfV2).
- **formIntakeHx / formintakeb**: Legacy intake forms from the generic intake module, which
  was removed in earlier cleanup efforts.

## Related Changes

- JSP pages removed: `formIntakeHx*.jsp`, `formonarenhanced*.jsp`, `formonarpg*.jsp`, and
  related form action classes
- `encounterForm` registry entries are deleted by the migration, so the forms no longer
  appear in the encounter form picker
- `development.sql` seed data references to these tables have been removed
- `formfollowup` (CAISI table) is separately deprecated in `initcaisi.sql` but is NOT
  dropped by this migration
