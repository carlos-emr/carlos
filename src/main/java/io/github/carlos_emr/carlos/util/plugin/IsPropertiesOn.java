/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.util.plugin;

import io.github.carlos_emr.CarlosProperties;

/**
 * Utility class for checking whether CARLOS EMR properties are enabled.
 * A property is considered "on" if its value is "yes", "true", or "on" (case-insensitive).
 *
 * @since 2006-01-01
 */
public class IsPropertiesOn {

    /**
     * Checks whether the named property is enabled (value is "yes", "true", or "on").
     *
     * @param proName String the property name to check
     * @return boolean true if the property is enabled
     */
    public static boolean propertiesOn(String proName) {

        CarlosProperties proper = CarlosProperties.getInstance();

        if (proper.getProperty(proName, "").equalsIgnoreCase("yes")
                || proper.getProperty(proName, "").equalsIgnoreCase("true")
                || proper.getProperty(proName, "").equalsIgnoreCase("on"))
            return true;
        else
            return false;

    }

    /**
     * Retrieves a CARLOS property value by name.
     *
     * @param proName String the property name
     * @return String the property value, or null if not set
     */
    public static String getProperty(String proName) {
        CarlosProperties proper = CarlosProperties.getInstance();
        return proper.getProperty(proName, null);
    }

    /**
     * Checks whether the CAISI (Community Approach to Integrated Services Initiative) module is enabled.
     *
     * @return boolean true if the "caisi" property is on
     */
    public static boolean isCaisiEnable() {
        return propertiesOn("caisi");
    }

    /**
     * Checks whether the program management module is enabled.
     *
     * @return boolean true if the "program" property is on
     */
    public static boolean isProgramEnable() {
        return propertiesOn("program");
    }
}
