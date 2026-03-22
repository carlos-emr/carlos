/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing patient appointments in the CARLOS EMR system.
 *
 * <p>Provides CRUD operations for appointments including scheduling, status updates,
 * type and urgency management, and appointment history retrieval. Supports both
 * active and deleted appointment queries for audit purposes.</p>
 *
 * @see AppointmentManagerImpl
 * @see io.github.carlos_emr.carlos.commn.model.Appointment
 * @since 2026-03-17
 */
public interface AppointmentManager {

    /**
     * Retrieves appointment history for a patient, excluding deleted appointments.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @param offset Integer the starting index for pagination
     * @param limit Integer the maximum number of records to return
     * @return List of Appointment records excluding deleted ones
     */
    public List<Appointment> getAppointmentHistoryWithoutDeleted(LoggedInInfo loggedInInfo, Integer demographicNo, Integer offset, Integer limit);

    /**
     * Retrieves complete appointment history for a patient, including deleted appointments.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @param offset Integer the starting index for pagination
     * @param limit Integer the maximum number of records to return
     * @return List of Objects representing appointment records with deletion metadata
     */
    public List<Object> getAppointmentHistoryWithDeleted(LoggedInInfo loggedInInfo, Integer demographicNo, Integer offset, Integer limit);

    /**
     * Adds an appointment to the system.
     *
     * @param loggedInInfo logged in provider information
     * @param appointment appointment data to add
     */
    public void addAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    /**
     * Updates an existing appointment record.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param appointment Appointment the updated appointment data
     */
    public void updateAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    /**
     * Marks an appointment as deleted.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param apptNo int the appointment number to delete
     */
    public void deleteAppointment(LoggedInInfo loggedInInfo, int apptNo);

    /**
     * Retrieves a single appointment by its number.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param apptNo int the appointment number
     * @return Appointment the appointment record, or null if not found
     */
    public Appointment getAppointment(LoggedInInfo loggedInInfo, int apptNo);

    /**
     * Updates the status of an existing appointment.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param apptNo int the appointment number
     * @param status String the new status code
     * @return Appointment the updated appointment record
     */
    public Appointment updateAppointmentStatus(LoggedInInfo loggedInInfo, int apptNo, String status);

    /**
     * Updates the type of an existing appointment.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param apptNo int the appointment number
     * @param type String the new appointment type
     * @return Appointment the updated appointment record
     */
    public Appointment updateAppointmentType(LoggedInInfo loggedInInfo, int apptNo, String type);

    /**
     * Updates the urgency level of an existing appointment.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param apptNo int the appointment number
     * @param urgency String the new urgency level
     * @return Appointment the updated appointment record
     */
    public Appointment updateAppointmentUrgency(LoggedInInfo loggedInInfo, int apptNo, String urgency);

    /**
     * Retrieves all defined appointment status options.
     *
     * @return List of AppointmentStatus records
     */
    public List<AppointmentStatus> getAppointmentStatuses();

    /**
     * Retrieves all defined appointment reason codes.
     *
     * @return List of LookupListItem records representing appointment reasons
     */
    public List<LookupListItem> getReasons();

    /**
     * Retrieves all appointments for a provider within a specific month.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param providerNo String the provider number
     * @param year int the calendar year
     * @param month int the calendar month (1-12)
     * @return List of Appointment records for the specified month
     */
    public List<Appointment> findMonthlyAppointments(LoggedInInfo loggedInInfo, String providerNo, int year, int month);

    /**
     * Returns the date of the next upcoming appointment for a patient.
     *
     * @param demographicNo Integer the patient demographic number
     * @return String the formatted date of the next appointment, or null if none scheduled
     */
    public String getNextAppointmentDate(Integer demographicNo);
}
