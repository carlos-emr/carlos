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

import ca.openosp.openo.casemgmt.dao.*;
import ca.openosp.openo.casemgmt.model.*;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.utility.*;
import com.opensymphony.xwork2.ActionSupport;
import ca.openosp.openo.model.security.Secrole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.SessionAware;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.service.AdmissionManager;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.PMmodule.service.ProviderManager;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.casemgmt.service.CaseManagementPrint;
import ca.openosp.openo.casemgmt.service.ClientImageManager;
import ca.openosp.openo.casemgmt.web.CaseManagementViewAction.IssueDisplay;
import ca.openosp.openo.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import ca.openosp.openo.managers.TicklerManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.appt.ApptStatusData;
import ca.openosp.openo.form.JSONUtil;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;
import ca.openosp.openo.util.UtilDateUtilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class CaseManagementEntry2Action extends ActionSupport implements SessionAware {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    private static Logger logger = MiscUtils.getLogger();

    private CaseManagementNoteDAO caseManagementNoteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
    private CaseManagementIssueDAO caseManagementIssueDao = (CaseManagementIssueDAO) SpringUtils.getBean(CaseManagementIssueDAO.class);
    private CaseManagementNoteExtDAO caseManagementNoteExtDao = (CaseManagementNoteExtDAO) SpringUtils.getBean(CaseManagementNoteExtDAO.class);
    private IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
    private CasemgmtNoteLockDao casemgmtNoteLockDao = SpringUtils.getBean(CasemgmtNoteLockDao.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            String message = "Illegal operation! Empty user session. LoggedInInfo " + loggedInInfo + " request " + request.getQueryString();
            logger.error(message);
            return null;
        }

        restoreFromSession();

        String method = request.getParameter("method") != null ? request.getParameter("method") : (String) request.getAttribute("method");
        
        if ("setUpMainEncounter".equals(method)) {
            return setUpMainEncounter();
        } else if ("isNoteEdited".equals(method)) {
            return isNoteEdited();
        } else if ("updateNoteLock".equals(method)) {
            return updateNoteLock();
        } else if ("issueNoteSaveJson".equals(method)) {
            return issueNoteSaveJson();
        } else if ("issueNoteSave".equals(method)) {
            return issueNoteSave();
        } else if ("save".equals(method)) {
            return save();
        } else if ("ajaxsave".equals(method)) {
            return ajaxsave();
        } else if ("releaseNoteLock".equals(method)) {
            return releaseNoteLock();
        } else if ("saveAndExit".equals(method)) {
            return saveAndExit();
        } else if ("cancel".equals(method)) {
            return cancel();
        } else if ("exit".equals(method)) {
            return exit();
        } else if ("addNewIssue".equals(method)) {
            return addNewIssue();
        } else if ("issueList".equals(method)) {
            return issueList();
        } else if ("issueSearch".equals(method)) {
            return issueSearch();
        } else if ("makeIssue".equals(method)) {
            return makeIssue();
        } else if ("issueAdd".equals(method)) {
            return issueAdd();
        } else if ("changeDiagnosis".equals(method)) {
            return changeDiagnosis();
        } else if ("submitChangeDiagnosis".equals(method)) {
            return submitChangeDiagnosis();
        } else if ("ajaxChangeDiagnosis".equals(method)) {
            return ajaxChangeDiagnosis();
        } else if ("issueDelete".equals(method)) {
            return issueDelete();
        } else if ("issueChange".equals(method)) {
            return issueChange();
        } else if ("notehistory".equals(method)) {
            return notehistory();
        } else if ("issuehistory".equals(method)) {
            return issuehistory();
        } else if ("history".equals(method)) {
            return history();
        } else if ("autosave".equals(method)) {
            return autosave();
        } else if ("restore".equals(method)) {
            return restore();
        } else if ("cleanup".equals(method)) {
            return cleanup();
        } else if ("displayNotes".equals(method)) {
            return displayNotes();
        } else if ("print".equals(method)) {
            return print();
        } else if ("ticklerSaveNote".equals(method)) {
            return ticklerSaveNote();
        } else if ("ticklerGetNote".equals(method)) {
            return ticklerGetNote();
        }

        // Defaulting to edit method
        return edit();
    }

    public String setUpMainEncounter() {
        String demono = getDemographicNo(request);
        logger.debug("client Image?");

        //get client image
        ClientImage img = clientImageMgr.getClientImage(Integer.parseInt(demono));
        if (img != null) {
            request.setAttribute("image_exists", "true");
            request.setAttribute("demographicNo", demono);
        }
        return "setUpMainEncounterPage";
    }

    public String edit() throws Exception {
        logger.debug("Edit Starts");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        long start = System.currentTimeMillis();
        long beginning = start;
        long current = 0;
        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        CaseManagementEntryFormBean cform = new CaseManagementEntryFormBean();
        cform.setChain("");
        request.setAttribute("change_flag", "false");
        request.setAttribute("from", "casemgmt");

        logger.debug("Get demo and providers no");
        String demono = getDemographicNo(request);
        Integer demographicNo = Integer.parseInt(demono);
        current = System.currentTimeMillis();
        logger.debug("Get demo and providers no " + String.valueOf(current - start));
        start = current;

        String programIdString = (String) session.getAttribute("case_program_id");
        Integer programId = null;
        try {
            programId = Integer.parseInt(programIdString);
        } catch (Exception e) {
            logger.warn("Error parsing programId:" + programIdString, e);
        }

        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        /* process the request from other module */
        if (!"casemgmt".equalsIgnoreCase(request.getParameter("from"))) {

            // no demographic number, no page
            if (request.getParameter("demographicNo") == null || "".equals(request.getParameter("demographicNo"))) {
                return "NoDemoERR";
            }
            request.setAttribute("from", "");
        }

        /* prepare url for billing */
        if (request.getParameter("from") != null) {
            request.setAttribute("from", request.getParameter("from"));
        }

        String url = "";
        if ("casemgmt".equals(request.getAttribute("from"))) {

            String province = OscarProperties.getInstance().getProperty("billregion", "").trim().toUpperCase();

            EctSessionBean bean = (EctSessionBean) session.getAttribute("EctSessionBean");

            if (bean.appointmentNo == null) {
                bean.appointmentNo = "0";
            }
            String bsurl = (String) session.getAttribute("casemgmt_oscar_baseurl");
            Date today = new Date();
            Calendar todayCal = Calendar.getInstance();
            todayCal.setTime(today);

            String Hour = Integer.toString(todayCal.get(Calendar.HOUR));
            String Min = Integer.toString(todayCal.get(Calendar.MINUTE));

            String default_view = OscarProperties.getInstance().getProperty("default_view", "");
            String contextPath = request.getContextPath();

            url = bsurl + contextPath + "/billing.do?billRegion=" + java.net.URLEncoder.encode(province, "UTF-8") + "&billForm=" + java.net.URLEncoder.encode(default_view, "UTF-8") + "&hotclick=" + java.net.URLEncoder.encode("", "UTF-8") + "&appointment_no=" + bean.appointmentNo + "&appointment_date=" + bean.appointmentDate + "&start_time=" + Hour + ":" + Min + "&demographic_name=" + java.net.URLEncoder.encode(bean.patientLastName + "," + bean.patientFirstName, "UTF-8") + "&demographic_no=" + bean.demographicNo
                    + "&providerview=" + bean.curProviderNo + "&user_no=" + bean.providerNo + "&apptProvider_no=" + bean.curProviderNo + "&bNewForm=1&status=t";

            session.setAttribute("billing_url", url);
        }

        /* remove the remembered echart string */
        session.removeAttribute("lastSavedNoteString");

        logger.debug("Get Issues and filter them");
        current = System.currentTimeMillis();
        logger.debug("Get Issues and filter them " + String.valueOf(current - start));
        start = current;

        cform.setDemoNo(demono);
        CaseManagementNote note = null;

        String nId = request.getParameter("noteId");
        String forceNote = request.getParameter("forceNote");
        if (forceNote == null) forceNote = "false";

        logger.debug("NoteId " + nId);

        String maxTmpSave = OscarProperties.getInstance().getProperty("maxTmpSave", "off");
        logger.debug("maxTmpSave " + maxTmpSave);
        // set date 2 weeks in past so we retrieve more recent saved notes
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -14);
        Date twoWeeksAgo = cal.getTime();
        logger.debug("Get tmp note");
        CaseManagementTmpSave tmpsavenote;
        if (maxTmpSave.equalsIgnoreCase("off")) {
            tmpsavenote = this.caseManagementMgr.restoreTmpSave(providerNo, demono, programIdString);
        } else {
            tmpsavenote = this.caseManagementMgr.restoreTmpSave(providerNo, demono, programIdString, twoWeeksAgo);
        }
        current = System.currentTimeMillis();
        logger.debug("Get tmp note " + String.valueOf(current - start));
        start = current;

        logger.debug("Get Note for editing");

        // create a new note
        if (request.getParameter("note_edit") != null && request.getParameter("note_edit").equals("new")) {
            logger.debug("NEW NOTE GENERATED");
            session.setAttribute("newNote", "true");
            session.setAttribute("issueStatusChanged", "false");
            request.setAttribute("newNoteIdx", request.getParameter("newNoteIdx"));

            note = new CaseManagementNote();
            note.setProviderNo(providerNo);
            Provider prov = new Provider();
            prov.setProviderNo(providerNo);
            note.setProvider(prov);
            note.setDemographic_no(demono);

            if (!OscarProperties.getInstance().isPropertyActive("encounter.empty_new_note")) {
                this.insertReason(request, note);
            } else {
                note.setNote("");
                note.setEncounter_type("");
            }

            EctSessionBean bean = (EctSessionBean) session.getAttribute("EctSessionBean");
            String encType = request.getParameter("encType");

            if (encType == null || encType.equals("")) {
                note.setEncounter_type("");
            } else {
                note.setEncounter_type(encType);
            }
            if (bean.encType != null && bean.encType.length() > 0) {
                note.setEncounter_type(bean.encType);
            }

            resetTemp(providerNo, demono, programIdString);

        }
        // get the last temp note?
        else if (tmpsavenote != null && !forceNote.equals("true")) {
            logger.debug("tempsavenote is NOT NULL");
            if (tmpsavenote.getNoteId() > 0) {
                session.setAttribute("newNote", "false");
                request.setAttribute("noteId", String.valueOf(tmpsavenote.getNoteId()));
                note = caseManagementMgr.getNote(String.valueOf(tmpsavenote.getNoteId()));
                logger.debug("Restoring " + String.valueOf(note.getId()));
            } else {
                session.setAttribute("newNote", "true");
                session.setAttribute("issueStatusChanged", "false");
                note = new CaseManagementNote();
                note.setProviderNo(providerNo);
                Provider prov = new Provider();
                prov.setProviderNo(providerNo);
                note.setProvider(prov);
                note.setDemographic_no(demono);
            }

            note.setNote(tmpsavenote.getNote());
            logger.debug("Setting note to " + note.getNote());

        }
        // get an existing non-temp note?
        else if (nId != null && !"null".equalsIgnoreCase(nId) && Integer.parseInt(nId) > 0) {
            logger.debug("Using nId " + nId + " to fetch note");
            session.setAttribute("newNote", "false");
            note = caseManagementMgr.getNote(nId);

            if (note.getHistory() == null || note.getHistory().equals("")) {
                // old note - we need to save the original in here
                note.setHistory(note.getNote());

                caseManagementMgr.saveNoteSimple(note);
                caseManagementMgr.addNewNoteLink(Long.parseLong(nId));
            }

        }
        // no note specified, get last unsigned
        else {
            // A hack to load last unsigned note when not specifying a particular note to edit
            // if there is no unsigned note load a new one
            if ((note = getLastSaved(request, demono, providerNo)) == null) {
                session.setAttribute("newNote", "true");
                session.setAttribute("issueStatusChanged", "false");
                note = this.makeNewNote(providerNo, demono, request);
            } else {
                session.setAttribute("newNote", "false"); // should be able to get getLatSaved from the manager now
            }
        }
        current = System.currentTimeMillis();
        logger.debug("Get note to edit " + String.valueOf(current - start));
        start = current;

        /*
         * do the restore if(restore != null && restore.booleanValue() == true) { String tmpsavenote = this.caseManagementMgr.restoreTmpSave(providerNo,demono,programId); if(tmpsavenote != null) { note.setNote(tmpsavenote); } }
         */
        logger.debug("Set Encounter Type: " + note.getEncounter_type());
        logger.debug("Fetched Note " + String.valueOf(note.getId()));

        logger.debug("Populate Note with editors");
        this.caseManagementMgr.getEditors(note);
        current = System.currentTimeMillis();
        logger.debug("Populate Note with editors " + String.valueOf(current - start));
        start = current;

        // put the new/retrieved not in the form object for rendering on page
        cform.setCaseNote(note);
        logger.debug("note in cform " + cform.getCaseNote_note());
        /* set issue checked list */

        // get issues for current demographic, based on providers rights
        Boolean useNewCaseMgmt = Boolean.valueOf((String) session.getAttribute("newCaseManagement"));

                List<CheckBoxBean> checkedList = null;
        if (useNewCaseMgmt) {

            CaseManagementView2Action caseManagementViewAction = new CaseManagementView2Action();
            ArrayList<CheckBoxBean> checkBoxBeanList = new ArrayList<CheckBoxBean>();
            caseManagementViewAction.addLocalIssues(providerNo, checkBoxBeanList, demographicNo, false, programId);
            caseManagementViewAction.addRemoteIssues(loggedInInfo, checkBoxBeanList, demographicNo, false);

            caseManagementViewAction.sortIssuesByOrderId(checkBoxBeanList);

                        checkedList = checkBoxBeanList;
            Iterator itr = note.getIssues().iterator();
            while (itr.hasNext()) {
                int id = ((CaseManagementIssue) itr.next()).getId().intValue();
                SetChecked(checkedList, id);
            }

        } else // old CME
        {
            CaseManagementView2Action caseManagementViewAction = new CaseManagementView2Action();
            ArrayList<CheckBoxBean> checkBoxBeanList = new ArrayList<CheckBoxBean>();
            caseManagementViewAction.addLocalIssues(providerNo, checkBoxBeanList, demographicNo, false, programId);
            caseManagementViewAction.addRemoteIssues(loggedInInfo, checkBoxBeanList, demographicNo, false);
            caseManagementViewAction.addGroupIssues(loggedInInfo, checkBoxBeanList, demographicNo, false);

                        checkedList = checkBoxBeanList;

            for (CaseManagementIssue cmi : note.getIssues()) {
                setChecked_oldCme(checkedList, cmi);
            }
        }

        current = System.currentTimeMillis();

        cform.setIssueCheckList(checkedList);

        cform.setSign("off");
        if (!note.isIncludeissue()) cform.setIncludeIssue("off");
        else cform.setIncludeIssue("on");

        boolean passwd = caseManagementMgr.getEnabled();
        String chain = request.getParameter("chain");

        current = System.currentTimeMillis();
        logger.debug("The End of Edit " + String.valueOf(current - beginning));
        start = current;

        LogAction.addLog((String) session.getAttribute("user"), LogConst.EDIT, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demono, note.getAuditString());

        //check to see if someone else is editing note in this chart
        String ipAddress = request.getRemoteAddr();
        CasemgmtNoteLock casemgmtNoteLock;
        Long note_id = note.getId() != null && note.getId() >= 0 ? note.getId() : 0L;
        casemgmtNoteLock = isNoteEdited(note_id, demographicNo, providerNo, ipAddress, request.getRequestedSessionId());

        if (casemgmtNoteLock.isLocked()) {
            note = makeNewNote(providerNo, demono, request);
            cform.setCaseNote(note);
        }

        session.setAttribute("casemgmtNoteLock" + demono, casemgmtNoteLock);

        String frmName = "caseManagementEntryForm" + demono;
        logger.debug("note in cform " + cform.getCaseNote_note());
        mySessionMap.put(frmName, cform);

        String fwd, finalFwd = null;
        if (chain != null && chain.length() > 0) {
            session.setAttribute("passwordEnabled", passwd);
            fwd = chain;
        } else {
            request.setAttribute("passwordEnabled", passwd);

            String ajax = request.getParameter("ajax");
            if (ajax != null && ajax.equalsIgnoreCase("true")) {
                fwd = "issueList_ajax";
            } else {
                fwd = "view";
            }
        }

        setOrRemove(session, "note_sort", request.getParameter("note_sort"));
        setOrRemove(session, "filter_roles", request.getParameterValues("filter_roles"));
        setOrRemove(session, "filter_provider", request.getParameterValues("filter_providers"));
        setOrRemove(session, "issues", request.getParameterValues("issues"));

        return fwd;
    }

    private void setOrRemove(HttpSession session, String key, String value) {
        if (value != null) {
            session.setAttribute(key, value);
        }
    }

    private void setOrRemove(HttpSession session, String key, String[] values) {
        if (values == null) {
            return;
        } else if (values.length > 0) {
            session.setAttribute(key, values);
        } else {
            session.removeAttribute(key);
        }
    }

    private CaseManagementNote makeNewNote(String providerNo, String demographicNo, HttpServletRequest request) {
        CaseManagementNote note = new CaseManagementNote();
        note.setProviderNo(providerNo);
        Provider prov = new Provider();
        prov.setProviderNo(providerNo);
        note.setProvider(prov);
        note.setDemographic_no(demographicNo);

        if (!OscarProperties.getInstance().isPropertyActive("encounter.empty_new_note")) {
            this.insertReason(request, note);
        } else {
            note.setNote("");
            note.setEncounter_type("");
        }
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        String encType = request.getParameter("encType");

        if (encType == null || encType.equals("")) {
            note.setEncounter_type("");
        } else {
            note.setEncounter_type(encType);
        }
        if (bean.encType != null && bean.encType.length() > 0) {
            note.setEncounter_type(bean.encType);
        }
        return note;
    }

    private static synchronized CasemgmtNoteLock isNoteEdited(Long note_id, Integer demographicNo, String providerNo, String ipAddress, String sessionId) {
        CasemgmtNoteLockDao casemgmtNoteLockDao = SpringUtils.getBean(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock casemgmtNoteLock = casemgmtNoteLockDao.findByNoteDemo(demographicNo, note_id);

        //We determine the lock status of the note
        if (casemgmtNoteLock != null) {
            //it has a lock; check if lock is same user
            if (casemgmtNoteLock.getProviderNo().equals(providerNo)) {
                //Same user has this note open elsewhere
                casemgmtNoteLock.setLockedBySameUser(true);
            } else if (note_id != 0) {
                //Another user is editing same note
                casemgmtNoteLock.setLocked(true);
            } else if (note_id == 0) {
                logger.debug("STATIC isNoteEdited CREATING LOCK NOTE ID 0 DEMO: " + demographicNo + " PROVIDER: " + providerNo);
                casemgmtNoteLock = new CasemgmtNoteLock();
                casemgmtNoteLock.setDemographicNo(demographicNo);
                casemgmtNoteLock.setIpAddress(ipAddress);
                casemgmtNoteLock.setNoteId(note_id);
                casemgmtNoteLock.setProviderNo(providerNo);
                casemgmtNoteLock.setSessionId(sessionId);
                casemgmtNoteLock.setLockAcquired(new Date());
                casemgmtNoteLockDao.persist(casemgmtNoteLock);
            }
        } else {
            logger.debug("STATIC isNoteEdited CREATING NEW LOCK DEMO: " + demographicNo + " PROVIDER: " + providerNo);
            casemgmtNoteLock = new CasemgmtNoteLock();
            casemgmtNoteLock.setDemographicNo(demographicNo);
            casemgmtNoteLock.setIpAddress(ipAddress);
            casemgmtNoteLock.setNoteId(note_id);
            casemgmtNoteLock.setProviderNo(providerNo);
            casemgmtNoteLock.setSessionId(sessionId);
            casemgmtNoteLock.setLockAcquired(new Date());
            casemgmtNoteLockDao.persist(casemgmtNoteLock);
        }

        return casemgmtNoteLock;
    }

    public String isNoteEdited() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        String ipAddress = request.getRemoteAddr();
        String sessionId = request.getRequestedSessionId();

        logger.debug("WEB isNoteEdited CALLED");
        CasemgmtNoteLock casemgmtNoteLock = isNoteEdited(Long.parseLong(noteId), Integer.parseInt(demoNo), providerNo, ipAddress, sessionId);

        String ret = "unlocked";
        if (casemgmtNoteLock.isLocked()) {
            ret = "other";
        } else if (casemgmtNoteLock.isLockedBySameUser()) {
            ret = "user";
        } else {
            request.getSession().setAttribute("casemgmtNoteLock" + demoNo, casemgmtNoteLock);
        }

        Map<String, String> jsonMap = new HashMap<String, String>();
        jsonMap.put("isNoteEdited", ret);
        ObjectNode json = objectMapper.valueToTree(jsonMap);
        response.getOutputStream().write(json.toString().getBytes());
        return null;
    }

    //Change IP Address and Session Id of note lock
    public String updateNoteLock() {
        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        HttpSession session = request.getSession();

        CasemgmtNoteLock casemgmtNoteLock = null;
        if (noteId != null && !"null".equalsIgnoreCase(noteId)) {
            casemgmtNoteLock = casemgmtNoteLockDao.findByNoteDemo(Integer.parseInt(demoNo), Long.parseLong(noteId));
        } else {
            casemgmtNoteLock = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demoNo);
        }

        casemgmtNoteLock.setIpAddress(request.getRemoteAddr());
        casemgmtNoteLock.setSessionId(request.getRequestedSessionId());
        logger.debug("UPDATING LOCK DEMO " + demoNo + " SESSION " + casemgmtNoteLock.getSessionId() + " LOCK IP " + casemgmtNoteLock.getIpAddress());
        casemgmtNoteLockDao.merge(casemgmtNoteLock);

        session.setAttribute("casemgmtNoteLock" + demoNo, casemgmtNoteLock);

        return null;

    }

    private void setChecked_oldCme(List<CheckBoxBean> checkedList, CaseManagementIssue cmi) {
        for (CheckBoxBean cbb : checkedList) {
            if (cbb.getIssueDisplay().code.equals(cmi.getIssue().getCode())) {
                cbb.setChecked("on");
                return;
            }
        }
    }

    public void resetTemp(String providerNo, String demoNo, String programId) {
        try {
            this.caseManagementMgr.deleteTmpSave(providerNo, demoNo, programId);
        } catch (Exception e) {
            logger.warn("Warning", e);
        }
    }

    public String issueNoteSaveJson() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String strNote = request.getParameter("value");
        String appointmentNo = request.getParameter("appointment_no");
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String noteId = request.getParameter("noteId");
        String demographicNo = request.getParameter("demographic_no");
        String issueCode = request.getParameter("issue_id");
        String issueAlphaCode = request.getParameter("issue_code");
        String archived = request.getParameter("archived");

        Date noteDate = new Date();

        strNote = StringUtils.trimToNull(strNote);
        if ((archived == null || !archived.equalsIgnoreCase("true")) && (strNote == null || strNote.equals("")))
            return null;

        CaseManagementNote note = new CaseManagementNote();
        if (!noteId.equals("0")) {
            note = this.caseManagementMgr.getNote(noteId);
            if ((archived == null || !archived.equalsIgnoreCase("true")) && (request.getParameter("sign") == null || !request.getParameter("sign").equalsIgnoreCase("true")) && note.getNote().equalsIgnoreCase(strNote))
                return null;

            note.setRevision(Integer.parseInt(note.getRevision()) + 1 + "");

            if (archived != null && archived.equalsIgnoreCase("true")) note.setArchived(true);

        } else {
            note.setDemographic_no(demographicNo);

            CaseManagementIssue cIssue;
            if (issueAlphaCode != null && issueAlphaCode.length() > 0)
                cIssue = this.caseManagementMgr.getIssueByIssueCode(demographicNo, issueAlphaCode);
            else cIssue = this.caseManagementMgr.getIssueById(demographicNo, issueCode);

            Set<CaseManagementIssue> issueSet = new HashSet<CaseManagementIssue>();
            Set<CaseManagementNote> noteSet = new HashSet<CaseManagementNote>();

            if (cIssue == null) {
                Issue issue;
                if (issueAlphaCode != null && issueAlphaCode.length() > 0)
                    issue = this.caseManagementMgr.getIssueByCode(issueAlphaCode);
                else issue = this.caseManagementMgr.getIssue(issueCode);

                cIssue = this.newIssueToCIssue(demographicNo, issue, Integer.parseInt("10016"));
                cIssue.setNotes(noteSet);
            }

            issueSet.add(cIssue);
            note.setIssues(issueSet);
            note.setCreate_date(noteDate);
            note.setObservation_date(noteDate);
            note.setRevision("1");

        }

        try {
            note.setAppointmentNo(Integer.parseInt(appointmentNo));
        } catch (Exception e) {
            // No appointment number set for this encounter
        }

        if (strNote != null) note.setNote(strNote);

        note.setProviderNo(providerNo);
        note.setProvider(loggedInInfo.getLoggedInProvider());

        if (request.getParameter("sign") != null && request.getParameter("sign").equalsIgnoreCase("true")) {
            note.setSigning_provider_no(providerNo);
            note.setSigned(true);
            if (request.getParameter("appendSignText") != null && request.getParameter("appendSignText").equalsIgnoreCase("true")) {
                SimpleDateFormat dt = new SimpleDateFormat("dd-MMM-yyyy H:mm", Locale.ENGLISH);
                Date now = new Date();
                ResourceBundle props = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

                ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
                String providerName = providerDao.getProviderName(providerNo);

                String signature = "[" + props.getString("oscarEncounter.class.EctSaveEncounterAction.msgSigned") + " " + dt.format(now) + " " + props.getString("oscarEncounter.class.EctSaveEncounterAction.msgSigBy") + " " + providerName + "]";
                note.setNote(note.getNote() + "\n" + signature);
            }

            if (request.getParameter("signAndExit") != null && request.getParameter("signAndExit").equalsIgnoreCase("true")) {
                OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
                try {
                    Appointment appointment = appointmentDao.find(Integer.parseInt(appointmentNo));
                    if (appointment != null) {
                        ApptStatusData statusData = new ApptStatusData();
                        appointment.setStatus(statusData.signStatus());
                        appointmentDao.merge(appointment);
                    }
                } catch (Exception e) {
                    logger.error("Couldn't parse appointmentNo: " + appointmentNo, e);
                }
            }
        } else if (!note.isSigned() && (archived == null || !archived.equalsIgnoreCase("true"))) {
            note.setSigned(false);
            note.setSigning_provider_no("");
        }

        // Determines what program & role to assign the note to
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProviderDefaultProgramDao defaultProgramDao = (ProviderDefaultProgramDao) SpringUtils.getBean(ProviderDefaultProgramDao.class);
        boolean programSet = false;

        List<ProviderDefaultProgram> programs = defaultProgramDao.getProgramByProviderNo(providerNo);
        HashMap<Program, List<Secrole>> rolesForDemo = NotePermissions2Action.getAllProviderAccessibleRolesForDemo(providerNo, demographicNo);
        for (ProviderDefaultProgram pdp : programs) {
            for (Program p : rolesForDemo.keySet()) {
                if (pdp.getProgramId() == p.getId().intValue()) {
                    List<ProgramProvider> programProviderList = programProviderDao.getProgramProviderByProviderProgramId(providerNo, (long) pdp.getProgramId());

                    note.setProgram_no("" + pdp.getProgramId());
                    note.setReporter_caisi_role("" + programProviderList.get(0).getRoleId());

                    programSet = true;
                }
            }
        }

        if (!programSet && !rolesForDemo.isEmpty()) {
            Program program = rolesForDemo.keySet().iterator().next();
            ProgramProvider programProvider = programProviderDao.getProgramProvider(providerNo, (long) program.getId());
            note.setProgram_no("" + programProvider.getProgramId());
            note.setReporter_caisi_role("" + programProvider.getRoleId());
        }

        note.setReporter_program_team("0");

        CaseManagementCPP cpp = this.caseManagementMgr.getCPP(demographicNo);
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(demographicNo);
        }

        caseManagementMgr.saveNote(cpp, note, providerNo, null, null, null);
        caseManagementMgr.addNewNoteLink(note.getId());

        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("id", note.getId());
        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.getOutputStream().write(json.toString().getBytes());

        return null;
    }

    /*
     * value (note)
     * appointmentNo
     * demographicNo
     * noteId
     * issueChange
     * archived
     *
     * session form caseManagementEntryForm + demoNo
     *
     */
    public String issueNoteSave() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String strNote = request.getParameter("value");
        String appointmentNo = request.getParameter("appointmentNo");
        HttpSession session = request.getSession();

        String[] extNames = {"startdate", "resolutiondate", "proceduredate", "ageatonset", "problemstatus", "treatment", "exposuredetail", "relationship", "lifestage", "hidecpp", "problemdescription", "procedure"};
        String[] extKeys = {CaseManagementNoteExt.STARTDATE, CaseManagementNoteExt.RESOLUTIONDATE, CaseManagementNoteExt.PROCEDUREDATE, CaseManagementNoteExt.AGEATONSET, CaseManagementNoteExt.PROBLEMSTATUS, CaseManagementNoteExt.TREATMENT, CaseManagementNoteExt.EXPOSUREDETAIL, CaseManagementNoteExt.RELATIONSHIP, CaseManagementNoteExt.LIFESTAGE, CaseManagementNoteExt.HIDECPP, CaseManagementNoteExt.PROBLEMDESC, CaseManagementNoteExt.PROCEDURE};

        logger.debug("Saving: " + strNote);
        strNote = StringUtils.trimToNull(strNote);
        if (strNote == null || strNote.equals("")) return null;

        String userName = loggedInInfo.getLoggedInProvider().getFullName();

        String demo = getDemographicNo(request);

        String noteId = request.getParameter("noteId");
        logger.debug("SAVING NOTE " + noteId + " STRING: " + strNote);
        String issueChange = request.getParameter("issueChange");
        String archived = request.getParameter("archived");

        CaseManagementNote note;
        boolean newNote = false;
        // we don't want to try to remove an issue from a new note so we test here
        if (noteId.isEmpty()) noteId = "0";

        if (noteId.equals("0")) {
            note = new CaseManagementNote();
            note.setDemographic_no(demo);
            newNote = true;
        } else {
            boolean extChanged = false;
            List<CaseManagementNoteExt> cmeList = caseManagementNoteExtDao.getExtByNote(Long.valueOf(noteId));

            extNames:
            for (int i = 0; i < extNames.length; i++) {
                boolean extKeyMatched = false;

                String val = request.getParameter(extNames[i]);
                for (CaseManagementNoteExt cme : cmeList) {
                    if (!cme.getKeyVal().equals(extKeys[i])) continue;

                    if (i <= 2) {
                        if (!nullEmptyEqual(cme.getDateValueStr(), partialFullDate(val, partialDateFormat(val)))) {
                            extChanged = true;
                            break extNames;
                        }
                        val = partialDateFormat(val);
                    }
                    if (!nullEmptyEqual(cme.getValue(), val)) {
                        extChanged = true;
                        break extNames;
                    }
                    extKeyMatched = true;
                    break;
                }
                if (filled(val) && !extKeyMatched) { // new ext value(s) added
                    extChanged = true;
                    break extNames;
                }
            }

            // if note has not changed don't save
            note = this.caseManagementMgr.getNote(noteId);
            if (strNote.equals(note.getNote()) && !issueChange.equals("true") && !extChanged && (archived == null || archived.equalsIgnoreCase("false")))
                return null;
        }
        note.setNote(strNote);
        note.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        note.setSigning_provider_no(loggedInInfo.getLoggedInProviderNo());
        note.setSigned(true);
        note.setProvider(loggedInInfo.getLoggedInProvider());

        String logAction = new String();
        if (archived == null || archived.equalsIgnoreCase("false")) {
            note.setArchived(false);
        } else {
            note.setArchived(true);
            logger.debug("Setting archived to true");
            logAction = LogConst.ARCHIVE;
        }

        logger.debug("Note archived " + note.isArchived());
        String programId = (String) session.getAttribute("case_program_id");
        note.setProgram_no(programId);

        WebApplicationContext ctx = this.getSpringContext();

        ProgramManager programManager = (ProgramManager) ctx.getBean(ProgramManager.class);
        AdmissionManager admissionManager = (AdmissionManager) ctx.getBean(AdmissionManager.class);

        String role = null;
        String team = null;

        try {
            role = String.valueOf((programManager.getProgramProvider(note.getProviderNo(), note.getProgram_no())).getRole().getId());
        } catch (Exception e) {
            logger.error("Error", e);
            role = "0";
        }

        note.setReporter_caisi_role(role);

        try {
            team = String.valueOf((admissionManager.getAdmission(note.getProgram_no(), Integer.valueOf(note.getDemographic_no()))).getTeamId());
        } catch (Exception e) {
            logger.error("Error", e);
            team = "0";
        }
        note.setReporter_program_team(team);
        if (appointmentNo != null && appointmentNo.length() > 0) {
            try {
                note.setAppointmentNo(Integer.parseInt(appointmentNo));
            } catch (NumberFormatException e) {
                logger.debug("no appt no");
            }
        }
        // update note issues
        String sessionFrmName = "caseManagementEntryForm" + demo;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
        Set<CaseManagementIssue> issueSet = new HashSet<CaseManagementIssue>();
        Set<CaseManagementNote> noteSet = new HashSet<CaseManagementNote>();
        String[] issue_id = request.getParameterValues("issue_id");
        List<CheckBoxBean> existingCaseIssueList = sessionFrm.getIssueCheckList();
        ArrayList<CheckBoxBean> caseIssueList = new ArrayList<CheckBoxBean>();

        // copy existing issues for sessionfrm
        if (existingCaseIssueList != null) {
            caseIssueList.addAll(existingCaseIssueList);
        }

        // first we check if any notes have been removed
        Set<CaseManagementIssue> noteIssues = note.getIssues();
        Iterator<CaseManagementIssue> iter = noteIssues.iterator();
        CaseManagementIssue cIssue;
        boolean issueExists;
        StringBuilder issueNames = new StringBuilder();
        int j;

        // we need the defining issue as originally passed in
        String reloadQuery = request.getParameter("reloadUrl");
        String[] params = reloadQuery.split("&");
        String cppStrIssue = new String();
        for (int p = 0; p < params.length; ++p) {
            String[] keyVal = params[p].split("=");
            if (keyVal[0].equalsIgnoreCase("issue_code")) {
                cppStrIssue = keyVal[1];
                break;
            }
        }

        boolean removed = false;

        // we've removed all issues so record that
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (issue_id == null) {
            while (iter.hasNext()) {
                cIssue = iter.next();
                issueNames.append(cIssue.getIssue().getDescription() + "\n");
            }

            strNote += "\n" + new SimpleDateFormat("dd-MMM-yyyy", request.getLocale()).format(new Date()) + " " + props.getString("oscarEncounter.removedIssue.Msg") + ":\n" + issueNames.toString();
            note.setNote(strNote);
            removed = true;
        } else {
            // check to see if we have removed any issues
            while (iter.hasNext()) {
                cIssue = iter.next();
                issueExists = false;
                for (j = 0; j < issue_id.length; ++j) {
                    if (Long.parseLong(issue_id[j]) == cIssue.getIssue_id()) {
                        issueExists = true;
                        break;
                    }
                }

                if (!issueExists) {
                    issueNames.append(cIssue.getIssue().getDescription() + "\n");
                    if (cIssue.getIssue().getCode().equalsIgnoreCase(cppStrIssue)) {
                        removed = true;
                    }
                }
            }

            // if we have removed an issue add it to message body
            if (issueNames.length() > 0) {
                strNote += "\n" + new SimpleDateFormat("dd-MMM-yyyy", request.getLocale()).format(new Date()) + " " + props.getString("oscarEncounter.removedIssue.Msg") + ":\n" + issueNames.toString();
                note.setNote(strNote);
            }

            for (int idx = 0; idx < issue_id.length; ++idx) {
                cIssue = this.caseManagementMgr.getIssueById(demo, issue_id[idx]);
                if (cIssue == null) {
                    Issue issue = this.caseManagementMgr.getIssue(issue_id[idx]);
                    cIssue = this.newIssueToCIssue(demo, issue, Integer.parseInt(programId));
                    cIssue.setNotes(noteSet);

                    // we have a new issue so add it to sessionfrm list
                    CheckBoxBean checkbox = new CheckBoxBean();
                    checkbox.setIssue(cIssue);
                    checkbox.setChecked("off");
                    checkbox.setUsed(true);
                    caseIssueList.add(checkbox);
                }
                issueSet.add(cIssue);

            } // end for

            sessionFrm.setIssueCheckList(caseIssueList);

        }
        note.setIssues(issueSet);

        // now we can update the order of the notes if necessary
        // if the note has been removed or archived we move notes up in the order
        // else we check if position has changed and move the note down
        int position = -1;
        int newPos = Integer.parseInt(request.getParameter("position"));

        List<Issue> cppIssue = caseManagementMgr.getIssueInfoByCode(providerNo, cppStrIssue);
        List<CaseManagementNote> curCPPNotes = new ArrayList<CaseManagementNote>();
        if (cppIssue.size() > 0) {
            String[] strIssueId = {String.valueOf(cppIssue.get(0).getId())};
            curCPPNotes = this.caseManagementMgr.getActiveNotes(demo, strIssueId);
        }

        CaseManagementNote curNote;
        long nId = Long.parseLong(noteId);
        int numNotes = curCPPNotes.size();
        // Alas we have to cycle through to make sure an ordering has been set
        // this is for legacy data
        for (int idx = 1; idx < curCPPNotes.size(); ++idx) {
            curNote = curCPPNotes.get(idx);
            if (curNote.getPosition() == 0) {
                curNote.setPosition(idx);

                if (curNote.getId() == nId) {
                    note.setPosition(idx);
                }

                this.caseManagementMgr.updateNote(curNote);
            }
        }

        if (removed || note.isArchived()) {
            position = note.getPosition();
            for (CaseManagementNote c : curCPPNotes) {
                if (c.getId() == nId) {
                    continue;
                } else if (position < c.getPosition()) {
                    newPos = c.getPosition() - 1;
                    c.setPosition(newPos);
                    this.caseManagementMgr.updateNote(c);
                }
            }
        } else if ((newPos != note.getPosition() && !(newPos == numNotes && note.getPosition() == (numNotes - 1))) || newNote) {
            for (CaseManagementNote c : curCPPNotes) {
                if (c.getId() != nId) {
                    if (newNote && c.getPosition() >= newPos) {
                        position = c.getPosition() + 1;
                        c.setPosition(position);
                        this.caseManagementMgr.updateNote(c);
                    } else if ((!newNote && newPos < note.getPosition()) && c.getPosition() >= newPos && c.getPosition() < note.getPosition()) {
                        position = c.getPosition() + 1;
                        c.setPosition(position);
                        this.caseManagementMgr.updateNote(c);
                    } else if ((!newNote && newPos > note.getPosition()) && c.getPosition() <= newPos && c.getPosition() > note.getPosition()) {
                        position = c.getPosition() - 1;
                        c.setPosition(position);
                        this.caseManagementMgr.updateNote(c);
                    }

                }
            }

            if (newPos == numNotes && !newNote) {
                --newPos;
            }
            note.setPosition(newPos);
        }

        int revision;

        if (note.getRevision() != null) {
            revision = Integer.parseInt(note.getRevision());
            ++revision;
        } else revision = 1;

        note.setRevision(String.valueOf(revision));
        Date now = new Date();
        if (note.getObservation_date() == null) {
            note.setObservation_date(now);
        }

        note.setUpdate_date(now);
        if (note.getCreate_date() == null) note.setCreate_date(now);

        /* save note including add signature */
        String lastSavedNoteString = (String) session.getAttribute("lastSavedNoteString");
        String roleName = caseManagementMgr.getRoleName(providerNo, note.getProgram_no());
        CaseManagementCPP cpp = this.caseManagementMgr.getCPP(demo);
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(demo);
        }
        cpp = copyNote2cpp(cpp, note);
        String savedStr = caseManagementMgr.saveNote(cpp, note, providerNo, userName, lastSavedNoteString, roleName);
        caseManagementMgr.addNewNoteLink(note.getId());
        logger.debug("Saved note " + savedStr);
        caseManagementMgr.saveCPP(cpp, providerNo);
        /* remember the str written into echart */
        session.setAttribute("lastSavedNoteString", savedStr);

        /* save extra fields */
        CaseManagementNoteExt cme = new CaseManagementNoteExt();
        cme.setNoteId(note.getId());
        for (int i = 0; i < extNames.length; i++) {
            String val = request.getParameter(extNames[i]);
            if (filled(val)) {
                cme.setKeyVal(extKeys[i]);
                cme.setDateValue((Date) null);
                cme.setValue(null);
                if (i <= 2) {
                    if (writePartialDate(val, cme)) caseManagementMgr.saveNoteExt(cme);
                } else {
                    cme.setValue(val);
                    caseManagementMgr.saveNoteExt(cme);
                }
            }
        }

        /* Save annotation */
        String attrib_name = request.getParameter("annotation_attrib");
        CaseManagementNote cmn = (CaseManagementNote) session.getAttribute(attrib_name);
        if (cmn != null) {
            // new annotation created and got it in session attribute
            caseManagementMgr.saveNoteSimple(cmn);
            CaseManagementNoteLink cml = new CaseManagementNoteLink(CaseManagementNoteLink.CASEMGMTNOTE, note.getId(), cmn.getId());
            caseManagementMgr.saveNoteLink(cml);
            LogAction.addLog(providerNo, LogConst.ANNOTATE, LogConst.CON_CME_NOTE, String.valueOf(cmn.getId()), request.getRemoteAddr(), demo, cmn.getNote());
            session.removeAttribute(attrib_name);
        }
        if (!noteId.equals("0")) {
            // Not a new note, look for old annotation
            CaseManagementNoteLink cml_anno = null;
            CaseManagementNoteLink cml_dump = null;
            List<CaseManagementNoteLink> cmll = caseManagementMgr.getLinkByTableIdDesc(CaseManagementNoteLink.CASEMGMTNOTE, Long.valueOf(noteId));
            for (CaseManagementNoteLink link : cmll) {
                CaseManagementNote cmmn = caseManagementMgr.getNote(link.getNoteId().toString());
                if (cmmn == null) continue;

                if (cmmn.getNote().startsWith("imported.cms4.2011.06")) {
                    if (cml_dump == null) cml_dump = link;
                } else {
                    if (cml_anno == null) cml_anno = link;
                }
                if (cml_anno != null && cml_dump != null) break;
            }

            if (cml_anno != null) {// old annotation exists - create new link
                CaseManagementNoteLink cml_n = new CaseManagementNoteLink(CaseManagementNoteLink.CASEMGMTNOTE, note.getId(), cml_anno.getNoteId());
                caseManagementMgr.saveNoteLink(cml_n);
            }
            if (cml_dump != null) {// old dump exists - create new link
                CaseManagementNoteLink cml_n = new CaseManagementNoteLink(CaseManagementNoteLink.CASEMGMTNOTE, note.getId(), cml_dump.getNoteId());
                caseManagementMgr.saveNoteLink(cml_n);
            }
        }
        caseManagementMgr.getEditors(note);

        if (newNote) {
            logAction = LogConst.ADD;
        } else if (note.isArchived()) {
            logAction = LogConst.ARCHIVE;
        } else {
            logAction = LogConst.UPDATE;
        }

        LogAction.addLog((String) session.getAttribute("user"), logAction, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demo, note.getAuditString());

        String f = request.getParameter("forward");
        if (f != null && f.equals("none")) {
            response.getWriter().println(note.getId());
            return null;
        }

        this.setReloadUrl("/CaseManagementView.do?" + reloadUrl);
        return "listCPPNotes";
    }

    private long noteSave() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        //Before we do anything we make sure we still have the lock on the note
        HttpSession session = request.getSession();
        String demo = getDemographicNo(request);
        String sessionFrmName = "caseManagementEntryForm" + demo;

        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        //compare locks and see if they are the same
        CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demo);

        try {
            if (casemgmtNoteLockSession == null) {
                throw new Exception("SESSION CASEMANAGEMENT NOTE LOCK OBJECT IS NULL");
            }

            CasemgmtNoteLock casemgmtNoteLock = casemgmtNoteLockDao.find(casemgmtNoteLockSession.getId());
            //if other window has acquired lock we reject save
            if (!casemgmtNoteLock.getSessionId().equals(casemgmtNoteLockSession.getSessionId()) || !request.getRequestedSessionId().equals(casemgmtNoteLockSession.getSessionId())) {
                logger.debug("DO NOT HAVE LOCK FOR " + demo + " PROVIDER " + providerNo + " CONTINUE SAVING LOCAL SESSION " + request.getRequestedSessionId() + " LOCAL IP " + request.getRemoteAddr() + " LOCK SESSION " + casemgmtNoteLockSession.getSessionId() + " LOCK IP " + casemgmtNoteLockSession.getIpAddress());
                return -1L;
            }
        } catch (Exception e) {
            //Exception thrown if other window has saved and exited so lock is gone
            logger.error("Lock not found for " + demo + " providers " + providerNo + " IP " + request.getRemoteAddr(), e);
            return -1L;
        }

        CaseManagementNote note = sessionFrm.getCaseNote();
        String noteTxt = this.getCaseNote_note();
        noteTxt = StringUtils.trimToNull(noteTxt);
        if (noteTxt == null || noteTxt.equals("")) return -1L;

        note.setNote(noteTxt);

        Provider provider = loggedInInfo.getLoggedInProvider();
        String userName = provider != null ? provider.getFullName() : "";

        CaseManagementCPP cpp = this.caseManagementMgr.getCPP(demo);
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(demo);
        }
        String lastSavedNoteString = (String) session.getAttribute("lastSavedNoteString");

        // bug fix - encounter type was not being updated.
        String encounterType = request.getParameter("caseNote.encounter_type");
        if (encounterType != null) {
            note.setEncounter_type(encounterType);
        }

        String hourOfEncounterTime = request.getParameter("hourOfEncounterTime");
        if (hourOfEncounterTime != null && hourOfEncounterTime != "") {
            note.setHourOfEncounterTime(Integer.valueOf(hourOfEncounterTime));
        }

        String minuteOfEncounterTime = request.getParameter("minuteOfEncounterTime");
        if (minuteOfEncounterTime != null && minuteOfEncounterTime != "") {
            note.setMinuteOfEncounterTime(Integer.valueOf(minuteOfEncounterTime));
        }

        String hourOfEncTransportationTime = request.getParameter("hourOfEncTransportationTime");
        if (hourOfEncTransportationTime != null && hourOfEncTransportationTime != "") {
            note.setHourOfEncTransportationTime(Integer.valueOf(hourOfEncTransportationTime));
        }

        String minuteOfEncTransportationTime = request.getParameter("minuteOfEncTransportationTime");
        if (minuteOfEncTransportationTime != null && minuteOfEncTransportationTime != "") {
            note.setMinuteOfEncTransportationTime(Integer.valueOf(minuteOfEncTransportationTime));
        }

        String sign = request.getParameter("sign");
        if (sign == null) {
            note.setSigning_provider_no("");
            note.setSigned(false);
            sessionFrm.setSign("off");
        } else if (sign.equalsIgnoreCase("persist")) {

            if (note.isSigned()) {
                note.setSigning_provider_no(providerNo);
                note.setSigned(true);
            } else {
                note.setSigning_provider_no("");
                note.setSigned(false);
                sessionFrm.setSign("off");
            }
        } else if (sign.equalsIgnoreCase("on")) {
            note.setSigning_provider_no(providerNo);
            note.setSigned(true);
        } else {
            note.setSigning_provider_no("");
            note.setSigned(false);
            sessionFrm.setSign("off");
        }

        note.setProviderNo(providerNo);
        if (provider != null) note.setProvider(provider);

        // if this is an update, don't overwrite the program id
        if (note.getProgram_no() == null || note.getProgram_no().equals("") || "0".equals(note.getProgram_no())) {
            String programId = (String) session.getAttribute("case_program_id");
            if (programId == null || "null".equalsIgnoreCase(programId)) {
                EctProgram ectProgram = new EctProgram(session);
                programId = ectProgram.getProgram(providerNo);
                session.setAttribute("case_program_id", programId);
            }
            note.setProgram_no(programId);
        }

        /* get the checked issue save into note */
        // this goes into the database casemgmt_issue table
        List<CaseManagementIssue> issuelist = new ArrayList<CaseManagementIssue>();

        List<CheckBoxBean> checkedlist = sessionFrm.getIssueCheckList();
        // this is for debugging, please keep it for future development
        // System.out.println("Checkedlist from sessionFrm: " + Arrays.toString(checkedlist));
        // this gets attached to the CaseManagementNote object
        Set<CaseManagementIssue> issueset = new HashSet<CaseManagementIssue>();
        Set<CaseManagementNote> noteSet = new HashSet<CaseManagementNote>();
        String ongoing = "";
        if (checkedlist != null) {
            for (CheckBoxBean cb : checkedlist) {
                if ("on".equalsIgnoreCase(cb.getChecked())) {
                    CaseManagementIssue issue = cb.getIssue();
                    if (issue != null && issue.getId() != null) {
                        issueset.add(issue);
                    }
                }
            }
        }
        note.setIssues(issueset);
        Boolean useNewCaseMgmt = Boolean.valueOf((String) session.getAttribute("newCaseManagement"));
        if (useNewCaseMgmt) {
            ongoing = saveCheckedIssues_newCme(request, demo, note, issuelist, checkedlist, issueset, noteSet, ongoing);
        } else {
            ongoing = saveCheckedIssues_oldCme(request, demo, issuelist, checkedlist, issueset, noteSet, ongoing);
        }

        sessionFrm.setIssueCheckList(checkedlist);
        note.setIssues(issueset);

        /* remove signature and the related issues from note */
        String noteString = note.getNote();
        noteString = removeCurrentIssue(noteString);
        note.setNote(noteString);

        String resident = request.getParameter("resident");
        if (resident != null && !"null".equalsIgnoreCase(resident) && !"".equalsIgnoreCase(resident)) {
            String reviewer = request.getParameter("reviewer");
            String residentMsg = "";
            ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);

            if (!"null".equalsIgnoreCase(reviewer) && !"".equalsIgnoreCase(reviewer)) {
                ProviderData providerData = providerDataDao.find(reviewer);
                residentMsg = "\n\n***Reviewed with " + providerData.getLastName() + ", " + providerData.getFirstName() + "***\n";
            }

            String supervisor = request.getParameter("supervisor");
            if (!"null".equalsIgnoreCase(supervisor) && !"".equalsIgnoreCase(supervisor)) {
                ProviderData providerData = providerDataDao.find(supervisor);
                residentMsg = "\n\n***Not yet verified by " + providerData.getLastName() + ", " + providerData.getFirstName() + "***\n";
            }

            noteString = note.getNote();
            noteString += residentMsg;
            note.setNote(noteString);

        }

        /* add issues into notes */
        String includeIssue = request.getParameter("includeIssue");
        if (includeIssue == null || !includeIssue.equals("on")) {
            /* set includeissue in note */
            note.setIncludeissue(false);
            sessionFrm.setIncludeIssue("off");
        } else {
            note.setIncludeissue(true);
            /* add the related issues to note */

            String issueString = new String();
            issueString = createIssueString(issueset);
            // insert the string before signiture

            int index = noteString.indexOf("\n[[");
            if (index >= 0) {
                String begString = noteString.substring(0, index);
                String endString = noteString.substring(index + 1);
                note.setNote(begString + issueString + endString);
            } else {
                note.setNote(noteString + issueString);
            }
        }

        // update appointment and add verify message to note if verified
        EctSessionBean sessionBean = (EctSessionBean) session.getAttribute("EctSessionBean");
        String verifyStr = request.getParameter("verify");
        boolean verify = false;
        if (verifyStr != null && verifyStr.equalsIgnoreCase("on")) {
            verify = true;
        }

        // update password
        String passwd = this.getCaseNote().getPassword();
        if (passwd != null && passwd.trim().length() > 0) {
            note.setPassword(passwd);
            note.setLocked(true);
        }

        Date now = new Date();

        String observationDate = this.getObservation_date();
        ResourceBundle props = ResourceBundle.getBundle("oscarResources", request.getLocale());
        if (observationDate != null && !observationDate.equals("")) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy H:mm", Locale.ENGLISH);
            Date dateObserve = formatter.parse(observationDate);
            if (dateObserve.getTime() > now.getTime()) {
                request.setAttribute("DateError", props.getString("oscarEncounter.futureDate.Msg"));
                note.setObservation_date(now);
            } else note.setObservation_date(dateObserve);
        } else if (note.getObservation_date() == null) {
            note.setObservation_date(now);
        }

        note.setUpdate_date(now);

        // Checks whether the user can set the program via the UI - if so, make sure that they can't screw it up if they do
        if (OscarProperties.getInstance().getBooleanProperty("note_program_ui_enabled", "true")) {
            String noteProgramNo = request.getParameter("_note_program_no");
            String noteRoleId = request.getParameter("_note_role_id");

            if (noteProgramNo != null && noteRoleId != null && noteProgramNo.trim().length() > 0 && noteRoleId.trim().length() > 0) {
                if (noteProgramNo.equalsIgnoreCase("-2") || noteRoleId.equalsIgnoreCase("-2")) {
                    throw new Exception("Patient is not admitted to any programs user has access to. [roleId=-2, programNo=-2]");
                } else if (!noteProgramNo.equalsIgnoreCase("-1") && !noteRoleId.equalsIgnoreCase("-1")) {
                    note.setProgram_no(noteProgramNo);
                    note.setReporter_caisi_role(noteRoleId);
                }
            } else {
                throw new Exception("Missing role id or program number. [roleId=" + noteRoleId + ", programNo=" + noteProgramNo + "]");
            }
        }

        if (sessionBean.appointmentNo != null && sessionBean.appointmentNo.length() > 0) {
            note.setAppointmentNo(Integer.parseInt(sessionBean.appointmentNo));
        }

        /* Save annotation */
        String attrib_name = request.getParameter("annotation_attribname");
        CaseManagementNote annotationNote = (CaseManagementNote) session.getAttribute(attrib_name);

        note = caseManagementMgr.saveCaseManagementNote(loggedInInfo, note, issuelist, cpp, ongoing, verify, request.getLocale(), now, annotationNote, userName, (String) session.getAttribute("user"), request.getRemoteAddr(), lastSavedNoteString);
        caseManagementMgr.getEditors(note);
        this.setCaseNote(note);

        //update lock to new note id
        casemgmtNoteLockSession.setNoteId(note.getId());
        logger.debug("UPDATING NOTE ID in LOCK");
        casemgmtNoteLockDao.merge(casemgmtNoteLockSession);
        session.setAttribute("casemgmtNoteLock" + demo, casemgmtNoteLockSession);
        session.removeAttribute(attrib_name);

        try {
            this.caseManagementMgr.deleteTmpSave(providerNo, note.getDemographic_no(), note.getProgram_no());
        } catch (Exception e) {
            logger.warn("Warning", e);
        }

        return note.getId();
    }

    private String saveCheckedIssues_oldCme(HttpServletRequest request, String demo, List<CaseManagementIssue> issuelist, List<CheckBoxBean> checkedlist, Set issueset, Set noteSet, String ongoing) {

        int demographicNo = Integer.parseInt(demo);

        for (int i = 0; i < checkedlist.size(); i++) {
            CheckBoxBean checkBoxBean = checkedlist.get(i);
            CaseManagementViewAction.IssueDisplay issueDisplay = checkBoxBean.getIssueDisplay();

            if (issueDisplay.resolved != null && issueDisplay.resolved.equals("unresolved")) {
                ongoing = ongoing + issueDisplay.getDescription() + "\n";
            }

            boolean isChecked = WebUtils.isChecked(request, "issueCheckList[" + i + "].checked");

            CaseManagementIssue caseManagementIssue = null;
            caseManagementIssue = caseManagementIssueDao.getIssuebyIssueCode(demo, issueDisplay.code);
            if (caseManagementIssue == null && isChecked) {
                Issue issue = issueDao.findIssueByCode(issueDisplay.code);
                if (issue != null) {
                    caseManagementIssue = new CaseManagementIssue();
                    caseManagementIssue.setDemographic_no(demographicNo);
                    caseManagementIssue.setIssue_id(issue.getId());
                    caseManagementIssue.setType(issue.getRole());
                    caseManagementIssue.setUpdate_date(new Date());

                    // Should not save duplicated issue for one demographic
                    if (caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId())) == null) {
                        caseManagementIssueDao.saveIssue(caseManagementIssue);
                    }
                    // reload to materliase generated fields.
                    caseManagementIssue = caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId()));
                }
            } else if (caseManagementIssue != null && isChecked) {
                caseManagementIssue.setAcute("acute".equals(issueDisplay.acute));
                caseManagementIssue.setCertain("certain".equals(issueDisplay.certain));
                caseManagementIssue.setMajor("major".equals(issueDisplay.major));
                caseManagementIssue.setResolved("resolved".equals(issueDisplay.resolved));
                Issue issue = issueDao.findIssueByCode(issueDisplay.code);
                if (issue != null) {
                    caseManagementIssue.setUpdate_date(new Date());
                    // Should not save duplicated issue for one demographic
                    // But should be able to update existing issues.
                    caseManagementIssueDao.saveIssue(caseManagementIssue);
                    // reload to materliase generated fields.
                    caseManagementIssue = caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId()));
                }
            }

            if (caseManagementIssue == null) continue;
            else copyIssueDisplayToCaseManagementIssue(caseManagementIssue, issueDisplay);

            if (isChecked) {
                checkBoxBean.setChecked("on");
                checkBoxBean.setUsed(true);
                caseManagementIssue.setNotes(noteSet);

                issueset.add(caseManagementIssue);
            } else {
                checkBoxBean.setChecked("off");
                boolean isLocal = "local".equals(issueDisplay.location);
                if (!isLocal) {
                    checkBoxBean.setUsed(false);
                } else {
                    checkBoxBean.setUsed(caseManagementNoteDao.haveIssue(issueDisplay.code, demographicNo));
                }
            }

            if (!containsIssue(issuelist, caseManagementIssue)) issuelist.add(caseManagementIssue);
        }
        return ongoing;
    }

    private boolean containsIssue(List<CaseManagementIssue> issuelist, CaseManagementIssue issue) {
        for (CaseManagementIssue tempIssue : issuelist) {
            if (tempIssue.getId() != null && tempIssue.getId().equals(issue.getId())) return (true);
        }

        return (false);
    }

    private void copyIssueDisplayToCaseManagementIssue(CaseManagementIssue caseManagementIssue, IssueDisplay issueDisplay) {
        caseManagementIssue.setAcute("acute".equals(issueDisplay.acute));
        caseManagementIssue.setCertain("certain".equals(issueDisplay.certain));
        caseManagementIssue.setMajor("major".equals(issueDisplay.major));
        caseManagementIssue.setResolved("resolved".equals(issueDisplay.resolved));
    }

    private String saveCheckedIssues_newCme(HttpServletRequest request, String demo, CaseManagementNote note, List issuelist, List<CheckBoxBean> checkedlist, Set issueset, Set noteSet, String ongoing) {
        int demographicNo = Integer.parseInt(demo);

        for (int i = 0; i < checkedlist.size(); i++) {
            CheckBoxBean checkBoxBean = checkedlist.get(i);
            IssueDisplay issueDisplay = checkBoxBean.getIssueDisplay();

            if (issueDisplay.resolved != null && issueDisplay.resolved.equals("unresolved")) {
                ongoing = ongoing + issueDisplay.getDescription() + "\n";
            }

            boolean isChecked = WebUtils.isChecked(request, "issueCheckList[" + i + "].checked");

            CaseManagementIssue caseManagementIssue = null;
            caseManagementIssue = caseManagementIssueDao.getIssuebyIssueCode(demo, issueDisplay.code);
            if (caseManagementIssue == null && isChecked) {
                Issue issue = issueDao.findIssueByCode(issueDisplay.code);
                if (issue != null) {
                    caseManagementIssue = new CaseManagementIssue();
                    caseManagementIssue.setDemographic_no(demographicNo);
                    caseManagementIssue.setIssue_id(issue.getId());
                    caseManagementIssue.setType(issue.getRole());
                    caseManagementIssue.setUpdate_date(new Date());

                    // Should not save duplicated issue for one demographic
                    if (caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId())) == null) {
                        caseManagementIssueDao.saveIssue(caseManagementIssue);
                    }
                    // reload to materliase generated fields.
                    caseManagementIssue = caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId()));
                }
            }

            if (caseManagementIssue == null) continue;
            else copyIssueDisplayToCaseManagementIssue(caseManagementIssue, issueDisplay);

            if (isChecked) {
                checkBoxBean.setChecked("on");
                checkBoxBean.setUsed(true);
                caseManagementIssue.setNotes(noteSet);

                issueset.add(caseManagementIssue);
            } else {
                checkBoxBean.setChecked("off");
                boolean isLocal = "local".equals(issueDisplay.location);
                if (!isLocal) {
                    checkBoxBean.setUsed(false);
                } else {
                    checkBoxBean.setUsed(caseManagementNoteDao.haveIssue(issueDisplay.code, demographicNo));
                }
            }

            if (!containsIssue(issuelist, caseManagementIssue)) issuelist.add(caseManagementIssue);
        }
        return ongoing;
    }

    public String save() throws Exception {
        HttpSession session = request.getSession();
        if (session == null || session.getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "false");

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        request.setAttribute("from", request.getParameter("from"));
        long noteId = noteSave();

        /* prepare the message */
        addActionMessage(getText("note.saved"));
        String chain = request.getParameter("chain");

        if (chain != null && !chain.equals("")) {
            return chain;
        }

        return "view";
    }

    public String ajaxsave() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String noteTxt = request.getParameter("noteTxt");
        noteTxt = StringUtils.trimToNull(noteTxt);
        if (noteTxt == null || noteTxt.equals("")) return null;

        logger.debug("Saving Note" + request.getParameter("nId"));
        logger.debug("Text -- " + noteTxt);
        String demo = getDemographicNo(request);
        Provider provider = loggedInInfo.getLoggedInProvider();

        CaseManagementNote note;
        String history;
        String noteId = request.getParameter("nId");
        boolean newNote;
        Date now = new Date();
        if (noteId.substring(0, 1).equals("0")) {
            note = new CaseManagementNote();
            note.setDemographic_no(demo);
            history = new String();
            newNote = true;
        } else {
            note = this.caseManagementMgr.getNote(request.getParameter("nId"));
            history = note.getHistory();
            history = "---------History Record---------" + history;
            newNote = false;
        }

        String observationDate = request.getParameter("obsDate");
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (observationDate != null && !observationDate.equals("")) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy H:mm", request.getLocale());
            Date dateObserve = formatter.parse(observationDate);
            if (dateObserve.getTime() > now.getTime()) {
                request.setAttribute("DateError", props.getString("oscarEncounter.futureDate.Msg"));
                note.setObservation_date(now);
            } else note.setObservation_date(dateObserve);
        } else if (note.getObservation_date() == null) {
            note.setObservation_date(now);
        }

        history = noteTxt + "[[" + now + "]]" + history;
        note.setNote(noteTxt);
        note.setHistory(history);

        if (note.isSigned()) {
            note.setSigning_provider_no(providerNo);
            note.setSigned(true);
        } else {
            note.setSigning_provider_no("");
            note.setSigned(false);
        }

        note.setProviderNo(providerNo);
        if (provider != null) note.setProvider(provider);

        String programId = (String) session.getAttribute("case_program_id");
        note.setProgram_no(programId);

        WebApplicationContext ctx = this.getSpringContext();
        ProgramManager programManager = (ProgramManager) ctx.getBean(ProgramManager.class);
        AdmissionManager admissionManager = (AdmissionManager) ctx.getBean(AdmissionManager.class);

        String role = null;
        try {
            role = String.valueOf((programManager.getProgramProvider(note.getProviderNo(), note.getProgram_no())).getRole().getId());
        } catch (Exception e) {
            logger.error("Error", e);
            role = "0";
        }

        note.setReporter_caisi_role(role);

        String team = null;
        try {
            team = String.valueOf((admissionManager.getAdmission(note.getProgram_no(), Integer.valueOf(note.getDemographic_no()))).getTeamId());
        } catch (Exception e) {
            logger.error("Error", e);
            team = "0";
        }

        note.setReporter_program_team(team);

        String sessionName = "caseManagementEntryForm" + demo;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionName);
        List<CaseManagementIssue> issuelist = new ArrayList<CaseManagementIssue>();
        Set<CaseManagementIssue> issueset = new HashSet<CaseManagementIssue>();
        Set<CaseManagementNote> noteSet = new HashSet<CaseManagementNote>();

        int numIssues = Integer.parseInt(request.getParameter("numIssues"));

        List<CheckBoxBean> checkedlist = sessionFrm.getIssueCheckList();
        for (int i = 0; i < numIssues; i++) {
            String ischecked = request.getParameter("issue" + i);
            if (ischecked != null && ischecked.equalsIgnoreCase("on")) {
                checkedlist.get(i).setChecked("on");
                CaseManagementIssue iss = checkedlist.get(i).getIssue();
                iss.setNotes(noteSet);
                issueset.add(checkedlist.get(i).getIssue());
            } else {
                checkedlist.get(i).setChecked("off");
            }
            issuelist.add(checkedlist.get(i).getIssue());
        }

        note.setIssues(issueset);
        note.setIncludeissue(false);
        caseManagementMgr.saveAndUpdateCaseIssues(issuelist);
        sessionFrm.setIssueCheckList(checkedlist);

        int revision;

        if (note.getRevision() != null) {
            revision = Integer.parseInt(note.getRevision());
            ++revision;
        } else revision = 1;

        note.setRevision(String.valueOf(revision));

        note.setUpdate_date(now);
        if (note.getCreate_date() == null) note.setCreate_date(now);

        note.setEncounter_type(request.getParameter("caseNote.encounter_type"));

        String hourOfEncounterTime = request.getParameter("hourOfEncounterTime");
        if (hourOfEncounterTime != null) {
            note.setHourOfEncounterTime(Integer.valueOf(hourOfEncounterTime));
        }
        String minuteOfEncounterTime = request.getParameter("minuteOfEncounterTime");
        if (minuteOfEncounterTime != null) {
            note.setMinuteOfEncounterTime(Integer.valueOf(minuteOfEncounterTime));
        }
        String hourOfEncTransportationTime = request.getParameter("hourOfEncTransportationTime");
        if (minuteOfEncounterTime != null) {
            note.setHourOfEncTransportationTime(Integer.valueOf(hourOfEncTransportationTime));
        }
        String minuteOfEncTransportationTime = request.getParameter("minuteOfEncTransportationTime");
        if (minuteOfEncounterTime != null) {
            note.setMinuteOfEncTransportationTime(Integer.valueOf(minuteOfEncTransportationTime));
        }

        // check if previous note is doc note.
        Long prevNoteId = note.getId();

        this.caseManagementMgr.saveNoteSimple(note);
        this.caseManagementMgr.getEditors(note);

        if (prevNoteId != null) {
            caseManagementMgr.addNewNoteLink(prevNoteId);
        }

        try {
            this.caseManagementMgr.deleteTmpSave(providerNo, note.getDemographic_no(), note.getProgram_no());
        } catch (Exception e) {
            logger.warn("Warning", e);
        }

        session.setAttribute(sessionName, sessionFrm);
        CaseManagementEntryFormBean newform = new CaseManagementEntryFormBean();
        newform.setCaseNote(note);
        newform.setIssueCheckList(checkedlist);
        request.setAttribute("caseManagementEntryForm", newform);
        String varName = "newNote";
        session.setAttribute(varName, false);
        request.setAttribute("ajaxsave", note.getId());
        request.setAttribute("origNoteId", noteId);

        String logAction;
        if (newNote) {
            logAction = LogConst.ADD;
        } else {
            logAction = LogConst.UPDATE;
        }

        LogAction.addLog((String) session.getAttribute("user"), logAction, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demo, note.getAuditString());

        return "issueList_ajax";
    }

    private void releaseNoteLock(String providerNo, Integer demographicNo, Long noteId) {
        logger.debug("REMOVING LOCK FOR PROVIDER " + providerNo + " DEMO " + demographicNo + " NOTE ID " + noteId);
        casemgmtNoteLockDao.remove(providerNo, demographicNo, noteId);
    }

    public String releaseNoteLock() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        String forceRelease = request.getParameter("force");
        HttpSession session = request.getSession();
        String sessionFrmName = "caseManagementEntryForm" + demoNo;

        try {
            CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demoNo);
            //If browser is exiting check to see if we should release lock.  It may be held by same user in another window so we check
            if (request.getRequestedSessionId().equals(casemgmtNoteLockSession.getSessionId()) && casemgmtNoteLockSession.getNoteId() == Long.parseLong(noteId)) {
                releaseNoteLock(providerNo, Integer.parseInt(demoNo), Long.parseLong(noteId));
                session.removeAttribute("casemgmtNoteLock" + demoNo);
            }
            //If we clicked on a note to edit we want to release old note's lock.  Session lock has already been updated with new note id
            //so we force removal of old note lock
            else if (forceRelease != null && forceRelease.equalsIgnoreCase("true")) {
                releaseNoteLock(providerNo, Integer.parseInt(demoNo), Long.parseLong(noteId));
            }

        } catch (Exception e) {
            //nothing to do. lock was not found
        }

        return null;
    }

    public String saveAndExit() throws Exception {
        logger.debug("saveandexit");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();

        String demoNo = getDemographicNo(request);

        if (session.getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "false");

        addActionMessage(getText("note.saved"));

        String priorNote = this.getNoteId();
        Long noteId = noteSave();
        session.removeAttribute("casemgmtNoteLock" + demoNo);

        if (noteId == -1) {
            return "windowCloseError";
        }

        releaseNoteLock(providerNo, Integer.parseInt(demoNo), noteId);
        this.setMethod("view");
        String error = (String) request.getAttribute("DateError");
        if (error != null) {
            return "windowCloseError";
        }

        String supervisor = null;
        String reviewer = null;
        String resident = request.getParameter("resident");
        String reviewerNo = null;
        ResidentOscarMsgDao residenOscarMsgDao = SpringUtils.getBean(ResidentOscarMsgDao.class);
        if (resident != null && !"null".equalsIgnoreCase(resident)) {
            reviewer = request.getParameter("reviewer");
            if ("null".equalsIgnoreCase(reviewer) || "".equalsIgnoreCase(reviewer)) {
                reviewer = null;
            }

            supervisor = request.getParameter("supervisor");
            if ("null".equalsIgnoreCase(supervisor) || "".equalsIgnoreCase(supervisor)) {
                supervisor = null;
            }

            if (supervisor != null) {
                Calendar epoch = GregorianCalendar.getInstance();
                epoch.set(1970, 0, 1, 0, 0, 0);
                ResidentOscarMsg residentOscarMsg = new ResidentOscarMsg();
                residentOscarMsg.setComplete(Boolean.FALSE);
                residentOscarMsg.setCreate_time(new Date(System.currentTimeMillis()));
                residentOscarMsg.setComplete_time(epoch.getTime());
                residentOscarMsg.setResident_no(loggedInInfo.getLoggedInProvider().getProviderNo());
                residentOscarMsg.setSupervisor_no(supervisor);
                residentOscarMsg.setDemographic_no(Integer.valueOf(demoNo));
                residentOscarMsg.setNote_id(noteId);
                if (this.getAppointmentNo() != null) {
                    residentOscarMsg.setAppointment_no(Integer.valueOf(this.getAppointmentNo()));
                }
                residenOscarMsgDao.persist(residentOscarMsg);

                reviewerNo = supervisor;
            } else if (reviewer != null) {
                reviewerNo = reviewer;
            }
        }

        if (OscarProperties.getInstance().getProperty("resident_review", "false").equalsIgnoreCase("true")) {
            String verifyStr = request.getParameter("verify");
            if (verifyStr != null && verifyStr.equalsIgnoreCase("on")) {

                if (priorNote != null && !"null".equalsIgnoreCase(priorNote) && !"".equalsIgnoreCase(priorNote)) {

                    for (CaseManagementNote n : caseManagementNoteDao.getNotesByUUID(this.getCaseNote().getUuid())) {

                        ResidentOscarMsg residentOscarMsg = residenOscarMsgDao.findByNoteId(n.getId());

                        if (residentOscarMsg != null) {

                            residentOscarMsg.setComplete(Boolean.TRUE);
                            residentOscarMsg.setComplete_time(new Date(System.currentTimeMillis()));

                            residenOscarMsgDao.merge(residentOscarMsg);
                        }
                    }
                }
            }
        }

        String toBill = request.getParameter("toBill");

        if (toBill != null && toBill.equalsIgnoreCase("true")) {
            String region = this.getBillRegion();
            // Try to get appointment number from the note first, before the action field
            Integer noteApptNo = this.getCaseNote() != null ? this.getCaseNote().getAppointmentNo() : null;
            String appointmentNo = (noteApptNo != null && noteApptNo > 0) ?
                String.valueOf(noteApptNo) : this.getAppointmentNo();
            String name = this.getDemoName(demoNo);
            String date = this.getAppointmentDate();
            String start_time = this.getStart_time();
            String apptProvider = this.getApptProvider();
            if (apptProvider == null || apptProvider.isEmpty() || "none".equals(apptProvider)) {
                apptProvider = providerNo;
            }
            String providerview = null;
            if (reviewerNo != null) {
                Provider p = providerMgr.getProvider(reviewerNo);
                if (p.getProviderType().equalsIgnoreCase("nurse")) {
                    providerview = "000000";
                } else {
                    providerview = reviewerNo;
                }
            } else {
                providerview = loggedInInfo.getLoggedInProviderNo();
            }
            String defaultView = OscarProperties.getInstance().getProperty("default_view", "");

            Set setIssues = this.getCaseNote().getIssues();
            Iterator iter = setIssues.iterator();
            StringBuilder dxCodes = new StringBuilder();
            String strDxCode;
            int dxNum = 0;
            while (iter.hasNext()) {
                CaseManagementIssue cIssue = (CaseManagementIssue) iter.next();
                dxCodes.append("&dxCode");
                strDxCode = String.valueOf(cIssue.getIssue().getCode());
                if (strDxCode.length() > 3) {
                    strDxCode = strDxCode.substring(0, 3);
                }

                if (dxNum > 0) {
                    dxCodes.append(String.valueOf(dxNum));
                }

                dxCodes.append("=" + strDxCode);
                ++dxNum;
            }

            String contextPath = request.getContextPath();
            String url = contextPath + "/billing.do?billRegion=" + region
                    + "&billForm=" + defaultView
                    + "&hotclick=&appointment_no="
                    + appointmentNo
                    + "&demographic_name=" + java.net.URLEncoder.encode(name, "utf-8")
                    + "&amp;status=t&demographic_no=" + demoNo
                    + "&providerview=" + providerview
                    + "&user_no=" + providerNo
                    + "&apptProvider_no=" + apptProvider
                    + "&appointment_date=" + date
                    + "&start_time=" + start_time
                    + "&bNewForm=1" + dxCodes.toString();
            logger.debug("BILLING URL " + url);
            response.sendRedirect(url);
        }

        String chain = request.getParameter("chain");

        if (chain != null && !chain.equals("")) {
            // Validate the redirect URL to prevent open redirect vulnerability
            if (isValidInternalRedirect(chain, request)) {
                response.sendRedirect(chain);
            } else {
                logger.warn("Attempted redirect to invalid URL: " + chain);
                // Fall through to return "windowClose" without redirect
            }
        }

        return "windowClose";
    }

    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String programNo = (String) session.getAttribute("case_program_id");

        String demo = this.getDemographicNo();

        try {
            logger.debug("CANCEL P:" + providerNo + " D:" + demo + " PROG:" + programNo);
            this.caseManagementMgr.deleteTmpSave(providerNo, demo, programNo);
        } catch (Exception e) {
            logger.warn("Warning", e);
        }

        return "windowClose";
    }

    public String exit() {
        request.setAttribute("change_flag", "false");
        return "list";
    }

    public String addNewIssue() {
        logger.debug("addNewIssue");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "true");
        String demono = getDemographicNo(request);
        String sessionFrmName = "caseManagementEntryForm" + demono;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
        CaseManagementNote note = sessionFrm.getCaseNote();
        String noteTxt = this.getCaseNote_note();
        noteTxt = StringUtils.trimToNull(noteTxt);
        note.setNote(noteTxt);

        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        request.setAttribute("from", request.getParameter("from"));

        this.setShowList("false");
        this.setSearString("");
        return "IssueSearch";
    }

    public String issueList() throws Exception {
        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String programId = (String) session.getAttribute("case_program_id");
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // get the issue list have search string
        String search = request.getParameter("issueSearch");
        if (search == null || search.isEmpty()) {
            search = request.getParameter("term");
        }

        List<Issue> searchResults = caseManagementMgr.searchIssues(providerNo, programId, search);

        JSONUtil.jsonResponse(response, JsonUtil.pojoCollectionToJson(searchResults));
        return null;
    }

    public String issueSearch() {
        logger.debug("issueSearch");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        String programId = (String) session.getAttribute("case_program_id");

        request.setAttribute("change_flag", "true");

        request.setAttribute("from", request.getParameter("from"));
        this.setShowList("true");

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        // get the issue list have search string
        String search = this.getSearString();

        List<Issue> searchResults;
        searchResults = caseManagementMgr.searchIssues(providerNo, programId, search);

        List<Issue> filteredSearchResults = new ArrayList<Issue>();

        // remove issues which we already have - we don't want duplicates
        List existingIssues = caseManagementMgr.filterIssues(loggedInInfo, loggedInInfo.getLoggedInProviderNo(), caseManagementMgr.getIssues(Integer.parseInt(demono)), programId);
        Map existingIssuesMap = convertIssueListToMap(existingIssues);
        for (Iterator<Issue> iter = searchResults.iterator(); iter.hasNext(); ) {
            Issue issue = iter.next();
            if (existingIssuesMap.get(issue.getId()) == null) {
                filteredSearchResults.add(issue);
            }
        }

        CheckIssueBoxBean[] issueList = new CheckIssueBoxBean[filteredSearchResults.size()];
        for (int i = 0; i < filteredSearchResults.size(); i++) {
            Issue issue = filteredSearchResults.get(i);
            issueList[i] = new CheckIssueBoxBean();
            issueList[i].setIssue(issue);
        }
        logger.debug("Community issue reconciliation complete");
        String sessionFrmName = "caseManagementEntryForm" + demono;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
        sessionFrm.setNewIssueCheckList(issueList);
        sessionFrm.setShowList("true");

        if (request.getParameter("change_diagnosis") != null)
            request.setAttribute("change_diagnosis", request.getParameter("change_diagnosis"));
        if (request.getParameter("change_diagnosis_id") != null)
            request.setAttribute("change_diagnosis_id", request.getParameter("change_diagnosis_id"));

        return "IssueSearch";
    }

    // we need to convert single issue into checkbox array so we can play nicely with CaseManagementEntryFormBean
    public String makeIssue() throws Exception {
        HttpSession session = request.getSession();
        // grab the issue we want to add
        String issueId = request.getParameter("newIssueId");

        String sessionFrmName = "caseManagementEntryForm" + this.getDemographicNo(request);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        // check to see if this issue has already been associated with this demographic
        long lIssueId = Long.parseLong(issueId);
        List<CheckBoxBean> existingCaseIssueList = sessionFrm.getIssueCheckList();
        if (existingCaseIssueList != null) {
            for (CheckBoxBean checkBoxBean : existingCaseIssueList) {
                if (checkBoxBean.getIssue().getIssue_id() == lIssueId) {
                    break;
                }
            }
        }

        // if issue hasn't been added, add it
        // if it has do nothing;-> change to if it's already added, still keep it but won't
        CheckIssueBoxBean[] caseIssueList = new CheckIssueBoxBean[1];

        caseIssueList[0] = new CheckIssueBoxBean();
        Issue issue = caseManagementMgr.getIssue(issueId);
        caseIssueList[0].setIssue(issue);
        caseIssueList[0].setChecked(true);
        sessionFrm.setNewIssueCheckList(caseIssueList);

        return issueAdd();
    }

    public String issueAdd() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String changeDiagnosis = request.getParameter("change_diagnosis");
        if (changeDiagnosis != null && changeDiagnosis.equalsIgnoreCase("true")) {
            return submitChangeDiagnosis();
        }
        logger.debug("issueAdd");
        request.setAttribute("change_flag", "true");
        request.setAttribute("from", request.getParameter("from"));

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        String sessionFrmName = "caseManagementEntryForm" + demono;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        if (sessionFrm != null) {
            if (sessionFrm.getCaseNote() != null)
                sessionFrm.getCaseNote().setObservation_date(UtilDateUtilities.StringToDate(this.getObservation_date(), "dd-MMM-yyyy H:mm"));
            if (this.getCaseNote() != null && this.getCaseNote().getEncounter_type() != null)
                sessionFrm.getCaseNote().setEncounter_type(this.getCaseNote().getEncounter_type());
            if (this.getMinuteOfEncounterTime() != null)
                sessionFrm.getCaseNote().setMinuteOfEncounterTime(this.getMinuteOfEncounterTime());
            if (this.getHourOfEncounterTime() != null)
                sessionFrm.getCaseNote().setHourOfEncounterTime(this.getHourOfEncounterTime());
            if (this.getMinuteOfEncTransportationTime() != null)
                sessionFrm.getCaseNote().setMinuteOfEncTransportationTime(this.getMinuteOfEncTransportationTime());
            if (this.getHourOfEncTransportationTime() != null)
                sessionFrm.getCaseNote().setHourOfEncTransportationTime(this.getHourOfEncTransportationTime());
        }

        // add checked new issues to client's issue list
        // client's old issues
                List<CheckBoxBean> oldList = sessionFrm.getIssueCheckList();
        // client's new issues
        CheckIssueBoxBean[] issueList = sessionFrm.getNewIssueCheckList();
        int k = 0;
        if (issueList != null) {
            for (int i = 0; i < issueList.length; i++) {

                // duplicated issues should be discarded.
                if (caseManagementIssueDao.getIssuebyId(demono, String.valueOf(issueList[i].getIssue().getId())) != null) {
                    continue;
                }

                if (issueList[i].isChecked()) k++;
            }
        }

        List<CheckBoxBean> caseIssueList = new ArrayList<CheckBoxBean>();
        for (CheckBoxBean bean : oldList) {
            caseIssueList.add(bean);
        }
        k = 0;

        String programIdStr = (String) session.getAttribute(SessionConstants.CURRENT_PROGRAM_ID);
        if (programIdStr == null) programIdStr = (String) session.getAttribute("case_program_id");
        Integer programId = null;
        if (programIdStr != null) programId = Integer.valueOf(programIdStr);

        Properties dxProps = new Properties();
        try {
            InputStream is = getClass().getResourceAsStream("/caisi_issues_dx.properties");
            dxProps.load(is);
        } catch (IOException e) {
            logger.warn("Unable to load Dx properties file");
        }

        if (issueList != null) {
            CaseManagementView2Action caseManagementViewAction = new CaseManagementView2Action();

            for (int i = 0; i < issueList.length; i++) {
                if (issueList[i].isChecked()) {
                    if (caseManagementIssueDao.getIssuebyId(demono, String.valueOf(issueList[i].getIssue().getId())) != null) {
                        //issue already added
                        for (int j = 0; j < oldList.size(); j++) {
                            if (oldList.get(j).getIssue().getIssue_id() == issueList[i].getIssue().getId().longValue()) { //find old issue and check it
                                caseIssueList.get(j).setChecked("on");
                                caseIssueList.get(j).getIssue().setAcute(false);
                                caseIssueList.get(j).getIssue().setCertain(false);
                                caseIssueList.get(j).getIssue().setMajor(false);
                                caseIssueList.get(j).getIssue().setResolved(false);

                                caseIssueList.get(j).getIssueDisplay().setAcute("chronic");
                                caseIssueList.get(j).getIssueDisplay().setCertain("uncertain");
                                caseIssueList.get(j).getIssueDisplay().setMajor("not major");
                                caseIssueList.get(j).getIssueDisplay().setResolved("unresolved");
                            }
                        }
                    } else {
                        CheckBoxBean newBean = new CheckBoxBean();
                        CaseManagementIssue cmi = newIssueToCIssue(sessionFrm, issueList[i].getIssue(), programId);
                        newBean.setIssue(cmi);
                        newBean.setChecked("on");
                        IssueDisplay issueDisplay = caseManagementViewAction.getIssueDisplay(providerNo, programId, cmi);
                        newBean.setIssueDisplay(issueDisplay);

                        // should issue be automagically added to Dx? check config file
                        if (dxProps != null && dxProps.get(issueList[i].getIssue().getCode()) != null) {
                            String codingSystem = dxProps.getProperty("coding_system");
                            if (newBean.getIssue().isCertain()) {
                                logger.debug("adding to Dx");
                                this.caseManagementMgr.saveToDx(loggedInInfo, getDemographicNo(request), issueList[i].getIssue().getCode(), codingSystem, false);
                                newBean.getIssue().setMajor(true);
                            }
                        }
                        caseIssueList.add(newBean);

                        k++;
                    }
                }
            }
        }
        this.setIssueCheckList(caseIssueList);
        sessionFrm.setIssueCheckList(caseIssueList);

        String ajax = request.getParameter("ajax");
        if (ajax != null && ajax.equalsIgnoreCase("true")) {
            request.setAttribute("caseManagementEntryForm", sessionFrm);
            return "issueList_ajax";
        } else return "view";
    }

    public String changeDiagnosis() {
        logger.debug("changeDiagnosis");
        if (request.getSession().getAttribute("userrole") == null) return "expired";

        String inds = this.getDeleteId();
        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));
        request.setAttribute("from", request.getParameter("from"));
        request.setAttribute("change_diagnosis", Boolean.valueOf(true));
        request.setAttribute("change_diagnosis_id", inds);
        this.setShowList("false");
        this.setSearString("");
        return "IssueSearch";
    }

    public String submitChangeDiagnosis() {
        logger.debug("submitChangeDiagnosis");
        if (request.getSession().getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "true");
        request.setAttribute("from", request.getParameter("from"));

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        // get issue we're changing
        String strIndex = request.getParameter("change_diagnosis_id");
        int index = Integer.parseInt(strIndex);

        // change issue
        List<CheckBoxBean> oldList = this.getIssueCheckList();

        CheckIssueBoxBean[] issueList = this.getNewIssueCheckList();
        CheckIssueBoxBean substitution = null;
        String origIssueDesc = null;
        String newIssueDesc = null;
        // find the checked issue
        for (CheckIssueBoxBean curr : issueList) {
            if (curr.isChecked()) {
                substitution = curr;
                break;
            }
        }

        if (substitution != null) {
            for (int x = 0; x < oldList.size(); x++) {
                if (x == index) {

                    Issue oldIssue = oldList.get(x).getIssue().getIssue();
                    origIssueDesc = oldIssue.getDescription();

                    Issue newIssue = caseManagementMgr.getIssue(String.valueOf(substitution.getIssue().getId().longValue()));
                    newIssueDesc = newIssue.getDescription();

                    oldList.get(x).getIssue().setIssue(newIssue);
                    oldList.get(x).getIssue().setIssue_id(substitution.getIssue().getId().longValue());
                    oldList.get(x).getIssue().setType(newIssue.getType());
                    oldList.get(x).getIssueDisplay().setCode(newIssue.getCode());
                    oldList.get(x).getIssueDisplay().setCodeType(newIssue.getType());
                    oldList.get(x).getIssueDisplay().setDescription(newIssue.getDescription());

                    caseManagementMgr.saveCaseIssue(oldList.get(x).getIssue());
                }
            }
        }

        this.setIssueCheckList(oldList);
        if (substitution != null && origIssueDesc != null)
            this.caseManagementMgr.changeIssueInCPP(demono, origIssueDesc, newIssueDesc);

        return "view";
    }

    public String ajaxChangeDiagnosis() {
        logger.debug("ajaxChangeDiagnosis");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        // get issue we're changing
        String strIndex = request.getParameter("change_diagnosis_id");
        int idx = Integer.parseInt(strIndex);

        String substitution = request.getParameter("newIssueId");
        String sessionFrmName = "caseManagementEntryForm" + getDemographicNo(request);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        List<CheckBoxBean> curIssues = sessionFrm.getIssueCheckList();

        if (substitution != null && curIssues != null && idx < curIssues.size()) {

            Issue iss = caseManagementMgr.getIssue(substitution);
            curIssues.get(idx).getIssue().setIssue(iss);
            curIssues.get(idx).getIssue().setIssue_id(iss.getId());
            this.caseManagementMgr.saveCaseIssue(curIssues.get(idx).getIssue());

            // update form with new issue list
            Set<CaseManagementIssue> issueset = new HashSet<CaseManagementIssue>();
            for (CheckBoxBean checkBoxBean : curIssues) {
                if (checkBoxBean.getChecked().equalsIgnoreCase("on")) issueset.add(checkBoxBean.getIssue());
            }

            sessionFrm.getCaseNote().setIssues(issueset);
        }

        if (curIssues != null) {
            sessionFrm.setIssueCheckList(curIssues);
        }
        request.setAttribute("caseManagementEntryForm", sessionFrm);

        return "issueList_ajax";
    }

    public String issueDelete() throws Exception {
        logger.debug("issueDelete");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        String demono = getDemographicNo(request);
        String sessionFrmName = "caseManagementEntryForm" + demono;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        request.setAttribute("change_flag", "true");
        request.setAttribute("from", request.getParameter("from"));
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        List<CheckBoxBean> oldList = sessionFrm.getIssueCheckList();

        String inds = this.getDeleteId();
        Integer ind = Integer.valueOf(inds);

        // delete the right issue
        List<CheckBoxBean> caseIssueList = new ArrayList<CheckBoxBean>();
        CaseManagementIssue iss = null;

        if (ind.intValue() >= oldList.size()) {
            logger.error("issueDelete index error");
            return "view";
        }
        for (int i = 0; i < oldList.size(); i++) {

            if (i != ind.intValue()) {
                caseIssueList.add(oldList.get(i));
            }
            if (i == ind.intValue()) {
                // delete from caseissue table
                iss = oldList.get(i).getIssue();
                caseManagementMgr.deleteIssueById(iss);
            }
        }
        this.setIssueCheckList(caseIssueList);
        sessionFrm.setIssueCheckList(caseIssueList);

        if (OscarProperties.getInstance().isCaisiLoaded() && iss != null) {
            // reset current concern in CPP
            caseManagementMgr.removeIssueFromCPP(demono, iss);
        }

        // added by Eugene Petrhin in order to guarantee the DB consistency if user hits Cancel afterwards
        if (sessionFrm.getCaseNote().getId() != null) {
            noteSave();
        }

        String ajax = request.getParameter("ajax");
        if (ajax != null && ajax.equalsIgnoreCase("true")) {
            request.setAttribute("caseManagementEntryForm", sessionFrm);
            return "issueList_ajax";
        } else return "view";
    }

    public String issueChange() throws Exception {
        logger.debug("issueChange");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        request.setAttribute("from", request.getParameter("from"));
        request.setAttribute("change_flag", "true");
        session.setAttribute("issueStatusChanged", "true");
        String demono = getDemographicNo(request);
        String sessionFrmName = "caseManagementEntryForm" + demono;
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);

        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        String inds = this.getLineId();
        Integer ind = Integer.valueOf(inds);

        // Get the list from session
        List<CheckBoxBean> oldList = sessionFrm.getIssueCheckList();

        // Read the changed values directly from request parameters
        String acuteParam = request.getParameter("issueCheckList[" + ind + "].issue.acute");
        String certainParam = request.getParameter("issueCheckList[" + ind + "].issue.certain");
        String majorParam = request.getParameter("issueCheckList[" + ind + "].issue.major");
        String resolvedParam = request.getParameter("issueCheckList[" + ind + "].issue.resolved");

        logger.debug("issueChange for index " + ind + ": resolved=" + resolvedParam);

        // Update the issue with the new values from the form
        if (acuteParam != null) {
            oldList.get(ind).getIssue().setAcute(Boolean.parseBoolean(acuteParam));
        }
        if (certainParam != null) {
            oldList.get(ind).getIssue().setCertain(Boolean.parseBoolean(certainParam));
        }
        if (majorParam != null) {
            oldList.get(ind).getIssue().setMajor(Boolean.parseBoolean(majorParam));
        }
        if (resolvedParam != null) {
            oldList.get(ind).getIssue().setResolved(Boolean.parseBoolean(resolvedParam));
        }

        List<CaseManagementIssue> iss = new ArrayList<CaseManagementIssue>();
        oldList.get(ind.intValue()).getIssue().setUpdate_date(new Date());
        iss.add(oldList.get(ind.intValue()).getIssue());
        caseManagementMgr.saveAndUpdateCaseIssues(iss);

        //Change issueDisplay if this issue is successfully saved.
        sessionFrm.getIssueCheckList().get(ind.intValue()).getIssueDisplay().setAcute(oldList.get(ind.intValue()).getIssue().isAcute() ? "acute" : "chronic");
        sessionFrm.getIssueCheckList().get(ind.intValue()).getIssueDisplay().setCertain(oldList.get(ind.intValue()).getIssue().isCertain() ? "certain" : "uncertain");
        sessionFrm.getIssueCheckList().get(ind.intValue()).getIssueDisplay().setMajor(oldList.get(ind.intValue()).getIssue().isMajor() ? "major" : "not major");
        sessionFrm.getIssueCheckList().get(ind.intValue()).getIssueDisplay().setResolved(oldList.get(ind.intValue()).getIssue().isResolved() ? "resolved" : "unresolved");

        if (OscarProperties.getInstance().isCaisiLoaded()) {
            // get access right
            List accessRight = caseManagementMgr.getAccessRight(providerNo, demono, (String) session.getAttribute("case_program_id"));

            // add medical history to CPP
            CaseManagementCPP cpp = this.caseManagementMgr.getCPP(getDemographicNo(request));
            if (cpp == null) {
                cpp = new CaseManagementCPP();
            }
            setCPPMedicalHistory(cpp, providerNo, accessRight);
            cpp.setUpdate_date(new Date());
            caseManagementMgr.saveCPP(cpp, providerNo);
        }

        String ajax = request.getParameter("ajax");
        if (ajax != null && ajax.equalsIgnoreCase("true")) {
            request.setAttribute("caseManagementEntryForm", sessionFrm);
            return "issueList_ajax";
        } else return "view";
    }

    public String notehistory() {
        if (request.getSession().getAttribute("userrole") == null) return "expired";

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));

        String noteid = request.getParameter("noteId");

        List<CaseManagementNote> history = caseManagementMgr.getHistory(noteid);
        for (CaseManagementNote caseManagementNote : history) {
            caseManagementNote.setNote(caseManagementNote.getNote().replace("\n", "<br/>"));
        }
        request.setAttribute("history", history);
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        request.setAttribute("title", props.getString("oscarEncounter.noteHistory.title"));
        return "showHistory";
    }

    public String issuehistory() {
        if (request.getSession().getAttribute("userrole") == null) return "expired";

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        String issueIds = request.getParameter("issueIds");

        List<CaseManagementNote> history = new ArrayList<CaseManagementNote>();
        List<CaseManagementNote> temp = caseManagementMgr.getIssueHistory(issueIds, demono);

        ArrayList<Boolean> current = new ArrayList<Boolean>(history.size());
        for (Iterator<CaseManagementNote> iter = temp.listIterator(); iter.hasNext(); ) {
            CaseManagementNote historyNote = iter.next();
            CaseManagementNote recentNote = caseManagementMgr.getMostRecentNote(historyNote.getUuid());

            if (!recentNote.isLocked()) {
                history.add(historyNote);
                if (recentNote.getUpdate_date().compareTo(historyNote.getUpdate_date()) > 0) {
                    current.add(Boolean.valueOf(false));
                } else current.add(Boolean.valueOf(true));
            }
        }

        request.setAttribute("history", history);
        request.setAttribute("current", current);

        StringBuilder title = new StringBuilder();
        String arrIssues[] = issueIds.split(",");
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (arrIssues != null) {
            for (int idx = 0; idx < arrIssues.length; ++idx) {
                String tempArrIssue = StringUtils.trimToNull(arrIssues[idx]);
                if (tempArrIssue == null) continue;
                Issue i = this.caseManagementMgr.getIssue(tempArrIssue);
                title.append(i.getDescription());
                if (idx < arrIssues.length - 1) title.append(", ");
            }
        }
        title.append(" " + props.getString("oscarEncounter.history.title"));
        request.setAttribute("title", title.toString());

        return "showHistory";
    }

    public String history() {
        logger.debug("history");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        String noteid = request.getParameter("noteId");
        CaseManagementNote note = caseManagementMgr.getNote(noteid);

        request.setAttribute("history", note.getHistory());

        this.setCaseNote_history(note.getHistory());

        LogAction.addLog((String) session.getAttribute("user"), LogConst.READ, LogConst.CON_CME_NOTE, noteid, request.getRemoteAddr(), note.getAuditString());

        return "historyview";
    }

    public String autosave() {
        logger.debug("autosave");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String demographicNo = getDemographicNo(request);
        String programId = request.getParameter("programId");
        String note = request.getParameter("note");
        String noteId = request.getParameter("note_id");

        //compare locks and see if they are the same
        CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) request.getSession().getAttribute("casemgmtNoteLock" + demographicNo);
        try {
            //if other window has acquired lock don't save
            CasemgmtNoteLock casemgmtNoteLock = casemgmtNoteLockDao.find(casemgmtNoteLockSession.getId());
            if (!casemgmtNoteLock.getSessionId().equals(casemgmtNoteLockSession.getSessionId())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return null;
            }
        } catch (Exception e) {
            //Exception thrown if other window has saved and exited so lock is gone
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;

        }

        if (note == null || note.length() == 0) {
            return null;
        }

        //delete from tmp save and then add another
        try {
            caseManagementMgr.deleteTmpSave(providerNo, demographicNo, programId);
            caseManagementMgr.tmpSave(providerNo, demographicNo, programId, noteId, note);
        } catch (Exception e) {
            logger.warn("AutoSave Error: " + e);
        }

        this.getCaseNote().setNote(note);

        response.setStatus(HttpServletResponse.SC_OK);
        return null;
    }

    public String restore() throws Exception {

        request.getSession().setAttribute("restoring", "true"); // tell CaseManagementView we're handling temp note
        request.setAttribute("restore", Boolean.valueOf(true));

        return edit();
    }

    public String cleanup() {
        String demoNo = this.getDemographicNo(request);
        String sessionFrmName = "caseManagementEntryForm" + demoNo;

        request.getSession().setAttribute(sessionFrmName, null);
        request.getSession().setAttribute("EctSessionBean", null);

        return null;
    }

    public String[] getIssueIds(List<Issue> issues) {
        String[] issueIds = new String[issues.size()];
        int idx = 0;
        for (Issue i : issues) {
            issueIds[idx] = String.valueOf(i.getId());
            ++idx;
        }
        return issueIds;
    }

    public String displayNotes() throws Exception {
        response.setContentType("text/html");
        doDisplayNotes(request, response.getWriter());
        return null;
    }

    public void doDisplayNotes(HttpServletRequest request, PrintWriter out) throws Exception {
        String ids = request.getParameter("notes2print");
        String[] noteIds;
        String textStr;

        ResourceBundle props = ResourceBundle.getBundle("oscarResources", request.getLocale());

        if (ids.length() > 0) noteIds = ids.split(",");
        else noteIds = (String[]) Array.newInstance(String.class, 0);

        out.println("<!DOCTYPE html><html><head><meta http-equiv='Content-Type' content='text/html; charset=UTF-8'></head><body>");

        for (int idx = 0; idx < noteIds.length; ++idx) {
            if (this.caseManagementMgr.getNote(noteIds[idx]).isLocked()) {
                textStr = this.caseManagementMgr.getNote(noteIds[idx]).getObservation_date().toString() + " " + this.caseManagementMgr.getNote(noteIds[idx]).getProviderName() + " " + props.getString("oscarEncounter.noteBrowser.msgNoteLocked");
            } else {

                textStr = this.caseManagementMgr.getNote(noteIds[idx]).getNote();
            }
            textStr = textStr.replaceAll("\n", "<br>");
            out.println(textStr);
            out.println("<br><br>");
        }

        out.println("</body></html>");
    }

    public String print() throws Exception {
        SimpleDateFormat headerFormat = new SimpleDateFormat("yyyy-MM-dd.hh.mm.ss");
        Date now = new Date();
        String headerDate = headerFormat.format(now);

        response.setContentType("application/pdf"); // octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"Encounter-" + headerDate + ".pdf\"");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Integer demographicNo = Integer.parseInt(getDemographicNo(request));
        String ids = request.getParameter("notes2print");

        String pStartDate = null;
        String pEndDate = null;
        String pType = null;

        Calendar cStartDate = null;
        Calendar cEndDate = null;

        pStartDate = request.getParameter("pStartDate");
        pEndDate = request.getParameter("pEndDate");
        pType = request.getParameter("pType");

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");

        if (pStartDate != null && !pStartDate.isEmpty()) {
            Date startDate = formatter.parse(pStartDate);
            cStartDate = Calendar.getInstance();
            cStartDate.setTime(startDate);
        }

        if (pEndDate != null && !pEndDate.isEmpty()) {
            Date endDate = formatter.parse(pEndDate);
            cEndDate = Calendar.getInstance();
            cEndDate.setTime(endDate);
        }

        boolean printAllNotes = "ALL_NOTES".equals(ids);
        String[] noteIds;
        if (ids.length() > 0) {
            noteIds = ids.split(",");
        } else {
            noteIds = new String[]{};
        }
        boolean printCPP = request.getParameter("printCPP").equalsIgnoreCase("true");
        boolean printRx = request.getParameter("printRx").equalsIgnoreCase("true");
        boolean printLabs = request.getParameter("printLabs") != null && request.getParameter("printLabs").equalsIgnoreCase("true");
        boolean printPreventions = request.getParameter("printPreventions") != null && request.getParameter("printPreventions").equalsIgnoreCase("true");
        boolean printAllergies = request.getParameter("printAllergies") != null && request.getParameter("printAllergies").equalsIgnoreCase("true");

        CaseManagementPrint cmp = new CaseManagementPrint();
        cmp.doPrint(loggedInInfo, demographicNo, printAllNotes, noteIds, printCPP, printRx, printLabs, printPreventions, printAllergies, (pType != null && "dates".equals(pType)) ? true : false, cStartDate, cEndDate, request, response.getOutputStream());

        return null;
    }

    public String getRefNo(String referal) {
        if (referal == null) return "";
        int start = referal.indexOf("<rdohip>");
        int end = referal.indexOf("</rdohip>");
        String ref = new String();

        if (start >= 0 && end >= 0) {
            String subreferal = referal.substring(start + 8, end);
            if (!"".equalsIgnoreCase(subreferal.trim())) {
                ref = subreferal;

            }
        }
        return ref;
    }

    /**
     * gets all the notes
     * if we have a key, and the note is locked, consider it
     * caisi - filter notes
     * grab the last one, where i am providers, and it's not signed
     *
     * @param request
     * @param demono
     * @param providerNo
     */
    public CaseManagementNote getLastSaved(HttpServletRequest request, String demono, String providerNo) {
        HttpSession session = request.getSession();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String programId = (String) session.getAttribute("case_program_id");
        Map unlockedNotesMap = this.getUnlockedNotesMap(request);
        return caseManagementMgr.getLastSaved(programId, demono, providerNo, unlockedNotesMap);
    }

    protected Map getUnlockedNotesMap(HttpServletRequest request) {
        Map<Long, Boolean> map = (Map<Long, Boolean>) request.getSession().getAttribute("unlockedNoteMap");
        if (map == null) {
            map = new HashMap<Long, Boolean>();
        }
        return map;
    }

    /*
     * Insert encounter reason for new note
     */
    protected void insertReason(HttpServletRequest request, CaseManagementNote note) {
        String encounterText = "";
        String apptDate = request.getParameter("appointmentDate");
        String reason = request.getParameter("reason");
        String appointmentNo = request.getParameter("appointmentNo");

        if (appointmentNo != null && !appointmentNo.isEmpty() && !"null".equals(appointmentNo)) {
            OscarAppointmentDao apptDao = SpringUtils.getBean(OscarAppointmentDao.class);
            Appointment appt = apptDao.find(Integer.parseInt(appointmentNo));
            if (appt != null) {
                reason = appt.getReason();
            }
        }
        if (reason == null) {
            reason = "";
        }

        if (apptDate == null || apptDate.equals("") || apptDate.equalsIgnoreCase("null")) {
            encounterText = "\n[" + UtilDateUtilities.DateToString(new Date(), "dd-MMM-yyyy", request.getLocale()) + " .: " + reason + "] \n";
        } else {
            apptDate = convertDateFmt(apptDate, request);
            encounterText = "\n[" + apptDate + " .: " + reason + "]\n";
        }

        note.setNote(encounterText);
        String encType = request.getParameter("encType");

        if (encType == null || encType.equals("")) {
            note.setEncounter_type("");
        } else {
            note.setEncounter_type(encType);
        }
    }

    protected String convertDateFmt(String strOldDate, HttpServletRequest request) {
        String strNewDate = new String();
        if (strOldDate != null && strOldDate.length() > 0) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
            try {

                Date tempDate = fmt.parse(strOldDate);
                strNewDate = new SimpleDateFormat("dd-MMM-yyyy", request.getLocale()).format(tempDate);

            } catch (ParseException ex) {
                MiscUtils.getLogger().error("Error", ex);
            }
        }

        return strNewDate;
    }

    protected CaseManagementCPP copyNote2cpp(CaseManagementCPP cpp, CaseManagementNote note) {
        Set<CaseManagementIssue> issueSet = note.getIssues();
        StringBuilder text = new StringBuilder();
        Date d = new Date();
        String separator = "\n-----[[" + d + "]]-----\n";
        for (CaseManagementIssue issue : issueSet) {
            String code = issue.getIssue().getCode();
            if (code.equals("OMeds")) {
                text.append(cpp.getFamilyHistory());
                text.append(separator);
                text.append(note.getNote());
                cpp.setFamilyHistory(text.toString());
                break;
            } else if (code.equals("SocHistory")) {
                text.append(cpp.getSocialHistory());
                text.append(separator);
                text.append(note.getNote());
                cpp.setSocialHistory(text.toString());
                break;
            } else if (code.equals("MedHistory")) {
                text.append(cpp.getMedicalHistory());
                text.append(separator);
                text.append(note.getNote());
                cpp.setMedicalHistory(text.toString());
                break;
            } else if (code.equals("Concerns")) {
                text.append(cpp.getOngoingConcerns());
                text.append(separator);
                text.append(note.getNote());
                cpp.setOngoingConcerns(text.toString());
                break;
            } else if (code.equals("Reminders")) {
                text.append(cpp.getReminders());
                text.append(separator);
                text.append(note.getNote());
                cpp.setReminders(text.toString());
                break;
            } else if (code.equals("FamHistory")) {
                text.append(cpp.getFamilyHistory());
                text.append(separator);
                text.append(note.getNote());
                cpp.setFamilyHistory(text.toString());
                break;
            } else if (code.equals("RiskFactors")) {
                text.append(cpp.getRiskFactors());
                text.append(separator);
                text.append(note.getNote());
                cpp.setRiskFactors(text.toString());
                break;
            }
        }

        return cpp;
    }

    /*
     * Retrieve CPP issuesIf not in session, load them
     */
    protected HashMap getCPPIssues(HttpServletRequest request, String providerNo) {
        HttpSession session = request.getSession();
        HashMap<String, Issue> issues = (HashMap<String, Issue>) session.getAttribute("CPPIssues");
        if (issues == null) {
            String[] issueCodes = {"SocHistory", "MedHistory", "Concerns", "Reminders", "FamHistory"};
            issues = new HashMap<String, Issue>();
            for (String issue : issueCodes) {
                List<Issue> i = caseManagementMgr.getIssueInfoByCode(providerNo, issue);
                issues.put(issue, i.get(0));
            }

            session.setAttribute("CPPIssues", issues);
        }
        return issues;
    }

    boolean filled(String s) {
        return (s != null && s.length() > 0);
    }

    public boolean haveIssue(Long issid, List allNotes) {
        Iterator itr = allNotes.iterator();
        while (itr.hasNext()) {
            CaseManagementNote note = (CaseManagementNote) itr.next();
            Set issues = note.getIssues();
            Iterator its = issues.iterator();
            while (its.hasNext()) {
                CaseManagementIssue iss = (CaseManagementIssue) its.next();
                if (iss.getId().intValue() == issid.intValue()) return true;
            }
        }
        return false;
    }

    private String partialDateFormat(String dateValue) {
        if (!filled(dateValue)) return null;

        dateValue = dateValue.trim();
        dateValue = dateValue.replace("/", "-");
        if (dateValue.length() == 4 && NumberUtils.isDigits(dateValue)) return PartialDate.YEARONLY;

        String[] dateParts = dateValue.split("-");
        if (dateParts.length == 2 && NumberUtils.isDigits(dateParts[0]) && NumberUtils.isDigits(dateParts[1])) {
            if (dateParts[0].length() == 4 && dateParts[1].length() >= 1 && dateParts[1].length() <= 2)
                return PartialDate.YEARMONTH;
        }
        if (dateParts.length == 3 && NumberUtils.isDigits(dateParts[0]) && NumberUtils.isDigits(dateParts[1]) && NumberUtils.isDigits(dateParts[2])) {
            if (dateParts[0].length() == 4 && dateParts[1].length() >= 1 && dateParts[1].length() <= 2 && dateParts[2].length() >= 1 && dateParts[2].length() <= 2)
                return ""; // full date
        }
        return null;
    }

    private String partialFullDate(String dateValue, String type) {
        if (type == null) return null;

        dateValue = dateValue.replace("/", "-");
        if (type.equals(PartialDate.YEARONLY)) return dateValue + "-01-01";
        if (type.equals(PartialDate.YEARMONTH)) return dateValue + "-01";
        return dateValue;
    }

    private boolean writePartialDate(String dateValue, CaseManagementNoteExt cme) {
        if (cme == null) return false;

        String type = partialDateFormat(dateValue);
        if (type == null) return false;

        cme.setValue(type);
        cme.setDateValue(partialFullDate(dateValue, type));
        return true;
    }

    private boolean nullEmptyEqual(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        return s1.trim().equals(s2.trim());
    }

    /*
     * 1) load existing note if possible
     * 1) update/save the note
     * 2) save/update link to the tickler (not sure yet)
     */
    public String ticklerSaveNote() {
        String strNote = request.getParameter("value");
        Date creationDate = new Date();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Provider loggedInProvider = loggedInInfo.getLoggedInProvider();
        String demographicNo = request.getParameter("demographicNo");
        String ticklerNo = request.getParameter("ticklerNo");
        String noteId = request.getParameter("noteId");
        String revision = "1";
        String history = strNote;
        String uuid = null;

        if (noteId != null && noteId.length() > 0 && !noteId.equals("0")) {
            CaseManagementNote existingNote = this.caseManagementNoteDao.getNote(Long.valueOf(noteId));

            revision = String.valueOf(Integer.valueOf(existingNote.getRevision()).intValue() + 1);
            history = strNote + "\n" + existingNote.getHistory();
            uuid = existingNote.getUuid();
        }

        CaseManagementNote cmn = new CaseManagementNote();
        cmn.setAppointmentNo(0);
        cmn.setArchived(false);
        cmn.setCreate_date(creationDate);
        cmn.setDemographic_no(demographicNo);
        cmn.setEncounter_type(EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue());
        cmn.setNote(strNote);
        cmn.setObservation_date(creationDate);
        cmn.setProviderNo(loggedInProvider.getProviderNo());
        cmn.setRevision(revision);
        cmn.setSigned(true);
        cmn.setSigning_provider_no(loggedInProvider.getProviderNo());
        cmn.setUpdate_date(creationDate);
        cmn.setHistory(history);
        cmn.setReporter_program_team("null");
        cmn.setUuid(uuid);

        String prog_no = new EctProgram(request.getSession()).getProgram(cmn.getProviderNo());
        cmn.setProgram_no(prog_no);

        determineNoteRole(cmn, loggedInProvider.getProviderNo(), demographicNo);

        caseManagementMgr.saveNoteSimple(cmn);

        //save link, so we know what tickler this note is linked to
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setNoteId(cmn.getId());
        link.setTableId(Long.parseLong(ticklerNo));
        link.setTableName(CaseManagementNoteLink.TICKLER);

        CaseManagementNoteLinkDAO caseManagementNoteLinkDao = (CaseManagementNoteLinkDAO) SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
        caseManagementNoteLinkDao.save(link);

        Issue issue = this.issueDao.findIssueByTypeAndCode("system", "TicklerNote");
        if (issue == null) {
            logger.warn("missing TicklerNote issue, please run all database updates");
            return null;
        }

        CaseManagementIssue cmi = caseManagementMgr.getIssueById(demographicNo.toString(), issue.getId().toString());

        if (cmi == null) {
            //save issue..this will make it a "cpp looking" issue in the eChart
            cmi = new CaseManagementIssue();
            cmi.setAcute(false);
            cmi.setCertain(false);
            cmi.setDemographic_no(Integer.valueOf(demographicNo));
            cmi.setIssue_id(issue.getId());
            cmi.setMajor(false);
            cmi.setProgram_id(Integer.parseInt(cmn.getProgram_no()));
            cmi.setResolved(false);
            cmi.setType(issue.getRole());
            cmi.setUpdate_date(creationDate);
            caseManagementIssueDao.saveIssue(cmi);
        }

        cmn.getIssues().add(cmi);

        caseManagementNoteDao.updateNote(cmn);
        return null;
    }

    public String ticklerGetNote() throws IOException {
        String ticklerNo = request.getParameter("ticklerNo");

        CaseManagementNoteLinkDAO caseManagementNoteLinkDao = (CaseManagementNoteLinkDAO) SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
        CaseManagementNoteLink link = caseManagementNoteLinkDao.getLastLinkByTableId(CaseManagementNoteLink.TICKLER, Long.valueOf(ticklerNo));
        ObjectNode json = (ObjectNode) objectMapper.readTree("{}");

        if (link != null) {
            Long noteId = link.getNoteId();

            CaseManagementNote note = this.caseManagementNoteDao.getNote(noteId);

            if (note != null) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");

                Map<String, Serializable> hashMap = new HashMap<String, Serializable>();
                hashMap.put("noteId", note.getId().toString());
                hashMap.put("note", note.getNote());
                hashMap.put("revision", note.getRevision());
                hashMap.put("obsDate", formatter.format(note.getObservation_date()));
                hashMap.put("editor", this.providerMgr.getProvider(note.getProviderNo()).getFormattedName());
                json = objectMapper.valueToTree(hashMap);
            }
        }
        response.getOutputStream().write(json.toString().getBytes());
        return null;
    }

    public static boolean determineNoteRole(CaseManagementNote note, String providerNo, String demographicNo) {
        // Determines what program & role to assign the note to
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProviderDefaultProgramDao defaultProgramDao = (ProviderDefaultProgramDao) SpringUtils.getBean(ProviderDefaultProgramDao.class);
        boolean programSet = false;

        if (note.getProgram_no() != null && note.getProgram_no().length() > 0 && !"null".equals(note.getProgram_no())) {
            try {
                Long programId = Long.valueOf(note.getProgram_no());
                if (programId > 0) {
                    ProgramProvider pp = programProviderDao.getProgramProvider(note.getProviderNo(), programId);
                    if (pp != null) {
                        note.setReporter_caisi_role(String.valueOf(pp.getRoleId()));
                        programSet = true;
                    }
                }
            } catch (NumberFormatException e) {
                // Invalid program number format, skip program provider lookup
            }
        }

        if (!programSet) {
            List<ProviderDefaultProgram> programs = defaultProgramDao.getProgramByProviderNo(providerNo);
            HashMap<Program, List<Secrole>> rolesForDemo = NotePermissions2Action.getAllProviderAccessibleRolesForDemo(providerNo, demographicNo);
            for (ProviderDefaultProgram pdp : programs) {
                for (Program p : rolesForDemo.keySet()) {
                    if (pdp.getProgramId() == p.getId().intValue()) {
                        List<ProgramProvider> programProviderList = programProviderDao.getProgramProviderByProviderProgramId(providerNo, (long) pdp.getProgramId());

                        note.setProgram_no("" + pdp.getProgramId());
                        note.setReporter_caisi_role("" + programProviderList.get(0).getRoleId());

                        programSet = true;
                    }
                }
            }
        }
        return programSet;
    }

    protected String relateIssueString = "Issues related to this note:";
    protected CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
    protected ClientImageManager clientImageMgr = SpringUtils.getBean(ClientImageManager.class);
    protected ProviderManager providerMgr = SpringUtils.getBean(ProviderManager.class);
    protected String getDemographicNo(HttpServletRequest request) {
        String demono = request.getParameter("demographicNo");
        if (demono == null || "".equals(demono)) {
            demono = (String) request.getAttribute("casemgmt_DemoNo");
        } else {
            request.setAttribute("casemgmt_DemoNo", demono);
        }
        return demono;
    }

    protected String getDemoName(String demoNo) {
        if (demoNo == null) {
            return "";
        }
        return caseManagementMgr.getDemoName(demoNo);
    }

    protected String getDemoSex(String demoNo) {
        if (demoNo == null) {
            return "";
        }
        return caseManagementMgr.getDemoGender(demoNo);
    }

    protected String getDemoAge(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoAge(demoNo);
    }

    protected String getDemoDOB(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoDOB(demoNo);
    }

    protected boolean inCaseIssue(Issue iss, List<CaseManagementIssue> issues) {
        Iterator<CaseManagementIssue> itr = issues.iterator();
        while (itr.hasNext()) {
            CaseManagementIssue cIss = itr.next();
            if (iss.getId().longValue() == cIss.getIssue_id())
                return true;
        }
        return false;
    }

    protected void SetChecked(List<CheckBoxBean> checkedlist, int id) {
        for (int i = 0; i < checkedlist.size(); i++) {
            if (checkedlist.get(i).getIssue().getId().intValue() == id) {
                checkedlist.get(i).setChecked("on");
                break;
            }
        }
    }

    protected boolean inCheckList(Long id, int[] list) {
        boolean ret = false;
        for (int i = 0; i < list.length; i++) {
            if (list[i] == id.intValue())
                ret = true;
        }
        return ret;
    }

    protected WebApplicationContext getSpringContext() {
        return WebApplicationContextUtils.getWebApplicationContext(ServletActionContext.getServletContext());
    }

    /* remove related issue list from note */
    protected String removeCurrentIssue(String noteString) {
        noteString = noteString.replaceAll("\r\n", "\n");
        noteString = noteString.replaceAll("\r", "\n");
        String rt = noteString;
        int index = noteString.indexOf("\n[" + relateIssueString);
        if (index >= 0) {
            String begString = noteString.substring(0, index);
            String endString = noteString.substring(index);
            endString = endString.substring(endString.indexOf("]\n") + 2);
            rt = begString + endString;
        }
        return rt;
    }

    /* remove signiature string */
    protected String removeSignature(String note) {
        note = note.replaceAll("\r\n", "\n");
        note = note.replaceAll("\r", "\n");
        String rt = note;
        String subStr = "\n[[";
        int indexb = note.lastIndexOf(subStr);
        if (indexb >= 0) {
            String subNote = note.substring(indexb);
            int indexe = subNote.indexOf("]]\n");
            if (indexe < 0)
                return rt;
            String begNote = note.substring(0, indexb);
            String endNote = subNote.substring(indexe + 3);
            // String midNote = subNote.substring(subStr.length());
            // String[] sp = midNote.split(" ");
            // midNote = "[" + sp[0] + " " + sp[1] + "]";
            rt = begNote + endNote;
        }
        return rt;
    }

    /* create related issue string */
    protected String createIssueString(Set<CaseManagementIssue> issuelist) {
        if (issuelist.isEmpty())
            return "";
        String rt = "\n[" + relateIssueString;
        Iterator<CaseManagementIssue> itr = issuelist.iterator();
        while (itr.hasNext()) {
            CaseManagementIssue iss = itr.next();
            rt = rt + "\n" + iss.getIssue().getDescription() + "\t\t\n";
            if (iss.isCertain())
                rt = rt + "certain" + "  ";
            else
                rt = rt + "uncertain" + "  ";
            if (iss.isAcute())
                rt = rt + "acute" + "  ";
            else
                rt = rt + "chronic" + "  ";
            if (iss.isMajor())
                rt = rt + "major" + "  ";
            else
                rt = rt + "not major" + "  ";
            if (iss.isResolved())
                rt = rt + "resolved";
            else
                rt = rt + "unresolved";
        }
        return rt + "]\n";
    }

    protected CaseManagementIssue newIssueToCIssue(String demographicNo, Issue iss, Integer programId) {
        CaseManagementIssue cIssue = new CaseManagementIssue();
        cIssue.setAcute(false);
        cIssue.setCertain(false);
        cIssue.setDemographic_no(Integer.valueOf(demographicNo));
        cIssue.setIssue_id(iss.getId().longValue());
        cIssue.setIssue(iss);
        cIssue.setMajor(false);
        cIssue.setNotes(new HashSet());
        cIssue.setResolved(false);
        String issueType = iss.getRole();
        cIssue.setType(issueType);
        cIssue.setUpdate_date(new Date());
        cIssue.setProgram_id(programId);
        // add it to database
        List<CaseManagementIssue> uList = new ArrayList<CaseManagementIssue>();
        uList.add(cIssue);
        caseManagementMgr.saveAndUpdateCaseIssues(uList);

        return cIssue;
    }

    /**
     * @param programId is optional, can be null for none.
     */
    protected CaseManagementIssue newIssueToCIssue(CaseManagementEntryFormBean cform, Issue iss, Integer programId) {
        return newIssueToCIssue(this.getDemographicNo(), iss, programId);
    }

    protected Map<Long, CaseManagementIssue> convertIssueListToMap(List<CaseManagementIssue> issueList) {
        Map<Long, CaseManagementIssue> map = new HashMap<Long, CaseManagementIssue>();
        for (Iterator<CaseManagementIssue> iter = issueList.iterator(); iter.hasNext(); ) {
            CaseManagementIssue issue = iter.next();
            map.put(issue.getIssue().getId(), issue);
        }
        return map;
    }

    //TODO: update access model
    public void setCPPMedicalHistory(CaseManagementCPP cpp, String providerNo, List accessRight) {

        if (caseManagementMgr.greaterEqualLevel(3, providerNo)) {
            String mHis = cpp.getMedicalHistory();
            if (mHis != null) {
                mHis = mHis.replaceAll("\r\n", "\n");
                mHis = mHis.replaceAll("\r", "\n");
            }
            List<CaseManagementIssue> allIssues = caseManagementMgr.getIssues(Integer.parseInt(cpp.getDemographic_no()));

            Iterator<CaseManagementIssue> itr = allIssues.iterator();
            while (itr.hasNext()) {
                CaseManagementIssue cis = itr.next();
                String issustring = cis.getIssue().getDescription();
                if (cis.isMajor() && cis.isResolved()) {
                    if (mHis != null && mHis.indexOf(issustring) < 0)
                        mHis = mHis + issustring + ";\n";
                } else {

                    if (mHis != null && mHis.indexOf(issustring) >= 0)
                        mHis = mHis.replaceAll(issustring + ";\n", "");
                }
            }
            if (mHis != null) {
                mHis = mHis.replaceAll("\r\n", "\n");
                mHis = mHis.replaceAll("\r", "\n");
            }
            cpp.setMedicalHistory(mHis);
        }
    }

    private Map<String, Object> mySessionMap;

    @Override
    public void setSession(Map<String, Object> session) {
        this.mySessionMap = session;
    }

    private void restoreFromSession() {
        if (this.caseNote == null) {
            this.caseNote = new CaseManagementNote();
        }
        
        if (demographicNo != null) {
            String sessionName = "caseManagementEntryForm" + demographicNo;
            CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) mySessionMap.get(sessionName);
            if (sessionFrm != null) {
                this.issueCheckList = sessionFrm.getIssueCheckList();
                this.newIssueCheckList = sessionFrm.getNewIssueCheckList();
            }
        }
    }

    private CaseManagementNote caseNote;
    private CaseManagementCPP cpp;
    private String demoNo;
    private String noteId;
        private List<CheckBoxBean> issueCheckList;
    private CheckIssueBoxBean[] newIssueCheckList;
    private List newIssueList;
    private String sign;
    private String includeIssue;
    private String method;
    private String showList;
    private String searString;
    private String deleteId;
    private String lineId;
    private String demographicNo;
    private String providerNo;
    private String programNo;
    private String demoName;
    private String caseNote_note;
    private String caseNote_history;
    private String chain;
    private String appointmentNo;
    private String appointmentDate;
    private String startTime;
    private String billRegion;
    private String apptProvider;
    private String providerview;

    private String observation_date;

    private boolean groupNote;
    private String[] groupNoteClientIds;
    private int groupNoteTotalAnonymous;

    private Integer hourOfEncounterTime;
    private Integer minuteOfEncounterTime;
    private Integer hourOfEncTransportationTime;
    private Integer minuteOfEncTransportationTime;

    private String reloadUrl;

    public String getObservation_date() {
        return this.observation_date;
    }

    public void setObservation_date(String date) {
        this.observation_date = date;
    }

    public String getCaseNote_history() {
        return caseNote_history;
    }

    public void setCaseNote_history(String caseNote_history) {
        this.caseNote_history = caseNote_history;
    }

    public String getDeleteId() {
        return deleteId;
    }

    public void setDeleteId(String deleteId) {
        this.deleteId = deleteId;
    }

    public String getIncludeIssue() {
        return includeIssue;
    }

    public void setIncludeIssue(String includeIssue) {
        this.includeIssue = includeIssue;
    }

    public List<CheckBoxBean> getIssueCheckList() {
        return issueCheckList;
    }

    public void setIssueCheckList(List<CheckBoxBean> issueCheckList) {
        // Only set if it's a valid list with persisted objects (not from Struts parameter binding)
        // During parameter binding, Struts creates NEW unpersisted objects which causes errors
        // We read form values directly from request parameters in issueChange() instead
        if (issueCheckList != null && !issueCheckList.isEmpty()) {
            try {
                CheckBoxBean firstItem = issueCheckList.get(0);
                // Check if the first item has a persisted issue (has an ID)
                if (firstItem != null && firstItem.getIssue() != null && firstItem.getIssue().getId() != null) {
                    this.issueCheckList = issueCheckList;
                }
            } catch (IndexOutOfBoundsException | NullPointerException e) {
                // Expected during Struts parameter binding with unpersisted objects
                logger.debug("Ignoring issueCheckList from Struts binding: {}", e.getMessage());
            }
        }
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public CheckIssueBoxBean[] getNewIssueCheckList() {
        return newIssueCheckList;
    }

    public void setNewIssueCheckList(CheckIssueBoxBean[] newIssueCheckList) {
        this.newIssueCheckList = newIssueCheckList;
    }

    public List getNewIssueList() {
        return newIssueList;
    }

    public void setNewIssueList(List newIssueList) {
        this.newIssueList = newIssueList;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getSearString() {
        return searString;
    }

    public void setSearString(String searString) {
        this.searString = searString;
    }

    public String getShowList() {
        return showList;
    }

    public void setShowList(String showList) {
        this.showList = showList;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public CaseManagementNote getCaseNote() {
        return caseNote;
    }

    public void setCaseNote(CaseManagementNote caseNote) {
        this.caseNote = caseNote;
    }

    public String getDemoNo() {
        return demoNo;
    }

    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getDemoName() {
        return demoName;
    }

    public void setDemoName(String demoName) {
        this.demoName = demoName;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getProgramNo() {
        return programNo;
    }

    public void setProgramNo(String programNo) {
        this.programNo = programNo;
    }


    public CaseManagementCPP getCpp() {
        return cpp;
    }

    public void setCpp(CaseManagementCPP cpp) {
        this.cpp = cpp;
    }

    public String getCaseNote_note() {
        this.caseNote_note = getCaseNote().getNote();
        return caseNote_note;
    }

    public void setCaseNote_note(String caseNote_note) {

        this.caseNote.setNote(caseNote_note);
        this.caseNote_note = caseNote_note;
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        this.chain = chain;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public String getAppointmentDate() {
        return this.appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getStart_time() {
        return this.startTime;
    }

    public void setStart_time(String startTime) {
        this.startTime = startTime;
    }

    public String getBillRegion() {
        return this.billRegion;
    }

    public void setBillRegion(String billRegion) {
        this.billRegion = billRegion;
    }

    public String getApptProvider() {
        return this.apptProvider;
    }

    public void setApptProvider(String apptProvider) {
        this.apptProvider = apptProvider;
    }

    public String getProviderview() {
        return this.providerview;
    }

    public void setProviderview(String providerview) {
        this.providerview = providerview;
    }

    public boolean isGroupNote() {
        return groupNote;
    }

    public void setGroupNote(boolean groupNote) {
        this.groupNote = groupNote;
    }

    public String[] getGroupNoteClientIds() {
        return groupNoteClientIds;
    }

    public void setGroupNoteClientIds(String[] groupNoteClientIds) {
        this.groupNoteClientIds = groupNoteClientIds;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getGroupNoteTotalAnonymous() {
        return groupNoteTotalAnonymous;
    }

    public void setGroupNoteTotalAnonymous(int groupNoteTotalAnonymous) {
        this.groupNoteTotalAnonymous = groupNoteTotalAnonymous;
    }

    public String getTrimmedNoteText() {
        return StringUtils.trimToNull(this.getCaseNote_note());
    }

    public Integer getHourOfEncounterTime() {
        return hourOfEncounterTime;
    }

    public void setHourOfEncounterTime(Integer hourOfEncounterTime) {
        this.hourOfEncounterTime = hourOfEncounterTime;
    }

    public Integer getMinuteOfEncounterTime() {
        return minuteOfEncounterTime;
    }

    public void setMinuteOfEncounterTime(Integer minuteOfEncounterTime) {
        this.minuteOfEncounterTime = minuteOfEncounterTime;
    }

    public Integer getHourOfEncTransportationTime() {
        return hourOfEncTransportationTime;
    }

    public void setHourOfEncTransportationTime(Integer hourOfEncTransportationTime) {
        this.hourOfEncTransportationTime = hourOfEncTransportationTime;
    }

    public Integer getMinuteOfEncTransportationTime() {
        return minuteOfEncTransportationTime;
    }

    public void setMinuteOfEncTransportationTime(Integer minuteOfEncTransportationTime) {
        this.minuteOfEncTransportationTime = minuteOfEncTransportationTime;
    }

    public String getReloadUrl() {
        return reloadUrl;
    }

    public void setReloadUrl(String reloadUrl) {
        this.reloadUrl = reloadUrl;
    }

    /**
     * Validates that a redirect URL is safe and points to an internal application URL.
     * This prevents open redirect vulnerabilities.
     * 
     * @param url The URL to validate
     * @param request The HTTP request object for context
     * @return true if the URL is safe for redirect, false otherwise
     */
    private boolean isValidInternalRedirect(String url, HttpServletRequest request) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        // Remove any leading/trailing whitespace
        url = url.trim();

        // Check for relative URLs (safe)
        if (url.startsWith("/") && !url.startsWith("//")) {
            // Ensure it doesn't contain protocol-relative URLs
            return !url.contains("://");
        }

        // Check if URL starts with the application's context path
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && url.startsWith(contextPath + "/")) {
            return true;
        }

        // Check for absolute URLs - must match the current server
        try {
            // Parse the URL to check if it's absolute
            if (url.contains("://")) {
                // Get the current server URL components
                String scheme = request.getScheme();
                String serverName = request.getServerName();
                int serverPort = request.getServerPort();
                
                // Build the expected server prefix
                StringBuilder expectedPrefix = new StringBuilder();
                expectedPrefix.append(scheme).append("://").append(serverName);
                
                // Add port if it's not the default for the scheme
                if ((scheme.equals("http") && serverPort != 80) || 
                    (scheme.equals("https") && serverPort != 443)) {
                    expectedPrefix.append(":").append(serverPort);
                }
                
                // Check if the URL starts with our server prefix
                if (url.startsWith(expectedPrefix.toString() + "/") ||
                    url.startsWith(expectedPrefix.toString() + contextPath + "/")) {
                    return true;
                }
                
                // Reject any other absolute URLs
                return false;
            }
        } catch (Exception e) {
            logger.error("Error validating redirect URL: " + url, e);
            return false;
        }

        // Default to rejecting unknown patterns
        return false;
    }

}
