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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Lists all billing payment types for the admin Manage Payment Types page.
 * Renders {@code manageBillingPaymentType.jsp}.
 *
 * <p>Mutation paths (create / update / remove) live in their own dedicated
 * action classes ({@link CreatePaymentType2Action},
 * {@link UpdatePaymentType2Action}, {@link RemovePaymentType2Action}) so
 * each URL has a single responsibility — replacing the legacy
 * {@code method=...} dispatcher.</p>
 *
 * @since 2024-12-06
 */
public class PaymentType2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final BillingPaymentTypeDao billingPaymentTypeDao;

    public PaymentType2Action(SecurityInfoManager securityInfoManager,
                              BillingPaymentTypeDao billingPaymentTypeDao) {
        this.securityInfoManager = securityInfoManager;
        this.billingPaymentTypeDao = billingPaymentTypeDao;
    }

    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        List<BillingPaymentType> paymentTypeList = billingPaymentTypeDao.findAll();
        request.setAttribute("paymentTypeList", paymentTypeList);
        return SUCCESS;
    }
}
