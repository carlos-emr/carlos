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

package io.github.carlos_emr.carlos.encounter.data;

import java.util.Date;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Represents an electronic chart (eChart) entry for a patient encounter.
 * Holds clinical data including social history, family history, medical history,
 * ongoing concerns, reminders, and the encounter narrative.
 *
 * <p>Note: Setter methods append to existing values rather than replacing them,
 * allowing incremental construction of chart content.</p>
 *
 * @since 2001-01-01
 */
public class Echart {
    private Date eChartTimeStamp = new Date();
    private String socialHistory = "";
    private String familyHistory = "";
    private String medicalHistory = "";
    private String ongoingConcerns = "";
    private String reminders = "";
    private String encounter = "";
    private String subject = "";
    private String demographicNo = "";
    private String providerNo = "";

    /**
     * Constructs an empty Echart with default empty strings and current timestamp.
     */
    public Echart() {

    }

    /**
     * Returns the timestamp of this eChart entry.
     *
     * @return Date the eChart creation/modification timestamp
     */
    public Date getEChartTimeStamp() {
        return this.eChartTimeStamp;
    }

    /**
     * Returns the patient's social history narrative.
     *
     * @return String the social history text
     */
    public String getSocialHistory() {
        return this.socialHistory;
    }

    /**
     * Returns the patient's family history narrative.
     *
     * @return String the family history text
     */
    public String getFamilyHistory() {
        return this.familyHistory;
    }

    /**
     * Returns the patient's medical history narrative.
     *
     * @return String the medical history text
     */
    public String getMedicalHistory() {
        return this.medicalHistory;
    }

    /**
     * Returns the patient's ongoing concerns narrative.
     *
     * @return String the ongoing concerns text
     */
    public String getOngoingConcerns() {
        return this.ongoingConcerns;
    }

    /**
     * Returns clinical reminders for this patient.
     *
     * @return String the reminders text
     */
    public String getReminders() {
        return this.reminders;
    }

    /**
     * Returns the encounter narrative text.
     *
     * @return String the encounter text
     */
    public String getEncounter() {
        return this.encounter;
    }

    /**
     * Returns the subject/reason for this encounter.
     *
     * @return String the subject text
     */
    public String getSubject() {
        return this.subject;
    }

    /**
     * Returns the demographic (patient) number.
     *
     * @return String the demographic number
     */
    public String getDemographicNo() {
        return this.demographicNo;
    }

    /**
     * Returns the provider number who created this chart entry.
     *
     * @return String the provider number
     */
    public String getProviderNo() {
        return this.providerNo;
    }

    /**
     * Returns the timestamp formatted as a string in "yyyy-MM-dd HH:mm:ss" format.
     *
     * @return String the formatted timestamp
     */
    public String getTimeStampToString() {
        return UtilDateUtilities.DateToString(eChartTimeStamp,
                "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Appends to the subject text. Does not replace existing content.
     *
     * @param subject String the subject text to append
     */
    public void setSubject(String subject) {
        this.subject += subject;
    }

    /**
     * Appends to the social history text. Does not replace existing content.
     *
     * @param socialHistory String the social history text to append
     */
    public void setSocialHistory(String socialHistory) {
        this.socialHistory += socialHistory;
    }

    /**
     * Appends to the family history text. Does not replace existing content.
     *
     * @param familyHistory String the family history text to append
     */
    public void setFamilyHistory(String familyHistory) {
        this.familyHistory += familyHistory;
    }

    /**
     * Appends to the medical history text. Does not replace existing content.
     *
     * @param medicalHistory String the medical history text to append
     */
    public void setMedicalHistory(String medicalHistory) {
        this.medicalHistory += medicalHistory;
    }

    /**
     * Appends to the ongoing concerns text. Does not replace existing content.
     *
     * @param ongoingConcerns String the ongoing concerns text to append
     */
    public void setOngoingConcerns(String ongoingConcerns) {
        this.ongoingConcerns += ongoingConcerns;
    }

    /**
     * Appends to the reminders text. Does not replace existing content.
     *
     * @param reminders String the reminders text to append
     */
    public void setReminders(String reminders) {
        this.reminders += reminders;
    }

    /**
     * Appends to the encounter narrative text. Does not replace existing content.
     *
     * @param encounter String the encounter text to append
     */
    public void setEncounter(String encounter) {
        this.encounter += encounter;
    }

    /**
     * Sets the demographic (patient) number.
     *
     * @param demographicNo String the demographic number to set
     */
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    /**
     * Sets the provider number.
     *
     * @param providerNo String the provider number to set
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * setTimeStamp
     *
     * @param date Date
     */
    public void setTimeStamp(Date date) {
        this.eChartTimeStamp = date;
    }
}
