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


package io.github.carlos_emr.carlos.casemgmt.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry for {@link ExtPrint} extension implementations. Maps human-readable
 * extension names to their Spring bean names, enabling dynamic discovery and invocation
 * of custom print sections during patient chart PDF generation.
 *
 * <p>Entries are stored in insertion order via a {@link LinkedHashMap} to ensure
 * predictable rendering order of print extensions.</p>
 *
 * @see ExtPrint
 * @see io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrintPdf
 * @since 2026-03-17
 */
public class ExtPrintRegistry {

    static LinkedHashMap<String, String> entries = new LinkedHashMap<String, String>();

    /**
     * Registers a print extension by mapping its display name to its Spring bean name.
     *
     * @param name String the human-readable name for the extension
     * @param beanName String the Spring bean name of the {@link ExtPrint} implementation
     */
    public static void addEntry(String name, String beanName) {
        entries.put(name, beanName);
    }

    /**
     * Returns all registered print extensions as a name-to-bean-name map in insertion order.
     *
     * @return Map&lt;String, String&gt; the registered extensions
     */
    public static Map<String, String> getEntries() {
        return entries;
    }

    /**
     * Retrieves the Spring bean name for the specified print extension.
     *
     * @param name String the human-readable extension name
     * @return String the Spring bean name, or null if not registered
     */
    public static String getEntry(String name) {
        return entries.get(name);
    }
}
