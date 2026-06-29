/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

/**
 * Deterministic validator for limited SQL identifier fragments used in legacy
 * dynamic SQL builders.
 *
 * <p>This class intentionally does not parse general SQL. It accepts only the
 * identifier shapes that the current callers need: simple or dotted
 * identifiers, report table references with optional aliases, and lookup field
 * expressions made from identifiers and simple function calls with numeric or
 * single-quoted string literal arguments.</p>
 */
public final class SqlIdentifierValidator {

    private static final int MAX_EXPRESSION_NESTING = 16;

    private SqlIdentifierValidator() {
    }

    /**
     * Validates whether the given value is a simple or dotted SQL identifier.
     *
     * @param identifier String the SQL identifier to validate
     * @return boolean true when the identifier is valid; false otherwise
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null) {
            return false;
        }

        Parser parser = new Parser(identifier);
        return parser.parseQualifiedIdentifier(0)
                && parser.isEnd();
    }

    /**
     * Validates whether the given value is a comma-separated SQL table reference list.
     *
     * <p>Each table reference may be a simple table name, schema-qualified table
     * name, or one of those forms followed by an optional alias with or without
     * {@code AS}.</p>
     *
     * @param tableReferences String the SQL table reference list to validate
     * @return boolean true when the table reference list is valid; false otherwise
     */
    public static boolean isValidTableReferenceList(String tableReferences) {
        if (tableReferences == null) {
            return false;
        }

        Parser parser = new Parser(tableReferences);
        parser.skipWhitespace();
        if (parser.isEnd()) {
            return false;
        }

        if (!parser.parseTableReference()) {
            return false;
        }

        while (true) {
            parser.skipWhitespace();
            if (parser.isEnd()) {
                return true;
            }
            if (!parser.consume(',')) {
                return false;
            }
            parser.skipWhitespace();
            if (!parser.parseTableReference()) {
                return false;
            }
        }
    }

    /**
     * Validates whether the given value is a safe lookup field expression.
     *
     * <p>Accepted top-level expressions are identifiers, dotted identifiers, and
     * function calls with safe expression arguments. Numeric and single-quoted
     * string literals are accepted only as function call arguments. This is a
     * deliberately small grammar for legacy lookup field configuration, not a
     * general SQL expression parser.</p>
     *
     * @param expression String the lookup field expression to validate
     * @return boolean true when the lookup field expression is valid; false otherwise
     */
    public static boolean isValidFieldExpression(String expression) {
        if (expression == null) {
            return false;
        }

        Parser parser = new Parser(expression);
        parser.skipWhitespace();
        if (!parser.parseExpression(0)) {
            return false;
        }
        parser.skipWhitespace();
        return parser.isEnd();
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private boolean parseTableReference() {
            if (!parseQualifiedIdentifier(2)) {
                return false;
            }

            int whitespaceStart = position;
            skipWhitespace();
            boolean hadWhitespace = position > whitespaceStart;
            if (!hadWhitespace || isEnd() || peek() == ',') {
                return true;
            }

            boolean hasAsKeyword = consumeKeyword("as");
            if (hasAsKeyword && !consumeRequiredWhitespace()) {
                return false;
            }

            return parseIdentifier();
        }

        private boolean parseExpression(int depth) {
            if (depth > MAX_EXPRESSION_NESTING) {
                return false;
            }

            skipWhitespace();

            int mark = position;
            if (!parseQualifiedIdentifier(0)) {
                return false;
            }

            int identifierEnd = position;
            skipWhitespace();
            if (!consume('(')) {
                return true;
            }

            if (!isSingleIdentifier(mark, identifierEnd)) {
                return false;
            }

            skipWhitespace();
            if (consume(')')) {
                return true;
            }

            if (!parseExpressionArgument(depth)) {
                return false;
            }

            while (true) {
                skipWhitespace();
                if (consume(')')) {
                    return true;
                }
                if (!consume(',')) {
                    return false;
                }
                if (!parseExpressionArgument(depth)) {
                    return false;
                }
            }
        }

        private boolean parseExpressionArgument(int depth) {
            skipWhitespace();
            if (isEnd()) {
                return false;
            }
            if (peek() == '\'') {
                return parseStringLiteral();
            }
            if (isDigit(peek())) {
                return parseNumber();
            }
            return parseExpression(depth + 1);
        }

        private boolean parseQualifiedIdentifier(int maxSegments) {
            int segmentCount = 0;
            if (!parseIdentifier()) {
                return false;
            }
            segmentCount++;
            while (consume('.')) {
                if (maxSegments > 0 && segmentCount >= maxSegments) {
                    return false;
                }
                if (!parseIdentifier()) {
                    return false;
                }
                segmentCount++;
            }
            return true;
        }

        private boolean parseIdentifier() {
            if (isEnd() || !isIdentifierStart(peek())) {
                return false;
            }
            position++;
            while (!isEnd() && isIdentifierPart(peek())) {
                position++;
            }
            return true;
        }

        private boolean parseStringLiteral() {
            if (!consume('\'')) {
                return false;
            }
            while (!isEnd()) {
                char c = peek();
                if (c == '\'') {
                    if (position + 1 < input.length() && input.charAt(position + 1) == '\'') {
                        position += 2;
                        continue;
                    }
                    position++;
                    return true;
                }
                if (!isStringLiteralCharacter(c)) {
                    return false;
                }
                position++;
            }
            return false;
        }

        private boolean parseNumber() {
            if (isEnd() || !isDigit(peek())) {
                return false;
            }
            while (!isEnd() && isDigit(peek())) {
                position++;
            }
            if (consume('.')) {
                if (isEnd() || !isDigit(peek())) {
                    return false;
                }
                while (!isEnd() && isDigit(peek())) {
                    position++;
                }
            }
            return true;
        }

        private boolean consumeKeyword(String keyword) {
            int end = position + keyword.length();
            if (end > input.length() || !input.regionMatches(true, position, keyword, 0, keyword.length())) {
                return false;
            }
            if (end < input.length() && isIdentifierPart(input.charAt(end))) {
                return false;
            }
            position = end;
            return true;
        }

        private boolean consumeRequiredWhitespace() {
            if (isEnd() || !Character.isWhitespace(peek())) {
                return false;
            }
            skipWhitespace();
            return true;
        }

        private boolean consume(char expected) {
            if (!isEnd() && peek() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private char peek() {
            return input.charAt(position);
        }

        private void skipWhitespace() {
            while (!isEnd() && Character.isWhitespace(peek())) {
                position++;
            }
        }

        private boolean isEnd() {
            return position >= input.length();
        }

        private boolean isSingleIdentifier(int startInclusive, int endExclusive) {
            for (int i = startInclusive; i < endExclusive; i++) {
                if (input.charAt(i) == '.') {
                    return false;
                }
            }
            return true;
        }

        private static boolean isIdentifierStart(char c) {
            return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
        }

        private static boolean isIdentifierPart(char c) {
            return isIdentifierStart(c) || isDigit(c);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        /**
         * Allows only characters known to be needed by legacy lookup literals.
         * Whitespace controls, operators, and other SQL-significant punctuation
         * are intentionally excluded.
         */
        private static boolean isStringLiteralCharacter(char c) {
            return c == ' ' || c == ',' || c == '.' || c == '(' || c == ')' || isIdentifierPart(c);
        }
    }
}
