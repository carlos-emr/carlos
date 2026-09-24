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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugReasonDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public final class RxReason2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Opens the drug-reason popup for a patient's drug, or, on POST, adds or archives a reason.
     *
     * <p>SearchDrug3 opens the popup with a GET naming {@code demographicNo} and {@code drugId};
     * that view needs {@code _rx} read, for the patient too. Adding and archiving reasons write
     * the patient's chart: they are POST-only (SelectReason.jsp's forms POST, CSRFGuard does not
     * check GET) and need {@code _rx} write, globally and for the patient (#3908). A GET that
     * names a write ({@code method=addDrugReason} / {@code archiveReason}) is refused with 405.</p>
     *
     * @return {@code "success"} to render SelectReason.jsp, {@code "close"} after a reason is
     *         added, or {@code NONE} after a 405
     * @throws java.io.IOException when the 405 cannot be sent
     */
    public String execute() throws java.io.IOException {
        String method = request.getParameter("method");
        boolean write = "archiveReason".equals(method) || "addDrugReason".equals(method);
        if (!"POST".equals(request.getMethod())) {
            if (write) {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
                return NONE;
            }
            return view();
        }
        if ("archiveReason".equals(method)) {
            return archiveReason();
        }
        return addDrugReason();
    }

    /** The popup for one of the patient's drugs; reads only. */
    private String view() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        int[] drugAndPatient = requireDrugOfPatient("r");
        request.setAttribute("drugId", drugAndPatient[0]);
        request.setAttribute("demoNo", drugAndPatient[1]);
        return SUCCESS;
    }

    /**
     * The request's drug and patient, after authorising the caller for that patient at
     * {@code privilege} (patient-level {@code _rx} and record access) and checking that the drug
     * is that patient's.
     *
     * @return {@code {drugId, demographicNo}}
     * @throws SecurityException when the ids are malformed, the caller may not access the patient,
     *                           or the drug is missing or another patient's
     */
    private int[] requireDrugOfPatient(String privilege) {
        String drugIdStr = request.getParameter("drugId");
        String demographicNo = request.getParameter("demographicNo");
        if (drugIdStr == null || !drugIdStr.matches("\\d{1,9}")
                || demographicNo == null || !demographicNo.matches("\\d{1,9}")) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        int drugId = Integer.parseInt(drugIdStr);
        int demographic = Integer.parseInt(demographicNo);
        RxRequestedPatientAccess.requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                demographic, "_rx", privilege);
        Drug drug = SpringUtils.getBean(DrugDao.class).find(drugId);
        if (drug == null || drug.getDemographicId() == null || drug.getDemographicId() != demographic) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        return new int[] {drugId, demographic};
    }

    private boolean refuseUnlessPost() {
        if ("POST".equals(request.getMethod())) {
            return false;
        }
        try {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return true;
    }

    /*
     * Needed for a new Drug Reason
     *
    private Integer drugId = null;
    private String codingSystem = null;    // (icd9,icd10,etc...) OR protocol
    private String code = null;   // (250 (for icd9) or could be the protocol identifier )
    private String comments = null;
    private Boolean primaryReasonFlag;
    private String providerNo = null;
    private Integer demographicNo = null;
     */
    public String addDrugReason() {
        if (refuseUnlessPost()) {
            return NONE;
        }
        // Files a reason on the patient's drug: _rx write, globally and for the patient (#3908).
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
        Icd9Dao icd9Dao = (Icd9Dao) SpringUtils.getBean(Icd9Dao.class);

        String codingSystem = request.getParameter("codingSystem");
        String primaryReasonFlagStr = request.getParameter("primaryReasonFlag");
        String comments = request.getParameter("comments");
        String code = request.getParameter("jsonDxSearch");

        String drugIdStr = request.getParameter("drugId");
        String demographicNo = request.getParameter("demographicNo");
        String providerNo = (String) request.getSession().getAttribute("user");

        // The reason is filed on this patient's drug: patient-level _rx write and record access,
        // and the drug must be that patient's (#3908).
        requireDrugOfPatient("w");

        request.setAttribute("drugId", Integer.parseInt(drugIdStr));
        request.setAttribute("demoNo", Integer.parseInt(demographicNo));

        if (code != null && code.trim().equals("")) {
            request.setAttribute("message", getText("SelectReason.error.codeEmpty"));
            return SUCCESS;
        }

        List<Icd9> list = icd9Dao.getIcd9Code(code);
        if (list.size() == 0) {
            request.setAttribute("message", getText("SelectReason.error.codeValid"));
            return SUCCESS;
        }

        if (drugReasonDao.hasReason(Integer.parseInt(drugIdStr), codingSystem, code, true)) {
            request.setAttribute("message", getText("SelectReason.error.duplicateCode"));
            return SUCCESS;
        }


        boolean primaryReasonFlag = true;
        if (!"true".equals(primaryReasonFlagStr)) {
            primaryReasonFlag = false;
        }

        DrugReason dr = new DrugReason();

        dr.setDrugId(Integer.parseInt(drugIdStr));
        dr.setProviderNo(providerNo);
        dr.setDemographicNo(Integer.parseInt(demographicNo));

        dr.setCodingSystem(codingSystem);
        dr.setCode(code);
        dr.setComments(comments);
        dr.setPrimaryReasonFlag(primaryReasonFlag);
        dr.setArchivedFlag(false);
        dr.setDateCoded(new Date());

        drugReasonDao.addNewDrugReason(dr);

        String ip = request.getRemoteAddr();
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ADD, LogConst.CON_DRUGREASON, "" + dr.getId(), ip, demographicNo, dr.getAuditString());

        return "close";
    }

    /**
     * Used for archiving the current reason, will set the value of the archived flag to true and set the reason.
     * And will show a success message to the user
     * @return "success" which will redirect back to the "SelectReason.jsp" page
     */
    public String archiveReason() {
        if (refuseUnlessPost()) {
            return NONE;
        }
        // Archiving changes the reason's patient's chart: _rx write, globally and for the patient (#3908).
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
        String reasonId = request.getParameter("reasonId");
        String archiveReason = request.getParameter("archiveReason");

        if (reasonId == null || !reasonId.matches("\\d{1,9}")) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        DrugReason drugReason = drugReasonDao.find(Integer.parseInt(reasonId));
        // Archiving changes the reason's patient's chart: authorise that patient (#3908).
        if (drugReason == null || drugReason.getDemographicNo() == null) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        RxRequestedPatientAccess.requirePatient(securityInfoManager, LoggedInInfo.getLoggedInInfoFromSession(request),
                drugReason.getDemographicNo(), "_rx", "w");

        drugReason.setArchivedFlag(true);
        drugReason.setArchivedReason(archiveReason);

        drugReasonDao.merge(drugReason);

        request.setAttribute("drugId", drugReason.getDrugId());
        request.setAttribute("demoNo", drugReason.getDemographicNo());

        String ip = request.getRemoteAddr();
        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ARCHIVE, LogConst.CON_DRUGREASON, "" + drugReason.getId(), ip, "" + drugReason.getDemographicNo(), drugReason.getAuditString());

        request.setAttribute("message", getText("SelectReason.msg.archived"));
        return SUCCESS;
    }
}
