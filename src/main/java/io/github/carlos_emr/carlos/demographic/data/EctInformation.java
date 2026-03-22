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

import java.util.Date;

import io.github.carlos_emr.carlos.encounter.data.EctPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Provides access to a patient's electronic chart (eChart) data including
 * social history, family history, medical history, ongoing concerns, reminders,
 * encounter notes, and subjects.
 *
 * <p>This class wraps {@link EctPatientData.Patient.eChart} to provide convenient
 * accessor methods for eChart fields.</p>
 *
 * @deprecated Requires excessive redundant data storage. Use the patient data
 *             and case management APIs directly instead.
 * @see io.github.carlos_emr.carlos.encounter.data.EctPatientData
 * @since 2026-03-17
 */
@Deprecated
public class EctInformation {

    private EctPatientData.Patient patient;
    private EctPatientData.Patient.eChart eChart;

    /**
     * Constructs an EctInformation instance for the given patient.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographic_no String the patient demographic number
     */
    public EctInformation(LoggedInInfo loggedInInfo, String demographic_no) {
        init(loggedInInfo, demographic_no);
    }

    private void init(LoggedInInfo loggedInInfo, String demographic_no) {
        EctPatientData patientData = new EctPatientData();
        this.patient = patientData.getPatient(loggedInInfo, demographic_no);
        this.eChart = patient.getEChart();
    }

    /**
     * Returns the timestamp of the patient's eChart.
     *
     * @return Date the eChart timestamp
     */
    public Date getEChartTimeStamp() {
        return eChart.getEChartTimeStamp();
    }

    /**
     * Returns the patient's social history from the eChart.
     *
     * @return String the social history text
     */
    public String getSocialHistory() {
        return eChart.getSocialHistory();
    }

    /**
     * Returns the patient's family history from the eChart.
     *
     * @return String the family history text
     */
    public String getFamilyHistory() {
        return eChart.getFamilyHistory();
    }

    /**
     * Returns the patient's medical history from the eChart.
     *
     * @return String the medical history text
     */
    public String getMedicalHistory() {
        return eChart.getMedicalHistory();
    }

    /**
     * Returns the patient's ongoing concerns from the eChart.
     *
     * @return String the ongoing concerns text
     */
    public String getOngoingConcerns() {
        return eChart.getOngoingConcerns();
    }

    /**
     * Returns the patient's reminders from the eChart.
     *
     * @return String the reminders text
     */
    public String getReminders() {
        return eChart.getReminders();
    }

    /**
     * Returns the patient's encounter notes from the eChart.
     *
     * @return String the encounter text
     */
    public String getEncounter() {
        return eChart.getEncounter();
    }

    /**
     * Returns the eChart subject line.
     *
     * @return String the subject text
     */
    public String getSubject() {
        return eChart.getSubject();
    }
}
