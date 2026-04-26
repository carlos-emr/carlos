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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONEditPrivateCodeViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingCodeImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Assembles {@link BillingONEditPrivateCodeViewModel} for
 * {@code billingONEditPrivateCode.jsp}, the manage-private-billing-code
 * admin form. Owns the four legacy form-processing branches the JSP
 * performed inline in a 130-line top-of-page scriptlet:
 *
 * <ul>
 *   <li>{@code submit=Save} with {@code action.startsWith("edit")} &rarr;
 *       {@link JdbcBillingCodeImpl#updateCodeByName}</li>
 *   <li>{@code submit=Save} with {@code action.startsWith("add")} &rarr;
 *       {@link JdbcBillingCodeImpl#addCodeByStr}</li>
 *   <li>{@code submit=Search} &rarr; {@link JdbcBillingCodeImpl#getBillingCodeAttr}</li>
 *   <li>{@code submit=Delete} &rarr; {@link JdbcBillingCodeImpl#deletePrivateCode}</li>
 * </ul>
 *
 * <p>The legacy code prefixed all stored service codes with an underscore
 * to disambiguate from regular billing codes; that convention is
 * preserved as-is.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONEditPrivateCodeDataAssembler {

    private static final String SUFFIX_TYPE_TO_SEARCH =
            "Type in a service code and search first to see if it is available.";
    private static final String FONT_RED_NOT = "<font color='red'>NOT</font>";
    private static final String VERB_UPDATED = "u" + "pdated";
    private static final String VERB_DELETED = "d" + "eleted";
    private static final String VERB_ADDED = "added";

    /** Build the view model. Mirrors all four legacy submit modes. */
    public BillingONEditPrivateCodeViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        JdbcBillingCodeImpl dbObj = new JdbcBillingCodeImpl();
        Map<String, String> formFields = new HashMap<>();
        String msg = SUFFIX_TYPE_TO_SEARCH;
        String alert = "info";
        String action = "search";

        String submit = request.getParameter("submit");
        if ("Save".equals(submit)) {
            FormResult r = handleSave(request, dbObj, formFields);
            msg = r.msg; action = r.action; alert = r.alert;
        } else if ("Search".equals(submit)) {
            FormResult r = handleSearch(request, dbObj, formFields);
            msg = r.msg; action = r.action;
        } else if ("Delete".equals(submit)) {
            FormResult r = handleDelete(request, dbObj, formFields);
            msg = r.msg; action = r.action;
        }

        // Dropdown of all existing private codes.
        List<BillingONEditPrivateCodeViewModel.PrivateCodeOption> options = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List sL = dbObj.getPrivateBillingCodeDesc();
        for (int i = 0; i + 1 < sL.size(); i = i + 2) {
            String strCode;
            try {
                String raw = (String) sL.get(i);
                strCode = raw == null ? "" : raw.substring(1);
            } catch (NullPointerException e) {
                strCode = "";
                MiscUtils.getLogger().warn("NULL value set for a private billing code");
            } catch (StringIndexOutOfBoundsException e) {
                strCode = "";
            }
            String strDesc;
            try {
                strDesc = (String) sL.get(i + 1);
                if (strDesc == null) strDesc = "";
                strDesc = strDesc.length() > 30 ? strDesc.substring(0, 30) : strDesc;
            } catch (NullPointerException e) {
                strDesc = "";
                MiscUtils.getLogger().warn(
                        "NULL value set for a private billing code description (code is {})",
                        strCode);
            }
            options.add(new BillingONEditPrivateCodeViewModel.PrivateCodeOption(
                    strCode, strCode + "| " + strDesc));
        }

        return BillingONEditPrivateCodeViewModel.builder()
                .message(msg)
                .alertLevel(alert)
                .action(action)
                .options(options)
                .formFields(formFields)
                .build();
    }

    private FormResult handleSave(HttpServletRequest request, JdbcBillingCodeImpl dbObj,
                                  Map<String, String> formFields) {
        String actionParam = nullToEmpty(request.getParameter("action"));
        String valuePara = nullToEmpty(request.getParameter("value"));
        if (actionParam.startsWith("edit")) {
            String serviceCode = "_" + nullToEmpty(request.getParameter("service_code"));
            if (!serviceCode.equals(actionParam.substring("edit".length()))) {
                formFields.put("service_code", serviceCode);
                String mismatchMsg = new StringBuilder()
                        .append("You can ").append(FONT_RED_NOT).append(" save the service code - ")
                        .append(SafeEncode.forHtml(serviceCode))
                        .append(". Please search the service code first.").toString();
                return new FormResult(mismatchMsg, "search", "info");
            }
            boolean ok = dbObj.updateCodeByName(serviceCode,
                    request.getParameter("description"), valuePara, "0.00",
                    request.getParameter("billingservice_date"),
                    request.getParameter("gstFlag"));
            String safeCode = SafeEncode.forHtml(serviceCode);
            if (ok) {
                formFields.put("service_code", serviceCode);
                String okMsg = new StringBuilder().append(safeCode).append(" is ")
                        .append(VERB_UPDATED).append(".<br>")
                        .append(SUFFIX_TYPE_TO_SEARCH).toString();
                return new FormResult(okMsg, "search", "info");
            }
            // Persist failed
            formFields.put("service_code", serviceCode);
            String desc = nullToEmpty(request.getParameter("description"));
            formFields.put("description", desc);
            formFields.put("value", valuePara);
            formFields.put("billingservice_date", nullToEmpty(request.getParameter("billingservice_date")));
            formFields.put("gstFlag", nullToEmpty(request.getParameter("gstFlag")));
            String failMsg = new StringBuilder().append(safeCode).append(" is ")
                    .append(FONT_RED_NOT).append(" ").append(VERB_UPDATED)
                    .append(". Action failed! Try edit it again.").toString();
            return new FormResult(failMsg, "edit" + serviceCode, "info");
        }
        if (actionParam.startsWith("add")) {
            String serviceCode = "_" + nullToEmpty(request.getParameter("service_code"));
            String safeCode = SafeEncode.forHtml(serviceCode);
            if (!serviceCode.equals(actionParam.substring("add".length()))) {
                formFields.put("service_code", serviceCode);
                String mismatchMsg = new StringBuilder().append("You can not save the service code - ")
                        .append(safeCode)
                        .append(". Please search the service code first.").toString();
                return new FormResult(mismatchMsg, "search", "error");
            }
            int rc = dbObj.addCodeByStr(serviceCode,
                    request.getParameter("description"), valuePara, "0.00",
                    request.getParameter("billingservice_date"),
                    request.getParameter("gstFlag"));
            if (rc > 0) {
                formFields.put("service_code", serviceCode);
                String okMsg = new StringBuilder().append(safeCode).append(" is ")
                        .append(VERB_ADDED).append(".<br>")
                        .append(SUFFIX_TYPE_TO_SEARCH).toString();
                return new FormResult(okMsg, "search", "info");
            }
            formFields.put("service_code", serviceCode);
            String desc = nullToEmpty(request.getParameter("description"));
            formFields.put("description", desc);
            formFields.put("value", valuePara);
            formFields.put("billingservice_date", nullToEmpty(request.getParameter("billingservice_date")));
            formFields.put("gstFlag", nullToEmpty(request.getParameter("gstFlag")));
            String failMsg = safeCode + " is not added. Action failed! Try edit it again.";
            return new FormResult(failMsg, "add" + serviceCode, "error");
        }
        return new FormResult(
                "You can not save the service code. Please search the service code first.",
                "search", "error");
    }

    private FormResult handleSearch(HttpServletRequest request, JdbcBillingCodeImpl dbObj,
                                    Map<String, String> formFields) {
        if (request.getParameter("service_code") == null) {
            return new FormResult("Please type in a right service code.", "search", "info");
        }
        String serviceCode = "_" + nullToEmpty(request.getParameter("service_code"));
        @SuppressWarnings("rawtypes")
        List ls = dbObj.getBillingCodeAttr(serviceCode);
        if (ls != null && ls.size() > 0) {
            formFields.put("service_code", serviceCode);
            Object descObj = ls.size() > 1 ? ls.get(1) : null;
            String description = descObj == null ? "" : descObj.toString();
            formFields.put("description", description);
            formFields.put("value", ls.size() > 2 ? String.valueOf(ls.get(2)) : "");
            formFields.put("percentage", ls.size() > 3 ? String.valueOf(ls.get(3)) : "");
            formFields.put("billingservice_date", ls.size() > 4 ? String.valueOf(ls.get(4)) : "");
            formFields.put("gstFlag", ls.size() > 5 ? String.valueOf(ls.get(5)) : "");
            return new FormResult("You can edit the service code.",
                    "edit" + serviceCode, "info");
        }
        formFields.put("service_code", serviceCode);
        return new FormResult("It is a NEW service code. You can add it.",
                "add" + serviceCode, "info");
    }

    private FormResult handleDelete(HttpServletRequest request, JdbcBillingCodeImpl dbObj,
                                    Map<String, String> formFields) {
        if (request.getParameter("service_code") == null) {
            return new FormResult("Please type in a right service code.", "search", "info");
        }
        String serviceCode = "_" + nullToEmpty(request.getParameter("service_code"));
        if (dbObj.deletePrivateCode(serviceCode)) {
            formFields.put("service_code", "_");
            String okMsg = new StringBuilder().append(SafeEncode.forHtml(serviceCode))
                    .append(" is ").append(VERB_DELETED).append(".<br>")
                    .append(SUFFIX_TYPE_TO_SEARCH).toString();
            return new FormResult(okMsg, "search", "info");
        }
        // No legacy fallthrough — leave message at default
        return new FormResult(SUFFIX_TYPE_TO_SEARCH, "search", "info");
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private record FormResult(String msg, String action, String alert) { }
}
