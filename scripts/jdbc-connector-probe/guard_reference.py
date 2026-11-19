#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Reference model of the proposed executeQuery statement guard, checked against the Connector/J 9.6.0 oracle."""
import sys

RESULT_WORDS = {"SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "ANALYZE", "CHECK", "CHECKSUM", "OPTIMIZE", "REPAIR",
                "HELP", "TABLE", "VALUES", "WITH", "CALL", "EXECUTE"}
QUERY_EXPRESSIONS = {"SELECT", "TABLE", "VALUES", "WITH"}


def is_ident(ch):
    return ch.isalnum() or ch in "_$" or ord(ch) > 127


def skip_ignorable(sql, i):
    """Skip whitespace and non-executable comments. Executable comments (/*! and /*M!) are SQL: step inside them."""
    n = len(sql)
    while i < n:
        ch = sql[i]
        if ch.isspace():
            i += 1
        elif sql.startswith("/*", i):
            if sql.startswith("/*!", i) or sql.startswith("/*M!", i):
                i += 3 if sql.startswith("/*!", i) else 4
                while i < n and sql[i].isdigit():
                    i += 1
            else:
                end = sql.find("*/", i + 2)
                i = n if end < 0 else end + 2
        elif sql.startswith("--", i) and (i + 2 >= n or sql[i + 2].isspace()):
            end = sql.find("\n", i)
            i = n if end < 0 else end + 1
        elif ch == "#":
            end = sql.find("\n", i)
            i = n if end < 0 else end + 1
        else:
            break
    return i


def word_at(sql, i):
    j = i
    while j < len(sql) and sql[j].isascii() and sql[j].isalpha():
        j += 1
    if j < len(sql) and is_ident(sql[j]):
        return "", i  # part of a longer identifier such as SELECTED or select_1
    return sql[i:j].upper(), j


def scan_keywords(sql, i, visit):
    """Calls visit(WORD, parenDepth, endIndex) for every bare word outside quotes, backticks and comments; stops when it returns True."""
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
        elif (sql.startswith("--", i) and (i + 2 >= n or sql[i + 2].isspace())) or ch == "#":
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
        nxt, _ = word_at(sql, skip_ignorable(sql, j))
        return "SENT" if nxt == "RECOVER" else "REFUSED"
    if word not in RESULT_WORDS:
        return "REFUSED"
    if word in QUERY_EXPRESSIONS:
        if scan_keywords(sql, j, lambda w, depth, end: w == "INTO"):
            return "REFUSED"
    if word == "WITH":
        previous = [""]

        def dml(w, depth, end):
            hit = False
            if depth == 0:
                nxt = skip_ignorable(sql, end)
                call = nxt < len(sql) and sql[nxt] == "("
                if w == "DELETE" or (w in ("INSERT", "REPLACE") and not call) or (w == "UPDATE" and previous[0] != "FOR"):
                    hit = True
            previous[0] = w
            return hit
        if scan_keywords(sql, j, dml):
            return "REFUSED"
    return "SENT"


if __name__ == "__main__":
    cj, maria = {}, {}
    for path, table in (("out118v/guard-cj.tsv", cj), ("out118v/guard-adapter.tsv", maria)):
        for line in open(path, encoding="utf-8"):
            p = line.rstrip("\n").split("\t")
            if len(p) >= 4 and p[0] == "S":
                table[p[1]] = (p[2], p[3])
    rows = []
    deviations = 0
    for esc, (outcome, _) in cj.items():
        sql = esc.replace("\\n", "\n").replace("\\t", "\t")
        if sql.startswith("?SELECT"):
            continue  # byte-order-mark case lost by the console encoding
        cjv = "REFUSED" if outcome == "REFUSED-BY-DRIVER" else "SENT"
        ours = classify(sql)
        changes = maria[esc][1] == "CHANGED"
        if ours != cjv:
            deviations += 1
            print("DEVIATION %-8s cj=%-8s changes=%-5s %s" % (ours, cjv, changes, esc[:90]))
        if cjv == "REFUSED" and changes and ours != "REFUSED":
            print("!!! UNSAFE: Connector/J refused a statement that changes data and the rule sends it:", esc)
        rows.append((cjv, ours, "yes" if changes else "no", esc))
    print("statements=%d deviations=%d" % (len(rows), deviations))
    with open("out118v/executequery-oracle.tsv", "w", encoding="utf-8") as f:
        f.write("# Oracle for the executeQuery() statement guard. Recorded 2026-11-19 on MariaDB 11.8.8 (sql_mode='').\n")
        f.write("# connectorj: what MySQL Connector/J 9.6.0 did with Statement.executeQuery(sql): REFUSED (client-side, never sent) or SENT.\n")
        f.write("# carlos:     what the CARLOS adapter must do. Differences from connectorj are deliberate; see docs/mariadb-connector-migration.md.\n")
        f.write("# changes:    whether sending the statement through MariaDB Connector/J 3.5.11 executeQuery() changed the database.\n")
        f.write("# statement:  \\n and \\t are escaped. Lines starting with # are comments.\n")
        f.write("connectorj\tcarlos\tchanges\tstatement\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
