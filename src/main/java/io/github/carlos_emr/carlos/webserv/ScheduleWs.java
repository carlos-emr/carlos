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

package io.github.carlos_emr.carlos.webserv;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.jws.WebParam;
import jakarta.jws.WebService;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.cxf.annotations.GZIP;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentArchiveTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTypeTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DayWorkScheduleTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ScheduleTemplateCodeTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SOAP web service endpoint for appointment schedule operations in the inter-EMR Integrator system.
 *
 * <p>Provides methods for querying appointments, work schedules, appointment types,
 * and template codes between CARLOS EMR installations.
 *
 * @since 2012-08-13
 */
@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class ScheduleWs extends AbstractWs {
    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private ScheduleManager scheduleManager;

    /**
     * Retrieves all schedule template codes as transfer objects.
     *
     * @return ScheduleTemplateCodeTransfer[] array of all template codes
     */
    public ScheduleTemplateCodeTransfer[] getScheduleTemplateCodes() {
        List<ScheduleTemplateCode> scheduleTemplateCodes = scheduleManager.getScheduleTemplateCodes();
        return (ScheduleTemplateCodeTransfer.toTransfer(scheduleTemplateCodes));
    }

    /**
     * Retrieves a single appointment by ID using local time.
     *
     * @param appointmentId Integer the appointment ID to retrieve
     * @return AppointmentTransfer the appointment data in local time
     * @deprecated Use {@link #getAppointment2(Integer, boolean)} with explicit GMT time control
     */
    @Deprecated
    public AppointmentTransfer getAppointment(Integer appointmentId) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentId);
        return (AppointmentTransfer.toTransfer(appointment, false));
    }

    /**
     * Retrieves appointments for a provider on a given day using local time.
     *
     * @param providerNo String the provider number
     * @param date Calendar the date to query
     * @return AppointmentTransfer[] array of appointments in local time
     * @deprecated Use {@link #getAppointmentsForProvider2(String, Calendar, boolean)} with explicit GMT time control
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForProvider(String providerNo, Calendar date) {
        List<Appointment> appointments = scheduleManager.getDayAppointments(getLoggedInInfo(), providerNo, date);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    /**
     * Retrieves appointments for a patient with pagination using local time.
     *
     * @param demographicId Integer the patient demographic ID
     * @param startIndex int the zero-based start index for pagination
     * @param itemsToReturn int the maximum number of appointments to return
     * @return AppointmentTransfer[] array of appointments in local time
     * @deprecated Use {@link #getAppointmentsForPatient2(Integer, int, int, boolean)} with explicit GMT time control
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForPatient(Integer demographicId, int startIndex, int itemsToReturn) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForPatient(getLoggedInInfo(), demographicId, startIndex, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    /**
     * Retrieves a single appointment by ID with timezone control.
     *
     * @param appointmentId Integer the appointment ID to retrieve
     * @param useGMTTime boolean if true, times are returned in GMT; otherwise in server local time
     * @return AppointmentTransfer the appointment data
     */
    public AppointmentTransfer getAppointment2(Integer appointmentId, boolean useGMTTime) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentId);
        return (AppointmentTransfer.toTransfer(appointment, useGMTTime));
    }

    /**
     * Retrieves appointments for a provider on a given day with timezone control.
     *
     * @param providerNo String the provider number
     * @param date Calendar the date to query
     * @param useGMTTime boolean if true, times are returned in GMT; otherwise in server local time
     * @return AppointmentTransfer[] array of appointments
     */
    public AppointmentTransfer[] getAppointmentsForProvider2(String providerNo, Calendar date, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getDayAppointments(getLoggedInInfo(), providerNo, date);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    /**
     * Retrieves appointments for a patient with pagination and timezone control.
     *
     * @param demographicId Integer the patient demographic ID
     * @param startIndex int the zero-based start index for pagination
     * @param itemsToReturn int the maximum number of appointments to return
     * @param useGMTTime boolean if true, times are returned in GMT; otherwise in server local time
     * @return AppointmentTransfer[] array of appointments
     */
    public AppointmentTransfer[] getAppointmentsForPatient2(Integer demographicId, int startIndex, int itemsToReturn, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForPatient(getLoggedInInfo(), demographicId, startIndex, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    /**
     * Retrieves a provider's work schedule for a specific day.
     *
     * @param providerNo String the provider number
     * @param date Calendar the date to retrieve the schedule for
     * @return DayWorkScheduleTransfer the day's schedule, or null if none configured
     */
    public DayWorkScheduleTransfer getDayWorkSchedule(String providerNo, Calendar date) {
        DayWorkSchedule dayWorkSchedule = scheduleManager.getDayWorkSchedule(providerNo, date);
        if (dayWorkSchedule == null) return (null);
        else return (DayWorkScheduleTransfer.toTransfer(dayWorkSchedule));
    }

    /**
     * Retrieves all configured appointment types.
     *
     * @return AppointmentTypeTransfer[] array of appointment types
     */
    public AppointmentTypeTransfer[] getAppointmentTypes() {
        List<AppointmentType> appointmentTypes = scheduleManager.getAppointmentTypes();
        return (AppointmentTypeTransfer.toTransfer(appointmentTypes));
    }

    /**
     * Creates a new appointment from the provided transfer object.
     *
     * @param appointmentTransfer AppointmentTransfer the appointment data to persist
     * @return Integer the ID of the newly created appointment
     */
    public Integer addAppointment(AppointmentTransfer appointmentTransfer) {
        Appointment appointment = new Appointment();
        appointmentTransfer.copyTo(appointment);
        scheduleManager.addAppointment(getLoggedInInfo(), getLoggedInSecurity(), appointment);
        return (appointment.getId());
    }

    /**
     * Updates an existing appointment with the provided transfer object data.
     *
     * @param appointmentTransfer AppointmentTransfer the updated appointment data
     */
    public void updateAppointment(AppointmentTransfer appointmentTransfer) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentTransfer.getId());

        appointmentTransfer.copyTo(appointment);
        appointment.setLastUpdateUser(getLoggedInInfo().getLoggedInProviderNo());

        scheduleManager.updateAppointment(getLoggedInInfo(), appointment);
    }

    /**
     * Retrieves appointments for a provider in a date range using local time.
     *
     * @param startTime Date the start of the date range
     * @param endTime Date the end of the date range
     * @param providerNo String the provider number
     * @return AppointmentTransfer[] array of appointments in local time
     * @deprecated Use {@link #getAppointmentsForDateRangeAndProvider2(Date, Date, String, boolean)} with explicit GMT time control
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForDateRangeAndProvider(Date startTime, Date endTime, String providerNo) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForDateRangeAndProvider(getLoggedInInfo(), startTime, endTime, providerNo);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    /**
     * Retrieves appointments for a provider in a date range with timezone control.
     *
     * @param startTime Date the start of the date range
     * @param endTime Date the end of the date range
     * @param providerNo String the provider number
     * @param useGMTTime boolean if true, times are returned in GMT; otherwise in server local time
     * @return AppointmentTransfer[] array of appointments
     */
    public AppointmentTransfer[] getAppointmentsForDateRangeAndProvider2(Date startTime, Date endTime, String providerNo, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForDateRangeAndProvider(getLoggedInInfo(), startTime, endTime, providerNo);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    /**
     * Retrieves appointments updated after a specified date with timezone control.
     *
     * @param updatedAfterThisDateExclusive Date only return appointments updated after this date
     * @param itemsToReturn int the maximum number of appointments to return
     * @param useGMTTime boolean if true, times are returned in GMT; otherwise in server local time
     * @return AppointmentTransfer[] array of recently updated appointments
     */
    public AppointmentTransfer[] getAppointmentsUpdatedAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentUpdatedAfterDate(getLoggedInInfo(), updatedAfterThisDateExclusive, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    public AppointmentArchiveTransfer[] getAppointmentArchivesUpdatedAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn, boolean useGMTTime) {
        List<AppointmentArchive> appointments = scheduleManager.getAppointmentArchiveUpdatedAfterDate(getLoggedInInfo(), updatedAfterThisDateExclusive, itemsToReturn);
        return (AppointmentArchiveTransfer.toTransfers(appointments, useGMTTime));
    }

    public AppointmentTransfer[] getAppointmentsByProgramProviderDemographicDate(Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentsByProgramProviderDemographicDate(getLoggedInInfo(), programId, providerNo, demographicId, updatedAfterThisDateExclusive, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    /**
     * This method is a helper method to help people code and test their clients against time zone differences.
     * We will not support revisioning for this method, if / when we want to change this, we will.
     */
    public Calendar testTimeZone_1492_05_12_18_26_32(boolean useGMTTime) {
        Calendar cal = new GregorianCalendar(1492, 05, 12, 18, 26, 32);
        cal = AppointmentTransfer.setToGMTIfRequired(cal, useGMTTime);

        logger.debug("timeZoneTest sent: " + cal);
        logger.debug("timeZoneTest sent: " + DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(cal));

        return (cal);
    }

    public Integer[] getAllDemographicIdByProgramProvider(Integer programId, String providerNo) {
        List<Integer> results = scheduleManager.getAllDemographicIdByProgramProvider(getLoggedInInfo(), programId, providerNo);
        return (results.toArray(new Integer[0]));
    }

    public AppointmentTransfer[] getAppointmentsByDemographicIdAfter(@WebParam(name = "lastUpdate") Calendar lastUpdate, @WebParam(name = "demographicId") Integer demographicId, @WebParam(name = "useGMTTime") boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentByDemographicIdUpdatedAfterDate(getLoggedInInfo(), demographicId, lastUpdate.getTime());
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }
}
