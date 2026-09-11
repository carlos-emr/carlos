/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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

package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.data.EctProgram;

import java.text.SimpleDateFormat;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;

import java.util.Locale;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RxWriteToEncounter2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private RxSessionBean rxSessionBean = null;


    /**
     * Appends prescription text to the originating patient's encounter.
     *
     * <p>Known pre-write rejection returns HTTP 405 (non-POST) or 409 (missing or
     * changed Rx patient context), with {@code X-Carlos-Encounter-Write: not-written}.
     * Successful completion sets that header to {@code written}. Exceptions after
     * persistence may mean the write committed: absence of the header is never
     * proof that retrying is safe.</p>
     *
     * @return {@link #NONE}, since this action owns the HTTP response
     * @throws SecurityException if the session or required prescription write privilege is absent
     * @throws IOException if request/response processing fails
     * @throws ServletException if servlet processing fails
     */
    @Override
    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, "w");
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return rejectBeforeWrite(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        HttpSession session = request.getSession(false);
        rxSessionBean = session == null ? null : (RxSessionBean) session.getAttribute("RxSessionBean");
        if (rxSessionBean == null) {
            return rejectBeforeWrite(HttpServletResponse.SC_CONFLICT);
        }
        String demographicNo = String.valueOf(rxSessionBean.getDemographicNo());
        // Bind the append to the originating prescription window. Another tab may
        // have changed RxSessionBean since this window rendered or queued its fax.
        if (rxSessionBean.getDemographicNo() <= 0
                || !demographicNo.equals(request.getParameter("expectedDemographicNo"))) {
            return rejectBeforeWrite(HttpServletResponse.SC_CONFLICT);
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, demographicNo)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        String programNo = new EctProgram(session).getProgram(session.getAttribute("user").toString());


        CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
        CaseManagementTmpSaveDao caseManagementTmpSaveDao = SpringUtils.getBean(CaseManagementTmpSaveDao.class);
        CaseManagementNote note = getLastSaved(request, demographicNo, loggedInInfo.getLoggedInProviderNo(), caseManagementMgr);
        CaseManagementTmpSave tmpSave = caseManagementMgr.getTmpSave(loggedInInfo.getLoggedInProviderNo(), demographicNo, programNo);
        Date today = new Date();
        if (tmpSave != null) {
            // Persist clinical text, not a JavaScript string literal. Apply output
            // encoding at the rendering boundary, as for the non-draft paths below.
            String noteBody = generateNote(loggedInInfo, request.getParameter("body"), false);

            if (tmpSave.getNoteId() > 0) {
                note = caseManagementMgr.getNote(String.valueOf(tmpSave.getNoteId()));
                if (note.getUpdate_date().after(tmpSave.getUpdateDate())) {
                    note.setNote(tmpSave.getNote() + "\n" + noteBody);
                    note.setUpdate_date(today);
                    caseManagementMgr.saveNoteSimple(note);
                } else {
                    createAndSaveNewNote(loggedInInfo, demographicNo, programNo, caseManagementMgr, today, tmpSave.getNote() + "\n" + noteBody, request.getParameter("sign"));
                }
            } else {
                createAndSaveNewNote(loggedInInfo, demographicNo, programNo, caseManagementMgr, today, tmpSave.getNote() + "\n" + noteBody, request.getParameter("sign"));
            }
            caseManagementTmpSaveDao.remove(tmpSave.getProviderNo(), tmpSave.getDemographicNo(), tmpSave.getProgramId());
        } else if (note != null) {
            String noteBody = generateNote(loggedInInfo, request.getParameter("body"), false);
            note.setNote(note.getNote() + "\n" + noteBody);
            note.setUpdate_date(today);
            caseManagementMgr.saveNoteSimple(note);
        } else {
            String noteBody = generateNote(loggedInInfo, request.getParameter("body"), true);
            createAndSaveNewNote(loggedInInfo, demographicNo, programNo, caseManagementMgr, today, noteBody, request.getParameter("sign"));
        }
        // Never emit this for an exception after saveNoteSimple: the append may
        // already have committed, so the client must not blindly repeat it.
        response.setHeader("X-Carlos-Encounter-Write", "written");
        return NONE;
    }

    private String rejectBeforeWrite(int status) {
        response.setHeader("X-Carlos-Encounter-Write", "not-written");
        response.setStatus(status);
        return NONE;
    }

    private String generateNote(LoggedInInfo loggedInInfo, String noteBody, boolean addDateString) {

        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        String formattedDate = df.format(new Date());

        String dateString = "[" + formattedDate + " .:Rx]\n\n";
        String note = addDateString ? dateString : "";
        note += noteBody;
        return note;
    }

    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (loggedInInfo == null || !securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
    }

    public CaseManagementNote getLastSaved(HttpServletRequest request, String demono, String providerNo, CaseManagementManager caseManagementMgr) {
        HttpSession session = request.getSession();
        String programId = (String) session.getAttribute("case_program_id");
        return caseManagementMgr.getLastSaved(programId, demono, providerNo);
    }

    private void createAndSaveNewNote(LoggedInInfo loggedInInfo, String demographicNo, String programNo, CaseManagementManager caseManagementMgr, Date today, String noteBody, String signNote) {
        CaseManagementNote note;
        note = new CaseManagementNote();
        note.setObservation_date(today);
        note.setCreate_date(today);
        note.setDemographic_no(demographicNo);
        note.setProvider(loggedInInfo.getLoggedInProvider());
        note.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        note.setSigned(false);
        note.setSigning_provider_no("");
        if ("sign".equals(signNote)) {
            note.setSigned(true);
            note.setSigning_provider_no(loggedInInfo.getLoggedInProviderNo());
        }
        note.setProgram_no(programNo);
        note.setNote(noteBody);
        note.setIncludeissue(false);
        ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
        String role;
        try {
            role = String.valueOf((programManager.getProgramProvider(note.getProviderNo(), note.getProgram_no())).getRole().getId());
        } catch (Exception e) {
            role = "0";
        }
        note.setReporter_caisi_role(role);
        note.setReporter_program_team("0");
        note.setLocked(false);
        note.setHistory(noteBody);
        note.setUpdate_date(today);
        caseManagementMgr.saveNoteSimple(note);
    }

}
