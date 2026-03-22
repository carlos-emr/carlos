/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;

/**
 * DAO interface for core operations.
 *
 * @since 2001
 */

public interface OscarAppointmentDao extends AbstractDao<Appointment> {

    /**
     * Check For Conflict.
     *
     * @param appt Appointment the appt
     * @return boolean
     */
    public boolean checkForConflict(Appointment appt);

    /**
     * Get Appointment History.
     *
     * @param demographicNo Integer the demographicNo
     * @param offset Integer the offset
     * @param limit Integer the limit
     * @return List<Appointment>
     */
    public List<Appointment> getAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    /**
     * Get All Appointment History.
     *
     * @param demographicNo Integer the demographicNo
     * @param offset Integer the offset
     * @param limit Integer the limit
     * @return List<Appointment>
     */
    public List<Appointment> getAllAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    /**
     * Get Deleted Appointment History.
     *
     * @param demographicNo Integer the demographicNo
     * @param offset Integer the offset
     * @param limit Integer the limit
     * @return List<AppointmentArchive>
     */
    public List<AppointmentArchive> getDeletedAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    /**
     * Get Appointment History.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Appointment>
     */
    public List<Appointment> getAppointmentHistory(Integer demographicNo);

    /**
     * Archive Appointment.
     *
     * @param appointmentNo int the appointmentNo
     */
    public void archiveAppointment(int appointmentNo);

    /**
     * Get All By Demographic No.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Appointment>
     */
    public List<Appointment> getAllByDemographicNo(Integer demographicNo);

    /**
     * Find By Update Date.
     *
     * @param updatedAfterThisDateExclusive Date the updatedAfterThisDateExclusive
     * @param itemsToReturn int the itemsToReturn
     * @return List<Appointment>
     */
    public List<Appointment> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Find By Demographic Id Update Date.
     *
     * @param demographicId Integer the demographicId
     * @param updatedAfterThisDateExclusive Date the updatedAfterThisDateExclusive
     * @return List<Appointment>
     */
    public List<Appointment> findByDemographicIdUpdateDate(Integer demographicId, Date updatedAfterThisDateExclusive);

    /**
     * Get All By Demographic No Since.
     *
     * @param demographicNo Integer the demographicNo
     * @param lastUpdateDate Date the lastUpdateDate
     * @return List<Appointment>
     */
    public List<Appointment> getAllByDemographicNoSince(Integer demographicNo, Date lastUpdateDate);

    /**
     * Get All Demographic No Since.
     *
     * @param lastUpdateDate Date the lastUpdateDate
     * @param programs List<Program> the programs
     * @return List<Integer>
     */
    public List<Integer> getAllDemographicNoSince(Date lastUpdateDate, List<Program> programs);

    /**
     * Find By Date Range.
     *
     * @param startTime Date the startTime
     * @param endTime Date the endTime
     * @return List<Appointment>
     */
    public List<Appointment> findByDateRange(Date startTime, Date endTime);

    /**
     * Find By Date Range And Provider.
     *
     * @param startTime Date the startTime
     * @param endTime Date the endTime
     * @param providerNo String the providerNo
     * @return List<Appointment>
     */
    public List<Appointment> findByDateRangeAndProvider(Date startTime, Date endTime, String providerNo);

    /**
     * Get By Provider And Day.
     *
     * @param date Date the date
     * @param providerNo String the providerNo
     * @return List<Appointment>
     */
    public List<Appointment> getByProviderAndDay(Date date, String providerNo);

    /**
     * Get By Demo No And Day.
     *
     * @param demoNo int the demoNo
     * @param date Date the date
     * @return List<Appointment>
     */
    public List<Appointment> getByDemoNoAndDay(int demoNo, Date date);

    /**
     * Find By Provider And Dayand Not Statuses.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @param notThisStatus String[] the notThisStatus
     * @return List<Appointment>
     */
    public List<Appointment> findByProviderAndDayandNotStatuses(String providerNo, Date date, String[] notThisStatus);

    /**
     * Find By Provider And Dayand Not Status.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @param notThisStatus String the notThisStatus
     * @return List<Appointment>
     */
    public List<Appointment> findByProviderAndDayandNotStatus(String providerNo, Date date, String notThisStatus);

    /**
     * Find By Provider Day And Status.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @param status String the status
     * @return List<Appointment>
     */
    public List<Appointment> findByProviderDayAndStatus(String providerNo, Date date, String status);

    /**
     * Find By Day And Status.
     *
     * @param date Date the date
     * @param status String the status
     * @return List<Appointment>
     */
    public List<Appointment> findByDayAndStatus(Date date, String status);

    public List<Appointment> find(Date date, String providerNo, Date startTime, Date endTime, String name,
                                  String notes, String reason, Date createDateTime, String creator, Integer demographicNo);

    /**
     * Find By Demographic Id.
     *
     * @param demographicId Integer the demographicId
     * @param startIndex int the startIndex
     * @param itemsToReturn int the itemsToReturn
     * @return List<Appointment>
     */
    public List<Appointment> findByDemographicId(Integer demographicId, int startIndex, int itemsToReturn);

    /**
     * Find All.
     * @return List<Appointment>
     */
    public List<Appointment> findAll();

    /**
     * Find Non Cancelled Future Appointments.
     *
     * @param demographicId Integer the demographicId
     * @return List<Appointment>
     */
    public List<Appointment> findNonCancelledFutureAppointments(Integer demographicId);

    /**
     * Find Next Appointment.
     *
     * @param demographicId Integer the demographicId
     * @return Appointment
     */
    public Appointment findNextAppointment(Integer demographicId);

    /**
     * Find Demo Appointment Today.
     *
     * @param demographicNo Integer the demographicNo
     * @return Appointment
     */
    public Appointment findDemoAppointmentToday(Integer demographicNo);

    /**
     * Find By Provider And Date.
     *
     * @param providerNo String the providerNo
     * @param appointmentDate Date the appointmentDate
     * @return List<Appointment>
     */
    public List<Appointment> findByProviderAndDate(String providerNo, Date appointmentDate);

    /**
     * Find Appointments.
     *
     * @param sDate Date the sDate
     * @param eDate Date the eDate
     * @return List<Object[]>
     */
    public List<Object[]> findAppointments(Date sDate, Date eDate);

    /**
     * Find Patient Appointments.
     *
     * @param providerNo String the providerNo
     * @param from Date the from
     * @param to Date the to
     * @return List<Object[]>
     */
    public List<Object[]> findPatientAppointments(String providerNo, Date from, Date to);

    /**
     * Search_unbill_history_daterange.
     *
     * @param providerNo String the providerNo
     * @param startDate Date the startDate
     * @param endDate Date the endDate
     * @return List<Appointment>
     */
    public List<Appointment> search_unbill_history_daterange(String providerNo, Date startDate, Date endDate);

    /**
     * Find By Date And Provider.
     *
     * @param date Date the date
     * @param provider_no String the provider_no
     * @return List<Appointment>
     */
    public List<Appointment> findByDateAndProvider(Date date, String provider_no);

    /**
     * Search_appt.
     *
     * @param startTime Date the startTime
     * @param endTime Date the endTime
     * @param providerNo String the providerNo
     * @return List<Appointment>
     */
    public List<Appointment> search_appt(Date startTime, Date endTime, String providerNo);

    public List<Appointment> search_appt(Date date, String providerNo, Date startTime1, Date startTime2, Date endTime1,
                                         Date endTime2, Date startTime3, Date endTime3, Integer programId);

    /**
     * Search_appt_future.
     *
     * @param demographicNo Integer the demographicNo
     * @param from Date the from
     * @param to Date the to
     * @return List<Object[]>
     */
    public List<Object[]> search_appt_future(Integer demographicNo, Date from, Date to);

    /**
     * Search_appt_past.
     *
     * @param demographicNo Integer the demographicNo
     * @param from Date the from
     * @param to Date the to
     * @return List<Object[]>
     */
    public List<Object[]> search_appt_past(Integer demographicNo, Date from, Date to);

    public Appointment search_appt_no(String providerNo, Date appointmentDate, Date startTime, Date endTime,
                                      Date createDateTime, String creator, Integer demographicNo);

    public List<Object[]> search_appt_data1(String providerNo, Date appointmentDate, Date startTime, Date endTime,
                                            Date createDateTime, String creator, Integer demographicNo);

    /**
     * Export_appt.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Object[]>
     */
    public List<Object[]> export_appt(Integer demographicNo);

    public List<Appointment> search_otherappt(Date appointmentDate, Date startTime1, Date endTime1, Date startTime2,
                                              Date startTime3);

    /**
     * Search_group_day_appt.
     *
     * @param myGroup String the myGroup
     * @param demographicNo Integer the demographicNo
     * @param appointmentDate Date the appointmentDate
     * @return List<Appointment>
     */
    public List<Appointment> search_group_day_appt(String myGroup, Integer demographicNo, Date appointmentDate);

    /**
     * Find By Date.
     *
     * @param appointmentDate Date the appointmentDate
     * @return Appointment
     */
    public Appointment findByDate(Date appointmentDate);

    /**
     * Find Appointment And Provider By Appointment No.
     *
     * @param apptNo Integer the apptNo
     * @return List<Object[]>
     */
    public List<Object[]> findAppointmentAndProviderByAppointmentNo(Integer apptNo);

    /**
     * Searchappointmentday.
     *
     * @param providerNo String the providerNo
     * @param appointmentDate Date the appointmentDate
     * @param programId Integer the programId
     * @return List<Appointment>
     */
    public List<Appointment> searchappointmentday(String providerNo, Date appointmentDate, Integer programId);

    public List<Appointment> searchAppointmentDaySite(String providerNo, Date appointmentDate, Integer programId,
                                                      String selectedSiteId);

    /**
     * Find Appointments By Demographic Ids.
     *
     * @param demoIds Set<String> the demoIds
     * @param from Date the from
     * @param to Date the to
     * @return List<Object[]>
     */
    public List<Object[]> findAppointmentsByDemographicIds(Set<String> demoIds, Date from, Date to);

    public List<Appointment> findPatientBilledAppointmentsByProviderAndAppointmentDate(
            String providerNo,
            Date startAppointmentDate,
            Date endAppointmentDate);

    public List<Appointment> findPatientUnbilledAppointmentsByProviderAndAppointmentDate(
            String providerNo,
            Date startAppointmentDate,
            Date endAppointmentDate);

    public List<Appointment> findByProgramProviderDemographicDate(Integer programId, String providerNo,
                                                                  Integer demographicId, Date updatedAfterThisDateExclusive, int itemsToReturn);

    /**
     * Find All Demographic Id By Program Provider.
     *
     * @param programId Integer the programId
     * @param providerNo String the providerNo
     * @return List<Integer>
     */
    public List<Integer> findAllDemographicIdByProgramProvider(Integer programId, String providerNo);

    /**
     * Find Demo Appointments Today.
     *
     * @param demographicNo Integer the demographicNo
     * @return List<Appointment>
     */
    public List<Appointment> findDemoAppointmentsToday(Integer demographicNo);

    /**
     * Find Demo Appointments On Date.
     *
     * @param demographicNo Integer the demographicNo
     * @param date Date the date
     * @return List<Appointment>
     */
    public List<Appointment> findDemoAppointmentsOnDate(Integer demographicNo, Date date);

    /**
     * Find Provide Appointment Today Num.
     *
     * @param provide String the provide
     * @param appdate String the appdate
     * @return int
     */
    public int findProvideAppointmentTodayNum(String provide, String appdate);

    /**
     * Update Appt Status.
     *
     * @param ids String the ids
     * @param status String the status
     * @return int
     */
    public int updateApptStatus(String ids, String status);

    /**
     * List Appointments By Period Provider.
     *
     * @param sDate Date the sDate
     * @param eDate Date the eDate
     * @param providerNos List<Integer> the providerNos
     * @return List<Object[]>
     */
    public List<Object[]> listAppointmentsByPeriodProvider(Date sDate, Date eDate, List<Integer> providerNos);

    /**
     * List Provider Appointment Counts.
     *
     * @param sDate Date the sDate
     * @param eDate Date the eDate
     * @return List<Object[]>
     */
    public List<Object[]> listProviderAppointmentCounts(Date sDate, Date eDate);

}
