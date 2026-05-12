/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.BillingBean;
import io.github.carlos_emr.BillingDataBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2Action for BC billing correction submission.
 * Archives old billing content, soft-deletes old billing details, updates the
 * billing header, and inserts corrected service line items.
 *
 * <p>Notable difference from the ON version: total is stored directly without
 * {@code movePointLeft(2)}.
 *
 * <p>Migrated from {@code billing/CA/BC/billingCorrectionSubmit.jsp}.
 *
 * @since 2026
 */
public final class BillingCorrectionSubmit2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private BillingCorrectionSubmitService submitService = SpringUtils.getBean(BillingCorrectionSubmitService.class);

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingDataBean billingDataBean = (BillingDataBean) request.getSession().getAttribute("billingDataBean");
        // Session key for BillingBean is "billing" (set by billingCorrectionValid.jsp)
        BillingBean billingBean = BillingSession.getBillingBean(request.getSession());

        if (billingDataBean == null || billingBean == null) {
            MiscUtils.getLogger().error("billingDataBean or billingBean not found in session");
            request.setAttribute("correctionError", Boolean.TRUE);
            return ERROR;
        }

        try {
            submitService.submit(loggedInInfo.getLoggedInProviderNo(), billingDataBean, billingBean);

        } catch (ArrayIndexOutOfBoundsException e) {
            MiscUtils.getLogger().warn("ArrayIndexOutOfBoundsException during BC billing correction submit", e);
            request.setAttribute("correctionError", Boolean.TRUE);
            return ERROR;
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("Invalid billing number format during BC billing correction submit: billingNoStr=" + billingDataBean.getBilling_no(), e);
            request.setAttribute("correctionError", Boolean.TRUE);
            return ERROR;
        }

        return SUCCESS;
    }
}
