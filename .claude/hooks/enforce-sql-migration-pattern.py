#!/usr/bin/env python3
"""
SQL Migration Pattern Enforcer Hook for Claude Code

This hook enforces CARLOS database migration standards:
- Original SQL initialization files must NOT be modified
- All schema changes must go into dated patch files (update-YYYY-MM-DD-*.sql)
- Patch files must be idempotent and safe to run multiple times

Exit codes:
- 0: Safe operation (patch file or non-SQL file)
- 2: Blocked operation (attempting to modify original SQL files)
"""

import json
import re
import sys
from datetime import datetime
from pathlib import Path


# Original SQL files that must NOT be modified
PROTECTED_SQL_FILES = [
    "oscarinit.sql",
    "oscarinit_2025.sql",
    "oscarinit_bc.sql",
    "oscarinit_on.sql",
    "oscardata.sql",
    "oscardata_bc.sql",
    "oscardata_on.sql",
    "icd9.sql",
    "icd10.sql",
    "measurementMapData.sql",
]

# Protected directories (cannot create new SQL files here)
PROTECTED_DIRECTORIES = [
    "database/mysql",
    "database/mysql/caisi",
    "database/mysql/olis",
    "database/mysql/SnomedCore",
]

# Allowed directory for patch files
PATCH_DIRECTORY = "database/mysql/updates"

# Expected patch file naming pattern
PATCH_PATTERN = re.compile(r"^update-\d{4}-\d{2}-\d{2}-.+\.sql$")


def get_file_path_from_input(tool_input: dict) -> str:
    """Extract file path from tool input."""
    return tool_input.get("file_path", "")


def is_protected_sql_file(file_path: str) -> bool:
    """Check if the file is a protected original SQL initialization file."""
    path = Path(file_path)

    # Check if it's in a protected directory and matches protected filename
    for protected_file in PROTECTED_SQL_FILES:
        if path.name == protected_file:
            # Check if it's in database/mysql or subdirectories
            if "database/mysql" in file_path:
                return True

    return False


def is_valid_patch_file(file_path: str) -> bool:
    """Check if the file is a valid dated patch file in the updates directory."""
    path = Path(file_path)

    # Must be in the updates directory
    if PATCH_DIRECTORY not in file_path:
        return False

    # Must match the naming pattern: update-YYYY-MM-DD-*.sql
    return PATCH_PATTERN.match(path.name) is not None


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
    """Generate a suggested patch filename based on current date."""
    today = datetime.now().strftime("%Y-%m-%d")
    return f"update-{today}-description-here.sql"


def main():
    """Main entry point for the hook."""
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

        # Block modification of protected original SQL files
        if is_protected_sql_file(file_path):
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print(f"BLOCKED: Cannot modify original SQL initialization file", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("CARLOS migration standards require:", file=sys.stderr)
            print("  ✗ Do NOT modify original SQL files (oscarinit*.sql, oscardata*.sql, etc.)", file=sys.stderr)
            print("  ✓ Create dated patch files in database/mysql/updates/\n", file=sys.stderr)
            print("To apply this schema change:", file=sys.stderr)
            print(f"  1. Create: database/mysql/updates/{generate_patch_filename_suggestion()}", file=sys.stderr)
            print("  2. Make your ALTER TABLE statements idempotent (check if exists first)", file=sys.stderr)
            print("  3. Test that the patch can be run multiple times safely\n", file=sys.stderr)
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
            print(f"BLOCKED: Cannot create new SQL file in protected directory", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("CARLOS migration standards require:", file=sys.stderr)
            print("  ✗ Do NOT create SQL files in database/mysql/ (root)", file=sys.stderr)
            print("  ✓ Create dated patch files in database/mysql/updates/\n", file=sys.stderr)
            print("To add this SQL content:", file=sys.stderr)
            print(f"  Create: database/mysql/updates/{generate_patch_filename_suggestion()}", file=sys.stderr)
            print("\nPatch file naming convention:", file=sys.stderr)
            print("  update-YYYY-MM-DD-brief-description.sql", file=sys.stderr)
            print("  Example: update-2026-02-12-add-provider-type-column.sql", file=sys.stderr)
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
