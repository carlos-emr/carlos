#!/usr/bin/env python3
"""
SQL Migration Pattern Enforcer Hook for Claude Code

This hook enforces CARLOS database migration standards:
- The five Flyway genesis baseline files (V1__baseline_schema.sql plus the province
  V1.0.1/V1.0.2 schema+data files under database/mysql/migration/**) must NOT be hand-edited.
  Note this is an exact-basename set, NOT the glob V1*.sql — forward migrations such as
  V1.0.3__*.sql / V1.0.4__*.sql are ordinary editable deltas, not immutable baseline.
- All schema changes must go into NEW forward migrations
  (database/mysql/migration/<common|on|bc>/V1.0.N__desc.sql, next free number), or the frozen legacy
  updates/ dir for historical patches
- Migrations must be idempotent and safe to run multiple times

Exit codes:
- 0: Safe operation (patch file or non-SQL file)
- 2: Blocked operation (attempting to modify original SQL files)
"""

import json
import posixpath
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
# Flyway forward-migration naming: exactly the documented V1.0.N convention (sequential, next
# free number), e.g. V1.0.3__performance_indexes.sql. Anything else — date-style (V2026.07.08),
# new-major (V2.0.0), minor bumps (V1.1), or sub-versions (V1.0.3.1) — is rejected. The trailing
# component is [1-9]\d* so leading zeros (V1.0.03, which Flyway parses as 1.0.3 — a SILENT
# duplicate of V1.0.3) and the below-baseline V1.0.0 are rejected too. The five genesis baseline
# files are additionally protected by exact basename above.
MIGRATION_PATTERN = re.compile(r"^V1\.0\.[1-9]\d*__.+\.sql$")
# Version prefix extractor for the duplicate-version scan (same shape as MIGRATION_PATTERN).
MIGRATION_VERSION = re.compile(r"^(V1\.0\.[1-9]\d*)__")

# Which location dirs are ever applied together. Flyway loads `common` + exactly one province, so
# a version is only a real collision within a co-applied set; the two provinces never co-apply
# (on/V1.0.4 and bc/V1.0.4 can legitimately coexist).
CO_APPLIED_LOCATIONS = {
    "common": ("common", "on", "bc"),
    "on": ("common", "on"),
    "bc": ("common", "bc"),
}



def migration_location_for(file_path: str) -> str | None:
    """Return the common|on|bc migration location containing file_path, if any."""
    normalized = Path(file_path).as_posix()
    for loc in MIGRATION_DIRECTORIES:
        if normalized == loc or normalized.startswith(f"{loc}/") or f"/{loc}/" in normalized:
            return loc
    return None


def is_direct_child_of_migration_location(file_path: str) -> bool:
    """Migrations must be direct children of common/on/bc, not nested descendants."""
    parent = Path(file_path).parent.as_posix()
    loc = migration_location_for(file_path)
    return loc is not None and (parent == loc or parent.endswith(f"/{loc}"))

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


def has_duplicate_version(file_path: str) -> bool:
    """True if another migration in the co-applied set already uses this file's V1.0.N version.

    Flyway rejects two migrations with the same version in one applied set (common + one province),
    and PROTECTED_SQL_FILES only guards five exact basenames — so a new file with an existing
    version but a different description (e.g. on/V1.0.1__new_change.sql vs the protected
    V1.0.1__on_schema.sql) would pass the shape check yet fail Flyway. Catch it here instead.
    """
    path = Path(file_path)
    m = MIGRATION_VERSION.match(path.name)
    if m is None:
        return False
    version = m.group(1)
    # Identify the common|on|bc location from the FULL path via the location anchors, not the
    # immediate parent: a migration may sit in a nested dir (Flyway scans locations recursively),
    # in which case path.parent.name would be the subdir and the scan would be silently skipped.
    loc = None
    migration_root = None
    for md in MIGRATION_DIRECTORIES:  # "database/mysql/migration/<loc>"
        anchor = f"{md}/"
        idx = file_path.find(anchor)
        if idx != -1:
            loc = md.rsplit("/", 1)[1]                              # common | on | bc
            migration_root = Path(file_path[:idx]) / md.rsplit("/", 1)[0]  # .../migration
            break
    if loc is None or migration_root is None:
        return False
    self_resolved = path.resolve()
    for sibling_loc in CO_APPLIED_LOCATIONS.get(loc, ()):
        sibling_dir = migration_root / sibling_loc
        if not sibling_dir.is_dir():
            continue
        # rglob (not glob) so an existing nested migration with the same version is caught too.
        for existing in sibling_dir.rglob(f"{version}__*.sql"):
            if existing.resolve() != self_resolved:
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
    # (Trailing slash makes this a path-component match, not a bare substring — it must not catch
    # sibling paths like updates_bak/ or an updates_summary.sql file.)
    if f"{PATCH_DIRECTORY}/" in file_path and PATCH_PATTERN.match(path.name) is not None:
        return path.exists()

    # Flyway forward migration under common/on/bc. Reject if the version collides with an existing
    # migration in the co-applied set (Flyway would otherwise fail at migrate time).
    if migration_location_for(file_path) is not None and MIGRATION_PATTERN.match(path.name) is not None:
        return is_direct_child_of_migration_location(file_path) and not has_duplicate_version(file_path)

    return False


def is_creating_sql_in_protected_dir(tool_name: str, file_path: str) -> bool:
    """Check if attempting to create a new ad-hoc SQL file under database/mysql/."""
    if tool_name not in ("Write", "MultiEdit"):
        return False

    if not file_path.endswith('.sql'):
        return False

    if Path(file_path).exists():
        return False

    if "database/mysql/" in file_path and not is_valid_patch_file(file_path):
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

        # Only process Edit, Write, and MultiEdit tools
        if tool_name not in ("Edit", "Write", "MultiEdit"):
            sys.exit(0)

        # Get file path. Lexically normalize it FIRST (collapse ./ and ../ segments) so the
        # substring/anchor containment checks below cannot be evaded by a traversal path — e.g.
        # database/mysql/migration/common/../../../outside/V1.0.99__bad.sql resolves outside the
        # migration tree and must not be classified as a valid migration. posixpath.normpath (not
        # os.path.normpath) so forward slashes are PRESERVED on Windows — os.path would rewrite
        # them to backslashes and silently bypass every POSIX-string check in this hook. It is
        # repo-root-agnostic and lexical, so it works for a not-yet-written file without touching
        # the filesystem or resolving symlinks.
        file_path = get_file_path_from_input(tool_input)
        if file_path:
            file_path = posixpath.normpath(file_path)

        # Only check SQL files
        if not file_path.endswith('.sql'):
            sys.exit(0)

        # Allow operations on valid patch files
        if is_valid_patch_file(file_path):
            sys.exit(0)

        # Block anything else under the FROZEN legacy updates/ dir: new files there would be
        # schema changes outside Flyway history (is_valid_patch_file only allows edits to
        # EXISTING well-named patches, which a few tests/demo seeds still read). Trailing slash
        # keeps this a directory-boundary match (updates_bak/ etc. must not be caught).
        if f"{PATCH_DIRECTORY}/" in file_path:
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print("BLOCKED: database/mysql/updates/ is FROZEN — no new files", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("New schema changes must be Flyway forward migrations:", file=sys.stderr)
            print("  database/mysql/migration/<common|on|bc>/V1.0.N__short_description.sql", file=sys.stderr)
            print("  (sequential; use the next free version number; make it idempotent)", file=sys.stderr)
            sys.exit(2)

        # Block modification of protected baseline files
        if is_protected_sql_file(file_path):
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print("BLOCKED: Cannot hand-edit the Flyway V1 baseline", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("CARLOS migration standards require:", file=sys.stderr)
            print("  ✗ Do NOT edit the five genesis baseline files (V1 + province V1.0.1/V1.0.2) — that is the genesis schema", file=sys.stderr)
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

        # Well-shaped V1.0.N name but the version already exists in the co-applied set — Flyway
        # would fail at migrate time. Give a precise message (distinct from the bad-name case).
        if migration_location_for(file_path) is not None and \
                MIGRATION_PATTERN.match(Path(file_path).name) is not None and \
                is_direct_child_of_migration_location(file_path) and \
                has_duplicate_version(file_path):
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            print("BLOCKED: duplicate Flyway migration version", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("Another migration in the co-applied set (common + this province) already uses", file=sys.stderr)
            print("this version. Flyway would reject two migrations with the same version.", file=sys.stderr)
            print("Use the next free version number (V1.0.N) instead.", file=sys.stderr)
            sys.exit(2)

        # Block badly-versioned SQL under the migration location dirs: anything that reached
        # here is neither a protected baseline nor a valid V1.0.N forward migration (date-style
        # V2026.07.08 or new-major V2.x names land here too). Nested descendants are also
        # rejected: migrations must be direct children of common/on/bc so every verifier sees the
        # same files Flyway is expected to apply.
        if migration_location_for(file_path) is not None:
            print("\n=== SQL Migration Pattern Enforcer ===", file=sys.stderr)
            if not is_direct_child_of_migration_location(file_path):
                print("BLOCKED: nested Flyway migrations are not allowed", file=sys.stderr)
            else:
                print("BLOCKED: invalid Flyway migration name for this location", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            print("Forward migrations must be direct children named V1.0.N__short_description.sql", file=sys.stderr)
            print("  (sequential; use the next free version number; make it idempotent)", file=sys.stderr)
            print("  Example: database/mysql/migration/common/V1.0.7__add_provider_type_column.sql", file=sys.stderr)
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
            print("  Example: V1.0.5__add_provider_type_column.sql", file=sys.stderr)
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
