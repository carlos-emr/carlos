/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.util;

import java.util.StringTokenizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Legacy string contracts used by stored CARLOS data. The blank helpers also
 * treat trimmed, case-insensitive {@code "null"} text as empty; numeric parsing,
 * tokenization and locale-sensitive matching intentionally differ from lang3.
 */
public class StringUtils {

    public final static String ELLIPSIS = "...";

    /**
     * use to have a maximum string length view
     * ie "hello world !!!" would be "hello wor..."
     * <p>
     * with maxlength 13 and shorted 8 and added "..."
     * <p>
     * BENZOICUM ACIDUM 1CH - 30CH
     * <p>
     * would equal
     * <p>
     * BENZOIC ...
     *
     * @param maxlength The maximum string length before truncating the string
     * @param shorted   length the string will be truncated to if maxlength is met
     * @param added     string added to original string if maxlength is met.  ie ...
     * @return either full description if its less than maxlength or shortened string if its not
     */
    public static String maxLenString(String str, int maxlength, int shorted, String added) {
        String ret = str;
        if ((str != null && maxlength > shorted) && (str.length() > maxlength)) {
            ret = str.substring(0, shorted) + added;
        }
        return ret;
    }

    /**
     * Returns true for Java null, text empty after {@link String#trim()}, or
     * trimmed {@code "null"} text in any case. Case conversion uses the default
     * locale. Unicode whitespace outside {@code trim()}'s range is retained.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isNullOrEmpty(String obj) {
        if (obj == null) {
            return true;
        } else if (obj.trim().equals("")) {
            return true;
        } else if (obj.trim().toUpperCase().equals("NULL")) {
            return true;
        } else if (obj.trim().toLowerCase().equals("null")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns true when {@link #filled(String)} and {@link Double#valueOf(String)}
     * accepts the value. This includes exponents, hexadecimal floating point,
     * surrounding ASCII whitespace, NaN and Infinity. Java null, blanks and
     * case-insensitive {@code "null"} text are rejected.
     *
     * @param str String
     * @return boolean
     */
    public static boolean isNumeric(String str) {
        boolean ret = false;
        if (filled(str)) {
            try {
                Double.valueOf(str);
                ret = true;
            } catch (NumberFormatException e) {
                ret = false;
            }
            return ret;
        }
        return ret;
    }

    /**
     * Returns true when {@link #filled(String)} and {@link Integer#valueOf(String)}
     * accepts the value. Java null, blanks and {@code "null"} text are rejected;
     * unlike {@link #isNumeric(String)}, surrounding whitespace is not accepted.
     *
     * @param str String
     * @return boolean
     */
    public static boolean isInteger(String str) {
        boolean ret = false;
        if (filled(str)) {
            try {
                Integer.valueOf(str);
                ret = true;
            } catch (NumberFormatException e) {
                ret = false;
            }
            return ret;
        }
        return ret;
    }

    /**
     * Splits using {@link StringTokenizer}: each delimiter character separates
     * tokens, and empty tokens are dropped (unlike {@link String#split(String)}).
     */
    public static String[] splitToStringArray(String rawString, String delimiter) {
        StringTokenizer st = new StringTokenizer(rawString, delimiter);
        String[] result = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            result[i] = (st.nextToken());
            i++;
        }
        return result;
    }

    /**
     * Strips linebreaks
     * Replace linebreaks and multiple spaces by a single space
     * johnchwk Apr 2008
     */
    public static String lineBreaks(String str) {
        StringBuilder mystringBuffer = new StringBuilder();
        mystringBuffer.append(str);

        boolean spaces = true;

        int position = 0;
        int strlen = (mystringBuffer.length());

        strlen--;                                // since position starts at 0

        // Convert all LB to spaces
        for (position = 0; position <= strlen; position++) {
            if (mystringBuffer.charAt(position) == '\r' || mystringBuffer.charAt(position) == '\n') {
                mystringBuffer.setCharAt(position, ' ');
            }
        }

        // Leave only single spaces
        position = 0;
        while (position <= strlen) {
            if (mystringBuffer.charAt(position) == ' ' && spaces) {
                mystringBuffer.deleteCharAt(position);
                strlen--;
            } else if (mystringBuffer.charAt(position) == ' ') {
                spaces = true;
                position++;
            } else {
                spaces = false;
                position++;
            }
        }

        return mystringBuffer.toString();
    }

    /**
     * Matches a substring after uppercasing both inputs in the default locale.
     * Returns false for null inputs; Unicode expansions and locale-specific case
     * mappings intentionally differ from lang3's region-based matching.
     */
    public static boolean containsIgnoreCase(String text, String searchWord) {
        if (text == null || searchWord == null) return false;

        text = text.toUpperCase();
        searchWord = searchWord.toUpperCase();

        return text.contains(searchWord);
    }

    /**
     * Returns empty text for Java null, trimmed blanks and case-insensitive
     * {@code "null"} text; otherwise returns the original, untrimmed value.
     * This is not equivalent to lang3's {@code defaultString} or {@code isBlank}.
     */
    static public String noNull(String maybeNullText) {
        return filled(maybeNullText) ? maybeNullText : "";
    }

    /** Returns true for Java null, trimmed blanks or {@code "null"} text in any case. */
    static public boolean empty(String s) {
        return isNullOrEmpty(s);
    }

    /** Returns false for Java null, trimmed blanks or {@code "null"} text in any case. */
    static public boolean filled(String s) {
        return !isNullOrEmpty(s);
    }
}
