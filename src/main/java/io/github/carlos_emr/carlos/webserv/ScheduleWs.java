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

@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@org.apache.cxf.interceptor.OutInterceptors(interceptors = {"io.github.carlos_emr.carlos.webserv.ScheduleSyncOutInterceptor"})
@org.apache.cxf.interceptor.OutFaultInterceptors(interceptors = {"io.github.carlos_emr.carlos.webserv.ScheduleSyncOutInterceptor"})
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
        Date after = updatedAfterThisDateExclusive; int limit = itemsToReturn;
        var actor = getLoggedInInfo();
        var selection = ScheduleSyncHeaders.select(actor, "current", after, limit, useGMTTime);
        List<Appointment> appointments = invokeSync(() -> {
            if (!selection.keyset()) return scheduleManager.getAppointmentUpdatedAfterDate(actor, after, limit);
            var page = scheduleManager.getAppointmentUpdatedPage(actor, new Date(selection.afterMillis()), selection.afterId(), limit);
            ScheduleSyncHeaders.complete(selection, page, Appointment::getUpdateDateTime, Appointment::getId);
            return page.items();
        });
        return AppointmentTransfer.toTransfers(appointments, useGMTTime);
    }

    public AppointmentArchiveTransfer[] getAppointmentArchivesUpdatedAfterDate(Date updatedAfterThisDateExclusive, int itemsToReturn, boolean useGMTTime) {
        Date after = updatedAfterThisDateExclusive; int limit = itemsToReturn;
        var actor = getLoggedInInfo();
        var selection = ScheduleSyncHeaders.select(actor, "archive", after, limit, useGMTTime);
        List<AppointmentArchive> appointments = invokeSync(() -> {
            if (!selection.keyset()) return scheduleManager.getAppointmentArchiveUpdatedAfterDate(actor, after, limit);
            var page = scheduleManager.getAppointmentArchiveUpdatedPage(actor, new Date(selection.afterMillis()), selection.afterId(), limit);
            ScheduleSyncHeaders.complete(selection, page, AppointmentArchive::getUpdateDateTime, AppointmentArchive::getId);
            return page.items();
        });
        return AppointmentArchiveTransfer.toTransfers(appointments, useGMTTime);
    }

    public AppointmentTransfer[] getAppointmentsByProgramProviderDemographicDate(Integer programId, String providerNo,
            Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn, boolean useGMTTime) {
        Calendar after = updatedAfterThisDateExclusive; int limit = itemsToReturn;
        Date initial = after == null ? null : after.getTime();
        var actor = getLoggedInInfo();
        var selection = ScheduleSyncHeaders.select(actor, "filtered", initial, limit, programId, providerNo, demographicId, useGMTTime);
        List<Appointment> appointments = invokeSync(() -> {
            if (!selection.keyset()) return scheduleManager.getAppointmentsByProgramProviderDemographicDate(
                    actor, programId, providerNo, demographicId, after, limit);
            var page = scheduleManager.getAppointmentsByProgramProviderDemographicPage(actor, programId, providerNo, demographicId,
                    new Date(selection.afterMillis()), selection.afterId(), limit);
            ScheduleSyncHeaders.complete(selection, page, Appointment::getUpdateDateTime, Appointment::getId);
            return page.items();
        });
        return AppointmentTransfer.toTransfers(appointments, useGMTTime);
    }

    private <T> T invokeSync(java.util.function.Supplier<T> read) {
        try { return read.get(); }
        catch (RuntimeException failure) {
            logger.error("Schedule synchronization failed: {}", io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(failure));
            throw new org.apache.cxf.binding.soap.SoapFault("Schedule synchronization failed", org.apache.cxf.binding.soap.SoapFault.FAULT_CODE_SERVER);
        }
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
