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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.github.carlos_emr.carlos.appointment.dto.PatientAppointmentExportRow;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.appointment.dto.AppointmentListItemDTO;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;

public interface OscarAppointmentDao extends AbstractDao<Appointment> {

    public boolean checkForConflict(Appointment appt);

    /**
     * Loads an appointment while acquiring a database write lock. Callers must
     * invoke this method inside the transaction that performs the corresponding
     * mutation so the lock remains held through validation and commit.
     *
     * @param appointmentNo appointment primary key
     * @return the locked appointment, or {@code null} when it does not exist
     */
    Appointment findForUpdate(Integer appointmentNo);

    /** Existing legacy-series members from the anchor date through endDate; bounded at 367 rows. */
    List<Appointment> findRecurringSeries(Appointment anchor, Date endDate);

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

    /**
     * Resolves the next appointment DATE for many patients in one query, for callers that would
     * otherwise call {@link #findNextAppointment(Integer)} once per row (the patient search returns
     * up to 100).
     *
     * <p>"Next" is the same selection {@link #findNextAppointment(Integer)} makes -- the earliest
     * uncancelled appointment that has not started yet -- so the two must be kept in step.</p>
     *
     * @param demographicIds patients to resolve; null or empty returns an empty map
     * @return a map from demographic number to that patient's next appointment date, holding no
     *         entry for a patient with no such appointment
     */
    public Map<Integer, Date> findNextAppointmentDates(Collection<Integer> demographicIds);

    public Appointment findDemoAppointmentToday(Integer demographicNo);

    public List<Appointment> findByProviderAndDate(String providerNo, Date appointmentDate);

    public List<Object[]> findAppointments(Date sDate, Date eDate);

    public List<Object[]> findPatientAppointments(String providerNo, Date from, Date to);

    /**
     * Streams patient appointment rows through a transaction-scoped cursor so
     * large report exports do not materialize the complete result set in memory.
     *
     * @param providerNo provider filter, or {@code null} for all providers
     * @param from inclusive start date, or {@code null}
     * @param to inclusive end date, or {@code null}
     * @param rowConsumer invoked once for each projected export row
     */
    void streamPatientAppointments(String providerNo, Date from, Date to,
                                   Consumer<PatientAppointmentExportRow> rowConsumer);

    /**
     * Lists appointments that still need billing for one provider and date range,
     * newest first. Billed ({@code B*}), No-Show ({@code N*}) and Cancelled
     * ({@code C*}) appointments are excluded, matching the Ontario "new report"
     * unbilled query ({@link #findBillingOnNewReportUnbilledRows}).
     *
     * <p>Equivalent to {@code search_unbill_history_daterange(providerNo,
     * startDate, endDate, false, false)}.</p>
     *
     * @param providerNo appointment provider number
     * @param startDate inclusive start of the appointment-date range
     * @param endDate inclusive end of the appointment-date range
     * @return unbilled, non-cancelled, attended-or-pending appointments
     */
    public List<Appointment> search_unbill_history_daterange(String providerNo, Date startDate, Date endDate);

    /**
     * Lists appointments that still need billing, with opt-in inclusion of
     * No-Show and Cancelled appointments for clinics that bill for missed visits.
     *
     * <p>Billed ({@code B*}) appointments and appointments without a patient
     * ({@code demographic_no = 0}) are always excluded. Status prefixes are
     * matched case-sensitively ({@code appointment.status} is
     * {@code utf8mb4_bin}), so lowercase custom statuses such as {@code c}
     * ("Customized 3") are never mistaken for Cancelled.</p>
     *
     * @param providerNo appointment provider number
     * @param startDate inclusive start of the appointment-date range
     * @param endDate inclusive end of the appointment-date range
     * @param includeNoShow {@code true} to keep {@code N*} (No-Show) appointments
     * @param includeCancelled {@code true} to keep {@code C*} (Cancelled) appointments
     * @return matching appointments ordered by date then start time, newest first
     * @since 2026-09-26
     */
    public List<Appointment> search_unbill_history_daterange(String providerNo, Date startDate, Date endDate,
                                                             boolean includeNoShow, boolean includeCancelled);

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

    public List<io.github.carlos_emr.carlos.commn.dao.projection.AppointmentProviderRow> findAppointmentAndProviderByAppointmentNo(Integer apptNo);

    public List<Appointment> searchappointmentday(String providerNo, Date appointmentDate, Integer programId);

    public List<Appointment> searchAppointmentDaySite(String providerNo, Date appointmentDate, Integer programId,
                                                      String selectedSiteId);

    public List<Object[]> findAppointmentsByDemographicIds(Set<String> demoIds, Date from, Date to);

    public List<Appointment> findByProgramProviderDemographicDate(Integer programId, String providerNo,
                                                                  Integer demographicId, Date updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Integer> findAllDemographicIdByProgramProvider(Integer programId, String providerNo);

    public List<Appointment> findDemoAppointmentsToday(Integer demographicNo);

    public List<Appointment> findDemoAppointmentsOnDate(Integer demographicNo, Date date);

    public int findProvideAppointmentTodayNum(String provide, String appdate);

    public int updateApptStatus(String ids, String status);

    public List<io.github.carlos_emr.carlos.commn.dao.projection.BillingOnNewReportUnbilledRow>
    findBillingOnNewReportUnbilledRows(String providerNo, String startDate, String endDate);

    public List<Object[]> listAppointmentsByPeriodProvider(Date sDate, Date eDate, List<Integer> providerNos);

    public List<Object[]> listProviderAppointmentCounts(Date sDate, Date eDate);

    /**
     * Returns lightweight appointment DTOs for a provider on a given date, with
     * pre-joined patient names from Demographic. Uses JPQL constructor expression
     * projection (17 fields vs 37 on entity).
     *
     * @param date Date the appointment date
     * @param providerNo String the provider number
     * @return List of AppointmentListItemDTO for the provider's daily schedule
     * @since 2026-04-11
     */
    public List<AppointmentListItemDTO> findDayAppointmentDTOs(Date date, String providerNo);
}
