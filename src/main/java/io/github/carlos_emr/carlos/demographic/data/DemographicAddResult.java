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


package io.github.carlos_emr.carlos.demographic.data;

import java.util.ArrayList;

/**
 * Encapsulates the result of a patient demographic record addition operation.
 *
 * <p>This class tracks whether a demographic record was successfully added to the system,
 * collects any warnings generated during the process (such as duplicate HIN or
 * matching name/DOB), and stores the assigned demographic ID on success.</p>
 *
 * <p>Warnings are accumulated when potential duplicates are detected but the record
 * is still added (e.g., added without HIN because the HIN was already in use),
 * or when the record is rejected entirely due to an exact duplicate match.</p>
 *
 * @see DemographicData#addDemographic
 * @since 2026-03-17
 */
public class DemographicAddResult {
    ArrayList<String> warnings = null;
    boolean added = false;
    String id = null;

    /**
     * Adds a warning message to the result.
     *
     * <p>Initializes the warnings list on first use. Warnings indicate issues
     * encountered during demographic addition such as duplicate records or
     * conflicting health insurance numbers.</p>
     *
     * @param str String the warning message to add
     */
    public void addWarning(String str) {

        if (warnings == null) {
            warnings = new ArrayList<String>();
        }
        warnings.add(str);
    }

    /**
     * Returns all warning messages as a String array.
     *
     * @return String[] array of warning messages, or an empty array if no warnings exist
     */
    public String[] getWarnings() {
        String[] s = {};
        if (warnings != null) {
            s = warnings.toArray(s);
        }
        return s;
    }

    /**
     * Returns the warnings as a mutable ArrayList.
     *
     * <p>Initializes the warnings list if it has not been created yet.</p>
     *
     * @return ArrayList&lt;String&gt; the list of warning messages, never null
     */
    public ArrayList<String> getWarningsCollection() {
        if (warnings == null) {
            warnings = new ArrayList<String>();
        }
        return warnings;
    }

    /**
     * Returns the demographic ID assigned to the newly added record.
     *
     * @return String the demographic number, or null if the record was not added
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the demographic ID for the newly added record.
     *
     * @param id String the demographic number assigned by the database
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Indicates whether the demographic record was successfully added.
     *
     * @return boolean true if the record was added to the database, false if rejected as duplicate
     */
    public boolean wasAdded() {
        return added;
    }

    /**
     * Sets whether the demographic record was successfully added.
     *
     * @param b boolean true if the record was persisted, false otherwise
     */
    public void setAdded(boolean b) {
        added = b;
    }
}
