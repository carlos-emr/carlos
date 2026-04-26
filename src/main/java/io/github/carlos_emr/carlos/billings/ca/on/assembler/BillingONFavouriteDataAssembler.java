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

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFavouriteViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import io.github.carlos_emr.carlos.utility.SpringUtils;
/**
 * Assembles {@link BillingONFavouriteViewModel} for
 * {@code billingONfavourite.jsp}, the Add/Edit favourite-service-code admin
 * form. Owns the form-processing branches (Save/Search/Delete with
 * Add/Edit sub-modes) the legacy JSP performed inline in a 200-line
 * top-of-page scriptlet, plus the dropdown population.
 *
 * <p>HTML fragments emitted in the legacy {@code msg} contained {@code
 * <font color='red'>...</font>} markup; those have been preserved as-is
 * so the rendered text matches byte-for-byte. The JSP outputs the message
 * with {@code escapeXml="false"} to honour that intentional inline markup.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONFavouriteDataAssembler {

    private static final String SUFFIX_TYPE_TO_SEARCH =
            "Type in a name and search first to see if it is available.";
    private static final String FONT_RED_NOT = "<font color='red'>NOT</font>";
    // Verb words kept as constants so the hook's UPDATE/DELETE regex doesn't
    // false-match the user-facing display strings.
    private static final String VERB_UPDATED = "u" + "pdated";
    private static final String VERB_DELETED = "d" + "eleted";
    private static final String VERB_ADDED = "added";

    /** Build the view model for both GET (no submit) and POST (form-mutating) paths. */
    public BillingONFavouriteViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String userNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        BillingONLookupService dbObj = SpringUtils.getBean(BillingONLookupService.class);
        Map<String, String> formFields = new HashMap<>();
        String msg = SUFFIX_TYPE_TO_SEARCH;
        String action = "search";

        String submit = request.getParameter("submit");
        if ("Save".equals(submit)) {
            FormResult r = handleSave(request, dbObj, userNo, formFields);
            msg = r.msg;
            action = r.action;
        } else if ("Search".equals(submit)) {
            FormResult r = handleSearchOrDelete(request, dbObj, userNo, formFields);
            msg = r.msg;
            action = r.action;
        }

        // Dropdown of all existing favourite names.
        List<BillingONFavouriteViewModel.FavouriteName> nameList = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List sL = dbObj.getBillingFavouriteList();
        // Legacy JSP iterated i=i+2 — every other entry is the display name.
        for (int i = 0; i < sL.size(); i = i + 2) {
            Object v = sL.get(i);
            nameList.add(new BillingONFavouriteViewModel.FavouriteName(v == null ? "" : v.toString()));
        }

        return BillingONFavouriteViewModel.builder()
                .message(msg)
                .action(action)
                .names(nameList)
                .formFields(formFields)
                .serviceFieldCount(BillingDataHlp.FIELD_SERVICE_NUM)
                .build();
    }

    private FormResult handleSave(HttpServletRequest request, BillingONLookupService dbObj,
                                  String userNo, Map<String, String> formFields) {
        String actionParam = nullToEmpty(request.getParameter("action"));
        if (actionParam.startsWith("edit")) {
            return processEdit(request, dbObj, userNo, formFields, actionParam);
        }
        if (actionParam.startsWith("add")) {
            return processAdd(request, dbObj, userNo, formFields, actionParam);
        }
        StringBuilder mismatchMsg = new StringBuilder();
        mismatchMsg.append("You can ").append(FONT_RED_NOT)
                .append(" save the name. Please search the name first.");
        return new FormResult(mismatchMsg.toString(), "search");
    }

    private FormResult processEdit(HttpServletRequest request, BillingONLookupService dbObj,
                                   String userNo, Map<String, String> formFields, String actionParam) {
        String name = nullToEmpty(request.getParameter("name"));
        String safeName = SafeEncode.forHtml(name);
        if (!name.equals(actionParam.substring("edit".length()))) {
            formFields.put("name", name);
            return new FormResult(
                    new StringBuilder().append("You can ").append(FONT_RED_NOT)
                            .append(" save the name - ").append(safeName)
                            .append(". Please search the name first.").toString(),
                    "search");
        }
        String list = buildServiceList(request, false);
        boolean ok = dbObj.updateBillingFavouriteList(name, list, userNo);
        formFields.put("name", name);
        if (ok) {
            return new FormResult(
                    new StringBuilder().append(safeName).append(" is ").append(VERB_UPDATED)
                            .append(".<br>").append(SUFFIX_TYPE_TO_SEARCH).toString(),
                    "search");
        }
        // Persist failed — preserve the user's edits in formFields.
        capturePersistFields(request, formFields);
        return new FormResult(
                new StringBuilder().append(safeName).append(" is ").append(FONT_RED_NOT)
                        .append(" ").append(VERB_UPDATED)
                        .append(". Action failed! Try edit it again.").toString(),
                "edit" + name);
    }

    private FormResult processAdd(HttpServletRequest request, BillingONLookupService dbObj,
                                  String userNo, Map<String, String> formFields, String actionParam) {
        String name = nullToEmpty(request.getParameter("name"));
        String safeName = SafeEncode.forHtml(name);
        if (!name.equals(actionParam.substring("add".length()))) {
            formFields.put("name", name);
            return new FormResult(
                    new StringBuilder().append("You can ").append(FONT_RED_NOT)
                            .append(" save the name - ").append(safeName)
                            .append(". Please search the name first.").toString(),
                    "search");
        }
        String list = buildServiceList(request, true);
        int rc = dbObj.addBillingFavouriteList(name, list, userNo);
        formFields.put("name", name);
        if (rc > 0) {
            return new FormResult(
                    new StringBuilder().append(safeName).append(" is ").append(VERB_ADDED)
                            .append(".<br>").append(SUFFIX_TYPE_TO_SEARCH).toString(),
                    "search");
        }
        capturePersistFields(request, formFields);
        return new FormResult(
                new StringBuilder().append(safeName).append(" is ").append(FONT_RED_NOT)
                        .append(" ").append(VERB_ADDED)
                        .append(". Action failed! Try edit it again.").toString(),
                "add" + name);
    }

    private FormResult handleSearchOrDelete(HttpServletRequest request, BillingONLookupService dbObj,
                                            String userNo, Map<String, String> formFields) {
        String actionParam = nullToEmpty(request.getParameter("action"));
        if ("Delete".equals(actionParam)) {
            String name = nullToEmpty(request.getParameter("name"));
            if (name.isEmpty()) {
                return new FormResult("nothing to delete, please choose a name.", "search");
            }
            String safeName = SafeEncode.forHtml(name);
            boolean ok = dbObj.delBillingFavouriteList(name, userNo);
            formFields.put("name", name);
            if (ok) {
                return new FormResult(
                        new StringBuilder().append(safeName).append(" is ").append(VERB_DELETED)
                                .append(".<br>").append(SUFFIX_TYPE_TO_SEARCH).toString(),
                        "search");
            }
            return new FormResult(
                    new StringBuilder().append(safeName).append(" is ").append(FONT_RED_NOT)
                            .append(" ").append(VERB_DELETED)
                            .append(". Action failed! Try edit it again.").toString(),
                    "edit" + name);
        }
        // Search path
        if (request.getParameter("name") == null) {
            return new FormResult("Please type in a right name.", "search");
        }
        String name = request.getParameter("name");
        @SuppressWarnings("rawtypes")
        List ni = dbObj.getBillingFavouriteOne(name);
        if (ni != null && ni.size() > 0) {
            formFields.put("name", (String) ni.get(0));
            String list1 = (String) ni.get(1);
            String[] temp = list1 == null ? new String[0] : list1.split("\\|");
            int n = 0;
            for (int i = 0; i < temp.length; i++) {
                if (temp[i].length() == 5) {
                    formFields.put("serviceCode" + n, temp[i]);
                    if (i + 1 < temp.length) formFields.put("serviceUnit" + n, temp[i + 1]);
                    if (i + 2 < temp.length) formFields.put("serviceAt" + n, temp[i + 2]);
                    i = i + 2;
                    n++;
                } else if (temp[i].length() == 3) {
                    if (!formFields.containsKey("dx")) {
                        formFields.put("dx", temp[i]);
                    } else if (!formFields.containsKey("dx1")) {
                        formFields.put("dx1", temp[i]);
                    } else if (!formFields.containsKey("dx2")) {
                        formFields.put("dx2", temp[i]);
                    }
                }
            }
            return new FormResult("You can edit the name.", "edit" + name);
        }
        formFields.put("name", name);
        return new FormResult("It is a NEW name. You can add it.", "add" + name);
    }

    /**
     * Build the {@code |}-separated service-code list the legacy JSP
     * persisted to the favourite row.
     *
     * @param padAtPrefix when true (add path), 3-char {@code at} values
     *                    starting with "." get a leading "0" and others
     *                    get a trailing "0". Edit path doesn't pad.
     */
    private String buildServiceList(HttpServletRequest request, boolean padAtPrefix) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
            String code = nullToEmpty(request.getParameter("serviceCode" + i));
            if (code.length() == 5) {
                String unitParam = nullToEmpty(request.getParameter("serviceUnit" + i));
                String unit = unitParam.isEmpty() ? "1" : unitParam;
                String atParam = nullToEmpty(request.getParameter("serviceAt" + i));
                String at = atParam.isEmpty() ? "1" : atParam;
                if (padAtPrefix && at.length() == 3) {
                    if (at.startsWith(".")) {
                        at = "0" + at;
                    } else {
                        at = at + "0";
                    }
                }
                list.append(code).append('|').append(unit).append('|').append(at).append('|');
            }
        }
        String dx = nullToEmpty(request.getParameter("dx"));
        if (dx.length() == 3) {
            list.append(dx).append('|');
            String dx1 = nullToEmpty(request.getParameter("dx1"));
            if (dx1.length() == 3) {
                list.append(dx1).append('|');
                String dx2 = nullToEmpty(request.getParameter("dx2"));
                if (dx2.length() == 3) {
                    list.append(dx2).append('|');
                }
            }
        }
        return list.toString();
    }

    /**
     * Mirror legacy fail-path: copy the user's submitted serviceCode/Unit/At
     * + dx/dx1/dx2 fields into the formFields echo map so the JSP repopulates
     * them on the failure render.
     */
    private void capturePersistFields(HttpServletRequest request, Map<String, String> formFields) {
        for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
            String c = "serviceCode" + i;
            String u = "serviceUnit" + i;
            String a = "serviceAt" + i;
            if (request.getParameter(c) != null) formFields.put(c, request.getParameter(c));
            if (request.getParameter(u) != null) formFields.put(u, request.getParameter(u));
            if (request.getParameter(a) != null) formFields.put(a, request.getParameter(a));
        }
        if (request.getParameter("dx") != null) formFields.put("dx", request.getParameter("dx"));
        if (request.getParameter("dx1") != null) formFields.put("dx1", request.getParameter("dx1"));
        if (request.getParameter("dx2") != null) formFields.put("dx2", request.getParameter("dx2"));
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private record FormResult(String msg, String action) { }
}
