---
description: Reset dev database data and reload development.sql with FAKE name patch
allowed-tools:
  - Bash(mysql -h db -u root -ppassword oscar < *)
  - Bash(mysql -h db -uroot -ppassword oscar *)
  - Bash(mysql -h db -uroot -ppassword oscar -e *)
  - Bash(curl * http://localhost:8080/*)
  - Bash(date *)
  - Bash(wc -l *)
---

# Reset Developer Database Data

This command resets the developer database data while preserving the schema, then reloads the development data and applies the FAKE name patch - mirroring the devcontainer initialization process.

## What This Does

1. **Preserves schema** - Uses TRUNCATE (not DROP), keeping all table structures intact
2. **Reloads demo data** - Loads `development.sql` which truncates 545+ tables and inserts fresh demo data
3. **Applies FAKE patch** - Prefixes patient names with "FAKE-" for clear identification of test data

## Execution Steps

### Step 1: Pre-flight Check

Verify database connectivity before proceeding:

```bash
mysql -h db -uroot -ppassword oscar -e "SELECT 1 AS connection_test"
```

If this fails, stop and report the database connection issue.

### Step 2: Load Development Data

Load the development.sql file which truncates all data tables and inserts fresh demo data:

```bash
mysql -h db -u root -ppassword oscar < /workspace/.devcontainer/db/scripts/development.sql
```

This file is approximately 56.5 MB and contains TRUNCATE + INSERT statements for all demo data.

### Step 3: Apply FAKE Name Sanitization Patch

Apply the FAKE name prefix patch to clearly identify test data:

```bash
mysql -h db -u root -ppassword oscar < /workspace/database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql
```

This patch is idempotent - it only updates names that don't already have the "FAKE-" prefix.

### Step 4: Verification

Verify the data was loaded correctly:

```bash
# Count patients
mysql -h db -uroot -ppassword oscar -e "SELECT COUNT(*) as patient_count FROM demographic"

# Verify FAKE prefix applied
mysql -h db -uroot -ppassword oscar -e "SELECT demographic_no, first_name, last_name FROM demographic LIMIT 5"

# Verify provider exists for login
mysql -h db -uroot -ppassword oscar -e "SELECT provider_no, first_name, last_name FROM provider WHERE provider_no = '999998'"
```

## Expected Results

After successful execution:
- Patient records are reloaded with fresh demo data
- All patient names are prefixed with "FAKE-"
- Login credentials remain: `openodoc` / `openo2025`
- Application should function normally with test data

## Troubleshooting

If the command fails:
1. **Database connection error**: Ensure the db container is running (`docker ps`)
2. **File not found**: Verify paths exist in the workspace
3. **Permission denied**: Check database user permissions

## Source Files

- `/workspace/.devcontainer/db/scripts/development.sql` - Main demo data (56.5 MB)
- `/workspace/database/mysql/updates/update-2025-11-06-demo-name-sanitization.sql` - FAKE prefix patch
