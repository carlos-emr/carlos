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
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Struts2 action that processes a demographic record update (save). Replaces the
 * business-logic scriptlets previously embedded in
 * {@code demographicupdatearecord.jsp}.
 *
 * <p>Only POST requests are accepted. Returns {@code "duplicate"} when a HIN
 * duplicate is detected (for a different patient), {@code "methodNotAllowed"}
 * for non-POST requests, and {@code "success"} on normal completion. On the
 * success path the action may also issue a redirect to
 * {@code DemographicEdit.do} when no waiting-list interaction is required.</p>
 *
 * @since 2026-04-04
 */
public class DemographicUpdate2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Validates session and privileges, then applies all update logic extracted
     * from the former {@code demographicupdatearecord.jsp} scriptlets.
     *
     * @return {@code "success"}, {@code "duplicate"}, or {@code "methodNotAllowed"},
     *         or {@code null} when a redirect has been issued
     * @throws SecurityException if the session is missing or the provider lacks
     *         {@code _demographic} write privilege
     */
    @Override
    public String execute() {
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
        int demographicNo = Integer.parseInt(demoNo);

        Demographic demographic = demographicDao.getDemographic(demoNo);
        if (demographic == null) {
            logger.warn("DemographicUpdate2Action: demographic_no={} not found", demoNo);
            addActionError("Patient record not found");
            return ERROR;
        }

        demographic.setLastName(request.getParameter("last_name").trim());
        demographic.setFirstName(request.getParameter("first_name").trim());
        demographic.setMiddleNames(request.getParameter("middleNames").trim());
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

        if (CarlosProperties.getInstance().getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
            PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
            List<ConsentType> consentTypes = patientConsentManager.getActiveConsentTypes();
            boolean explicitConsent = Boolean.TRUE;
            for (ConsentType consentType : consentTypes) {
                String type = consentType.getType();
                String consentRecord = request.getParameter(type);
                int deleteme = 0;
                if (!org.apache.commons.lang3.StringUtils.isEmpty(request.getParameter("deleteConsent_" + type))) {
                    deleteme = Integer.parseInt(request.getParameter("deleteConsent_" + type));
                }
                if (consentRecord != null) {
                    boolean optOut = Integer.parseInt(consentRecord) == 1;
                    patientConsentManager.addEditConsentRecord(loggedInInfo, demographic.getDemographicNo(),
                            consentType.getId(), explicitConsent, optOut);
                } else if (deleteme == 1) {
                    patientConsentManager.deleteConsent(loggedInInfo, demographic.getDemographicNo(), consentType.getId());
                }
            }
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

        for (DemographicExt extension : extensions) {
            demographicExtDao.saveEntity(extension);
        }

        OtherIdManager.saveIdDemographic(demographicNo, "meditech_id", request.getParameter("meditech_id"));

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
            demographicCust.setNotes("<unotes>" + request.getParameter("notes") + "</unotes>");
            demographicCustDao.merge(demographicCust);
        } else {
            demographicCust = new DemographicCust();
            demographicCust.setResident(request.getParameter("resident"));
            demographicCust.setNurse(request.getParameter("nurse"));
            demographicCust.setAlert(request.getParameter("alert"));
            demographicCust.setMidwife(request.getParameter("midwife"));
            demographicCust.setNotes("<unotes>" + request.getParameter("notes") + "</unotes>");
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
            if (!"0".equalsIgnoreCase(listId)) {
                List<WaitingList> waitingListList = waitingListDao.findByWaitingListIdAndDemographicId(
                        Integer.valueOf(listId), Integer.valueOf(demoNo));
                if (waitingListList.isEmpty()) {
                    List<Appointment> apptList = appointmentDao.findNonCancelledFutureAppointments(demographicNo);
                    request.setAttribute("demographicNo", demoNo);
                    request.setAttribute("wlDemoNo", demoNo);
                    request.setAttribute("wlListId", listId);
                    request.setAttribute("wlNote", StringUtils.noNull(request.getParameter("waiting_list_note")));
                    request.setAttribute("wlReferralDate", StringUtils.noNull(request.getParameter("waiting_list_referral_date")));
                    request.setAttribute("addToWl", Boolean.TRUE);
                    request.setAttribute("needsWlConfirm", Boolean.valueOf(!apptList.isEmpty()));
                    return SUCCESS;
                } else {
                    response.sendRedirect(request.getContextPath() + "/demographic/DemographicEdit.do?demographic_no=" + demoNo);
                    return null;
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/demographic/DemographicEdit.do?demographic_no=" + demoNo);
                return null;
            }
        } else {
            response.sendRedirect(request.getContextPath() + "/demographic/DemographicEdit.do?demographic_no=" + demoNo);
            return null;
        }
    }
}
