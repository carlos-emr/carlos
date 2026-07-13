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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;


import java.util.List;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo;

/**
 * Utility class providing helper methods for measurement flowsheet operations and clinical decision support.
 * This class contains static utility methods that support measurement-related workflows,
 * particularly for flowsheet evaluation and clinical alert generation.
 * 
 * <p>The helper focuses on analyzing measurement data to determine if clinical action is required,
 * such as identifying missing measurements, out-of-range values, or overdue assessments based
 * on configured flowsheet rules and clinical guidelines.</p>
 * 
 * <p>Key functionality:</p>
 * <ul>
 *   <li>Flowsheet evaluation and warning detection</li>
 *   <li>Clinical decision support through measurement analysis</li>
 *   <li>Integration with {@link MeasurementFlowSheet} for rule-based assessments</li>
 * </ul>
 * 
 * @since 2006
 * @see MeasurementFlowSheet
 * @see MeasurementInfo
 */
public class MeasurementHelper {

    /**
     * Determines if a measurement flowsheet requires clinical attention for a patient.
     * Evaluates the patient's current measurements against flowsheet rules to identify
     * clinical warnings, missing data, or values requiring follow-up action.
     * 
     * <p>This method is commonly used in clinical dashboards and patient charts to provide
     * visual indicators when measurements need attention. It integrates with the flowsheet
     * rule engine to analyze measurement completeness and clinical significance.</p>
     * 
     * <p>Warnings may be generated for scenarios such as:</p>
     * <ul>
     *   <li>Missing required measurements for the patient's condition</li>
     *   <li>Out-of-range values requiring clinical review</li>
     *   <li>Overdue measurements based on clinical guidelines</li>
     *   <li>Abnormal trends in measurement values</li>
     * </ul>
     * 
     * @param demographic_no String the patient's demographic ID as a string
     * @param mFlowsheet MeasurementFlowSheet the configured flowsheet with clinical rules
     * @return boolean true if the flowsheet has warnings requiring clinical attention, false otherwise
     * @throws Exception if there's an error retrieving or evaluating measurement data
     * 
     * @see MeasurementFlowSheet#getMessages(MeasurementInfo)
     * @see MeasurementInfo#getMeasurements(List)
     */
    public static boolean flowSheetRequiresWork(String demographic_no, MeasurementFlowSheet mFlowsheet) throws Exception {
        MeasurementInfo mi = new MeasurementInfo(demographic_no);
        List<String> measurements = mFlowsheet.getMeasurementList();
        mi.getMeasurements(measurements);
        return mFlowsheet.getMessages(mi).getWarnings().size() != 0;
    }
}
