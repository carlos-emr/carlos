/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.io.IOException;
import java.util.ArrayList;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.daos.DefaultIssueDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.encounter.data.EctProviderData;
import io.github.carlos_emr.carlos.encounter.data.EctSplitChart;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import org.owasp.encoder.Encode;

import java.util.Properties;
import java.util.Vector;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctIncomingEncounter2Action extends ActionSupport {

    private static Logger log = MiscUtils.getLogger();
    private CaseManagementNoteDAO caseManagementNoteDao = SpringUtils.getBean(CaseManagementNoteDAO.class);
    private CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws IOException, ServletException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demoNo = request.getParameter("demographicNo");

        // Check if demographicNo is null or invalid
        if (demoNo == null || demoNo.trim().isEmpty() || "null".equals(demoNo)) {
            log.error("EctIncomingEncounter2Action called with null or invalid demographicNo");
            throw new IllegalArgumentException("Invalid or missing demographicNo parameter");
        }
        if (!demoNo.matches("\\d{1,9}")) {
            throw new IllegalArgumentException("Invalid demographicNo parameter");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r",
                null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (!"true".equals(CarlosProperties.getInstance().getProperty("program_domain.show_echart", "false"))) {
            if (!caseManagementMgr.isClientInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo)
                    && !caseManagementMgr.isClientReferredInProgramDomain(loggedInInfo.getLoggedInProviderNo(),
                    demoNo)) {
                return "domain-error";
            }
        }

        EctSessionBean bean;
        String appointmentNo = null;

        if (request.getSession().getAttribute("cur_appointment_no") != null) {
            appointmentNo = (String) request.getSession().getAttribute("cur_appointment_no");
        }

        if (request.getParameter("appointmentList") != null) {
            bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
            bean.setUpEncounterPage(loggedInInfo, request.getParameter("appointmentNo"));
            bean.template = "";
        } else if (request.getParameter("demographicSearch") != null) {
            // Coming in from the demographicSearch page
            bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
            // demographicNo is passed from search screen
            bean.demographicNo = request.getParameter("demographicNo");
            // no curProviderNo when viewing eCharts from search screen
            // bean.curProviderNo="";
            // no reason when viewing eChart from search screen
            bean.reason = "";
            // userName is already set
            // bean.userName=request.getParameter("userName");
            // no appointmentDate from search screen keep old date
            // bean.appointmentDate="";
            // no startTime from search screen
            bean.startTime = "";
            // no status from search screen
            bean.status = "";
            // no date from search screen-keep old date
            // bean.date="";
            bean.appointmentNo = "0";
            bean.check = "myCheck";
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            request.getSession().setAttribute("EctSessionBean", bean);
        } else {
            if ("yes".equals(request.getParameter("PEAttach"))) {
                String selectClientmo = request.getParameter("selectId");
                // save
                String lastId = request.getParameter("noteId");

                CaseManagementNote note = caseManagementNoteDao.getNote(Long.parseLong(lastId));
                note.setId(null);
                note.setDemographic_no(selectClientmo);
                caseManagementNoteDao.saveNote(note);
            }
            bean = new EctSessionBean();
            bean.currentDate = UtilDateUtilities.StringToDate(request.getParameter("curDate"));

            if (bean.currentDate == null) {
                bean.currentDate = new Date();
            }

            bean.providerNo = request.getParameter("providerNo");
            if (bean.providerNo != null && !bean.providerNo.matches("[a-zA-Z0-9]{1,6}")) {
                throw new IllegalArgumentException("Invalid providerNo parameter");
            }
            if (bean.providerNo == null) {
                bean.providerNo = (String) request.getSession().getAttribute("user");
            }

            bean.demographicNo = demoNo;
            bean.appointmentNo = request.getParameter("appointmentNo");
            if (bean.appointmentNo == null || "null".equalsIgnoreCase(bean.appointmentNo) || bean.appointmentNo.isEmpty()) {
                bean.appointmentNo = null;
            } else if (!bean.appointmentNo.matches("\\d{1,9}")) {
                throw new IllegalArgumentException("Invalid appointmentNo parameter");
            }
            // use this one.
            if (bean.appointmentNo != null && appointmentNo != null) {
                bean.appointmentNo = appointmentNo;
            }

            bean.curProviderNo = request.getParameter("curProviderNo");
            if (bean.curProviderNo != null && !bean.curProviderNo.matches("[a-zA-Z0-9]{1,6}")) {
                throw new IllegalArgumentException("Invalid curProviderNo parameter");
            }
            Provider provider = loggedInInfo.getLoggedInProvider();
            if (bean.curProviderNo == null || bean.curProviderNo.trim().length() == 0)
                bean.curProviderNo = provider.getProviderNo();
            // Reject oversized free-text parameters to prevent session storage abuse
            for (String paramName : new String[]{"reason", "reasonCode", "encType", "userName",
                    "appointmentDate", "startTime", "status", "date", "source"}) {
                String val = request.getParameter(paramName);
                if (val != null && val.length() > 255) {
                    throw new IllegalArgumentException("Parameter too long: " + paramName);
                }
            }

            bean.reason = request.getParameter("reason");
            bean.reasonCode = request.getParameter("reasonCode");
            bean.encType = request.getParameter("encType");
            bean.userName = request.getParameter("userName");
            if (bean.userName == null) {
                bean.userName = ((String) request.getSession().getAttribute("userfirstname")) + " "
                        + ((String) request.getSession().getAttribute("userlastname"));
            }

            bean.appointmentDate = request.getParameter("appointmentDate");
            bean.startTime = request.getParameter("startTime");
            bean.status = request.getParameter("status");
            bean.date = request.getParameter("date");
            bean.check = "myCheck";
            bean.oscarMsgID = request.getParameter("msgId");
            if (bean.oscarMsgID != null && !bean.oscarMsgID.matches("\\d{1,9}")) {
                throw new IllegalArgumentException("Invalid msgId parameter");
            }
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            request.getSession().setAttribute("EctSessionBean", bean);
            request.getSession().setAttribute("eChartID", bean.eChartId);
            if (request.getParameter("source") != null) {
                bean.source = request.getParameter("source");
            }

            long notesCount = caseManagementNoteDao.getNotesCountByDemographicId(bean.getDemographicNo());
            if (notesCount == 0
                    && CarlosProperties.getInstance().getProperty("wl_default_issue", "false").equals("true")) {
                // assign default issues for a feature: WL: default issues assignment
                String wlProgramId = (String) request.getSession().getAttribute(SessionConstants.CURRENT_PROGRAM_ID);
                DefaultIssueDao defaultIssueDao = SpringUtils.getBean(DefaultIssueDao.class);
                IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
                CaseManagementIssueDAO cmiDao = (CaseManagementIssueDAO) SpringUtils.getBean(CaseManagementIssueDAO.class);
                Set<Long> issueIdSet = getIssueIdSet(bean.getCurProviderNo(), wlProgramId);
                String[] issueIds = defaultIssueDao.findAllDefaultIssueIds();
                for (String id : issueIds) {
                    Issue issue = issueDao.getIssue(Long.valueOf(id));
                    // judge current provider can access this issue
                    if (!issueIdSet.contains(Long.parseLong(id))) {
                        continue;
                    }

                    // judge this issue exists or not
                    CaseManagementIssue cmi = cmiDao.getIssuebyId(bean.getDemographicNo(), id);
                    if (cmi != null) { // this issue exists
                        continue;
                    }
                    cmi = new CaseManagementIssue();
                    cmi.setAcute(false);
                    cmi.setCertain(false);
                    cmi.setDemographic_no(Integer.valueOf(bean.getDemographicNo()));
                    cmi.setIssue_id(Long.valueOf(id));
                    cmi.setMajor(false);
                    cmi.setProgram_id(Integer.valueOf(wlProgramId));
                    cmi.setResolved(false);
                    cmi.setType(issue.getRole());
                    cmi.setUpdate_date(new Date());
                    cmiDao.saveIssue(cmi);
                }
            }

        }

        setEncounterAttributes(request, bean, loggedInInfo);
        return "success2";
    }

    /**
     * Sets request attributes needed by Index2.jsp and its includes.
     * Moves data-fetching logic from JSP scriptlets into the Action layer.
     */
    private void setEncounterAttributes(HttpServletRequest request, EctSessionBean bean, LoggedInInfo loggedInInfo) {
        CarlosProperties oscarProps = CarlosProperties.getInstance();

        // Patient age as integer string
        String pAge = Integer.toString(new UtilDateUtilities().calcAge(
                bean.yearOfBirth, bean.monthOfBirth, bean.dateOfBirth));
        request.setAttribute("pAge", pAge);

        // Family doctor info
        if (bean.familyDoctorNo != null && !bean.familyDoctorNo.isEmpty()) {
            EctProviderData.Provider prov = new EctProviderData().getProvider(bean.familyDoctorNo);
            if (prov != null) {
                request.setAttribute("famDocName", prov.getFirstName());
                request.setAttribute("famDocSurname", prov.getSurname());
            } else {
                request.setAttribute("famDocName", "");
                request.setAttribute("famDocSurname", "");
            }
        } else {
            request.setAttribute("famDocName", "");
            request.setAttribute("famDocSurname", "");
        }

        // Window sizes (individual values for EL access)
        Properties windowSizes = EctWindowSizes.getWindowSizes(bean.providerNo);
        request.setAttribute("rowOneSize", windowSizes.getProperty("rowOneSize"));
        request.setAttribute("rowTwoSize", windowSizes.getProperty("rowTwoSize"));
        request.setAttribute("rowThreeSize", windowSizes.getProperty("rowThreeSize"));
        request.setAttribute("presBoxSize", windowSizes.getProperty("presBoxSize"));

        // Province
        request.setAttribute("province", oscarProps.getProperty("billregion", "").trim().toUpperCase());

        // Split chart data
        Vector splitChart = new EctSplitChart().getSplitCharts(bean.demographicNo);
        request.setAttribute("splitChart", splitChart);
        request.setAttribute("hasSplitChart", splitChart != null && !splitChart.isEmpty());

        // Allergies and Prescriptions — guard against non-numeric demographicNo and null patient
        int demoNoInt = -1;
        try {
            demoNoInt = Integer.parseInt(bean.demographicNo);
        } catch (NumberFormatException e) {
            log.error("Non-numeric demographicNo in session bean; skipping allergies/prescriptions");
        }

        Allergy[] allergies = new Allergy[0];
        RxPrescriptionData.Prescription[] prescriptions = new RxPrescriptionData.Prescription[0];
        if (demoNoInt >= 0) {
            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demoNoInt);
            if (patient != null) {
                allergies = patient.getAllergies(loggedInInfo);
            }
            prescriptions = new RxPrescriptionData().getUniquePrescriptionsByPatient(demoNoInt);
        }
        request.setAttribute("allergies", allergies);
        request.setAttribute("prescriptions", prescriptions);

        // Encounter text and consumption
        boolean bSplit = request.getParameter("splitchart") != null;
        int nEctLen = bean.encounter != null ? bean.encounter.length() : 0;
        boolean bTruncate = bSplit && nEctLen > 5120;
        int consumption = (int) ((bTruncate ? 5120 : nEctLen) / (10.24 * 32));
        consumption = consumption == 0 ? 1 : consumption;
        String ccolor = consumption >= 70 ? "red" : (consumption >= 50 ? "orange" : "green");
        request.setAttribute("consumption", consumption);
        request.setAttribute("consumptionColor", ccolor);
        request.setAttribute("bSplit", bSplit);

        // Build encounter text
        String encounterText = buildEncounterText(bean, bSplit, bTruncate, nEctLen);
        request.setAttribute("encounterText", encounterText);

        // CarlosProperties labels
        request.setAttribute("otherMedLabel", oscarProps.getProperty("otherMedications", ""));
        request.setAttribute("medHistLabel", oscarProps.getProperty("medicalHistory", ""));
        request.setAttribute("ongoingConcernsLabel", oscarProps.getProperty("ongoingConcerns", ""));

        // Popup URL — only allow http/https to prevent javascript: URI injection
        String rawPopUrl = request.getParameter("popupUrl");
        if (rawPopUrl != null && (rawPopUrl.startsWith("http://") || rawPopUrl.startsWith("https://"))) {
            request.setAttribute("popUrl", rawPopUrl);
        } else {
            request.setAttribute("popUrl", "");
        }

        // Template names (escaped for JS)
        int maxLen = 25;
        int truncLen = 22;
        String ellipses = "...";
        List<String> escapedTemplates = new ArrayList<>();
        if (bean.templateNames != null) {
            for (String tmpl : bean.templateNames) {
                String truncated = StringUtils.maxLenString(tmpl, maxLen, truncLen, ellipses);
                escapedTemplates.add(Encode.forJavaScript(truncated));
            }
        }
        request.setAttribute("templateNames", escapedTemplates);
    }

    /**
     * Assembles the encounter text from the session bean, handling split chart
     * truncation and date stamp insertion.
     */
    private String buildEncounterText(EctSessionBean bean, boolean bSplit, boolean bTruncate, int nEctLen) {
        StringBuilder sb = new StringBuilder();
        try {
            if (!bSplit) {
                sb.append(bean.encounter != null ? bean.encounter : "");
            } else if (bTruncate) {
                sb.append(bean.encounter.substring(nEctLen - 5120));
                sb.append("\n--------------------------------------------------\n$$SPLIT CHART$$\n");
            } else {
                sb.append(bean.encounter != null ? bean.encounter : "");
                sb.append("\n--------------------------------------------------\n$$SPLIT CHART$$\n");
            }

            UtilDateUtilities dateConvert = new UtilDateUtilities();
            if (bean.eChartTimeStamp == null) {
                sb.append("\n[").append(dateConvert.DateToString(bean.currentDate))
                        .append(" .: ").append(bean.reason != null ? bean.reason : "").append("] \n");
            } else if (bean.currentDate.compareTo(bean.eChartTimeStamp) > 0) {
                String apptDate = (bean.appointmentDate == null || bean.appointmentDate.isEmpty())
                        ? UtilDateUtilities.getToday("yyyy-MM-dd") : bean.appointmentDate;
                sb.append("\n__________________________________________________\n[")
                        .append(apptDate).append(" .: ").append(bean.reason != null ? bean.reason : "").append("]\n");
            } else if ((bean.currentDate.compareTo(bean.eChartTimeStamp) == 0)
                    && (bean.reason != null || bean.subject != null)
                    && !java.util.Objects.equals(bean.reason, bean.subject)) {
                sb.append("\n__________________________________________________\n[")
                        .append(bean.appointmentDate != null ? bean.appointmentDate : "")
                        .append(" .: ").append(bean.reason != null ? bean.reason : "").append("]\n");
            }
            if (bean.oscarMsg != null && !bean.oscarMsg.isEmpty()) {
                sb.append("\n\n").append(bean.oscarMsg);
            }
        } catch (Exception e) {
            log.error("Error building encounter text", e);
        }
        return sb.toString();
    }

    private Set<Long> getIssueIdSet(String providerNo, String wlProgramId) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        List<ProgramProvider> ppList = programProviderDao.getProgramProviderByProviderProgramId(providerNo,
                Long.valueOf(wlProgramId));
        ProgramProvider pp = ppList.get(0);
        Secrole role = pp.getRole();

        // get program accesses... program allows either all roles or not all roles
        // (does this mean no roles?)
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        List<ProgramAccess> paList = programAccessDAO.getAccessListByProgramId(Long.valueOf(wlProgramId));
        Map<String, ProgramAccess> paMap = new HashMap<String, ProgramAccess>();
        for (Iterator<ProgramAccess> iter = paList.iterator(); iter.hasNext(); ) {
            ProgramAccess pa = iter.next();
            paMap.put(pa.getAccessType().getName().toLowerCase(), pa);
        }

        // get all roles
        CaseManagementManager cmm = new CaseManagementManagerImpl();
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        List<Secrole> allRoles = secroleDao.getRoles();

        RoleProgramAccessDAO roleProgramAccessDAO = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);

        List<Secrole> allowableSearchRoles = new ArrayList<Secrole>();
        for (Iterator<Secrole> iter = allRoles.iterator(); iter.hasNext(); ) {
            Secrole r = iter.next();
            String key = "write " + r.getName().toLowerCase() + " issues";
            ProgramAccess pa = paMap.get(key);
            if (pa != null) {
                if (pa.isAllRoles() || cmm.isRoleIncludedInAccess(pa, role)) {
                    allowableSearchRoles.add(r);
                }
            }
            if (pa == null && r.getId().intValue() == role.getId().intValue()) {
                allowableSearchRoles.add(r);
            }

            // global default role access
            if (roleProgramAccessDAO.hasAccess(key, role.getId())) {
                allowableSearchRoles.add(r);
            }
        }
        IssueDAO issueDAO = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
        List<Long> issIdList = issueDAO.getIssueCodeListByRoles(allowableSearchRoles);
        Set<Long> issueSet = new HashSet<Long>();
        for (Long id : issIdList) {
            issueSet.add(id);
        }
        return issueSet;
    }
}
