/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.test.builders;

import io.github.carlos_emr.carlos.commn.model.Appointment;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Test data builder for {@link Appointment} entities.
 *
 * <p>Provides deterministic defaults for appointment scheduling test data.
 * Uses fixed dates (2024-01-15) and times (09:00-09:30) to ensure reproducibility.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Appointment appt = AppointmentTestBuilder.anAppointment().build();
 * Appointment afternoon = AppointmentTestBuilder.anAppointment()
 *     .withStartTime(14, 0)
 *     .withEndTime(14, 30)
 *     .withReason("Follow-up")
 *     .build();
 * }</pre>
 *
 * @since 2026-03-07
 */
public class AppointmentTestBuilder {

    private String providerNo = "999990";
    private Date appointmentDate = new Date(1705276800000L); // 2024-01-15
    private Date startTime;
    private Date endTime;
    private String name = "Test Patient";
    private int demographicNo = 1;
    private int programId = 10016;
    private String notes = "";
    private String reason = "General checkup";
    private String location = "";
    private String resources = "";
    private String type = "";
    private String style = "";
    private String billing = "";
    private String status = "t";
    private String creator = "999990";
    private String lastUpdateUser = "999990";
    private String remarks = "";
    private String urgency = "";

    private AppointmentTestBuilder() {
        Calendar cal = new GregorianCalendar(1970, Calendar.JANUARY, 1, 9, 0, 0);
        this.startTime = cal.getTime();
        cal.set(Calendar.MINUTE, 30);
        this.endTime = cal.getTime();
    }

    /**
     * Creates a new builder with deterministic scheduling defaults.
     *
     * @return a new builder instance
     */
    public static AppointmentTestBuilder anAppointment() {
        return new AppointmentTestBuilder();
    }

    public AppointmentTestBuilder withProviderNo(String providerNo) {
        this.providerNo = providerNo;
        return this;
    }

    public AppointmentTestBuilder withAppointmentDate(Date appointmentDate) {
        this.appointmentDate = appointmentDate;
        return this;
    }

    /**
     * Sets the start time using hour and minute.
     *
     * @param hour   hour in 24h format (0-23)
     * @param minute minute (0-59)
     * @return this builder
     */
    public AppointmentTestBuilder withStartTime(int hour, int minute) {
        Calendar cal = new GregorianCalendar(1970, Calendar.JANUARY, 1, hour, minute, 0);
        this.startTime = cal.getTime();
        return this;
    }

    /**
     * Sets the end time using hour and minute.
     *
     * @param hour   hour in 24h format (0-23)
     * @param minute minute (0-59)
     * @return this builder
     */
    public AppointmentTestBuilder withEndTime(int hour, int minute) {
        Calendar cal = new GregorianCalendar(1970, Calendar.JANUARY, 1, hour, minute, 0);
        this.endTime = cal.getTime();
        return this;
    }

    public AppointmentTestBuilder withStartTime(Date startTime) {
        this.startTime = startTime;
        return this;
    }

    public AppointmentTestBuilder withEndTime(Date endTime) {
        this.endTime = endTime;
        return this;
    }

    public AppointmentTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public AppointmentTestBuilder withDemographicNo(int demographicNo) {
        this.demographicNo = demographicNo;
        return this;
    }

    public AppointmentTestBuilder withProgramId(int programId) {
        this.programId = programId;
        return this;
    }

    public AppointmentTestBuilder withReason(String reason) {
        this.reason = reason;
        return this;
    }

    public AppointmentTestBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public AppointmentTestBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public AppointmentTestBuilder withLocation(String location) {
        this.location = location;
        return this;
    }

    public AppointmentTestBuilder withCreator(String creator) {
        this.creator = creator;
        return this;
    }

    public AppointmentTestBuilder withNotes(String notes) {
        this.notes = notes;
        return this;
    }

    public Appointment build() {
        Appointment a = new Appointment();
        a.setProviderNo(providerNo);
        a.setAppointmentDate(appointmentDate);
        a.setStartTime(startTime);
        a.setEndTime(endTime);
        a.setName(name);
        a.setDemographicNo(demographicNo);
        a.setProgramId(programId);
        a.setNotes(notes);
        a.setReason(reason);
        a.setLocation(location);
        a.setResources(resources);
        a.setType(type);
        a.setStyle(style);
        a.setBilling(billing);
        a.setStatus(status);
        a.setCreator(creator);
        a.setLastUpdateUser(lastUpdateUser);
        a.setRemarks(remarks);
        a.setUrgency(urgency);
        return a;
    }
}
