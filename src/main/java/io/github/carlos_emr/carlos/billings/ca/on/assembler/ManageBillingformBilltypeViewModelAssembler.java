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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.ManageBillingformBilltypeViewModel;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles a {@link ManageBillingformBilltypeViewModel} for
 * {@code manageBillingform_billtype.jsp}.
 *
 * <p>The fragment is loaded via {@code fetch()} with two query parameters:
 * {@code type_id} and {@code type_name}. The legacy scriptlet then iterated
 * {@link CtlBillingTypeDao#findByServiceType(String)} and used the last row's
 * {@link CtlBillingType#getBillType()} (defaulting to {@code "no"} when no
 * rows existed). This assembler reproduces that contract.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class ManageBillingformBilltypeViewModelAssembler {

    private final CtlBillingTypeDao ctlBillingTypeDao;

    public ManageBillingformBilltypeViewModelAssembler(CtlBillingTypeDao ctlBillingTypeDao) {
        this.ctlBillingTypeDao = ctlBillingTypeDao;
    }

    /**
     * Build the view model for the active fetch request.
     *
     * @param request live request — supplies {@code type_id} and {@code type_name}
     * @return populated view model with {@code billType="no"} when none persisted
     */
    public ManageBillingformBilltypeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String typeId = request.getParameter("type_id");
        String typeName = request.getParameter("type_name");
        String billType = "no";
        if (typeId != null && !typeId.isEmpty()) {
            for (CtlBillingType cbt : ctlBillingTypeDao.findByServiceType(typeId)) {
                if (cbt.getBillType() != null) {
                    billType = cbt.getBillType();
                }
            }
        }
        return new ManageBillingformBilltypeViewModel(typeId, typeName, billType);
    }
}
