/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

/**
 * Struts 2Action for Ontario billing deletion by billing number without appointment.
 * Soft-deletes a billing record by setting its status to 'D'.
 *
 * <p>Migrated from {@code billing/CA/ON/billingDeleteNoAppt.jsp}.
 *
 * @since 2026
 */
public final class BillingDeleteNoAppt2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingDao billingDao = SpringUtils.getBean(BillingDao.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String billCode = request.getParameter("billCode");
        if (billCode == null || billCode.isEmpty()) {
            MiscUtils.getLogger().error("billCode parameter is missing");
            return ERROR;
        }

        if (billCode.substring(0, 1).compareTo("B") == 0) {
            request.setAttribute("cannotDelete", Boolean.TRUE);
            return "cannotDelete";
        }

        String curUserNo = loggedInInfo.getLoggedInProviderNo();
        String billingNoStr = request.getParameter("billing_no");
        CarlosProperties props = CarlosProperties.getInstance();

        if (props.getProperty("isNewONbilling", "").equals("true")) {
            BillingCorrectionPrep dbObj = new BillingCorrectionPrep();
            dbObj.deleteBilling(billingNoStr, "D", curUserNo);
        } else {
            try {
                Billing b = billingDao.find(Integer.parseInt(billingNoStr));
                if (b != null) {
                    b.setStatus("D");
                    billingDao.merge(b);
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invalid billing_no: {}", billingNoStr);
                return ERROR;
            }
        }

        return SUCCESS;
    }
}
