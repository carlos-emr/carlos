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

public interface OscarAppointmentDao extends AbstractDao<Appointment> {

    public boolean checkForConflict(Appointment appt);

    public List<Appointment> getAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    public List<Appointment> getAllAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    public List<AppointmentArchive> getDeletedAppointmentHistory(Integer demographicNo, Integer offset, Integer limit);

    public List<Appointment> getAppointmentHistory(Integer demographicNo);

    public void archiveAppointment(int appointmentNo);

    public List<Appointment> getAllByDemographicNo(Integer demographicNo);

    public List<Appointment> findByUpdateDate(Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Appointment> findByDemographicIdUpdateDate(Integer demographicId, Date updatedAfterThisDateExclusive);

    public List<Appointment> getAllByDemographicNoSince(Integer demographicNo, Date lastUpdateDate);

    public List<Integer> getAllDemographicNoSince(Date lastUpdateDate, List<Program> programs);

    public List<Appointment> findByDateRange(Date startTime, Date endTime);

    public List<Appointment> findByDateRangeAndProvider(Date startTime, Date endTime, String providerNo);

    public List<Appointment> getByProviderAndDay(Date date, String providerNo);

    public List<Appointment> getByDemoNoAndDay(int demoNo, Date date);

    public List<Appointment> findByProviderAndDayandNotStatuses(String providerNo, Date date, String[] notThisStatus);

    public List<Appointment> findByProviderAndDayandNotStatus(String providerNo, Date date, String notThisStatus);

    public List<Appointment> findByProviderDayAndStatus(String providerNo, Date date, String status);

    public List<Appointment> findByDayAndStatus(Date date, String status);

    public List<Appointment> find(Date date, String providerNo, Date startTime, Date endTime, String name,
                                  String notes, String reason, Date createDateTime, String creator, Integer demographicNo);

    public List<Appointment> findByDemographicId(Integer demographicId, int startIndex, int itemsToReturn);

    public List<Appointment> findAll();

    public List<Appointment> findNonCancelledFutureAppointments(Integer demographicId);

    public Appointment findNextAppointment(Integer demographicId);

    public Appointment findDemoAppointmentToday(Integer demographicNo);

    public List<Appointment> findByProviderAndDate(String providerNo, Date appointmentDate);

    public List<Object[]> findAppointments(Date sDate, Date eDate);

    public List<Object[]> findPatientAppointments(String providerNo, Date from, Date to);

    public List<Appointment> search_unbill_history_daterange(String providerNo, Date startDate, Date endDate);

    public List<Appointment> findByDateAndProvider(Date date, String provider_no);

    public List<Appointment> search_appt(Date startTime, Date endTime, String providerNo);

    public List<Appointment> search_appt(Date date, String providerNo, Date startTime1, Date startTime2, Date endTime1,
                                         Date endTime2, Date startTime3, Date endTime3, Integer programId);

    public List<Object[]> search_appt_future(Integer demographicNo, Date from, Date to);

    public List<Object[]> search_appt_past(Integer demographicNo, Date from, Date to);

    public Appointment search_appt_no(String providerNo, Date appointmentDate, Date startTime, Date endTime,
                                      Date createDateTime, String creator, Integer demographicNo);

    public List<Object[]> search_appt_data1(String providerNo, Date appointmentDate, Date startTime, Date endTime,
                                            Date createDateTime, String creator, Integer demographicNo);

    public List<Object[]> export_appt(Integer demographicNo);

    public List<Appointment> search_otherappt(Date appointmentDate, Date startTime1, Date endTime1, Date startTime2,
                                              Date startTime3);

    public List<Appointment> search_group_day_appt(String myGroup, Integer demographicNo, Date appointmentDate);

    public Appointment findByDate(Date appointmentDate);

    public List<Object[]> findAppointmentAndProviderByAppointmentNo(Integer apptNo);

    public List<Appointment> searchappointmentday(String providerNo, Date appointmentDate, Integer programId);

    public List<Appointment> searchAppointmentDaySite(String providerNo, Date appointmentDate, Integer programId,
                                                      String selectedSiteId);

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

    public List<Integer> findAllDemographicIdByProgramProvider(Integer programId, String providerNo);

    public List<Appointment> findDemoAppointmentsToday(Integer demographicNo);

    public List<Appointment> findDemoAppointmentsOnDate(Integer demographicNo, Date date);

    public int findProvideAppointmentTodayNum(String provide, String appdate);

    public int updateApptStatus(String ids, String status);

    public List<Object[]> listAppointmentsByPeriodProvider(Date sDate, Date eDate, List<Integer> providerNos);

    public List<Object[]> listProviderAppointmentCounts(Date sDate, Date eDate);

}
