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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao.projection;

/**
 * One row from
 * {@code OscarAppointmentDao.findAppointmentAndProviderByAppointmentNo} —
 * an {@code Appointment} joined with its {@code Provider}, projected to the
 * three fields the billing-form site-context composer reads when defaulting
 * the multisite picker for an existing appointment.
 *
 * @param location          the appointment's site code (Appointment.location)
 * @param appointmentProviderNo the appointment's primary providerNo
 * @param providerOhipNo    the provider's OHIP no for the {@code defaultXmlp} hidden field
 * @since 2026-05-01
 */
public record AppointmentProviderRow(
        String location,
        String appointmentProviderNo,
        String providerOhipNo) {
    public AppointmentProviderRow {
        location = location == null ? "" : location;
        appointmentProviderNo = appointmentProviderNo == null ? "" : appointmentProviderNo;
        providerOhipNo = providerOhipNo == null ? "" : providerOhipNo;
    }
}
