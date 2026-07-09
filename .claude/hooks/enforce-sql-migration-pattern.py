#!/usr/bin/env python3
"""
SQL Migration Pattern Enforcer Hook for Claude Code

This hook enforces CARLOS database migration standards:
- The Flyway V1 baseline (database/mysql/migration/**/V1*.sql) must NOT be hand-edited
- All schema changes must go into NEW forward migrations
  (database/mysql/migration/<common|on|bc>/V1.0.N__desc.sql, next free number), or the frozen legacy
  updates/ dir for historical patches
- Migrations must be idempotent and safe to run multiple times

Exit codes:
- 0: Safe operation (patch file or non-SQL file)
- 2: Blocked operation (attempting to modify original SQL files)
"""

import json
import re
import sys
from datetime import datetime
from pathlib import Path


# Flyway baseline files that must NOT be hand-edited. The V1 baseline is the genesis
# schema + reference data (captured once); schema changes ship as NEW forward migrations,
# never as edits to the baseline. Guarded by basename inside database/mysql/migration/.
PROTECTED_SQL_FILES = [
    "V1__baseline_schema.sql",
    "V1.0.1__on_schema.sql",
    "V1.0.2__on_data.sql",
    "V1.0.1__bc_schema.sql",
    "V1.0.2__bc_data.sql",
]

# Protected directories (cannot create ad-hoc raw SQL files directly here)
PROTECTED_DIRECTORIES = [
    "database/mysql",
    "database/mysql/SnomedCore",
]

# Allowed directories for new migrations: the legacy dated-patch dir (frozen, still read by
# a few tests) and the Flyway forward-migration locations.
PATCH_DIRECTORY = "database/mysql/updates"
MIGRATION_DIRECTORIES = (
    "database/mysql/migration/common",
    "database/mysql/migration/on",
    "database/mysql/migration/bc",
)

# Expected patch file naming pattern
PATCH_PATTERN = re.compile(r"^update-\d{4}-\d{2}-\d{2}-.+\.sql$")
# Flyway forward-migration naming: sequential versions in the V1.x family (next free number),
# e.g. V1.0.3__performance_indexes.sql. The five genesis baseline files are protected by exact
# basename above, so they can never be (re)created or edited through this allowance.
MIGRATION_PATTERN = re.compile(r"^V\d+(\.\d+)*__.+\.sql$")


def get_file_path_from_input(tool_input: dict) -> str:
    """Extracts the file path from the given tool input."""
    return tool_input.get("file_path", "")


def is_protected_sql_file(file_path: str) -> bool:
    """Check if the file is a protected Flyway baseline file (must not be hand-edited)."""
    path = Path(file_path)

    # Check if it's a baseline file inside the Flyway migration tree
    for protected_file in PROTECTED_SQL_FILES:
        if path.name == protected_file:
            if "database/mysql/migration" in file_path:
                return True

    return False


def is_valid_patch_file(file_path: str) -> bool:
    """Check if the file is a valid new migration: a dated patch in updates/, or a Flyway
    forward migration (V1.0.N__desc.sql, next free sequential version) under a location dir."""
    path = Path(file_path)

    # The genesis baseline files also match the sequential-version pattern; they are NEVER a
    # valid target through this allowance (main() would otherwise allow before the block check).
    if path.name in PROTECTED_SQL_FILES:
        return False

    # Legacy dated patch directory is FROZEN: existing files may still be edited (a few are read
    # by regression tests / applied for demo seeding), but NEW files there would be schema changes
    # outside Flyway history — those must go to migration/<common|on|bc> instead.
    if PATCH_DIRECTORY in file_path and PATCH_PATTERN.match(path.name) is not None:
        return path.exists()

    # Flyway forward migration under common/on/bc
    if any(loc in file_path for loc in MIGRATION_DIRECTORIES) and \
            MIGRATION_PATTERN.match(path.name) is not None:
        return True

    return False


def is_creating_sql_in_protected_dir(tool_name: str, file_path: str) -> bool:
    """Check if attempting to create a new SQL file in a protected directory."""
    if tool_name != "Write":
        return False

    if not file_path.endswith('.sql'):
        return False

    path = Path(file_path)

    # Check if trying to create SQL in protected directories
    for protected_dir in PROTECTED_DIRECTORIES:
        # Exact match - not in a subdirectory
        parent_str = str(path.parent)
        if parent_str == protected_dir or parent_str.endswith(f"/{protected_dir}"):
            return True

    return False


def generate_patch_filename_suggestion() -> str:
    """Generate a suggested patch filename based on the current date."""
    today = datetime.now().strftime("%Y-%m-%d")
    return f"update-{today}-description-here.sql"


def main():
    """Main entry point for the hook.
    
    This function reads JSON input from standard input and processes it based on
    the specified tool name.  It enforces migration standards by blocking
    modifications to protected SQL files and the creation of new SQL files in
    protected directories.  The function also handles JSON parsing errors and other
    exceptions gracefully, ensuring that the hook does not block on errors.
    """
    try:
        # Read JSON input from stdin
        input_data = json.load(sys.stdin)

        # Extract tool input
        tool_input = input_data.get("tool_input", {})
        tool_name = input_data.get("tool_name", "")

        # Only process Edit and Write tools
        if tool_name not in ("Edit", "Write"):
            sys.exit(0)

        # Get file path
        file_path = get_file_path_from_input(tool_input)

        # Only check SQL files
        if not file_path.endswith('.sql'):
            sys.exit(0)

        # Allow operations on valid patch files
        if is_valid_patch_file(file_path):
            sys.exit(0)

        # Block modification of protected baseline files
        if is_protected_sql_file(file_path):
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print("BLOCKED: Cannot hand-edit the Flyway V1 baseline", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("CARLOS migration standards require:", file=sys.stderr)
            print("  ✗ Do NOT edit the V1 baseline (migration/**/V1*.sql) — it is the genesis schema", file=sys.stderr)
            print("  ✓ Ship schema changes as NEW forward migrations:", file=sys.stderr)
            print("      database/mysql/migration/<common|on|bc>/V1.0.N__short_description.sql (next free number)\n", file=sys.stderr)
            print("To apply this schema change:", file=sys.stderr)
            print("  1. Create a forward migration: V1.0.N__short_description.sql (next free version)", file=sys.stderr)
            print("  2. Make your ALTER TABLE statements idempotent (check if exists first)", file=sys.stderr)
            print("  3. Test that the migration can be run multiple times safely\n", file=sys.stderr)
            print("Example idempotent ALTER TABLE:", file=sys.stderr)
            print("  SET @col_exists = 0;", file=sys.stderr)
            print("  SELECT COUNT(*) INTO @col_exists FROM information_schema.COLUMNS", file=sys.stderr)
            print("    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'table_name'", file=sys.stderr)
            print("          AND COLUMN_NAME = 'column_name';", file=sys.stderr)
            print("  SET @sql = IF(@col_exists = 0,", file=sys.stderr)
            print("    'ALTER TABLE table_name ADD COLUMN column_name varchar(25)',", file=sys.stderr)
            print("    'SELECT \"Column already exists\"');", file=sys.stderr)
            print("  PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;", file=sys.stderr)
            sys.exit(2)

        # Block creation of new SQL files in protected directories
        if is_creating_sql_in_protected_dir(tool_name, file_path):
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print("BLOCKED: Cannot create new SQL file in protected directory", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("CARLOS migration standards require:", file=sys.stderr)
            print("  ✗ Do NOT create ad-hoc SQL files in database/mysql/ (root)", file=sys.stderr)
            print("  ✓ Create a Flyway forward migration in a location directory\n", file=sys.stderr)
            print("To add this SQL content:", file=sys.stderr)
            print("  Create: database/mysql/migration/<common|on|bc>/V1.0.N__brief_description.sql", file=sys.stderr)
            print("\nForward-migration naming convention:", file=sys.stderr)
            print("  V1.0.N__brief_description.sql   (sequential; use the next free version number)", file=sys.stderr)
            print("  Example: V2026.07.08__add_provider_type_column.sql", file=sys.stderr)
            sys.exit(2)

        # Allow all other SQL operations
        sys.exit(0)

    except json.JSONDecodeError as e:
        print(f"Error parsing JSON input: {e}", file=sys.stderr)
        sys.exit(0)  # Don't block on parse errors
    except Exception as e:
        print(f"Error in SQL migration enforcer hook: {e}", file=sys.stderr)
        sys.exit(0)  # Don't block on unexpected errors


if __name__ == "__main__":
    main()
