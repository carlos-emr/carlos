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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute;
import io.github.carlos_emr.carlos.billings.ca.on.dto.PrivateBillingCode;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnEditPrivateCodeViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodeLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link BillingOnEditPrivateCodeViewModel} for
 * {@code billingONEditPrivateCode.jsp}, the manage-private-billing-code
 * admin form. Owns the read-side search branch and option population the JSP
 * performed inline in a 130-line top-of-page scriptlet:
 *
 * <ul>
 *   <li>{@code submit=Search} &rarr; {@link ServiceCodeLoader#getBillingCodeAttr}</li>
 * </ul>
 *
 * <p>The action layer invokes {@link ServiceCodePersister} for add/edit/delete
 * submissions before calling this assembler.</p>
 *
 * <p>The legacy code prefixed all stored service codes with an underscore
 * to disambiguate from regular billing codes; that convention is
 * preserved as-is.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnEditPrivateCodeViewModelAssembler {

    private static final String SUFFIX_TYPE_TO_SEARCH =
            "Type in a service code and search first to see if it is available.";

    private final ServiceCodeLoader codeLoader;

    public BillingOnEditPrivateCodeViewModelAssembler(ServiceCodeLoader codeLoader) {
        this.codeLoader = codeLoader;
    }

    /** Build the read-side view model. */
    public BillingOnEditPrivateCodeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        return assemble(request, loggedInInfo, null);
    }

    /** Build the view model after an optional private-code mutation. */
    public BillingOnEditPrivateCodeViewModel assemble(
            HttpServletRequest request, LoggedInInfo loggedInInfo,
            ServiceCodePersister.PrivateCodeMutationResult mutationResult) {
        Map<String, String> formFields = new HashMap<>();
        String msg = SUFFIX_TYPE_TO_SEARCH;
        String alert = "info";
        String action = "search";

        String submit = request.getParameter("submit");
        if (mutationResult != null) {
            msg = mutationResult.message();
            action = mutationResult.action();
            alert = mutationResult.alert();
            formFields.putAll(mutationResult.formFields());
        } else if ("Search".equals(submit)) {
            FormResult r = handleSearch(request, formFields);
            msg = r.msg; action = r.action;
        }

        // Dropdown of all existing private codes.
        List<BillingOnEditPrivateCodeViewModel.PrivateCodeOption> options = new ArrayList<>();
        for (PrivateBillingCode pc : codeLoader.getPrivateBillingCodeDesc()) {
            String raw = pc.serviceCode();
            String strCode;
            if (raw.isEmpty()) {
                strCode = "";
                MiscUtils.getLogger().warn("Empty serviceCode in private-billing-code list");
            } else {
                strCode = raw.substring(1);
            }
            String strDesc = pc.description();
            if (strDesc.length() > 30) {
                strDesc = strDesc.substring(0, 30);
            }
            options.add(new BillingOnEditPrivateCodeViewModel.PrivateCodeOption(
                    strCode, strCode + "| " + strDesc));
        }

        return BillingOnEditPrivateCodeViewModel.builder()
                .message(msg)
                .alertLevel(alert)
                .action(action)
                .options(options)
                .formFields(formFields)
                .build();
    }

    private FormResult handleSearch(HttpServletRequest request, Map<String, String> formFields) {
        if (request.getParameter("service_code") == null) {
            return new FormResult("Please type in a right service code.", "search", "info");
        }
        String serviceCode = "_" + nullToEmpty(request.getParameter("service_code"));
        List<BillingCodeAttribute> ls = codeLoader.getBillingCodeAttr(serviceCode);
        if (ls != null && !ls.isEmpty()) {
            BillingCodeAttribute attr = ls.get(0);
            formFields.put("service_code", serviceCode);
            formFields.put("description", attr.description());
            formFields.put("value", attr.value());
            formFields.put("percentage", attr.percentage());
            formFields.put("billingservice_date", attr.billingServiceDate());
            formFields.put("gstFlag", attr.gstFlag());
            return new FormResult("You can edit the service code.",
                    "edit" + serviceCode, "info");
        }
        formFields.put("service_code", serviceCode);
        return new FormResult("It is a NEW service code. You can add it.",
                "add" + serviceCode, "info");
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private record FormResult(String msg, String action, String alert) { }
}
