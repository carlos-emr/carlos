/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util;

/**
 * Utility methods for {@link StringBuilder} operations not available in the standard library.
 *
 * @since 2005-01-01
 */
public class StringBuilderUtils {

    /**
     * Performs a case-insensitive search for the target string within the StringBuilder,
     * starting at the specified index.
     *
     * @param strbuf StringBuilder the buffer to search within
     * @param target String the string to search for (case-insensitive)
     * @param start int the starting index for the search
     * @return int the index of the first occurrence, or -1 if not found
     */
    static public int indexOfIgnoreCase(StringBuilder strbuf, String target, int start) {
        String searchStr = strbuf.toString().toLowerCase();
        String lowerTarget = target.toLowerCase();
        return searchStr.indexOf(lowerTarget, start);
    }
}
