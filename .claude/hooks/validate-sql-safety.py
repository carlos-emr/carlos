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
- Masks all Java comments (// and /* */) before scanning, so SQL in comments
  is ignored AND comment text can never serve as allowlisting evidence;
  text blocks (\"\"\"...\"\"\") are skipped so their content is never mistaken
  for a comment start
- Detects raw operands passed through calls with arguments (e.g.
  String.valueOf(userId)) and parenthesized operands (e.g. + (userId))
- Safety evidence (placeholders, class metadata) is scoped to the Java
  statement containing the match — evidence in another statement, on the
  same line, or elsewhere in the file never allowlists a raw-value concat
- Quote-sandwich SQL injection (value embedded between SQL quotes) is never
  allowlistable
- Any raw concatenated operand (not a builder var, counter, or class
  metadata) vetoes all allowlisting for its statement
- No file-wide bypass: setParameter usage elsewhere never whitelists other matches
- For Edit/MultiEdit, reconstructs the full post-edit file before scanning

Exit codes:
- 0: Safe patterns detected or non-applicable file
- 2: Unsafe patterns detected (blocks the operation with feedback)
"""

import json
import re
import sys
from pathlib import Path


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

# Loop/parameter counter variable names used when building "?N" placeholders
SAFE_COUNTER_VARS = re.compile(
    r'^(?:[ijknx]|idx|index|pos|position|paramIndex|paramCount|counter|count)$',
    re.IGNORECASE
)

# Concatenation operands: an identifier chain whose segments may be method
# calls *with or without* arguments (so `String.valueOf(userId)` and
# `Objects.toString(userId)` are captured as raw operands), directly before or
# after a '+' that is not '++' or '+='.
_OPERAND_CHAIN = (
    r'[A-Za-z_$][\w$]*(?:\s*\([^()]*\))?'
    r'(?:\s*\.\s*[A-Za-z_$][\w$]*(?:\s*\([^()]*\))?)*'
)
_OPERAND_BEFORE = re.compile(r'(' + _OPERAND_CHAIN + r')\s*\+(?![+=])')
_OPERAND_AFTER = re.compile(r'(?<!\+)\+(?![+=])\s*(' + _OPERAND_CHAIN + r')')

# Parenthesized operands next to a '+', e.g. `+ (paramIndex++)` or `+ (userId)`.
_PAREN_BEFORE = re.compile(r'\(([^()]*)\)\s*\+(?![+=])')
_PAREN_AFTER = re.compile(r'(?<!\+)\+(?![+=])\s*\(([^()]*)\)')

# Java keywords / cast tokens that carry no user data inside a parenthesized
# operand and therefore must not be treated as concatenated values.
PAREN_IGNORED_TOKENS = frozenset({
    'int', 'long', 'short', 'byte', 'char', 'float', 'double', 'boolean',
    'new', 'true', 'false', 'null', 'instanceof',
})


def strip_line_comment(line: str) -> str:
    """Strip trailing // single-line comment from a Java line."""
    idx = line.find('//')
    if idx == -1:
        return line
    # Count double-quote characters before // to determine if inside a string
    before = line[:idx]
    if before.count('"') % 2 == 0:  # Even number of quotes = not inside string
        return line[:idx]
    return line



def get_statement_containing(content: str, start: int, end: int) -> str:
    """Extract the full Java statement containing the given match span.

    Expands backward to the previous statement boundary (';', '{' or '}')
    and forward to the next ';'. This is the correct scope for safety
    evidence: placeholders or class metadata in a *different* statement on
    the same line must never allowlist this one, while evidence in the same
    statement that a truncated regex match missed still counts.
    """
    begin = max(
        content.rfind(';', 0, start),
        content.rfind('{', 0, start),
        content.rfind('}', 0, start),
    ) + 1
    term = content.find(';', end)
    if term == -1:
        term = len(content)
    return content[begin:term + 1]


def _mask_string_literals(text: str) -> str:
    """Replace double-quoted string literal contents with empty literals."""
    return re.sub(r'"(?:[^"\\]|\\.)*"', '""', text)


def find_concat_operands(statement: str) -> list[str]:
    """Return the non-literal operand expressions concatenated with '+'.

    String literals are masked first so identifiers inside them are ignored.
    Numeric literals are never captured (the operand regexes require an
    identifier start character). Method calls with arguments are captured as
    whole operands, and parenthesized operands contribute their identifier
    tokens, so raw values such as `String.valueOf(userId)` or `(userId)` are
    reliably detected.
    """
    masked = _mask_string_literals(statement)
    operands = []
    for pattern in (_OPERAND_BEFORE, _OPERAND_AFTER):
        for m in pattern.finditer(masked):
            operands.append(m.group(1).strip())
    for pattern in (_PAREN_BEFORE, _PAREN_AFTER):
        for m in pattern.finditer(masked):
            for token in re.findall(r'[A-Za-z_$][\w$]*', m.group(1)):
                if token not in PAREN_IGNORED_TOKENS:
                    operands.append(token)
    return operands


def is_safe_operand(operand: str) -> bool:
    """True if a concatenated operand is structurally safe (not user data).

    Safe operands are: query-builder variables (query fragments), loop /
    parameter counters used to build "?N" placeholders, and entity/class
    metadata expressions (getSimpleName() etc.). An operand that passes a
    non-counter value as a call argument (e.g. `String.valueOf(userId)`, or
    `query.append(userId)`) is never safe — the argument is the raw value.
    """
    for args in re.findall(r'\(([^()]*)\)', operand):
        for token in re.findall(r'[A-Za-z_$][\w$]*', args):
            if token in PAREN_IGNORED_TOKENS:
                continue
            if not SAFE_COUNTER_VARS.match(token):
                return False
    base = re.split(r'[.(]', operand, maxsplit=1)[0].strip()
    if QUERY_BUILDER_VARS.match(base):
        return True
    if SAFE_COUNTER_VARS.match(base):
        return True
    for pattern in CLASS_NAME_PATTERNS:
        if pattern.search(operand):
            return True
    return False


def has_raw_concat_operand(statement: str) -> bool:
    """True if the statement concatenates any operand that is not safe.

    A single raw operand (e.g. `+ userId`) vetoes every allowlist check:
    placeholders or metadata elsewhere in the statement must not excuse a
    raw value being spliced into SQL.
    """
    return any(not is_safe_operand(op) for op in find_concat_operands(statement))


def mask_java_comments(content: str) -> str:
    """Replace Java comment spans with spaces, preserving offsets and newlines.

    Masks // line comments and /* ... */ block comments while respecting string,
    text-block (\"\"\"...\"\"\") and char literals, so that (a) SQL inside comments is
    never flagged and (b) comment text (e.g. a trailing "// :id") can never be
    used as safety evidence to allowlist executable code on the same line.
    Unlike a line-level comment check, this correctly handles executable code
    that follows a closed block comment on the same line.
    """
    out = list(content)
    i = 0
    n = len(content)
    in_string = False
    in_char = False
    while i < n:
        c = content[i]
        if in_string:
            if c == '\\':
                i += 2
                continue
            if c == '"':
                in_string = False
            i += 1
            continue
        if in_char:
            if c == '\\':
                i += 2
                continue
            if c == "'":
                in_char = False
            i += 1
            continue
        if content.startswith('"""', i):
            # Java text block: content is literal, so '//' and '/*' inside it
            # must not be treated as comment starts.
            end = content.find('"""', i + 3)
            i = n if end == -1 else end + 3
            continue
        if c == '"':
            in_string = True
            i += 1
            continue
        if c == "'":
            in_char = True
            i += 1
            continue
        if c == '/' and i + 1 < n:
            nxt = content[i + 1]
            if nxt == '/':
                j = content.find('\n', i)
                if j == -1:
                    j = n
                for k in range(i, j):
                    out[k] = ' '
                i = j
                continue
            if nxt == '*':
                j = content.find('*/', i + 2)
                end = n if j == -1 else j + 2
                for k in range(i, end):
                    if out[k] != '\n':
                        out[k] = ' '
                i = end
                continue
        i += 1
    return ''.join(out)


def is_in_string_literal_context(line: str) -> bool:
    # Strip trailing // comments first (prevents comment-based bypasses)
    """Check if the line contains parameter placeholders inside string literals."""
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


def has_param_placeholder_in_context(statement: str) -> bool:
    """Check for parameter placeholder patterns within the statement.

    Only the statement containing the match is inspected — placeholder
    evidence in a different statement (even on the same line) must not
    allowlist an unrelated raw-value concat.
    """
    for pattern in PARAM_PLACEHOLDER_PATTERNS:
        if pattern.search(statement):
            return True
    return False


def has_class_name_insertion(statement: str) -> bool:
    """Check if the statement involves entity/class name insertion.

    Scoped to the statement so class metadata elsewhere in the file or in
    another statement cannot suppress an unrelated user-value concatenation.
    """
    for pattern in CLASS_NAME_PATTERNS:
        if pattern.search(statement):
            return True
    return False


def is_stringbuilder_entity_name_query(line: str, content: str) -> bool:
    """Allow StringBuilder queries whose dynamic FROM target is model metadata."""
    start_match = re.search(
        r'(\w+)\.append\s*\(\s*["\']\s*select\s+\w+\s+from\s*["\']\s*\)',
        line,
        re.IGNORECASE,
    )
    if not start_match:
        return False

    builder = re.escape(start_match.group(1))
    has_entity_metadata_append = re.search(
        rf'{builder}\.append\s*\(\s*(?:modelClass\.getSimpleName\(\)|\w+\.class\.getSimpleName\(\))\s*\)',  # nosemgrep: skills.code-injection.skill-ldap-injection.skill-ldap-injection -- SQL safety hook regex, not an LDAP filter; builder is escaped with re.escape().
        content,
    )
    has_query_creation = re.search(
        rf'(?:createQuery|createNativeQuery|createSQLQuery)\s*\(\s*{builder}\.toString\(\)\s*\)',  # nosemgrep: skills.code-injection.skill-ldap-injection.skill-ldap-injection -- SQL safety hook regex, not an LDAP filter; builder is escaped with re.escape().
        content,
        re.IGNORECASE,
    )
    return bool(has_entity_metadata_append and has_query_creation and has_parameterized_usage(content))



def has_parameterized_usage(content: str) -> bool:
    """Check for parameterized query usage patterns in the content."""
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


def is_safe_pattern(match_text: str, statement: str, content: str) -> bool:
    """Determine if a flagged match is actually a safe pattern.

    Evidence is evaluated against the Java statement containing the match
    (not the whole line or file). Any raw concatenated operand in the
    statement — a variable that is not a query-builder fragment, a
    placeholder counter, or class metadata — vetoes every allowlist check,
    so placeholders or metadata elsewhere in the statement can never excuse
    splicing a raw value into SQL.

    Args:
        match_text (str): The matched text (kept for reporting context).
        statement (str): The full statement containing the match.
        content (str): The full content, used for content-level evidence.

    Returns:
        bool: True if the match is considered safe, False otherwise.
    """
    # Note: comments are masked out of the content before scanning
    # (mask_java_comments), so matches can never originate from comment text
    # and comment text can never serve as safety evidence.

    # Veto: a raw operand concatenated anywhere in this statement makes it
    # unsafe regardless of any other evidence (e.g. `"... a = :a AND b = "
    # + userId` is still injectable through userId).
    if has_raw_concat_operand(statement):
        return False

    # 1. Parameter placeholder building within the statement
    if has_param_placeholder_in_context(statement):
        return True

    # 2. Entity/class name insertion within the statement
    if has_class_name_insertion(statement):
        return True

    # 3. StringBuilder query construction where the only dynamic FROM
    # target is model metadata and parameterized usage is present elsewhere.
    if is_stringbuilder_entity_name_query(statement, content):
        return True

    # 4. Statement has parameter placeholders inside string literals AND the
    # overall content uses parameterized queries. The raw-operand veto above
    # guarantees no unsafe variable rides along with the placeholders.
    if is_in_string_literal_context(statement) and has_parameterized_usage(content):
        return True

    # 5. Pure string literal + string literal concatenation (no variable
    # operands at all): splitting a long string across lines is safe.
    if re.search(r'["\']\s*\+\s*["\']', statement) and not find_concat_operands(statement):
        return True

    return False


# ---------------------------------------------------------------------------
# Core detection logic
# ---------------------------------------------------------------------------

def _read_existing_file(file_path: str) -> str:
    try:
        return Path(file_path).read_text(errors="replace")
    except OSError:
        return ""


def _apply_edit(content: str, old_string: str, new_string: str, replace_all: bool = False) -> str:
    if old_string == "":
        return content + new_string
    if old_string not in content:
        # Do not let a stale/mismatched edit become a hook bypass: scan the existing file plus the
        # proposed replacement text so newly introduced SQL still gets inspected.
        return content + "\n" + new_string
    return content.replace(old_string, new_string) if replace_all else content.replace(old_string, new_string, 1)


def get_file_content_from_input(tool_input: dict, tool_name: str) -> tuple[str, str]:
    """Extract file path and reconstruct the full post-edit content when possible."""
    file_path = tool_input.get("file_path", "")

    if tool_name == "Write":
        return file_path, tool_input.get("content", "")

    content = _read_existing_file(file_path)
    if tool_name == "Edit":
        content = _apply_edit(
            content,
            tool_input.get("old_string", ""),
            tool_input.get("new_string", ""),
            bool(tool_input.get("replace_all", False)),
        )
    elif tool_name == "MultiEdit":
        for edit in tool_input.get("edits", []):
            content = _apply_edit(
                content,
                edit.get("old_string", ""),
                edit.get("new_string", ""),
                bool(edit.get("replace_all", False)),
            )

    return file_path, content

def check_sql_injection_patterns(content: str) -> list[str]:
    """def check_sql_injection_patterns(content: str) -> list[str]:
    
    Check Java content for SQL injection vulnerabilities.  This function analyzes
    the provided Java content for unsafe SQL patterns  that may lead to SQL
    injection vulnerabilities. It identifies various  patterns such as string
    concatenation, usage of String.format, and  direct variable inclusion in SQL
    queries. The function also checks for  known safe patterns to avoid false
    positives, ensuring that only  potentially dangerous constructs are flagged as
    issues.
    
    Args:
        content (str): The Java content to be analyzed for SQL injection patterns.
    """
    issues = []

    # Mask comments up front so (a) SQL inside comments is never flagged and
    # (b) comment text can never provide allowlisting evidence. Offsets are
    # preserved, so reported positions remain valid.
    content = mask_java_comments(content)

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
        (concat_pattern, "String concatenation in SQL query", False),
        (concat_pattern2, "String concatenation in SQL query", False),
        (concat_pattern3, "Multiple string concatenation in SQL query", False),
        (format_pattern, "String.format() with SQL query", False),
        (builder_pattern, "StringBuilder with SQL query", False),
        (append_pattern, "StringBuilder.append() with SQL fragment", False),
        (execute_concat, "executeQuery() with string concatenation", False),
        (execute_concat2, "executeQuery() with string concatenation", False),
        (create_query_concat, "createQuery() with string concatenation", False),
        (create_query_concat2, "createQuery() with string concatenation", False),
        (table_concat, "Table name concatenation in SQL", False),
        (where_concat, "WHERE clause concatenation in SQL", False),
        # Quote-sandwich is never allowlistable: a raw variable embedded
        # between SQL quotes is injectable regardless of any parameter
        # placeholders or class metadata elsewhere in the statement.
        (quote_sandwich, "Value embedded between SQL quotes (injection)", True),
    ]

    found_patterns = set()  # Avoid duplicate messages

    for pattern, description, always_unsafe in patterns_to_check:
        matches = re.finditer(pattern, content, re.IGNORECASE)
        for match in matches:
            match_text = match.group(0)

            # Get the full statement containing this match
            statement = get_statement_containing(content, match.start(), match.end())

            # Skip if match is a safe pattern (never for always-unsafe patterns)
            if not always_unsafe and is_safe_pattern(match_text, statement, content):
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
        # Never allowlistable: quote-embedded raw value is injectable.
        (rf'["\'][^"\']*{sql_keywords}[^"\']*=\s*(["\'])\s*\+\s*\w+\s*\+\s*\1',  # nosemgrep: skills.code-injection.skill-sql-string-formatting.skill-sql-string-formatting -- detector regex for SQL safety hook, not SQL execution
         "String concatenation with quotes in SQL", True),
        # query = "SELECT ... " + variable;
        (rf'\w+\s*=\s*["\'][^"\']*{sql_keywords}[^"\']*["\']\s*\+',
         "SQL query built with string concatenation", False),
    ]

    for pattern, description, always_unsafe in raw_query_patterns:
        matches = re.finditer(pattern, content, re.IGNORECASE)
        for match in matches:
            match_text = match.group(0)
            statement = get_statement_containing(content, match.start(), match.end())

            if not always_unsafe and is_safe_pattern(match_text, statement, content):
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

        # Only process Edit, Write, and MultiEdit tools
        if tool_name not in ("Edit", "Write", "MultiEdit"):
            sys.exit(0)

        # Get file path and content
        file_path, content = get_file_content_from_input(tool_input, tool_name)

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
