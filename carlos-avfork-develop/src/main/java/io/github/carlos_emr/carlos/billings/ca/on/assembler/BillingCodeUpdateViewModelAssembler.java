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

import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeUpdateViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link BillingCodeUpdateViewModel} for
 * {@code billingCodeUpdate.jsp}, the popup that handles two distinct
 * branches:
 *
 * <ol>
 *   <li><b>Confirm</b>: user clicked "Confirm" on the parent search popup
 *       — read the {@code code_*} checkbox params, pick the first 3, and
 *       hand the JSP the values to inject into the opener via
 *       {@code CodeAttach}.</li>
 *   <li><b>Update</b>: user clicked "update <code>" — persist the new
 *       description for that {@code BillingService} row, then signal the
 *       JSP to close the popup via {@code history.go(-1)} + opener
 *       refresh.</li>
 * </ol>
 *
 * <p>Read-only — the description-merge that the JSP used to perform
 * mid-render now lives on
 * {@link io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister#updateDescriptionByServiceCode}
 * and is invoked by the action before this assembler runs.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingCodeUpdateViewModelAssembler {

    public BillingCodeUpdateViewModelAssembler() {
    }

    /**
     * Build the view model and execute the persist when applicable.
     *
     * @param request in-flight request — supplies the {@code update}
     *                button value and any {@code code_*} checkbox params
     * @return populated view model. {@link BillingCodeUpdateViewModel#getMode()}
     *         tells the JSP which client-side script branch to emit.
     */
    public BillingCodeUpdateViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String update = request.getParameter("update");
        String nameFSafe = validateNameF(request.getParameter("nameF"));
        if ("Confirm".equals(update)) {
            return assembleConfirmMode(request, nameFSafe);
        }
        return assembleUpdateMode(request, update, nameFSafe);
    }

    /**
     * Validate the {@code nameF} request parameter against the legacy
     * {@code [a-zA-Z_][a-zA-Z0-9_.]*} JS-identifier-path pattern. Returns
     * the input when it matches, empty string otherwise. Never returns null.
     */
    private static String validateNameF(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.matches("[a-zA-Z_][a-zA-Z0-9_.]*") ? raw : "";
    }

    private BillingCodeUpdateViewModel assembleConfirmMode(HttpServletRequest request, String nameFSafe) {
        // Walk the request param names, picking the first 3 that start
        // with "code_". Mirrors the legacy scriptlet exactly.
        String[] params = new String[]{"", "", ""};
        int count = 0;
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String temp = e.nextElement();
            if (!temp.startsWith("code_")) {
                continue;
            }
            String code = temp.substring("code_".length()).toUpperCase();
            if (count < 3) {
                params[count] = code;
            }
            count++;
        }

        return BillingCodeUpdateViewModel.builder()
                .mode(BillingCodeUpdateViewModel.Mode.CONFIRM_SELECTION)
                .noSelection(count == 0)
                .selected0(params[0])
                .selected1(params[1])
                .selected2(params[2])
                .nameFSafe(nameFSafe)
                .build();
    }

    private BillingCodeUpdateViewModel assembleUpdateMode(HttpServletRequest request, String update, String nameFSafe) {
        return BillingCodeUpdateViewModel.builder()
                .mode(BillingCodeUpdateViewModel.Mode.UPDATE_DESCRIPTION)
                .nameFSafe(nameFSafe)
                .build();
    }
}
