---
description: Reset dev database data and reload development.sql with FAKE name patch
allowed-tools:
  - Bash(mariadb -h db -u root -ppassword carlos < *)
  - Bash(mariadb -h db -uroot -ppassword carlos *)
  - Bash(mariadb -h db -uroot -ppassword carlos -e *)
  - Bash(sh /workspace/.devcontainer/db/scripts/reapply_security_migrations.sh /workspace/database/mysql/migration carlos *)
  - Bash(curl * http://localhost:8080/*)
  - Bash(date *)
  - Bash(wc -l *)
---

# Reset Developer Database Data

This command resets the developer database data while preserving the schema, then reloads the development data and applies the FAKE name sanitization - mirroring the devcontainer initialization process (`.devcontainer/db/scripts/populate_db.sh`).

## What This Does

1. **Preserves schema** - Uses TRUNCATE (not DROP), keeping all table structures intact
2. **Reloads demo data** - Loads `development.sql` which truncates ~400 tables and inserts fresh demo data
3. **Restores privileges** - Re-applies the Flyway migrations that add security objects, then the current role privileges that the snapshot's older `secObjPrivilege` rows clobber, so the roles match a packaged install
4. **Seeds fake specialists** - Loads the 60-entry clearly-fake referral specialist list and provider/facility links
5. **Applies FAKE sanitization v2** - Prefixes person names with "FAKE-" across all name-bearing tables (patients, providers, appointments, forms, HL7 info, ...) and replaces known real names

## Execution Steps

### Step 1: Pre-flight Check

Verify database connectivity before proceeding:

```bash
mariadb -h db -uroot -ppassword carlos -e "SELECT 1 AS connection_test"
```

If this fails, stop and report the database connection issue.

### Step 2: Load Development Data

Load the development.sql file which truncates all data tables and inserts fresh demo data:

```bash
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/development.sql
```

This file is approximately 54 MB and contains TRUNCATE + INSERT statements for all demo data.

### Step 3: Restore Privileges

The snapshot carries an older security set. First re-apply every Flyway migration that touches the `sec*` tables (it wipes the security objects added after it was taken, issue #4369), then restore the role rows it carries differently from the Flyway seeds (issue #4566). Run them in this order, as `populate_db.sh` does:

```bash
sh /workspace/.devcontainer/db/scripts/reapply_security_migrations.sh /workspace/database/mysql/migration carlos -h db -u root -ppassword
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/development_privileges.sql
```

### Step 4: Seed Fake Specialists and Provider Links

```bash
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/demo-provider-links.sql
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/demo-specialists.sql
```

### Step 5: Apply FAKE Name Sanitization (v2)

Apply the FAKE- prefixes and real-name replacements. The `-on` supplement covers Ontario-only form tables; the devcontainer database uses the Ontario schema, so both apply:

```bash
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/demo-name-sanitization.sql
mariadb -h db -u root -ppassword carlos < /workspace/.devcontainer/db/scripts/demo-name-sanitization-on.sql
```

Every statement is idempotent - names already carrying the "FAKE-" prefix are never touched again, so re-running can never produce "FAKE-FAKE-". The functional accounts (`-1` system and `999998` carlosdoc, `doctor, doctor`) are exempt.

### Step 6: Verification

Verify the data was loaded correctly:

```bash
# Count patients
mariadb -h db -uroot -ppassword carlos -e "SELECT COUNT(*) as patient_count FROM demographic"

# Verify FAKE prefix applied
mariadb -h db -uroot -ppassword carlos -e "SELECT demographic_no, first_name, last_name FROM demographic LIMIT 5"

# Verify provider exists for login (exempt from the FAKE- prefix)
mariadb -h db -uroot -ppassword carlos -e "SELECT provider_no, first_name, last_name FROM provider WHERE provider_no = '999998'"

# Verify the 60 fake specialists
mariadb -h db -uroot -ppassword carlos -e "SELECT COUNT(*) as fake_specialists FROM professionalSpecialists WHERE specId BETWEEN 9001 AND 9060"
```

## Expected Results

After successful execution:
- Patient records are reloaded with fresh demo data
- All patient names are prefixed with "FAKE-"; provider names too, except the functional accounts
- 60 clearly-fake referral specialists (specIds 9001-9060, referral numbers 99001-99060)
- Login credentials remain: `carlosdoc` / `carlos2026`
- Application should function normally with test data

## Troubleshooting

If the command fails:
1. **Database connection error**: Ensure the db container is running (`docker ps`)
2. **File not found**: Verify paths exist in the workspace
3. **Permission denied**: Check database user permissions

## Source Files

- `/workspace/.devcontainer/db/scripts/development.sql` - Main demo data (~54 MB)
- `/workspace/.devcontainer/db/scripts/reapply_security_migrations.sh` - Re-applies the security-object Flyway migrations
- `/workspace/.devcontainer/db/scripts/development_privileges.sql` - Role privileges restore (matches the Flyway seeds)
- `/workspace/.devcontainer/db/scripts/demo-provider-links.sql` - Guarded provider/facility links
- `/workspace/.devcontainer/db/scripts/demo-specialists.sql` - 60 clearly-fake referral specialists
- `/workspace/.devcontainer/db/scripts/demo-name-sanitization.sql` - FAKE- sanitization v2 (common tables)
- `/workspace/.devcontainer/db/scripts/demo-name-sanitization-on.sql` - Ontario-only form tables

The deb installer's optional demo load (`carlos-ctl demo-data`) uses an additive transform of the same dataset - see `scripts/build-demo-additive.sh`.
