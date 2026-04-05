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
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.DemographicExtArchive;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Struts2 action that processes a new patient demographic record creation.
 *
 * <p>Extracts all business logic previously embedded in
 * {@code demographicaddarecord.jsp} scriptlets. Performs security checks,
 * builds and persists the {@link Demographic} entity and related records
 * (DemographicCust, DemographicExt, archive, program admission, waiting list),
 * then sets request attributes for the view template.</p>
 *
 * <p>Only POST requests are accepted. Returns {@code "duplicate"} when a HIN
 * duplicate is detected, {@code "methodNotAllowed"} for non-POST requests, and
 * {@code "success"} on normal completion.</p>
 *
 * @since 2026-04-04
 */
public class DemographicAddRecord2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private DemographicCustDao demographicCustDao = SpringUtils.getBean(DemographicCustDao.class);
    private DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    private DemographicArchiveDao demographicArchiveDao = SpringUtils.getBean(DemographicArchiveDao.class);
    private DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
    private WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);

    /**
     * Processes a demographic add form POST, persisting all related records and
     * setting response attributes for the view template.
     *
     * @return {@code "success"}, {@code "duplicate"}, or {@code "methodNotAllowed"}
     * @throws SecurityException if the session is missing or the provider lacks
     *                           {@code _demographic} write privilege
     */
    @Override
    public String execute() {
        if (!"POST".equals(request.getMethod())) {
            return "methodNotAllowed";
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.warn("DemographicAddRecord2Action: missing session");
            throw new SecurityException("missing required session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            logger.warn("DemographicAddRecord2Action: provider {} lacks _demographic write privilege",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_demographic)");
        }

        String curUser_no = (String) request.getSession().getAttribute("user");
        String proNo = curUser_no;

        // --- Appointment return parameters ---
        String fromAppt = request.getParameter("fromAppt");
        String provider_no2 = request.getParameter("provider_no");
        String bFirstDisp2 = request.getParameter("bFirstDisp");
        String year2 = request.getParameter("year");
        String month2 = request.getParameter("month");
        String day2 = request.getParameter("day");
        String start_time2 = request.getParameter("start_time");
        String end_time2 = request.getParameter("end_time");
        String duration2 = request.getParameter("duration");

        // --- Build Demographic entity ---
        Demographic demographic = new Demographic();
        demographic.setLastName(request.getParameter("last_name").trim());
        demographic.setFirstName(request.getParameter("first_name").trim());
        demographic.setMiddleNames(request.getParameter("middleNames").trim());
        demographic.setAlias(request.getParameter("nameUsed"));
        demographic.setPrefName(request.getParameter("nameUsed"));
        demographic.setAddress(request.getParameter("address"));
        demographic.setCity(request.getParameter("city"));
        if (request.getParameter("province") != null) {
            demographic.setProvince(request.getParameter("province"));
        } else {
            demographic.setProvince("");
        }
        demographic.setPostal(request.getParameter("postal"));
        demographic.setResidentialAddress(request.getParameter("residentialAddress"));
        demographic.setResidentialCity(request.getParameter("residentialCity"));
        if (request.getParameter("residentialProvince") != null) {
            demographic.setResidentialProvince(request.getParameter("residentialProvince"));
        } else {
            demographic.setResidentialProvince("");
        }
        demographic.setResidentialPostal(request.getParameter("residentialPostal"));
        demographic.setPhone(request.getParameter("phone"));
        demographic.setPhone2(request.getParameter("phone2"));
        demographic.setEmail(request.getParameter("email"));
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
        demographic.setDateJoined(MyDateFormat.getSysDate(
                request.getParameter("date_joined_year") + "-" +
                request.getParameter("date_joined_month") + "-" +
                request.getParameter("date_joined_date")));
        demographic.setChartNo(request.getParameter("chart_no"));
        demographic.setProviderNo(request.getParameter("staff"));
        demographic.setSex(request.getParameter("sex"));
        demographic.setPronoun(request.getParameter("pronouns"));
        demographic.setGender(request.getParameter("gender"));

        String year = StringUtils.trimToNull(request.getParameter("end_date_year"));
        String month = StringUtils.trimToNull(request.getParameter("end_date_month"));
        String day = StringUtils.trimToNull(request.getParameter("end_date_date"));
        if (year != null && month != null && day != null) {
            demographic.setEndDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
        } else {
            demographic.setEndDate(null);
        }

        year = StringUtils.trimToNull(request.getParameter("eff_date_year"));
        month = StringUtils.trimToNull(request.getParameter("eff_date_month"));
        day = StringUtils.trimToNull(request.getParameter("eff_date_date"));
        if (year != null && month != null && day != null) {
            demographic.setEffDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
        } else {
            demographic.setEffDate(null);
        }

        demographic.setPcnIndicator(request.getParameter("pcn_indicator"));
        demographic.setHcType(request.getParameter("hc_type"));

        year = StringUtils.trimToNull(request.getParameter("roster_date_year"));
        month = StringUtils.trimToNull(request.getParameter("roster_date_month"));
        day = StringUtils.trimToNull(request.getParameter("roster_date_date"));
        if (year != null && month != null && day != null) {
            demographic.setRosterDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
        } else {
            demographic.setRosterDate(null);
        }

        year = StringUtils.trimToNull(request.getParameter("hc_renew_date_year"));
        month = StringUtils.trimToNull(request.getParameter("hc_renew_date_month"));
        day = StringUtils.trimToNull(request.getParameter("hc_renew_date_date"));
        if (year != null && month != null && day != null) {
            demographic.setHcRenewDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
        } else {
            demographic.setHcRenewDate(null);
        }

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
        demographic.setLastUpdateUser(curUser_no);
        demographic.setLastUpdateDate(new Date());

        if (request.getParameter("patient_status_date") != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            try {
                demographic.setPatientStatusDate(fmt.parse(request.getParameter("patient_status_date")));
            } catch (Exception e) {
                demographic.setPatientStatusDate(new Date());
            }
        } else {
            demographic.setPatientStatusDate(new Date());
        }

        // --- HIN duplicate check ---
        boolean hinDupCheckException = false;
        String hcType = request.getParameter("hc_type");
        String ver = request.getParameter("ver");
        if (hcType != null && ver != null && hcType.equals("BC") && ver.equals("66")) {
            hinDupCheckException = true;
        }

        if (request.getParameter("hin") != null && request.getParameter("hin").length() > 5 && !hinDupCheckException) {
            String paramNameHin = request.getParameter("hin").trim();
            List<Demographic> demographics = demographicDao.searchByHealthCard(paramNameHin);
            if (!demographics.isEmpty()) {
                Demographic dupDemo = demographics.get(0);
                request.setAttribute("hinDuplicateDemo", dupDemo);
                return "duplicate";
            }
        }

        StringBuilder bufName = new StringBuilder(request.getParameter("last_name") + "," + request.getParameter("first_name"));
        StringBuilder bufChart = new StringBuilder(StringUtils.trimToEmpty("chart_no"));
        StringBuilder bufDoctorNo = new StringBuilder(StringUtils.trimToEmpty("provider_no"));

        demographicDao.save(demographic);

        String dem = demographic.getDemographicNo().toString();

        // --- DemographicCust ---
        DemographicCust demographicCust = new DemographicCust();
        demographicCust.setResident(request.getParameter("cust2"));
        demographicCust.setNurse(request.getParameter("cust1"));
        demographicCust.setAlert(request.getParameter("cust3"));
        demographicCust.setMidwife(request.getParameter("cust4"));
        demographicCust.setNotes("<unotes>" + request.getParameter("content") + "</unotes>");
        demographicCust.setId(demographic.getDemographicNo());
        demographicCustDao.persist(demographicCust);

        // --- Patient consents ---
        if (CarlosProperties.getInstance().getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
            PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
            List<ConsentType> consentTypes = patientConsentManager.getActiveConsentTypes();
            boolean explicitConsent = Boolean.TRUE;
            for (ConsentType consentType : consentTypes) {
                String type = consentType.getType();
                String consentRecord = request.getParameter(type);
                if (consentRecord != null) {
                    boolean optOut = Integer.parseInt(consentRecord) == 1;
                    patientConsentManager.addEditConsentRecord(loggedInInfo, demographic.getDemographicNo(),
                            consentType.getId(), explicitConsent, optOut);
                }
            }
        }

        // --- DemographicExt keys ---
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "hPhoneExt", request.getParameter("hPhoneExt"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "wPhoneExt", request.getParameter("wPhoneExt"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "demo_cell", request.getParameter("demo_cell"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "aboriginal", request.getParameter("aboriginal"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "cytolNum", request.getParameter("cytolNum"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "ethnicity", request.getParameter("ethnicity"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "area", request.getParameter("area"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "statusNum", request.getParameter("statusNum"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationCom", request.getParameter("fNationCom"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "given_consent", request.getParameter("given_consent"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "rxInteractionWarningLevel", request.getParameter("rxInteractionWarningLevel"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "primaryEMR", request.getParameter("primaryEMR"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "phoneComment", request.getParameter("phoneComment"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "usSigned", request.getParameter("usSigned"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "privacyConsent", request.getParameter("privacyConsent"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "informedConsent", request.getParameter("informedConsent"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "HasPrimaryCarePhysician", request.getParameter("HasPrimaryCarePhysician"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "EmploymentStatus", request.getParameter("EmploymentStatus"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "PHU", request.getParameter("PHU"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationFamilyNumber", request.getParameter("fNationFamilyNumber"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationFamilyPosition", request.getParameter("fNationFamilyPosition"), "");
        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "labelfNationCom", request.getParameter("labelfNationCom"), "");

        OtherIdManager.saveIdDemographic(dem, "meditech_id", request.getParameter("meditech_id"));

        // --- Customized extension keys from properties ---
        java.util.Properties oscarVariables = CarlosProperties.getInstance();
        if (oscarVariables.getProperty("demographicExt") != null) {
            String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
            for (String propKey : propDemoExt) {
                String key = propKey.replace(' ', '_');
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), key, request.getParameter(key), "");
            }
        }

        // --- Audit log ---
        String ip = request.getRemoteAddr();
        LogAction.addLog(curUser_no, "add", "demographic", dem, ip, dem);

        // --- Archive the initial record ---
        Long archiveId = demographicArchiveDao.archiveRecord(demographicDao.getDemographic(dem));
        List<DemographicExt> extensions = demographicExtDao.getDemographicExtByDemographicNo(Integer.parseInt(dem));
        for (DemographicExt extension : extensions) {
            DemographicExtArchive archive = new DemographicExtArchive(extension);
            archive.setArchiveId(archiveId);
            archive.setValue(request.getParameter(archive.getKey()));
            demographicExtArchiveDao.saveEntity(archive);
        }

        // --- Waiting list ---
        io.github.carlos_emr.carlos.waitinglist.WaitingList wL = io.github.carlos_emr.carlos.waitinglist.WaitingList.getInstance();
        if (wL.getFound() && CarlosProperties.getInstance().getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")) {
            String listId = request.getParameter("list_id");
            if (listId != null && !listId.isEmpty()) {
                List<Long> positionList = new ArrayList<>();
                List<io.github.carlos_emr.carlos.commn.model.WaitingList> waitingListList =
                        waitingListDao.findByWaitingListId(1);
                if (waitingListList != null) {
                    for (io.github.carlos_emr.carlos.commn.model.WaitingList wlEntry : waitingListList) {
                        positionList.add(wlEntry.getPosition());
                    }
                    Long maxPosition = 0L;
                    if (!positionList.isEmpty()) {
                        maxPosition = Collections.max(positionList);
                    }
                    if (!listId.isEmpty() && !listId.equals("0")) {
                        io.github.carlos_emr.carlos.commn.model.WaitingList waitingList =
                                new io.github.carlos_emr.carlos.commn.model.WaitingList();
                        waitingList.setListId(Integer.parseInt(listId));
                        waitingList.setDemographicNo(demographic.getDemographicNo());
                        waitingList.setNote(request.getParameter("waiting_list_note"));
                        waitingList.setPosition(maxPosition + 1);
                        waitingList.setOnListSince(MyDateFormat.getSysDate(request.getParameter("waiting_list_referral_date")));
                        waitingList.setIsHistory("N");
                        waitingList.setOnListSince(new Date());
                        waitingListDao.persist(waitingList);
                    }
                }
            }
        }

        // --- Program admission ---
        String rps = request.getParameter("rps");
        if (rps == null || rps.trim().isEmpty()) {
            ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
            Program oscarProgram = programDao.getProgramByName("OSCAR");
            if (oscarProgram != null) {
                rps = String.valueOf(oscarProgram.getId());
            }
        }
        if (rps != null && !rps.trim().isEmpty()) {
            try {
                Integer programId = Integer.parseInt(rps);
                Admission admission = new Admission();
                admission.setClientId(demographic.getDemographicNo());
                admission.setProgramId(programId);
                admission.setProviderNo(curUser_no);
                admission.setAdmissionDate(new Date());
                admission.setAdmissionStatus("current");
                admission.setAdmissionFromTransfer(false);
                AdmissionDao admissionDao = SpringUtils.getBean(AdmissionDao.class);
                admissionDao.saveAdmission(admission);
            } catch (Exception e) {
                logger.warn("Failed to create program admission for demographic {}", dem, e);
            }
        }

        // --- Set view attributes ---
        request.setAttribute("demographicNo", dem);
        request.setAttribute("fromAppt", fromAppt);
        request.setAttribute("startTime", start_time2);
        request.setAttribute("providerNo", provider_no2);
        request.setAttribute("bFirstDisp", bFirstDisp2);
        request.setAttribute("year2", year2);
        request.setAttribute("month2", month2);
        request.setAttribute("day2", day2);
        request.setAttribute("endTime", end_time2);
        request.setAttribute("duration", duration2);
        request.setAttribute("bufName", bufName.toString());
        request.setAttribute("bufChart", bufChart.toString());
        request.setAttribute("bufDoctorNo", bufDoctorNo.toString());
        // Pass through remaining appointment params for URL construction in view
        request.setAttribute("creator", request.getParameter("creator"));
        request.setAttribute("appointmentDate", request.getParameter("appointment_date"));
        request.setAttribute("messageId", request.getParameter("messageId"));
        request.setAttribute("notes", request.getParameter("notes"));
        request.setAttribute("reason", request.getParameter("reason"));
        request.setAttribute("location", request.getParameter("location"));
        request.setAttribute("resources", request.getParameter("resources"));
        request.setAttribute("type", request.getParameter("type"));
        request.setAttribute("style", request.getParameter("style"));
        request.setAttribute("billing", request.getParameter("billing"));
        request.setAttribute("status", request.getParameter("status"));
        request.setAttribute("createdatetime", request.getParameter("createdatetime"));
        request.setAttribute("remarks", request.getParameter("remarks"));

        return SUCCESS;
    }
}
