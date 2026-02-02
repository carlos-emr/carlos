# OpenO Developer Commands

This namespace contains slash commands for OpenO EMR developer workflow automation.

## Available Commands

| Command | Description |
|---------|-------------|
| `/openo-dev:resetsql-developer` | Reset dev database data and reload development.sql with FAKE name patch |

## Command Details

### `/openo-dev:resetsql-developer`

Resets the developer database to a clean state by:
1. Truncating all data tables (schema preserved)
2. Loading fresh demo data from `development.sql`
3. Applying the FAKE name sanitization patch

**Use when:**
- Database data is corrupted or inconsistent
- You need a fresh start with known test data
- After testing destructive operations on patient data

**Safe to use:**
- Schema is preserved (TRUNCATE, not DROP)
- Reference data tables are not affected
- FAKE patch is idempotent

**Login after reset:**
- Username: `openodoc`
- Password: `openo2025`

## Adding New Commands

To add a new developer command:

1. Create a new `.md` file in this directory
2. Add YAML frontmatter with:
   - `description`: Brief description shown in command list
   - `allowed-tools`: List of Bash commands the command can execute
3. Document the execution steps clearly
4. Test the command in a devcontainer environment

## Security Notes

These commands are designed for **isolated devcontainer environments only**:
- Passwords are hardcoded for development convenience
- Commands operate on synthetic/test data only
- Do NOT use in production or shared environments
