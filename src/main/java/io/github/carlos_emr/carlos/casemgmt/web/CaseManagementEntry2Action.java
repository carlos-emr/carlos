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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.*;
import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.carlos.model.security.Secrole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.SessionAware;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.casemgmt.service.ChartPrintDateRange;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.casemgmt.web.CaseManagementViewAction.IssueDisplay;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Fixed date patterns used in this action; formatters are cached per-thread by {@link CachedDateFormats}. */
    private static final String DD_MMM_YYYY_HMM_PATTERN = "dd-MMM-yyyy H:mm";
    private static final String DD_MMM_YYYY_PATTERN = "dd-MMM-yyyy";
    private static final String HEADER_PATTERN = "yyyy-MM-dd.HH.mm.ss";
    private static final String YYYY_MM_DD_PATTERN = "yyyy-MM-dd";
    private static final String YYYY_MM_DD_HHMM_PATTERN = "yyyy-MM-dd HH:mm";
    /** Request attribute set when a save is refused for an unreadable encounter date (closeWithError.jsp). */
    static final String OBSERVATION_DATE_REJECTED = "observationDateRejected";
    private static final String CASE_MANAGEMENT_LIST_CHAIN = "list";
    @SuppressWarnings("java:S1075") // fixed allowlist target; making this configurable would weaken redirect hardening
    private static final String CASE_MANAGEMENT_LIST_REDIRECT_PATH = "/CaseManagementView?method=view";
    private static final int REMOVED_ISSUE_MESSAGE_OVERHEAD = 64;

    private static String appendRemovedIssueMessage(String noteText, Locale locale, ResourceBundle props, CharSequence issueNames) {
        String originalNote = StringUtils.defaultString(noteText);
        return new StringBuilder(originalNote.length() + issueNames.length() + REMOVED_ISSUE_MESSAGE_OVERHEAD)
                .append(originalNote)
                .append('\n')
                .append(CachedDateFormats.format(new Date(), DD_MMM_YYYY_PATTERN, locale))
                .append(' ')
                .append(props.getString("encounter.removedIssue.Msg"))
                .append(":\n")
                .append(issueNames)
                .toString();
    }

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(java.sql.Date.class, new JsDateSerializer());
        objectMapper.registerModule(module);
    }

    // Whitelist for the 'from' request parameter — only "casemgmt" is a known valid value
    private static final Set<String> ALLOWED_FROM_VALUES = Set.of("casemgmt");

    // Whitelist for the 'note_sort' request parameter — matches values used in sortNotes/sortNotes_old
    private static final Set<String> ALLOWED_NOTE_SORT_VALUES = Set.of(
            "observation_date_asc", "observation_date_desc",
            "providerName", "programName", "roleName", "update_date");

    private static final String ISSUE_LIST_AJAX_RESULT = "issueList_ajax";
    private static final Set<String> ALLOWED_CHAIN_RESULT_NAMES = Set.of("list", "view", ISSUE_LIST_AJAX_RESULT);
    private static final Set<String> NOTE_WRITE_METHODS = Set.of(
            "issueNoteSave", "issueNoteSaveJson", "ajaxsave", "save", "saveAndExit", "ticklerSaveNote");

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.error("Illegal operation! Empty user session");
            return null;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required security object (_demographic)");
        }

        String operation = request.getParameter("method") != null ? request.getParameter("method")
                : (String) request.getAttribute("method");
        if (NOTE_WRITE_METHODS.contains(operation == null ? "" : operation) && !requireNoteWriteAccess()) {
            return NONE;
        }
        if (Set.of("makeIssue", "issueAdd", "issueDelete", "issueChange",
                "submitChangeDiagnosis", "ajaxChangeDiagnosis").contains(operation == null ? "" : operation)) {
            if (!requireIssueWriteAccess()) return NONE;
        }
        restoreFromSession();

        String result = dispatchRequest();
        if ("view".equals(result)) {
            prepareBillingUrl(getDemographicNo(request));
        }
        return result;
    }

    /** Refuse note writes before restoring or mutating a patient-scoped session form. */
    private boolean requireNoteWriteAccess() {
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return false;
        }
        String patient;
        try {
            patient = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (login == null || !securityInfoManager.hasPrivilege(login, "_eChart", "w", patient)
                || !securityInfoManager.hasPrivilege(login, "_casemgmt.notes", "w", patient)
                || !securityInfoManager.isAllowedAccessToPatientRecord(login, Integer.valueOf(patient))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        request.setAttribute("casemgmt_DemoNo", patient);
        return true;
    }

    /** Match the issue editor's permission and enforce access before session or database writes. */
    private boolean requireIssueWriteAccess() {
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "POST");
            return false;
        }
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = getDemographicNo(request);
        Integer patient;
        try {
            patient = Integer.valueOf(demographicNo);
        } catch (NumberFormatException invalid) {
            throw new SecurityException("missing required patient scope for issue write");
        }
        if (user == null || patient <= 0
                || !securityInfoManager.hasPrivilege(user, "_demographic", "r", demographicNo)
                || !securityInfoManager.hasPrivilege(user, "_casemgmt.issues", "w", demographicNo)
                || !securityInfoManager.isAllowedAccessToPatientRecord(user, patient)) {
            throw new SecurityException("missing required security object (_casemgmt.issues w)");
        }
        // Read only this session's existing form after numeric patient validation and record authorization;
        // the note/issue owners are checked below before any mutation. No request value is stored here.
        // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        CaseManagementEntryFormBean form = (CaseManagementEntryFormBean) request.getSession().getAttribute(ChartProgramContext.formKey(request, demographicNo));
        if (form != null) {
            if (form.getCaseNote() != null && form.getCaseNote().getDemographic_no() != null
                    && !demographicNo.equals(form.getCaseNote().getDemographic_no())) {
                throw new SecurityException("missing required patient scope for issue note");
            }
            if (form.getIssueCheckList() != null) {
                for (CheckBoxBean entry : form.getIssueCheckList()) {
                    if (entry == null || entry.getIssue() == null
                            || !patient.equals(entry.getIssue().getDemographic_no())) {
                        throw new SecurityException("missing required patient scope for clinical issue");
                    }
                }
            }
        }
        return true;
    }

    private String dispatchRequest() throws Exception {
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

    static Integer parseEncounterMessageId(String value) {
        if (StringUtils.isBlank(value)) return null;
        int id = Integer.parseInt(value);
        if (id <= 0) throw new NumberFormatException("Message ID must be positive");
        return id;
    }

    /**
     * Prepares the chart page, loading a nonblank msgId for the current demographic into
     * the encounterMessage request attribute after access/link checks.
     * @return chart layout result, or NONE after a controlled invalid/missing-message response
     * @throws java.io.IOException if sending an error response fails
     */
    public String setUpMainEncounter() throws java.io.IOException {
        String demono = getDemographicNo(request);
        Integer messageId;
        try {
            messageId = parseEncounterMessageId(request.getParameter("msgId"));
        } catch (NumberFormatException _) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid encounter message ID");
            return NONE;
        }
        if (messageId != null) {
            try {
                String text = SpringUtils.getBean(io.github.carlos_emr.carlos.messenger.service.MessageEncounterService.class)
                        .load(LoggedInInfo.getLoggedInInfoFromSession(request), messageId, Integer.parseInt(demono));
                request.setAttribute("encounterMessage", text);
            } catch (IllegalArgumentException _) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Encounter message no longer exists");
                return NONE;
            }
        }
        logger.debug("client Image?");

        //get client image
        ClientImage img = clientImageMgr.getClientImage(Integer.parseInt(demono));
        if (img != null) {
            request.setAttribute("image_exists", "true");
            request.setAttribute("demographicNo", demono);
        }
        return "setUpMainEncounterPage";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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

        String programIdString = ChartProgramContext.resolve(request);
        Integer programId = null;
        try {
            programId = Integer.parseInt(programIdString);
        } catch (Exception e) {
            logger.warn("Unable to parse encounter program identifier ({})", e.getClass().getSimpleName());
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
            request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
        }

        prepareBillingUrl(demono);

        /* remove this patient's remembered echart string; other open charts keep theirs */
        session.removeAttribute(ChartSessionKeys.lastSavedNoteText(demono)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- ChartSessionKeys accepts only a digits-only patient number

        logger.debug("Get Issues and filter them");
        current = System.currentTimeMillis();
        logger.debug("Get Issues and filter them " + String.valueOf(current - start));
        start = current;

        cform.setDemoNo(demono);
        CaseManagementNote note = null;

        String nId = request.getParameter("noteId");
        int requestedNoteId = 0;
        if (StringUtils.isNotBlank(nId) && !"null".equalsIgnoreCase(nId)) {
            try {
                requestedNoteId = Integer.parseInt(nId);
            } catch (NumberFormatException invalidNoteId) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
        }
        String forceNote = request.getParameter("forceNote");
        if (forceNote == null) forceNote = "false";

        logger.debug("NoteId {}", LogSafe.sanitize(nId));

        String maxTmpSave = CarlosProperties.getInstance().getProperty("maxTmpSave", "off");
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
            request.setAttribute("newNote", "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
            request.setAttribute("issueStatusChanged", "false"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
            request.setAttribute("newNoteIdx", request.getParameter("newNoteIdx"));

            note = new CaseManagementNote();
            note.setProviderNo(providerNo);
            Provider prov = new Provider();
            prov.setProviderNo(providerNo);
            note.setProvider(prov);
            note.setDemographic_no(demono);

            if (!CarlosProperties.getInstance().isPropertyActive("encounter.empty_new_note")) {
                this.insertReason(request, note);
            } else {
                note.setNote("");
                note.setEncounter_type("");
            }

            EctSessionBean bean = EncounterPatientContext.findEncounter(request, demono);
            String encType = request.getParameter("encType");

            if (encType == null || encType.equals("")) {
                note.setEncounter_type("");
            } else {
                note.setEncounter_type(encType);
            }
            if (StringUtils.isBlank(encType) && bean != null && StringUtils.isNotBlank(bean.encType)) {
                note.setEncounter_type(bean.encType);
            }

            resetTemp(providerNo, demono, programIdString);

        }
        // get the last temp note?
        else if (tmpsavenote != null && !forceNote.equals("true")) {
            logger.debug("tempsavenote is NOT NULL");
            CaseManagementNote original = tmpsavenote.getNoteId() != null && tmpsavenote.getNoteId() > 0
                    ? caseManagementMgr.getNote(String.valueOf(tmpsavenote.getNoteId())) : null;
            if (original != null && !authorizeEditorNoteReference(loggedInInfo, demono, programIdString, original)) return NONE;
            note = restoreDraftNote(original, tmpsavenote.getNote(), providerNo, demono);
            if (original == null) {
                // Keep the draft text even if its original note no longer exists. Render it
                // as a new note so the save and lock paths do not reuse the orphaned ID.
                request.setAttribute("newNote", "true");
                request.setAttribute("issueStatusChanged", "false");
                request.setAttribute("noteId", "0");
            } else {
                request.setAttribute("newNote", "false");
                request.setAttribute("noteId", String.valueOf(note.getId()));
            }

            logger.debug("Restored temp note id={} noteLength={}",
                    LogSafe.sanitize(String.valueOf(note.getId())),
                    note.getNote() == null ? 0 : note.getNote().length());

        }
        // get an existing non-temp note?
        else if (requestedNoteId > 0) {
            logger.debug("Using nId {} to fetch note", LogSafe.sanitize(nId));
            // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- value is hardcoded literal "false", not user input
            request.setAttribute("newNote", "false");
            note = caseManagementMgr.getNote(nId);
            if (note == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
            if (!authorizeEditorNoteReference(loggedInInfo, demono, programIdString, note)) return NONE;

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
                request.setAttribute("newNote", "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                request.setAttribute("issueStatusChanged", "false"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
                note = this.makeNewNote(providerNo, demono, request);
            } else {
                request.setAttribute("newNote", "false"); // should be able to get getLatSaved from the manager now // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
            }
        }
        current = System.currentTimeMillis();
        logger.debug("Get note to edit " + String.valueOf(current - start));
        start = current;

        /*
         * do the restore if(restore != null && restore.booleanValue() == true) { String tmpsavenote = this.caseManagementMgr.restoreTmpSave(providerNo,demono,programId); if(tmpsavenote != null) { note.setNote(tmpsavenote); } }
         */
        logger.debug("Set Encounter Type: {}", LogSafe.sanitize(note.getEncounter_type()));
        logger.debug("Fetched Note {}", LogSafe.sanitize(String.valueOf(note.getId())));

        logger.debug("Populate Note with editors");
        this.caseManagementMgr.getEditors(note);
        current = System.currentTimeMillis();
        logger.debug("Populate Note with editors " + String.valueOf(current - start));
        start = current;

        // put the new/retrieved not in the form object for rendering on page
        cform.setCaseNote(note);
        logger.debug("Loaded note into form id={} noteLength={}",
                LogSafe.sanitize(String.valueOf(note.getId())),
                cform.getCaseNote_note() == null ? 0 : cform.getCaseNote_note().length());
        /* set issue checked list */

        // get issues for current demographic, based on providers rights
        Boolean useNewCaseMgmt = Boolean.valueOf((String) session.getAttribute("newCaseManagement"));

                List<CheckBoxBean> checkedList = null;
        if (useNewCaseMgmt) {

            CaseManagementView2Action caseManagementViewAction = new CaseManagementView2Action();
            ArrayList<CheckBoxBean> checkBoxBeanList = new ArrayList<CheckBoxBean>();
            caseManagementViewAction.addLocalIssues(providerNo, checkBoxBeanList, demographicNo, false, programId);

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

        String chain = request.getParameter("chain");

        current = System.currentTimeMillis();
        logger.debug("The End of Edit " + String.valueOf(current - beginning));
        start = current;

        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.EDIT, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demono, note.getAuditString());

        //check to see if someone else is editing note in this chart
        String ipAddress = request.getRemoteAddr();
        CasemgmtNoteLock casemgmtNoteLock;
        Long note_id = note.getId() != null && note.getId() >= 0 ? note.getId() : 0L;
        casemgmtNoteLock = isNoteEdited(note_id, demographicNo, providerNo, ipAddress, request.getSession().getId());

        if (casemgmtNoteLock.isLocked()) {
            note = makeNewNote(providerNo, demono, request);
            cform.setCaseNote(note);
        }

        session.setAttribute("casemgmtNoteLock" + demono, casemgmtNoteLock); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep

        String frmName = ChartProgramContext.formKey(request, demono);
        logger.debug("Stored note form id={} noteLength={}",
                LogSafe.sanitize(String.valueOf(note.getId())),
                cform.getCaseNote_note() == null ? 0 : cform.getCaseNote_note().length());
        mySessionMap.put(frmName, cform);

        String fwd, finalFwd = null;
        String chainResult = sanitizeChainResultName(chain);
        if (chainResult != null) {
            fwd = chainResult;
        } else {
            if (StringUtils.isNotBlank(chain)) {
                logger.warn("Rejected invalid chain result target");
            }
            String ajax = request.getParameter("ajax");
            if (ajax != null && ajax.equalsIgnoreCase("true")) {
                fwd = ISSUE_LIST_AJAX_RESULT;
            } else {
                fwd = "view";
            }
        }

        // A one-use handoff to this patient's next note list. Keyed by patient so another chart's
        // note list neither consumes nor applies it (issue #4673).
        setOrRemove(session, ChartSessionKeys.noteFilterHandoff("note_sort", demono), sanitizeNoteSortParam(request.getParameter("note_sort")));
        setOrRemove(session, ChartSessionKeys.noteFilterHandoff("filter_roles", demono), sanitizeIdFilterArray(request.getParameterValues("filter_roles")));
        setOrRemove(session, ChartSessionKeys.noteFilterHandoff("filter_provider", demono), sanitizeIdFilterArray(request.getParameterValues("filter_providers")));
        setOrRemove(session, ChartSessionKeys.noteFilterHandoff("issues", demono), sanitizeIdFilterArray(request.getParameterValues("issues")));

        return fwd;
    }

    /** Authorizes a referenced note before its history, editor model or links can be changed. */
    private boolean authorizeEditorNoteReference(LoggedInInfo login, String patient, String program, CaseManagementNote note) {
        if (!patient.equals(note.getDemographic_no())
                || !securityInfoManager.hasPrivilege(login, "_eChart", "r", patient)
                || !securityInfoManager.hasPrivilege(login, "_casemgmt.notes", "r", patient)
                || !securityInfoManager.isAllowedAccessToPatientRecord(login, Integer.parseInt(patient))
                || (!caseManagementMgr.isClientInProgramDomain(login.getLoggedInProviderNo(), patient)
                    && !caseManagementMgr.isClientReferredInProgramDomain(login.getLoggedInProviderNo(), patient))
                || (program != null && !"0".equals(program) && caseManagementMgr.filterNotes(login,
                    login.getLoggedInProviderNo(), List.of(note), program).isEmpty())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * Returns {@code value} only when it is in the {@link #ALLOWED_FROM_VALUES} whitelist;
     * otherwise returns {@code null}.  Prevents trust-boundary violations (CWE-501) when
     * the raw "from" request parameter is stored as a request/session attribute.
     */
    static String sanitizeFromParam(String value) {
        if (value == null) return null;
        return ALLOWED_FROM_VALUES.contains(value) ? value : null;
    }

    /**
     * Returns {@code value} only when it is in the {@link #ALLOWED_NOTE_SORT_VALUES} whitelist;
     * otherwise returns {@code null}.  Prevents trust-boundary violations (CWE-501) when
     * the raw "note_sort" request parameter is stored in the HttpSession.
     */
    static String sanitizeNoteSortParam(String value) {
        if (value == null) return null;
        return ALLOWED_NOTE_SORT_VALUES.contains(value) ? value : null;
    }

    /**
     * Filters an array of filter-ID strings so that only well-formed values survive.
     * <p>Accepted tokens: {@code "a"} (all), {@code "n"} (none), and digit-only strings
     * representing database primary-key IDs.  Any other value is silently dropped.
     * Returns an empty array (not {@code null}) if all values are rejected, which causes
     * {@link #setOrRemove} to clear the session attribute rather than leave stale data.
     *
     * @param values raw parameter values from the HTTP request; may be {@code null}
     * @return sanitized array, or {@code null} when the input is {@code null}
     */
    static String[] sanitizeIdFilterArray(String[] values) {
        if (values == null) return null;
        List<String> sanitized = new ArrayList<>();
        for (String v : values) {
            if (v != null && (v.equals("a") || v.equals("n") || v.matches("\\d+"))) {
                sanitized.add(v);
            }
        }
        return sanitized.toArray(new String[0]);
    }

    /**
     * Resolves the reporter program team identifier for a case-management note.
     *
     * @param admissionManager AdmissionManager used to load the admission for the current program and demographic
     * @param programNo String program identifier associated with the note; may be null
     * @param demographicNo String demographic identifier associated with the note; may be null
     * @return String team identifier when an admission with a non-null team ID exists; otherwise "0"
     */
    static String resolveReporterProgramTeamId(AdmissionManager admissionManager, String programNo, String demographicNo) {
        if (!NumberUtils.isParsable(programNo) || !NumberUtils.isParsable(demographicNo)) {
            return "0";
        }

        int parsedProgramNo = Integer.parseInt(programNo);
        int parsedDemographicNo = Integer.parseInt(demographicNo);
        if (parsedProgramNo <= 0 || parsedDemographicNo <= 0) {
            return "0";
        }

        try {
            Admission admission = admissionManager.getAdmission(String.valueOf(parsedProgramNo), parsedDemographicNo);
            if (admission == null || admission.getTeamId() == null) {
                return "0";
            }
            return String.valueOf(admission.getTeamId());
        } catch (Exception e) {
            logger.error("Error resolving reporter program team admission lookup (programNoPresent={}, demographicNoPresent={}, exceptionType={})",
                    StringUtils.isNotBlank(programNo), StringUtils.isNotBlank(demographicNo),
                    e.getClass().getSimpleName(), e);
            return "0";
        }
    }

    private void setOrRemove(HttpSession session, String key, String value) {
        if (value != null) {
            session.setAttribute(key, value); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }
    }

    private void setOrRemove(HttpSession session, String key, String[] values) {
        if (values == null) {
            return;
        } else if (values.length > 0) {
            session.setAttribute(key, values); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
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

        if (!CarlosProperties.getInstance().isPropertyActive("encounter.empty_new_note")) {
            this.insertReason(request, note);
        } else {
            note.setNote("");
            note.setEncounter_type("");
        }
        EctSessionBean bean = EncounterPatientContext.findEncounter(request, demographicNo);
        String encType = request.getParameter("encType");

        if (encType == null || encType.equals("")) {
            note.setEncounter_type("");
        } else {
            note.setEncounter_type(encType);
        }
        if (StringUtils.isBlank(encType) && bean != null && StringUtils.isNotBlank(bean.encType)) {
            note.setEncounter_type(bean.encType);
        }
        return note;
    }

    /** Restore draft content without discarding text when the referenced note is missing. */
    static CaseManagementNote restoreDraftNote(CaseManagementNote original, String draftText,
                                               String providerNo, String demographicNo) {
        CaseManagementNote restored = original;
        if (restored == null) {
            restored = new CaseManagementNote();
            restored.setProviderNo(providerNo);
            Provider provider = new Provider();
            provider.setProviderNo(providerNo);
            restored.setProvider(provider);
            restored.setDemographic_no(demographicNo);
        } else if (!Objects.equals(demographicNo, restored.getDemographic_no())) {
            throw new SecurityException("Draft references a note outside this patient chart");
        } else {
            restored = original.copyForRevision();
            restored.setId(original.getId());
        }
        restored.setNote(draftText == null ? "" : draftText);
        return restored;
    }

    static synchronized CasemgmtNoteLock isNoteEdited(Long note_id, Integer demographicNo, String providerNo, String ipAddress, String sessionId) {
        CasemgmtNoteLockDao casemgmtNoteLockDao = SpringUtils.getBean(CasemgmtNoteLockDao.class);
        CasemgmtNoteLock casemgmtNoteLock = casemgmtNoteLockDao.findByNoteDemo(demographicNo, note_id);

        //We determine the lock status of the note
        if (casemgmtNoteLock != null) {
            //it has a lock; check if lock is same user
            if (Objects.equals(casemgmtNoteLock.getProviderNo(), providerNo)) {
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

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String isNoteEdited() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        String ipAddress = request.getRemoteAddr();
        String sessionId = request.getSession().getId();

        logger.debug("WEB isNoteEdited CALLED");
        CasemgmtNoteLock casemgmtNoteLock = isNoteEdited(Long.parseLong(noteId), Integer.parseInt(demoNo), providerNo, ipAddress, sessionId);

        String ret = "unlocked";
        if (casemgmtNoteLock.isLocked()) {
            ret = "other";
        } else if (casemgmtNoteLock.isLockedBySameUser()) {
            ret = "user";
        } else {
            request.getSession().setAttribute("casemgmtNoteLock" + demoNo, casemgmtNoteLock); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }

        Map<String, String> jsonMap = new HashMap<String, String>();
        jsonMap.put("isNoteEdited", ret);
        ObjectNode json = objectMapper.valueToTree(jsonMap);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
        return null;
    }

    //Change IP Address and Session Id of note lock
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String updateNoteLock() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            logger.error("updateNoteLock: empty user session");
            return null;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required security object (_demographic)");
        }

        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        HttpSession session = request.getSession();

        CasemgmtNoteLock casemgmtNoteLock = null;
        if (noteId != null && !"null".equalsIgnoreCase(noteId)) {
            casemgmtNoteLock = casemgmtNoteLockDao.findByNoteDemo(Integer.parseInt(demoNo), Long.parseLong(noteId));
        } else {
            casemgmtNoteLock = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demoNo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of session-scoped lock keyed by demographicNo; authz re-checked downstream
        }

        if (casemgmtNoteLock == null) {
            logger.warn("updateNoteLock: lock not found - lock may have been released");
            return null;
        }

        casemgmtNoteLock.setIpAddress(request.getRemoteAddr());
        String currentSessionId = request.getSession().getId();
        casemgmtNoteLock.setSessionId(currentSessionId);
        logger.debug("UPDATING LOCK DEMO {} LOCK IP {}", LogSafe.sanitize(demoNo), LogSafe.sanitize(casemgmtNoteLock.getIpAddress()));
        casemgmtNoteLockDao.merge(casemgmtNoteLock);

        session.setAttribute("casemgmtNoteLock" + demoNo, casemgmtNoteLock); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep

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
            logger.warn("Warning ({})", e.getClass().getSimpleName());
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = {"XSS_SERVLET", "IMPROPER_UNICODE"}, justification = "XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink. case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String issueNoteSaveJson() throws Exception {
        if (!requireNoteWriteAccess()) return NONE;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String strNote = request.getParameter("value");
        String appointmentNo = request.getParameter("appointment_no");
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String noteId = request.getParameter("noteId");
        String demographicNo = getDemographicNo(request);
        String issueCode = request.getParameter("issue_id");
        String issueAlphaCode = request.getParameter("issue_code");
        String archived = request.getParameter("archived");

        if (!hasNoteLock(demographicNo)) {
            logger.debug("issueNoteSaveJson rejected: no valid lock for demographic {}", LogSafe.sanitize(demographicNo));
            return null;
        }

        Date noteDate = new Date();

        strNote = StringUtils.trimToNull(strNote);
        if ((archived == null || !archived.equalsIgnoreCase("true")) && (strNote == null || strNote.equals("")))
            return null;

        CaseManagementNote note = new CaseManagementNote();
        if (!noteId.equals("0")) {
            note = this.caseManagementMgr.getNote(noteId);
            if (note == null || !Objects.equals(demographicNo, note.getDemographic_no())) {
                throw new SecurityException("JSON note save references a note outside this patient chart");
            }
            if ((archived == null || !archived.equalsIgnoreCase("true")) && (request.getParameter("sign") == null || !request.getParameter("sign").equalsIgnoreCase("true")) && note.getNote().equalsIgnoreCase(strNote))
                return null;

            CaseManagementNote draft = note.copyForRevision();
            draft.setId(note.getId());
            note = draft;
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

                cIssue = prepareCaseIssue(demographicNo, issue, 10016);
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
                Date now = new Date();
                ResourceBundle props = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

                ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
                String providerName = providerDao.getProviderName(providerNo);

                String signature = "[" + props.getString("encounter.class.EctSaveEncounterAction.msgSigned") + " " + CachedDateFormats.format(now, DD_MMM_YYYY_HMM_PATTERN, Locale.ENGLISH) + " " + props.getString("encounter.class.EctSaveEncounterAction.msgSigBy") + " " + providerName + "]";
                note.setNote(note.getNote() + "\n" + signature);
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
        HashMap<Program, List<Secrole>> rolesForDemo = getAllProviderAccessibleRolesForDemo(providerNo, demographicNo);
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

        try {
            note = caseManagementMgr.saveNoteRevision(cpp, note, providerNo, null, null, null).note();
        } catch (RuntimeException e) {
            if (!StaleNoteRevisionException.isConflict(e)) throw e;
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return null;
        }

        // The appointment must not be signed when the note revision was rejected.
        if ("true".equalsIgnoreCase(request.getParameter("sign"))
                && "true".equalsIgnoreCase(request.getParameter("signAndExit"))) {
            OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
            try {
                Appointment appointment = appointmentDao.find(Integer.parseInt(appointmentNo));
                if (appointment != null
                        && Objects.equals(String.valueOf(appointment.getDemographicNo()), demographicNo)) {
                    ApptStatusData statusData = new ApptStatusData();
                    statusData.setApptStatus(appointment.getStatus());
                    // Archive only after the note succeeds, before changing the appointment.
                    SpringUtils.getBean(AppointmentArchiveDao.class).archiveAppointment(appointment);
                    appointment.setStatus(statusData.signStatus());
                    appointmentDao.merge(appointment);
                }
            } catch (Exception e) {
                logger.error("Unable to update encounter appointment ({})", e.getClass().getSimpleName());
            }
        }

        HashMap<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("id", note.getId());
        ObjectNode json = objectMapper.valueToTree(hashMap);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());

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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String issueNoteSave() throws Exception {
        if (!requireNoteWriteAccess()) return NONE;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String strNote = request.getParameter("value");
        String appointmentNo = request.getParameter("appointmentNo");
        HttpSession session = request.getSession();

        String[] extNames = {"startdate", "resolutiondate", "proceduredate", "ageatonset", "problemstatus", "treatment", "exposuredetail", "relationship", "lifestage", "hidecpp", "problemdescription", "procedure"};
        String[] extKeys = {CaseManagementNoteExt.STARTDATE, CaseManagementNoteExt.RESOLUTIONDATE, CaseManagementNoteExt.PROCEDUREDATE, CaseManagementNoteExt.AGEATONSET, CaseManagementNoteExt.PROBLEMSTATUS, CaseManagementNoteExt.TREATMENT, CaseManagementNoteExt.EXPOSUREDETAIL, CaseManagementNoteExt.RELATIONSHIP, CaseManagementNoteExt.LIFESTAGE, CaseManagementNoteExt.HIDECPP, CaseManagementNoteExt.PROBLEMDESC, CaseManagementNoteExt.PROCEDURE};

        logger.debug("Saving note");
        strNote = StringUtils.trimToNull(strNote);
        if (strNote == null || strNote.equals("")) return null;

        String userName = loggedInInfo.getLoggedInProvider().getFullName();

        String demo = getDemographicNo(request);

        if (!hasNoteLock(demo)) {
            logger.debug("issueNoteSave rejected: no valid lock for demographic {}", demo);
            return "windowCloseError";
        }

        String noteId = request.getParameter("noteId");
        logger.debug("SAVING NOTE {}", LogSafe.sanitize(noteId));
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

            // EVERY ROW A KEY HOLDS, NOT JUST THE FIRST. getExtByNote() orders id desc, so the
            // first match is the NEWEST row, and this loop used to stop there. A note that already
            // carries several rows for one key can hold different values in them, and
            // NotesService.getNote() reads to the end of that list, so it reports the OLDEST.
            // Submitting the value the newest row already held therefore looked like "nothing
            // changed": the save returned early below, the reconciliation further down never ran,
            // and the chart went on showing the older value. Walking every row makes disagreement
            // between them a change in its own right.
            extNames:
            for (int i = 0; i < extNames.length; i++) {
                boolean extKeyMatched = false;

                String submitted = request.getParameter(extNames[i]);
                // Resolved once per key: the date fields are normalised for comparison, and
                // re-normalising an already-normalised value on a second row would compare the
                // wrong thing.
                String comparableDate = i <= 2 ? partialFullDate(submitted, partialDateFormat(submitted)) : null;
                String comparableValue = i <= 2 ? partialDateFormat(submitted) : submitted;
                // A date the write loop below would discard cannot change anything. A null format
                // is an empty or malformed submission, and writePartialDate() skips exactly that,
                // so counting it as a change would re-save an otherwise unchanged note.
                if (i <= 2 && comparableValue == null) continue;
                for (CaseManagementNoteExt cme : cmeList) {
                    if (!cme.getKeyVal().equals(extKeys[i])) continue;

                    if (i <= 2 && !nullEmptyEqual(cme.getDateValueStr(), comparableDate)) {
                        extChanged = true;
                        break extNames;
                    }
                    if (!nullEmptyEqual(cme.getValue(), comparableValue)) {
                        extChanged = true;
                        break extNames;
                    }
                    extKeyMatched = true;
                }
                // THE SUBMITTED VALUE, NOT THE COMPARISON FORM. partialDateFormat() returns the
                // EMPTY STRING for a full YYYY-MM-DD date -- that is its marker for "full
                // precision" -- so comparableValue is "" exactly when the clinician typed a
                // complete date. Testing it here would read a newly added full date as nothing
                // added, and the early return below would then discard it. comparableValue exists
                // only to compare against a stored row's precision marker.
                if (filled(submitted) && !extKeyMatched) { // new ext value(s) added
                    extChanged = true;
                    break extNames;
                }
            }

            // if note has not changed don't save
            note = this.caseManagementMgr.getNote(noteId);
            // THE NOTE MUST BE IN THE CHART WHOSE LOCK WAS CHECKED. noteId comes from the request,
            // and everything below rewrites that note -- its text, signer, archived flag and every
            // extension row -- so a note from another patient's chart is refused here rather than
            // trusted because this patient's lock is held.
            if (note == null || !Objects.equals(demo, note.getDemographic_no())) {
                throw new SecurityException("CPP note save references a note outside this patient chart");
            }
            if (strNote.equals(note.getNote()) && !issueChange.equals("true") && !extChanged && (archived == null || archived.equalsIgnoreCase("false")))
                return null;
            // Keep submitted edits off a potentially managed historical row until the service
            // validates the revision and commits every part of the CPP save together.
            CaseManagementNote draft = note.copyForRevision();
            draft.setId(note.getId());
            note = draft;
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

        logger.debug("Note archived {}", LogSafe.sanitize(String.valueOf(note.isArchived())));
        String programId = ChartProgramContext.resolve(request);
        note.setProgram_no(programId);

        WebApplicationContext ctx = this.getSpringContext();

        ProgramManager programManager = (ProgramManager) ctx.getBean(ProgramManager.class);
        AdmissionManager admissionManager = (AdmissionManager) ctx.getBean(AdmissionManager.class);

        String role = null;
        try {
            role = String.valueOf((programManager.getProgramProvider(note.getProviderNo(), note.getProgram_no())).getRole().getId());
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
            role = "0";
        }

        note.setReporter_caisi_role(role);

        String team = resolveReporterProgramTeamId(admissionManager, note.getProgram_no(), note.getDemographic_no());
        note.setReporter_program_team(team);
        if (appointmentNo != null && appointmentNo.length() > 0) {
            try {
                note.setAppointmentNo(Integer.parseInt(appointmentNo));
            } catch (NumberFormatException e) {
                logger.debug("no appt no");
            }
        }
        // update note issues
        String sessionFrmName = ChartProgramContext.formKey(request, demo);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope
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

            strNote = appendRemovedIssueMessage(strNote, request.getLocale(), props, issueNames);
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
                strNote = appendRemovedIssueMessage(strNote, request.getLocale(), props, issueNames);
                note.setNote(strNote);
            }

            for (int idx = 0; idx < issue_id.length; ++idx) {
                cIssue = this.caseManagementMgr.getIssueById(demo, issue_id[idx]);
                if (cIssue == null) {
                    Issue issue = this.caseManagementMgr.getIssue(issue_id[idx]);
                    cIssue = prepareCaseIssue(demo, issue, Integer.parseInt(programId));
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

        Map<Long, Integer> notePositions = new HashMap<>();
        CaseManagementNote curNote;
        long nId = Long.parseLong(noteId);
        int numNotes = curCPPNotes.size();
        // Alas we have to cycle through to make sure an ordering has been set
        // this is for legacy data
        for (int idx = 1; idx < curCPPNotes.size(); ++idx) {
            curNote = curCPPNotes.get(idx);
            if (curNote.getPosition() == 0) {
                if (curNote.getId() == nId) {
                    note.setPosition(idx);
                } else {
                    notePositions.put(curNote.getId(), idx);
                }
            }
        }

        if (removed || note.isArchived()) {
            position = note.getPosition();
            for (CaseManagementNote c : curCPPNotes) {
                int currentPosition = notePositions.getOrDefault(c.getId(), c.getPosition());
                if (c.getId() == nId) {
                    continue;
                } else if (position < currentPosition) {
                    newPos = currentPosition - 1;
                    notePositions.put(c.getId(), newPos);
                }
            }
        } else if ((newPos != note.getPosition() && !(newPos == numNotes && note.getPosition() == (numNotes - 1))) || newNote) {
            for (CaseManagementNote c : curCPPNotes) {
                int currentPosition = notePositions.getOrDefault(c.getId(), c.getPosition());
                if (c.getId() != nId) {
                    if (currentPosition >= newPos
                            && (newNote || (newPos < note.getPosition() && currentPosition < note.getPosition()))) {
                        position = currentPosition + 1;
                        notePositions.put(c.getId(), position);
                    } else if ((!newNote && newPos > note.getPosition()) && currentPosition <= newPos && currentPosition > note.getPosition()) {
                        position = currentPosition - 1;
                        notePositions.put(c.getId(), position);
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
        // This patient's own last eChart text: another chart's would be looked for, and removed,
        // in this patient's eChart record (issue #4673).
        String lastSavedNoteString = (String) session.getAttribute(ChartSessionKeys.lastSavedNoteText(demo)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- ChartSessionKeys accepts only a digits-only patient number
        List<CaseManagementNoteExt> submittedExtensions = readSubmittedCppExtensions(extNames, extKeys);
        String savedStr;
        try {
            var savedRevision = caseManagementMgr.saveCppNoteRevision(
                    note, userName, lastSavedNoteString, notePositions, submittedExtensions);
            note = savedRevision.note();
            savedStr = savedRevision.legacyChartText();
        } catch (RuntimeException e) {
            if (!StaleNoteRevisionException.isConflict(e)) throw e;
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return null;
        }
        logger.debug("Saved note");
        if (issue_id != null) {
            bindSavedCppIssues(caseIssueList, note.getIssues());
            sessionFrm.setIssueCheckList(caseIssueList);
        }
        /* remember the str written into this patient's echart */
        io.github.carlos_emr.carlos.utility.BoundedSessionAttributes.put(session, "savedNoteText", ChartSessionKeys.lastSavedNoteText(demo), savedStr); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep

        caseManagementMgr.getEditors(note);

        if (newNote) {
            logAction = LogConst.ADD;
        } else if (note.isArchived()) {
            logAction = LogConst.ARCHIVE;
        } else {
            logAction = LogConst.UPDATE;
        }

        LogAction.addLog(providerNo, logAction, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demo, note.getAuditString());

        String f = request.getParameter("forward");
        if (f != null && f.equals("none")) {
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().println(note.getId()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- text/plain response writing numeric note id
            return null;
        }

        this.setReloadUrl("/CaseManagementView?" + reloadUrl);
        return "listCPPNotes";
    }

    /** Publish persisted issue identities only after the CPP transaction has committed. */
    private static void bindSavedCppIssues(List<CheckBoxBean> checkboxes, Set<CaseManagementIssue> savedIssues) {
        Map<Long, CaseManagementIssue> savedByIssue = new HashMap<>();
        for (CaseManagementIssue issue : savedIssues) savedByIssue.put(issue.getIssue_id(), issue);
        for (CheckBoxBean checkbox : checkboxes) {
            if (checkbox.getIssue().getId() != null) continue;
            CaseManagementIssue saved = savedByIssue.get(checkbox.getIssue().getIssue_id());
            if (saved != null) checkbox.setIssue(saved);
        }
    }

    /** Resolve CPP form fields without writing or changing persisted extension rows. */
    private List<CaseManagementNoteExt> readSubmittedCppExtensions(String[] names, String[] keys) {
        List<CaseManagementNoteExt> submitted = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String value = request.getParameter(names[i]);
            if (value == null) continue;
            CaseManagementNoteExt extension = new CaseManagementNoteExt();
            extension.setKeyVal(keys[i]);
            boolean dateField = i <= 2;
            if (!dateField) extension.setValue(value);
            if (!dateField || writePartialDate(value, extension)) submitted.add(extension);
        }
        return submitted;
    }

    /** Bind a new note to this chart's appointment, without changing an existing note's visit. */
    static int resolveNoteAppointmentNo(CaseManagementNote note, String requestAppointmentNo,
                                        String sessionAppointmentNo,
                                        java.util.function.IntFunction<Appointment> findAppointment) {
        if (note.getId() != null && note.getId() > 0) return note.getAppointmentNo();
        String candidate = requestAppointmentNo != null ? requestAppointmentNo : sessionAppointmentNo;
        if (candidate == null || !candidate.matches("[0-9]{1,9}") || "0".equals(candidate)) return 0;
        int appointmentNo = Integer.parseInt(candidate);
        Appointment appointment = findAppointment.apply(appointmentNo);
        if (appointment == null || !String.valueOf(appointment.getDemographicNo()).equals(note.getDemographic_no())) {
            return 0;
        }
        return appointmentNo;
    }

    /** Rebuilds a patient-bound billing link for every full editor response. */
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Canonicalizes a configured province code, not a patient/provider identity or an authorization decision")
    private void prepareBillingUrl(String demono) throws java.io.UnsupportedEncodingException {
        HttpSession session = request.getSession();
        String url = "";
        if ("casemgmt".equals(request.getAttribute("from"))) {

            String province = CarlosProperties.getInstance().getProperty("billregion", "").trim().toUpperCase(java.util.Locale.ROOT);

            EctSessionBean bean = encounterForPatient(demono);

            if (bean.appointmentNo == null) {
                bean.appointmentNo = "0";
            }
            // casemgmt_oscar_baseurl is already the application root (Index2.jsp stores the context
            // path, and navigation.jsp appends routes to it directly), so the context path must not
            // be added again. An absent or blank base (session not entered through Index2) falls back
            // to the request's own context path rather than rendering a literal "null/billing".
            String bsurl = (String) session.getAttribute("casemgmt_oscar_baseurl");
            if (StringUtils.isBlank(bsurl)) {
                bsurl = request.getContextPath();
            }
            Date today = new Date();
            Calendar todayCal = Calendar.getInstance();
            todayCal.setTime(today);

            String Hour = Integer.toString(todayCal.get(Calendar.HOUR));
            String Min = Integer.toString(todayCal.get(Calendar.MINUTE));

            String default_view = CarlosProperties.getInstance().getProperty("default_view", "");

            url = bsurl + "/billing?billRegion=" + java.net.URLEncoder.encode(province, "UTF-8") + "&billForm=" + java.net.URLEncoder.encode(default_view, "UTF-8") + "&hotclick=" + java.net.URLEncoder.encode("", "UTF-8") + "&appointment_no=" + bean.appointmentNo + "&appointment_date=" + bean.appointmentDate + "&start_time=" + Hour + ":" + Min + "&demographic_name=" + java.net.URLEncoder.encode(bean.patientLastName + "," + bean.patientFirstName, "UTF-8") + "&demographic_no=" + bean.demographicNo
                    + "&providerview=" + bean.curProviderNo + "&user_no=" + bean.providerNo + "&apptProvider_no=" + bean.curProviderNo + "&bNewForm=1&status=t";

            request.setAttribute("billing_url", url);
        }

    }

    /** Builds missing legacy billing metadata from this request's patient, never another window. */
    private EctSessionBean encounterForPatient(String demographicNo) {
        EctSessionBean bean = EncounterPatientContext.findEncounter(request, demographicNo);
        if (bean == null) {
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            var demographic = SpringUtils.getBean(DemographicManager.class).getDemographic(loggedInInfo, demographicNo);
            bean = new EctSessionBean();
            bean.demographicNo = demographicNo;
            bean.providerNo = loggedInInfo.getLoggedInProviderNo();
            bean.curProviderNo = bean.providerNo;
            bean.patientFirstName = demographic == null ? "" : demographic.getFirstName();
            bean.patientLastName = demographic == null ? "" : demographic.getLastName();
        }
        String requestedAppointment = request.getParameter("appointmentNo");
        if (StringUtils.isBlank(requestedAppointment)) requestedAppointment = bean.appointmentNo;
        bean.appointmentNo = "0";
        bean.appointmentDate = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");
        if (requestedAppointment != null && requestedAppointment.matches("[1-9][0-9]{0,8}")) {
            Appointment appointment = SpringUtils.getBean(OscarAppointmentDao.class).find(Integer.valueOf(requestedAppointment));
            if (appointment != null && demographicNo.equals(String.valueOf(appointment.getDemographicNo()))) {
                bean.appointmentNo = requestedAppointment;
                bean.appointmentDate = UtilDateUtilities.DateToString(appointment.getAppointmentDate(), "yyyy-MM-dd");
            }
        }
        return bean;
    }

    private void bindNoteAppointment(CaseManagementNote note) {
        EctSessionBean encounter = EncounterPatientContext.findEncounter(request, note.getDemographic_no());
        note.setAppointmentNo(resolveNoteAppointmentNo(note, request.getParameter("appointmentNo"),
                encounter == null ? null : encounter.appointmentNo,
                id -> SpringUtils.getBean(OscarAppointmentDao.class).find(id)));
    }

    // FindSecBugs IMPROPER_UNICODE: compares on/persist/null/empty form flags and sentinels, not provider identities or secrets.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive form flags and null/empty sentinels; provider identities and secrets are not case-folded")
    private long noteSave() throws Exception {
        if (!requireNoteWriteAccess()) return -1L;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        //Before we do anything we make sure we still have the lock on the note
        HttpSession session = request.getSession();
        String demo = getDemographicNo(request);
        String sessionFrmName = ChartProgramContext.formKey(request, demo);

        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

        if (sessionFrm == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return -1L;
        }
        if (!hasNoteLock(demo)) {
            logger.debug("noteSave rejected: no valid lock for demographic {}", demo);
            return -1L;
        }

        CaseManagementNote note = sessionFrm.getCaseNote();
        if (note == null || !demo.equals(note.getDemographic_no())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return -1L;
        }
        String noteTxt = this.getCaseNote_note();
        noteTxt = StringUtils.trimToNull(noteTxt);
        if (noteTxt == null || noteTxt.equals("")) return -1L;

        // Read the encounter date before the session note is touched: an unreadable one refuses
        // the whole save (#4711) and must leave the note the chart still shows unchanged.
        Date dateObserve;
        try {
            dateObserve = NoteObservationDate.parse(this.getObservation_date(), LocaleUtils.resolveBundleLocale(request));
            if (rejectFutureObservationDate(dateObserve)) return -1L;
        } catch (ParseException unreadable) {
            rejectObservationDate();
            return -1L;
        }

        bindNoteAppointment(note);
        note.setNote(noteTxt);

        Provider provider = loggedInInfo.getLoggedInProvider();
        String userName = provider != null ? provider.getFullName() : "";

        CaseManagementCPP cpp = this.caseManagementMgr.getCPP(demo);
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(demo);
        }
        String lastSavedNoteString = (String) session.getAttribute(ChartSessionKeys.lastSavedNoteText(demo)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- ChartSessionKeys accepts only a digits-only patient number

        // bug fix - encounter type was not being updated.
        String encounterType = request.getParameter("caseNote.encounter_type");
        if (encounterType != null) {
            note.setEncounter_type(encounterType);
        }

        String hourOfEncounterTime = request.getParameter("hourOfEncounterTime");
        if (StringUtils.isNotEmpty(hourOfEncounterTime)) {
            note.setHourOfEncounterTime(Integer.valueOf(hourOfEncounterTime));
        }

        String minuteOfEncounterTime = request.getParameter("minuteOfEncounterTime");
        if (StringUtils.isNotEmpty(minuteOfEncounterTime)) {
            note.setMinuteOfEncounterTime(Integer.valueOf(minuteOfEncounterTime));
        }

        String hourOfEncTransportationTime = request.getParameter("hourOfEncTransportationTime");
        if (StringUtils.isNotEmpty(hourOfEncTransportationTime)) {
            note.setHourOfEncTransportationTime(Integer.valueOf(hourOfEncTransportationTime));
        }

        String minuteOfEncTransportationTime = request.getParameter("minuteOfEncTransportationTime");
        if (StringUtils.isNotEmpty(minuteOfEncTransportationTime)) {
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
            String programId = ChartProgramContext.resolve(request);
            if (programId == null || "null".equalsIgnoreCase(programId)) {
                EctProgram ectProgram = new EctProgram(session);
                programId = ectProgram.getProgram(providerNo);
                ChartProgramContext.remember(request, programId); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
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
            ongoing = prepareCheckedIssues(request, demo, issuelist, checkedlist, issueset, noteSet, ongoing);
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
        String verifyStr = request.getParameter("verify");
        boolean verify = false;
        if (verifyStr != null && verifyStr.equalsIgnoreCase("on")) {
            verify = true;
        }

        Date now = new Date();

        ResourceBundle props = ResourceBundle.getBundle("oscarResources", request.getLocale());
        if (dateObserve != null) {
            note.setObservation_date(dateObserve);
        } else if (note.getObservation_date() == null) {
            note.setObservation_date(now);
        }

        note.setUpdate_date(now);

        try {
            note = caseManagementMgr.saveCaseManagementNote(
                    loggedInInfo, note, issuelist, cpp, ongoing, verify, request.getLocale(), now,
                    userName, providerNo, request.getRemoteAddr(), lastSavedNoteString);
        } catch (RuntimeException e) {
            if (!StaleNoteRevisionException.isConflict(e)) throw e;
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return -1;
        }
        caseManagementMgr.getEditors(note);
        this.setCaseNote(note);
        sessionFrm.setCaseNote(note);

        //update lock to new note id
        CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session lock keyed by demographic scope
        if (casemgmtNoteLockSession != null) {
            casemgmtNoteLockSession.setNoteId(note.getId());
            logger.debug("UPDATING NOTE ID in LOCK");
            casemgmtNoteLockDao.merge(casemgmtNoteLockSession);
            session.setAttribute("casemgmtNoteLock" + demo, casemgmtNoteLockSession); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }

        deleteSavedChartDraft(providerNo, note.getDemographic_no());

        return note.getId();
    }

    /** The chart owns its draft even when it explicitly edits a visible note from another program. */
    private void deleteSavedChartDraft(String providerNo, String patient) {
        String program = ChartProgramContext.resolve(request);
        if (program == null) return;
        try {
            caseManagementMgr.deleteTmpSave(providerNo, patient, program);
        } catch (Exception e) {
            logger.warn("Warning ({})", e.getClass().getSimpleName());
        }
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
            if (tempIssue == issue || (tempIssue.getId() != null && tempIssue.getId().equals(issue.getId()))) return true;
        }

        return (false);
    }

    private void copyIssueDisplayToCaseManagementIssue(CaseManagementIssue caseManagementIssue, IssueDisplay issueDisplay) {
        caseManagementIssue.setAcute("acute".equals(issueDisplay.acute));
        caseManagementIssue.setCertain("certain".equals(issueDisplay.certain));
        caseManagementIssue.setMajor("major".equals(issueDisplay.major));
        caseManagementIssue.setResolved("resolved".equals(issueDisplay.resolved));
    }

    private String prepareCheckedIssues(HttpServletRequest request, String demo, List<CaseManagementIssue> issuelist,
                                        List<CheckBoxBean> checkedlist, Set issueset, Set noteSet, String ongoing) {
        int demographicNo = Integer.parseInt(demo);

        for (int i = 0; i < checkedlist.size(); i++) {
            CheckBoxBean checkBoxBean = checkedlist.get(i);
            IssueDisplay issueDisplay = checkBoxBean.getIssueDisplay();

            if ("unresolved".equals(issueDisplay.resolved)) {
                ongoing = ongoing + issueDisplay.getDescription() + "\n";
            }

            boolean isChecked = WebUtils.isChecked(request, "issueCheckList[" + i + "].checked");

            CaseManagementIssue caseManagementIssue = findCheckedIssue(demo, issueDisplay, isChecked, issuelist);

            if (caseManagementIssue == null) continue;
            copyIssueDisplayToCaseManagementIssue(caseManagementIssue, issueDisplay);

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

    private CaseManagementIssue findCheckedIssue(String demo, IssueDisplay display, boolean checked,
                                                 List<CaseManagementIssue> pending) {
        CaseManagementIssue existing = caseManagementIssueDao.getIssuebyIssueCode(demo, display.code);
        if (existing != null || !checked) return existing;
        Issue issue = issueDao.findIssueByCode(display.code);
        return issue == null ? null : prepareCheckedIssue(demo, issue, pending);
    }

    private CaseManagementIssue prepareCheckedIssue(String demo, Issue issue, List<CaseManagementIssue> pending) {
        CaseManagementIssue existing = caseManagementIssueDao.getIssuebyId(demo, String.valueOf(issue.getId()));
        if (existing != null) return existing;
        for (CaseManagementIssue prepared : pending) {
            if (prepared.getIssue_id() == issue.getId()) return prepared;
        }
        // Persist with saveCaseManagementNote so a stale revision rolls it back too.
        CaseManagementIssue prepared = new CaseManagementIssue();
        prepared.setDemographic_no(Integer.parseInt(demo));
        prepared.setIssue_id(issue.getId());
        prepared.setIssue(issue);
        prepared.setType(issue.getRole());
        return prepared;
    }

    public String save() throws Exception {
        if (!requireNoteWriteAccess()) return NONE;
        HttpSession session = request.getSession();
        if (session == null || session.getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "false");

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        if (!hasNoteLock(demono)) {
            return "windowCloseError";
        }

        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
        // Capture the raw draft before noteSave trims text or adds a signature.
        String unsavedNoteText = request.getParameter("caseNote_note");
        if (unsavedNoteText == null) unsavedNoteText = this.getCaseNote_note();
        long noteId = noteSave();
        if (noteId < 0) return rejectedFullFormSave(unsavedNoteText, "view");

        /* prepare the message */
        addActionMessage(getText("note.saved"));
        String chain = request.getParameter("chain");
        String chainResult = sanitizeChainResultName(chain);

        if (chainResult != null) {
            return chainResult;
        } else if (StringUtils.isNotBlank(chain)) {
            logger.warn("Rejected invalid chain result target");
        }

        return "view";
    }

    /**
     * Refuses a note save whose encounter date {@link NoteObservationDate} cannot read (#4711),
     * with 400 instead of the unhandled {@code ParseException} that answered 500. The posted value
     * is not logged: it is client-supplied text about a patient's visit.
     */
    private void rejectObservationDate() {
        logger.warn("Note save refused: the posted encounter date could not be read");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        request.setAttribute(OBSERVATION_DATE_REJECTED, Boolean.TRUE);
    }

    /** A future clinical date is invalid; refuse before touching the draft or persistent state. */
    private boolean rejectFutureObservationDate(Date date) {
        if (date == null || !date.after(new Date())) return false;
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        request.setAttribute(OBSERVATION_DATE_REJECTED, Boolean.TRUE);
        request.setAttribute("DateError", ResourceBundle.getBundle("oscarResources", request.getLocale())
                .getString("encounter.observationDateFuture.msg"));
        return true;
    }

    /** Keep a rejected full-form draft available when the response replaces the editor page. */
    private String rejectedFullFormSave(String unsavedNoteText, String fallbackResult) {
        if (Boolean.TRUE.equals(request.getAttribute(OBSERVATION_DATE_REJECTED))) {
            request.setAttribute("unsavedNoteText", unsavedNoteText);
            return "windowCloseError";
        }
        if (response.getStatus() != HttpServletResponse.SC_CONFLICT) return fallbackResult;
        request.setAttribute("noteRevisionConflict", Boolean.TRUE);
        request.setAttribute("unsavedNoteText", unsavedNoteText);
        return "windowCloseError";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String ajaxsave() throws Exception {
        if (!requireNoteWriteAccess()) return NONE;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String noteTxt = request.getParameter("noteTxt");
        noteTxt = StringUtils.trimToNull(noteTxt);
        if (noteTxt == null || noteTxt.equals("")) return null;

        logger.debug("Saving Note {}", LogSafe.sanitize(request.getParameter("nId")));
        logger.debug("Note text received");
        String demo = getDemographicNo(request);

        if (!hasNoteLock(demo)) {
            logger.debug("ajaxsave rejected: no valid lock for demographic {}", demo);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return null;
        }

        // Before the note is loaded, so a refusal changes nothing. The editor renders English
        // months whatever the browser's language; reading them in the request locale (as this
        // did) made every non-English switch save fail once that rendering was fixed (#4711).
        Date dateObserve;
        try {
            dateObserve = NoteObservationDate.parse(request.getParameter("obsDate"), LocaleUtils.resolveBundleLocale(request));
            if (rejectFutureObservationDate(dateObserve)) return null;
        } catch (ParseException unreadable) {
            rejectObservationDate();
            return null;
        }

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
            if (note == null || !demo.equals(note.getDemographic_no())) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            history = note.getHistory();
            history = "---------History Record---------" + history;
            newNote = false;
        }

        bindNoteAppointment(note);
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        if (dateObserve != null) {
            note.setObservation_date(dateObserve);
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

        String programId = ChartProgramContext.resolve(request);
        note.setProgram_no(programId);

        WebApplicationContext ctx = this.getSpringContext();
        ProgramManager programManager = (ProgramManager) ctx.getBean(ProgramManager.class);
        AdmissionManager admissionManager = (AdmissionManager) ctx.getBean(AdmissionManager.class);

        String role = null;
        try {
            role = String.valueOf((programManager.getProgramProvider(note.getProviderNo(), note.getProgram_no())).getRole().getId());
        } catch (Exception e) {
            logger.error("Error ({})", e.getClass().getSimpleName());
            role = "0";
        }

        note.setReporter_caisi_role(role);

        String team = resolveReporterProgramTeamId(admissionManager, note.getProgram_no(), note.getDemographic_no());

        note.setReporter_program_team(team);

        String sessionName = ChartProgramContext.formKey(request, demo);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope
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

        try {
            note = this.caseManagementMgr.saveNoteRevisionSimple(note);
        } catch (RuntimeException e) {
            if (!StaleNoteRevisionException.isConflict(e)) throw e;
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return null;
        }
        this.caseManagementMgr.getEditors(note);
        sessionFrm.setCaseNote(note);
        CasemgmtNoteLock noteLock = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): reads a server-created lock from this session; hasNoteLock already checked database and current-session ownership, and the loaded note's demographic matches demo
        if (noteLock != null) {
            noteLock.setNoteId(note.getId());
            casemgmtNoteLockDao.merge(noteLock);
        }

        deleteSavedChartDraft(providerNo, note.getDemographic_no());

        session.setAttribute(sessionName, sessionFrm); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        CaseManagementEntryFormBean newform = new CaseManagementEntryFormBean();
        newform.setCaseNote(note);
        newform.setIssueCheckList(checkedlist);
        request.setAttribute("caseManagementEntryForm", newform);
        request.setAttribute("newNote", "false"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        request.setAttribute("ajaxsave", note.getId());
        request.setAttribute("origNoteId", noteId);
        // noteIssueList.jsp renders the saved text into the read-only view through
        // ${noteTxt}; EL reads scoped attributes, not request parameters, so without this
        // the view of a note saved on switch came up empty until the chart was reloaded.
        request.setAttribute("noteTxt", noteTxt);

        String logAction;
        if (newNote) {
            logAction = LogConst.ADD;
        } else {
            logAction = LogConst.UPDATE;
        }

        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), logAction, LogConst.CON_CME_NOTE, String.valueOf(note.getId()), request.getRemoteAddr(), demo, note.getAuditString());

        return ISSUE_LIST_AJAX_RESULT;
    }

    /**
     * Checks whether the current HTTP session holds a valid note lock for the given demographic.
     * Performs two validations:
     * <ol>
     *   <li>The database lock's session ID matches the session-stored lock's session ID
     *       (detects if another window/session has taken over the lock)</li>
     *   <li>The current request's session ID matches the session-stored lock's session ID
     *       (detects session ID staleness after rotation or migration)</li>
     * </ol>
     *
     * @param demo String the demographic number to check lock ownership for
     * @return boolean true if this session owns the lock, false otherwise
     */
    private boolean hasNoteLock(String demo) {
        HttpSession session = request.getSession();
        CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session lock keyed by demographic scope
        try {
            if (casemgmtNoteLockSession == null) {
                return false;
            }
            CasemgmtNoteLock casemgmtNoteLock = casemgmtNoteLockDao.find(casemgmtNoteLockSession.getId());
            if (casemgmtNoteLock == null) {
                return false;
            }
            String currentSessionId = request.getSession().getId();
            return Objects.equals(casemgmtNoteLock.getSessionId(), casemgmtNoteLockSession.getSessionId())
                && Objects.equals(currentSessionId, casemgmtNoteLockSession.getSessionId());
        } catch (Exception e) {
            logger.warn("Lock check failed unexpectedly ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private void releaseNoteLock(String providerNo, Integer demographicNo, Long noteId) {
        logger.debug("REMOVING LOCK FOR PROVIDER " + providerNo + " DEMO " + demographicNo + " NOTE ID " + noteId);
        casemgmtNoteLockDao.removeForSession(providerNo, demographicNo, noteId, request.getSession().getId());
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String releaseNoteLock() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String demoNo = getDemographicNo(request);
        String noteId = request.getParameter("noteId");
        String forceRelease = request.getParameter("force");
        HttpSession session = request.getSession();
        String sessionFrmName = ChartProgramContext.formKey(request, demoNo);

        try {
            CasemgmtNoteLock casemgmtNoteLockSession = (CasemgmtNoteLock) session.getAttribute("casemgmtNoteLock" + demoNo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session lock keyed by demographic scope
            //If browser is exiting check to see if we should release lock.  It may be held by same user in another window so we check
            if (request.getSession().getId().equals(casemgmtNoteLockSession.getSessionId()) && casemgmtNoteLockSession.getNoteId() == Long.parseLong(noteId)) {
                releaseNoteLock(providerNo, Integer.parseInt(demoNo), Long.parseLong(noteId));
                session.removeAttribute("casemgmtNoteLock" + demoNo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): release of own-session lock keyed by demographic scope
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String saveAndExit() throws Exception {
        if (!requireNoteWriteAccess()) return NONE;
        logger.debug("saveandexit");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();

        String demoNo = getDemographicNo(request);

        if (session.getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "false");

        addActionMessage(getText("note.saved"));

        String priorNote = this.getNoteId();
        // Full form posts replace the editor page; retain the raw draft before noteSave normalizes it.
        String unsavedNoteText = request.getParameter("caseNote_note");
        if (unsavedNoteText == null) unsavedNoteText = this.getCaseNote_note();
        Long noteId = noteSave();
        if (noteId < 0) {
            return rejectedFullFormSave(unsavedNoteText, "windowCloseError");
        }
        session.removeAttribute("casemgmtNoteLock" + demoNo); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): release of own-session lock keyed by demographic scope

        releaseNoteLock(providerNo, Integer.parseInt(demoNo), noteId);
        this.setMethod("view");
        String error = (String) request.getAttribute("DateError");
        if (error != null) {
            return "windowCloseError";
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
            String providerview = loggedInInfo.getLoggedInProviderNo();
            String defaultView = CarlosProperties.getInstance().getProperty("default_view", "");

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
            String url = contextPath + "/billing?billRegion=" + region
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
            logger.debug("Redirecting to billing form for appointment_no={}, demographic_no={}",
                    appointmentNo, demoNo);
            sendBillingRedirect(url);
            return NONE;
        }

        String chain = request.getParameter("chain");

        if (chain != null && !chain.equals("")) {
            if (isAllowedInternalRedirectChain(chain)) {
                sendCaseManagementListRedirect(response, request.getContextPath());
                return NONE;
            } else {
                logger.warn("Rejected invalid chain redirect target");
                // Fall through to return "windowClose" without redirect
            }
        }

        return "windowClose";
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a fixed same-origin billing path (contextPath + "/billing"); only query parameters (billing/appointment request and session values) vary and cannot alter the host or scheme.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a fixed same-origin billing path (contextPath + \"/billing\"); only query parameters (billing/appointment request and session values) vary and cannot alter the host or scheme")
    private void sendBillingRedirect(String url) throws IOException {
        response.sendRedirect(url);
    }

    public String cancel() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String programNo = ChartProgramContext.resolve(request);

        String demo = this.getDemographicNo();

        try {
            logger.debug("CANCEL P:" + providerNo + " D:" + demo + " PROG:" + programNo);
            this.caseManagementMgr.deleteTmpSave(providerNo, demo, programNo);
        } catch (Exception e) {
            logger.warn("Warning ({})", e.getClass().getSimpleName());
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
        String sessionFrmName = ChartProgramContext.formKey(request, demono);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope
        CaseManagementNote note = sessionFrm.getCaseNote();
        String noteTxt = this.getCaseNote_note();
        noteTxt = StringUtils.trimToNull(noteTxt);
        note.setNote(noteTxt);

        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));

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
        String programId = ChartProgramContext.resolve(request);
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
        String programId = ChartProgramContext.resolve(request);

        request.setAttribute("change_flag", "true");

        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
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
        String sessionFrmName = ChartProgramContext.formKey(request, demono);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope
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
        if (!requireIssueWriteAccess()) return NONE;
        HttpSession session = request.getSession();
        // grab the issue we want to add
        String issueId = request.getParameter("newIssueId");

        String sessionFrmName = ChartProgramContext.formKey(request, this.getDemographicNo(request));
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String issueAdd() throws Exception {
        if (!requireIssueWriteAccess()) return NONE;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        String changeDiagnosis = request.getParameter("change_diagnosis");
        if (changeDiagnosis != null && changeDiagnosis.equalsIgnoreCase("true")) {
            return submitChangeDiagnosis();
        }
        try {
            if (rejectFutureObservationDate(NoteObservationDate.parse(this.getObservation_date(),
                    LocaleUtils.resolveBundleLocale(request)))) {
                return rejectedFullFormSave(this.getCaseNote_note(), "view");
            }
        } catch (ParseException unreadable) {
            // IssueSearch sends no encounter date; keep the existing draft date in this path.
        }
        logger.debug("issueAdd");
        request.setAttribute("change_flag", "true");
        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));

        String demono = getDemographicNo(request);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        String sessionFrmName = ChartProgramContext.formKey(request, demono);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

        if (sessionFrm != null) {
            if (sessionFrm.getCaseNote() != null) {
                // IssueSearch.jsp, the page that posts here, sends no date at all. Only a readable one
                // replaces the note's: nulling it (as this used to) made the next save stamp "now".
                try {
                    Date posted = NoteObservationDate.parse(this.getObservation_date(), LocaleUtils.resolveBundleLocale(request));
                    if (posted != null) sessionFrm.getCaseNote().setObservation_date(posted);
                } catch (ParseException unreadable) {
                    logger.debug("issueAdd kept the note's encounter date: the posted one could not be read");
                }
            }
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

        String programIdStr = ChartProgramContext.resolve(request);
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
            return ISSUE_LIST_AJAX_RESULT;
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
        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
        request.setAttribute("change_diagnosis", Boolean.valueOf(true));
        request.setAttribute("change_diagnosis_id", inds);
        this.setShowList("false");
        this.setSearString("");
        return "IssueSearch";
    }

    public String submitChangeDiagnosis() {
        if (!requireIssueWriteAccess()) return NONE;
        logger.debug("submitChangeDiagnosis");
        if (request.getSession().getAttribute("userrole") == null) return "expired";

        request.setAttribute("change_flag", "true");
        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));

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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String ajaxChangeDiagnosis() {
        if (!requireIssueWriteAccess()) return NONE;
        logger.debug("ajaxChangeDiagnosis");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        // get issue we're changing
        String strIndex = request.getParameter("change_diagnosis_id");
        int idx = Integer.parseInt(strIndex);

        String substitution = request.getParameter("newIssueId");
        String sessionFrmName = ChartProgramContext.formKey(request, getDemographicNo(request));
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

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

        return ISSUE_LIST_AJAX_RESULT;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String issueDelete() throws Exception {
        if (!requireIssueWriteAccess()) return NONE;
        // The issue deletion commits before noteSave below. Refuse invalid dates first,
        // so a rejected note save cannot leave its issue already deleted.
        try {
            if (rejectFutureObservationDate(NoteObservationDate.parse(this.getObservation_date(),
                    LocaleUtils.resolveBundleLocale(request)))) {
                return rejectedFullFormSave(this.getCaseNote_note(), "view");
            }
        } catch (ParseException unreadable) {
            rejectObservationDate();
            return rejectedFullFormSave(this.getCaseNote_note(), "view");
        }
        logger.debug("issueDelete");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        String demono = getDemographicNo(request);
        String sessionFrmName = ChartProgramContext.formKey(request, demono);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

        request.setAttribute("change_flag", "true");
        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
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

        if (CarlosProperties.getInstance().isCaisiLoaded() && iss != null) {
            // reset current concern in CPP
            caseManagementMgr.removeIssueFromCPP(demono, iss);
        }

        // added by Eugene Petrhin in order to guarantee the DB consistency if user hits Cancel afterwards
        if (sessionFrm.getCaseNote().getId() != null && noteSave() < 0) return null;

        String ajax = request.getParameter("ajax");
        if (ajax != null && ajax.equalsIgnoreCase("true")) {
            request.setAttribute("caseManagementEntryForm", sessionFrm);
            return ISSUE_LIST_AJAX_RESULT;
        } else return "view";
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String issueChange() throws Exception {
        if (!requireIssueWriteAccess()) return NONE;
        logger.debug("issueChange");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        request.setAttribute("from", sanitizeFromParam(request.getParameter("from")));
        request.setAttribute("change_flag", "true");
        request.setAttribute("issueStatusChanged", "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        String demono = getDemographicNo(request);
        String sessionFrmName = ChartProgramContext.formKey(request, demono);
        CaseManagementEntryFormBean sessionFrm = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): read of own-session form bean keyed by validated demographic scope

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

        logger.debug("issueChange for index {}: resolved={}", LogSafe.sanitize(String.valueOf(ind)), LogSafe.sanitize(resolvedParam));

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

        if (CarlosProperties.getInstance().isCaisiLoaded()) {
            // get access right
            List accessRight = caseManagementMgr.getAccessRight(providerNo, demono, ChartProgramContext.resolve(request));

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
            return ISSUE_LIST_AJAX_RESULT;
        } else return "view";
    }

    public String notehistory() {
        if (request.getSession().getAttribute("userrole") == null) return "expired";
        AuthorizedNoteHistory access = authorizedNoteHistory();
        return access == null ? refuseNoteHistory() : prepareNoteHistoryView(access);
    }

    /** Both revision-history entry points enforce the chart's patient, domain and program boundary. */
    private AuthorizedNoteHistory authorizedNoteHistory() {
        String demono;
        try {
            demono = getDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            return null;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Long noteId = parseExistingNoteId(request.getParameter("noteId"));
        Long demographicId = parseExistingNoteId(demono);
        if (loggedInInfo == null || demographicId == null || demographicId <= 0 || demographicId > Integer.MAX_VALUE
                || noteId == null || noteId <= 0
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", demono)
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_casemgmt.notes", "r", demono)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicId.intValue())) {
            return null;
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (!caseManagementMgr.isClientInProgramDomain(providerNo, demono)
                && !caseManagementMgr.isClientReferredInProgramDomain(providerNo, demono)) {
            return null;
        }
        CaseManagementNote selectedNote = caseManagementMgr.getNote(noteId.toString());
        if (selectedNote == null || parseExistingNoteId(selectedNote.getDemographic_no()) == null) {
            return null;
        }
        boolean linkedGroupNote = !demono.equals(selectedNote.getDemographic_no());

        // The note text is rendered by showHistory.jsp through the null-safe encoder with
        // line breaks preserved (carlos:forHtmlContentWithBreaks). Splicing "<br/>" into
        // the stored text here forced the view to emit it raw, which made a stored
        // "</p><script>" in a note execute in the history popup.
        List<CaseManagementNote> history;
        if (linkedGroupNote) {
            history = linkedGroupNoteHistory(loggedInInfo, demographicId.intValue(), noteId, selectedNote);
        } else {
            history = caseManagementMgr.getHistory(noteId.toString());
        }
        if (history == null || history.isEmpty() || history.stream().anyMatch(note -> note == null
                || !selectedNote.getDemographic_no().equals(note.getDemographic_no()))) {
            return null;
        }
        String programId = ChartProgramContext.resolve(request);
        if (!linkedGroupNote && programId != null && !programId.isEmpty() && !"0".equals(programId)) {
            // Match the chart's role/program/facility filter. Reject the whole expansion if any
            // row is hidden: a visible row's cumulative text may contain that earlier revision.
            List<CaseManagementNote> visible = caseManagementMgr.filterNotes(loggedInInfo,
                    loggedInInfo.getLoggedInProviderNo(), history, programId);
            if (visible.size() != history.size()) {
                return null;
            }
        }
        return new AuthorizedNoteHistory(demono, selectedNote, history, linkedGroupNote);
    }

    private record AuthorizedNoteHistory(String patient, CaseManagementNote selectedNote,
                                         List<CaseManagementNote> history, boolean linkedGroupNote) { }

    private String prepareNoteHistoryView(AuthorizedNoteHistory access) {
        request.setAttribute("demoName", getDemoName(access.patient()));
        request.setAttribute("history", access.history());
        request.setAttribute("showStoredNoteHistory", !access.linkedGroupNote());
        ResourceBundle props = ResourceBundle.getBundle("oscarResources");
        request.setAttribute("title", props.getString("encounter.noteHistory.title"));
        return "showHistory";
    }

    /**
     * GroupNoteLink grants access to one saved revision, not its entire UUID chain.
     * Match the modern chart's facility and active-link contract after authorizing
     * clinical read on the recipient. Access to the owner's full chart is not required.
     */
    private List<CaseManagementNote> linkedGroupNoteHistory(LoggedInInfo loggedInInfo, int demographicNo,
                                                           long selectedId, CaseManagementNote selected) {
        if (!Objects.equals(selectedId, selected.getId()) || selectedId > Integer.MAX_VALUE
                || loggedInInfo.getCurrentFacility() == null
                || !loggedInInfo.getCurrentFacility().isEnableGroupNotes()) return null;
        Set<Long> linkedIds = new HashSet<>();
        for (GroupNoteLink link : SpringUtils.getBean(GroupNoteDao.class).findLinksByDemographic(demographicNo)) {
            if (link.isActive() && Objects.equals(link.getDemographicNo(), demographicNo)
                    && link.getNoteId() != null && link.getNoteId() > 0) linkedIds.add(link.getNoteId().longValue());
        }
        if (!linkedIds.contains(selectedId)) return null;
        List<CaseManagementNote> history = StringUtils.isBlank(selected.getUuid())
                ? List.of(selected) : caseManagementMgr.getHistory(Long.toString(selectedId));
        List<CaseManagementNote> visible = new ArrayList<>();
        for (CaseManagementNote note : history) {
            if (note == null || !Objects.equals(selected.getDemographic_no(), note.getDemographic_no())
                    || !Objects.equals(selected.getUuid(), note.getUuid())) return null;
            if (note.getId() == null || note.getId() > selectedId || !linkedIds.contains(note.getId())) continue;
            // Stored cumulative prose may contain unshared revisions. Keep it off the
            // request-owned view model as well as disabling the JSP's legacy expansion.
            CaseManagementNote shared = note.copyForRevision();
            shared.setId(note.getId());
            shared.setHistory(null);
            visible.add(shared);
        }
        return visible.stream().anyMatch(note -> Objects.equals(selectedId, note.getId())) ? visible : null;
    }

    /** Refuses history before any clinical text or demographic name is exposed to the view. */
    private String refuseNoteHistory() {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return NONE;
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
        title.append(" " + props.getString("encounter.history.title"));
        request.setAttribute("title", title.toString());

        return "showHistory";
    }

    public String history() {
        logger.debug("history");

        HttpSession session = request.getSession();
        if (session.getAttribute("userrole") == null) return "expired";

        AuthorizedNoteHistory access = authorizedNoteHistory();
        if (access == null) return refuseNoteHistory();
        // A shared group-note link grants only its explicitly shared revisions. Its stored
        // cumulative history can include private owner revisions, so use the bounded view.
        if (access.linkedGroupNote()) return prepareNoteHistoryView(access);
        String demono = access.patient();
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoDOB", getDemoDOB(demono));

        String noteid = request.getParameter("noteId");
        CaseManagementNote note = access.selectedNote();

        request.setAttribute("history", note.getHistory());

        this.setCaseNote_history(note.getHistory());

        // The note's own demographic number is the patient the audit row is filed under; the note
        // text goes in the data slot. Passing the text as the sixth argument put it in the
        // demographicNo parameter, which logged it as a NumberFormatException and lost the patient (#4426).
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.READ, LogConst.CON_CME_NOTE, noteid, request.getRemoteAddr(), note.getDemographic_no(), note.getAuditString());

        return "historyview";
    }

    public String autosave() {
        logger.debug("autosave");

        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String demographicNo;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        String programId = ChartProgramContext.resolve(request);
        if (programId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "w", demographicNo)
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_casemgmt.notes", "w", demographicNo)
                || !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, Integer.parseInt(demographicNo))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        String note = request.getParameter("note");
        String noteId = request.getParameter("note_id");

        if (note == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!hasNoteLock(demographicNo)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return null;
        }

        if (!authorizeAutosaveNoteReference(loggedInInfo, demographicNo, programId, noteId)) return null;

        try {
            caseManagementMgr.replaceTmpSave(providerNo, demographicNo, programId, noteId, note);
        } catch (Exception e) {
            logger.warn("Encounter autosave failed ({})", e.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return null;
        }

        this.getCaseNote().setNote(note);

        response.setStatus(HttpServletResponse.SC_OK);
        return null;
    }

    /** A draft may refer only to this chart's authorized, selected note; a new note uses zero. */
    private boolean authorizeAutosaveNoteReference(LoggedInInfo login, String patient, String program, String rawId) {
        int reference;
        try {
            reference = rawId == null || rawId.isEmpty() ? 0 : Integer.parseInt(rawId);
        } catch (NumberFormatException invalidReference) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }
        CaseManagementNote selected = null;
        if (reference > 0) {
            selected = caseManagementMgr.getNote(Integer.toString(reference));
            if (selected == null || !patient.equals(selected.getDemographic_no())
                    || (!"0".equals(program) && caseManagementMgr.filterNotes(login,
                    login.getLoggedInProviderNo(), List.of(selected), program).isEmpty())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }
        CaseManagementEntryFormBean form = (CaseManagementEntryFormBean) request.getSession()
                .getAttribute(ChartProgramContext.formKey(request, patient));
        if (form != null) {
            CaseManagementNote current = form.getCaseNote();
            long expected = current == null || current.getId() == null ? 0 : Math.max(0, current.getId());
            if (expected != Math.max(0, reference) || (selected != null
                    && !Objects.equals(current.getProgram_no(), selected.getProgram_no()))) {
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                return false;
            }
        }
        return true;
    }

    public String restore() throws Exception {

        // Tell this patient's next chart view that its unsaved draft is being restored. Keyed by
        // patient: another chart's view must not consume the signal and withhold its own prompt.
        String demono = getDemographicNo(request);
        if (ChartSessionKeys.isPatientNumber(demono)) {
            request.getSession().setAttribute(ChartSessionKeys.draftRestoring(demono), "true"); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        }
        request.setAttribute("restore", Boolean.valueOf(true));

        return edit();
    }

    /**
     * Releases what one patient's chart keeps in the session ({@code method=cleanup}): its entry
     * form, its encounter entry, the text its last save wrote into the legacy eChart record and a
     * pending draft restore. No page sends this method at present. A request that names no patient
     * (or names two) releases nothing.
     *
     * @return NONE: cleanup does not render a view
     */
    public String cleanup() {
        clearPatientChartSession();
        return NONE;
    }

    /** Session cleanup is a void operation; its action wrapper supplies the Struts result. */
    private void clearPatientChartSession() {
        String demoNo;
        try {
            demoNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException noPatient) {
            // No chart to release, and every key below needs its patient: this used to fail with
            // a 500 when the encounter entry was forgotten.
            return;
        }
        String sessionFrmName = ChartProgramContext.formKey(request, demoNo);

        request.getSession().setAttribute(sessionFrmName, null); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
        EncounterPatientContext.forgetEncounter(request, demoNo);
        // The text this chart's last save wrote into the legacy eChart record, and a pending draft
        // restore: kept per patient (issue #4673). Opening the editor again starts without them,
        // as it always did.
        request.getSession().removeAttribute(ChartSessionKeys.lastSavedNoteText(demoNo)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- ChartSessionKeys accepts only a digits-only patient number
        request.getSession().removeAttribute(ChartSessionKeys.draftRestoring(demoNo)); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- ChartSessionKeys accepts only a digits-only patient number
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
        response.setContentType("text/html;charset=UTF-8");
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
                textStr = this.caseManagementMgr.getNote(noteIds[idx]).getObservation_date().toString() + " " + this.caseManagementMgr.getNote(noteIds[idx]).getProviderName() + " " + props.getString("encounter.noteBrowser.msgNoteLocked");
            } else {

                textStr = this.caseManagementMgr.getNote(noteIds[idx]).getNote();
            }
            textStr = SafeEncode.forHtmlContent(textStr).replace("\n", "<br>");
            // textStr is SafeEncode.forHtmlContent output followed only by fixed br tags.
            // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer
            out.println(textStr);
            out.println("<br><br>");
        }

        out.println("</body></html>");
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String print() throws Exception {
        Date now = new Date();
        String headerDate = CachedDateFormats.format(now, HEADER_PATTERN);

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

        try {
            if ("dates".equals(pType)) {
                cStartDate = ChartPrintDateRange.parseDialogDate(pStartDate, LocaleUtils.resolveBundleLocale(request));
                cEndDate = ChartPrintDateRange.parseDialogDate(pEndDate, LocaleUtils.resolveBundleLocale(request));
                ChartPrintDateRange.from(cStartDate, cEndDate);
            }
        } catch (IllegalArgumentException _) {
            response.reset();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid print date range");
            return null;
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
        try {
            cmp.doPrint(loggedInInfo, demographicNo, printAllNotes, noteIds, printCPP, printRx, printLabs, printPreventions, printAllergies, (pType != null && "dates".equals(pType)) ? true : false, cStartDate, cEndDate, request, response.getOutputStream());
        } catch (Exception e) {
            // Direct-response action: doPrint fails before writing any bytes (DocumentException/
            // IOException/SecurityException all fire pre-write), so the response is still uncommitted
            // here. Surface a real error instead of an empty HTTP-200 PDF (CLAUDE.md Direct-Response
            // Actions). If the merge failed mid-stream the response is committed and we can only log.
            logger.error("Encounter chart print failed ({})", e.getClass().getSimpleName());
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate the chart print");
            }
        }

        return null;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
        String programId = ChartProgramContext.resolve(request);
        return caseManagementMgr.getLastSaved(programId, demono, providerNo);
    }

    /*
     * Insert encounter reason for new note
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
            try {

                Date tempDate = CachedDateFormats.parse(strOldDate, YYYY_MM_DD_PATTERN, request.getLocale());
                strNewDate = CachedDateFormats.format(tempDate, DD_MMM_YYYY_PATTERN, request.getLocale());

            } catch (ParseException ex) {
                MiscUtils.getLogger().error("Error ({})", ex.getClass().getSimpleName());
            }
        }

        return strNewDate;
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

            session.setAttribute("CPPIssues", issues); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep
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

    /**
     * Parses the {@code noteId} request parameter for {@link #ticklerSaveNote()} into an existing
     * note's primary key, or {@code null} when there is no existing note to revise.
     * <p>The tickler-note dialog's hidden {@code noteId} field is client-populated; a stale or
     * malformed value (historically the literal string {@code "undefined"}, see the tickler-note
     * "undefined" display bug) must not blow up note persistence with an uncaught
     * {@link NumberFormatException} — it should be treated the same as "no existing note".
     */
    static Long parseExistingNoteId(String noteId) {
        if (noteId == null || !noteId.matches("\\d+") || noteId.equals("0")) {
            return null;
        }
        try {
            return Long.valueOf(noteId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Computes the next revision number for {@link #ticklerSaveNote()} from the prior note's
     * stored revision. A non-numeric {@code priorRevision} must not throw an uncaught
     * {@link NumberFormatException} and abort the save; it is treated as if this were the first
     * revision instead.
     */
    static String nextRevision(String priorRevision) {
        try {
            return String.valueOf(Integer.parseInt(priorRevision) + 1);
        } catch (NumberFormatException e) {
            return "1";
        }
    }

    /**
     * Prepends the new note text to the prior note's history for {@link #ticklerSaveNote()}.
     * Legacy notes can have a {@code null} or empty stored history; concatenating that directly
     * would persist the literal text {@code "null"} into the new note's history instead of
     * being treated as "no prior history".
     */
    static String combineHistory(String newNote, String priorHistory) {
        return (priorHistory == null || priorHistory.isEmpty())
                ? newNote
                : newNote + "\n" + priorHistory;
    }

    /*
     * 1) load existing note if possible
     * 1) update/save the note
     * 2) save/update link to the tickler (not sure yet)
     */
    public String ticklerSaveNote() {
        if (!requireNoteWriteAccess()) return NONE;
        String strNote = request.getParameter("value");
        Date creationDate = new Date();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        Provider loggedInProvider = loggedInInfo.getLoggedInProvider();
        String demographicNo = getDemographicNo(request);
        Tickler tickler = authorizedTickler();
        if (tickler == null) return NONE;
        if (!Integer.valueOf(demographicNo).equals(tickler.getDemographicNo())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }
        if (StringUtils.isBlank(strNote)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        String noteId = request.getParameter("noteId");
        String revision = "1";
        String history = strNote;
        String uuid = null;

        Long existingNoteId = parseExistingNoteId(noteId);
        if (existingNoteId != null) {
            CaseManagementNote existingNote = this.caseManagementNoteDao.getNote(existingNoteId);
            if (existingNote == null || !demographicNo.equals(existingNote.getDemographic_no())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            CaseManagementNoteLinkDAO links = SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
            CaseManagementNoteLink latest = links.getLastLinkByTableId(CaseManagementNoteLink.TICKLER,
                    Long.valueOf(tickler.getId()));
            if (latest == null || !existingNoteId.equals(latest.getNoteId())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            revision = nextRevision(existingNote.getRevision());
            history = combineHistory(strNote, existingNote.getHistory());
            uuid = existingNote.getUuid();
        }

        Issue issue = this.issueDao.findIssueByTypeAndCode("system", "TicklerNote");
        if (issue == null) {
            logger.error("missing TicklerNote issue, please run all database updates");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return NONE;
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
        link.setTableId(Long.valueOf(tickler.getId()));
        link.setTableName(CaseManagementNoteLink.TICKLER);

        CaseManagementNoteLinkDAO caseManagementNoteLinkDao = (CaseManagementNoteLinkDAO) SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
        caseManagementNoteLinkDao.save(link);

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

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String ticklerGetNote() throws IOException {
        Tickler tickler = authorizedTickler();
        if (tickler == null) return NONE;
        String patient = Integer.toString(tickler.getDemographicNo());
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(login, "_casemgmt.notes", "r", patient)
                || !securityInfoManager.isAllowedAccessToPatientRecord(login, tickler.getDemographicNo())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        CaseManagementNoteLinkDAO caseManagementNoteLinkDao = (CaseManagementNoteLinkDAO) SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
        CaseManagementNoteLink link = caseManagementNoteLinkDao.getLastLinkByTableId(CaseManagementNoteLink.TICKLER,
                Long.valueOf(tickler.getId()));
        ObjectNode json = (ObjectNode) objectMapper.readTree("{}");

        if (link != null) {
            Long noteId = link.getNoteId();

            CaseManagementNote note = this.caseManagementNoteDao.getNote(noteId);
            if (note == null || !patient.equals(note.getDemographic_no())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }

            Map<String, Serializable> hashMap = new HashMap<String, Serializable>();
            hashMap.put("noteId", note.getId().toString());
            hashMap.put("note", note.getNote());
            hashMap.put("revision", note.getRevision());
            hashMap.put("obsDate", CachedDateFormats.format(note.getObservation_date(), YYYY_MM_DD_HHMM_PATTERN));
            hashMap.put("editor", this.providerMgr.getProvider(note.getProviderNo()).getFormattedName());
            json = objectMapper.valueToTree(hashMap);
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
        return null;
    }

    /** Resolves a real tickler before its patient or note links can authorize any operation. */
    private Tickler authorizedTickler() {
        String raw = request.getParameter("ticklerNo");
        Integer id;
        try {
            if (raw == null || !raw.matches("[1-9]\\d*")) throw new NumberFormatException();
            id = Integer.valueOf(raw);
        } catch (NumberFormatException invalid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (login == null || !securityInfoManager.hasPrivilege(login, "_tickler", "r", null)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        Tickler tickler = ticklerManager.getTickler(login, id);
        if (tickler == null || tickler.getDemographicNo() == null || tickler.getDemographicNo() <= 0) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return tickler;
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
            HashMap<Program, List<Secrole>> rolesForDemo = getAllProviderAccessibleRolesForDemo(providerNo, demographicNo);
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
        } else if (!demono.matches("\\d+")) {
            // Reject tainted value but fall back to request attribute to avoid crashing callers
            logger.error("Invalid non-numeric demographicNo rejected, falling back to request attribute: {}", LogSafe.sanitize(demono)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
        CaseManagementIssue cIssue = prepareCaseIssue(demographicNo, iss, programId);
        caseManagementMgr.saveAndUpdateCaseIssues(List.of(cIssue));
        return cIssue;
    }

    /** Prepare a new issue for a caller that controls its persistence transaction. */
    private static CaseManagementIssue prepareCaseIssue(String demographicNo, Issue iss, Integer programId) {
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
    public void withSession(Map<String, Object> session) {
        this.mySessionMap = session;
    }

    private void restoreFromSession() {
        if (this.caseNote == null) {
            this.caseNote = new CaseManagementNote();
        }
        
        if (demographicNo != null) {
            String sessionName = ChartProgramContext.formKey(request, demographicNo);
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

    @StrutsParameter
    public void setObservation_date(String date) {
        this.observation_date = date;
    }

    public String getCaseNote_history() {
        return caseNote_history;
    }

    @StrutsParameter
    public void setCaseNote_history(String caseNote_history) {
        this.caseNote_history = caseNote_history;
    }

    public String getDeleteId() {
        return deleteId;
    }

    @StrutsParameter
    public void setDeleteId(String deleteId) {
        this.deleteId = deleteId;
    }

    public String getIncludeIssue() {
        return includeIssue;
    }

    @StrutsParameter
    public void setIncludeIssue(String includeIssue) {
        this.includeIssue = includeIssue;
    }

    @StrutsParameter(depth = 1)
    public List<CheckBoxBean> getIssueCheckList() {
        return issueCheckList;
    }

    @StrutsParameter
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

    @StrutsParameter
    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getMethod() {
        return method;
    }

    @StrutsParameter
    public void setMethod(String method) {
        this.method = method;
    }

    @StrutsParameter(depth = 1)
    public CheckIssueBoxBean[] getNewIssueCheckList() {
        return newIssueCheckList;
    }

    @StrutsParameter
    public void setNewIssueCheckList(CheckIssueBoxBean[] newIssueCheckList) {
        this.newIssueCheckList = newIssueCheckList;
    }

    @StrutsParameter(depth = 1)
    public List getNewIssueList() {
        return newIssueList;
    }

    @StrutsParameter
    public void setNewIssueList(List newIssueList) {
        this.newIssueList = newIssueList;
    }

    public String getNoteId() {
        return noteId;
    }

    @StrutsParameter
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getSearString() {
        return searString;
    }

    @StrutsParameter
    public void setSearString(String searString) {
        this.searString = searString;
    }

    public String getShowList() {
        return showList;
    }

    @StrutsParameter
    public void setShowList(String showList) {
        this.showList = showList;
    }

    public String getSign() {
        return sign;
    }

    @StrutsParameter
    public void setSign(String sign) {
        this.sign = sign;
    }

    @StrutsParameter(depth = 1)
    public CaseManagementNote getCaseNote() {
        return caseNote;
    }

    @StrutsParameter
    public void setCaseNote(CaseManagementNote caseNote) {
        this.caseNote = caseNote;
    }

    public String getDemoNo() {
        return demoNo;
    }

    @StrutsParameter
    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    @StrutsParameter
    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getDemoName() {
        return demoName;
    }

    @StrutsParameter
    public void setDemoName(String demoName) {
        this.demoName = demoName;
    }

    public String getProviderNo() {
        return providerNo;
    }

    @StrutsParameter
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getProgramNo() {
        return programNo;
    }

    @StrutsParameter
    public void setProgramNo(String programNo) {
        this.programNo = programNo;
    }


    @StrutsParameter(depth = 1)
    public CaseManagementCPP getCpp() {
        return cpp;
    }

    @StrutsParameter
    public void setCpp(CaseManagementCPP cpp) {
        this.cpp = cpp;
    }

    public String getCaseNote_note() {
        this.caseNote_note = getCaseNote().getNote();
        return caseNote_note;
    }

    @StrutsParameter
    public void setCaseNote_note(String caseNote_note) {

        this.caseNote.setNote(caseNote_note);
        this.caseNote_note = caseNote_note;
    }

    public String getChain() {
        return chain;
    }

    @StrutsParameter
    public void setChain(String chain) {
        this.chain = chain;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    @StrutsParameter
    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public String getAppointmentDate() {
        return this.appointmentDate;
    }

    @StrutsParameter
    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getStart_time() {
        return this.startTime;
    }

    @StrutsParameter
    public void setStart_time(String startTime) {
        this.startTime = startTime;
    }

    public String getBillRegion() {
        return this.billRegion;
    }

    @StrutsParameter
    public void setBillRegion(String billRegion) {
        this.billRegion = billRegion;
    }

    public String getApptProvider() {
        return this.apptProvider;
    }

    @StrutsParameter
    public void setApptProvider(String apptProvider) {
        this.apptProvider = apptProvider;
    }

    public String getProviderview() {
        return this.providerview;
    }

    @StrutsParameter
    public void setProviderview(String providerview) {
        this.providerview = providerview;
    }

    public boolean isGroupNote() {
        return groupNote;
    }

    @StrutsParameter
    public void setGroupNote(boolean groupNote) {
        this.groupNote = groupNote;
    }

    public String[] getGroupNoteClientIds() {
        return groupNoteClientIds;
    }

    @StrutsParameter
    public void setGroupNoteClientIds(String[] groupNoteClientIds) {
        this.groupNoteClientIds = groupNoteClientIds;
    }

    public String getStartTime() {
        return startTime;
    }

    @StrutsParameter
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public int getGroupNoteTotalAnonymous() {
        return groupNoteTotalAnonymous;
    }

    @StrutsParameter
    public void setGroupNoteTotalAnonymous(int groupNoteTotalAnonymous) {
        this.groupNoteTotalAnonymous = groupNoteTotalAnonymous;
    }

    public String getTrimmedNoteText() {
        return StringUtils.trimToNull(this.getCaseNote_note());
    }

    public Integer getHourOfEncounterTime() {
        return hourOfEncounterTime;
    }

    @StrutsParameter
    public void setHourOfEncounterTime(Integer hourOfEncounterTime) {
        this.hourOfEncounterTime = hourOfEncounterTime;
    }

    public Integer getMinuteOfEncounterTime() {
        return minuteOfEncounterTime;
    }

    @StrutsParameter
    public void setMinuteOfEncounterTime(Integer minuteOfEncounterTime) {
        this.minuteOfEncounterTime = minuteOfEncounterTime;
    }

    public Integer getHourOfEncTransportationTime() {
        return hourOfEncTransportationTime;
    }

    @StrutsParameter
    public void setHourOfEncTransportationTime(Integer hourOfEncTransportationTime) {
        this.hourOfEncTransportationTime = hourOfEncTransportationTime;
    }

    public Integer getMinuteOfEncTransportationTime() {
        return minuteOfEncTransportationTime;
    }

    @StrutsParameter
    public void setMinuteOfEncTransportationTime(Integer minuteOfEncTransportationTime) {
        this.minuteOfEncTransportationTime = minuteOfEncTransportationTime;
    }

    public String getReloadUrl() {
        return reloadUrl;
    }

    @StrutsParameter
    public void setReloadUrl(String reloadUrl) {
        this.reloadUrl = reloadUrl;
    }

    static boolean isAllowedInternalRedirectChain(String chain) {
        return CASE_MANAGEMENT_LIST_CHAIN.equals(StringUtils.trimToEmpty(chain));
    }

    static String caseManagementListRedirectUrl(String contextPath) {
        String redirectUrl = StringUtils.defaultString(contextPath) + CASE_MANAGEMENT_LIST_REDIRECT_PATH;
        if (!RedirectValidationUtils.isValidRelativeRedirect(redirectUrl)) {
            throw new IllegalArgumentException("Unsafe case-management redirect context path");
        }
        return redirectUrl;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: this sink redirects only to the fixed case-management list path under the servlet context after RedirectValidationUtils validates the final root-relative URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a fixed case-management path under the servlet context and is validated with RedirectValidationUtils before sendRedirect")
    private static void sendCaseManagementListRedirect(HttpServletResponse response, String contextPath)
            throws IOException {
        response.sendRedirect(caseManagementListRedirectUrl(contextPath));
    }

    /**
     * Returns a whitelisted Struts result name for the legacy {@code chain} parameter.
     *
     * @param chain raw requested result name
     * @return safe result name, or null when absent or unsafe
     */
    static String sanitizeChainResultName(String chain) {
        String trimmedChain = StringUtils.trimToNull(chain);
        if (trimmedChain == null || !ALLOWED_CHAIN_RESULT_NAMES.contains(trimmedChain)) {
            return null;
        }
        return trimmedChain;
    }

    /**
     * Returns a map of programs and accessible roles for a given provider and demographic.
     * This is an independent copy of the equivalent method formerly in NotePermissions2Action (GPL2-only),
     * mirroring the private copy already present in NotesService (GPL2+).
     */
    private static HashMap<Program, List<Secrole>> getAllProviderAccessibleRolesForDemo(String providerNo, String demoNo) {
        ProgramProviderDAO programProviderDao = (ProgramProviderDAO) SpringUtils.getBean(ProgramProviderDAO.class);
        ProgramAccessDAO programAccessDAO = (ProgramAccessDAO) SpringUtils.getBean(ProgramAccessDAO.class);
        SecroleDao secroleDao = (SecroleDao) SpringUtils.getBean(SecroleDao.class);
        RoleProgramAccessDAO roleProgramAccessDao = (RoleProgramAccessDAO) SpringUtils.getBean(RoleProgramAccessDAO.class);
        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);

        HashMap<Program, List<Secrole>> visibleRoles = new HashMap<Program, List<Secrole>>();

        @SuppressWarnings("unchecked")
        List<ProgramProvider> programProviderList = programProviderDao.getProgramProvidersByProvider(providerNo);

        List<Integer> demoPrograms = new ArrayList<Integer>();
        for (Admission a : admissionDao.getCurrentAdmissions(Integer.parseInt(demoNo))) {
            demoPrograms.add(a.getProgramId());
        }

        for (ProgramProvider provider : programProviderList) {
            if (!demoPrograms.contains(provider.getProgram().getId()))
                continue;

            if (!visibleRoles.containsKey(provider.getProgram())) {
                visibleRoles.put(provider.getProgram(), new ArrayList<Secrole>());
            }

            List<Secrole> roleList = visibleRoles.get(provider.getProgram());
            if (!roleList.contains(provider.getRole())) {
                roleList.add(provider.getRole());

                // This role definitely has access to these permissions -> get role names and add to list
                List<DefaultRoleAccess> defaultAccess = roleProgramAccessDao.getDefaultSpecificAccessRightByRole(provider.getRoleId(), "read%notes");
                for (DefaultRoleAccess access : defaultAccess) {
                    String roleName = access.getAccess_type().getName().substring(5, access.getAccess_type().getName().length() - 6);
                    Secrole role = secroleDao.getRoleByName(roleName);
                    if (!roleList.contains(role))
                        roleList.add(role);
                }

                // This role also has access to these permissions -> add them to the list as well
                List<ProgramAccess> programAccess = programAccessDAO.getProgramAccessListByType(provider.getProgramId(), "read%notes");
                for (ProgramAccess access : programAccess) {
                    if (access.getRoles().contains(provider.getRole())) {
                        String roleName = access.getAccessType().getName().substring(5, access.getAccessType().getName().length() - 6);
                        Secrole role = secroleDao.getRoleByName(roleName);
                        if (!roleList.contains(role))
                            roleList.add(role);
                    }
                }
            }
        }

        return visibleRoles;
    }

}
