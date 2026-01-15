//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.webserv;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebService;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.cxf.annotations.GZIP;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Appointment;
import ca.openosp.openo.commn.model.AppointmentArchive;
import ca.openosp.openo.commn.model.AppointmentType;
import ca.openosp.openo.commn.model.ScheduleTemplateCode;
import ca.openosp.openo.managers.DayWorkSchedule;
import ca.openosp.openo.managers.ScheduleManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.webserv.transfer_objects.AppointmentArchiveTransfer;
import ca.openosp.openo.webserv.transfer_objects.AppointmentTransfer;
import ca.openosp.openo.webserv.transfer_objects.AppointmentTypeTransfer;
import ca.openosp.openo.webserv.transfer_objects.DayWorkScheduleTransfer;
import ca.openosp.openo.webserv.transfer_objects.ScheduleTemplateCodeTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class ScheduleWs extends AbstractWs {
    private static final Logger logger = MiscUtils.getLogger();

    @Autowired
    private ScheduleManager scheduleManager;

    public ScheduleTemplateCodeTransfer[] getScheduleTemplateCodes() {
        List<ScheduleTemplateCode> scheduleTemplateCodes = scheduleManager.getScheduleTemplateCodes();
        return (ScheduleTemplateCodeTransfer.toTransfer(scheduleTemplateCodes));
    }

    /**
     * @deprecated you should use the method with the useGMTTime option
     */
    @Deprecated
    public AppointmentTransfer getAppointment(Integer appointmentId) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentId);
        return (AppointmentTransfer.toTransfer(appointment, false));
    }

    /**
     * @deprecated you should use the method with the useGMTTime option
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForProvider(String providerNo, Calendar date) {
        List<Appointment> appointments = scheduleManager.getDayAppointments(getLoggedInInfo(), providerNo, date);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    /**
     * @deprecated you should use the method with the useGMTTime option
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForPatient(Integer demographicId, int startIndex, int itemsToReturn) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForPatient(getLoggedInInfo(), demographicId, startIndex, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    public AppointmentTransfer getAppointment2(Integer appointmentId, boolean useGMTTime) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentId);
        return (AppointmentTransfer.toTransfer(appointment, useGMTTime));
    }

    public AppointmentTransfer[] getAppointmentsForProvider2(String providerNo, Calendar date, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getDayAppointments(getLoggedInInfo(), providerNo, date);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    public AppointmentTransfer[] getAppointmentsForPatient2(Integer demographicId, int startIndex, int itemsToReturn, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForPatient(getLoggedInInfo(), demographicId, startIndex, itemsToReturn);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

    public DayWorkScheduleTransfer getDayWorkSchedule(String providerNo, Calendar date) {
        DayWorkSchedule dayWorkSchedule = scheduleManager.getDayWorkSchedule(providerNo, date);
        if (dayWorkSchedule == null) return (null);
        else return (DayWorkScheduleTransfer.toTransfer(dayWorkSchedule));
    }

    public AppointmentTypeTransfer[] getAppointmentTypes() {
        List<AppointmentType> appointmentTypes = scheduleManager.getAppointmentTypes();
        return (AppointmentTypeTransfer.toTransfer(appointmentTypes));
    }

    /**
     * @return the ID of the appointment just added
     */
    public Integer addAppointment(AppointmentTransfer appointmentTransfer) {
        Appointment appointment = new Appointment();
        appointmentTransfer.copyTo(appointment);
        scheduleManager.addAppointment(getLoggedInInfo(), getLoggedInSecurity(), appointment);
        return (appointment.getId());
    }

    public void updateAppointment(AppointmentTransfer appointmentTransfer) {
        Appointment appointment = scheduleManager.getAppointment(getLoggedInInfo(), appointmentTransfer.getId());

        appointmentTransfer.copyTo(appointment);
        appointment.setLastUpdateUser(getLoggedInInfo().getLoggedInProviderNo());

        scheduleManager.updateAppointment(getLoggedInInfo(), appointment);
    }

    /**
     * @deprecated you should use the method with the useGMTTime option
     */
    @Deprecated
    public AppointmentTransfer[] getAppointmentsForDateRangeAndProvider(Date startTime, Date endTime, String providerNo) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForDateRangeAndProvider(getLoggedInInfo(), startTime, endTime, providerNo);
        return (AppointmentTransfer.toTransfers(appointments, false));
    }

    public AppointmentTransfer[] getAppointmentsForDateRangeAndProvider2(Date startTime, Date endTime, String providerNo, boolean useGMTTime) {
        List<Appointment> appointments = scheduleManager.getAppointmentsForDateRangeAndProvider(getLoggedInInfo(), startTime, endTime, providerNo);
        return (AppointmentTransfer.toTransfers(appointments, useGMTTime));
    }

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
