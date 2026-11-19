#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Reference model of the executeQuery statement guard, and the tool that checks or writes its oracle.

usage:
  guard_reference.py check  ORACLE.tsv
      Verify that the model agrees with the `carlos` column of every row. Exit status 1 on any
      disagreement, or when a statement Connector/J refused and that changes data would be sent.
  guard_reference.py record GUARD-CJ.tsv GUARD-ADAPTER.tsv
      Print oracle rows built from two GuardOracle outputs (the reference driver's and the adapter's).

The rule is written out in docs/superpowers/specs/2026-11-19-mariadb-driver-switch-findings.md, section 6.
The Java implementation must agree with this model on every row of
src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv.
"""
import sys

RESULT_WORDS = {"SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "ANALYZE", "CHECK", "CHECKSUM", "OPTIMIZE", "REPAIR",
                "HELP", "TABLE", "VALUES", "WITH", "CALL", "EXECUTE"}
QUERY_EXPRESSIONS = {"SELECT", "TABLE", "VALUES", "WITH"}
WHITESPACE = " \t\n\r\x0b\x0c"


def is_ident(ch):
    """Identifier characters: ASCII letters and digits, underscore, dollar, and everything above U+007F."""
    return ("0" <= ch <= "9") or ("A" <= ch <= "Z") or ("a" <= ch <= "z") or ch in "_$" or ord(ch) > 127


def is_line_comment(sql, i):
    """'#', or '--' followed by white space or the end of the text."""
    return sql[i] == "#" or (sql.startswith("--", i) and (i + 2 >= len(sql) or sql[i + 2] in WHITESPACE))


def skip_ignorable(sql, i):
    """Skip what precedes the first word: white space, comments, and the opener of a `/*!` executable comment.

    `/*!` and its version digits are stepped over, so the word inside is read as SQL, as MySQL Connector/J
    did. `/*M!` is MariaDB-only; Connector/J read it as an ordinary comment, and so does this function.
    """
    n = len(sql)
    while i < n:
        ch = sql[i]
        if ch in WHITESPACE:
            i += 1
        elif sql.startswith("/*!", i):
            i += 3
            while i < n and "0" <= sql[i] <= "9":
                i += 1
        elif sql.startswith("/*", i):
            end = sql.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif is_line_comment(sql, i):
            end = sql.find("\n", i)
            i = n if end < 0 else end + 1
        else:
            break
    return i


def word_at(sql, i):
    """The run of ASCII letters at i, upper-cased, unless it is part of a longer identifier (SELECTED, select_1)."""
    j = i
    while j < len(sql) and (("A" <= sql[j] <= "Z") or ("a" <= sql[j] <= "z")):
        j += 1
    if j < len(sql) and is_ident(sql[j]):
        return "", i
    return sql[i:j].upper(), j


def scan_words(sql, i, visit):
    """Call visit(WORD, parenDepth, endIndex) for every bare word after i; stop when it returns True.

    Words inside quotes, backticks and non-executable comments are not visited, nor is a word directly
    preceded by '@' or '.'. The text of both kinds of executable comment (`/*!` and `/*M!`) is visited:
    the server may run it, so a write keyword there must count. A backslash escapes the next character
    inside single and double quotes.
    """
    n = len(sql)
    depth = 0
    while i < n:
        ch = sql[i]
        if ch in "'\"`":
            quote = ch
            i += 1
            while i < n:
                if sql[i] == "\\" and quote != "`":
                    i += 2
                    continue
                if sql[i] == quote:
                    if i + 1 < n and sql[i + 1] == quote:
                        i += 2
                        continue
                    break
                i += 1
            i += 1
        elif sql.startswith("/*", i) and not (sql.startswith("/*!", i) or sql.startswith("/*M!", i)):
            end = sql.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif is_line_comment(sql, i):
            end = sql.find("\n", i)
            i = n if end < 0 else end + 1
        elif is_ident(ch):
            j = i
            while j < n and is_ident(sql[j]):
                j += 1
            qualified = i > 0 and sql[i - 1] in "@."
            if not qualified and visit(sql[i:j].upper(), depth, j):
                return True
            i = j
        elif ch == "(":
            depth += 1
            i += 1
        elif ch == ")":
            depth -= 1
            i += 1
        else:
            i += 1
    return False


def classify(sql):
    """'SENT' when executeQuery may send the statement, 'REFUSED' otherwise."""
    if sql is None:
        return "REFUSED"
    i = skip_ignorable(sql, 0)
    if i < len(sql) and sql[i] == "{":  # JDBC call escape: {call p()} / {?= call f()}
        i = skip_ignorable(sql, i + 1)
        if sql.startswith("?", i):
            i = skip_ignorable(sql, i + 1)
            if sql.startswith("=", i):
                i = skip_ignorable(sql, i + 1)
    while i < len(sql) and sql[i] == "(":
        i = skip_ignorable(sql, i + 1)
    word, j = word_at(sql, i)
    if word == "XA":
        following, _ = word_at(sql, skip_ignorable(sql, j))
        return "SENT" if following == "RECOVER" else "REFUSED"
    if word not in RESULT_WORDS:
        return "REFUSED"
    if word not in QUERY_EXPRESSIONS:
        return "SENT"
    previous = [""]

    def writes(w, depth, end):
        hit = w == "INTO"
        if word == "WITH" and depth == 0:
            following = skip_ignorable(sql, end)
            call = following < len(sql) and sql[following] == "("
            if w == "DELETE" or (w in ("INSERT", "REPLACE") and not call) or (w == "UPDATE" and previous[0] != "FOR"):
                hit = True
        previous[0] = w
        return hit

    return "REFUSED" if scan_words(sql, j, writes) else "SENT"


def unescape(text):
    return text.replace("\\n", "\n").replace("\\t", "\t")


def check(path):
    rows = recorded = deviations = wrong = unsafe = 0
    for number, line in enumerate(open(path, encoding="utf-8"), 1):
        line = line.rstrip("\n")
        if line.startswith("#") or line.startswith("connectorj\t"):
            continue
        connectorj, carlos, changes, statement = line.split("\t", 3)
        rows += 1
        model = classify(unescape(statement))
        if model != carlos:
            wrong += 1
            print("line %d: the model says %s, the oracle says %s: %s" % (number, model, carlos, statement[:90]))
        if connectorj != "unrecorded":
            recorded += 1
            if connectorj != carlos:
                deviations += 1
            if connectorj == "REFUSED" and changes == "yes" and model != "REFUSED":
                unsafe += 1
                print("line %d: UNSAFE, a refused statement that changes data is sent: %s" % (number, statement[:90]))
    print("rows=%d recorded=%d deviations-from-connectorj=%d model-disagreements=%d unsafe=%d"
          % (rows, recorded, deviations, wrong, unsafe))
    return 1 if wrong or unsafe else 0


def record(cj_path, adapter_path):
    tables = []
    for path in (cj_path, adapter_path):
        table = {}
        for line in open(path, encoding="utf-8"):
            p = line.rstrip("\n").split("\t")
            if len(p) >= 4 and p[0] == "S":
                table[p[1]] = (p[2], p[3])
        tables.append(table)
    cj, adapter = tables
    print("connectorj\tcarlos\tchanges\tstatement")
    for escaped, (outcome, _) in cj.items():
        connectorj = "REFUSED" if outcome == "REFUSED-BY-DRIVER" else "SENT"
        changes = "yes" if adapter[escaped][1] == "CHANGED" else "no"
        print("\t".join((connectorj, classify(unescape(escaped)), changes, escaped)))
    return 0


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "check":
        sys.exit(check(sys.argv[2]))
    if len(sys.argv) == 4 and sys.argv[1] == "record":
        sys.exit(record(sys.argv[2], sys.argv[3]))
    sys.exit(__doc__)
