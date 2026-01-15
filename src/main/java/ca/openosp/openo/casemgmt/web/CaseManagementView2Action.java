//CHECKSTYLE:OFF
/**
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
 */

package ca.openosp.openo.casemgmt.web;

import ca.openosp.OscarProperties;
import ca.openosp.openo.caisi_integrator.ws.*;
import ca.openosp.openo.casemgmt.model.*;
import ca.openosp.openo.casemgmt.service.*;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.services.security.SecurityManager;
import ca.openosp.openo.util.UtilDateUtilities;
import com.opensymphony.xwork2.ActionSupport;
import ca.openosp.openo.model.security.Secrole;
import ca.openosp.openo.services.security.RolesManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;

import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.PMmodule.dao.SecUserRoleDao;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.model.ProgramTeam;
import ca.openosp.openo.PMmodule.model.SecUserRole;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.common.Colour;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.web.formbeans.CaseManagementViewFormBean;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.provider.web.CppPreferencesUIBean;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.CppUtils;
import ca.openosp.openo.utility.JsDateSerializer;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.casemgmt.web.CaseManagementViewAction.IssueDisplay;
import ca.openosp.OscarProperties;
import ca.openosp.openo.eform.EFormUtil;
import ca.openosp.openo.encounter.data.EctFormData;
import ca.openosp.openo.encounter.data.EctFormData.PatientForm;
import ca.openosp.openo.prescript.pageUtil.RxSessionBean;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.LabelValueBean;
import ca.openosp.openo.util.OscarRoleObjectPrivilege;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.*;

public class CaseManagementView2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final Integer MAX_INVOICES = 20;
    private static Logger logger = MiscUtils.getLogger();
    private CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
    private IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
    private CaseManagementNoteDAO caseManagementNoteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
    private SecUserRoleDao secUserRoleDao = (SecUserRoleDao) SpringUtils.getBean(SecUserRoleDao.class);
    private GroupNoteDao groupNoteDao = (GroupNoteDao) SpringUtils.getBean(GroupNoteDao.class);
    private DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private CaseManagementIssueNotesDao cmeIssueNotesDao = (CaseManagementIssueNotesDao) SpringUtils.getBean(CaseManagementIssueNotesDao.class);
    private BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    private NoteService noteService = SpringUtils.getBean(NoteService.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    protected CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
    protected ClientImageManager clientImageMgr = SpringUtils.getBean(ClientImageManager.class);
    protected RolesManager roleMgr = SpringUtils.getBean(RolesManager.class);
    protected ProgramManager programMgr = SpringUtils.getBean(ProgramManager.class);
    protected AdmissionManager admissionMgr = SpringUtils.getBean(AdmissionManager.class);

    static {
        // Initialize CPP 
        try {
            CppUtils.addCppCode("CurrentHistory");
            CppUtils.addCppCode("DiagnosticNotes");
            CppUtils.addCppCode("PastOcularHistory");
        } catch (Exception e) {
            System.err.println("Failed to init CPP codes in CaseManagementView2Action: " + e.getMessage());
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }
    
    public String execute() throws Exception {
        this.setFilter_provider("");
        request.setAttribute("patientCppPrintPreview", "false");

        // prevent null pointer errors as both these variables are required in navigation.jsp
        request.getSession().setAttribute("casemgmt_newFormBeans", new ArrayList<Object>());
        request.getSession().setAttribute("casemgmt_msgBeans", new ArrayList<Object>());

        String method = request.getParameter("method") != null ? request.getParameter("method") : (String) request.getAttribute("method");

        if ("setViewType".equals(method)) {
            return setViewType();
        } else if ("setPrescriptViewType".equals(method)) {
            return setPrescriptViewType();
        } else if ("setHideActiveIssues".equals(method)) {
            return setHideActiveIssues();
        } else if ("saveAndExit".equals(method)) {
            return saveAndExit();
        } else if ("save".equals(method)) {
            return save();
        } else if ("patientCPPSave".equals(method)) {
            return patientCPPSave();
        } else if ("patientCppPrintPreview".equals(method)) {
            return patientCppPrintPreview();
        } else if ("viewNotes".equals(method)) {
            return viewNotes();
        } else if ("viewNote".equals(method)) {
            return viewNote();
        } else if ("listNotes".equals(method)) {
            return listNotes();
        } else if ("search".equals(method)) {
            return search();
        } else if ("unlock".equals(method)) {
            return unlock();
        } else if ("do_unlock".equals(method)) {
            return do_unlock();
        } else if ("addToDx".equals(method)) {
            return addToDx();
        } else if ("viewNotesOpt".equals(method)) {
            return viewNotesOpt();
        } 

        // Default method
        return view();
    }

    public String setViewType() throws Exception {
        return view();
    }

    public String setPrescriptViewType() throws Exception {
        return view();
    }

    public String setHideActiveIssues() throws Exception {
        return view();
    }

    public String saveAndExit() throws Exception {
        return save();
    }

    public String save() throws Exception {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();

        if (session.getAttribute("userrole") != null) {
            CaseManagementCPP cpp = this.getCpp();
            cpp.setUpdate_date(new Date());

            caseManagementMgr.saveCPP(cpp, providerNo);
        } else response.sendError(HttpServletResponse.SC_FORBIDDEN);

        return null;
    }

    /* save CPP for patient */
    public String patientCPPSave() throws Exception {
        logger.debug("patientCPPSave");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        CaseManagementCPP cpp = this.getCpp();
        cpp.setUpdate_date(new Date());
        cpp.setDemographic_no(this.getDemographicNo());

        caseManagementMgr.saveCPP(cpp, providerNo);
        addActionMessage(getText("cpp.saved"));

        return view();
    }

    public String patientCppPrintPreview() throws Exception {
        logger.debug("patientCPPSave");

        request.setAttribute("patientCppPrintPreview", "true");
        return view();
    }

    public String viewNotes() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        logger.debug("Starting VIEW");
        String tab = request.getParameter("tab");
        if (tab == null) {
            tab = CaseManagementViewFormBean.tabs[0];
        }
        HttpSession se = request.getSession();
        if (se.getAttribute("userrole") == null) return "expired";

        String demoNo = getDemographicNo();

        logger.debug("is client in program");
        // need to check to see if the client is in our program domain
        // if not...don't show this screen!
        if (!caseManagementMgr.isClientInProgramDomain(providerNo, demoNo) && !caseManagementMgr.isClientReferredInProgramDomain(providerNo, demoNo)) {
            return "domain-error";
        }
        String programId = (String) request.getSession().getAttribute("case_program_id");

        viewCurrentIssuesTab_newCmeNotes(demoNo, programId);

        return "ajaxDisplayNotes";
    }

    /* show case management view */
    /*
     * Session variables : case_program_id casemgmt_DemoNo casemgmt_VlCountry casemgmt_msgBeans readonly
     */
    public String view() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        long start = System.currentTimeMillis();
        long beginning = start;
        long current = 0;
        boolean useNewCaseMgmt = false;
        // First, try to get the newCaseManagement boolean from the session
        String useNewCaseMgmtString = (String) request.getSession().getAttribute("newCaseManagement");
        // If null, try to get the newCaseManagement boolean from the parameters
        if (useNewCaseMgmtString == null) useNewCaseMgmtString = (String) request.getParameter("newCaseManagement");
        // Set the correct boolean if default or fallback value is present
        if (useNewCaseMgmtString != null) useNewCaseMgmt = Boolean.parseBoolean(useNewCaseMgmtString);

        logger.debug("Starting VIEW");
        String tab = request.getParameter("tab");
        if (tab == null) {
            tab = CaseManagementViewFormBean.tabs[0];
        }
        HttpSession se = request.getSession();
        if (se.getAttribute("userrole") == null) return "expired";

        String demoNo = getDemographicNo();

        logger.debug("is client in program");
        // need to check to see if the client is in our program domain
        // if not...don't show this screen!
        if (!caseManagementMgr.isClientInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo) && !caseManagementMgr.isClientReferredInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo)) {
            return "domain-error";
        }

        current = System.currentTimeMillis();
        logger.debug("client in program " + String.valueOf(current - start));
        start = current;

        request.setAttribute("casemgmt_demoName", getDemoName(demoNo));
        request.setAttribute("casemgmt_demoAge", getDemoAge(demoNo));
        request.setAttribute("casemgmt_demoDOB", getDemoDOB(demoNo));
        request.setAttribute("demographicNo", demoNo);

        logger.debug("client Image?");
        // get client image
        ClientImage img = clientImageMgr.getClientImage(Integer.parseInt(demoNo));
        if (img != null) {
            request.setAttribute("image_exists", "true");
        }

        current = System.currentTimeMillis();
        logger.debug("client image " + String.valueOf(current - start));
        start = current;

        String programId = (String) request.getSession().getAttribute("case_program_id");

        if (programId == null || programId.length() == 0) {
            programId = "0";
        }

        logger.debug("is there a tmp note?");
        // check to see if there is an unsaved note
        // if there is see if casemanagemententry has already handled it
        // if it has, disregard unsaved note; if it has not then set attribute
        CaseManagementTmpSave tmpsavenote = this.caseManagementMgr.restoreTmpSave(loggedInInfo.getLoggedInProviderNo(), demoNo, programId);
        if (tmpsavenote != null) {
            String restoring = (String) se.getAttribute("restoring");
            if (restoring == null) request.setAttribute("can_restore", Boolean.valueOf(true));
            else se.setAttribute("restoring", null);
        }

        current = System.currentTimeMillis();
        logger.debug("tmp note " + String.valueOf(current - start));
        start = current;

        logger.debug("Get admission");
        String teamName = "";
        Admission admission = null;
        
        // Only get admission if we have a valid programId
        if (programId != null && !programId.equals("0") && !programId.isEmpty()) {
            try {
                admission = admissionMgr.getCurrentAdmission(programId, Integer.valueOf(demoNo));
            } catch (Exception e) {
                logger.debug("No admission found for programId: " + programId + " and demoNo: " + demoNo + " - " + e.getMessage());
            }
        } else {
            logger.debug("No valid programId available - skipping admission lookup");
        }
        
        current = System.currentTimeMillis();
        logger.debug("Get admission " + String.valueOf(current - start));
        start = current;

        if (admission != null && admission.getTeamId() != null) {
            logger.debug("Get teams");
            List<ProgramTeam> teams = programMgr.getProgramTeams(programId);
            current = System.currentTimeMillis();
            logger.debug("Get teams " + String.valueOf(current - start));
            start = current;

            for (Iterator<ProgramTeam> i = teams.iterator(); i.hasNext(); ) {
                logger.debug("Searching teams");
                ProgramTeam team = i.next();
                String id1 = Integer.toString(team.getId());
                String id2 = Integer.toString(admission.getTeamId());
                if (id1.equals(id2)) teamName = team.getName();
            }
        }
        request.setAttribute("teamName", teamName);

        if (OscarProperties.getInstance().isCaisiLoaded() && !useNewCaseMgmt) {

            logger.debug("Get program providers");
            List<String> teamMembers = new ArrayList<String>();
            List<ProgramProvider> ps = new ArrayList<ProgramProvider>();
            
            // Only get program providers if we have a valid programId
            if (programId != null && !programId.equals("0") && !programId.isEmpty()) {
                try {
                    ps = programMgr.getProgramProviders(programId);
                } catch (Exception e) {
                    logger.debug("Unable to get program providers for programId: " + programId + " - " + e.getMessage());
                }
            }
            current = System.currentTimeMillis();
            logger.debug("Get program providers " + String.valueOf(current - start));
            start = current;

            for (Iterator<ProgramProvider> j = ps.iterator(); j.hasNext(); ) {
                ProgramProvider pp = j.next();
                logger.debug("Get program providers teams");
                for (Iterator<ProgramTeam> k = pp.getTeams().iterator(); k.hasNext(); ) {
                    ProgramTeam pt = k.next();
                    if (pt.getName().equals(teamName)) {
                        teamMembers.add(pp.getProvider().getFormattedName());
                    }
                }
                current = System.currentTimeMillis();
                logger.debug("Get program providers teams " + String.valueOf(current - start));
                start = current;

            }
            request.setAttribute("teamMembers", teamMembers);

            /* prepare new form list for patient */
            EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
            se.setAttribute("casemgmt_newFormBeans", encounterFormDao.findAll());

            /* prepare messenger list */
            se.setAttribute("casemgmt_msgBeans", this.caseManagementMgr.getMsgBeans(Integer.valueOf(demoNo)));

            // readonly access to define creat a new note button in jsp.
            se.setAttribute("readonly", Boolean.valueOf(this.caseManagementMgr.hasAccessRight("note-read-only", "access", loggedInInfo.getLoggedInProviderNo(), demoNo, (String) se.getAttribute("case_program_id"))));

        }
        /* Dx */
        List<Dxresearch> dxList = this.caseManagementMgr.getDxByDemographicNo(demoNo);
        Map<String, Dxresearch> dxMap = new HashMap<String, Dxresearch>();
        for (Dxresearch dx : dxList) {
            dxMap.put(dx.getDxresearchCode(), dx);
        }
        request.setAttribute("dxMap", dxMap);

        // UCF
        /* ISSUES */
        if (tab.equals("Current Issues")) {
            if (useNewCaseMgmt) viewCurrentIssuesTab_newCme(demoNo, programId);
            else viewCurrentIssuesTab_oldCme(demoNo, programId);
        } // end Current Issues Tab

        logger.debug("Get CPP");
        current = System.currentTimeMillis();
        CaseManagementCPP cpp = this.caseManagementMgr.getCPP(this.getDemographicNo(request));
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(getDemographicNo(request));
        }
        request.setAttribute("cpp", cpp);
        this.setCpp(cpp);
        current = System.currentTimeMillis();
        logger.debug("Get CPP " + String.valueOf(current - start));
        start = current;

        /* get allergies */
        logger.debug("Get Allergies");
        List<Allergy> allergies = this.caseManagementMgr.getAllergies(this.getDemographicNo(request));
        request.setAttribute("Allergies", allergies);
        current = System.currentTimeMillis();
        logger.debug("Get Allergies " + String.valueOf(current - start));
        start = current;

        /* get prescriptions */
        if (tab.equals("Prescriptions")) {
            List<Drug> prescriptions = null;
            boolean viewAll = this.getPrescipt_view().equals("all");
            String demographicId = getDemographicNo(request);
            request.setAttribute("isIntegratorEnabled", loggedInInfo.getCurrentFacility().isIntegratorEnabled());
            prescriptions = caseManagementMgr.getPrescriptions(loggedInInfo, Integer.parseInt(demographicId), viewAll);

            request.setAttribute("Prescriptions", prescriptions);

            // Setup RX bean start
            RxSessionBean bean = new RxSessionBean();
            bean.setProviderNo(loggedInInfo.getLoggedInProviderNo());
            bean.setDemographicNo(Integer.parseInt(demoNo));
            request.getSession().setAttribute("RxSessionBean", bean);
            // Setup RX end
        }

        /* tickler */
        if (tab != null && tab.equalsIgnoreCase("Ticklers")) {
            CustomFilter cf = new CustomFilter();
            cf.setDemographicNo(this.getDemographicNo(request));
            cf.setStatus("A");
            request.setAttribute("ticklers", ticklerManager.getTicklers(loggedInInfo, cf));
        }

        if (tab != null && tab.equalsIgnoreCase("Search")) {
            request.setAttribute("roles", roleMgr.getRoles());
            request.setAttribute("program_domain", programMgr.getProgramDomain(getProviderNo(request)));
        }

        /* set form value for e-chart */

        Locale vLocale = request.getLocale();
        this.setVlCountry(vLocale.getCountry());
        this.setDemographicNo(getDemographicNo(request));

        se.setAttribute("casemgmt_DemoNo", demoNo);
        this.setRootCompURL((String) se.getAttribute("casemgmt_oscar_baseurl"));
        se.setAttribute("casemgmt_VlCountry", vLocale.getCountry());

        // if we have just saved a note, remove saveNote flag
        String varName = "saveNote" + demoNo;
        Boolean saved = (Boolean) se.getAttribute(varName);
        if (saved != null && saved == true) {
            request.setAttribute("saveNote", saved);
            se.removeAttribute(varName);
        }
        current = System.currentTimeMillis();

        //load up custom JavaScript
        //1. try from Properties
        String customCmeJs = OscarProperties.getInstance().getProperty("cme_js");
        if (customCmeJs == null || customCmeJs.length() == 0) {
            request.setAttribute("cme_js", "default");
        } else {
            request.setAttribute("cme_js", customCmeJs);
        }

        //2. Override from provider preferences?

        //3. Override based on appointment type?
        logger.debug("VIEW Exiting " + String.valueOf(current - beginning));

        String printPreview = (String) request.getAttribute("patientCppPrintPreview");
        if ("true".equals(printPreview)) {
            request.setAttribute("patientCppPrintPreview", "false");
            return "clientHistoryPrintPreview";
        } else {
            if (useNewCaseMgmt) {
                String fwdName = request.getParameter("ajaxview");
                if (fwdName == null || fwdName.equals("") || fwdName.equalsIgnoreCase("null")) {
                    return "page.newcasemgmt.view";
                } else {
                    return fwdName;
                }
            } else return "page.casemgmt.view";
        }
    }

    private void viewCurrentIssuesTab_oldCme(String demoNo, String programId) throws Exception {
        long startTime = System.currentTimeMillis();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        int demographicNo = Integer.parseInt(demoNo);
        boolean hideInactiveIssues = Boolean.parseBoolean(this.getHideActiveIssue());

        ArrayList<CheckBoxBean> checkBoxBeanList = new ArrayList<CheckBoxBean>();
        addLocalIssues(providerNo, checkBoxBeanList, demographicNo, hideInactiveIssues, Integer.valueOf(programId));
        addRemoteIssues(loggedInInfo, checkBoxBeanList, demographicNo, hideInactiveIssues);
        addGroupIssues(loggedInInfo, checkBoxBeanList, demographicNo, hideInactiveIssues);

        sortIssues(checkBoxBeanList);
        request.setAttribute("Issues", checkBoxBeanList);
        logger.debug("Get issues time : " + (System.currentTimeMillis() - startTime));

        logger.debug("Get stale note date");
        startTime = System.currentTimeMillis();
        // filter the notes by the checked issues and date if set
        UserProperty userProp = caseManagementMgr.getUserProperty(providerNo, UserProperty.STALE_NOTEDATE);
        request.setAttribute(UserProperty.STALE_NOTEDATE, userProp);
        UserProperty userProp2 = caseManagementMgr.getUserProperty(providerNo, UserProperty.STALE_FORMAT);
        request.setAttribute(UserProperty.STALE_FORMAT, userProp2);
        logger.debug("Get stale note date " + (System.currentTimeMillis() - startTime));

        /* PROGRESS NOTES */
        startTime = System.currentTimeMillis();
        String[] checkedIssues = request.getParameterValues("check_issue");

        // extract just the codes for local usage
        ArrayList<String> checkedCodeList = new ArrayList<String>();
        if (checkedIssues != null) {
            for (String s : checkedIssues) {
                String[] temp = s.split("\\.");
                if (temp.length == 2) checkedCodeList.add(temp[1]);
                else logger.warn("Unexpected parameter, wrong format : " + s);
            }
        }

        ArrayList<NoteDisplay> notesToDisplay = new ArrayList<NoteDisplay>();

        // deal with local notes
        startTime = System.currentTimeMillis();
        Collection<CaseManagementNote> localNotes = caseManagementNoteDao.findNotesByDemographicAndIssueCode(demographicNo, checkedCodeList.toArray(new String[0]));
        //show locked notes anyway: localNotes = manageLockedNotes(localNotes, true, this.getUnlockedNotesMap(request));
        localNotes = manageLockedNotes(localNotes, false, this.getUnlockedNotesMap(request));
        
        // Only filter if we have a valid program ID
        if (programId != null && !programId.equals("0") && !programId.isEmpty()) {
            localNotes = caseManagementMgr.filterNotes(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), localNotes, programId);
        }

        caseManagementMgr.getEditors(localNotes);

        for (CaseManagementNote noteTemp : localNotes)
            notesToDisplay.add(new NoteDisplayLocal(loggedInInfo, noteTemp));
        logger.debug("FETCHED " + localNotes.size() + " NOTES in time : " + (System.currentTimeMillis() - startTime));

        // deal with remote notes
        startTime = System.currentTimeMillis();
        List<Issue> issueList = issueDao.findIssueByCode(checkedCodeList.toArray(new String[0]));
        addRemoteNotes(loggedInInfo, notesToDisplay, demographicNo, issueList, programId);
        addGroupNotes(loggedInInfo, notesToDisplay, Integer.parseInt(demoNo), null);
        logger.debug("Get remote notes. time=" + (System.currentTimeMillis() - startTime));

        // not sure what everything else is after this
        String resetFilter = request.getParameter("resetFilter");
        logger.debug("RESET FILTER " + resetFilter);
        if (resetFilter != null && resetFilter.equals("true")) {
            logger.debug("CASEMGMTVIEW RESET FILTER");
            this.setFilter_providers(null);
            this.setFilter_roles(null);
            this.setNote_sort(null);
        }

        // apply if we are filtering on role
        logger.debug("Filter on Role");
        startTime = System.currentTimeMillis();
        List<Secrole> roles = roleMgr.getRoles();
        request.setAttribute("roles", roles);
        String[] roleId = this.getFilter_roles();
        notesToDisplay = applyRoleFilter(notesToDisplay, roleId);
        logger.debug("Filter on Role " + (System.currentTimeMillis() - startTime));

        // filter providers
        notesToDisplay = applyProviderFilter(notesToDisplay, this.getFilter_providers());

        // set providers to display
        HashSet<LabelValueBean> providers = new HashSet<LabelValueBean>();
        for (NoteDisplay tempNote : notesToDisplay) {
            String tempProvider = tempNote.getProviderName();
            providers.add(new LabelValueBean(tempProvider, tempProvider));
        }
        request.setAttribute("providers", providers);

        /*
         * people are changing the default sorting of notes so it's safer to explicity set it here, some one already changed it once and it reversed our sorting.
         */
        logger.debug("Apply sorting to notes");
        startTime = System.currentTimeMillis();
        String noteSort = this.getNote_sort();
        if (noteSort != null && noteSort.length() > 0) {
            notesToDisplay = sortNotes(notesToDisplay, noteSort);
        } else {
            OscarProperties p = OscarProperties.getInstance();
            noteSort = p.getProperty("CMESort", "");
            if (noteSort.trim().equalsIgnoreCase("UP"))
                notesToDisplay = sortNotes(notesToDisplay, "observation_date_asc");
            else notesToDisplay = sortNotes(notesToDisplay, "observation_date_desc");
        }

        request.setAttribute("Notes", notesToDisplay);
        logger.debug("Apply sorting to notes " + (System.currentTimeMillis() - startTime));
    }

    private void sortIssues(ArrayList<CheckBoxBean> checkBoxBeanList) {
        Comparator<CheckBoxBean> cbbComparator = new Comparator<CheckBoxBean>() {
            public int compare(CheckBoxBean o1, CheckBoxBean o2) {
                if (o1.getIssueDisplay() != null && o2.getIssueDisplay() != null && o1.getIssueDisplay().code != null) {
                    return (o1.getIssueDisplay().code.compareTo(o2.getIssueDisplay().code));
                } else return (0);
            }
        };

        Collections.sort(checkBoxBeanList, cbbComparator);
    }

    public void sortIssuesByOrderId(ArrayList<CheckBoxBean> checkBoxBeanList) {
        Comparator<CheckBoxBean> cbbComparator = new Comparator<CheckBoxBean>() {
            public int compare(CheckBoxBean o1, CheckBoxBean o2) {
                if (o1.getIssueDisplay() != null && o2.getIssueDisplay() != null && o1.getIssueDisplay().sortOrderId != null && o2.getIssueDisplay().sortOrderId != null) {
                    return (o1.getIssueDisplay().sortOrderId.compareTo(o2.getIssueDisplay().sortOrderId));
                } else return (0);
            }
        };

        Collections.sort(checkBoxBeanList, cbbComparator);
    }

    /**
     * New CME
     */
    private void viewCurrentIssuesTab_newCmeNotes(String demoNo, String programId) throws Exception {
        int demographicId = Integer.parseInt(demoNo);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        long startTime;
        startTime = System.currentTimeMillis();

        /* PROGRESS NOTES */
        List<CaseManagementNote> notes = null;
        // here we might have a checked/unchecked issue that is remote and has no issue_id (they're all zero).
        String[] checkedIssues = request.getParameterValues("check_issue");

        if (request.getParameter("offset") != null && request.getParameter("numToReturn") != null) {
            Integer offset = Integer.parseInt(request.getParameter("offset"));
            Integer numToReturn = Integer.parseInt(request.getParameter("numToReturn"));
            if (offset > 0) request.setAttribute("moreNotes", true);
            notes = caseManagementMgr.getNotesWithLimit(demoNo, offset, numToReturn);
        } else {
            notes = caseManagementMgr.getNotes(demoNo);
        }

        logger.debug("FETCHED " + notes.size() + " NOTES");

        startTime = System.currentTimeMillis();
        String resetFilter = request.getParameter("resetFilter");
        logger.debug("RESET FILTER " + resetFilter);
        if (resetFilter != null && resetFilter.equals("true")) {
            logger.debug("CASEMGMTVIEW RESET FILTER");
            this.setFilter_providers(null);
            this.setFilter_roles(null);
            this.setNote_sort(null);
            this.setIssues(null);
        }

        logger.debug("Filter Notes");

        // filter notes based on role and program/providers mappings
        // Only filter if we have a valid program ID
        if (programId != null && !programId.equals("0") && !programId.isEmpty()) {
            notes = caseManagementMgr.filterNotes(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), notes, programId);
        }
        logger.debug("FILTER NOTES " + (System.currentTimeMillis() - startTime));

        // apply providers filter
        logger.debug("Filter Notes Provider");
        startTime = System.currentTimeMillis();
        notes = applyProviderFilters(notes, this.getFilter_providers());
        logger.debug("FILTER NOTES PROVIDER " + (System.currentTimeMillis() - startTime));

        // apply if we are filtering on role
        logger.debug("Filter on Role");
        startTime = System.currentTimeMillis();
        String[] roleId = this.getFilter_roles();
        if (roleId != null && roleId.length > 0) notes = applyRoleFilter(notes, roleId);
        logger.debug("Filter on Role " + (System.currentTimeMillis() - startTime));

        // apply if we are filtering on issues
        logger.debug("Filter on issues");
        startTime = System.currentTimeMillis();

        if (checkedIssues != null && checkedIssues.length > 0) notes = applyIssueFilter(notes, checkedIssues);
        logger.debug("Filter on issue " + (System.currentTimeMillis() - startTime));

        // this is a local filter and does not apply to remote notes
        logger.debug("Pop notes with editors");
        startTime = System.currentTimeMillis();
        this.caseManagementMgr.getEditors(notes);
        logger.debug("Pop notes with editors " + (System.currentTimeMillis() - startTime));

        ArrayList<NoteDisplay> notesToDisplay = new ArrayList<NoteDisplay>();
        for (CaseManagementNote noteTemp : notes) {
            notesToDisplay.add(new NoteDisplayLocal(loggedInInfo, noteTemp));
        }

        if (request.getParameter("offset") == null || request.getParameter("offset").equalsIgnoreCase("0")) {
            addRemoteNotes(loggedInInfo, notesToDisplay, demographicId, null, programId);
            addGroupNotes(loggedInInfo, notesToDisplay, demographicId, null);

            // add eforms to notes list as single line items
            String roleName = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");
            ArrayList<HashMap<String, ? extends Object>> eForms = EFormUtil.listPatientEForms(EFormUtil.DATE, EFormUtil.CURRENT, demoNo, roleName);

            // add forms to notes list as single line items
            ArrayList<PatientForm> allPatientForms = EctFormData.getGroupedPatientFormsFromAllTables(demographicId);

            for (HashMap<String, ? extends Object> eform : eForms) {
                notesToDisplay.add(new NoteDisplayNonNote(eform));
            }

            // add forms to notes list as single line items
            for (PatientForm patientForm : allPatientForms) {
                notesToDisplay.add(new NoteDisplayNonNote(patientForm));
            }

            if (OscarProperties.getInstance().getProperty("billregion", "").equalsIgnoreCase("ON")) {
                fetchInvoices(notesToDisplay, demoNo);
            }
        }

        // sort the notes
        String noteSort = OscarProperties.getInstance().getProperty("CMESort", "");
        if (noteSort.trim().equalsIgnoreCase("UP")) notesToDisplay = sortNotes(notesToDisplay, "observation_date_asc");
        else notesToDisplay = sortNotes(notesToDisplay, "observation_date_desc");

        request.setAttribute("notesToDisplay", notesToDisplay);
    }

    /**
     * New CME
     */
    private void viewCurrentIssuesTab_newCme(String demoNo, String programId) throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        int demographicId = Integer.parseInt(demoNo);

        long startTime;
        startTime = System.currentTimeMillis();

        logger.debug("Get stale note date");
        // filter the notes by the checked issues and date if set
        UserProperty userProp = caseManagementMgr.getUserProperty(providerNo, UserProperty.STALE_NOTEDATE);
        request.setAttribute(UserProperty.STALE_NOTEDATE, userProp);
        UserProperty userProp2 = caseManagementMgr.getUserProperty(providerNo, UserProperty.STALE_FORMAT);
        request.setAttribute(UserProperty.STALE_FORMAT, userProp2);
        logger.debug("Get stale note date " + (System.currentTimeMillis() - startTime));

        ArrayList<CheckBoxBean> checkBoxBeanList = new ArrayList<CheckBoxBean>();
        addLocalIssues(providerNo, checkBoxBeanList, demographicId, false, Integer.valueOf(programId));
        addRemoteIssues(loggedInInfo, checkBoxBeanList, demographicId, false);
        addGroupIssues(loggedInInfo, checkBoxBeanList, demographicId, false);
        sortIssues(checkBoxBeanList);
        request.setAttribute("cme_issues", checkBoxBeanList);

        Set<Provider> providers = new HashSet<Provider>(caseManagementMgr.getAllEditors(demoNo));
        request.setAttribute("providers", providers);

        List<Secrole> roles = roleMgr.getRoles();
        request.setAttribute("roles", roles);
    }

    private void fetchInvoices(ArrayList<NoteDisplay> notes, String demographicNo) {
        List<BillingONCHeader1> bills = billingONCHeader1Dao.getInvoices(Integer.parseInt(demographicNo), MAX_INVOICES);

        for (BillingONCHeader1 h1 : bills) {
            notes.add(new NoteDisplayNonNote(h1));
        }
    }

    private List<CaseManagementNote> applyRoleFilter(List<CaseManagementNote> notes, String[] roleId) {

        // if no filter return everything
        if (Arrays.binarySearch(roleId, "a") >= 0) return notes;

        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();

        for (Iterator<CaseManagementNote> iter = notes.listIterator(); iter.hasNext(); ) {
            CaseManagementNote note = iter.next();

            if (Arrays.binarySearch(roleId, note.getReporter_caisi_role()) >= 0) filteredNotes.add(note);
        }

        return filteredNotes;
    }

    private List<CaseManagementNote> applyIssueFilter(List<CaseManagementNote> notes, String[] issueId) {

        // if no filter return everything
        if (Arrays.binarySearch(issueId, "a") >= 0) return notes;

        boolean none = (Arrays.binarySearch(issueId, "n") >= 0) ? true : false;

        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();

        for (Iterator<CaseManagementNote> iter = notes.listIterator(); iter.hasNext(); ) {
            CaseManagementNote note = iter.next();
            List<CaseManagementIssue> issues = cmeIssueNotesDao.getNoteIssues((Integer.valueOf(note.getId().toString())));
            if (issues.size() == 0 && none) {
                filteredNotes.add(note);
            } else {
                for (CaseManagementIssue issue : issues) {
                    if (Arrays.binarySearch(issueId, String.valueOf(issue.getId())) >= 0) {
                        filteredNotes.add(note);
                        break;
                    }
                }
            }
        }

        return filteredNotes;
    }

    private List<CaseManagementNote> manageLockedNotes(List<CaseManagementNote> notes, boolean removeLockedNotes, Map<Long, Boolean> unlockedNotesMap) {
        List<CaseManagementNote> notesNoLocked = new ArrayList<CaseManagementNote>();
        for (CaseManagementNote note : notes) {
            if (note.isLocked()) {
                if (unlockedNotesMap.get(note.getId()) != null) {
                    note.setLocked(false);
                }
            }
            if (removeLockedNotes && !note.isLocked()) {
                notesNoLocked.add(note);
            }
        }
        if (removeLockedNotes) {
            return notesNoLocked;
        }
        return notes;
    }

    private List<CaseManagementNote> applyProviderFilters(List<CaseManagementNote> notes, String[] providerNo) {
        boolean filter = false;
        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();

        if (providerNo != null && Arrays.binarySearch(providerNo, "a") < 0) {
            filter = true;
        }

        for (Iterator<CaseManagementNote> iter = notes.iterator(); iter.hasNext(); ) {
            CaseManagementNote note = iter.next();
            if (!filter) {
                // no filter, add all
                filteredNotes.add(note);

            } else {
                if (Arrays.binarySearch(providerNo, note.getProviderNo()) >= 0)
                    // correct providers
                    filteredNotes.add(note);
            }
        }

        return filteredNotes;
    }

    private static boolean hasRole(List<SecUserRole> roles, String role) {
        if (roles == null) return (false);

        logger.debug("Note Role : " + role);

        for (SecUserRole roleTmp : roles) {
            logger.debug("Provider Roles : " + roleTmp.getRoleName());
            if (roleTmp.getRoleName().equals(role)) return (true);
        }

        return (false);
    }

    private void addGroupNotes(LoggedInInfo loggedInInfo, ArrayList<NoteDisplay> notesToDisplay, int demographicNo, ArrayList<String> issueCodesToDisplay) {

        List<SecUserRole> roles = secUserRoleDao.getUserRoles(loggedInInfo.getLoggedInProviderNo());

        if (!loggedInInfo.getCurrentFacility().isEnableGroupNotes()) return;

        List<GroupNoteLink> noteLinks = groupNoteDao.findLinksByDemographic(demographicNo);
        for (GroupNoteLink noteLink : noteLinks) {
            try {

                int orginalNoteId = noteLink.getNoteId();
                CaseManagementNote note = this.caseManagementNoteDao.getNote(Long.valueOf(orginalNoteId));

                // filter on issues to display
                // filter on role based access
                String roleName = this.roleMgr.getRole(note.getReporter_caisi_role()).getRoleName();
                if (hasRole(roles, roleName)) {
                    String originaldemo = note.getDemographic_no();

                    note.setDemographic_no(String.valueOf(demographicNo));
                    NoteDisplayLocal disp = new NoteDisplayLocal(loggedInInfo, note);
                    disp.setReadOnly(true);
                    disp.setGroupNote(true);
                    Demographic origDemographic = demographicDao.getDemographic(originaldemo);
                    disp.setLocation(String.valueOf(origDemographic.getDemographicNo()));
                    notesToDisplay.add(disp);
                }
            } catch (Exception e) {
                logger.error("Unexpected error.", e);
            }
        }

    }

    private void addRemoteNotes(LoggedInInfo loggedInInfo, ArrayList<NoteDisplay> notesToDisplay, int demographicNo, List<Issue> issueCodesToDisplay, String programId) {
        if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) return;
        List<CachedDemographicNote> linkedNotes = null;
        try {
            if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                linkedNotes = CaisiIntegratorManager.getLinkedNotes(loggedInInfo, demographicNo);
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
        }

        if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
            linkedNotes = IntegratorFallBackManager.getLinkedNotes(loggedInInfo, demographicNo);
        }

        if (linkedNotes == null) return;

        for (CachedDemographicNote cachedDemographicNote : linkedNotes) {
            try {

                // filter on issues to display
                if (issueCodesToDisplay == null || hasIssueToBeDisplayed(cachedDemographicNote, issueCodesToDisplay)) {
                    // filter on role based access
                    if (caseManagementMgr.hasRole(loggedInInfo.getLoggedInProvider().getProviderNo(), cachedDemographicNote, programId)) {
                        notesToDisplay.add(new NoteDisplayIntegrator(loggedInInfo, cachedDemographicNote));
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error.", e);
            }
        }
    }

    private boolean hasIssueToBeDisplayed(CachedDemographicNote cachedDemographicNote, List<Issue> issueCodesToDisplay) {
        // no issue selected means display all
        if (issueCodesToDisplay == null || issueCodesToDisplay.size() == 0) return (true);

        for (NoteIssue noteIssue : cachedDemographicNote.getIssues()) {
            for (Issue issue : issueCodesToDisplay) {
                logger.info("Comparing " + noteIssue.getIssueCode() + " type:" + noteIssue.getCodeType() + " TO " + issue.getCode() + " type:" + issue.getType());
                if (Issue.CUSTOM_ISSUE.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.CUSTOM_ISSUE && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                } else if (Issue.SYSTEM.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.SYSTEM && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                } else if (Issue.ICD_9.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.ICD_9 && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                } else if (Issue.ICD_10.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.ICD_10 && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                } else if (Issue.SNOMED.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.SNOMED && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                } else if (Issue.SNOMED_CORE.equalsIgnoreCase(issue.getType()) && noteIssue.getCodeType() == CodeType.SNOMED_CORE && noteIssue.getIssueCode().equalsIgnoreCase(issue.getCode())) {
                    return true;
                }
            }
        }

        return (false);
    }

    /*
     * This does absolutely nothing
     */
    protected void addGroupIssues(LoggedInInfo loggedInInfo, ArrayList<CheckBoxBean> checkBoxBeanList, int demographicNo, boolean hideInactiveIssues) {

        if (!loggedInInfo.getCurrentFacility().isEnableGroupNotes()) return;

        try {
            // get all the issues for which we have group notes for
            List<GroupNoteLink> links = this.groupNoteDao.findLinksByDemographic(demographicNo);
            for (GroupNoteLink link : links) {
                int noteId = link.getNoteId();
                List<CaseManagementIssue> issues = this.caseManagementMgr.getIssuesByNote(noteId);
                logger.warn("we are doing nothing with this: " + issues);
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
        }
    }

    protected void addRemoteIssues(LoggedInInfo loggedInInfo, ArrayList<CheckBoxBean> checkBoxBeanList, int demographicNo, boolean hideInactiveIssues) {
        if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) return;

        try {
            List<CachedDemographicIssue> remoteIssues = null;
            try {
                if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                    DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                    remoteIssues = demographicWs.getLinkedCachedDemographicIssuesByDemographicId(demographicNo);
                }
            } catch (Exception e) {
                logger.error("Unexpected error.", e);
                CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
            }

            if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                remoteIssues = IntegratorFallBackManager.getRemoteDemographicIssues(loggedInInfo, demographicNo);
            }

            for (CachedDemographicIssue cachedDemographicIssue : remoteIssues) {
                try {
                    IssueDisplay issueDisplay = null;

                    if (!hideInactiveIssues) issueDisplay = getIssueToDisplay(loggedInInfo, cachedDemographicIssue);
                    else if (!cachedDemographicIssue.isResolved())
                        issueDisplay = getIssueToDisplay(loggedInInfo, cachedDemographicIssue);

                    if (issueDisplay != null) {
                        if (existsIssueWithSameAttributes(issueDisplay, checkBoxBeanList)) continue;

                        CheckBoxBean checkBoxBean = new CheckBoxBean();
                        checkBoxBean.setIssueDisplay(issueDisplay);
                        checkBoxBean.setUsed(caseManagementNoteDao.haveIssue(issueDisplay.getCode(), demographicNo));
                        checkBoxBeanList.add(checkBoxBean);
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error.", e);
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
        }
    }

    private static boolean existsIssueWithSameAttributes(IssueDisplay issueDisplay, ArrayList<CheckBoxBean> checkBoxBeanList) {
        // must iterate through all items, can't stop at first hit
        for (CheckBoxBean cbb : checkBoxBeanList) {
            IssueDisplay existingIssueDisplay = cbb.getIssueDisplay();
            if (hasSameAttributes(existingIssueDisplay, issueDisplay)) return (true);
        }

        return (false);
    }

    public static boolean hasSameAttributes(IssueDisplay issueDisplay1, IssueDisplay issueDisplay2) {
        if (issueDisplay1.code != null && !issueDisplay1.code.equals(issueDisplay2.code)) return (false);
        if (issueDisplay1.acute != null && !issueDisplay1.acute.equals(issueDisplay2.acute)) return (false);
        if (issueDisplay1.certain != null && !issueDisplay1.certain.equals(issueDisplay2.certain)) return (false);
        if (issueDisplay1.major != null && !issueDisplay1.major.equals(issueDisplay2.major)) return (false);
        if (issueDisplay1.priority != null && !issueDisplay1.priority.equals(issueDisplay2.priority)) return (false);
        if (issueDisplay1.resolved != null && !issueDisplay1.resolved.equals(issueDisplay2.resolved)) return (false);

        return (true);
    }

    private IssueDisplay getIssueToDisplay(LoggedInInfo loggedInInfo, CachedDemographicIssue cachedDemographicIssue) throws MalformedURLException {
        IssueDisplay issueDisplay = new IssueDisplay();

        issueDisplay.writeAccess = true;
        issueDisplay.acute = cachedDemographicIssue.isAcute() ? "acute" : "chronic";
        issueDisplay.certain = cachedDemographicIssue.isCertain() ? "certain" : "uncertain";
        issueDisplay.code = cachedDemographicIssue.getFacilityDemographicIssuePk().getIssueCode();
        issueDisplay.codeType = "ICD10"; // temp hard coded hack till issue is resolved

        Issue issue = null;
        // temp hard coded icd hack till issue is resolved
        if ("ICD10".equalsIgnoreCase(OscarProperties.getInstance().getProperty("COMMUNITY_ISSUE_CODETYPE").toUpperCase())) {
            issue = issueDao.findIssueByCode(cachedDemographicIssue.getFacilityDemographicIssuePk().getIssueCode());
        }

        if (issue != null) {
            issueDisplay.description = issue.getDescription();
            issueDisplay.priority = issue.getPriority();
            issueDisplay.role = issue.getRole();
        } else {
            issueDisplay.description = "Not Available";
            issueDisplay.priority = "Not Available";
            issueDisplay.role = "Not Available";
        }

        Integer remoteFacilityId = cachedDemographicIssue.getFacilityDemographicIssuePk().getIntegratorFacilityId();
        CachedFacility remoteFacility = CaisiIntegratorManager.getRemoteFacility(loggedInInfo, loggedInInfo.getCurrentFacility(), remoteFacilityId);
        if (remoteFacility != null) issueDisplay.location = "remote: " + remoteFacility.getName();
        else issueDisplay.location = "remote: name unavailable";

        issueDisplay.major = cachedDemographicIssue.isMajor() ? "major" : "not major";
        issueDisplay.resolved = cachedDemographicIssue.isResolved() ? "resolved" : "unresolved";

        return (issueDisplay);
    }

    protected void addLocalIssues(String providerNo, ArrayList<CheckBoxBean> checkBoxBeanList, Integer demographicNo, boolean hideInactiveIssues, Integer programId) {
        List<CaseManagementIssue> localIssues = caseManagementManager.getIssues(demographicNo, hideInactiveIssues ? false : null);

        for (CaseManagementIssue cmi : localIssues) {
            CheckBoxBean checkBoxBean = new CheckBoxBean();

            checkBoxBean.setIssue(cmi);

            IssueDisplay issueDisplay = getIssueDisplay(providerNo, programId, cmi);
            checkBoxBean.setIssueDisplay(issueDisplay);

            checkBoxBean.setUsed(caseManagementNoteDao.haveIssue(cmi.getIssue().getCode(), demographicNo));

            checkBoxBeanList.add(checkBoxBean);
        }
    }

    protected IssueDisplay getIssueDisplay(String providerNo, Integer programId, CaseManagementIssue cmi) {
        IssueDisplay issueDisplay = new IssueDisplay();

        if (programId != null) issueDisplay.writeAccess = cmi.isWriteAccess(providerNo, programId);

        issueDisplay.acute = cmi.isAcute() ? "acute" : "chronic";
        issueDisplay.certain = cmi.isCertain() ? "certain" : "uncertain";

        long issueId = cmi.getIssue_id();
        Issue issue = issueDao.getIssue(issueId);

        issueDisplay.code = issue.getCode();
        issueDisplay.codeType = OscarProperties.getInstance().getProperty("COMMUNITY_ISSUE_CODETYPE").toUpperCase();
        issueDisplay.description = issue.getDescription();
        issueDisplay.location = "local";
        issueDisplay.major = cmi.isMajor() ? "major" : "not major";
        issueDisplay.priority = issue.getPriority();
        issueDisplay.resolved = cmi.isResolved() ? "resolved" : "unresolved";
        issueDisplay.role = issue.getRole();
        issueDisplay.sortOrderId = issue.getSortOrderId();

        return issueDisplay;
    }

    public String viewNote() {
        String nId = request.getParameter("noteId");
        CaseManagementNote note = this.caseManagementMgr.getNote(nId);
        request.setAttribute("noteStr", note.getNote());
        boolean raw = request.getParameter("raw").equalsIgnoreCase("true");
        request.setAttribute("raw", raw);
        return "displayNote";
    }

    public String listNotes() throws Exception {
        logger.debug("List Notes start");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String providerNo = getProviderNo(request);
        String demoNo = getDemographicNo(request);
        Collection<CaseManagementNote> notes = null;

        String appointmentNo = request.getParameter("appointment_no");

        String[] codes = request.getParameterValues("issue_code");
        SecurityManager securityManager = new SecurityManager();
        //these are the ones on the right nav bar. - generic implementation of view access
        if (codes != null && codes.length > 0) {
            if (!securityManager.hasReadAccess("_" + codes[0], request.getSession().getAttribute("userrole") + "," + request.getSession().getAttribute("user"))) {
                return null;
            }
        }

        String roleName = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");

        boolean a = true;
        if (codes[0].equalsIgnoreCase("OMeds")) {
            a = hasPrivilege("_newCasemgmt.otherMeds", roleName);
            if (!a) {
                return SUCCESS; // The link of Other Meds won't show up on new CME screen.
            }
        } else if (codes[0].equalsIgnoreCase("RiskFactors")) {
            a = hasPrivilege("_newCasemgmt.riskFactors", roleName);
            if (!a) {
                return SUCCESS; // The link of Risk Factors won't show up on new CME screen.
            }
        } else if (codes[0].equalsIgnoreCase("FamHistory")) {
            a = hasPrivilege("_newCasemgmt.familyHistory", roleName);
            if (!a) {
                return SUCCESS; // The link of Family History won't show up on new CME screen.
            }
        } else if (codes[0].equalsIgnoreCase("MedHistory")) {
            a = hasPrivilege("_newCasemgmt.medicalHistory", roleName);
            if (!a) {
                return SUCCESS; // The link of Medical History won't show up on new CME screen.
            }
        }

        // set save url to be used by ajax editor
        String identUrl = request.getQueryString();
        request.setAttribute("identUrl", identUrl);

        // filter the notes by the checked issues
        List<Issue> issues = caseManagementMgr.getIssueInfoByCode(providerNo, codes);
        StringBuilder checked_issues = new StringBuilder();
        StringBuilder cppIssues = new StringBuilder();
        String[] issueIds = new String[issues.size()];
        int idx = 0;
        for (Issue issue : issues) {
            checked_issues.append("&issue_id=" + String.valueOf(issue.getId()));
            if (idx > 0) {
                cppIssues.append(";");
            }
            cppIssues.append(issue.getId() + ";" + issue.getCode() + ";" + issue.getDescription());
            issueIds[idx] = String.valueOf(issue.getId());
            idx++;
        }

        // set save Url
        String addUrl = request.getContextPath() + "/CaseManagementEntry.do?method=issueNoteSave&providerNo=" + providerNo + "&demographicNo=" + demoNo + "&appointmentNo=" + appointmentNo + "&noteId=";
        request.setAttribute("addUrl", addUrl);
        request.setAttribute("cppIssue", cppIssues.toString());

        // set issueIds for retrieving history
        request.setAttribute("issueIds", StringUtils.join(issueIds, ","));

        // need to apply issue filter
        notes = caseManagementMgr.getActiveNotes(demoNo, issueIds);
        notes = manageLockedNotes(notes, true, this.getUnlockedNotesMap(request));

        logger.debug("FETCHED " + notes.size() + " NOTES filtered by " + StringUtils.join(issueIds, ","));
        logger.debug("REFERER " + request.getRequestURL().toString() + "?" + request.getQueryString());

        String programId = (String) request.getSession().getAttribute("case_program_id");

        if (programId == null || programId.length() == 0) {
            programId = "0";
        }

        // Only filter notes if we have a valid program ID
        // When programId is "0", skip filtering to show all notes
        if (!programId.equals("0")) {
            notes = caseManagementMgr.filterNotes(loggedInInfo, providerNo, notes, programId);
        }
        this.caseManagementMgr.getEditors(notes);

        List<CaseManagementNoteExt> lcme = new ArrayList<CaseManagementNoteExt>();
        for (Object obj : notes) {
            CaseManagementNote cmn = (CaseManagementNote) obj;
            lcme.addAll(caseManagementMgr.getExtByNote(cmn.getId()));
        }
        request.setAttribute("NoteExts", lcme);
        request.setAttribute("Notes", notes);

        ArrayList<NoteDisplay> remoteNotes = new ArrayList<NoteDisplay>();
        ArrayList<String> issueCodes = new ArrayList<String>(Arrays.asList(codes));
        addRemoteNotes(loggedInInfo, remoteNotes, Integer.parseInt(demoNo), issues, programId);

        if (remoteNotes.size() > 0) {
            request.setAttribute("remoteNotes", remoteNotes);
        }

        boolean isJsonRequest = request.getParameter("json") != null && request.getParameter("json").equalsIgnoreCase("true");
        if (isJsonRequest) {
            HashMap<String, Object> hashMap = new HashMap<String, Object>();

            List<HashMap<String, Object>> notesList = new ArrayList<HashMap<String, Object>>();
            for (Object cmn : notes)
                notesList.add((HashMap<String, Object>) ((CaseManagementNote) cmn).getMap());

            hashMap.put("Items", notesList);
            hashMap.put("RightURL", addUrl);
            hashMap.put("Issues", issues);

            ObjectNode json = objectMapper.valueToTree(hashMap);
            response.getOutputStream().write(json.toString().getBytes());
            return null;
        }

        return "listNotes";
    }

    public String search() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String programId = (String) request.getSession().getAttribute("case_program_id");

        CaseManagementSearchBean searchBean = new CaseManagementSearchBean(this.getDemographicNo(request));
        searchBean.setSearchEncounterType(this.getSearchEncounterType());
        searchBean.setSearchEndDate(this.getSearchEndDate());
        searchBean.setSearchProgramId(this.getSearchProgramId());
        searchBean.setSearchProviderNo(this.getSearchProviderNo());
        searchBean.setSearchRoleId(this.getSearchRoleId());
        searchBean.setSearchStartDate(this.getSearchStartDate());
        searchBean.setSearchText(this.getSearchText());
        List<CaseManagementNote> results = caseManagementMgr.search(searchBean);
        Collection<CaseManagementNote> filtered1 = manageLockedNotes(results, false, this.getUnlockedNotesMap(request));
        
        // Only filter if we have a valid program ID
        List<CaseManagementNote> filteredResults;
        if (programId != null && !programId.equals("0") && !programId.isEmpty()) {
            filteredResults = caseManagementMgr.filterNotes(loggedInInfo, providerNo, filtered1, programId);
        } else {
            filteredResults = new ArrayList<CaseManagementNote>(filtered1);
        }

        List<CaseManagementNote> sortedResults = sortNotes_old(filteredResults, this.getNote_sort());
        request.setAttribute("search_results", sortedResults);
        return view();
    }

    private List<CaseManagementNote> sortNotes_old(Collection<CaseManagementNote> notes, String field) {
        logger.debug("Sorting notes by field: " + field);

        ArrayList<CaseManagementNote> resultsSorted = new ArrayList<CaseManagementNote>(notes);

        if (field == null || field.equals("") || field.equals("update_date")) {
            return resultsSorted;
        }

        if (field.equals("providerName")) {
            Collections.sort(resultsSorted, CaseManagementNote.getProviderComparator());
        }
        if (field.equals("programName")) {
            Collections.sort(resultsSorted, CaseManagementNote.getProgramComparator());
        }
        if (field.equals("roleName")) {
            Collections.sort(resultsSorted, CaseManagementNote.getRoleComparator());
        }
        if (field.equals("observation_date_asc")) {
            Collections.sort(resultsSorted, CaseManagementNote.noteObservationDateComparator);
            Collections.reverse(resultsSorted);
        }
        if (field.equals("observation_date_desc")) {
            Collections.sort(resultsSorted, CaseManagementNote.noteObservationDateComparator);
        }

        return resultsSorted;
    }

    private ArrayList<NoteDisplay> sortNotes(ArrayList<NoteDisplay> notes, String field) {
        logger.debug("Sorting notes by field: " + field);

        if (field == null || field.equals("") || field.equals("update_date")) {
            return notes;
        }

        if (field.equals("providerName")) {
            Collections.sort(notes, NoteDisplay.noteProviderComparator);
        }
        if (field.equals("programName")) {
            Collections.sort(notes, NoteDisplay.noteRoleComparator);
        }
        if (field.equals("observation_date_asc")) {
            Collections.sort(notes, NoteDisplay.noteObservationDateComparator);
            Collections.reverse(notes);
        }
        if (field.equals("observation_date_desc")) {
            Collections.sort(notes, NoteDisplay.noteObservationDateComparator);
        }

        return notes;
    }

    // unlock a note temporarily - session
    /*
     * show password
     */
    public String unlock() throws Exception {
        String noteId = request.getParameter("noteId");
        this.setNoteId(Integer.parseInt(noteId));
        return "unlockForm";
    }

    public String do_unlock_ajax() throws Exception {
        String password = request.getParameter("password");
        int noteId = Integer.parseInt(request.getParameter("noteId"));

        CaseManagementNote note = this.caseManagementMgr.getNote(request.getParameter("noteId"));
        this.caseManagementMgr.getEditors(note);
        request.setAttribute("Note", note);

        boolean success = caseManagementMgr.unlockNote(noteId, password);
        request.setAttribute("success", Boolean.valueOf(success));

        if (success) {
            Map<Long, Boolean> unlockedNoteMap = this.getUnlockedNotesMap(request);
            unlockedNoteMap.put(Long.valueOf(noteId), Boolean.valueOf(success));
            request.getSession().setAttribute("unlockedNoteMap", unlockedNoteMap);
        }

        return "unlock_ajax";

    }

    public String do_unlock() throws Exception {
        String password = this.getPassword();
        int noteId = this.getNoteId();

        boolean success = caseManagementMgr.unlockNote(noteId, password);
        request.setAttribute("success", Boolean.valueOf(success));

        if (success) {
            Map<Long, Boolean> unlockedNoteMap = this.getUnlockedNotesMap(request);
            unlockedNoteMap.put(Long.valueOf(noteId), Boolean.valueOf(success));
            request.getSession().setAttribute("unlockedNoteMap", unlockedNoteMap);
            return "unlockSuccess";
        } else {
            return unlock();
        }

    }

    protected Map<Long, Boolean> getUnlockedNotesMap(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Map<Long, Boolean> map = (Map<Long, Boolean>) request.getSession().getAttribute("unlockedNoteMap");
        if (map == null) {
            map = new HashMap<Long, Boolean>();
        }
        return map;
    }

    private ArrayList<NoteDisplay> applyRoleFilter(ArrayList<NoteDisplay> notes, String[] roleId) {

        if (roleId == null || hasRole(roleId, "a")) return (notes);

        ArrayList<NoteDisplay> filteredNotes = new ArrayList<NoteDisplay>();

        for (NoteDisplay note : notes) {
            if (hasRole(roleId, note.getRoleName())) filteredNotes.add(note);
        }

        return filteredNotes;
    }

    private static boolean hasRole(String[] roleId, String role) {
        for (String s : roleId) {
            if (s.equals(role)) return (true);
        }

        return (false);
    }

    private Collection<CaseManagementNote> manageLockedNotes(Collection<CaseManagementNote> notes, boolean removeLockedNotes, Map<Long, Boolean> unlockedNotesMap) {
        List<CaseManagementNote> notesNoLocked = new ArrayList<CaseManagementNote>();
        for (CaseManagementNote note : notes) {
            if (note.isLocked()) {
                if (unlockedNotesMap.get(note.getId()) != null) {
                    note.setLocked(false);
                }
            }
            if (removeLockedNotes && !note.isLocked()) {
                notesNoLocked.add(note);
            }
        }
        if (removeLockedNotes) {
            return notesNoLocked;
        }
        return notes;
    }

    private ArrayList<NoteDisplay> applyProviderFilter(ArrayList<NoteDisplay> notes, String[] providerName) {
        ArrayList<NoteDisplay> filteredNotes = new ArrayList<NoteDisplay>();

        // no list, or empty list, or list of no providers
        if (providerName == null || providerName.length == 0 || providerName[0].length() == 0) return (notes);

        for (NoteDisplay note : notes) {
            String tempName = note.getProviderName();

            for (String temp : providerName) {
                if (tempName.equals(temp)) filteredNotes.add(note);
            }
        }

        return filteredNotes;
    }

    /*
     * Retrieve CPP issuesIf not in session, load them
     */
    protected Map<String, Issue> getCPPIssues(HttpServletRequest request, String providerNo) {
        @SuppressWarnings("unchecked")
        Map<String, Issue> issues = (HashMap<String, Issue>) request.getSession().getAttribute("CPPIssues");
        if (issues == null) {
            String[] issueCodes = {"SocHistory", "MedHistory", "Concerns", "Reminders", "FamHistory", "RiskFactors"};
            issues = new HashMap<String, Issue>();
            for (String issue : issueCodes) {
                List<Issue> i = caseManagementMgr.getIssueInfoByCode(providerNo, issue);
                issues.put(issue, i.get(0));
            }

            request.getSession().setAttribute("CPPIssues", issues);
        }
        return issues;
    }

    public String addToDx() throws Exception {
        String codingSystem = null;
        Properties dxProps = new Properties();
        try {
            InputStream is = getClass().getResourceAsStream("/caisi_issues_dx.properties");
            dxProps.load(is);
            codingSystem = dxProps.getProperty("coding_system");
        } catch (IOException e) {
            logger.warn("Unable to load Dx properties file");
        }

        this.caseManagementMgr.saveToDx(LoggedInInfo.getLoggedInInfoFromSession(request), getDemographicNo(request), request.getParameter("issue_code"), codingSystem, false);

        return view();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean hasPrivilege(String objectName, String roleName) {
        Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (Vector) v.get(1));
    }

    public static String getNoteColour(NoteDisplay noteDisplay) {
        // set all colors
        String blackColour = "FFFFFF";
        String documentColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().documents + ";";
        String eFormsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().eForms + ";";
        String formsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().forms + ";";
        String rxColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().rx + ";";
        String invoiceColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().invoices + ";";
        String ticklerNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().ticklerNotes + ";";
        String externalNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().externalNotes + ";";
        String emailNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().emailNotes + ";";
        String bgColour = "color:#000000;background-color:#CCCCFF;";

        if (noteDisplay.isCpp()) {
            bgColour = "color:#FFFFFF;background-color:#" + getCppColour(noteDisplay) + ";";
            if (noteDisplay.isTicklerNote()) {
                bgColour = ticklerNoteColour;
            } else if (noteDisplay.isExternalNote()) {
                bgColour = externalNoteColour;
            }
        } else if (noteDisplay.isDocument()) {
            bgColour = documentColour;
        } else if (noteDisplay.isRxAnnotation()) {
            bgColour = rxColour;
        } else if (noteDisplay.isEformData()) {
            bgColour = eFormsColour;
        } else if (noteDisplay.isEncounterForm()) {
            bgColour = formsColour;
        } else if (noteDisplay.isInvoice()) {
            bgColour = invoiceColour;
        } else if (noteDisplay.isEmailNote()) {
            bgColour = emailNoteColour;
        }

        return (bgColour);
    }

    private static String getCppColour(NoteDisplay noteDisplay) {
        Colour colour = Colour.getInstance();

        if (noteDisplay.containsIssue("OMeds")) return (colour.omed);
        else if (noteDisplay.containsIssue("FamHistory")) return (colour.familyHistory);
        else if (noteDisplay.containsIssue("RiskFactors")) return (colour.riskFactors);
        else if (noteDisplay.containsIssue("SocHistory")) return (colour.socialHistory);
        else if (noteDisplay.containsIssue("MedHistory")) return (colour.medicalHistory);
        else if (noteDisplay.containsIssue("Concerns")) return (colour.ongoingConcerns);
        else if (noteDisplay.containsIssue("Reminders")) return (colour.reminders);
        else return colour.prevention;
    }

    public static CaseManagementNote getLatestCppNote(String demographicNo, long issueId, int appointmentNo, boolean filterByAppointment) {
        CaseManagementManager caseManagementMgr = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        Collection<CaseManagementNote> notes = caseManagementMgr.getActiveNotes(demographicNo, new String[]{String.valueOf(issueId)});
        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();

        if (notes.size() == 0) {
            return null;
        }
        if (filterByAppointment) {
            for (CaseManagementNote note : notes) {
                if (note.getAppointmentNo() == appointmentNo) {
                    filteredNotes.add(note);
                }
            }
            if (filteredNotes.size() == 0) {
                return null;
            }
        } else {
            filteredNotes.addAll(notes);
        }
        return filteredNotes.iterator().next();
    }

    public static String getCppAdditionalData(Long noteId, String issueCode, List<CaseManagementNoteExt> noteExts, CppPreferencesUIBean prefsBean) {
        if (prefsBean.getEnable() == null || !prefsBean.getEnable().equals("on")) {
            return new String();
        }
        String issueCodeArr[] = issueCode.split(";");
        StringBuilder sb = new StringBuilder();
        if (issueCodeArr[1].equals("SocHistory")) {
            if (prefsBean.getSocialHxStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getSocialHxResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("Reminders")) {
            if (prefsBean.getRemindersStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getRemindersResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("Concerns")) {
            if (prefsBean.getOngoingConcernsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getOngoingConcernsResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getOngoingConcernsProblemStatus().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Status:" + getNoteExt(noteId, "Problem Status", noteExts));
            }
        }
        if (issueCodeArr[1].equals("MedHistory")) {
            if (prefsBean.getMedHxStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getMedHxResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getMedHxProcedureDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Procedure Date:" + getNoteExt(noteId, "Procedure Date", noteExts));
            }
            if (prefsBean.getMedHxTreatment().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Treatment:" + getNoteExt(noteId, "Treatment", noteExts));
            }
        }
        if (issueCodeArr[1].equals("RiskFactors")) {
            if (prefsBean.getRiskFactorsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getRiskFactorsResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("OMeds")) {
            if (prefsBean.getOtherMedsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getOtherMedsResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("FamHistory")) {
            if (prefsBean.getFamilyHistoryStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getFamilyHistoryResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getFamilyHistoryTreatment().equals("on")) {
                sb.append("Treatment:" + getNoteExt(noteId, "Treatment", noteExts));
            }
            if (prefsBean.getFamilyHistoryRelationship().equals("on")) {
                sb.append("Relationship:" + getNoteExt(noteId, "Relationship", noteExts));
            }
        }
        if (sb.length() > 0) {
            sb.insert(0, " (");
            sb.append(")");
        }
        return sb.toString();
    }

    static String getNoteExt(Long noteId, String key, List<CaseManagementNoteExt> lcme) {
        for (CaseManagementNoteExt cme : lcme) {
            if (cme.getNoteId().equals(noteId) && cme.getKeyVal().equals(key)) {
                String val = null;

                if (key.contains(" Date")) {
                    val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy-MM-dd");
                } else {
                    val = StringEscapeUtils.escapeEcmaScript(cme.getValue());
                }
                return val;
            }
        }
        return "";
    }

    public String viewNotesOpt() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        HttpSession se = request.getSession();
        if (se.getAttribute("userrole") == null) {
            return "expired";
        }

        String demoNo = getDemographicNo(request);

        logger.debug("is client in program");
        // need to check to see if the client is in our program domain
        // if not...don't show this screen!
        if (!caseManagementMgr.isClientInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo) && !caseManagementMgr.isClientReferredInProgramDomain(loggedInInfo.getLoggedInProviderNo(), demoNo)) {
            return "domain-error";
        }
        String programId = (String) request.getSession().getAttribute("case_program_id");

        NoteSelectionCriteria criteria = new NoteSelectionCriteria();
        if (request.getParameter("numToReturn") != null && request.getParameter("numToReturn").length() > 0) {
            criteria.setMaxResults(ConversionUtils.fromIntString(request.getParameter("numToReturn")));
        }

        String offset = request.getParameter("offset");
        if (offset != null && !offset.trim().equals("0")) {
            // in case offset is configured, make sure it's set on the criteria...
            criteria.setFirstResult(ConversionUtils.fromIntString(offset));
        }
        criteria.setDemographicId(ConversionUtils.fromIntString(demoNo));
        criteria.setUserRole((String) request.getSession().getAttribute("userrole"));
        criteria.setUserName((String) request.getSession().getAttribute("user"));

        getParamOrSession("note_sort")
            .ifPresent(criteria::setNoteSort);

        getParamsOrSession("filter_roles").ifPresent(rs -> {
            criteria.getRoles().addAll(rs);
            se.setAttribute("CaseManagementViewAction_filter_roles", rs);
        });
        getParamsOrSession("filter_provider").ifPresent(rs -> {
            criteria.getProviders().addAll(rs);
            se.setAttribute("CaseManagementViewAction_filter_providers", rs);
        });
        getParamsOrSession("issues").ifPresent(rs -> {
            criteria.getIssues().addAll(rs);
            se.setAttribute("CaseManagementViewAction_filter_issues", rs);
        });

        // Only set programId if it's valid (not null, not empty, not "0")
        if (programId != null && !programId.trim().isEmpty() && !programId.equals("0")) {
            criteria.setProgramId(programId);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SEARCHING FOR NOTES WITH CRITERIA: " + criteria);
        }

        NoteSelectionResult result = noteService.findNotes(loggedInInfo, criteria);

        if (logger.isDebugEnabled()) {
            logger.debug("FOUND: " + result);
            for (NoteDisplay nd : result.getNotes()) {
                logger.debug("   " + nd.getClass().getSimpleName() + " " + nd.getNoteId() + " " + nd.getNote());
            }
        }

        if (result.isMoreNotes()) {
            request.setAttribute("moreNotes", true);
        }
        request.setAttribute("notesToDisplay", result.getNotes());
        return "ajaxDisplayNotes";
    }

    // Gets the parameters or attributes that are connected to the inputted name
    // Works for string outputs
    private Optional<String> getParamOrSession(String name) {
        HttpSession session = request.getSession();
        String val = request.getParameter(name);
        if (val == null) {
            val = (String) session.getAttribute(name);
            session.removeAttribute(name);
        }
        return Optional.ofNullable(val).filter(s -> !s.isEmpty());
    }

    // Gets the parameters or attributes that are connected to the inputted name
    // Works for Array and List string outputs
    private Optional<List<String>> getParamsOrSession(String name) {
        HttpSession session = request.getSession();
        String[] vals = request.getParameterValues(name);
        if (vals == null) {
            Object o = session.getAttribute(name);
            session.removeAttribute(name);
            if      (o instanceof String[]) vals = (String[]) o;
            else if (o instanceof String)   vals = new String[]{(String)o};
        }
        return Optional.ofNullable(vals).map(Arrays::asList);
    }

    private String demographicNo;
    private String providerNo;
    private String issues[];
    private String note_view = "summary";
    private String prescipt_view = "current";
    private String tab;
    private String vlCountry = "";
    private String rootCompURL = "";
    private String hideActiveIssue = "true";

    private CaseManagementCPP cpp = new CaseManagementCPP();
    private EncounterWindow ectWin = new EncounterWindow();
    public static final String[] tabs = {"Current Issues", "Client History", "Allergies", "Prescriptions", "Reminders", "Ticklers", "Search"};
    private File imageFile;

    private String searchStartDate;
    private String searchEndDate;
    private int searchRoleId;
    private String searchProviderNo;
    private int searchProgramId;
    private String searchText;
    private String searchEncounterType;
    private String note_sort = null;
    private String filter_provider = "";
    private String filter_providers[];
    private String filter_roles[];
    private long formId;
    private int noteId;
    private String password;

    public EncounterWindow getEctWin() {
        return this.ectWin;
    }

    public void setEctWin(EncounterWindow ectWin) {
        this.ectWin = ectWin;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNote_sort() {
        return note_sort;
    }

    public void setNote_sort(String note_sort) {
        this.note_sort = note_sort;
    }

    public File getImageFile() {
        return imageFile;
    }

    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }

    public CaseManagementCPP getCpp() {
        return this.cpp;
    }

    public void setCpp(CaseManagementCPP cpp) {
        this.cpp = cpp;
    }

    public String getVlCountry() {
        return vlCountry;
    }

    public void setVlCountry(String vlCountry) {
        this.vlCountry = vlCountry;
    }

    public String getRootCompURL() {
        return rootCompURL;
    }

    public void setRootCompURL(String rootCompURL) {
        this.rootCompURL = rootCompURL;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String[] getIssues() {
        return issues;
    }

    public void setIssues(String[] issues) {
        this.issues = issues;
    }

    public String getNote_view() {
        return note_view;
    }

    public void setNote_view(String note_view) {
        this.note_view = note_view;
    }

    public String getTab() {
        return tab;
    }

    public void setTab(String tab) {
        this.tab = tab;
    }

    public String getHideActiveIssue() {
        return hideActiveIssue;
    }

    public void setHideActiveIssue(String hideActiveIssue) {
        this.hideActiveIssue = hideActiveIssue;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getPrescipt_view() {
        return prescipt_view;
    }

    public void setPrescipt_view(String prescipt_view) {
        this.prescipt_view = prescipt_view;
    }

    public String getSearchEncounterType() {
        return searchEncounterType;
    }

    public String getSearchEndDate() {
        return searchEndDate;
    }

    public int getSearchProgramId() {
        return searchProgramId;
    }

    public String getSearchProviderNo() {
        return searchProviderNo;
    }

    public int getSearchRoleId() {
        return searchRoleId;
    }

    public String getSearchStartDate() {
        return searchStartDate;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchEncounterType(String searchEncounterType) {
        this.searchEncounterType = searchEncounterType;
    }

    public void setSearchEndDate(String searchEndDate) {
        this.searchEndDate = searchEndDate;
    }

    public void setSearchProgramId(int searchProgramId) {
        this.searchProgramId = searchProgramId;
    }

    public void setSearchProviderNo(String searchProviderNo) {
        this.searchProviderNo = searchProviderNo;
    }

    public void setSearchRoleId(int searchRoleId) {
        this.searchRoleId = searchRoleId;
    }

    public void setSearchStartDate(String searchStartDate) {
        this.searchStartDate = searchStartDate;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public int getNoteId() {
        return noteId;
    }

    public void setNoteId(int noteId) {
        this.noteId = noteId;
    }

    public String[] getFilter_providers() {
        return this.filter_providers;
    }

    public void setFilter_providers(String[] provs) {
        this.filter_providers = provs;
    }

    public String getFilter_provider() {
        return filter_provider;
    }

    public void setFilter_provider(String filter_provider) {
        this.filter_provider = filter_provider;
    }

    public String[] getFilter_roles() {
        return filter_roles;
    }

    public void setFilter_roles(String[] filter_roles) {
        this.filter_roles = filter_roles;
    }

    public long getFormId() {
        return formId;
    }

    public void setFormId(long formId) {
        this.formId = formId;
    }
    public String getDemoName(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoName(demoNo);
    }

    public String getDemoAge(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoAge(demoNo);
    }

    public String getDemoDOB(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoDOB(demoNo);
    }
    public String getDemographicNo(HttpServletRequest request) {
        String demono = request.getParameter("demographicNo");
        if (demono == null || "".equals(demono))
            demono = (String) request.getSession().getAttribute("casemgmt_DemoNo");
        else
            request.getSession().setAttribute("casemgmt_DemoNo", demono);
        return demono;
    }

    public String getProviderNo(HttpServletRequest request) {
        String providerNo = request.getParameter("providerNo");
        if (providerNo == null)
            providerNo = (String) request.getSession().getAttribute("user");
        return providerNo;
    }
}
