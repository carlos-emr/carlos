/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;

/**
 * One open time offered to a patient through the portal (issue #3850).
 *
 * <p>The portal receives only {@link #getSlotId() slotId}, an opaque random identifier; this row is
 * the only place that says which provider and time it stands for. Rows are never deleted by the
 * booking flow: the status records what became of the offer.
 */
@Entity
@Table(name = "portal_booking_offer")
public class PortalBookingOffer extends AbstractModel<String> {

    /** Sent to the portal and still open. */
    public static final String OFFERED = "offered";
    /** Booked as an appointment for the patient's pick. */
    public static final String BOOKED = "booked";
    /** Picked, but gone by the time CARLOS tried to book it; the portal was told. */
    public static final String UNAVAILABLE = "unavailable";
    /** No longer bookable for another reason (its booking was undone, or the prompt ended). */
    public static final String CLOSED = "closed";

    @Id
    @Column(name = "slot_id", length = 64, nullable = false)
    private String slotId;

    @Column(name = "operation_id", length = 64, nullable = false)
    private String operationId;

    @Column(name = "prompt_id")
    private Long promptId;

    @Column(name = "demographic_no", nullable = false)
    private int demographicNo;

    @Column(name = "provider_no", length = 6, nullable = false)
    private String providerNo;

    @Column(name = "start_time", nullable = false)
    private Date startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "template_code", length = 1, nullable = false)
    private String templateCode;

    @Column(name = "offered_by", length = 6, nullable = false)
    private String offeredBy;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    /** The pick that booked this time, or (for a replacement) the pick that was refused. */
    @Column(name = "choice_id")
    private Long choiceId;

    @Column(name = "appointment_no")
    private Integer appointmentNo;

    /** Reserved for the declined/expired follow-up ticklers (#4480). */
    @Column(name = "tickler_no")
    private Integer ticklerNo;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @Override
    public String getId() {
        return slotId;
    }

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public Long getPromptId() { return promptId; }
    public void setPromptId(Long promptId) { this.promptId = promptId; }
    public int getDemographicNo() { return demographicNo; }
    public void setDemographicNo(int demographicNo) { this.demographicNo = demographicNo; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getOfferedBy() { return offeredBy; }
    public void setOfferedBy(String offeredBy) { this.offeredBy = offeredBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public Integer getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(Integer appointmentNo) { this.appointmentNo = appointmentNo; }
    public Integer getTicklerNo() { return ticklerNo; }
    public void setTicklerNo(Integer ticklerNo) { this.ticklerNo = ticklerNo; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
