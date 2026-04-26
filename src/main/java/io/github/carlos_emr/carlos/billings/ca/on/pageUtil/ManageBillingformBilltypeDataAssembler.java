/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformBilltypeViewModel;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
public final class ManageBillingformBilltypeDataAssembler {

    private final CtlBillingTypeDao ctlBillingTypeDao;

    public ManageBillingformBilltypeDataAssembler() {
        this(SpringUtils.getBean(CtlBillingTypeDao.class));
    }

    ManageBillingformBilltypeDataAssembler(CtlBillingTypeDao ctlBillingTypeDao) {
        this.ctlBillingTypeDao = ctlBillingTypeDao;
    }

    /**
     * Build the view model for the active fetch request.
     *
     * @param request live request — supplies {@code type_id} and {@code type_name}
     * @return populated view model with {@code billType="no"} when none persisted
     */
    public ManageBillingformBilltypeViewModel assemble(HttpServletRequest request) {
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
