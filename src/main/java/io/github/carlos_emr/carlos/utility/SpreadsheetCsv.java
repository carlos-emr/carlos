/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

/** Cell policy for CSV files intended to be opened in a spreadsheet. */
public final class SpreadsheetCsv {
    private SpreadsheetCsv() {
    }

    /**
     * Prefixes formula-like text with a tab, preserving plain negative numbers,
     * including those following leading whitespace. Full-width formula markers
     * are guarded too, as in the patient-list spreadsheet export policy.
     * Leading whitespace, control characters and a BOM must not hide a formula
     * marker. An initial tab, CR or LF is guarded even without a formula marker,
     * matching the dashboard export policy. Null is an empty CSV field.
     *
     * <p>This is a cell policy, not CSV serialization: callers must still quote
     * delimiters, quotes and line breaks, including the leading guard tab. The
     * tab is intentionally part of the exported value; CSV quoting alone does
     * not prevent formula evaluation by spreadsheet importers.</p>
     *
     * @param text raw header or data cell
     * @return spreadsheet-safe cell text, before CSV quoting
     */
    public static String sanitizeCell(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        char first = text.charAt(0);
        if (first == '\t' || first == '\r' || first == '\n') {
            return "\t" + text;
        }
        int index = 0;
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (!Character.isWhitespace(ch) && !Character.isSpaceChar(ch)
                    && !Character.isISOControl(ch) && ch != '\uFEFF') {
                break;
            }
            index++;
        }
        if (index < text.length()) {
            char marker = text.charAt(index);
            if (marker == '-' && isNegativeNumber(text, index)) {
                return text;
            }
            if (marker == '=' || marker == '+' || marker == '-' || marker == '@'
                    || marker == '\uFF1D' || marker == '\uFF0B'
                    || marker == '\uFF0D' || marker == '\uFF20') {
                return "\t" + text;
            }
        }
        return text;
    }

    /** Checks decimal/scientific notation in linear time without regex backtracking. */
    private static boolean isNegativeNumber(String text, int minusIndex) {
        int start = minusIndex + 1;
        int cursor = skipDigits(text, start);
        boolean hasDigits = cursor > start;
        if (cursor < text.length() && text.charAt(cursor) == '.') {
            start = cursor + 1;
            cursor = skipDigits(text, start);
            hasDigits |= cursor > start;
        }
        if (!hasDigits) {
            return false;
        }
        if (cursor < text.length() && (text.charAt(cursor) == 'e' || text.charAt(cursor) == 'E')) {
            cursor++;
            if (cursor < text.length() && (text.charAt(cursor) == '+' || text.charAt(cursor) == '-')) {
                cursor++;
            }
            start = cursor;
            cursor = skipDigits(text, start);
            if (cursor == start) {
                return false;
            }
        }
        return cursor == text.length();
    }

    private static int skipDigits(String text, int start) {
        int cursor = start;
        while (cursor < text.length() && text.charAt(cursor) >= '0' && text.charAt(cursor) <= '9') {
            cursor++;
        }
        return cursor;
    }
}
