#!/usr/bin/env python3
"""
Parameterized SQL Query Enforcer Hook for Claude Code

This hook validates that Java files use parameterized queries
to prevent SQL injection vulnerabilities.

Exit codes:
- 0: Safe patterns detected or non-applicable file
- 2: Unsafe patterns detected (blocks the operation with feedback)
"""

import json
import re
import sys


def get_file_content_from_input(tool_input: dict) -> tuple[str, str]:
    """Extract file path and content from tool input."""
    file_path = tool_input.get("file_path", "")

    # For Write tool, content is in 'content' field
    # For Edit tool, new content is in 'new_string' field
    content = tool_input.get("content", "") or tool_input.get("new_string", "")

    return file_path, content


def check_sql_injection_patterns(content: str) -> list[str]:
    """
    Check Java content for SQL injection vulnerabilities.

    Unsafe patterns:
    - "SELECT * FROM users WHERE id = " + userId
    - "SELECT * FROM " + tableName + " WHERE ..."
    - String.format("SELECT * FROM users WHERE id = %s", id)
    - "INSERT INTO table VALUES ('" + value + "')"
    - executeQuery("SELECT ... " + variable)
    - createQuery("SELECT ... " + variable)

    Safe patterns:
    - query.setParameter("id", userId)
    - PreparedStatement with ? placeholders
    - createQuery("SELECT u FROM User u WHERE u.id = :id").setParameter("id", id)
    - Named parameters (:paramName)
    - Positional parameters (?)
    """
    issues = []

    # SQL keywords to look for
    sql_keywords = r'(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|INTO|VALUES|SET|JOIN|ORDER\s+BY|GROUP\s+BY)'

    # Pattern 1: String concatenation with SQL keywords
    # Matches: "SELECT ... " + variable or variable + " SELECT ..."
    concat_pattern = rf'["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+\s*\w+'
    concat_pattern2 = rf'\w+\s*\+\s*["\'][^"\']*{sql_keywords}'
    concat_pattern3 = rf'["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+\s*["\'][^"\']*["\']\s*\+\s*\w+'

    # Pattern 2: String.format with SQL
    format_pattern = rf'String\s*\.\s*format\s*\(\s*["\'][^"\']*{sql_keywords}'

    # Pattern 3: StringBuilder/StringBuffer append with SQL
    builder_pattern = rf'(?:StringBuilder|StringBuffer)\s*\(\s*["\'][^"\']*{sql_keywords}'
    append_pattern = rf'\.append\s*\(\s*["\'][^"\']*{sql_keywords}'

    # Pattern 4: executeQuery/executeUpdate with concatenation
    execute_concat = rf'(?:executeQuery|executeUpdate|execute)\s*\(\s*["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+'
    execute_concat2 = r'(?:executeQuery|executeUpdate|execute)\s*\(\s*\w+\s*\+\s*["\']'

    # Pattern 5: createQuery/createNativeQuery with concatenation
    create_query_concat = rf'(?:createQuery|createNativeQuery|createSQLQuery)\s*\(\s*["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+'
    create_query_concat2 = r'(?:createQuery|createNativeQuery|createSQLQuery)\s*\(\s*\w+\s*\+\s*["\']'

    # Pattern 6: Direct variable in SQL string construction
    # "SELECT * FROM " + tableName
    table_concat = r'["\']SELECT\s+\*?\s+FROM\s*["\']\s*\+\s*\w+'
    where_concat = r'["\']WHERE\s+\w+\s*=\s*["\']\s*\+\s*\w+'

    patterns_to_check = [
        (concat_pattern, "String concatenation in SQL query"),
        (concat_pattern2, "String concatenation in SQL query"),
        (concat_pattern3, "Multiple string concatenation in SQL query"),
        (format_pattern, "String.format() with SQL query"),
        (builder_pattern, "StringBuilder with SQL query"),
        (append_pattern, "StringBuilder.append() with SQL fragment"),
        (execute_concat, "executeQuery() with string concatenation"),
        (execute_concat2, "executeQuery() with string concatenation"),
        (create_query_concat, "createQuery() with string concatenation"),
        (create_query_concat2, "createQuery() with string concatenation"),
        (table_concat, "Table name concatenation in SQL"),
        (where_concat, "WHERE clause concatenation in SQL"),
    ]

    found_patterns = set()  # Avoid duplicate messages

    for pattern, description in patterns_to_check:
        matches = re.finditer(pattern, content, re.IGNORECASE)
        for match in matches:
            # Get some context around the match
            start = max(0, match.start() - 20)
            end = min(len(content), match.end() + 20)
            context = content[start:end].replace('\n', ' ').strip()

            # Create a unique key to avoid duplicates
            issue_key = f"{description}:{match.start()}"
            if issue_key not in found_patterns:
                found_patterns.add(issue_key)
                issues.append(
                    f"CRITICAL: {description}\n"
                    f"  Found: ...{context}...\n"
                    f"  This is vulnerable to SQL injection."
                )

    # Additional check: Look for dangerous patterns in query construction
    # Check for queries that don't use parameterized approach
    raw_query_patterns = [
        # "SELECT ... WHERE id = '" + id + "'"
        (rf'["\'][^"\']*{sql_keywords}[^"\']*=\s*(["\'])\s*\+\s*\w+\s*\+\s*\1',
         "String concatenation with quotes in SQL"),
        # query = "SELECT ... " + variable;
        (rf'\w+\s*=\s*["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+',
         "SQL query built with string concatenation"),
    ]

    for pattern, description in raw_query_patterns:
        matches = re.finditer(pattern, content, re.IGNORECASE)
        for match in matches:
            start = max(0, match.start() - 10)
            end = min(len(content), match.end() + 10)
            context = content[start:end].replace('\n', ' ').strip()

            issue_key = f"{description}:{match.start()}"
            if issue_key not in found_patterns:
                found_patterns.add(issue_key)
                issues.append(
                    f"CRITICAL: {description}\n"
                    f"  Found: ...{context}...\n"
                    f"  This is vulnerable to SQL injection."
                )

    return issues


def validate_content(file_path: str, content: str) -> tuple[bool, list[str]]:
    """
    Validate file content for SQL injection vulnerabilities.

    Returns:
        (is_safe, issues): Tuple of safety status and list of issues found
    """
    if not file_path or not content:
        return True, []

    issues = []

    # Only check Java files
    if file_path.endswith('.java'):
        issues.extend(check_sql_injection_patterns(content))

    # All SQL injection issues are critical
    has_critical = len(issues) > 0

    return not has_critical, issues


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

        # Get file path and content
        file_path, content = get_file_content_from_input(tool_input)

        # Only check Java files
        if not file_path.endswith('.java'):
            sys.exit(0)

        # Validate content
        is_safe, issues = validate_content(file_path, content)

        if issues:
            # Output feedback to stderr
            print("\n=== Parameterized SQL Query Enforcer ===", file=sys.stderr)
            print(f"File: {file_path}\n", file=sys.stderr)
            for issue in issues:
                print(f"{issue}\n", file=sys.stderr)

            if not is_safe:
                print("BLOCKED: SQL injection vulnerability detected.", file=sys.stderr)
                print("Please use parameterized queries.\n", file=sys.stderr)
                print("Safe alternatives:", file=sys.stderr)
                print("  JPA/Hibernate:", file=sys.stderr)
                print('    createQuery("SELECT u FROM User u WHERE u.id = :id")', file=sys.stderr)
                print('      .setParameter("id", userId)', file=sys.stderr)
                print("\n  PreparedStatement:", file=sys.stderr)
                print('    PreparedStatement ps = conn.prepareStatement(', file=sys.stderr)
                print('        "SELECT * FROM users WHERE id = ?");', file=sys.stderr)
                print('    ps.setInt(1, userId);', file=sys.stderr)
                print("\n  Hibernate Criteria:", file=sys.stderr)
                print('    session.createCriteria(User.class)', file=sys.stderr)
                print('      .add(Restrictions.eq("id", userId));', file=sys.stderr)
                sys.exit(2)

        sys.exit(0)

    except json.JSONDecodeError as e:
        print(f"Error parsing JSON input: {e}", file=sys.stderr)
        sys.exit(0)  # Don't block on parse errors
    except Exception as e:
        print(f"Error in SQL safety hook: {e}", file=sys.stderr)
        sys.exit(0)  # Don't block on unexpected errors


if __name__ == "__main__":
    main()
