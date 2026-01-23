//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.encounter.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.daos.DefaultIssueDao;
import ca.openosp.openo.PMmodule.dao.ProgramAccessDAO;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.PMmodule.model.ProgramAccess;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.casemgmt.dao.CaseManagementIssueDAO;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.dao.RoleProgramAccessDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.casemgmt.service.CaseManagementManagerImpl;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.daos.security.SecroleDao;
import ca.openosp.openo.model.security.Secrole;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.UtilDateUtilities;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctIncomingEncounter2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger log = MiscUtils.getLogger();
    private CaseManagementNoteDAO caseManagementNoteDao = SpringUtils.getBean(CaseManagementNoteDAO.class);
    private CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws IOException, ServletException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demoNo = request.getParameter("demographicNo");

        // Check if demographicNo is null or invalid
        if (demoNo == null || demoNo.trim().isEmpty() || "null".equals(demoNo)) {
            log.error("EctIncomingEncounter2Action called with null or invalid demographicNo");
            throw new IllegalArgumentException("Invalid or missing demographicNo parameter");
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r",
                null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (!"true".equals(OscarProperties.getInstance().getProperty("program_domain.show_echart", "false"))) {
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
            if (bean.providerNo == null) {
                bean.providerNo = (String) request.getSession().getAttribute("user");
            }

            bean.demographicNo = request.getParameter("demographicNo");
            bean.appointmentNo = request.getParameter("appointmentNo");
            // use this one.
            if (bean.appointmentNo != null && !bean.appointmentNo.equalsIgnoreCase("null")
                    && !"".equals(bean.appointmentNo) && appointmentNo != null) {
                bean.appointmentNo = appointmentNo;
            }

            bean.curProviderNo = request.getParameter("curProviderNo");
            Provider provider = loggedInInfo.getLoggedInProvider();
            if (bean.curProviderNo == null || bean.curProviderNo.trim().length() == 0)
                bean.curProviderNo = provider.getProviderNo();
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
            bean.setUpEncounterPage(LoggedInInfo.getLoggedInInfoFromSession(request));
            request.getSession().setAttribute("EctSessionBean", bean);
            request.getSession().setAttribute("eChartID", bean.eChartId);
            if (request.getParameter("source") != null) {
                bean.source = request.getParameter("source");
            }

            long notesCount = caseManagementNoteDao.getNotesCountByDemographicId(bean.getDemographicNo());
            if (notesCount == 0
                    && OscarProperties.getInstance().getProperty("wl_default_issue", "false").equals("true")) {
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

        ArrayList newDocArr = (ArrayList) request.getSession().getServletContext().getAttribute("newDocArr");
        Boolean useNewEchart = (Boolean) request.getSession().getServletContext().getAttribute("useNewEchart");

        String proNo = (String) request.getSession().getAttribute("user");
        if (proNo != null && newDocArr != null && Collections.binarySearch(newDocArr, proNo) >= 0) {
            return "success2";
        } else if (useNewEchart != null && useNewEchart.equals(Boolean.TRUE)) {

            return "success2";
        } else {
            return SUCCESS;
        }
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
