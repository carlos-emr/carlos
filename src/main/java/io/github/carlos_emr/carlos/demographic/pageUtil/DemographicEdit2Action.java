/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CountryCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.CountryCode;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import io.github.carlos_emr.carlos.commn.model.DemographicExtArchive;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.data.ProvinceNames;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Struts2 action that loads all data needed by the demographic edit page
 * and sets it as request attributes for the JSP fragments under
 * {@code /WEB-INF/jsp/demographic/}.
 *
 * <p>This replaces the data-loading scriptlet blocks that were previously
 * embedded in {@code demographiceditdemographic.jsp}. By moving data loading
 * to an action, the JSP can be split into {@code <jsp:include>} fragments
 * (each compiling as a separate class) to stay under the JVM's 64KB
 * bytecode method limit.</p>
 *
 * @since 2026-04-04
 */
public class DemographicEdit2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        HttpSession session = request.getSession();
        if (session.getAttribute("user") == null) {
            return "logout";
        }

        String demographic_no = request.getParameter("demographic_no");
        if (demographic_no == null || demographic_no.trim().isEmpty()) {
            throw new IllegalArgumentException("demographic_no is required");
        }

        CarlosProperties oscarProps = CarlosProperties.getInstance();
        String prov = StringUtils.trimToEmpty(oscarProps.getProperty("billregion", "")).toUpperCase();
        String curProvider_no = (String) session.getAttribute("user");

        // --- Load core demographic data ---
        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        Demographic demographic = demographicDao.getDemographic(demographic_no);

        DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
        Map<String, String> demoExt = demographicExtDao.getAllValuesForDemo(Integer.parseInt(demographic_no));

        DemographicCustDao demographicCustDao = SpringUtils.getBean(DemographicCustDao.class);

        DemographicArchiveDao demographicArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);
        List<DemographicArchive> archives = demographicArchiveDao.findByDemographicNo(Integer.parseInt(demographic_no));

        DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
        List<DemographicExtArchive> extArchives = demographicExtArchiveDao.getDemographicExtArchiveByDemoAndKey(
                Integer.parseInt(demographic_no), "demo_cell");

        // --- Admissions ---
        AdmissionManager admissionManager = SpringUtils.getBean(AdmissionManager.class);
        Admission communityAdmission = null;
        List<Admission> serviceAdmissions = new ArrayList<>();
        if (demographic != null) {
            communityAdmission = admissionManager.getCurrentCommunityProgramAdmission(demographic.getDemographicNo());
            serviceAdmissions = admissionManager.getCurrentServiceProgramAdmission(demographic.getDemographicNo());
            if (serviceAdmissions == null) {
                serviceAdmissions = new ArrayList<>();
            }
        }

        // --- Provider lists ---
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        List<Provider> providers = providerDao.getActiveProviders();
        List<Provider> doctors = providerDao.getActiveProvidersByRole("doctor");
        List<Provider> nurses = providerDao.getActiveProvidersByRole("nurse");
        List<Provider> midwifes = providerDao.getActiveProvidersByRole("midwife");

        // --- Country codes ---
        CountryCodeDao ccDAO = SpringUtils.getBean(CountryCodeDao.class);
        List<CountryCode> countryList = ccDAO.getAllCountryCodes();

        // --- Case management ---
        CaseManagementManager cmm = SpringUtils.getBean(CaseManagementManager.class);
        List<CaseManagementNoteLink> cml = cmm.getLinkByTableId(
                CaseManagementNoteLink.DEMOGRAPHIC, Long.valueOf(demographic_no));
        boolean hasImportExtra = (cml != null && !cml.isEmpty());
        String annotation_display = CaseManagementNoteLink.DISP_DEMO;

        // --- Consent values ---
        String usSigned = StringUtils.defaultString(demoExt.get("usSigned"));
        String privacyConsent = StringUtils.defaultString(demoExt.get("privacyConsent"), "");
        String informedConsent = StringUtils.defaultString(demoExt.get("informedConsent"), "");

        // --- Privacy/consent config ---
        String privateConsentEnabledProp = oscarProps.getProperty("privateConsentEnabled");
        boolean privateConsentEnabled = "true".equals(privateConsentEnabledProp);

        // --- Province names ---
        ProvinceNames pNames = ProvinceNames.getInstance();

        // --- Current date ---
        GregorianCalendar now = new GregorianCalendar();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = sdf.format(now.getTime());

        // --- i18n note reason ---
        ResourceBundle oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
        String noteReason = oscarResources.getString("encounter.noteReason.TelProgress");
        if ("yes".equals(oscarProps.getProperty("disableTelProgressNoteTitleInEncouterNotes"))) {
            noteReason = "";
        }

        // --- Current program ---
        String currentProgram = "";
        String programId = (String) session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID);
        if (programId != null && !programId.isEmpty()) {
            try {
                Integer prId = Integer.parseInt(programId);
                ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
                Program p = programManager2.getProgram(loggedInInfo, prId);
                if (p != null) {
                    currentProgram = p.getName();
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // --- Patient consent module ---
        if (oscarProps.getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
            PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
            request.setAttribute("consentTypes", patientConsentManager.getActiveConsentTypes());
            request.setAttribute("patientConsents", patientConsentManager.getAllConsentsByDemographic(
                    loggedInInfo, Integer.parseInt(demographic_no)));
        }

        // --- Lookup lists ---
        LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);

        LookupList firstNationCommunities = lookupListManager.findLookupListByName(loggedInInfo, "firstNationCommunity");
        if (firstNationCommunities != null) {
            LookupListItem fncommunity = lookupListManager.findLookupListItemByLookupListIdAndValue(
                    loggedInInfo, firstNationCommunities.getId(), demoExt.get("fNationCom"));
            if (fncommunity != null) {
                request.setAttribute("fncommunity", fncommunity.getLabel());
            }
        }

        LookupList phuLookupList = lookupListManager.findLookupListByName(loggedInInfo, "phu");
        if (phuLookupList != null) {
            LookupListItem phuItem = lookupListManager.findLookupListItemByLookupListIdAndValue(
                    loggedInInfo, phuLookupList.getId(), demoExt.get("PHU"));
            if (phuItem != null && phuItem.isActive()) {
                request.setAttribute("phuName", phuItem.getLabel());
            }
        }

        Boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;

        // --- Audit log ---
        LogAction.addLog(curProvider_no, LogConst.READ, LogConst.CON_DEMOGRAPHIC,
                demographic_no, request.getRemoteAddr(), demographic_no);

        // --- DemographicCust fields (used by view + edit sections) ---
        io.github.carlos_emr.carlos.commn.model.DemographicCust demographicCust =
                demographicCustDao.find(Integer.parseInt(demographic_no));
        String resident = "", nurse = "", alert = "", notes = "", midwife = "";
        if (demographicCust != null) {
            resident = StringUtils.defaultString(demographicCust.getResident());
            nurse = StringUtils.defaultString(demographicCust.getNurse());
            alert = StringUtils.defaultString(demographicCust.getAlert());
            midwife = StringUtils.defaultString(demographicCust.getMidwife());
            notes = SxmlMisc.getXmlContent(demographicCust.getNotes(), "unotes");
            notes = notes == null ? "" : notes;
        }

        // --- Referral doctor fields ---
        String rd = "", rdohip = "", family_doc = "";
        if (demographic != null) {
            String fd = demographic.getFamilyDoctor();
            if (fd != null) {
                rd = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(fd), "rd");
                rd = (rd != null && !"null".equals(rd)) ? rd : "";
                rdohip = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(fd), "rdohip");
                rdohip = (rdohip != null && !"null".equals(rdohip)) ? rdohip : "";
                family_doc = SxmlMisc.getXmlContent(StringUtils.trimToEmpty(fd), "family_doc");
                family_doc = family_doc != null ? family_doc : "";
            }
        }

        // --- Birth date fields ---
        String birthYear = "0000", birthMonth = "00", birthDate = "00";
        if (demographic != null) {
            if (io.github.carlos_emr.carlos.util.StringUtils.filled(demographic.getYearOfBirth()))
                birthYear = StringUtils.trimToEmpty(demographic.getYearOfBirth());
            if (io.github.carlos_emr.carlos.util.StringUtils.filled(demographic.getMonthOfBirth()))
                birthMonth = StringUtils.trimToEmpty(demographic.getMonthOfBirth());
            if (io.github.carlos_emr.carlos.util.StringUtils.filled(demographic.getDateOfBirth()))
                birthDate = StringUtils.trimToEmpty(demographic.getDateOfBirth());
        }

        // --- Additional beans needed by fragments ---
        ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
        WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);
        WaitingListNameDao waitingListNameDao = SpringUtils.getBean(WaitingListNameDao.class);
        UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        ProgramManager pm = SpringUtils.getBean(ProgramManager.class);
        ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);

        // --- Set ALL request attributes for JSP fragments ---
        request.setAttribute("demographic_no", demographic_no);
        request.setAttribute("demographic", demographic);
        request.setAttribute("demoExt", demoExt);
        request.setAttribute("demographicCust", demographicCust);
        request.setAttribute("demographicCustDao", demographicCustDao);
        request.setAttribute("archives", archives);
        request.setAttribute("extArchives", extArchives);
        request.setAttribute("communityAdmission", communityAdmission);
        request.setAttribute("serviceAdmissions", serviceAdmissions);
        request.setAttribute("providers", providers);
        request.setAttribute("doctors", doctors);
        request.setAttribute("nurses", nurses);
        request.setAttribute("midwifes", midwifes);
        request.setAttribute("countryList", countryList);
        request.setAttribute("hasImportExtra", hasImportExtra);
        request.setAttribute("annotation_display", annotation_display);
        request.setAttribute("usSigned", usSigned);
        request.setAttribute("privacyConsent", privacyConsent);
        request.setAttribute("informedConsent", informedConsent);
        request.setAttribute("privateConsentEnabled", privateConsentEnabled);
        request.setAttribute("pNames", pNames);
        request.setAttribute("dateString", dateString);
        request.setAttribute("noteReason", noteReason);
        request.setAttribute("currentProgram", currentProgram);
        request.setAttribute("isMobileOptimized", isMobileOptimized);
        request.setAttribute("prov", prov);
        request.setAttribute("curProvider_no", curProvider_no);
        request.setAttribute("userfirstname", session.getAttribute("userfirstname"));
        request.setAttribute("userlastname", session.getAttribute("userlastname"));
        request.setAttribute("apptProvider", request.getParameter("apptProvider"));
        request.setAttribute("appointment", request.getParameter("appointment"));
        request.setAttribute("oscarProps", oscarProps);
        request.setAttribute("firstNationCommunities", firstNationCommunities);
        request.setAttribute("phuLookupList", phuLookupList);
        request.setAttribute("lookupListManager", lookupListManager);

        // Computed intermediate values shared across fragments
        request.setAttribute("rd", rd);
        request.setAttribute("rdohip", rdohip);
        request.setAttribute("family_doc", family_doc);
        request.setAttribute("resident", resident);
        request.setAttribute("nurse", nurse);
        request.setAttribute("alert", alert);
        request.setAttribute("notes", notes);
        request.setAttribute("midwife", midwife);
        request.setAttribute("birthYear", birthYear);
        request.setAttribute("birthMonth", birthMonth);
        request.setAttribute("birthDate", birthDate);

        // DAOs/managers needed by fragments for inline queries
        request.setAttribute("scheduleTemplateCodeDao", scheduleTemplateCodeDao);
        request.setAttribute("waitingListDao", waitingListDao);
        request.setAttribute("waitingListNameDao", waitingListNameDao);
        request.setAttribute("userPropertyDAO", userPropertyDAO);
        request.setAttribute("demographicDao", demographicDao);
        request.setAttribute("demographicExtDao", demographicExtDao);
        request.setAttribute("demographicArchiveDao", demographicArchiveDao);
        request.setAttribute("demographicExtArchiveDao", demographicExtArchiveDao);
        request.setAttribute("demographicManager", demographicManager);
        request.setAttribute("programManager", pm);
        request.setAttribute("programDao", programDao);
        request.setAttribute("admissionManager", admissionManager);
        request.setAttribute("ccDAO", ccDAO);
        request.setAttribute("providerDao", providerDao);
        request.setAttribute("programManager2", SpringUtils.getBean(ProgramManager2.class));

        return SUCCESS;
    }
}
