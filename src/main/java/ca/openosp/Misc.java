//CHECKSTYLE:OFF
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
 */


package ca.openosp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Miscellaneous utility class providing various string manipulation, formatting,
 * and conversion methods.
 * 
 * <p>This utility class provides a wide range of helper methods for:</p>
 * <ul>
 *   <li>HTML and JavaScript escaping for XSS protection</li>
 *   <li>String manipulation and formatting</li>
 *   <li>Phone number processing</li>
 *   <li>Money formatting and decimal point handling</li>
 *   <li>String parsing and splitting</li>
 *   <li>ResultSet data extraction</li>
 *   <li>Random number generation</li>
 *   <li>PIN encryption (legacy)</li>
 * </ul>
 * 
 * <p>This is a final utility class with a private constructor to prevent instantiation.
 * All methods are static.</p>
 * 
 * @see StringEscapeUtils for HTML escaping implementation
 */
public final class Misc {

    private Misc() {
        // prevent instantiation
    }

    /**
     * Creates a Hashtable from parallel arrays of names and values.
     * If the names array is longer than the values array, only processes up to the values array length.
     * 
     * @param names array of key names
     * @param values array of values corresponding to the keys
     * @return Hashtable containing the name-value pairs
     */
    public static Hashtable hashDefs(String[] names, String[] values) {
        Hashtable H = new Hashtable();
        if (names.length > values.length) return H;
        for (int i = 0; i < names.length; i++)
            H.put(names[i], values[i]);
        return H;
    }

    /**
     * Extracts the application name from an application root path.
     * 
     * @param sAppRootPath the full path to the application root
     * @return the extracted application name
     */
    public static String getApplicationName(String sAppRootPath) {
        int idx = sAppRootPath.lastIndexOf('/');
        sAppRootPath = sAppRootPath.substring(0, idx);
        idx = sAppRootPath.lastIndexOf('/');
        sAppRootPath = sAppRootPath.substring(idx + 1);
        idx = sAppRootPath.lastIndexOf('.');
        if (idx > 0) sAppRootPath = sAppRootPath.substring(0, idx);
        return sAppRootPath;
    }

    /**
     * Escapes HTML special characters to prevent XSS attacks.
     * Uses Apache Commons Text StringEscapeUtils for proper HTML4 escaping.
     * 
     * <p>Converts characters like &lt;, &gt;, &amp;, &quot;, and ' to their HTML entity equivalents.</p>
     * 
     * @param s the string to escape
     * @return the HTML-escaped string, safe for output in HTML contexts
     */
    public static String htmlEscape(String s) {
        return (StringEscapeUtils.escapeHtml4(s));

//		if (null == S) return S;
//		int N = S.length();
//		StringBuilder sb = new StringBuilder(N);
//		for (int i = 0; i < N; i++) {
//			char c = S.charAt(i);
//			if (c == '&') sb.append("&amp;");
//			else if (c == '"') sb.append("&quot;");
//			else if (c == '<') sb.append("&lt;");
//			else if (c == '>') sb.append("&gt;");
//			else if (c == '\'') sb.append("&#39;");
//			else sb.append(c);
//		}
//		return sb.toString();
    }

    /**
     * Escapes a specific character in a string by prefixing it with a backslash.
     * Backslashes themselves are also escaped.
     * 
     * @param S the string to escape
     * @param a the character to escape
     * @return the escaped string
     */
    public static String charEscape(String S, char a) {
        if (null == S) return S;
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '\\') sb.append("\\");
            else if (c == a) sb.append("\\" + a);
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes HTML special characters and converts newlines to HTML line breaks.
     * Useful for displaying user input that may contain line breaks in HTML.
     * 
     * @param s the string to escape
     * @return the HTML-escaped string with newlines converted to &lt;br/&gt; tags
     */
    public static String htmlJsEscape(String s) {
        return (StringEscapeUtils.escapeHtml4(s).replaceAll("\\n", "<br/>"));

//		if (null == S) return S;
//		int N = S.length();
//		StringBuilder sb = new StringBuilder(N);
//		for (int i = 0; i < N; i++) {
//			char c = S.charAt(i);
//			if (c == '&') sb.append("&amp;");
//			else if (c == '"') sb.append("&quot;");
//			else if (c == '<') sb.append("&lt;");
//			else if (c == '>') sb.append("&gt;");
//			else if (c == '\'') sb.append("&#39;");
//			else if (c == '\n') sb.append("<br>");
//			else sb.append(c);
//		}
//		return sb.toString();
    }

    /**
     * Extracts the phone number portion (without area code) from a phone number string.
     * Removes all non-digit characters and returns everything after the first 3 digits.
     * 
     * @param num the phone number string
     * @return the phone number portion (7 digits for standard North American format)
     */
    public static String phoneNumber(String num) {
        String retval = num;
        try {
            retval = cleanNumber(num).substring(3);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return retval;
    }

    /**
     * Extracts the area code (first 3 digits) from a phone number string.
     * 
     * @param num the phone number string
     * @return the area code (first 3 digits after cleaning)
     */
    public static String areaCode(String num) {
        String retval = num;
        try {
            retval = cleanNumber(num).substring(0, 3);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return retval;
    }

    /**
     * Removes all non-digit characters from a string.
     * Returns "0" if the result would be empty.
     * 
     * @param Num the string to clean
     * @return the string with only digits remaining, or "0" if empty
     */
    public static String cleanNumber(String Num) {
        Num = safeString(Num);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\D");
        java.util.regex.Matcher m = p.matcher(Num);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return (0 == sb.toString().compareTo("")) ? "0" : sb.toString();
    }

    /**
     * Safely converts a potentially null string to a non-null string.
     * 
     * @param str the string to check
     * @return the original string if not null, empty string otherwise
     */
    public static String safeString(String str) {
        return (null != str) ? str : "";
    }

    /**
     * Escapes special characters for MySQL string literals.
     * 
     * <p><strong>WARNING:</strong> This method is deprecated and should not be used.
     * Use parameterized queries (PreparedStatement) instead to prevent SQL injection.</p>
     * 
     * @param S the string to escape
     * @return the escaped string
     * @deprecated Use parameterized queries instead
     */
    @Deprecated
    public static String mysqlEscape(String S) {
        if (null == S) return S;
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '\\') sb.append("\\");
            else if (c == '\'') sb.append("\\'");
            else if (c == '\n') sb.append("\\r\\n");
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes quotes and newlines for JavaScript contexts.
     * 
     * <p><strong>Note:</strong> For better security, use OWASP Encoder's
     * {@code Encode.forJavaScript()} method instead.</p>
     * 
     * @param S the string to escape
     * @return the JavaScript-escaped string
     * @see org.owasp.encoder.Encode#forJavaScript(String)
     */
    public static String JSEscape(String S) {
        if (null == S) return S;
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            if (c == '"') sb.append("&quot;");
            else if (c == '\'') sb.append("&#39;");
            else if (c == '\n') sb.append("<br>");
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Converts a string to title case (first letter of each word capitalized).
     * Words are delimited by spaces or commas.
     * 
     * @param S the string to convert
     * @return the title-cased string, or null if input is null
     */
    public static String toUpperLowerCase(String S) {
        if (S == null) return S;
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
     * Truncates a string to a maximum length, using a default value if the string is null.
     * 
     * @param s the string to truncate
     * @param dflt the default value if s is null
     * @param nLimit the maximum length
     * @return the truncated string
     */
    public static String getShortStr(java.lang.String s, java.lang.String dflt, int nLimit) {
        if (s == null) s = dflt;
        int nLength = s.length();
        if (nLength > nLimit) {
            s = s.substring(0, nLimit);
        }
        return s;
    }

    /**
     * Joins an array of strings with a delimiter.
     * 
     * @param A the array of strings to join
     * @param S the delimiter string
     * @return the joined string, or empty string if array is null or empty
     */
    public static String stringArrayJoin(String[] A, String S) {
        if (A == null || A.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(A[0]);
        for (int i = 1; i < A.length; i++) {
            sb.append(S);
            sb.append(A[i]);
        }
        return sb.toString();
    }

    /**
     * Splits a string using a character delimiter.
     * 
     * @param S the string to split
     * @param delim the delimiter character
     * @return array of string tokens
     */
    public static String[] stringSplit(String S, char delim) {
        Vector V = new Vector();
        StringSplitter SS = new StringSplitter(S, delim);
        while (SS.hasMoreTokens())
            V.addElement(SS.nextToken());
        return vectorToStringArray(V);
    }

    /**
     * Splits a string using a string delimiter.
     * 
     * @param S the string to split
     * @param delim the delimiter string
     * @return array of string tokens
     */
    public static String[] stringSplit(String S, String delim) {
        Vector V = new Vector();
        StringSplitter SS = new StringSplitter(S, delim);
        while (SS.hasMoreTokens())
            V.addElement(SS.nextToken());
        return vectorToStringArray(V);
    }

    /**
     * Splits a string using the first character as the delimiter.
     * 
     * @param S the string to split; first character is used as delimiter
     * @return array of string tokens (excluding the delimiter character itself)
     */
    public static String[] stringSplit(String S) { // delim==S[0]
        if (S == null || S.length() == 0) return new String[0];
        char delim = S.charAt(0);
        Vector V = new Vector();
        StringSplitter SS = new StringSplitter(S, delim, 1);
        while (SS.hasMoreTokens())
            V.addElement(SS.nextToken());
        return vectorToStringArray(V);
    }

    /**
     * Parses a delimited string into a Hashtable of key-value pairs.
     * The first character of the string is used as the delimiter.
     * Format: "delimKeyDelimValueDelimKey2DelimValue2..."
     * 
     * <p>Example: "xjoexSchmoexagex42" with delimiter 'x' produces
     * {joe="Schmoe", age="42"}</p>
     * 
     * @param S the delimited string (first character is delimiter)
     * @return Hashtable containing the parsed key-value pairs
     */
    public static Hashtable splitDelimHash(String S) {// delim=S[0]
        // S="xjoexSchmoexagex42xcommentsxxIQx42"
        // becomes joe="Schmoe",age="42",comments="",IQ="42"
        Hashtable H = new Hashtable(1);
        if (S == null || S.length() == 0) return H;
        char delim = S.charAt(0);
        StringSplitter SS = new StringSplitter(S, delim, 1);
        while (SS.hasMoreTokens()) {
            String k = SS.nextToken();
            if (SS.hasMoreTokens()) H.put(k, evalQuotedChars(SS.nextToken()));
        }
        return H;
    }

    /**
     * Performs delimiter-based string substitution using a Dict for replacements.
     * Replaces all occurrences of delimited keys with their values from the dictionary.
     * 
     * @param S the string containing delimited keys
     * @param d the delimiter string
     * @param defs the Dict containing key-value definitions
     * @return the string with substitutions applied
     */
    public static String stringDelimSubst(String S, String d, Dict defs) {
        // S contains keys, beginning and ending with copies of delim;
        // result is to be that of replacing these with their values
        String[] A = stringSplit(S, d);
        for (int i = 1; i < A.length; i += 2)
            A[i] = defs.getDef(A[i]);
        return stringArrayJoin(A, "");
    }

    /**
     * Performs delimiter-based string substitution using a Hashtable for replacements.
     * Replaces all occurrences of delimited keys with their values from the hashtable.
     * 
     * @param S the string containing delimited keys
     * @param d the delimiter string
     * @param defs the Hashtable containing key-value definitions
     * @return the string with substitutions applied
     */
    public static String stringDelimSubst(String S, String d, Hashtable defs) {
        // S contains keys, beginning and ending with copies of delim;
        // result is to be that of replacing these with their values
        String[] A = stringSplit(S, d);
        for (int i = 1; i < A.length; i += 2)
            A[i] = (String) defs.get(A[i]);
        return stringArrayJoin(A, "");
    }

    /*
     * public static String substFile(String fName, String fDelim, String defs){ Hashtable dict=splitDelimHash(defs); if(dict==null)return("no definitions for "+fName+" in "+defs); return stringDelimSubst(MiscFile.fileToString(fName),fDelim,dict); }
     */
    /**
     * Creates an indentation string of spaces based on the level.
     * 
     * @param Level the indentation level (2 spaces per level)
     * @return string of spaces for indentation
     */
    public static String indent(int Level) {
        String S = "";
        while (0 < Level--)
            S += "  ";
        return S;
    }

    /**
     * Parses a string to an integer with a default value for parsing errors.
     * 
     * @param S the string to parse
     * @param dval the default value if parsing fails or S is null
     * @return the parsed integer or default value
     */
    public static int getInt(String S, int dval) {
        if (S == null) return dval;
        try {
            int N = Integer.parseInt(S);
            return N;
        } catch (Exception e) {
            return dval;
        }
    }

    /**
     * Returns the string if not null, otherwise returns the default value.
     * 
     * @param S the string to check
     * @param dval the default value if S is null
     * @return S if not null, otherwise dval
     */
    public static String getStr(String S, String dval) {
        if (S == null) return dval;
        return S;
    }

    /**
     * Processes escape sequences in a string (removes backslash escapes).
     * 
     * @param S the string containing escape sequences
     * @return the string with escape sequences processed
     */
    public static String evalQuotedChars(String S) {
        String R = "";
        for (int i = 0; i < S.length(); i++) {
            char c = S.charAt(i);
            if (c != '\\') R += "" + c;
            else {
                i++;
                R += "" + S.charAt(i);
            }
        }
        return R;
    }

    /**
     * Adds backslash escapes before specified special characters.
     * 
     * @param S the string to escape
     * @param specials string containing all characters that should be escaped
     * @return the escaped string
     */
    public static String quoteSpecialChars(String S, String specials) {
        String R = ""; // should use stringbuffer for efficiency?
        for (int i = 0; i < S.length(); i++) {
            char c = S.charAt(i);
            if (specials.indexOf(c) >= 0) R += "\\" + c;
            else R += "" + c;
        }
        return R;

    }

    /**
     * Converts a Hashtable to an attribute string format.
     * Format: key1="value1" key2="value2" ...
     * Special characters in values are escaped with backslash.
     * 
     * @param H the Hashtable containing key-value pairs
     * @return the formatted attribute string
     */
    public static String hashAttribString(Hashtable H) {
        // returns the attribute string joe="schmoe" john="smith" &c.
        Enumeration KK = H.keys();
        String S = "";
        String specialChars = "\\\"";
        while (KK.hasMoreElements()) {
            String k = (String) KK.nextElement();
            String v = (String) H.get(k);
            S += " " + k + "=\"" + quoteSpecialChars(v, specialChars) + "\"";
        }
        return S;
    }

    /**
     * Parses an attribute string into a Hashtable.
     * Supports quotes: ", ', or any character following =
     * Format: key1="value1" key2='value2' ...
     * String must be delimited by blanks.
     * 
     * @param S the attribute string to parse
     * @return Hashtable containing the parsed attributes
     */
    public static Hashtable attribStringHash(String S) {
        // interprets the attribute string joe="schmoe" john="smith" &c.
        // or joe='schmoe' john='smith' &c.
        // or even joe=qschmoeq john=qsmithq &c
        // but closing "quote" is required, and the string must be
        // _delimited_ by blanks; no error checking yet.
        Hashtable H = new Hashtable();
        int loc = 0;
        int lim = S.length();
        while (loc < lim && ' ' == S.charAt(loc))
            loc++;
        while (loc < lim) { // pointing, e.g., at john="smith"
            int eqLoc = S.indexOf("=", loc);
            if (eqLoc < 0) return H;
            String k = S.substring(loc, eqLoc);
            char q = S.charAt(eqLoc + 1);
            int endLoc = eqLoc + 2;
            char c;
            while (endLoc < lim && (c = S.charAt(endLoc)) != q)
                if (c == '\\') endLoc += 2;
                else endLoc++;
            if (endLoc > lim) return H; // no closing quote
            String v = S.substring(eqLoc + 2, endLoc);
            H.put(k, evalQuotedChars(v));
            loc = endLoc + 2;
            while ((loc < lim) && ' ' == S.charAt(loc))
                loc++;
        }
        return H;
    }

    /**
     * Removes line break characters (\n and \r) from a string, replacing them with spaces.
     * 
     * @param input the string to process
     * @return the string with line breaks removed, or null if input is null
     */
    public static String stripLineBreaks(String input) {
        if (input != null) {
            input = input.replaceAll("\\n", " ").replaceAll("\\r", "");
        }
        return input;
    }

    /**
     * Inserts a decimal point two places from the right in a numeric string.
     * For converting cents to dollars (e.g., "1234" becomes "12.34").
     * 
     * @param input the numeric string representing cents
     * @return the formatted string with decimal point, or "0.00" if conversion fails
     */
    public static String insertDecimalPoint(String input) {
        String moneyStr = "0.00";
        try {
            moneyStr = new java.math.BigDecimal(input).movePointLeft(2).toString();
        } catch (Exception moneyException) {
            MiscUtils.getLogger().error("Error", moneyException);
        }
        return moneyStr;
    }

    /**
     * Returns the default value if check equals checkAgainst, otherwise returns check.
     * 
     * @param check the value to check
     * @param checkAgainst the value to compare against
     * @param defaultValue the default value to return if they match
     * @return check if different from checkAgainst, otherwise defaultValue
     */
    public static String check(String check, String checkAgainst, String defaultValue) {
        return ((check == checkAgainst) ? defaultValue : check);
    }

    /**
     * Returns the default value if check is null, otherwise returns check.
     * 
     * @param check the value to check
     * @param defaultValue the default value to return if check is null
     * @return check if not null, otherwise defaultValue
     */
    public static String check(String check, String defaultValue) {
        return check(check, null, defaultValue);
    }

    /**
     * Converts a Vector to a String array.
     * 
     * @param V the Vector containing String elements
     * @return array of strings from the vector
     */
    public static String[] vectorToStringArray(Vector V) {
        String[] S = new String[V.size()];
        for (int i = 0; i < S.length; i++)
            S[i] = (String) V.elementAt(i);
        return S;
    }

    /**
     * Extracts a column from a 2D string matrix.
     * 
     * @param N the column index to extract
     * @param matrix the 2D string array
     * @return array containing the Nth column elements (null for rows shorter than N)
     */
    public static String[] column(int N, String[][] matrix) {
        String[] col = new String[matrix.length];
        for (int i = 0; i < col.length; i++) {
            String[] row = matrix[i];
            if (row.length > N) col[i] = row[N]; // otherwise null
        }
        return col;
    }

    /**
     * Removes newline characters (CR and LF) from a string, replacing them with spaces.
     * 
     * @param str the string to process
     * @return the string with newlines replaced by spaces
     */
    public static String removeNewLine(String str) {
        StringBuilder stringBuffer = new java.lang.StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            int a = str.charAt(i);
            if (a == 13 || a == 10) {
                stringBuffer.append(" ");
            } else {
                stringBuffer.append((char) a);
            }
        }
        return stringBuffer.toString();
    }

    // /

    /**
     * Creates a string of spaces with the specified length.
     * 
     * @param i the number of spaces
     * @return string containing i spaces
     */
    public static String space(int i) {
        String returnValue = new String();
        for (int j = 0; j < i; j++) {
            returnValue += " ";
        }
        return returnValue;
    }

    /**
     * Pads a string with trailing spaces to reach the specified length, then truncates if needed.
     * 
     * @param y the string to pad
     * @param i the target length
     * @return the padded and/or truncated string
     */
    public static String backwardSpace(String y, int i) {
        String returnValue = new String();
        y = safeString(y);
        for (int j = y.length(); j < i; j++) {
            returnValue += " ";
        }
        return cutBackString(y + returnValue, i);
    }

    /**
     * Creates a string of zeros with the specified length.
     * 
     * @param x the number of zeros
     * @return string containing x zeros
     */
    public static String zero(int x) {
        String returnZeroValue = new String();
        for (int y = 0; y < x; y++) {
            returnZeroValue += "0";
        }
        return returnZeroValue;
    }

    /**
     * Pads a string with leading zeros to reach the specified length, then truncates from the left if needed.
     * 
     * @param y the string to pad
     * @param x the target length
     * @return the zero-padded string, keeping only the rightmost x characters
     */
    public static String forwardZero(String y, int x) {
        String returnZeroValue = new String();
        y = safeString(y);
        for (int i = y.length(); i < x; i++) {
            returnZeroValue += "0";
        }
        return cutFrontString(returnZeroValue + y, x);
    }

    /**
     * Pads a string with trailing zeros to reach the specified length, then truncates if needed.
     * 
     * @param y the string to pad
     * @param i the target length
     * @return the zero-padded and/or truncated string
     */
    public static String backwardZero(String y, int i) {
        String returnValue = new String();
        y = safeString(y);
        for (int j = y.length(); j < i; j++) {
            returnValue += "0";
        }
        return cutBackString(y + returnValue, i);
    }

    /**
     * Returns the last len characters of a string.
     * 
     * @param str the string to extract from
     * @param len the number of characters to extract from the end
     * @return the last len characters
     */
    public static String cutFrontString(String str, int len) {
        return str.substring(str.length() - len, str.length());
    }

    /**
     * Returns the first len characters of a string.
     * If the string is shorter than len, returns the entire string.
     * 
     * @param str the string to extract from
     * @param len the number of characters to extract from the beginning
     * @return the first len characters (or entire string if shorter)
     */
    public static String cutBackString(String str, int len) {
        if (str != null && str.length() < len) {
            return str;
        }
        return str.substring(0, len);
    }

    /**
     * Pads a string with leading spaces to reach the specified length, then truncates from the left if needed.
     * 
     * @param y the string to pad
     * @param x the target length
     * @return the space-padded string, keeping only the rightmost x characters
     */
    public static String forwardSpace(String y, int x) {
        String returnZeroValue = new String();
        y = safeString(y);
        for (int i = y.length(); i < x; i++) {
            returnZeroValue += " ";
        }
        return cutFrontString(returnZeroValue + y, x);
    }

    /**
     * Formats a money string by removing decimal point and padding with leading zeros.
     * For converting dollar amounts to cents format.
     * 
     * @param y the money string (e.g., "12.34")
     * @param x the target length
     * @return zero-padded string without decimal point, or all zeros if conversion fails
     */
    public static String moneyFormatPaddedZeroNoDecimal(String y, int x) {
        String returnZeroValue = "";
        try {
            returnZeroValue = forwardZero(y.replaceAll("\\.", ""), x);
        } catch (Exception e2) {
            returnZeroValue = zero(x);
        }
        return cutFrontString(returnZeroValue, x);
    }

    /**
     * Formats a numeric string as money by moving the decimal point two places left.
     * For converting cents to dollar format (e.g., "1234" becomes "12.34").
     * 
     * @param str the numeric string representing cents
     * @return the formatted money string, or "0.00" if conversion fails
     */
    public static String moneyFormat(String str) {
        String moneyStr = "0.00";
        try {
            moneyStr = new java.math.BigDecimal(str).movePointLeft(2).toString();
        } catch (Exception moneyException) {
        }
        return moneyStr;
    }

    /**
     * Gets a string value from a ResultSet by column name, returning empty string if null.
     * 
     * @param rs the ResultSet to extract from
     * @param columnName the name of the column
     * @return the trimmed string value, or empty string if null
     * @throws SQLException if column access fails
     */
    public static String getString(ResultSet rs, String columnName) throws SQLException {
        return (StringUtils.trimToEmpty(rs.getString(columnName)));

//		String text = rs.getString(columnName);
//		if (rs.wasNull()) {
//			text = "";
//		}
//		return text;
    }

    /**
     * Gets a string value from a ResultSet by column index, returning empty string if null.
     * 
     * @param rs the ResultSet to extract from
     * @param columnIndex the 1-based column index
     * @return the trimmed string value, or empty string if null
     * @throws SQLException if column access fails
     */
    public static String getString(ResultSet rs, int columnIndex) throws SQLException {
        return (StringUtils.trimToEmpty(rs.getString(columnIndex)));

//		String text = rs.getString(columnIndex);
//		if (rs.wasNull()) {
//			text = "";
//		}
//		return text;
    }

    /**
     * Safely converts an Object to a String, returning empty string if null.
     * 
     * @param s the object to convert
     * @return the string value, or empty string if null
     */
    public static String getString(Object s) {
        if (s == null) return "";
        return (String) s;
    }

    /**
     * Replaces all occurrences of a pattern string with a replacement string.
     * 
     * @param str the string to process
     * @param pattern the pattern to find
     * @param replaceTo the replacement string
     * @return the string with replacements applied
     */
    public static String replace(String str, String pattern, String replaceTo) {
        String[] buff = str.split(pattern);
        StringBuilder sb = new StringBuilder();

        sb.append(buff[0]);
        for (int i = 1; i < buff.length; i++) {
            sb.append(replaceTo);
            sb.append(buff[i]);
        }
        if (str.endsWith(pattern)) sb.append(replaceTo);

        return sb.toString();
    }

    /**
     * Converts an Object to a JavaScript-safe string by escaping quotes.
     * Returns empty string if the object is null.
     * 
     * @param s the object to convert
     * @return the JavaScript-escaped string
     */
    public static String getStringJs(Object s) {
        if (s == null) return "";
        String s1 = replace((String) s, "'", "\\'");
        return replace(s1, "\"", "&#34;");
        // return ((String) s).replace("'", "\\'");
    }

    /**
     * Encrypts a PIN using a simple character-based algorithm.
     * 
     * <p><strong>WARNING:</strong> This is a weak encryption method and should not be used
     * for securing sensitive data. Use proper encryption methods like BCrypt or Argon2
     * for password hashing.</p>
     * 
     * @param sPin the PIN to encrypt
     * @return the encrypted PIN, or null if input is null
     * @deprecated Use proper password hashing algorithms (BCrypt, Argon2) instead
     */
    @Deprecated
    public static String encryptPIN(String sPin) {
        StringBuilder sb = new StringBuilder();
        int i, j;
        if (sPin == null) return null;

        j = 0;
        for (i = 0; i < sPin.length(); i++) {
            char c = sPin.charAt(i);
            j = j + c;
            if (j > 127) j = j - 127;
            sb.append((char) j);
        }
        return sb.toString();
    }

    /**
     * Generates a random number with the specified number of digits.
     * The number is left-padded with zeros if needed.
     * 
     * @param digits the number of digits for the random number
     * @return a string containing the random number
     */
    public static String getRandomNumber(int digits) {
        int max = (int) Math.pow(10, digits) - 1;
        java.util.Date dt = new java.util.Date();
        long seed = dt.getTime();
        java.util.Random rnd = new java.util.Random(seed);
        int rn = rnd.nextInt(max);
        return forwardZero(String.valueOf(rn), digits);
    }
}
