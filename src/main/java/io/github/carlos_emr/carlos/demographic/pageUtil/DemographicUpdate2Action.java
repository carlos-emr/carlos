/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExtArchive;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.demographic.util.DemographicXml;
import io.github.carlos_emr.carlos.managers.ChartConsentOutcome;
import io.github.carlos_emr.carlos.managers.ChartConsentRequest;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.model.PreventionManager;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.waitinglist.WaitingListAvailabilityService;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action that processes a demographic record update (save). Replaces the
 * business-logic scriptlets previously embedded in
 * {@code demographicupdatearecord.jsp}.
 *
 * <p>Only POST requests are accepted. Returns {@code "duplicate"} when a HIN
 * duplicate is detected (for a different patient), {@code "methodNotAllowed"}
 * for non-POST requests, and {@code "success"} on normal completion. On the
 * success path the action may also issue a redirect to
 * {@code DemographicEdit} when no waiting-list interaction is required.</p>
 *
 * @since 2026-04-04
 */
public class DemographicUpdate2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private transient SecurityInfoManager securityInfoManager;

    public DemographicUpdate2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public DemographicUpdate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /** A container service is not action state; reacquire it if the action was deserialized. */
    private SecurityInfoManager securityInfoManager() {
        if (securityInfoManager == null) {
            securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        }
        return securityInfoManager;
    }

    /**
     * Validates session and privileges, then applies all update logic extracted
     * from the former {@code demographicupdatearecord.jsp} scriptlets.
     *
     * @return {@code "success"}, {@code "duplicate"}, or {@code "methodNotAllowed"},
     *         {@code NONE} after an HTTP 409 for a waiting list that is no longer
     *         selectable, or {@code null} when a redirect has been issued
     * @throws SecurityException if the session is missing or the provider lacks
     *         {@code _demographic} write privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws IOException {
        if (!"POST".equals(request.getMethod())) {
            return "methodNotAllowed";
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.warn("DemographicUpdate2Action: missing session");
            throw new SecurityException("missing required session");
        }
        if (!securityInfoManager().hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            logger.warn("DemographicUpdate2Action: provider {} lacks _demographic write privilege",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_demographic)");
        }

        // A list choice must still be one the Master Record would offer now: the list may
        // have been archived or moved, or the provider's group changed, since the form was
        // drawn. Refuse before ANY write, so a combined save never half-applies (the note
        // and date are edited together with the list, so dropping just the waiting-list
        // part would report edits as saved that were not). "0" is the form's "no list".
        String submittedListId = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("list_id"));
        boolean waitingListChosen = submittedListId != null && !"0".equals(submittedListId);
        int waitingListId = 0;
        if (waitingListChosen) {
            waitingListId = parseListId(submittedListId);
            ProviderPreference providerPreference = (ProviderPreference) request.getSession()
                    .getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
            if (!SpringUtils.getBean(WaitingListAvailabilityService.class)
                    .isSelectable(providerPreference, waitingListId)) {
                logger.warn("DemographicUpdate2Action: refused a waiting list that is not currently selectable");
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "The selected waiting list is no longer available. Reload the patient record and choose a current list.");
                return NONE;
            }
        }

        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        DemographicArchiveDao demographicArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);
        DemographicCustDao demographicCustDao = SpringUtils.getBean(DemographicCustDao.class);
        DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
        DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
        WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);
        OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

        String proNo = (String) request.getSession().getAttribute("user");
        String demoNo = request.getParameter("demographic_no");
        if (demoNo == null || demoNo.trim().isEmpty()) {
            addActionError("Missing patient record identifier");
            return ERROR;
        }
        int demographicNo;
        try {
            demographicNo = Integer.parseInt(demoNo);
        } catch (NumberFormatException e) {
            logger.warn("DemographicUpdate2Action: invalid demographic_no={}", LogSafe.sanitize(demoNo));
            addActionError("Invalid patient record identifier");
            return ERROR;
        }

        // The record-level check the Master Record itself enforces (DemographicEdit2Action): a user
        // kept from this patient may neither change the record nor read it back from the conflict page.
        if (!securityInfoManager().hasPrivilege(loggedInInfo, "_demographic", "w", String.valueOf(demographicNo))) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        List<String> identityFieldErrors = Demographic.validateIdentityFieldLengths(
                request.getParameter("nameUsed"), request.getParameter("pronouns"), request.getParameter("gender"));
        if (!identityFieldErrors.isEmpty()) {
            request.setAttribute("fieldLengthValidationErrors", identityFieldErrors);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "validationError";
        }

        Demographic demographic = demographicDao.getDemographic(demoNo);
        if (demographic == null) {
            logger.warn("DemographicUpdate2Action: demographic_no={} not found", LogSafe.sanitize(demoNo));
            addActionError("Patient record not found");
            return ERROR;
        }

        applyForm(demographic, proNo);

        List<String> fieldLengthValidationErrors = demographic.validateFieldLengths();
        if (!fieldLengthValidationErrors.isEmpty()) {
            logger.warn("DemographicUpdate2Action: rejected demographic input due to field length limits: {}",
                    LogSafe.sanitize(String.join("; ", fieldLengthValidationErrors)));
            request.setAttribute("fieldLengthValidationErrors", fieldLengthValidationErrors);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "validationError";
        }

        // The extension rows this form writes. Their ids are resolved from the patient's existing
        // rows when they are saved, not taken from the form (see the write phase below).
        List<DemographicExt> extensions = new ArrayList<>();
        for (String key : DemographicEditVersion.editedExtensionKeys()) {
            extensions.add(new DemographicExt(proNo, demographicNo, key, request.getParameter(key)));
        }

        // --- HIN duplicate check must run before any persistence to prevent partial updates ---
        boolean hinDupCheckException = false;
        String hcType = request.getParameter("hc_type");
        String ver = request.getParameter("ver");
        if (hcType != null && ver != null && hcType.equals("BC") && ver.equals("66")) {
            hinDupCheckException = true;
        }

        if (request.getParameter("hin") != null && request.getParameter("hin").length() > 5 && !hinDupCheckException) {
            String paramNameHin = request.getParameter("hin").trim();
            boolean outOfDomain = true;
            List<Demographic> hinDemoList = demographicDao.searchDemographicByHIN(
                    paramNameHin, 100, 0, loggedInInfo.getLoggedInProviderNo(), outOfDomain);
            for (Demographic hinDemo : hinDemoList) {
                if (!hinDemo.getDemographicNo().toString().equals(demoNo)) {
                    if (hinDemo.getVer() != null && !hinDemo.getVer().equals("66")) {
                        request.setAttribute("hinDuplicateDemo", hinDemo);
                        return "duplicate";
                    }
                }
            }
        }

        // --- Write under a row lock (issue #4225) ---
        // The patient row is locked before anything is written and every write below commits
        // together, so two saves of one patient cannot interleave. A form that carries the
        // version it was rendered from is refused when that is no longer the record's version:
        // writing it would put back every field another user changed since it was opened (a
        // cleared phone number), so the newer record is kept and the user's entries are shown on
        // the conflict page instead.
        final int patientNo = demographicNo;
        final String postedVersion = request.getParameter(DemographicEditVersion.PARAMETER);
        PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
        List<ConsentType> consentNotSaved = new ArrayList<>();
        UpdateOutcome outcome = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class))
                .execute(status -> {
                    Demographic current = demographicDao.findForUpdate(patientNo);
                    if (current == null) {
                        status.setRollbackOnly();
                        return UpdateOutcome.whenMissing();
                    }
                    if (DemographicEditVersion.isPresent(postedVersion)) {
                        Map<String, String> currentExtensions = demographicExtDao.getAllValuesForDemo(patientNo);
                        DemographicCust currentCust = demographicCustDao.find(patientNo);
                        String currentVersion = DemographicEditVersion.of(current, currentExtensions, currentCust);
                        if (!currentVersion.equals(postedVersion)) {
                            status.setRollbackOnly();
                            return UpdateOutcome.whenStale(currentVersion, DemographicEditVersion.differences(
                                    demographic, postedExtensionValues(extensions), postedCust(patientNo),
                                    current, currentExtensions, currentCust));
                        }
                    }

                    // The form is applied to the locked row, not to the copy read before the lock, so a
                    // column the form does not write keeps the value the row holds now.
                    applyForm(current, proNo);
                    consentNotSaved.addAll(saveConsents(request, loggedInInfo, patientNo, patientConsentManager));

                    // Each extension row is matched to the patient's existing row by key, never by an
                    // id the form posted: a form rendered before the rows existed posts no ids and
                    // used to insert them a second time (uk_demo_ext), and a posted id could name
                    // another patient's row.
                    Map<String, Integer> rowIds = new HashMap<>();
                    for (DemographicExt row : demographicExtDao.getDemographicExtByDemographicNo(patientNo)) {
                        rowIds.putIfAbsent(row.getKey(), row.getId());
                    }
                    for (DemographicExt extension : extensions) {
                        extension.setId(rowIds.get(extension.getKey()));
                        demographicExtDao.saveEntity(extension);
                        rowIds.putIfAbsent(extension.getKey(), extension.getId());
                    }

                    OtherIdManager.saveIdDemographic(patientNo, "meditech_id", request.getParameter("meditech_id"));

                    Long archiveId = demographicArchiveDao.archiveRecord(current);
                    for (DemographicExt extension : extensions) {
                        DemographicExtArchive archive = new DemographicExtArchive(extension);
                        archive.setArchiveId(archiveId);
                        archive.setValue(request.getParameter(archive.getKey()));
                        demographicExtArchiveDao.saveEntity(archive);
                    }

                    demographicDao.save(current);

                    DemographicCust demographicCust = demographicCustDao.find(patientNo);
                    if (demographicCust != null) {
                        applyPostedCust(demographicCust);
                        demographicCustDao.merge(demographicCust);
                    } else {
                        demographicCustDao.persist(postedCust(patientNo));
                    }
                    return UpdateOutcome.whenSaved();
                });

        if (outcome == null || outcome.missing()) {
            logger.warn("DemographicUpdate2Action: the patient record was removed before the update could be saved");
            addActionError("Patient record not found");
            return ERROR;
        }
        if (!outcome.saved()) {
            return refuseStaleForm(demoNo, outcome);
        }

        try {
            DemographicNameAgeString.resetDemographic(demoNo);
        } catch (Exception nameAgeEx) {
            logger.error("ERROR RESETTING NAME AGE", nameAgeEx);
        }

        PreventionManager prevMgr = SpringUtils.getBean(PreventionManager.class);
        prevMgr.removePrevention(demoNo);

        LogAction.addLog(proNo, LogConst.UPDATE, LogConst.CON_DEMOGRAPHIC,
                demoNo, request.getRemoteAddr(), demoNo);

        if (waitingListChosen) {
            String listId = String.valueOf(waitingListId);
            // Records a new note/date row when the patient is already on the list; a
            // no-op otherwise, in which case the result page confirms and posts the add.
            WLWaitingListUtil.updateWaitingListRecord(listId, request.getParameter("waiting_list_note"),
                    demoNo, request.getParameter("waiting_list_referral_date"));

            List<WaitingList> waitingListList = waitingListDao.findByWaitingListIdAndDemographicId(
                    waitingListId, demographicNo);
            if (waitingListList.isEmpty()) {
                List<Appointment> apptList = appointmentDao.findNonCancelledFutureAppointments(demographicNo);
                request.setAttribute("demographicNo", demoNo);
                request.setAttribute("wlDemoNo", demoNo);
                request.setAttribute("wlListId", listId);
                request.setAttribute("wlNote", StringUtils.noNull(request.getParameter("waiting_list_note")));
                request.setAttribute("wlReferralDate", StringUtils.noNull(request.getParameter("waiting_list_referral_date")));
                request.setAttribute("addToWl", Boolean.TRUE);
                request.setAttribute("needsWlConfirm", Boolean.valueOf(!apptList.isEmpty()));
                if (!consentNotSaved.isEmpty()) {
                    request.setAttribute(ConsentNotSavedNotice.PARAMETER,
                            ConsentNotSavedNotice.parameterValue(consentNotSaved));
                }
                return SUCCESS;
            }
        }
        String editRedirectUrl = request.getContextPath() + "/demographic/DemographicEdit?demographic_no=" + demographicNo;
        response.sendRedirect(ConsentNotSavedNotice.appendTo(editRedirectUrl, ConsentNotSavedNotice.parameterValue(consentNotSaved)));
        return null;
    }

    /**
     * Copies the posted Master Record form onto {@code demographic}: every field the edit form
     * writes, plus the audit columns. Applied first to a copy for validation and the duplicate-HIN
     * check, then to the row locked for the write, so a column the form does not write keeps
     * whatever the locked row holds.
     */
    private void applyForm(Demographic demographic, String proNo) {
        demographic.setLastName(org.apache.commons.lang3.StringUtils.trimToEmpty(request.getParameter("last_name")));
        demographic.setFirstName(org.apache.commons.lang3.StringUtils.trimToEmpty(request.getParameter("first_name")));
        demographic.setMiddleNames(normalizeOptionalMiddleNames(request.getParameter("middleNames")));
        demographic.setAlias(request.getParameter("nameUsed"));
        // Name Used is kept whole in the alias; pref_name holds only what fits its column (#5128).
        demographic.setPrefName(Demographic.prefNameForNameUsed(request.getParameter("nameUsed")));
        demographic.setAddress(request.getParameter("address"));
        demographic.setCity(request.getParameter("city"));
        demographic.setProvince(request.getParameter("province"));
        demographic.setPostal(request.getParameter("postal"));
        demographic.setResidentialAddress(request.getParameter("residentialAddress"));
        demographic.setResidentialCity(request.getParameter("residentialCity"));
        demographic.setResidentialProvince(request.getParameter("residentialProvince"));
        demographic.setResidentialPostal(request.getParameter("residentialPostal"));
        demographic.setPhone(request.getParameter("phone"));
        demographic.setPhone2(request.getParameter("phone2"));
        demographic.setEmail(request.getParameter("email"));

        if ("yes".equals(request.getParameter("consentToUseEmailForCare"))) {
            demographic.setConsentToUseEmailForCare(Boolean.TRUE);
        } else if ("no".equals(request.getParameter("consentToUseEmailForCare"))) {
            demographic.setConsentToUseEmailForCare(Boolean.FALSE);
        } else {
            demographic.setConsentToUseEmailForCare(null);
        }

        demographic.setYearOfBirth(request.getParameter("year_of_birth"));
        String monthOfBirth = request.getParameter("month_of_birth");
        demographic.setMonthOfBirth(monthOfBirth != null && monthOfBirth.length() == 1 ? "0" + monthOfBirth : monthOfBirth);
        String dateOfBirth = request.getParameter("date_of_birth");
        demographic.setDateOfBirth(dateOfBirth != null && dateOfBirth.length() == 1 ? "0" + dateOfBirth : dateOfBirth);
        demographic.setHin(request.getParameter("hin"));
        demographic.setVer(request.getParameter("ver"));
        demographic.setRosterStatus(request.getParameter("roster_status"));
        demographic.setRosterEnrolledTo(request.getParameter("roster_enrolled_to"));
        demographic.setPatientStatus(request.getParameter("patient_status"));
        demographic.setChartNo(request.getParameter("chart_no"));
        demographic.setProviderNo(request.getParameter("provider_no"));
        demographic.setSex(request.getParameter("sex"));
        demographic.setPcnIndicator(request.getParameter("pcn_indicator"));
        demographic.setHcType(request.getParameter("hc_type"));
        demographic.setFamilyDoctor(
                "<rdohip>" + request.getParameter("r_doctor_ohip") + "</rdohip>" +
                "<rd>" + request.getParameter("r_doctor") + "</rd>" +
                (request.getParameter("family_doc") != null
                        ? "<family_doc>" + request.getParameter("family_doc") + "</family_doc>"
                        : ""));
        demographic.setCountryOfOrigin(request.getParameter("countryOfOrigin"));
        demographic.setNewsletter(request.getParameter("newsletter"));
        demographic.setSin(request.getParameter("sin"));
        demographic.setTitle(request.getParameter("title"));
        demographic.setOfficialLanguage(request.getParameter("official_lang"));
        demographic.setSpokenLanguage(request.getParameter("spoken_lang"));
        demographic.setRosterTerminationReason(request.getParameter("roster_termination_reason"));
        demographic.setLastUpdateUser(proNo);
        demographic.setLastUpdateDate(new Date());
        demographic.setGender(request.getParameter("gender"));
        demographic.setPronoun(request.getParameter("pronouns"));

        String yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("date_joined_year"));
        String monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("date_joined_month"));
        String dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("date_joined_date"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setDateJoined(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setDateJoined(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("end_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("end_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("end_date_date"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setEndDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setEndDate(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("eff_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("eff_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("eff_date_date"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setEffDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setEffDate(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("hc_renew_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("hc_renew_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("hc_renew_date_date"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setHcRenewDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setHcRenewDate(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_date_day"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setRosterDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setRosterDate(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_termination_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_termination_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("roster_termination_date_day"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setRosterTerminationDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setRosterTerminationDate(null);
        }

        yearTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("patientstatus_date_year"));
        monthTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("patientstatus_date_month"));
        dayTmp = org.apache.commons.lang3.StringUtils.trimToNull(request.getParameter("patientstatus_date_day"));
        if (yearTmp != null && monthTmp != null && dayTmp != null) {
            demographic.setPatientStatusDate(MyDateFormat.getSysDate(yearTmp + '-' + monthTmp + '-' + dayTmp));
        } else {
            demographic.setPatientStatusDate(null);
        }
    }

    /** Records the consent choices the form posted (the new patient consent module). */
    static List<ConsentType> saveConsents(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo,
                                          PatientConsentManager patientConsentManager) {
        List<ConsentType> refused = new ArrayList<>();
        for (ConsentType consentType : patientConsentManager.getActiveConsentTypes()) {
            String type = consentType.getType();
            ChartConsentRequest.Choice choice = readChoice(request, consentType);
            if (choice == ChartConsentRequest.Choice.NONE) {
                continue;
            }
            ShownRecord shown;
            try {
                shown = readShownRecord(request, type);
            } catch (IllegalArgumentException e) {
                // Without a readable shown record the choice cannot be checked, so it is not
                // applied. The values are not logged: they are raw request input.
                logger.warn("DemographicUpdate2Action: consent change refused, unreadable shown record for consent type id {}",
                        consentType.getId());
                // Recorded against the patient too, as the manager records its own refusals.
                LogAction.addLogSynchronous(loggedInInfo, "DemographicUpdate2Action.saveConsents", "consent", null,
                        demographicNo, " Demographic: " + demographicNo + " ConsentTypeId: " + consentType.getId()
                                + " refused: the consent the page showed could not be read");
                refused.add(consentType);
                continue;
            }

            boolean explicitRequested = choice == ChartConsentRequest.Choice.OPT_IN
                    && "1".equals(request.getParameter("recordExplicit_" + type));
            ChartConsentOutcome outcome = patientConsentManager.saveChartConsent(loggedInInfo, demographicNo,
                    consentType.getId(), new ChartConsentRequest(choice, explicitRequested, shown != null,
                            shown == null ? null : shown.id(), shown == null ? null : shown.optOut()));
            if (outcome == ChartConsentOutcome.STALE) {
                logger.warn("DemographicUpdate2Action: consent change refused, the record changed after the page was loaded, for consent type id {}",
                        consentType.getId());
                refused.add(consentType);
            } else if (outcome == ChartConsentOutcome.EXPLICIT_NOT_RECORDED) {
                // Staff ticked the box, so they believe it happened; leave a trace when it did not.
                logger.warn("DemographicUpdate2Action: explicit consent was requested but not recorded for consent type id {}",
                        consentType.getId());
            }
        }
        return refused;
    }

    /**
     * Reads one consent type's posted choice: the radio (0 opt in, 1 opt out) or, without one, the
     * Clear flag. Anything else, including an unrecognised value, is {@code NONE}: the type is left
     * unchanged, and the value is not logged because it is raw request input.
     */
    private static ChartConsentRequest.Choice readChoice(HttpServletRequest request, ConsentType consentType) {
        String type = consentType.getType();
        String consentRecord = request.getParameter(type);
        if (consentRecord != null) {
            ChartConsentRequest.Choice choice = parseConsentChoice(consentRecord);
            if (choice == ChartConsentRequest.Choice.NONE) {
                logger.warn("DemographicUpdate2Action: ignoring an unrecognised choice for consent type id {}",
                        consentType.getId());
            }
            return choice;
        }
        String delete = request.getParameter("deleteConsent_" + type);
        if ("1".equals(delete)) {
            return ChartConsentRequest.Choice.CLEAR;
        }
        if (delete != null && !delete.isEmpty() && !"0".equals(delete)) {
            logger.warn("DemographicUpdate2Action: ignoring an unrecognised clear flag for consent type id {}",
                    consentType.getId());
        }
        return ChartConsentRequest.Choice.NONE;
    }

    /** The consent record the chart page showed for one type: both fields null when it showed none. */
    private record ShownRecord(Integer id, Boolean optOut) {
    }

    /**
     * Reads the record the page showed for one consent type, or returns null when the form posted
     * neither field (an older form, applied without the check). The page always posts both, empty
     * when it showed no record.
     *
     * @throws IllegalArgumentException when only one field was posted, either value cannot be read,
     *                                  or one is empty and the other is not
     */
    private static ShownRecord readShownRecord(HttpServletRequest request, String type) {
        String shownIdValue = request.getParameter("consentShownId_" + type);
        String shownChoiceValue = request.getParameter("consentShownChoice_" + type);
        if (shownIdValue == null && shownChoiceValue == null) {
            return null;
        }
        if (shownIdValue == null || shownChoiceValue == null) {
            throw new IllegalArgumentException("consent shown record incomplete");
        }
        Integer shownId = parseShownId(shownIdValue);
        ChartConsentRequest.Choice shownChoice = parseShownChoice(shownChoiceValue);
        Boolean shownOptOut = shownChoice == ChartConsentRequest.Choice.NONE
                ? null : Boolean.valueOf(shownChoice == ChartConsentRequest.Choice.OPT_OUT);
        if ((shownId == null) != (shownOptOut == null)) {
            throw new IllegalArgumentException("consent shown record half empty");
        }
        return new ShownRecord(shownId, shownOptOut);
    }

    /**
     * Builds the chart page URL a save redirects to, naming the consent types whose consent change
     * was refused so the page can say so. Only consent type ids are added, nothing about the patient.
     */
    static String editRedirectUrl(String contextPath, int demographicNo, List<ConsentType> consentNotSaved) {
        return ConsentNotSavedNotice.appendTo(
                contextPath + "/demographic/DemographicEdit?demographic_no=" + demographicNo,
                ConsentNotSavedNotice.parameterValue(consentNotSaved));
    }

    /** Returns the id, or null for an empty value: the page showed no record. */
    private static Integer parseShownId(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.matches("\\d{1,9}")) {
            throw new IllegalArgumentException("consent shown id");
        }
        return Integer.valueOf(trimmed);
    }

    /** Returns the choice the page showed, or {@code NONE} for an empty value: the page showed no record. */
    private static ChartConsentRequest.Choice parseShownChoice(String value) {
        if (value.trim().isEmpty()) {
            return ChartConsentRequest.Choice.NONE;
        }
        ChartConsentRequest.Choice choice = parseConsentChoice(value);
        if (choice == ChartConsentRequest.Choice.NONE) {
            throw new IllegalArgumentException("consent shown choice");
        }
        return choice;
    }

    /** Returns {@code OPT_OUT} for "1", {@code OPT_IN} for "0", and {@code NONE} for anything else. */
    private static ChartConsentRequest.Choice parseConsentChoice(String value) {
        return switch (value.trim()) {
            case "0" -> ChartConsentRequest.Choice.OPT_IN;
            case "1" -> ChartConsentRequest.Choice.OPT_OUT;
            default -> ChartConsentRequest.Choice.NONE;
        };
    }

    /** The custom-row values the form posted, applied to {@code cust}. */
    private void applyPostedCust(DemographicCust cust) {
        cust.setResident(request.getParameter("resident"));
        cust.setNurse(request.getParameter("nurse"));
        cust.setAlert(request.getParameter("alert"));
        cust.setMidwife(request.getParameter("midwife"));
        cust.setNotes(DemographicXml.userNotes(request.getParameter("notes")));
    }

    /** A new custom row for patient {@code patientNo} holding the values the form posted. */
    private DemographicCust postedCust(int patientNo) {
        DemographicCust cust = new DemographicCust();
        applyPostedCust(cust);
        cust.setId(patientNo);
        return cust;
    }

    private static Map<String, String> postedExtensionValues(List<DemographicExt> extensions) {
        Map<String, String> values = new LinkedHashMap<>();
        for (DemographicExt extension : extensions) {
            values.putIfAbsent(extension.getKey(), extension.getValue());
        }
        return values;
    }

    /**
     * Answers a form rendered from an older version of the record: HTTP 409, never cached, and a
     * page that shows the user's entries beside the current values, links to the current record
     * and offers to save the same entries again deliberately, as a new form bound to the current
     * version. Nothing was written.
     */
    private String refuseStaleForm(String demoNo, UpdateOutcome outcome) {
        logger.info("DemographicUpdate2Action: refused a Master Record form rendered from an older version of the record");
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        List<String[]> draft = new ArrayList<>();
        for (Map.Entry<String, String[]> parameter : request.getParameterMap().entrySet()) {
            String name = parameter.getKey();
            // The page renders the current version, and CSRFGuard injects a fresh token.
            if (DemographicEditVersion.PARAMETER.equals(name) || "CSRF-TOKEN".equals(name)) {
                continue;
            }
            for (String value : parameter.getValue()) {
                draft.add(new String[]{name, value});
            }
        }
        request.setAttribute("demographicNo", demoNo);
        request.setAttribute("conflictDifferences", outcome.differences());
        request.setAttribute("conflictVersion", outcome.currentVersion());
        request.setAttribute("conflictDraft", draft);
        return "conflict";
    }

    /**
     * What the locked write phase did: saved, refused as stale (with the record's current version
     * and the fields the refused form differs in), or found no patient row.
     */
    private record UpdateOutcome(boolean saved, boolean missing, String currentVersion,
                                 List<DemographicEditVersion.Difference> differences) {
        static UpdateOutcome whenSaved() {
            return new UpdateOutcome(true, false, null, List.of());
        }

        static UpdateOutcome whenMissing() {
            return new UpdateOutcome(false, true, null, List.of());
        }

        static UpdateOutcome whenStale(String currentVersion, List<DemographicEditVersion.Difference> differences) {
            return new UpdateOutcome(false, false, currentVersion, differences);
        }
    }

    /**
     * Parses a submitted {@code list_id}; anything that is not a positive integer
     * becomes {@code -1}, which no list matches, so it is refused with the stale ones.
     */
    static int parseListId(String submittedListId) {
        try {
            int parsed = Integer.parseInt(submittedListId);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static String normalizeOptionalMiddleNames(String rawMiddleNames) {
        String middleNames = org.apache.commons.lang3.StringUtils.trimToEmpty(rawMiddleNames);
        return "null".equalsIgnoreCase(middleNames) ? "" : middleNames;
    }
}
