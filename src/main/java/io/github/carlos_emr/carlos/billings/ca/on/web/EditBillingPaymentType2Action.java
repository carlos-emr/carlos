/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.EditBillingPaymentTypeViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.EditBillingPaymentTypeViewModelAssembler;

/**
 * View-scope gate for {@code billing/CA/ON/editBillingPaymentType.jsp}. Enforces
 * {@code _admin.billing} w privilege before forwarding to the JSP, then assembles
 * the {@link EditBillingPaymentTypeViewModel} on the request as
 * {@code paymentTypeModel} so the JSP body can render in pure EL.
 *
 * <p>This action is read-only: the assembler does not mutate any state, it only
 * echoes the {@code id}/{@code type} request parameters into a viewmodel that
 * the JSP renders. The actual create/update happens via separate, POST-only
 * actions ({@code billing/CA/ON/createPaymentType},
 * {@code billing/CA/ON/updatePaymentType}). GET is therefore allowed so the
 * "Edit" / "Create" links on {@code manageBillingPaymentType.jsp} can open the
 * form.</p>
 *
 * @since 2026-04-13
 */
public class EditBillingPaymentType2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EditBillingPaymentTypeViewModelAssembler assembler;

    public EditBillingPaymentType2Action(SecurityInfoManager securityInfoManager,
                                  EditBillingPaymentTypeViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        request.setAttribute("paymentTypeModel", assembler.assemble(request, loggedInInfo));
        return SUCCESS;
    }
}
