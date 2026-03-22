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

package io.github.carlos_emr.carlos.caisi;

/**
 * Utility class for CAISI (Client Access to Integrated Services and Information)
 * community integration operations.
 *
 * <p>Provides helper methods for manipulating query strings used in CAISI module URLs.</p>
 *
 * @since 2005-01-19
 */
public class CaisiUtil {
    /**
     * Removes a named parameter and its value from a URL query string.
     *
     * <p>Locates the parameter by name in the query string and removes it along
     * with its value and any trailing or leading ampersand separator.</p>
     *
     * @param str String the full query string to modify
     * @param attr String the parameter name to remove (e.g. "demographicNo")
     * @return String the query string with the specified parameter removed,
     *         or {@code null} if the input string is {@code null},
     *         or the original string if the parameter is not found
     */
    public static String removeAttr(String str, String attr) {
        if (str == null) return (null);

        /*delete a parameter from query string*/
        int index, index1;
        String temps;
        index = str.indexOf(attr);
        if (index == -1) return str;
        temps = str.substring(index);
        index1 = temps.indexOf("&");
        if (index1 != -1) return str.substring(0, index) + temps.substring(index1 + 1);
        else {
            temps = str.substring(0, index);
            if (temps.endsWith("&")) return str.substring(0, index - 1);
            else return temps;
        }

    }

}
