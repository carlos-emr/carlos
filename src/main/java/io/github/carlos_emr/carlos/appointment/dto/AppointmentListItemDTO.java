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
package io.github.carlos_emr.carlos.appointment.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.utility.DtoFormatUtils;

/**
 * Lightweight data transfer object for daily schedule and appointment list views,
 * optimized for JPQL constructor expression projection with a LEFT JOIN to
 * Demographic for patient name. Carries only the 17 fields needed for schedule display
 * out of Appointment's 37 fields.
 *
 * <p>Omits: {@code programId}, {@code style}, {@code billing}, {@code importedStatus},
 * {@code createDateTime}, {@code updateDateTime}, {@code creator}, {@code lastUpdateUser},
 * {@code creatorSecurityId}, {@code bookingSource}, {@code resources}.</p>
 *
 * @since 2026-04-11
 */
public class AppointmentListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String providerNo;
    private Date appointmentDate;
    private Date startTime;
    private Date endTime;
    private String name;
    private int demographicNo;
    private String status;
    private String type;
    private String reason;
    private String location;
    private String notes;
    private String urgency;
    private String remarks;
    private Integer reasonCode;
    // Pre-joined from Demographic via LEFT JOIN
    private String patientLastName;
    private String patientFirstName;

    public AppointmentListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the appointment number
     * @param providerNo String the provider number
     * @param appointmentDate Date the appointment date
     * @param startTime Date the start time
     * @param endTime Date the end time
     * @param name String the appointment name/label
     * @param demographicNo int the patient demographic number
     * @param status String the appointment status
     * @param type String the appointment type
     * @param reason String the appointment reason
     * @param location String the appointment location
     * @param notes String the appointment notes
     * @param urgency String the urgency level
     * @param remarks String the remarks
     * @param reasonCode Integer the reason code
     * @param patientLastName String the patient's last name (from LEFT JOIN)
     * @param patientFirstName String the patient's first name (from LEFT JOIN)
     */
    public AppointmentListItemDTO(Integer id, String providerNo, Date appointmentDate,
                                  Date startTime, Date endTime, String name, int demographicNo,
                                  String status, String type, String reason, String location,
                                  String notes, String urgency, String remarks, Integer reasonCode,
                                  String patientLastName, String patientFirstName) {
        this.id = id;
        this.providerNo = providerNo;
        this.appointmentDate = appointmentDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.name = name;
        this.demographicNo = demographicNo;
        this.status = status;
        this.type = type;
        this.reason = reason;
        this.location = location;
        this.notes = notes;
        this.urgency = urgency;
        this.remarks = remarks;
        this.reasonCode = reasonCode;
        this.patientLastName = patientLastName;
        this.patientFirstName = patientFirstName;
    }

    /**
     * Creates an AppointmentListItemDTO from a full Appointment entity.
     * Patient name fields are not populated (they require a Demographic join).
     *
     * @param a Appointment the entity to convert; must not be null
     * @return AppointmentListItemDTO a lightweight projection (without patient name)
     */
    public static AppointmentListItemDTO fromEntity(Appointment a) {
        Objects.requireNonNull(a, "Appointment entity must not be null for DTO conversion");
        return new AppointmentListItemDTO(
                a.getId(), a.getProviderNo(), a.getAppointmentDate(),
                a.getStartTime(), a.getEndTime(), a.getName(), a.getDemographicNo(),
                a.getStatus(), a.getType(), a.getReason(), a.getLocation(),
                a.getNotes(), a.getUrgency(), a.getRemarks(), a.getReasonCode(),
                null, null
        );
    }

    /**
     * Returns the patient's formatted name as "LastName, FirstName".
     *
     * @return String the formatted patient name, or empty string if not joined
     */
    public String getPatientName() {
        return DtoFormatUtils.formatName(patientLastName, patientFirstName, "");
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public Date getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(Date appointmentDate) { this.appointmentDate = appointmentDate; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getDemographicNo() { return demographicNo; }
    public void setDemographicNo(int demographicNo) { this.demographicNo = demographicNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Integer getReasonCode() { return reasonCode; }
    public void setReasonCode(Integer reasonCode) { this.reasonCode = reasonCode; }
    public String getPatientLastName() { return patientLastName; }
    public void setPatientLastName(String patientLastName) { this.patientLastName = patientLastName; }
    public String getPatientFirstName() { return patientFirstName; }
    public void setPatientFirstName(String patientFirstName) { this.patientFirstName = patientFirstName; }
}
