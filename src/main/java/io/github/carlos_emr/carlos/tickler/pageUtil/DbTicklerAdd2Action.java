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

package io.github.carlos_emr.carlos.tickler.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.CaseManagementEntry2Action;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Date;
import java.util.ResourceBundle;

/**
 * Struts2 action that migrates the server-side logic from {@code tickler/dbTicklerAdd.jsp}.
 *
 * <p>Processes POST form submissions from {@code ticklerAdd.jsp} to create new ticklers.
 * Optionally writes the tickler message to the patient's encounter chart as a
 * {@link CaseManagementNote} when the {@code writeToEncounter} parameter is set.
 *
 * <p>On success the action forwards to {@code /WEB-INF/jsp/tickler/dbTicklerAdd.jsp},
 * which renders the sentinel {@code <span>} elements read by {@code ticklerAdd.jsp}'s
 * {@code iframe.onload} handler:
 * <ul>
 *   <li>{@code #tickler-save-ok} – tickler saved successfully</li>
 *   <li>{@code #tickler-save-ok-link-failed} – saved but document link failed</li>
 *   <li>{@code #tickler-write-encounter-failed} – saved but encounter note failed</li>
 * </ul>
 *
 * <p>Security: requires {@code _tickler} write privilege. POST method enforced.
 *
 * @since 2026-04-05
 */
public final class DbTicklerAdd2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private TicklerLinkDao ticklerLinkDao = SpringUtils.getBean(TicklerLinkDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new tickler, optionally links it to a document, and optionally writes
     * the message to the patient's encounter chart.
     *
     * @return {@link #SUCCESS} to forward to the view JSP, or {@link #NONE} on redirect/error
     * @throws SecurityException if the user lacks {@code _tickler} write privilege
     */
    @Override
    public String execute() throws Exception {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Security check — requires _tickler write privilege
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", "w", null)) {
            response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");
            return NONE;
        }

        // Enforce POST
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        String moduleId = request.getParameter("demographic_no");
        String docCreator = request.getParameter("user_no");
        String docDate = request.getParameter("xml_appointment_date");
        String ticklerMessage = request.getParameter("ticklerMessage");
        String priority = request.getParameter("priority");
        String taskAssignedTo = request.getParameter("task_assigned_to");
        String docType = request.getParameter("docType");
        String docId = request.getParameter("docId");
        String parentAjaxId = request.getParameter("parentAjaxId");
        String updateParent = request.getParameter("updateParent");

        if (moduleId == null || moduleId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing demographic_no");
            return NONE;
        }

        Tickler tickler = new Tickler();
        try {
            tickler.setDemographicNo(Integer.parseInt(moduleId));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic_no");
            return NONE;
        }

        tickler.setUpdateDate(new Date());
        if (priority != null && priority.equalsIgnoreCase("High")) {
            tickler.setPriority(Tickler.PRIORITY.High);
        } else if (priority != null && priority.equalsIgnoreCase("Low")) {
            tickler.setPriority(Tickler.PRIORITY.Low);
        }
        tickler.setTaskAssignedTo(taskAssignedTo);
        tickler.setCreator(docCreator);
        tickler.setMessage(ticklerMessage);

        Date serviceDate = UtilDateUtilities.StringToDate(docDate);
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
            MiscUtils.getLogger().error("Failed to add tickler", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save tickler");
            return NONE;
        }

        int ticklerNo = tickler.getId();

        // Optionally link the new tickler to a document
        boolean ticklerLinkFailed = false;
        if (docType != null && docId != null
                && !docType.trim().isEmpty() && !docId.trim().isEmpty()
                && !docId.equalsIgnoreCase("null")) {
            if (ticklerNo > 0) {
                try {
                    TicklerLink tLink = new TicklerLink();
                    tLink.setTableId(Long.parseLong(docId));
                    tLink.setTableName(docType);
                    tLink.setTicklerNo(ticklerNo);
                    ticklerLinkDao.save(tLink);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().error(
                            "Invalid docId format for TicklerLink: ticklerNo=" + ticklerNo + ", docId=" + docId, e);
                    ticklerLinkFailed = true;
                } catch (Exception e) {
                    MiscUtils.getLogger().error(
                            "Failed to save TicklerLink for ticklerNo=" + ticklerNo
                            + ", docType=" + docType + ", docId=" + docId, e);
                    ticklerLinkFailed = true;
                }
            }
        }

        // Optionally write the tickler message to the patient's encounter chart
        boolean writeToEncounter = "true".equals(request.getParameter("writeToEncounter"));
        boolean writeToEncounterFailed = false;
        if (writeToEncounter && rowsAffected && ticklerNo > 0) {
            try {
                Provider loggedInProvider = loggedInInfo.getLoggedInProvider();
                Date creationDate = new Date();

                // Build a signed encounter note mirroring CaseManagementEntry2Action.ticklerSaveNote()
                CaseManagementNote cmn = new CaseManagementNote();
                cmn.setAppointmentNo(0);
                cmn.setArchived(false);
                cmn.setCreate_date(creationDate);
                cmn.setDemographic_no(moduleId);
                // Use the "global.tickler" i18n label as the encounter type to distinguish
                // tickler-originated notes from clinical face-to-face encounter notes
                String ticklerEncounterType = ResourceBundle
                        .getBundle("oscarResources", request.getLocale())
                        .getString("global.tickler");
                cmn.setEncounter_type(ticklerEncounterType);
                cmn.setNote(ticklerMessage);
                cmn.setObservation_date(creationDate);
                cmn.setProviderNo(loggedInProvider.getProviderNo());
                cmn.setRevision("1");
                cmn.setSigned(true);
                cmn.setSigning_provider_no(loggedInProvider.getProviderNo());
                cmn.setUpdate_date(creationDate);
                cmn.setHistory(ticklerMessage);
                cmn.setReporter_program_team("null");

                // Resolve the provider's default program and CAISI role for the note
                String progNo = new EctProgram(request.getSession()).getProgram(loggedInProvider.getProviderNo());
                cmn.setProgram_no(progNo);
                CaseManagementEntry2Action.determineNoteRole(cmn, loggedInProvider.getProviderNo(), moduleId);

                CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
                caseManagementMgr.saveNoteSimple(cmn);

                // Link the encounter note to this tickler
                CaseManagementNoteLink noteLink = new CaseManagementNoteLink();
                noteLink.setNoteId(cmn.getId());
                noteLink.setTableId(Long.valueOf(ticklerNo));
                noteLink.setTableName(CaseManagementNoteLink.TICKLER);
                CaseManagementNoteLinkDAO noteLinkDao = SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
                noteLinkDao.save(noteLink);

                // Associate with TicklerNote issue so it appears in the encounter CPP section
                IssueDAO issueDao = SpringUtils.getBean(IssueDAO.class);
                Issue issue = issueDao.findIssueByTypeAndCode("system", "TicklerNote");
                if (issue != null) {
                    CaseManagementIssue cmi = caseManagementMgr.getIssueById(moduleId, issue.getId().toString());
                    if (cmi == null) {
                        cmi = new CaseManagementIssue();
                        cmi.setAcute(false);
                        cmi.setCertain(false);
                        cmi.setDemographic_no(Integer.valueOf(moduleId));
                        cmi.setIssue_id(issue.getId());
                        cmi.setMajor(false);
                        String progNoStr = cmn.getProgram_no();
                        int programId = 0;
                        if (progNoStr != null && !progNoStr.isEmpty()) {
                            try {
                                programId = Integer.parseInt(progNoStr);
                            } catch (NumberFormatException nfe) {
                                MiscUtils.getLogger().error(
                                        "Non-numeric program_no on CaseManagementNote: " + progNoStr, nfe);
                            }
                        }
                        cmi.setProgram_id(programId);
                        cmi.setResolved(false);
                        cmi.setType(issue.getRole());
                        cmi.setUpdate_date(creationDate);
                        CaseManagementIssueDAO caseManagementIssueDao =
                                SpringUtils.getBean(CaseManagementIssueDAO.class);
                        caseManagementIssueDao.saveIssue(cmi);
                    }
                    cmn.getIssues().add(cmi);
                    caseManagementMgr.saveNoteSimple(cmn);
                } else {
                    MiscUtils.getLogger().warn(
                            "TicklerNote issue not found in database — tickler note saved without issue link");
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error(
                        "Failed to write tickler to encounter for ticklerNo=" + ticklerNo, e);
                writeToEncounterFailed = true;
            }
        }

        request.setAttribute("rowsAffected", rowsAffected);
        request.setAttribute("ticklerLinkFailed", ticklerLinkFailed);
        request.setAttribute("writeToEncounterFailed", writeToEncounterFailed);
        request.setAttribute("parentAjaxId", parentAjaxId);
        request.setAttribute("updateParent", updateParent);

        return SUCCESS;
    }
}
