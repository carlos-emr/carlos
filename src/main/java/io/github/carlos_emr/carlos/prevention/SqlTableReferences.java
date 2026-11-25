/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prevention;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads which tables MariaDB SQL text names in a table position, without a grammar.
 *
 * <p>{@link PreventionReadSafetyService} reads every installed SQL definition this way as well as
 * with JSqlParser, because JSqlParser 5.3 does not see what MariaDB sees: it rejects both shipped
 * influenza dashboard queries, whose {@code BETWEEN} upper bound is a call with a nested call in a
 * later argument ({@code STR_TO_DATE(CONCAT('31,', '12,', (YEAR(NOW()) - 1)), '%d,%m,%Y')}), and it
 * takes text MariaDB runs, after {@code 1--} or inside an executable comment, for a comment. This
 * reader answers the one question the classifier asks, for any text: which tables are named where
 * MariaDB resolves a table (issue #6344).</p>
 *
 * <p>A table position is the reference right after {@code FROM} in a query, after {@code JOIN} or
 * {@code STRAIGHT_JOIN}, after a comma of a {@code FROM} list, and the first entry of a
 * parenthesised {@code FROM} list. Select-list and predicate columns, aliases, literals, schema
 * qualifiers and the {@code FROM} of a function call such as {@code EXTRACT(YEAR FROM ...)} are not
 * table references. Views, stored functions and common table expressions are not resolved: a CTE
 * is reported under its own name.</p>
 *
 * <p>The lexical rules are MariaDB's: {@code #} comments, {@code --} comments (only when blank or
 * control text follows the dashes) and block comments are skipped, but the body of an executable
 * comment ({@code /*!} or {@code /*M!}) is read, because MariaDB runs it. Single- and double-quoted
 * text is a literal, except that a double-quoted name in a table position is read as the name
 * {@code ANSI_QUOTES} makes it. Backtick-quoted names are unquoted, and a qualified name such as
 * {@code `clinic`.`preventions`} or {@code clinic . preventions} is reported by its last part.</p>
 */
final class SqlTableReferences {

    /** What a keyword does to the table positions of its parenthesis level. */
    private enum Role { QUERY, FROM, JOIN, STRAIGHT_JOIN, CLAUSE, FOR, OTHER }

    /** Kinds of lexical token. {@code QUOTED} is a backtick- or double-quoted name. */
    private enum Kind { WORD, QUOTED, LITERAL, SYMBOL, END }

    private static final Map<String, Role> KEYWORDS = Map.ofEntries(
            Map.entry("select", Role.QUERY),
            Map.entry("with", Role.QUERY),
            Map.entry("from", Role.FROM),
            Map.entry("join", Role.JOIN),
            Map.entry("straight_join", Role.STRAIGHT_JOIN),
            Map.entry("for", Role.FOR),
            // After these, a comma separates expressions or assignments, never tables.
            Map.entry("where", Role.CLAUSE),
            Map.entry("group", Role.CLAUSE),
            Map.entry("having", Role.CLAUSE),
            Map.entry("order", Role.CLAUSE),
            Map.entry("limit", Role.CLAUSE),
            Map.entry("window", Role.CLAUSE),
            Map.entry("union", Role.CLAUSE),
            Map.entry("except", Role.CLAUSE),
            Map.entry("intersect", Role.CLAUSE),
            Map.entry("set", Role.CLAUSE),
            Map.entry("update", Role.CLAUSE),
            Map.entry("returning", Role.CLAUSE));

    private static final Token END = new Token(Kind.END, "");

    private SqlTableReferences() {
    }

    /**
     * Returns the tables the text names in a table position.
     *
     * @param sql SQL text; template placeholders and malformed or truncated text are tolerated
     * @return the unquoted last part of each table name, as written, in order of first appearance
     */
    static Set<String> tableNames(String sql) {
        return new TableScan(sql).run();
    }

    /**
     * Folds ASCII letters to lower case and leaves every other character alone. SQL keywords and the
     * table names compared with them are ASCII, so no locale or Unicode case rule is involved.
     */
    static String foldAsciiCase(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] >= 'A' && chars[i] <= 'Z') {
                chars[i] = (char) (chars[i] + ('a' - 'A'));
            }
        }
        return new String(chars);
    }

    private static Role role(Token word) {
        return KEYWORDS.getOrDefault(foldAsciiCase(word.text()), Role.OTHER);
    }

    /** A lexical token; the text of a quoted name or literal is unquoted. */
    private record Token(Kind kind, String text) {
        boolean isSymbol(char symbol) {
            return kind == Kind.SYMBOL && text.charAt(0) == symbol;
        }

        boolean isName() {
            return kind == Kind.WORD || kind == Kind.QUOTED;
        }
    }

    /** Splits text into MariaDB tokens, skipping blanks and comments; one token of look-ahead. */
    private static final class Lexer {
        private final String sql;
        private int pos;
        private boolean inExecutableComment;
        private Token peeked;

        Lexer(String sql) {
            this.sql = sql;
        }

        Token next() {
            Token token = peek();
            peeked = null;
            return token;
        }

        Token peek() {
            if (peeked == null) {
                peeked = read();
            }
            return peeked;
        }

        private Token read() {
            skipIgnorable();
            if (pos >= sql.length()) {
                return END;
            }
            char c = sql.charAt(pos);
            if (c == '`') {
                return new Token(Kind.QUOTED, quoted(c, false));
            }
            if (c == '"') {
                return new Token(Kind.QUOTED, quoted(c, true));
            }
            if (c == '\'') {
                return new Token(Kind.LITERAL, quoted(c, true));
            }
            if (isWordChar(c)) {
                return new Token(Kind.WORD, word());
            }
            pos++;
            return new Token(Kind.SYMBOL, String.valueOf(c));
        }

        private void skipIgnorable() {
            boolean skipped = true;
            while (skipped && pos < sql.length()) {
                skipped = skipOne();
            }
        }

        /** Skips one blank, comment or executable-comment delimiter at the position, if there is one. */
        private boolean skipOne() {
            char c = sql.charAt(pos);
            if (c <= ' ') {
                pos++;
            } else if (c == '#' || startsDashComment()) {
                int end = sql.indexOf('\n', pos);
                pos = end < 0 ? sql.length() : end;
            } else if (sql.startsWith("/*", pos)) {
                skipBlockCommentOpening();
            } else if (inExecutableComment && sql.startsWith("*/", pos)) {
                inExecutableComment = false;
                pos += 2;
            } else {
                return false;
            }
            return true;
        }

        /** MariaDB starts a comment at "--" only when a blank or control character, or the end, follows. */
        private boolean startsDashComment() {
            return sql.startsWith("--", pos) && (pos + 2 == sql.length() || sql.charAt(pos + 2) <= ' ');
        }

        /** Skips a whole comment, or only the opening (and version) of an executable one. */
        private void skipBlockCommentOpening() {
            int body = pos + 2;
            int code = executableCodeStart(body);
            if (code > 0) {
                inExecutableComment = true;
                pos = code;
            } else {
                int end = sql.indexOf("*/", body);
                pos = end < 0 ? sql.length() : end + 2;
            }
        }

        /** Where the code of an executable comment starts, after an optional version number; 0 if none. */
        private int executableCodeStart(int body) {
            int code;
            if (sql.startsWith("!", body)) {
                code = body + 1;
            } else if (sql.startsWith("M!", body)) {
                code = body + 2;
            } else {
                return 0;
            }
            while (code < sql.length() && sql.charAt(code) >= '0' && sql.charAt(code) <= '9') {
                code++;
            }
            return code;
        }

        /** Reads quoted text after its opening quote; a doubled quote stands for itself. */
        private String quoted(char quote, boolean backslashEscapes) {
            StringBuilder text = new StringBuilder();
            pos++;
            while (pos < sql.length()) {
                char c = sql.charAt(pos++);
                if (c == '\\' && backslashEscapes && pos < sql.length()) {
                    text.append(sql.charAt(pos++));
                } else if (c != quote) {
                    text.append(c);
                } else if (pos < sql.length() && sql.charAt(pos) == quote) {
                    text.append(quote);
                    pos++;
                } else {
                    return text.toString();
                }
            }
            return text.toString();
        }

        private String word() {
            int start = pos;
            while (pos < sql.length() && isWordChar(sql.charAt(pos))) {
                pos++;
            }
            return sql.substring(start, pos);
        }

        /** MariaDB unquoted identifiers: ASCII letters, digits, '$', '_' and anything from U+0080. */
        private static boolean isWordChar(char c) {
            return c >= 0x80 || c == '_' || c == '$' || Character.isLetterOrDigit(c);
        }
    }

    /** Table-position state of one parenthesis level. */
    private static final class Scope {
        private boolean query;
        private boolean tableList;
        private boolean expectTable;

        /** A parenthesis opened in a table position holds a derived table or a parenthesised FROM list. */
        Scope(boolean tablePosition) {
            listTables(tablePosition);
        }

        void listTables(boolean active) {
            tableList = active;
            expectTable = active;
        }
    }

    /** Walks the tokens once, keeping one {@link Scope} per open parenthesis. */
    private static final class TableScan {
        private final Lexer lexer;
        private final Deque<Scope> scopes = new ArrayDeque<>();
        private final Set<String> names = new LinkedHashSet<>();
        private boolean afterFor;

        TableScan(String sql) {
            lexer = new Lexer(sql);
            scopes.push(new Scope(false));
        }

        Set<String> run() {
            Token token = lexer.next();
            while (token.kind() != Kind.END) {
                token = accept(token);
            }
            return names;
        }

        /** Applies one token and returns the next one to apply. */
        private Token accept(Token token) {
            Scope scope = scopes.element();
            boolean followsFor = afterFor;
            afterFor = false;
            if (scope.expectTable && isTableName(token)) {
                scope.expectTable = false;
                return readQualifiedName(token);
            }
            if (token.kind() == Kind.SYMBOL) {
                acceptSymbol(scope, token.text().charAt(0));
            } else if (token.kind() == Kind.WORD) {
                acceptWord(scope, role(token), followsFor);
            } else {
                scope.expectTable = false;
            }
            return lexer.next();
        }

        /**
         * In {@code FOR UPDATE} and in the index hints {@code FOR JOIN}, {@code FOR ORDER BY} and
         * {@code FOR GROUP BY}, the keyword after FOR neither joins a table nor ends the FROM list.
         */
        private void acceptWord(Scope scope, Role role, boolean followsFor) {
            if (role == Role.FOR) {
                afterFor = true;
                scope.expectTable = false;
            } else if (followsFor && (role == Role.JOIN || role == Role.CLAUSE)) {
                scope.expectTable = false;
            } else {
                acceptKeyword(scope, role);
            }
        }

        /** SELECT or WITH in a table position opens a derived table rather than naming one. */
        private static boolean isTableName(Token token) {
            return token.kind() == Kind.QUOTED || (token.kind() == Kind.WORD && role(token) != Role.QUERY);
        }

        /** Records the last part of {@code name}, {@code schema.name} or {@code `schema` . `name`}. */
        private Token readQualifiedName(Token first) {
            String name = first.text();
            Token next = lexer.next();
            while (next.isSymbol('.') && lexer.peek().isName()) {
                name = lexer.next().text();
                next = lexer.next();
            }
            names.add(name);
            return next;
        }

        private void acceptSymbol(Scope scope, char symbol) {
            switch (symbol) {
                case '(' -> {
                    scopes.push(new Scope(scope.expectTable));
                    scope.expectTable = false;
                }
                case ')' -> {
                    if (scopes.size() > 1) {
                        scopes.pop();
                    }
                }
                case ',' -> scope.expectTable = scope.tableList;
                case ';' -> {
                    scopes.clear();
                    scopes.push(new Scope(false));
                }
                // A leading dot (ODBC ".tbl_name", the default database) keeps the table position.
                default -> scope.expectTable = scope.expectTable && symbol == '.';
            }
        }

        private static void acceptKeyword(Scope scope, Role role) {
            switch (role) {
                case QUERY -> {
                    scope.query = true;
                    scope.listTables(false);
                }
                // FROM inside a function call, as in EXTRACT(YEAR FROM ...), names no table.
                case FROM -> scope.listTables(scope.query);
                case JOIN -> scope.listTables(true);
                // Before the select list, STRAIGHT_JOIN is a modifier, not a join.
                case STRAIGHT_JOIN -> scope.expectTable = scope.tableList;
                case CLAUSE -> scope.listTables(false);
                default -> scope.expectTable = false;
            }
        }
    }
}
