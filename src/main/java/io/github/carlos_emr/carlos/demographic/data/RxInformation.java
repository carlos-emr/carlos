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

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;

/**
 * Provides access to a patient's current medication list and active allergies.
 *
 * <p>This class retrieves prescription and allergy data for a given patient
 * demographic number, formatting the results as newline-separated text strings
 * suitable for display in clinical encounter forms or export documents.</p>
 *
 * @see io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData
 * @see io.github.carlos_emr.carlos.prescript.data.RxPatientData
 * @since 2026-03-17
 */
public class RxInformation {
    private String currentMedication;
    private String allergies;

    /**
     * Retrieves the patient's current active medications as a newline-separated string.
     *
     * <p>Filters prescriptions to only include those marked as current, and formats
     * each as a full outline with semicolons replaced by spaces.</p>
     *
     * @param demographic_no String the patient demographic number
     * @return String newline-separated list of current medications
     */
    public String getCurrentMedication(String demographic_no) {
        RxPrescriptionData prescriptData = new RxPrescriptionData();
        RxPrescriptionData.Prescription[] arr = {};
        arr = prescriptData.getUniquePrescriptionsByPatient(Integer.parseInt(demographic_no));
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].isCurrent()) {

                stringBuffer.append(arr[i].getFullOutLine().replaceAll(";", " ") + "\n");
                // stringBuffer.append(arr[i].getRxDisplay()+"\n");
            }
        }
        this.currentMedication = stringBuffer.toString();
        return this.currentMedication;
    }

    /**
     * Retrieves the patient's active allergies as a newline-separated string.
     *
     * <p>Each allergy entry includes the description and type descriptor.</p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographic_no String the patient demographic number
     * @return String newline-separated list of active allergies with descriptions and types
     */
    public String getAllergies(LoggedInInfo loggedInInfo, String demographic_no) {
        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, Integer.parseInt(demographic_no));
        Allergy[] allergies = {};
        allergies = patient.getActiveAllergies();
        StringBuilder stringBuffer = new StringBuilder();
        for (int i = 0; i < allergies.length; i++) {
            Allergy allerg = allergies[i];
            stringBuffer.append(allerg.getDescription() + "  " + Allergy.getTypeDesc(allerg.getTypeCode()) + " \n");
        }
        this.allergies = stringBuffer.toString();

        return this.allergies;
    }
}
