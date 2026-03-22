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
import java.util.Hashtable;
import java.util.Map;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Singleton cache for formatted patient name, age, and sex strings.
 *
 * <p>Provides efficient access to "LastName, FirstName Sex Age" formatted strings
 * by caching the underlying data in a Hashtable keyed by demographic number.
 * This avoids repeated database lookups when displaying patient identifiers
 * across multiple UI components.</p>
 *
 * <p>Use {@link #resetDemographic(String)} to invalidate the cache entry when
 * a patient's demographic information is updated.</p>
 *
 * @see DemographicData
 * @since 2026-03-17
 */
public class DemographicNameAgeString {

    static DemographicNameAgeString demographicNameAgeString = new DemographicNameAgeString();
   static Hashtable<String, String[]> hashtable = new Hashtable<>();


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
     * Value is buffered in a hashtable so that it is only retrieved from the database once.
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
     * Removes demographic number from hashtable. Used if you update the demographic information
     *
     * @param demoNo Demoraphic Number
     */
    public static void resetDemographic(String demoNo) {
      if (demoNo != null) {
            hashtable.remove(demoNo);
        }
    }

    private String nameAgeSexString(String[] s) {
        return s[0] + ", " + s[1] + " " + s[2] + " " + s[3];
    }

    /**
     * Used to get a Hashtable containing  lastName, firstName, sex, age
     *
     * @param demoNo Demographic Number
     * @return Hashtable with these fields:
     * "lastName"
     * "firstName"
     * "sex"
     * "age"
     */
    public Map<String, String> getNameAgeSexHashtable(LoggedInInfo loggedInInfo, String demoNo){
      Map<String, String> h = Collections.emptyMap();

        if (demoNo != null) {
            if (!hashtable.containsKey(demoNo)) {

                DemographicData dData = new DemographicData();
                String[] dArray = dData.getNameAgeSexArray(loggedInInfo, Integer.valueOf(demoNo));
                if (dArray != null) {
                    hashtable.put(demoNo, dArray);
                }
            }//else{MiscUtils.getLogger().debug("name age in buffer "+demoNo);}

         String[] s = hashtable.get(demoNo);
            if (s != null) {
            h = new HashMap<>();
                h.put("lastName", s[0]);
                h.put("firstName", s[1]);
                h.put("sex", s[2]);
                h.put("age", s[3]);
            }
        }
        return h;
    }

}
