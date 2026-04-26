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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.EditBillingPaymentTypeViewModel;

/**
 * Assembles a {@link EditBillingPaymentTypeViewModel} for
 * {@code editBillingPaymentType.jsp}.
 *
 * <p>The JSP toggles between create and modify modes based on the presence of
 * both {@code id} and {@code type} request parameters. This assembler
 * preserves that contract:
 * <ul>
 *   <li>Modify mode (both present): title "Modify Billing Payment Type",
 *       method "editType", echoes {@code id} and {@code type}.</li>
 *   <li>Create mode: title "Create Billing Payment Type",
 *       method "createType", empty {@code type}.</li>
 * </ul></p>
 *
 * @since 2026-04-25
 */
public final class EditBillingPaymentTypeDataAssembler {

    public EditBillingPaymentTypeViewModel assemble(HttpServletRequest request) {
        String id = request.getParameter("id");
        String type = request.getParameter("type");
        boolean modify = id != null && type != null;
        if (!modify) {
            type = "";
        }
        String title = modify ? "Modify Billing Payment Type" : "Create Billing Payment Type";
        String method = modify ? "editType" : "createType";
        return new EditBillingPaymentTypeViewModel(modify, id, type, title, method);
    }
}
