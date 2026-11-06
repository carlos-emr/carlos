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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billing.web;

import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the shared three-group billing form. Order validation stays with each
 * action so its existing rejection and diagnostic behavior is preserved.
 */
public record BillingFormServiceCodeInput(int groupIndex, int rowIndex, String groupName,
                                         String serviceCode, String order) {

    public static List<BillingFormServiceCodeInput> read(HttpServletRequest request) {
        List<BillingFormServiceCodeInput> inputs = new ArrayList<>();
        for (int group = 1; group <= 3; group++) {
            String groupName = Objects.toString(request.getParameter("group" + group), "");
            for (int row = 0; row < 20; row++) {
                String parameter = "group" + group + "_service" + row;
                String code = Objects.toString(request.getParameter(parameter), "").trim();
                if (!code.isEmpty()) {
                    inputs.add(new BillingFormServiceCodeInput(group, row, groupName, code,
                            request.getParameter(parameter + "_order")));
                }
            }
        }
        return inputs;
    }

    public String parameterName() {
        return "group" + groupIndex + "_service" + rowIndex;
    }

    public CtlBillingService toRow(String typeId, String typeName, int serviceOrder) {
        CtlBillingService row = new CtlBillingService();
        row.setServiceTypeName(typeName);
        row.setServiceType(typeId);
        row.setServiceCode(serviceCode);
        row.setServiceGroupName(groupName);
        row.setServiceGroup("Group" + groupIndex);
        row.setStatus("A");
        row.setServiceOrder(serviceOrder);
        return row;
    }
}
