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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;

import io.github.carlos_emr.Misc;
import org.apache.commons.codec.binary.Base64;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Miscellaneous utility class providing HTML/JavaScript/MySQL escaping, Base64 encoding/decoding,
 * type conversion, string manipulation, and result set processing helpers.
 *
 * @since 2001-01-01
 */
public class UtilMisc {
    /**
     * @deprecated use apache's StringEscapeUtils instead.
     */
    @Deprecated
    public static String htmlEscape(String S) {

        if (null == S) {
            return S;
        }
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&#39;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * For eformGenerator to Edit-Html window.
     * This method is used to generate html symbols,
     * e.g., change HTML entities to their character equivalents.
     */
    public static String rhtmlEscape(String S) {
        if (null == S) return S;

        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '&') {//the read one more char and encode
                String temp = new String();
                if (i + 1 < N) temp += S.charAt(i + 1);
                if (temp.equalsIgnoreCase("a")) {//&amp
                    sb.append("&");
                    i += 4;
                    continue;
                } else if (temp.equalsIgnoreCase("l")) {//&lt
                    sb.append("<");
                    i += 3;
                    continue;
                } else if (temp.equalsIgnoreCase("g")) {//&gt
                    sb.append(">");
                    i += 3;
                    continue;
                } else if (temp.equalsIgnoreCase("q")) {//&quot
                    sb.append("\"");
                    i += 5;
                    continue;
                } else if (temp.equals("#")) {//&#
                    if (i + 2 < N) temp += S.charAt(i + 2); //&#?
                    if (i + 3 < N) temp += S.charAt(i + 3); //&#??
                    if (i + 4 < N) temp += S.charAt(i + 4); //&#???
                    if (temp.equals("&#39;")) {//'
                        sb.append("\'");
                        i += 5;
                        continue;
                    }
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes a string for safe inclusion in MySQL queries by escaping backslashes,
     * single quotes, and converting newlines to literal {@code \r\n}.
     *
     * @param S String the string to escape
     * @return String the MySQL-escaped string, or null if input is null
     */
    public static String mysqlEscape(String S) {
        if (null == S) {
            return S;
        }
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '\\') {
                sb.append("\\");
            } else if (c == '\'') {
                sb.append("\\'");
            } else if (c == '\n') {
                sb.append("\\r\\n");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * @deprecated use apache's StringEscapeUtils instead.
     */
    @Deprecated
    public static String JSEscape(String S) {
        if (null == S) {
            return S;
        }
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '"') {
                sb.append("&quot;");
            } else if (c == '\'') {
                sb.append("&#39;");
            } else if (c == '\n') {
                sb.append("<br>");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts a string to title case (first letter of each word capitalized, rest lowercase).
     * Words are separated by spaces or commas.
     *
     * @param S String the string to convert
     * @return String the title-cased string, or null if input is null
     */
    public static String toUpperLowerCase(String S) {
        if (S == null) {
            return S;
        }
        S = S.trim().toLowerCase();
        int N = S.length();
        boolean bUpper = false;
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (i == 0 || bUpper) {
                sb.append(Character.toUpperCase(c));
                bUpper = false;
            } else {
                sb.append(c);
            }
            if (c == ' ' || c == ',') {
                bUpper = true;
            }
        }
        return sb.toString();
    }

    /**
     * Returns a string truncated to the specified limit, using a default if the input is null.
     *
     * @param s String the input string (may be null)
     * @param dflt String the default value if input is null
     * @param nLimit int the maximum length
     * @return String the truncated string
     */
    public static String getShortStr(String s, String dflt, int nLimit) {
        if (s == null) {
            s = dflt;
        }
        int nLength = s.length();
        if (nLength > nLimit) {
            s = s.substring(0, nLimit);
        }
        return s;
    }

    /**
     * Encodes a string to Base64.
     *
     * @param plainText String the text to encode
     * @return String the Base64-encoded string
     */
    public static String encode64(String plainText) {
        return (new String(Base64.encodeBase64(plainText.getBytes())));
    }

    /**
     * Decodes a Base64-encoded string back to plain text.
     *
     * @param encodedText String the Base64-encoded text
     * @return String the decoded plain text
     */
    public static String decode64(String encodedText) {
        return (new String(Base64.decodeBase64(encodedText.getBytes())));
    }

    /**
     * Converts a boolean to an integer (1 for true, 0 for false).
     *
     * @param Expression boolean the value to convert
     * @return int 1 if true, 0 if false
     */
    public static int BoolToInt(boolean Expression) {
        return !Expression ? 0 : 1;
    }

    /**
     * Converts an integer to a boolean (false for 0, true for non-zero).
     *
     * @param Expression int the value to convert
     * @return boolean false if 0, true otherwise
     */
    public static boolean IntToBool(int Expression) {
        return Expression != 0;
    }

    /**
     * Formats a float value to a locale-specific number string.
     *
     * @param value float the value to format
     * @return String the formatted number string
     */
    public static String FloatToString(float value) {
        Float f = Float.valueOf(value);
        NumberFormat fmt = NumberFormat.getNumberInstance();
        String s = fmt.format(f.doubleValue());
        return s;
    }

    /**
     * Parses a string to a float value.
     *
     * @param value String the string to parse
     * @return float the parsed float value
     * @throws NumberFormatException if the string cannot be parsed
     */
    public static float StringToFloat(String value) {
        return Float.parseFloat(value);
    }

    /**
     * This method attempts to parse the provided String to a double value
     * If the value is non-numeric a value of 0.0 is returned.
     *
     * @param value String
     * @return double
     */
    public static double safeParseDouble(String value) {
        double ret = 0.0;
        try {
            ret = Double.parseDouble(value);
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        return ret;

    }

    /**
     * Inline-if function returning one of two objects based on a boolean expression.
     *
     * @param Expression boolean the condition to evaluate
     * @param TruePart Object the value returned if the condition is true
     * @param FalsePart Object the value returned if the condition is false
     * @return Object the selected value
     */
    public static Object IIf(boolean Expression, Object TruePart,
                             Object FalsePart) {
        if (Expression) {
            return TruePart;
        } else {
            return FalsePart;
        }
    }

    /**
     * Joins an array of objects into a comma-separated string with each element single-quoted.
     *
     * @param array Object[] the array to join
     * @return String the joined string (e.g., {@code 'a', 'b', 'c'})
     */
    public static String joinArray(Object array[]) {
        String ret = "";
        for (int i = 0; i < array.length; i++) {
            ret = String.valueOf(ret)
                    + String.valueOf(String.valueOf(String
                    .valueOf((new StringBuilder("'")).
                            append(
                                    String.valueOf(array[i])).append("'"))));
            if (i < array.length - 1) {
                ret = String.valueOf(String.valueOf(ret)).concat(", ");
            }
        }
        return ret;
    }

    /**
     * Replaces all occurrences of a search string within an expression with a replacement string.
     *
     * @param expression String the source string (may be null)
     * @param searchFor String the substring to search for
     * @param replaceWith String the replacement string
     * @return String the modified string, or null if expression is null
     */
    public static String replace(String expression, String searchFor,
                                 String replaceWith) {
        if (expression != null) {
            StringBuilder buf = new StringBuilder(expression);
            int pos = -1;
            do {
                pos = buf.indexOf(searchFor, pos);
                if (pos > -1) {
                    buf.delete(pos, pos + searchFor.length());
                    buf.insert(pos, replaceWith);
                    pos += replaceWith.length();
                } else {
                    return buf.toString();
                }
            }
            while (true);
        } else {
            return null;
        }
    }

    /**
     * not quite qorking yet
     public static int[] range(int start, int stop, int step) {
     stop = stop < step ? start : step++;
     step = step < 1 ? 1 : step;
     int arrayLen = (stop - start) / step + (stop - start) % step;
     MiscUtils.getLogger().debug(arrayLen);
     int[] rangeArray = new int[arrayLen];
     for (int i = 0; i < arrayLen; i++) {
     if (i == 0) {
     rangeArray[i] = start;
     }
     else {
     rangeArray[i] = rangeArray[i - 1] + step;
     }
     }
     return rangeArray;
     }

     public static int[] range(int start, int stop) {
     return range(start, stop, 1);
     }
     }
     **/
    /**
     * Returns an int array with the specified number of elements in
     *
     * @param length int
     * @return int[]
     */
    public static int[] range(int length) {
        int[] rangeArray = new int[length];
        for (int i = 0; i < length; i++) {
            rangeArray[i] = i;
        }
        return rangeArray;
    }

    /**
     * This method attempts to parse the provided String to an int value
     * If the value is non-numeric a value of 0 is returned.
     *
     * @param value String
     * @return int
     */

    public static int safeParseInt(String value) {
        int ret = 0;
        try {
            ret = Integer.parseInt(value);
        } catch (Exception ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        return ret;

    }

    /**
     * Returns the provided double value rounded up to 2 decimal places
     *
     * @param value double
     * @return double
     */
    public static double toCurrencyDouble(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
        return bd.doubleValue();
    }


    /**
     * Converts a {@link ResultSet} into a two-dimensional string array. The first row
     * contains column names, and subsequent rows contain the data values.
     * Used by the "report by template" feature.
     *
     * @param rs ResultSet the result set to convert
     * @return String[][] the column names and data as a 2D array
     * @throws SQLException if a database access error occurs
     */
    public static String[][] getArrayFromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData rsmd = rs.getMetaData();
        int columns = rsmd.getColumnCount();
        ArrayList rows = new ArrayList();
        ArrayList cols = new ArrayList();
        for (int i = 0; i < columns; i++) {  // for each column in result set
            cols.add(rsmd.getColumnName(i + 1));
        }
        rows.add(cols);
        rs.first();
        do {
            cols = new ArrayList();
            for (int j = 0; j < columns; j++) {
                cols.add(Misc.getString(rs, j + 1));
            }
            rows.add(cols);
        } while (rs.next());
        String[][] data = new String[rows.size()][columns];
        for (int i = 0; i < rows.size(); i++) {
            data[i] = (String[]) ((ArrayList) rows.get(i)).toArray(data[i]);
        }
        return data;
    }
}
