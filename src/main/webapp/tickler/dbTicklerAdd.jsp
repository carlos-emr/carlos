<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>

<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="org.springframework.web.context.WebApplicationContext" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Tickler" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.TicklerLink" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.managers.TicklerManager" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.CaseManagementEntry2Action" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.Issue" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.data.EctProgram" %>
<%@ page import="io.github.carlos_emr.carlos.utility.EncounterUtil" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.time.LocalDateTime" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
%>

<%!
    TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);

%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String module = "", module_id = "", doctype = "", docdesc = "", docxml = "", doccreator = "", docdate = "", docfilename = "", docpriority = "", docassigned = "";
    module_id = request.getParameter("demographic_no");
    doccreator = request.getParameter("user_no");
    docdate = request.getParameter("xml_appointment_date");
    docfilename = request.getParameter("ticklerMessage");
    docpriority = request.getParameter("priority");
    docassigned = request.getParameter("task_assigned_to");

    String docType = request.getParameter("docType");
    String docId = request.getParameter("docId");


    Tickler tickler = new Tickler();
    if (module_id == null || module_id.trim().isEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing demographic_no");
        return;
    }
    try {
        tickler.setDemographicNo(Integer.parseInt(module_id));
    } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic_no");
        return;
    }
    tickler.setUpdateDate(new java.util.Date());
    if (docpriority != null && docpriority.equalsIgnoreCase("High")) {
        tickler.setPriority(Tickler.PRIORITY.High);
    }
    if (docpriority != null && docpriority.equalsIgnoreCase("Low")) {
        tickler.setPriority(Tickler.PRIORITY.Low);
    }
    tickler.setTaskAssignedTo(docassigned);
    tickler.setCreator(doccreator);
    tickler.setMessage(docfilename);
    Date serviceDate = UtilDateUtilities.StringToDate(docdate);
    if (serviceDate == null) {
        serviceDate = new Date();
    }
    tickler.setServiceDate(serviceDate);
    tickler.setCreateDate(new Date());

    boolean rowsAffected = false;
    try {
        ticklerManager.addTickler(loggedInInfo, tickler);
        rowsAffected = true;
    } catch (Exception e) {
        MiscUtils.getLogger().error("Failed to add tickler for demographicNo=" + module_id, e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save tickler");
        return;
    }

    int ticklerNo = tickler.getId();
    boolean ticklerLinkFailed = false;
    if (docType != null && docId != null && !docType.trim().equals("") && !docId.trim().equals("") && !docId.equalsIgnoreCase("null")) {
        if (ticklerNo > 0) {
            try {
                TicklerLink tLink = new TicklerLink();
                tLink.setTableId(Long.parseLong(docId));
                tLink.setTableName(docType);
                tLink.setTicklerNo(new Long(ticklerNo).intValue());
                TicklerLinkDao ticklerLinkDao = (TicklerLinkDao) SpringUtils.getBean(TicklerLinkDao.class);
                ticklerLinkDao.save(tLink);
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid docId format for TicklerLink: ticklerNo=" + ticklerNo + ", docId=" + docId, e);
                ticklerLinkFailed = true;
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to save TicklerLink for ticklerNo=" + ticklerNo + ", docType=" + docType + ", docId=" + docId, e);
                ticklerLinkFailed = true;
            }
        }
    }

    // Write tickler message to the patient's encounter chart as a CaseManagementNote.
    // Follows the same pattern as CaseManagementEntry2Action.ticklerSaveNote().
    boolean writeToEncounter = "true".equals(request.getParameter("writeToEncounter"));
    boolean writeToEncounterFailed = false;
    if (writeToEncounter && rowsAffected && ticklerNo > 0) {
        try {
            Provider loggedInProvider = loggedInInfo.getLoggedInProvider();
            Date creationDate = new Date();

            CaseManagementNote cmn = new CaseManagementNote();
            cmn.setAppointmentNo(0);
            cmn.setArchived(false);
            cmn.setCreate_date(creationDate);
            cmn.setDemographic_no(module_id);
            cmn.setEncounter_type(EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue());
            cmn.setNote(docfilename);
            cmn.setObservation_date(creationDate);
            cmn.setProviderNo(loggedInProvider.getProviderNo());
            cmn.setRevision("1");
            cmn.setSigned(true);
            cmn.setSigning_provider_no(loggedInProvider.getProviderNo());
            cmn.setUpdate_date(creationDate);
            cmn.setHistory(docfilename);
            cmn.setReporter_program_team("null");

            String prog_no = new EctProgram(request.getSession()).getProgram(loggedInProvider.getProviderNo());
            cmn.setProgram_no(prog_no);

            CaseManagementEntry2Action.determineNoteRole(cmn, loggedInProvider.getProviderNo(), module_id);

            CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
            caseManagementMgr.saveNoteSimple(cmn);

            // Link the encounter note to this tickler
            CaseManagementNoteLink link = new CaseManagementNoteLink();
            link.setNoteId(cmn.getId());
            link.setTableId(Long.valueOf(ticklerNo));
            link.setTableName(CaseManagementNoteLink.TICKLER);
            CaseManagementNoteLinkDAO noteLinkDao = SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
            noteLinkDao.save(link);

            // Associate with TicklerNote issue so it appears in the encounter CPP section
            IssueDAO issueDao = SpringUtils.getBean(IssueDAO.class);
            Issue issue = issueDao.findIssueByTypeAndCode("system", "TicklerNote");
            if (issue != null) {
                CaseManagementIssue cmi = caseManagementMgr.getIssueById(module_id, issue.getId().toString());
                if (cmi == null) {
                    cmi = new CaseManagementIssue();
                    cmi.setAcute(false);
                    cmi.setCertain(false);
                    cmi.setDemographic_no(Integer.valueOf(module_id));
                    cmi.setIssue_id(issue.getId());
                    cmi.setMajor(false);
                    cmi.setProgram_id(Integer.parseInt(cmn.getProgram_no()));
                    cmi.setResolved(false);
                    cmi.setType(issue.getRole());
                    cmi.setUpdate_date(creationDate);
                    CaseManagementIssueDAO caseManagementIssueDao = SpringUtils.getBean(CaseManagementIssueDAO.class);
                    caseManagementIssueDao.saveIssue(cmi);
                }
                cmn.getIssues().add(cmi);
                caseManagementMgr.saveNoteSimple(cmn);
            } else {
                MiscUtils.getLogger().warn("TicklerNote issue not found in database — tickler note saved without issue link");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to write tickler to encounter for ticklerNo=" + ticklerNo + ", demographicNo=" + module_id, e);
            writeToEncounterFailed = true;
        }
    }

    String parentAjaxId = request.getParameter("parentAjaxId");
    String updateParent = request.getParameter("updateParent");

    if (rowsAffected && !ticklerLinkFailed) {
%>
<%-- ticklerAdd.jsp reads this element to confirm the save succeeded before closing --%>
<span id="tickler-save-ok" style="display:none;"></span>
<% if (writeToEncounterFailed) { %>
<span id="tickler-write-encounter-failed" style="display:none;"></span>
<% } %>
<%} else if (ticklerLinkFailed) {
    // Tickler was saved but the document link failed. Emit both sentinels so the
    // iframe.onload in ticklerAdd.jsp proceeds with close/refresh while showing a warning.
%>
<span id="tickler-save-ok" style="display:none;"></span>
<span id="tickler-save-ok-link-failed" style="display:none;"></span>
<%}%>
