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
import java.util.function.Consumer;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface ScheduleManager {

    /*Right now the date object passed is converted to a local time.
     *
     * As in, if the server's timezone is set to EST and the method is called with two data objects set to
     *
     * 2011-11-11 2:01 TZ america/new york
     * 2011-11-10 23:01 TZ america/los angeles
     *
     * They will both return the DayWorkSchedule for November 11 2011;
     *
     * The DayWorkSchedule returned will be in the server's local timezone.
     *
     */
    public DayWorkSchedule getDayWorkSchedule(String providerNo, Calendar date);

    public List<Appointment> getDayAppointments(LoggedInInfo loggedInInfo, String providerNo, Date date);

    public List<Appointment> getDayAppointments(LoggedInInfo loggedInInfo, String providerNo, Calendar date);

    public List<ScheduleTemplateCode> getScheduleTemplateCodes();

    public List<AppointmentType> getAppointmentTypes();

    public void addAppointment(LoggedInInfo loggedInInfo, Security security, Appointment appointment);

    public List<Appointment> getAppointmentsForPatient(LoggedInInfo loggedInInfo, Integer demographicId, int startIndex, int itemsToReturn);

    /**
     * Timestamp-only page of one patient's appointments with a provider in a program, in update order.
     * Follows the paging contract of {@link #getAppointmentUpdatedAfterDate}: never refused for a shared
     * update timestamp, and extended past {@code itemsToReturn} to the end of the last row's timestamp.
     */
    public List<Appointment> getAppointmentsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId, String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn);

    public Appointment getAppointment(LoggedInInfo loggedInInfo, Integer appointmentId);

    /**
     * Legacy server-entity update used by SOAP; does not validate a client's read version.
     * Read/echo clients must use the versioned overload below to reject lost updates.
     */
    public void updateAppointment(LoggedInInfo loggedInInfo, Appointment appointment);

    /**
     * Archives and edits the locked current row, validating the client's read version when supplied.
     * Missing or blank versions preserve legacy last-writer-wins behavior.
     *
     * @param loggedInInfo LoggedInInfo the authenticated caller
     * @param appointmentId Integer the appointment_no of the row to edit
     * @param expectedVersion String the editVersion token the client read; null or blank for a legacy unconditional edit
     * @param edit Consumer&lt;Appointment&gt; callback that applies editable fields to the locked row
     * @return Appointment the updated appointment, carrying its next editVersion
     * @throws AppointmentUpdateException when a supplied version is stale, or the row does not exist
     * @throws SecurityException when the caller lacks the _appointment write privilege
     */
    Appointment updateAppointment(LoggedInInfo loggedInInfo, Integer appointmentId,
            String expectedVersion, Consumer<Appointment> edit);

    public List<Appointment> getAppointmentsForDateRangeAndProvider(LoggedInInfo loggedInInfo, Date startTime, Date endTime, String providerNo);

    /**
     * Timestamp-only page of appointments updated strictly after a timestamp, in (update timestamp, id)
     * order. This is the published SOAP contract used by clients that send no schedule sync header: the
     * caller's next request is bounded by the newest update timestamp it received.
     *
     * <p>Update timestamps hold whole seconds, so rows share them. A page that would end inside such a
     * group is extended to the end of the group, so the caller's next request cannot skip rows: the result
     * can hold more than {@code itemsToReturn} rows, by at most the rest of that one group, and never more
     * than the DAO hard limit. A request is never refused because of a shared timestamp. Only when the hard
     * limit cannot hold the whole group is the page cut at {@code itemsToReturn}, as it was at 2026.08.0-beta6;
     * the explicit timestamp/ID methods below page through groups of any size.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated caller
     * @param updatedAfterThisDateExclusive Date exclusive lower bound on the update timestamp
     * @param itemsToReturn int requested page size; zero returns an empty list
     * @return List&lt;Appointment&gt; the page, after the provider-specific consent policy
     */
    public List<Appointment> getAppointmentUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Appointment> getAppointmentByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId, Date updatedAfterThisDateExclusive);

    /**
     * Timestamp-only page of archived appointment versions, in update order. Follows the paging contract
     * of {@link #getAppointmentUpdatedAfterDate}: never refused for a shared update timestamp, and extended
     * past {@code itemsToReturn} to the end of the last row's timestamp.
     */
    public List<AppointmentArchive> getAppointmentArchiveUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<AppointmentStatus> getAppointmentStatuses(LoggedInInfo loggedInInfo);

    public List<Integer> getAllDemographicIdByProgramProvider(LoggedInInfo loggedInInfo, Integer programId, String providerNo);

    public List<Object[]> listAppointmentsByPeriodProvider(LoggedInInfo loggedInInfo, Date sDate, Date eDate, String providers);

    public List<Object[]> listProviderAppointmentCounts(LoggedInInfo loggedInInfo, String sDateStr, String eDateStr);

    public boolean removeIfDoubleBooked(LoggedInInfo loggedInInfo, Calendar startTime, Calendar endTime, String providerNo, Appointment appointment);

    /** Explicit timestamp/ID sync; defaults preserve extension linkage and refuse unsupported semantics. */
    default AppointmentUpdatePage<Appointment> getAppointmentUpdatedPage(LoggedInInfo actor, Date after, int afterId, int limit) {
        throw new UnsupportedOperationException("Schedule keyset sync is not supported by this manager");
    }
    default AppointmentUpdatePage<AppointmentArchive> getAppointmentArchiveUpdatedPage(LoggedInInfo actor, Date after, int afterId, int limit) {
        throw new UnsupportedOperationException("Archive keyset sync is not supported by this manager");
    }
    default AppointmentUpdatePage<Appointment> getAppointmentsByProgramProviderDemographicPage(LoggedInInfo actor,
            Integer programId, String providerNo, Integer demographicId, Date after, int afterId, int limit) {
        throw new UnsupportedOperationException("Filtered keyset sync is not supported by this manager");
    }

}
