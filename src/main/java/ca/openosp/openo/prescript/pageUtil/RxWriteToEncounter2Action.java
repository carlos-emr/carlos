//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.prescript.pageUtil;

import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.CaseManagementTmpSaveDao;
import ca.openosp.openo.commn.model.CaseManagementTmpSave;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.data.EctProgram;

import java.text.SimpleDateFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import org.owasp.encoder.Encode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class RxWriteToEncounter2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private RxSessionBean rxSessionBean = null;


    public String execute() throws IOException, ServletException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        checkPrivilege(loggedInInfo, "w");

        HttpSession session = request.getSession();
        rxSessionBean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (rxSessionBean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String demographicNo = String.valueOf(rxSessionBean.getDemographicNo());
        String programNo = new EctProgram(session).getProgram(session.getAttribute("user").toString());


        CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);
        CaseManagementTmpSaveDao caseManagementTmpSaveDao = SpringUtils.getBean(CaseManagementTmpSaveDao.class);
        CaseManagementNote note = getLastSaved(request, demographicNo, loggedInInfo.getLoggedInProviderNo(), caseManagementMgr);
        CaseManagementTmpSave tmpSave = caseManagementMgr.getTmpSave(loggedInInfo.getLoggedInProviderNo(), demographicNo, programNo);
        Date today = new Date();
        if (tmpSave != null) {
            String noteBody = generateNote(loggedInInfo, Encode.forJavaScript(request.getParameter("body")), false);

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


        return null;
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
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", privilege, null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
    }

    public CaseManagementNote getLastSaved(HttpServletRequest request, String demono, String providerNo, CaseManagementManager caseManagementMgr) {
        HttpSession session = request.getSession();
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
        note.setPassword(null);
        note.setLocked(false);
        note.setHistory(noteBody);
        note.setUpdate_date(today);
        caseManagementMgr.saveNoteSimple(note);
    }

}