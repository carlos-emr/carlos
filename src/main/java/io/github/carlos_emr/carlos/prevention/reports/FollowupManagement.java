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


package io.github.carlos_emr.carlos.prevention.reports;

import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.WriteNewMeasurements;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Manages the recording of prevention follow-up procedures (letters and phone calls)
 * as clinical measurements.
 *
 * <p>Writes follow-up contact records (L1=Letter 1, L2=Letter 2, P1=Phone Call 1)
 * to the measurements system for tracking patient outreach in prevention compliance
 * workflows. Each follow-up type corresponds to a measurement type code
 * (e.g., PAPF, FLUF, MAMF, FOBF, CIMF).</p>
 *
 * @since 2001-2002
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.WriteNewMeasurements
 */
public class FollowupManagement {
    private static Logger log = MiscUtils.getLogger();
    final public String LETTER1 = "L1";
    final public String LETTER2 = "L2";
    final public String PHONE1 = "P1";

    /**
     * Creates a new instance of FollowupManagement
     */
    public FollowupManagement() {
    }


    /**
     * Class used to mark prevention follow up procedures
     *
     * @param followUpType    --  procedure type eg PAPF,FLUF,MAMF,FOBF,CIMF
     * @param followUpValue   --  proedure value eg L1,L2,P1 letter 1, letter 2, phone call 1
     * @param demographicList --  list of demographic numbers as strings
     * @param providerNo      --  provider # generating the list
     * @param dateObserved    --  date list is generated
     * @param comment         -- comment
     */
    public void markFollowupProcedure(String followUpType, String followUpValue, List demographicList, String providerNo, Date dateObserved, String comment) {
        for (int i = 0; i < demographicList.size(); i++) {
            String demographicNo = (String) demographicList.get(i);
            writeProcedure(followUpType, followUpValue, demographicNo, providerNo, dateObserved, comment);
        }

    }

    /**
     * Class used to mark prevention follow up procedures
     *
     * @param followUpType    --  procedure type eg PAPF,FLUF,MAMF,FOBF,CIMF
     * @param followUpValue   --  proedure value eg L1,L2,P1 letter 1, letter 2, phone call 1
     * @param demographicList --  list of demographic numbers as strings
     * @param providerNo      --  provider # generating the list
     * @param dateObserved    --  date list is generated
     * @param comment         -- comment
     */
    public void markFollowupProcedure(String followUpType, String followUpValue, String[] demographicList, String providerNo, Date dateObserved, String comment) {
        for (int i = 0; i < demographicList.length; i++) {
            writeProcedure(followUpType, followUpValue, demographicList[i], providerNo, dateObserved, comment);

        }

    }

    private void writeProcedure(final String followUpType, final String followUpValue, final String demographicNo, final String providerNo, final Date dateObserved, final String comment) {
        log.debug("Calling WriteProcedure for " + demographicNo);
        Hashtable measure = new Hashtable();
        measure.put("value", followUpValue);
        measure.put("type", followUpType);
        measure.put("measuringInstruction", "");
        measure.put("comments", comment == null ? "" : comment);
        measure.put("dateObserved", UtilDateUtilities.DateToString(dateObserved, "yyyy-MM-dd HH:mm:ss"));
        measure.put("dateEntered", UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd HH:mm:ss"));
        WriteNewMeasurements.write(measure, demographicNo, providerNo);
    }


}
