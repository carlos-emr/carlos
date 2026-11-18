/**
 * Modifications by CARLOS Contributors, 2026.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import org.apache.commons.lang3.time.DateFormatUtils;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.commn.model.enumerator.ConsultationRequestExtKey;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListRow;
import io.github.carlos_emr.carlos.provider.dto.ProviderSummaryDTO;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationNameFormat;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class EctViewConsultationRequestsUtil {

   /**
    * Determines whether a list row has a usable patient identifier for bulk ticklers.
    * Missing patients remain visible in consultation lists but cannot receive a tickler.
    * @param demographicNo patient identifier from the consultation list
    * @return true only for a positive decimal patient identifier
    */
   public static boolean isTicklerDemographic(String demographicNo) {
      return demographicNo != null && demographicNo.matches("[1-9][0-9]*");
   }

   /** The follow-up text the overdue-consultation link suggests for the new task. */
   public static final String OVERDUE_TICKLER_MESSAGE =
         "Patient has Consultation Letter with a status of 'Nothing Done' for over one week";

   /**
    * Builds the link from the Consultations list to the standard Add Tickler form for one patient
    * whose referral is overdue. The form is a single-patient page that reads {@code demographic_no}
    * and starts its Reminder Message box from {@code ticklerMessage}; the list used to send the
    * retired bulk action's {@code demo} and {@code message}, which the form ignores, so staff got a
    * blank task (issue #4999). Keep these names in step with {@code ticklerAdd.jsp}.
    *
    * @param contextPath the web application's context path ({@code request.getContextPath()})
    * @param demographicNo the patient, which must satisfy {@link #isTicklerDemographic(String)}
    * @return a root-relative URL, percent-encoded for a query string; callers still HTML-encode it
    *         when writing it into an attribute
    * @throws IllegalArgumentException if the patient identifier cannot receive a tickler
    */
   public static String overdueTicklerPath(String contextPath, String demographicNo) {
      if (!isTicklerDemographic(demographicNo)) {
         throw new IllegalArgumentException("A positive numeric patient identifier is required");
      }
      return (contextPath == null ? "" : contextPath)
            + "/tickler/ViewAddTickler?demographic_no=" + SafeEncode.forUriComponent(demographicNo)
            + "&ticklerMessage=" + SafeEncode.forUriComponent(OVERDUE_TICKLER_MESSAGE);
   }
   public List<String> ids;
   public List<String> status;
   public List<String> patient;
   public List<String> teams;
   public List<String> provider;
   public List<String> service;
   public List<String> vSpecialist;
   public List<String> date;
   public List<String> demographicNo;
   public List<String> apptDate;
   public List<String> patientWillBook;
   public List<String> urgency;
   public List<String> followUpDate;
   public List<String> providerNo;   
   public List<String> siteName;
   public List<ProviderSummaryDTO> consultProvider;
   public List<Boolean> eReferral; 
   /** Whether the last team page load found a row beyond the page it was asked for. */
   private boolean moreRows;
   /** Whether the last team load left out rows of patients the user may not read. */
   private boolean restrictedRows;

   /**
    * Whether the list continues after the page loaded by
    * {@link #estConsultationPageByTeam(LoggedInInfo, ConsultationListFilterDto, int)}. Rows left
    * out of the page do not change the answer.
    */
   public boolean hasMoreRows() {
      return moreRows;
   }

   /**
    * Whether the last team load left out one or more rows because the user may not read the
    * patient's record. Such a row is not listed at all: not its patient, not its referral.
    */
   public boolean hasRestrictedRows() {
      return restrictedRows;
   }

   /**
    * Loads one page of the team list, for a caller that fetches a lookahead row to learn whether a
    * next page exists.
    *
    * <p>The filter's limit must be {@code pageSize + 1}. Only the first {@code pageSize} rows
    * fetched are the page; the extra row answers {@link #hasMoreRows()} and is neither listed nor
    * looked up. Rows of patients the user may not read are left out of the page (see
    * {@link #hasRestrictedRows()}) without pulling the next page's rows forward, so the offsets of
    * the previous and next pages do not depend on what the user may read.</p>
    *
    * @param loggedInInfo the current user, for the per-patient read checks
    * @param filter the list filters, with the page's offset and a limit of {@code pageSize + 1}
    * @param pageSize the number of rows on a page
    * @return true when the page loaded, with or without rows; false when it could not be loaded, in
    *         which case every list is empty and the caller must report a failure rather than an
    *         empty result
    * @since 2026-11-12
    */
   public boolean estConsultationPageByTeam(LoggedInInfo loggedInInfo, ConsultationListFilterDto filter, int pageSize) {
      initializeLists();
      try {
         List<ConsultationListRow> rows = SpringUtils.getBean(ConsultationRequestDao.class)
                 .getConsultationListRows(filter);
         boolean lookaheadRowFound = rows.size() > pageSize;
         boolean loaded = populate(loggedInInfo, rows.subList(0, Math.min(pageSize, rows.size())), false);
         moreRows = lookaheadRowFound;
         return loaded;
      } catch (Exception e) {
         return loadFailed(e);
      }
   }
   
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team) {   
      return estConsultationVecByTeam(loggedInInfo, team, false, null, null);
   }
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team, boolean showCompleted) {   
      return estConsultationVecByTeam(loggedInInfo, team, showCompleted, null, null);
   }   
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team, boolean showCompleted, Date startDate, Date endDate) {
      return estConsultationVecByTeam(loggedInInfo, team, showCompleted, null, null, null);
   }   
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team, boolean showCompleted, Date startDate, Date endDate, String orderby) {   
      return estConsultationVecByTeam(loggedInInfo, team, showCompleted, null, null, null, null);
   }   
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc) { 
      return estConsultationVecByTeam(loggedInInfo, team, showCompleted, null, null, null, null, null, null, null);
   }  
            
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, String team, boolean showCompleted, Date startDate, Date endDate, String orderby, String desc, String searchDate, Integer offset, Integer limit) {
      return estConsultationVecByTeam(loggedInInfo, new ConsultationListFilterDto(team, showCompleted, startDate, endDate,
              orderby, desc, searchDate, offset, limit, null, null));
   }

   /**
    * Loads the rows the filter selects using scalar rows and bulk lookups, retaining
    * patient-specific read checks. Rows of patients the user may not read are left out (see
    * {@link #hasRestrictedRows()}); one restricted patient does not fail the list (issue #5775).
    *
    * @return true when the rows loaded; false when they could not be loaded, in which case every
    *         list is empty
    */
   public boolean estConsultationVecByTeam(LoggedInInfo loggedInInfo, ConsultationListFilterDto filter) {
      initializeLists();
      try {
         return populate(loggedInInfo, SpringUtils.getBean(ConsultationRequestDao.class)
                 .getConsultationListRows(filter), false);
      } catch (Exception e) {
         return loadFailed(e);
      }
   }

   /**
    * Loads the patient's consultation rows without materializing eager associations. All rows
    * belong to the one patient, so a user who may not read that patient gets no rows and false.
    */
   public boolean estConsultationVecByDemographic(LoggedInInfo loggedInInfo, String demoNo) {
      initializeLists();
      try {
         return populate(loggedInInfo, SpringUtils.getBean(ConsultationRequestDao.class)
                 .getConsultationListRows(Integer.valueOf(demoNo)), true);
      } catch (Exception e) {
         return loadFailed(e);
      }
   }

   /**
    * Leaves every list empty, so a failure part-way through cannot publish some rows or leave the
    * parallel lists at different lengths, and logs where it failed. The trace carries exception
    * types and code locations only: a message could quote a patient's data.
    */
   private boolean loadFailed(Exception e) {
      initializeLists();
      MiscUtils.getLogger().error("Consultation list could not be loaded trace={}", LogSafe.exceptionTrace(e));
      return false;
   }

   private void initializeLists() {
      moreRows = false;
      restrictedRows = false;
      ids = new ArrayList<>();
      status = new ArrayList<>();
      patient = new ArrayList<>();
      provider = new ArrayList<>();
      providerNo = new ArrayList<>();
      teams = new ArrayList<>();
      service = new ArrayList<>();
      vSpecialist = new ArrayList<>();
      urgency = new ArrayList<>();
      date = new ArrayList<>();
      demographicNo = new ArrayList<>();
      siteName = new ArrayList<>();
      patientWillBook = new ArrayList<>();
      apptDate = new ArrayList<>();
      followUpDate = new ArrayList<>();
      consultProvider = new ArrayList<>();
      eReferral = new ArrayList<>();
   }

   private boolean populate(LoggedInInfo loggedInInfo, List<ConsultationListRow> rows, boolean patientChart) {
      if (rows.isEmpty()) return true;
      Set<Integer> patientIds = new LinkedHashSet<>();
      for (ConsultationListRow row : rows) {
         if (row.demographicId() != null) patientIds.add(row.demographicId());
      }
      // The manager decides each distinct patient's read access before any name is fetched.
      // Do not replace it with a direct demographic join: the list's scope filter is an additional gate.
      DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
      Map<Integer, String> names;
      if (patientChart) {
         // One patient, whose chart the caller opened: a denial fails the list, as it always has.
         names = demographicManager.getDemographicFormattedNames(loggedInInfo, patientIds);
      } else {
         // A team page mixes patients. A row whose patient this user may not read is left out
         // whole, as it was when names were looked up row by row; unlike then, the readable rows
         // after it are kept (issue #5775). A row with no patient, or whose patient record is
         // gone, stays listed without a name.
         names = demographicManager.getReadableDemographicFormattedNames(loggedInInfo, patientIds);
         List<ConsultationListRow> readableRows = new ArrayList<>();
         for (ConsultationListRow row : rows) {
            if (row.demographicId() == null || row.demographicId() == 0 || names.containsKey(row.demographicId())) {
               readableRows.add(row);
            }
         }
         restrictedRows = readableRows.size() != rows.size();
         rows = readableRows;
         if (rows.isEmpty()) return true;
      }
      Set<String> requestingProviders = new LinkedHashSet<>();
      for (ConsultationListRow row : rows) {
         if (row.providerNo() != null && !row.providerNo().isEmpty()) requestingProviders.add(row.providerNo());
      }
      Map<String, ProviderSummaryDTO> providers = requestingProviders.isEmpty() ? Map.of()
              : SpringUtils.getBean(ProviderDao.class).getProviderSummariesByIds(requestingProviders);
      Map<Integer, Map<String, String>> extensions = new HashMap<>();
      for (ConsultationRequestExt ext : SpringUtils.getBean(ConsultationRequestExtDao.class)
              .getConsultationRequestExts(rows.stream().map(ConsultationListRow::id).toList())) {
         Map<String, String> values = extensions.computeIfAbsent(ext.getRequestId(), ignored -> new HashMap<>());
         // The chart's legacy key lookup took the first match; the team list's map took the last.
         if (!patientChart || !values.containsKey(ext.getKey())) values.put(ext.getKey(), ext.getValue());
      }
      for (ConsultationListRow row : rows) {
         Map<String, String> extras = extensions.getOrDefault(row.id(), Map.of());
         String description = row.resolvedServiceId() == null ? (patientChart ? "unknown" : "") : row.serviceDescription();
         if (Integer.valueOf(0).equals(row.serviceId())) {
            description = patientChart ? extras.get(ConsultationRequestExtKey.EREFERRAL_SERVICE.getKey())
                    : extras.getOrDefault(ConsultationRequestExtKey.EREFERRAL_SERVICE.getKey(), "");
         }
         String specialist = row.specialistId() == null ? "N/A"
                 : formatSpecialistName(row.specialistLastName(), row.specialistFirstName());
         if (row.specialistId() == null && Integer.valueOf(0).equals(row.serviceId())) {
            specialist = patientChart ? extras.get(ConsultationRequestExtKey.EREFERRAL_DOCTOR.getKey())
                    : extras.getOrDefault(ConsultationRequestExtKey.EREFERRAL_DOCTOR.getKey(), "N/A");
         }
         ids.add(row.id().toString());
         status.add(row.status());
         String patientName = row.demographicId() == null ? "" : names.getOrDefault(row.demographicId(), "");
         // Only beside a name that was found: the team lookup also lists a readable patient with no record.
         if (!patientName.isEmpty() && row.patientAlias() != null && !row.patientAlias().isEmpty()) {
            patientName += " (" + row.patientAlias() + ")";
         }
         patient.add(patientName);
         provider.add(row.mrpNo() == null ? "N/A" : row.mrpLastName() + ", " + row.mrpFirstName());
         providerNo.add(row.mrpNo() == null ? "-1" : row.mrpNo());
         demographicNo.add(row.demographicId() == null ? "" : row.demographicId().toString());
         service.add(description);
         vSpecialist.add(specialist);
         urgency.add(row.urgency() == null ? "" : row.urgency());
         siteName.add(row.siteName());
         teams.add(row.sendTo());
         eReferral.add(extras.containsKey(ConsultationRequestExtKey.EREFERRAL_REF.getKey()));
         date.add(row.referralDate() == null ? "" : DateFormatUtils.ISO_DATE_FORMAT.format(row.referralDate()));
         String appointment = row.appointmentDate() == null ? "N/A" : DateFormatUtils.ISO_DATE_FORMAT.format(row.appointmentDate());
         if (row.appointmentDate() != null && row.appointmentTime() != null) {
            appointment += " " + DateFormatUtils.ISO_TIME_FORMAT.format(row.appointmentTime());
         }
         apptDate.add(appointment);
         patientWillBook.add("" + Boolean.TRUE.equals(row.patientWillBook()));
         followUpDate.add(row.followUpDate() == null ? "N/A" : DateFormatUtils.ISO_DATE_FORMAT.format(row.followUpDate()));
         consultProvider.add(row.providerNo() == null ? null : providers.get(row.providerNo()));
      }
      return true;
   }

    /**
     * Formats a specialist as "Last, First", dropping whichever part is missing so an incomplete
     * specialist record never renders as "Smith, null" in the consult list or the eChart box.
     *
     * @param lastName String the specialist's last name; may be null or blank
     * @param firstName String the specialist's first name; may be null or blank
     * @return String "Last, First", the single part present, or an empty string
     * @since 2026-09-24
     */
    static String formatSpecialistName(String lastName, String firstName) {
        return ConsultationNameFormat.lastCommaFirst(lastName, firstName);
    }
}
