/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.consultation.dto;

import java.io.Serializable;
import java.util.Date;

import io.github.carlos_emr.carlos.utility.DtoFormatUtils;

/**
 * Lightweight DTO for consultation request list views. Eliminates the 3 EAGER
 * relationships (ProfessionalSpecialist, DemographicContact, LookupListItem)
 * that are loaded on every ConsultationRequest entity fetch.
 *
 * <p>Pre-joins specialist name via LEFT JOIN. Omits: full entity relationships,
 * clinicalInfo, currentMeds, allergies, signature, letterhead details.</p>
 *
 * @since 2026-04-11
 */
public class ConsultationRequestListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Date referralDate;
    private Integer serviceId;
    private Integer demographicNo;
    private String providerNo;
    private String status;
    private String statusText;
    private String urgency;
    private String reasonForReferral;
    private Date appointmentDate;
    private Date followUpDate;
    private String sendTo;
    private String siteName;
    private String letterheadName;
    private String source;
    private Date lastUpdateDate;
    private String specialistLastName;
    private String specialistFirstName;

    /** Default constructor for serialization/framework binding. */
    public ConsultationRequestListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the consultation request ID
     * @param referralDate Date the referral date
     * @param serviceId Integer the consultation service ID
     * @param demographicNo Integer the patient demographic number
     * @param providerNo String the referring provider number
     * @param status String the status code
     * @param statusText String the status display text
     * @param urgency String the urgency code
     * @param reasonForReferral String the referral reason
     * @param appointmentDate Date the appointment date
     * @param followUpDate Date the follow-up date
     * @param sendTo String the destination/team
     * @param siteName String the site name
     * @param letterheadName String the letterhead name
     * @param source String the source system
     * @param lastUpdateDate Date the last update timestamp
     * @param specialistLastName String the specialist last name (from LEFT JOIN)
     * @param specialistFirstName String the specialist first name (from LEFT JOIN)
     * @since 2026-04-11
     */
    public ConsultationRequestListItemDTO(Integer id, Date referralDate, Integer serviceId,
                                          Integer demographicNo, String providerNo,
                                          String status, String statusText, String urgency,
                                          String reasonForReferral, Date appointmentDate,
                                          Date followUpDate, String sendTo, String siteName,
                                          String letterheadName, String source, Date lastUpdateDate,
                                          String specialistLastName, String specialistFirstName) {
        this.id = id;
        this.referralDate = referralDate;
        this.serviceId = serviceId;
        this.demographicNo = demographicNo;
        this.providerNo = providerNo;
        this.status = status;
        this.statusText = statusText;
        this.urgency = urgency;
        this.reasonForReferral = reasonForReferral;
        this.appointmentDate = appointmentDate;
        this.followUpDate = followUpDate;
        this.sendTo = sendTo;
        this.siteName = siteName;
        this.letterheadName = letterheadName;
        this.source = source;
        this.lastUpdateDate = lastUpdateDate;
        this.specialistLastName = specialistLastName;
        this.specialistFirstName = specialistFirstName;
    }

    /**
     * Returns the specialist's formatted name as "LastName, FirstName".
     *
     * @return String the formatted specialist name, or empty string if not joined
     */
    public String getSpecialistName() {
        return DtoFormatUtils.formatName(specialistLastName, specialistFirstName, "");
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Date getReferralDate() { return referralDate; }
    public void setReferralDate(Date referralDate) { this.referralDate = referralDate; }
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getReasonForReferral() { return reasonForReferral; }
    public void setReasonForReferral(String reasonForReferral) { this.reasonForReferral = reasonForReferral; }
    public Date getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(Date appointmentDate) { this.appointmentDate = appointmentDate; }
    public Date getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(Date followUpDate) { this.followUpDate = followUpDate; }
    public String getSendTo() { return sendTo; }
    public void setSendTo(String sendTo) { this.sendTo = sendTo; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getLetterheadName() { return letterheadName; }
    public void setLetterheadName(String letterheadName) { this.letterheadName = letterheadName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Date getLastUpdateDate() { return lastUpdateDate; }
    public void setLastUpdateDate(Date lastUpdateDate) { this.lastUpdateDate = lastUpdateDate; }
    public String getSpecialistLastName() { return specialistLastName; }
    public void setSpecialistLastName(String specialistLastName) { this.specialistLastName = specialistLastName; }
    public String getSpecialistFirstName() { return specialistFirstName; }
    public void setSpecialistFirstName(String specialistFirstName) { this.specialistFirstName = specialistFirstName; }
}
