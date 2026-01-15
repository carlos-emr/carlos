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


package ca.openosp.openo.messenger.util;

/**
 * Utility class for escaping special characters in strings for SQL queries.
 * 
 * <p>This class provides string escaping functionality to prevent SQL injection
 * attacks by properly escaping single quotes and backslashes in user input
 * before inclusion in SQL statements.</p>
 * 
 * <p>Note: Modern applications should use parameterized queries instead of
 * string escaping for better security. This class is maintained for legacy
 * code compatibility.</p>
 * 
 * @version 1.0
 * @since 2002
 * @deprecated Use parameterized queries or PreparedStatements instead for better security
 */
@Deprecated
public class MsgStringQuote {

    /**
     * StringBuilder used for string manipulation during escaping.
     */
    private java.lang.StringBuilder stringBuffer;

    /**
     * Escapes special SQL characters in a string.
     * 
     * <p>This method escapes single quotes (') and backslashes (\) by
     * prefixing them with a backslash. This prevents SQL injection when
     * the escaped string is used in SQL queries.</p>
     * 
     * <p>Example transformations:
     * <ul>
     *   <li>"O'Brien" becomes "O\'Brien"</li>
     *   <li>"C:\temp" becomes "C:\\temp"</li>
     * </ul>
     * </p>
     * 
     * @param str The input string to escape
     * @return The escaped string safe for SQL inclusion
     */
    public String q(String str) {
        stringBuffer = new java.lang.StringBuilder();
        stringBuffer.append(str);
        for (int i = 0; i < stringBuffer.length(); i++) {
            if (stringBuffer.charAt(i) == '\'' || stringBuffer.charAt(i) == '\\') {
                stringBuffer.insert(i, '\\');
                i++;
            }
        }
        return stringBuffer.toString();
    }

}
