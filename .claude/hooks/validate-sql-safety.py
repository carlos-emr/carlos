#!/usr/bin/env python3
"""
Parameterized SQL Query Enforcer Hook for Claude Code

This hook validates that Java files use parameterized queries
to prevent SQL injection vulnerabilities.

Improvements over the original version:
- Adds safe-pattern allowlist to reduce false positives on legitimate
  dynamic JPQL/HQL query building (e.g. TicklerDaoImpl, BillingDaoImpl)
- Recognizes parameter placeholder concatenation (?N, :paramName) as safe
- Recognizes query-builder variable concatenation as safe (with param evidence)
- Recognizes entity/class name insertion (getSimpleName()) as safe
- Skips matches inside Java comments
- Strips trailing // comments before analysis to prevent comment-based bypasses
- Detects quote-sandwich SQL injection (value embedded between SQL quotes)
- is_query_builder_variable requires combined parameter placeholder evidence
- No file-wide bypass: setParameter usage elsewhere never whitelists other matches

Exit codes:
- 0: Safe patterns detected or non-applicable file
- 2: Unsafe patterns detected (blocks the operation with feedback)
"""

import json
import re
import sys


# ---------------------------------------------------------------------------
# Safe-pattern allowlist helpers
# ---------------------------------------------------------------------------

# Variable names that represent query fragments (not user input)
QUERY_BUILDER_VARS = re.compile(
    r'^(?:'
    r'query|hql|sql|jpql|buf|sb|sqlCommand|queryString|'
    r'whereClause|selectQuery|orderClause|groupClause|'
    r'providerQuery|startDateQuery|endDateQuery|demoQuery|'
    r'serviceCodeValues|conditions|'
    r'\w+Query|\w+Clause|\w+Sql|\w+Hql'
    r')$',
    re.IGNORECASE
)

# Patterns that indicate the concatenation is building a parameter placeholder
PARAM_PLACEHOLDER_PATTERNS = [
    # "?" + paramIndex / counter / i / idx  (positional parameter building)
    re.compile(r'\?\s*["\']\s*\+\s*(?:paramIndex|counter|index|idx|param\w*|i\b)', re.IGNORECASE),
    # .append("?").append(counter)  or  .append("?").append(paramIndex)
    re.compile(r'\.append\s*\(\s*["\']\?\s*["\']\s*\)\s*\.append\s*\(', re.IGNORECASE),
    # ?" + paramIndex  or  ?" + (paramIndex++)  or  ?" + (i + 1)
    re.compile(r'\?\s*["\']\s*\+\s*\(?(?:paramIndex|counter|index|idx|i)\b', re.IGNORECASE),
    # "= :").append(param)  (named parameter building)
    re.compile(r'[:=]\s*:\s*["\']\s*\)\s*\.append\s*\(', re.IGNORECASE),
    # ":paramName" or "= :paramName" inside a string literal (safe named param)
    re.compile(r'["\']\s*(?:=\s*)?:\w+\s*["\']\s*\+', re.IGNORECASE),
    # + ":paramName"  (concatenating a named param reference)
    re.compile(r'\+\s*["\']\s*(?:and|or|where)?\s+\w+\s*=\s*:\w+', re.IGNORECASE),
    # .append(" AND field = :").append(paramName)
    re.compile(r'=\s*:\s*["\']\s*\)\s*\.append\s*\(\s*\w+\s*\)', re.IGNORECASE),
]

# Patterns indicating entity/class name insertion (safe metadata)
CLASS_NAME_PATTERNS = [
    re.compile(r'getSimpleName\s*\(\s*\)'),
    re.compile(r'getName\s*\(\s*\)'),
    re.compile(r'\.class\s*\.'),
    re.compile(r'modelClass'),
    re.compile(r'\w+\.class\.getSimpleName'),
]


def strip_line_comment(line: str) -> str:
    """Strip trailing // single-line comment from a Java line.

    Uses a simple heuristic: find // where the number of double-quote
    characters before it is even (meaning we are not inside a string literal).
    """
    idx = line.find('//')
    if idx == -1:
        return line
    # Count double-quote characters before // to determine if inside a string
    before = line[:idx]
    if before.count('"') % 2 == 0:  # Even number of quotes = not inside string
        return line[:idx]
    return line


def get_line_containing(content: str, position: int) -> str:
    """Extract the full line containing the given character position."""
    line_start = content.rfind('\n', 0, position) + 1
    line_end = content.find('\n', position)
    if line_end == -1:
        line_end = len(content)
    return content[line_start:line_end]


def is_comment_line(line: str) -> bool:
    """Check if a line is a Java comment (single-line, block, or Javadoc)."""
    stripped = line.strip()
    return (
        stripped.startswith('//')
        or stripped.startswith('*')
        or stripped.startswith('/*')
    )


def is_in_string_literal_context(line: str) -> bool:
    """Check if the line contains parameter placeholders inside string literals.

    Strips trailing // comments before analysis to prevent comment-based bypasses
    where an attacker could add // :id on the same line as an unsafe concatenation.
    Named parameters (:word) are only matched when inside double-quoted string literals,
    not when they appear in comments, variable names, or other non-string contexts.
    """
    # Strip trailing // comments first (prevents comment-based bypasses)
    stripped = strip_line_comment(line)

    # Check for positional parameter in string concatenation: "?" + ...
    if re.search(r'["\']\s*\?\s*["\']\s*\+', stripped):
        return True

    # Check for positional parameter reference: ?1, ?2, etc.
    if re.search(r'\?\d+', stripped):
        return True

    # Check for named parameters INSIDE string literals only (between double-quotes)
    # This prevents matching :word in comments or unquoted variable names
    if re.search(r'"[^"]*:\w+[^"]*"', stripped):
        return True

    return False


def has_param_placeholder_in_context(match_text: str, line: str) -> bool:
    """Check if the match or its line contains parameter placeholder patterns."""
    combined = match_text + " " + line
    for pattern in PARAM_PLACEHOLDER_PATTERNS:
        if pattern.search(combined):
            return True
    return False


def has_class_name_insertion(match_text: str, line: str) -> bool:
    """Check if the match involves entity/class name insertion (safe)."""
    combined = match_text + " " + line
    for pattern in CLASS_NAME_PATTERNS:
        if pattern.search(combined):
            return True
    return False


def is_query_builder_variable(match_text: str) -> bool:
    """Check if the concatenated variable is a known query-builder variable name."""
    var_matches = re.findall(r'(\w+)\s*\+\s*["\']|["\']\s*\+\s*(\w+)', match_text)
    for groups in var_matches:
        for var_name in groups:
            if var_name and QUERY_BUILDER_VARS.match(var_name):
                return True
    return False


def has_parameterized_usage(content: str) -> bool:
    """Check if the content shows parameterized query usage patterns."""
    indicators = [
        r'\.setParameter\s*\(',
        r'paramList\.add\s*\(',
        r'params\.put\s*\(',
        r'parameters\.put\s*\(',
        r'query\.setParameter',
        r'\?\d+',           # ?1, ?2 positional params
        r':\w+["\'\s,)]',   # :paramName in query strings
    ]
    for indicator in indicators:
        if re.search(indicator, content):
            return True
    return False


def is_safe_pattern(match_text: str, line: str, content: str) -> bool:
    """
    Determine if a flagged match is actually a safe pattern.

    Returns True if the match represents safe dynamic query building
    (not actual SQL injection), False if it remains suspicious.

    Security notes:
    - is_query_builder_variable is never used alone: requires combined parameter
      placeholder evidence to prevent variable-name-based bypasses.
    - is_in_string_literal_context strips // comments before checking to prevent
      a comment like // :id from whitelisting an unsafe concatenation on the same line.
    - File-wide parameterized usage signals (setParameter elsewhere in the file) only
      contribute when the current line itself has parameter placeholder evidence.
    """
    # 1. Check if in a comment
    if is_comment_line(line):
        return True

    # 2. Check for parameter placeholder concatenation
    if has_param_placeholder_in_context(match_text, line):
        return True

    # 3. Check for entity/class name insertion
    if has_class_name_insertion(match_text, line):
        return True

    # 4. Check if concatenated variable is a query-builder variable AND the line
    # also has parameter placeholder evidence (prevents variable-name-only bypasses).
    # A variable named 'sql' or 'providerQuery' is only considered safe when the
    # same line also contains a named or positional parameter placeholder.
    if is_query_builder_variable(match_text) and is_in_string_literal_context(line):
        return True

    # 5. Check if the line has parameter placeholders inside string literals AND
    # the overall content uses parameterized queries.
    # Note: is_in_string_literal_context strips comments first, so a trailing
    # // :id comment cannot bypass this check.
    if is_in_string_literal_context(line) and has_parameterized_usage(content):
        return True

    # 6. Check for string literal + string literal concatenation (no variables)
    # "SELECT ..." + " WHERE ..." is just splitting a long string, which is safe.
    if re.search(r'["\']\s*\+\s*["\']', match_text):
        # Pure string-to-string concat with no variable in between
        var_in_between = re.search(r'["\']\s*\+\s*\w+\s*\+\s*["\']', match_text)
        if not var_in_between:
            return True

    return False


# ---------------------------------------------------------------------------
# Core detection logic
# ---------------------------------------------------------------------------

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

    Detects unsafe patterns while allowing safe dynamic query building.

    Unsafe patterns:
    - "SELECT * FROM users WHERE id = " + userId
    - "SELECT * FROM " + tableName + " WHERE ..."
    - String.format("SELECT * FROM users WHERE id = %s", id)
    - "INSERT INTO table VALUES ('" + value + "')"
    - executeQuery("SELECT ... " + variable)
    - createQuery("SELECT ... " + variable)
    - "... = '" + variable + "'" (quote-sandwich injection)

    Safe patterns (allowlisted):
    - query.setParameter("id", userId)
    - PreparedStatement with ? placeholders
    - query = query + " and t.field = ?" + paramIndex++
    - .append("field = :").append(paramName)
    - "FROM " + modelClass.getSimpleName() + " WHERE ..."
    - Named parameters (:paramName)
    - Positional parameters (?1, ?2)
    """
    issues = []

    # SQL keywords to look for
    sql_keywords = r'(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|INTO|VALUES|SET|JOIN|ORDER\s+BY|GROUP\s+BY)'

    # Pattern 1: String concatenation with SQL keywords
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
    table_concat = r'["\']SELECT\s+\*?\s+FROM\s*["\']\s*\+\s*\w+'
    where_concat = r'["\']WHERE\s+\w+\s*=\s*["\']\s*\+\s*\w+'

    # Pattern 7: Quote-sandwich injection (value embedded between SQL single-quotes)
    # Catches: "... = '" + variable + "'" (classic SQL injection via quote embedding)
    # In Java source, this appears as a string ending with ' (single-quote before
    # the closing double-quote), then + variable +, then a string starting with '
    # Example: "WHERE name = '" + patientName + "' AND ..."
    quote_sandwich = r"""'"\s*\+\s*\w+\s*\+\s*"'"""

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
        (quote_sandwich, "Value embedded between SQL quotes (injection)"),
    ]

    found_patterns = set()  # Avoid duplicate messages

    for pattern, description in patterns_to_check:
        matches = re.finditer(pattern, content, re.IGNORECASE)
        for match in matches:
            match_text = match.group(0)

            # Get the full line containing this match
            line = get_line_containing(content, match.start())

            # Skip if match is a safe pattern
            if is_safe_pattern(match_text, line, content):
                continue

            # Still flagged: report as issue
            start = max(0, match.start() - 20)
            end = min(len(content), match.end() + 20)
            context = content[start:end].replace('\n', ' ').strip()

            issue_key = f"{description}:{match.start()}"
            if issue_key not in found_patterns:
                found_patterns.add(issue_key)
                issues.append(
                    f"CRITICAL: {description}\n"
                    f"  Found: ...{context}...\n"
                    f"  This is vulnerable to SQL injection."
                )

    # Additional check: Look for dangerous patterns in query construction
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
            match_text = match.group(0)
            line = get_line_containing(content, match.start())

            if is_safe_pattern(match_text, line, content):
                continue

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
