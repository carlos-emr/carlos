/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import java.util.regex.Pattern;

/** Cell policy for CSV files intended to be opened in a spreadsheet. */
public final class SpreadsheetCsv {
    private static final Pattern NEGATIVE_NUMBER =
            Pattern.compile("^-(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");

    private SpreadsheetCsv() {
    }

    /**
     * Prefixes formula-like text with a tab, preserving plain negative numbers.
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
        if (text == null || text.isEmpty() || NEGATIVE_NUMBER.matcher(text).matches()) {
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
            if (marker == '=' || marker == '+' || marker == '-' || marker == '@') {
                return "\t" + text;
            }
        }
        return text;
    }
}
