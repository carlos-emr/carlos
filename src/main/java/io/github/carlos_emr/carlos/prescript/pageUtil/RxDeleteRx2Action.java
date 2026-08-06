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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts 2 action for managing prescription deletion and discontinuation operations.
 * <p>
 * This action handles multiple prescription management operations including:
 * <ul>
 * <li>Deleting single or multiple prescriptions</li>
 * <li>Discontinuing prescriptions with reason tracking</li>
 * <li>Clearing prescription stash and re-prescription lists</li>
 * <li>Deleting prescriptions when closing the prescription dialog box</li>
 * </ul>
 * <p>
 * All deletion operations archive the drug record rather than performing hard deletes,
 * maintaining audit trail compliance for healthcare data.
 *
 * @since 2006-04-20
 */
public final class RxDeleteRx2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DrugDao drugDao = SpringUtils.getBean(DrugDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String PRIVILEGE_UPDATE = "u";

    @Override
    public String execute()
            throws IOException, ServletException {
        String method = request.getParameter("parameterValue");
        if ("Delete2".equals(method)) {
            return Delete2();
        } else if ("DeleteRxOnCloseRxBox".equals(method)) {
            return DeleteRxOnCloseRxBox();
        } else if ("clearStash".equals(method)) {
            return clearStash();
        } else if ("clearReRxDrugList".equals(method)) {
            return clearReRxDrugList();
        } else if ("Discontinue".equals(method)) {
            return Discontinue();
        }
        checkPrivilege(request, PRIVILEGE_UPDATE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String ip = request.getRemoteAddr();
        try {

            String[] drugArr = drugList.split(",");
            int drugId;
            int i;

            // First pass: validate ownership of every requested drug before archiving any.
            List<Drug> drugsToDelete = new ArrayList<>();
            for (i = 0; i < drugArr.length; i++) {
                try {
                    drugId = Integer.parseInt(drugArr[i]);

                } catch (Exception e) {
                    break;
                }
                Drug drug = drugDao.find(drugId);
                if (drug == null || drug.getDemographicId() != bean.getDemographicNo()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return NONE;
                }
                drugsToDelete.add(drug);
            }

            // Second pass: all requested drugs passed ownership validation, safe to archive.
            for (Drug drug : drugsToDelete) {
                setDrugDelete(drug);
                drugDao.merge(drug);
                LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PRESCRIPTION, String.valueOf(drug.getId()), ip, "" + bean.getDemographicNo(), drug.getAuditString());
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    private void setDrugDelete(Drug drug) {
        drug.setArchived(true);
        drug.setArchivedDate(new Date());
        drug.setArchivedReason(Drug.DELETED);
    }

    public String Delete2()
            throws IOException {

        MiscUtils.getLogger().debug("===========================Delete2 RxDeleteRx2Action========================");
        checkPrivilege(request, PRIVILEGE_UPDATE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        String ip = request.getRemoteAddr();
        try {
            String deleteRxId = (request.getParameter("deleteRxId").split("_"))[1];

            Drug drug = drugDao.find(Integer.parseInt(deleteRxId));
            if (drug == null || drug.getDemographicId() != bean.getDemographicNo()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return NONE;
            }
            setDrugDelete(drug);
            drugDao.merge(drug);
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PRESCRIPTION, deleteRxId, ip, "" + bean.getDemographicNo(), drug.getAuditString());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        MiscUtils.getLogger().debug("===========================END Delete2 RxDeleteRx2Action========================");
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String DeleteRxOnCloseRxBox()
            throws IOException {

        MiscUtils.getLogger().debug("===========================DeleteRxOnCloseRxBox RxDeleteRx2Action========================");
        checkPrivilege(request, PRIVILEGE_UPDATE);

        String randomId = request.getParameter("randomId");

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        if (randomId != null) {
            HashMap rd = bean.getRandomIdDrugIdPair();
            Integer drugId = (Integer) rd.get(Long.parseLong(randomId));
            MiscUtils.getLogger().debug("111drugId=" + drugId + "--randomId=" + randomId);
            if (drugId != null) {
                String ip = request.getRemoteAddr();
                try {
                    Drug drug = drugDao.find(drugId);
                    setDrugDelete(drug);
                    drugDao.merge(drug);
                    LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DELETE, LogConst.CON_PRESCRIPTION, drugId.toString(), ip, "" + bean.getDemographicNo(), drug.getAuditString());
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }
            HashMap hm = new HashMap();
            hm.put("drugId", drugId);
            ObjectNode jsonObject = objectMapper.valueToTree(hm);
            MiscUtils.getLogger().debug("jsonObject=" + jsonObject.toString());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(jsonObject.toString());
            MiscUtils.getLogger().debug("===========================END DeleteRxOnCloseRxBox RxDeleteRx2Action========================");
            return NONE;
        }
        MiscUtils.getLogger().debug("===========================END DeleteRxOnCloseRxBox RxDeleteRx2Action========================");
        return null;
    }

    public String clearStash()
            throws IOException {
        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        bean.clearStash();
        return "successClearStash";
    }

    public String clearReRxDrugList()
            throws IOException {
        checkPrivilege(request, PRIVILEGE_UPDATE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }
        bean.clearReRxDrugIdList();
        return null;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String Discontinue() throws IOException {
        checkPrivilege(request, PRIVILEGE_UPDATE);

        RxSessionBean bean = (RxSessionBean) request.getSession().getAttribute("RxSessionBean");
        if (bean == null) {
            response.sendRedirect("error.html");
            return null;
        }

        String idStr = request.getParameter("drugId");
        int id = Integer.parseInt(idStr);

        String reason = request.getParameter("reason");

        String ip = request.getRemoteAddr();

        Drug drug = drugDao.find(id);
        if (drug == null || drug.getDemographicId() != bean.getDemographicNo()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        Date date = new Date();
        String logStatement = drug + " Changing end date to :" + date;
        drug.setArchivedDate(date);
        drug.setArchived(true);
        drug.setArchivedReason(reason);

        drugDao.merge(drug);
        try {
            createDiscontinueNote(request, bean.getDemographicNo());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.DISCONTINUE, LogConst.CON_PRESCRIPTION, "" + drug.getId(), ip, "" + drug.getDemographicId(), logStatement);

        Hashtable d = new Hashtable();
        d.put("id", "" + id);
        d.put("reason", reason);
        response.setContentType("application/json");
        ObjectNode jsonArray = (ObjectNode) objectMapper.valueToTree(d);
        response.getWriter().write(jsonArray.toString());

        return NONE;
    }

    private void createDiscontinueNote(HttpServletRequest request, int sessionDemographicNo) {
        CaseManagementNote cmn = new CaseManagementNote();
        Date now = EDocUtil.getDmsDateTimeAsDate();
        String demoNo = String.valueOf(sessionDemographicNo);
        String idStr = request.getParameter("drugId");
        String user = request.getSession().getAttribute("user").toString();
        String strNote = request.getParameter("drugSpecial") + "\nDiscontinued reason: " + request.getParameter("reason") + "\nDiscontinued comment: " + request.getParameter("comment");
        HttpSession se = request.getSession();
        String prog_no = new EctProgram(se).getProgram(user);

        cmn.setUpdate_date(now);
        cmn.setObservation_date(now);
        cmn.setDemographic_no(demoNo);
        cmn.setProviderNo(user);
        cmn.setNote(strNote);
        cmn.setSigned(true);
        cmn.setSigning_provider_no(user);
        cmn.setProgram_no(prog_no);

        SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        SecRole doctorRole = secRoleDao.findByName("doctor");
        cmn.setReporter_caisi_role(doctorRole.getId().toString());

        cmn.setReporter_program_team("0");
        cmn.setLocked(false);
        cmn.setHistory(strNote);

        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(se.getServletContext());
        CaseManagementManager cmm = (CaseManagementManager) ctx.getBean(CaseManagementManager.class);

        Long note_id = cmm.saveNoteSimpleReturnID(cmn);
        MiscUtils.getLogger().info("Document Note ID: " + note_id.toString());

        CaseManagementNoteLink cmnl = new CaseManagementNoteLink();
        cmnl.setTableName(CaseManagementNoteLink.DRUGS);
        cmnl.setTableId(Long.parseLong(idStr));
        cmnl.setNoteId(note_id);

        EDocUtil.addCaseMgmtNoteLink(cmnl);
    }

    private void checkPrivilege(HttpServletRequest request, String privilege) {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", privilege, null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }
    }

    private String drugList = null;

    public String getDrugList() {
        return this.drugList;
    }

    @StrutsParameter
    public void setDrugList(String RHS) {
        this.drugList = RHS;
    }
}
