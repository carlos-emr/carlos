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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;

/**
 * Struts 2Action for Ontario billing deletion by bill number (with bill number known).
 * Soft-deletes a billing record by setting its status to 'D'.
 * Previously used as a {@code <jsp:include>} target in billingONSave.jsp;
 * the delete logic is also called directly by {@link BillingONSave2Action}.
 *
 * <p>Migrated from {@code billing/CA/ON/billingDeleteWithBillNo.jsp}.
 *
 * @since 2026
 */
public final class BillingDeleteWithBillNo2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String curUserNo = loggedInInfo.getLoggedInProviderNo();
        return deleteBillingByBillNo(request, curUserNo, billingDao);
    }

    /**
     * Performs the delete-by-bill-number logic. Called both from {@link #execute()}
     * and from {@link BillingONSave2Action} to replace the old {@code <jsp:include>} pattern.
     *
     * @param request    the current HTTP request
     * @param curUserNo  the logged-in provider number
     * @param billingDao the billing DAO
     * @return Struts result name: {@code "success"}, {@code "cannotDelete"}, or {@code "error"}
     */
    public static String deleteBillingByBillNo(HttpServletRequest request, String curUserNo, BillingDao billingDao) {
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
                MiscUtils.getLogger().error("Invalid appointment_no: {}", apptNoStr);
                return ERROR;
            }
        }

        if (billCode.isEmpty() || billCode.substring(0, 1).compareTo("B") == 0) {
            return "cannotDelete";
        }

        if (billNo.isEmpty() || "null".equals(billNo)) {
            return SUCCESS;
        }

        CarlosProperties props = CarlosProperties.getInstance();
        if (props.getProperty("isNewONbilling", "").equals("true")) {
            BillingCorrectionPrep dbObj = new BillingCorrectionPrep();
            List billStatus = dbObj.getBillingNoStatusByBillNo(billNo);
            if (billStatus != null && ((billStatus.size() == 0) || (billStatus.size() > 1 && ((String) billStatus.get(billStatus.size() - 1)).startsWith("B")))) {
                return "cannotDelete";
            } else if (billStatus != null) {
                for (int idx = 0; idx < billStatus.size(); idx += 2) {
                    // idx = billing header ID, idx+1 = status; bounds check prevents IOOBE on odd-sized list
                    if (idx + 1 < billStatus.size() && !((String) billStatus.get(idx + 1)).equals("D")) {
                        dbObj.deleteBilling((String) billStatus.get(idx), "D", curUserNo);
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
                MiscUtils.getLogger().error("Invalid billNo: {}", billNo);
                return ERROR;
            }
        }

        return SUCCESS;
    }
}
