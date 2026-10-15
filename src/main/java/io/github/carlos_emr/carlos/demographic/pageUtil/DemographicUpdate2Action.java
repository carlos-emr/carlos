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

import io.github.carlos_emr.CarlosProperties;
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
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.demographic.util.DemographicXml;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.ChartConsentOutcome;
import io.github.carlos_emr.carlos.managers.ChartConsentRequest;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.model.PreventionManager;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    private final transient SecurityInfoManager securityInfoManager;

    public DemographicUpdate2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public DemographicUpdate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    /**
     * Validates session and privileges, then applies all update logic extracted
     * from the former {@code demographicupdatearecord.jsp} scriptlets.
     *
     * @return {@code "success"}, {@code "duplicate"}, or {@code "methodNotAllowed"},
     *         or {@code null} when a redirect has been issued
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            logger.warn("DemographicUpdate2Action: provider {} lacks _demographic write privilege",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_demographic)");
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
            logger.warn("DemographicUpdate2Action: invalid demographic_no={}", demoNo);
            addActionError("Invalid patient record identifier");
            return ERROR;
        }

        Demographic demographic = demographicDao.getDemographic(demoNo);
        if (demographic == null) {
            logger.warn("DemographicUpdate2Action: demographic_no={} not found", demoNo);
            addActionError("Patient record not found");
            return ERROR;
        }

        demographic.setLastName(org.apache.commons.lang3.StringUtils.trimToEmpty(request.getParameter("last_name")));
        demographic.setFirstName(org.apache.commons.lang3.StringUtils.trimToEmpty(request.getParameter("first_name")));
        demographic.setMiddleNames(normalizeOptionalMiddleNames(request.getParameter("middleNames")));
        demographic.setAlias(request.getParameter("nameUsed"));
        demographic.setPrefName(request.getParameter("nameUsed"));
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
        demographic.setFamilyDoctor(DemographicXml.familyDoctor(
                request.getParameter("r_doctor_ohip"),
                request.getParameter("r_doctor"),
                request.getParameter("family_doc")));
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

        List<String> fieldLengthValidationErrors = demographic.validateFieldLengths();
        if (!fieldLengthValidationErrors.isEmpty()) {
            logger.warn("DemographicUpdate2Action: rejected demographic input due to field length limits: {}",
                    String.join("; ", fieldLengthValidationErrors));
            request.setAttribute("fieldLengthValidationErrors", fieldLengthValidationErrors);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "validationError";
        }

        List<DemographicExt> extensions = new ArrayList<>();
        extensions.add(new DemographicExt(request.getParameter("demo_cell_id"), proNo, demographicNo, "demo_cell", request.getParameter("demo_cell")));
        extensions.add(new DemographicExt(request.getParameter("aboriginal_id"), proNo, demographicNo, "aboriginal", request.getParameter("aboriginal")));
        extensions.add(new DemographicExt(request.getParameter("hPhoneExt_id"), proNo, demographicNo, "hPhoneExt", request.getParameter("hPhoneExt")));
        extensions.add(new DemographicExt(request.getParameter("wPhoneExt_id"), proNo, demographicNo, "wPhoneExt", request.getParameter("wPhoneExt")));
        extensions.add(new DemographicExt(request.getParameter("cytolNum_id"), proNo, demographicNo, "cytolNum", request.getParameter("cytolNum")));
        extensions.add(new DemographicExt(request.getParameter("ethnicity_id"), proNo, demographicNo, "ethnicity", request.getParameter("ethnicity")));
        extensions.add(new DemographicExt(request.getParameter("area_id"), proNo, demographicNo, "area", request.getParameter("area")));
        if ("true".equals(CarlosProperties.getInstance().getProperty("FIRST_NATIONS_MODULE", "false"))) {
            extensions.add(new DemographicExt(request.getParameter("statusNum_id"), proNo, demographicNo, "statusNum", request.getParameter("statusNum")));
            extensions.add(new DemographicExt(request.getParameter("fNationCom_id"), proNo, demographicNo, "fNationCom", request.getParameter("fNationCom")));
            extensions.add(new DemographicExt(request.getParameter("labelfNationCom_id"), proNo, demographicNo, "labelfNationCom", request.getParameter("labelfNationCom")));
            if ("false".equals(CarlosProperties.getInstance().getProperty("showBandNumberOnly", "true"))) {
                extensions.add(new DemographicExt(request.getParameter("fNationFamilyPosition_id"), proNo, demographicNo, "fNationFamilyPosition", request.getParameter("fNationFamilyPosition")));
                extensions.add(new DemographicExt(request.getParameter("fNationFamilyNumber_id"), proNo, demographicNo, "fNationFamilyNumber", request.getParameter("fNationFamilyNumber")));
            }
        }
        extensions.add(new DemographicExt(request.getParameter("given_consent_id"), proNo, demographicNo, "given_consent", request.getParameter("given_consent")));
        extensions.add(new DemographicExt(request.getParameter("rxInteractionWarningLevel_id"), proNo, demographicNo, "rxInteractionWarningLevel", request.getParameter("rxInteractionWarningLevel")));
        extensions.add(new DemographicExt(request.getParameter("primaryEMR_id"), proNo, demographicNo, "primaryEMR", request.getParameter("primaryEMR")));
        extensions.add(new DemographicExt(request.getParameter("phoneComment_id"), proNo, demographicNo, "phoneComment", request.getParameter("phoneComment")));
        extensions.add(new DemographicExt(request.getParameter("usSigned_id"), proNo, demographicNo, "usSigned", request.getParameter("usSigned")));
        extensions.add(new DemographicExt(request.getParameter("privacyConsent_id"), proNo, demographicNo, "privacyConsent", request.getParameter("privacyConsent")));
        extensions.add(new DemographicExt(request.getParameter("informedConsent_id"), proNo, demographicNo, "informedConsent", request.getParameter("informedConsent")));
        extensions.add(new DemographicExt(request.getParameter("paper_chart_archived_id"), proNo, demographicNo, "paper_chart_archived", request.getParameter("paper_chart_archived")));
        extensions.add(new DemographicExt(request.getParameter("paper_chart_archived_date_id"), proNo, demographicNo, "paper_chart_archived_date", request.getParameter("paper_chart_archived_date")));
        extensions.add(new DemographicExt(request.getParameter("paper_chart_archived_program_id"), proNo, demographicNo, "paper_chart_archived_program", request.getParameter("paper_chart_archived_program")));
        extensions.add(new DemographicExt(request.getParameter("HasPrimaryCarePhysician_id"), proNo, demographicNo, "HasPrimaryCarePhysician", request.getParameter("HasPrimaryCarePhysician")));
        extensions.add(new DemographicExt(request.getParameter("EmploymentStatus_id"), proNo, demographicNo, "EmploymentStatus", request.getParameter("EmploymentStatus")));
        extensions.add(new DemographicExt(request.getParameter("PHU_id"), proNo, demographicNo, "PHU", request.getParameter("PHU")));

        java.util.Properties oscarVariables = CarlosProperties.getInstance();
        if (oscarVariables.getProperty("demographicExt") != null) {
            String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
            for (String propKey : propDemoExt) {
                String key = propKey.replace(' ', '_');
                extensions.add(new DemographicExt(request.getParameter(key + "_id"), proNo, demographicNo, key, request.getParameter(key)));
            }
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

        // Consent is persisted only once the form has passed every check above; a form rejected as
        // a HIN duplicate must not have recorded, for instance, a confirmed explicit consent.
        // A consent choice made against a record that has since changed is refused for that consent
        // type; the rest of the chart is still saved, and the page the save ends on says so.
        List<ConsentType> consentNotSaved = new ArrayList<>();
        if (CarlosProperties.getInstance().getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
            consentNotSaved = saveConsents(request, loggedInInfo, demographic.getDemographicNo(),
                    SpringUtils.getBean(PatientConsentManager.class));
        }
        String editRedirectUrl = editRedirectUrl(request.getContextPath(), demographicNo, consentNotSaved);

        for (DemographicExt extension : extensions) {
            demographicExtDao.saveEntity(extension);
        }

        OtherIdManager.saveIdDemographic(demographicNo, "meditech_id", request.getParameter("meditech_id"));

        Long archiveId = demographicArchiveDao.archiveRecord(demographic);
        for (DemographicExt extension : extensions) {
            DemographicExtArchive archive = new DemographicExtArchive(extension);
            archive.setArchiveId(archiveId);
            archive.setValue(request.getParameter(archive.getKey()));
            demographicExtArchiveDao.saveEntity(archive);
        }

        demographicDao.save(demographic);

        try {
            DemographicNameAgeString.resetDemographic(demoNo);
        } catch (Exception nameAgeEx) {
            logger.error("ERROR RESETTING NAME AGE", nameAgeEx);
        }

        DemographicCust demographicCust = demographicCustDao.find(demographicNo);
        if (demographicCust != null) {
            demographicCust.setResident(request.getParameter("resident"));
            demographicCust.setNurse(request.getParameter("nurse"));
            demographicCust.setAlert(request.getParameter("alert"));
            demographicCust.setMidwife(request.getParameter("midwife"));
            demographicCust.setNotes(DemographicXml.userNotes(request.getParameter("notes")));
            demographicCustDao.merge(demographicCust);
        } else {
            demographicCust = new DemographicCust();
            demographicCust.setResident(request.getParameter("resident"));
            demographicCust.setNurse(request.getParameter("nurse"));
            demographicCust.setAlert(request.getParameter("alert"));
            demographicCust.setMidwife(request.getParameter("midwife"));
            demographicCust.setNotes(DemographicXml.userNotes(request.getParameter("notes")));
            demographicCust.setId(demographicNo);
            demographicCustDao.persist(demographicCust);
        }

        PreventionManager prevMgr = SpringUtils.getBean(PreventionManager.class);
        prevMgr.removePrevention(demoNo);

        LogAction.addLog(proNo, LogConst.UPDATE, LogConst.CON_DEMOGRAPHIC,
                demoNo, request.getRemoteAddr(), demoNo);

        io.github.carlos_emr.carlos.waitinglist.WaitingList wL =
                io.github.carlos_emr.carlos.waitinglist.WaitingList.getInstance();
        if (wL.getFound() && CarlosProperties.getInstance().getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")) {
            WLWaitingListUtil.updateWaitingListRecord(
                    request.getParameter("list_id"), request.getParameter("waiting_list_note"),
                    demoNo, request.getParameter("waiting_list_referral_date"));

            String listId = request.getParameter("list_id");
            if (listId != null && !listId.isEmpty() && !"0".equalsIgnoreCase(listId)) {
                int listIdInt;
                try {
                    listIdInt = Integer.parseInt(listId);
                } catch (NumberFormatException e) {
                    logger.warn("DemographicUpdate2Action: invalid list_id={}, treating as 0", listId);
                    response.sendRedirect(editRedirectUrl);
                    return null;
                }
                List<WaitingList> waitingListList = waitingListDao.findByWaitingListIdAndDemographicId(
                        listIdInt, demographicNo);
                if (waitingListList.isEmpty()) {
                    List<Appointment> apptList = appointmentDao.findNonCancelledFutureAppointments(demographicNo);
                    request.setAttribute("demographicNo", demoNo);
                    request.setAttribute("wlDemoNo", demoNo);
                    request.setAttribute("wlListId", listId);
                    request.setAttribute("wlNote", StringUtils.noNull(request.getParameter("waiting_list_note")));
                    request.setAttribute("wlReferralDate", StringUtils.noNull(request.getParameter("waiting_list_referral_date")));
                    request.setAttribute("addToWl", Boolean.TRUE);
                    request.setAttribute("needsWlConfirm", Boolean.valueOf(!apptList.isEmpty()));
                    // This path forwards to a page that posts on to the waiting list, which then
                    // redirects to the chart; the page carries the refused consent types along.
                    if (!consentNotSaved.isEmpty()) {
                        request.setAttribute(ConsentNotSavedNotice.PARAMETER,
                                ConsentNotSavedNotice.parameterValue(consentNotSaved));
                    }
                    return SUCCESS;
                } else {
                    response.sendRedirect(editRedirectUrl);
                    return null;
                }
            } else {
                response.sendRedirect(editRedirectUrl);
                return null;
            }
        } else {
            response.sendRedirect(editRedirectUrl);
            return null;
        }
    }

    /**
     * Applies the chart's consent section for every active consent type.
     *
     * <p>Every demographic save re-posts each type's pre-checked radio, so an Opt-in here is not
     * evidence that anyone just asked the patient, and re-saving never changes whether an existing
     * record is explicit. Only the separate {@code recordExplicit_<type>} checkbox, ticked with
     * Opt-in selected, upgrades one (#3858). A record created here, when staff pick a choice for a
     * type that had none, is explicit as before: that choice is the staff member's own entry.</p>
     *
     * <p>A radio value other than 0 (opt in) or 1 (opt out) leaves that type unchanged. It used to
     * default to opt-in, which recorded consent nobody gave.</p>
     *
     * <p>The page also posts, for each type, the consent record it showed
     * ({@code consentShownId_<type>} and {@code consentShownChoice_<type>}). A choice is applied
     * only while that record still decides the patient's consent; if a colleague has changed it
     * since the page was loaded, the consent change for that type is refused and nothing else is
     * affected. A form that posts neither field is applied without that check; one that posts only
     * one of them, or one empty and the other not, is refused as a shown record that cannot be read.</p>
     *
     * @return the consent types whose consent change was refused; empty when none was
     */
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static String normalizeOptionalMiddleNames(String rawMiddleNames) {
        String middleNames = org.apache.commons.lang3.StringUtils.trimToEmpty(rawMiddleNames);
        return "null".equalsIgnoreCase(middleNames) ? "" : middleNames;
    }
}
