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


package ca.openosp.openo.prescript.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.DrugReasonDao;
import ca.openosp.openo.commn.dao.Icd9Dao;
import ca.openosp.openo.commn.model.DrugReason;
import ca.openosp.openo.commn.model.Icd9;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;

public final class RxReason2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if ("archiveReason".equals(request.getParameter("method"))) {
            return archiveReason();
        }
        return addDrugReason();
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

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
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

        MiscUtils.getLogger().debug("addDrugReasonCalled codingSystem " + codingSystem + " code " + code + " drugIdStr " + drugIdStr);


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
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ADD, LogConst.CON_DRUGREASON, "" + dr.getId(), ip, demographicNo, dr.getAuditString());

        return "close";
    }

    /**
     * Used for archiving the current reason, will set the value of the archived flag to true and set the reason.
     * And will show a success message to the user
     * @return "success" which will redirect back to the "SelectReason.jsp" page
     */
    public String archiveReason() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new RuntimeException("missing required sec object (_rx)");
        }

        DrugReasonDao drugReasonDao = (DrugReasonDao) SpringUtils.getBean(DrugReasonDao.class);
        String reasonId = request.getParameter("reasonId");
        String archiveReason = request.getParameter("archiveReason");

        DrugReason drugReason = drugReasonDao.find(Integer.parseInt(reasonId));

        drugReason.setArchivedFlag(true);
        drugReason.setArchivedReason(archiveReason);

        drugReasonDao.merge(drugReason);

        request.setAttribute("drugId", drugReason.getDrugId());
        request.setAttribute("demoNo", drugReason.getDemographicNo());

        String ip = request.getRemoteAddr();
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ARCHIVE, LogConst.CON_DRUGREASON, "" + drugReason.getId(), ip, "" + drugReason.getDemographicNo(), drugReason.getAuditString());

        request.setAttribute("message", getText("SelectReason.msg.archived"));
        return SUCCESS;
    }
}
