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


package ca.openosp.openo.messenger.docxfer.util;

/**
 * Utility class for message and document transfer string processing.
 * 
 * <p>This class provides utility methods for string manipulation specific to
 * the document transfer functionality of the messaging system. It handles
 * special character escaping required for safe data transmission and storage.</p>
 * 
 * <p>The class is designed to work with XML document transfers where special
 * characters need to be properly escaped to prevent parsing errors and
 * potential security issues.</p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgCommxml
 * @see ca.openosp.openo.messenger.util.MsgStringQuote
 */
public class MsgUtil {
    /**
     * Escapes single quotes and backslashes in a string for safe SQL usage.
     * 
     * <p>This method processes a string to escape special characters that could
     * cause issues in SQL queries or string literals. It prefixes single quotes (')
     * and backslashes (\) with a backslash to neutralize their special meaning.</p>
     * 
     * <p>Example transformations:
     * <ul>
     *   <li>"O'Brien" becomes "O\'Brien"</li>
     *   <li>"C:\temp" becomes "C:\\temp"</li>
     *   <li>"It's a test\path" becomes "It\'s a test\\path"</li>
     * </ul>
     * </p>
     * 
     * <p>Note: This method provides similar functionality to MsgStringQuote.q() but
     * is specifically used in the document transfer context. Modern applications
     * should use parameterized queries instead of string escaping for better security.</p>
     * 
     * @param Expression The input string to escape
     * @return The escaped string with quotes and backslashes properly escaped
     * @deprecated Use parameterized queries or PreparedStatements for better SQL injection prevention
     */
    @Deprecated
    public static String replaceQuote(String Expression) {
        StringBuilder sb = new java.lang.StringBuilder(Expression);

        // Iterate through the string and escape special characters
        for (int i = 0; i < sb.length(); i++) {
            // Check for single quote or backslash
            if (sb.charAt(i) == '\'' || sb.charAt(i) == '\\') {
                // Insert escape backslash before the special character
                sb.insert(i, '\\');
                // Skip the inserted character in next iteration
                i++;
            }
        }
        return sb.toString();
    }
}
