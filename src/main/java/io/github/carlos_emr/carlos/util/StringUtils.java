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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
/**
 * String utility class providing methods for truncation, splitting, joining, null-safe
 * comparison, numeric/date validation, line break normalization, and CSV generation.
 *
 * @since 2001-01-01
 */
public class StringUtils {

    private static Logger logger = MiscUtils.getLogger();
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
     * Splits a string by the specified delimiter and returns the tokens as a Vector.
     *
     * @param str String the string to split
     * @param delimeter String the delimiter characters
     * @return Vector of String tokens
     */
    public static Vector splitString(String str, String delimeter) {
        Vector result = new Vector();
        StringTokenizer st = new StringTokenizer(str, delimeter);

        while (st.hasMoreTokens()) {
            result.addElement(st.nextToken());

        }
        return result;
    }

    /**
     * Returns an empty string if the input is null, otherwise returns the input unchanged.
     *
     * @param value String the input value (may be null)
     * @return String the input or an empty string
     */
    public static String transformNullInEmptyString(String value) {
        return ((value == null) ? "" : value);
    }

    /**
     * Returns the replacement string if the input is null or empty, otherwise returns the input.
     *
     * @param value String the input value (may be null or empty)
     * @param str String the replacement value
     * @return String the input or the replacement
     */
    public static String transformNullInOtherString(String value, String str) {
        return (((value == null) || value.equals("")) ? str : value);
    }

    /**
     * Generates a string of {@code n} space characters.
     *
     * @param n int the number of spaces
     * @return String the space-padded string
     */
    public static String preencheBranco(int n) {
        String espaco = "";

        for (int i = 0; i < n; i++) {
            espaco = espaco + " ";
        }

        return espaco;
    }

    /**
     * Generates a left-padded string by repeating character {@code c} {@code n} times,
     * followed by the suffix {@code c1}.
     *
     * @param n int the number of padding characters
     * @param c String the padding character
     * @param c1 String the suffix to append after padding
     * @return String the padded string
     */
    public static String preenchimentoEsquerda(int n, String c, String c1) {
        String result = "";

        for (int i = 0; i < n; i++) {
            result = result + c;
        }

        return result + c1;
    }

    /**
     * Generates a string by repeating character {@code c} {@code n} times.
     *
     * @param n int the number of repetitions
     * @param c String the character to repeat
     * @return String the repeated string
     */
    public static String preenchimento(int n, String c) {
        String result = "";

        for (int i = 0; i < n; i++) {
            result = result + c;
        }

        return result;
    }

    /**
     * Checks whether a string is null, empty, or the literal string "null" (case-insensitive).
     *
     * @param obj String the string to check
     * @return boolean true if the string is null, empty, or "null"
     */
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
     * Replaces all occurrences of a character in a string. Null-safe.
     *
     * @param oldChar char the character to replace
     * @param newChar char the replacement character
     * @param word String the input string (may be null)
     * @return String the modified string, or null if input is null
     */
    public static String replaceChar(char oldChar, char newChar, String word) {
        return ((word == null) ? null : word.replace(oldChar, newChar));
    }

    /**
     * Joins an array of ID strings into a comma-separated string suitable for SQL IN clauses.
     *
     * @param ids String[] the array of IDs
     * @return String the comma-separated string
     */
    public static String getStrIn(String[] ids) {
        String id = "";

        for (int i = 0; i < ids.length; i++) {
            if (i == 0) {
                id = ids[i];
            } else {
                id = id + "," + ids[i];
            }
        }

        return id;
    }

    /**
     * Returns true if the provided string is a numeral
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
     * Returns true if the provided string is an integer
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
     * Returns the substring up to (but not including) the first occurrence of a delimiter.
     *
     * @param str String the input string
     * @param firstChar String the delimiter to search for
     * @return String the substring before the delimiter, or the original string if not found
     */
    public static String returnStringToFirst(String str, String firstChar) {
        String ret = str;
        if (str != null) {
            int i = str.indexOf(firstChar);
            if (i != -1) {
                ret = str.substring(0, i);
            }
        }
        return ret;
    }

    /**
     * Returns true if the specified String represents a valid date
     *
     * @param dateString String
     * @param format     String
     * @return boolean
     */
    public static boolean isValidDate(String dateString, String format) {
        boolean ret = false;
        SimpleDateFormat fmt = new SimpleDateFormat(format);
        try {
            fmt.parse(dateString);
            ret = true;
        } catch (ParseException ex) {
            MiscUtils.getLogger().error("Looks bad, too bad original author didn't document how bad", ex);
        }
        return ret;

    }

    /**
     * Joins an array of strings into a single string separated by the specified delimiter.
     *
     * @param strArray String[] the array to join
     * @param delimiter String the separator between elements
     * @return String the joined string
     */
    public static String join(String[] strArray, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (int i = 0, arrayLength = strArray.length; i < arrayLength; i++) {
            result.append(strArray[i]);
            if (i < arrayLength - 1) {
                result.append(delimiter);
            }
        }
        return result.toString();
    }

    /**
     * Joins a list of objects into a single string separated by the specified delimiter.
     *
     * @param strArray List the list to join (elements must be String-compatible)
     * @param delimiter String the separator between elements
     * @return String the joined string
     */
    public static String join(List strArray, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (int i = 0, arrayLength = strArray.size(); i < arrayLength; i++) {
            result.append(strArray.get(i));
            if (i < arrayLength - 1) {
                result.append(delimiter);
            }
        }
        return result.toString();
    }

    /**
     * Splits a string by the specified delimiter and returns the tokens as an ArrayList.
     *
     * @param rawString String the string to split
     * @param delimiter String the delimiter characters
     * @return ArrayList of String tokens
     */
    public static ArrayList<String> split(String rawString, String delimiter) {
        ArrayList<String> result = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(rawString, delimiter);

        while (st.hasMoreTokens()) {
            result.add(st.nextToken());

        }
        return result;
    }

    /**
     * Splits a string by the specified delimiter and returns the tokens as a String array.
     *
     * @param rawString String the string to split
     * @param delimiter String the delimiter characters
     * @return String[] the array of tokens
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
     * Takes a list of String Objects and returns a String with the all values from the list separated by a comma
     */
    public static String getCSV(List l) {
        StringBuilder ret = new StringBuilder();
        if (l != null) {
            for (int i = 0; i < l.size(); i++) {
                ret.append((String) l.get(i));
                if (i + 1 < l.size()) {
                    ret.append(",");
                }
            }
        }
        return ret.toString();
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
     * Null-safe string equality check.
     *
     * @param s1 String the first string (may be null)
     * @param s2 String the second string (may be null)
     * @return boolean true if both are null or both are equal
     */
    public static boolean nullSafeEquals(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 != null)
            return s1.equals(s2);
        else
            return s2 == null;

    }

    public static boolean nullSafeEqualsIgnoreCase(String s1, String s2) {
        return nullSafeEquals(s1.toUpperCase(), s2.toUpperCase());
    }

    public static boolean containsIgnoreCase(String text, String searchWord) {
        if (text == null || searchWord == null) return false;

        text = text.toUpperCase();
        searchWord = searchWord.toUpperCase();

        return text.contains(searchWord);
    }

    static public String noNull(String maybeNullText) {
        return filled(maybeNullText) ? maybeNullText : "";
    }

    static public boolean empty(String s) {
        return isNullOrEmpty(s);
    }

    static public boolean filled(String s) {
        return !isNullOrEmpty(s);
    }
}
