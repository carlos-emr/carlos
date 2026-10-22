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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * @author Jay Gallagher
 */
public class DemographicNameAgeString {

    static DemographicNameAgeString demographicNameAgeString = new DemographicNameAgeString();


    /**
     * Used to obtain an instance of DemographicNameAgeString
     *
     * @return Returns instance of DemographicNameAgeString
     */
    public static DemographicNameAgeString getInstance() {
        return demographicNameAgeString;
    }

    private DemographicNameAgeString() {
    }


    /**
     * Used to get a String containing Name Age Sex ie "Gallagher, Jay M 25 years"
     * <p>
     * Values are resolved for the current caller on every lookup.
     *
     * @param demoNo Demographic Number
     * @return returns a String containing name age sex ie "Last, First M 2 weeks"
     */
    public String getNameAgeString(LoggedInInfo loggedInInfo, Integer demoNo) {

        String retval = "";
        if (demoNo != null) {
            DemographicData dData = new DemographicData();
            String[] dArray = dData.getNameAgeSexArray(loggedInInfo, demoNo);
                if (dArray != null) {
                    retval = nameAgeSexString(dArray);
                }

        }
        return retval;
    }

    /**
     * Retained for callers that invalidate demographic labels after an update.
     * Labels are no longer cached, so subsequent reads always resolve current data and access.
     *
     * @param demoNo demographic number whose label changed
     * @deprecated labels are resolved on every read; no invalidation is necessary
     */
    @Deprecated
    public static void resetDemographic(String demoNo) {
        // Compatibility entry point; there is no cross-user cache to invalidate.
    }

    private String nameAgeSexString(String[] s) {
        return s[0] + ", " + s[1] + " " + s[2] + " " + s[3];
    }

    /**
     * Resolves current patient label fields through the caller's authorized demographic lookup.
     * No patient data is shared between callers or retained across updates and locale changes.
     *
     * @param loggedInInfo current authenticated caller
     * @param demoNo positive demographic number
     * @return lastName, firstName, sex and age fields, or an empty map for an invalid or absent patient
     *
     * @throws SecurityException if the current caller cannot access the patient
     */
    public Map<String, String> getNameAgeSexHashtable(LoggedInInfo loggedInInfo, String demoNo) {
        if (demoNo == null || !demoNo.matches("\\d{1,10}")) return Collections.emptyMap();
        int id;
        try {
            id = Integer.parseInt(demoNo);
        } catch (NumberFormatException _) {
            return Collections.emptyMap();
        }
        if (id <= 0) return Collections.emptyMap();
        // Patient access and locale must be evaluated for this caller, even if another
        // caller previously requested the same ID or the demographic record has changed.
        String[] values = new DemographicData().getNameAgeSexArray(loggedInInfo, id);
        if (values == null) return Collections.emptyMap();
        Map<String, String> label = new HashMap<>();
        label.put("lastName", values[0]);
        label.put("firstName", values[1]);
        label.put("sex", values[2]);
        label.put("age", values[3]);
        return label;
    }
}
