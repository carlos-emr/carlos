/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService;

/**
 * Struts 2Action for Ontario billing deletion by bill number (with bill number known).
 * Soft-deletes a billing record by setting its status to 'D'.
 * Previously used as a {@code <jsp:include>} target in billingONSave.jsp;
 * the delete logic is also called directly by {@link BillingOnSave2Action}.
 *
 * <p>Migrated from {@code billing/CA/ON/billingDeleteWithBillNo.jsp}.
 *
 * @since 2026
 */
public class BillingDeleteWithBillNo2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingDao billingDao;
    private final BillingCorrectionRecordService correctionPrep;

    public BillingDeleteWithBillNo2Action(SecurityInfoManager securityInfoManager,
                                          BillingDao billingDao,
                                          BillingCorrectionRecordService correctionPrep) {
        this.securityInfoManager = securityInfoManager;
        this.billingDao = billingDao;
        this.correctionPrep = correctionPrep;
    }
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        String curUserNo = loggedInInfo.getLoggedInProviderNo();
        return deleteBillingByBillNo(request, curUserNo, billingDao, correctionPrep);
    }

    /**
     * Performs the delete-by-bill-number logic. Called both from {@link #execute()}
     * and from {@link BillingOnSave2Action} to replace the old {@code <jsp:include>} pattern.
     *
     * @param request        the current HTTP request
     * @param curUserNo      the logged-in provider number
     * @param billingDao     the billing DAO
     * @param correctionPrep the billing-correction prep service
     * @return Struts result name: {@code "success"}, {@code "cannotDelete"}, or {@code "error"}
     */
    public static String deleteBillingByBillNo(HttpServletRequest request, String curUserNo,
                                                BillingDao billingDao, BillingCorrectionRecordService correctionPrep) {
        String apptNoStr = request.getParameter("appointment_no");
        String billNoParam = request.getParameter("billNo_old");
        String billStatusParam = request.getParameter("billStatus_old");

        String billNo = (billNoParam != null && !billNoParam.isEmpty() && !"null".equals(billNoParam)) ? billNoParam : "";
        String billCode = (billStatusParam != null) ? billStatusParam : " ";

        if (billNo.isEmpty() && apptNoStr != null && !apptNoStr.isEmpty()) {
            try {
                for (Billing b : billingDao.findByAppointmentNo(Integer.parseInt(apptNoStr))) {
                    billCode = b.getStatus();
                    billNo = b.getId().toString();
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid appointment_no: {}", LogSanitizer.sanitize(apptNoStr));
                return ERROR;
            }
        }

        if (billCode == null || billCode.isEmpty() || billCode.startsWith("B")) {
            return "cannotDelete";
        }

        if (billNo.isEmpty() || "null".equals(billNo)) {
            return SUCCESS;
        }

        CarlosProperties props = CarlosProperties.getInstance();
        if (props.getProperty("isNewONbilling", "").equals("true")) {
            List billStatus = correctionPrep.getBillingNoStatusByBillNo(billNo);
            if (billStatus != null && ((billStatus.size() == 0) || (billStatus.size() > 1 && ((String) billStatus.get(billStatus.size() - 1)).startsWith("B")))) {
                return "cannotDelete";
            } else if (billStatus != null) {
                for (int idx = 0; idx < billStatus.size(); idx += 2) {
                    // idx = billing header ID, idx+1 = status; bounds check prevents IOOBE on odd-sized list
                    if (idx + 1 < billStatus.size() && !((String) billStatus.get(idx + 1)).equals("D")) {
                        correctionPrep.deleteBilling((String) billStatus.get(idx), "D", curUserNo);
                    }
                }
            }
        } else {
            try {
                Billing b = billingDao.find(Integer.parseInt(billNo));
                if (b != null) {
                    b.setStatus("D");
                    billingDao.merge(b);
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid billNo: {}", LogSanitizer.sanitize(billNo));
                return ERROR;
            }
        }

        return SUCCESS;
    }
}
