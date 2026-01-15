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


/**
 * String tokenizer utility that can return empty tokens.
 * 
 * <p>This class provides similar functionality to {@link java.util.StringTokenizer}
 * but with important differences:</p>
 * <ul>
 *   <li>Can return empty tokens (consecutive delimiters produce empty strings)</li>
 *   <li>Uses a single delimiter (character or string) rather than a set of delimiters</li>
 *   <li>Preserves empty fields in delimited data</li>
 * </ul>
 * 
 * <p>Example usage with character delimiter:</p>
 * <pre>
 * StringSplitter splitter = new StringSplitter("a,b,,d", ',');
 * while (splitter.hasMoreTokens()) {
 *     String token = splitter.nextToken();
 *     // Returns: "a", "b", "", "d"
 * }
 * </pre>
 * 
 * <p>Example usage with string delimiter:</p>
 * <pre>
 * StringSplitter splitter = new StringSplitter("foo::bar::::baz", "::");
 * while (splitter.hasMoreTokens()) {
 *     String token = splitter.nextToken();
 *     // Returns: "foo", "bar", "", "baz"
 * }
 * </pre>
 */
public class StringSplitter {
    
    /** The string being split into tokens */
    String theString;
    
    /** The delimiter character (when using character-based splitting) */
    char theDelim;
    
    /** Current position in the string */
    int thePos;
    
    /** The delimiter string (when using string-based splitting), null if using character delimiter */
    String theDelimStr = null;
    
    /** Length of the delimiter (1 for character, length of string for string delimiter) */
    int theDelimLength;
    
    /**
     * Constructs a StringSplitter with a character delimiter starting at a specific position.
     * 
     * @param S the string to split
     * @param d the delimiter character
     * @param p the starting position (0-based index)
     */
    public StringSplitter(String S, char d, int p) {
        theString = S;
        theDelim = d;
        thePos = p;
        theDelimLength = 1;
        if (thePos >= theString.length()) thePos = -1;
    }

    /**
     * Constructs a StringSplitter with a character delimiter starting at position 0.
     * 
     * @param S the string to split
     * @param d the delimiter character
     */
    public StringSplitter(String S, char d) {
        this(S, d, 0);
    }

    /**
     * Constructs a StringSplitter with a string delimiter starting at a specific position.
     * 
     * @param S the string to split
     * @param d the delimiter string
     * @param p the starting position (0-based index)
     */
    public StringSplitter(String S, String d, int p) {
        theString = S;
        theDelimStr = d;
        thePos = p;
        theDelimLength = d.length();
        if (thePos >= theString.length()) thePos = -1;
    }

    /**
     * Constructs a StringSplitter with a string delimiter starting at position 0.
     * 
     * @param S the string to split
     * @param d the delimiter string
     */
    public StringSplitter(String S, String d) {
        this(S, d, 0);
    }

    /**
     * Checks if there are more tokens available.
     * 
     * @return true if there are more tokens to retrieve, false otherwise
     */
    public boolean hasMoreTokens() {
        return thePos >= 0;
    }

    /**
     * Returns the next token from the string.
     * The token is the substring from the current position up to (but not including)
     * the next occurrence of the delimiter. If no delimiter is found, returns the
     * remainder of the string.
     * 
     * @return the next token, or null if no more tokens are available
     */
    public String nextToken() {
        if (thePos < 0) return null;
        int nextPos;
        if (theDelimStr == null) nextPos = theString.indexOf(theDelim, thePos);
        else nextPos = theString.indexOf(theDelimStr, thePos);
        String R;
        if (nextPos >= 0) {
            R = theString.substring(thePos, nextPos);
            thePos = nextPos + theDelimLength;
        } else {
            R = theString.substring(thePos);
            thePos = nextPos;
        }
        return R;
    }

}
