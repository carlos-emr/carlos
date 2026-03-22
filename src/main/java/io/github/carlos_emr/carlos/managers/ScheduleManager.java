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

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for provider schedule and appointment management.
 *
 * <p>Provides operations for managing provider work schedules, retrieving
 * day-level schedule information, and performing CRUD operations on appointments.
 * Includes support for schedule templates, holiday awareness, appointment archiving,
 * and double-booking detection.</p>
 *
 * <p>All methods that access patient-related appointment data require a
 * {@link LoggedInInfo} parameter for audit logging.</p>
 *
 * @since 2026-03-17
 */
public interface ScheduleManager {

    /**
     * Retrieves the work schedule for a provider on a specific day.
     *
     * <p>The date is converted to server-local time. For example, if the server's
     * timezone is EST, both "2011-11-11 2:01 TZ America/New_York" and
     * "2011-11-10 23:01 TZ America/Los_Angeles" return the schedule for
     * November 11, 2011. The returned schedule is in the server's local timezone.</p>
     *
     * @param providerNo String the provider number
     * @param date Calendar the date to retrieve the schedule for
     * @return DayWorkSchedule the provider's schedule for the day, or null if no schedule is configured
     */
    public DayWorkSchedule getDayWorkSchedule(String providerNo, Calendar date);

    /**
     * Retrieves all non-cancelled appointments for a provider on a specific date.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param providerNo String the provider number
     * @param date Date the date to retrieve appointments for
     * @return List of Appointment objects for the specified day
     */
    public List<Appointment> getDayAppointments(LoggedInInfo loggedInInfo, String providerNo, Date date);

    /**
     * Retrieves all non-cancelled appointments for a provider on a specific date.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param providerNo String the provider number
     * @param date Calendar the date to retrieve appointments for
     * @return List of Appointment objects for the specified day
     */
    public List<Appointment> getDayAppointments(LoggedInInfo loggedInInfo, String providerNo, Calendar date);

    /**
     * Retrieves all schedule template codes (appointment type definitions).
     *
     * @return List of ScheduleTemplateCode objects
     */
    public List<ScheduleTemplateCode> getScheduleTemplateCodes();

    /**
     * Retrieves all configured appointment types.
     *
     * @return List of AppointmentType objects
     */
    public List<AppointmentType> getAppointmentTypes();

    /**
     * Creates a new appointment and sets the creator information from the security context.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param security Security the security object identifying the creator
     * @param appointment Appointment the appointment to persist
     */
    public void addAppointment(LoggedInInfo loggedInInfo, Security security, Appointment appointment);

    /**
     * Retrieves appointments for a specific patient with pagination.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param demographicId Integer the patient demographic ID
     * @param startIndex int the zero-based start index for pagination
     * @param itemsToReturn int the maximum number of appointments to return
     * @return List of Appointment objects for the patient
     */
    public List<Appointment> getAppointmentsForPatient(LoggedInInfo loggedInInfo, Integer demographicId, int startIndex, int itemsToReturn);

    /**
     * Retrieves appointments filtered by program, provider, demographic, and update date.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param programId Integer the program ID to filter by
     * @param providerNo String the provider number to filter by
     * @param demographicId Integer the demographic ID to filter by
     * @param updatedAfterThisDateExclusive Calendar only return appointments updated after this date
     * @param itemsToReturn int the maximum number of appointments to return
     * @return List of Appointment objects matching the criteria
     */
    public List<Appointment> getAppointmentsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves a single appointment by its ID.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param appointmentId Integer the appointment ID
     * @return Appointment the appointment, or null if not found
     */
    public Appointment getAppointment(LoggedInInfo loggedInInfo, Integer appointmentId);

    /**
     * Updates an existing appointment, archiving the previous version first.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param appointment Appointment the appointment with updated fields
     */
    public void updateAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    /**
     * Retrieves appointments for a provider within a date range.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param startTime Date the start of the date range
     * @param endTime Date the end of the date range
     * @param providerNo String the provider number
     * @return List of Appointment objects within the date range
     */
    public List<Appointment> getAppointmentsForDateRangeAndProvider(LoggedInInfo loggedInInfo, Date startTime, Date endTime, String providerNo);

    /**
     * Retrieves appointments updated after a specified date, with consent filtering.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param updatedAfterThisDateExclusive Date only return appointments updated after this date
     * @param itemsToReturn int the maximum number of appointments to return
     * @return List of Appointment objects updated after the specified date
     */
    public List<Appointment> getAppointmentUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves appointments for a specific patient updated after a given date, subject to consent checks.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param demographicId Integer the patient demographic ID
     * @param updatedAfterThisDateExclusive Date only return appointments updated after this date
     * @return List of Appointment objects matching the criteria
     */
    public List<Appointment> getAppointmentByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId, Date updatedAfterThisDateExclusive);

    /**
     * Retrieves archived appointment records updated after a specified date.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param updatedAfterThisDateExclusive Date only return archives updated after this date
     * @param itemsToReturn int the maximum number of archives to return
     * @return List of AppointmentArchive objects updated after the specified date
     */
    public List<AppointmentArchive> getAppointmentArchiveUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Retrieves all appointment statuses (up to 100).
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @return List of AppointmentStatus objects
     */
    public List<AppointmentStatus> getAppointmentStatuses(LoggedInInfo loggedInInfo);

    /**
     * Retrieves all demographic IDs that have appointments with a specific program and provider.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param programId Integer the program ID to filter by
     * @param providerNo String the provider number to filter by
     * @return List of Integer demographic IDs
     */
    public List<Integer> getAllDemographicIdByProgramProvider(LoggedInInfo loggedInInfo, Integer programId, String providerNo);

    /**
     * Lists appointments with extended demographic details for a date range and set of providers.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param sDate Date the start date
     * @param eDate Date the end date
     * @param providers String comma-separated list of provider numbers
     * @return List of Object arrays containing appointment and demographic fields
     */
    public List<Object[]> listAppointmentsByPeriodProvider(LoggedInInfo loggedInInfo, Date sDate, Date eDate, String providers);

    /**
     * Lists appointment counts per provider for a date range.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param sDateStr String the start date in ISO 8601 format
     * @param eDateStr String the end date in ISO 8601 format
     * @return List of Object arrays containing provider info and appointment counts
     */
    public List<Object[]> listProviderAppointmentCounts(LoggedInInfo loggedInInfo, String sDateStr, String eDateStr);

    /**
     * Checks for double-booking conflicts and removes the appointment if a conflict is detected.
     *
     * <p>Scans existing appointments in a time window around the given appointment
     * and deletes the appointment if any overlap is found.</p>
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for audit logging
     * @param startTime Calendar the appointment start time
     * @param endTime Calendar the appointment end time
     * @param providerNo String the provider number
     * @param appointment Appointment the appointment to check for conflicts
     * @return boolean true if the appointment was removed due to a double-booking conflict
     */
    public boolean removeIfDoubleBooked(LoggedInInfo loggedInInfo, Calendar startTime, Calendar endTime, String providerNo, Appointment appointment);

}
 