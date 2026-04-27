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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.SearchRefDocViewModel;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for billing/CA/ON/searchRefDoc.jsp. Enforces _billing r privilege
 * before forwarding to the JSP, AND assembles the SearchRefDocViewModel the
 * JSP renders so the JSP body is pure EL/JSTL.
 *
 * @since 2026-04-13
 */
public class ViewSearchRefDoc2Action extends ActionSupport {

    /**
     * Recognised opener-form-field path expressions. Allows dots in element
     * names (for example pref.default_dx_code from UserPreferences.jsp).
     */
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "^document\\.forms\\[(\\d+)\\]\\.elements\\['([a-zA-Z0-9_.]+)'\\]\\.value$");

    private final SecurityInfoManager securityInfoManager;
    private final ProfessionalSpecialistDao professionalSpecialistDao;

    public ViewSearchRefDoc2Action(SecurityInfoManager securityInfoManager,
                                   ProfessionalSpecialistDao professionalSpecialistDao) {
        this.securityInfoManager = securityInfoManager;
        this.professionalSpecialistDao = professionalSpecialistDao;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        request.setAttribute("refDocModel", assembleViewModel(request));

        return SUCCESS;
    }

    /**
     * Assembles the view model. Public so the defensive JSP fallback can
     * invoke it directly when the action chain wasn't traversed (callers
     * remain responsible for the privilege check before invoking).
     *
     * @param request the live servlet request
     * @return populated view model (never null)
     */
    public SearchRefDocViewModel assembleViewModel(HttpServletRequest request) {
        String fld1Val = nullToEmpty(request.getParameter("param"));
        String fld2Val = nullToEmpty(request.getParameter("param2"));
        String fld3Val = nullToEmpty(request.getParameter("toname"));
        String fld4Val = nullToEmpty(request.getParameter("toaddress1"));
        String fld5Val = nullToEmpty(request.getParameter("tophone"));
        String fld6Val = nullToEmpty(request.getParameter("tofax"));
        String keyword = request.getParameter("keyword");
        String submit = request.getParameter("submit");

        SearchRefDocViewModel.JsPath fld1 = extractJsPath(fld1Val, "param");
        SearchRefDocViewModel.JsPath fld2 = extractJsPath(fld2Val, "param2");
        SearchRefDocViewModel.JsPath fld3 = extractJsPath(fld3Val, "toname");
        SearchRefDocViewModel.JsPath fld4 = extractJsPath(fld4Val, "toaddress1");
        SearchRefDocViewModel.JsPath fld5 = extractJsPath(fld5Val, "tophone");
        SearchRefDocViewModel.JsPath fld6 = extractJsPath(fld6Val, "tofax");

        List<ProfessionalSpecialist> results = null;
        if (submit != null && (submit.equals("Search") || submit.equals("Next Page") || submit.equals("Last Page"))) {
            String searchMode = request.getParameter("search_mode") == null
                    ? "search_name" : request.getParameter("search_mode");

            if ("search_name".equals(searchMode)) {
                String[] temp = keyword == null ? new String[]{""} : keyword.split("\\,\\p{Space}*");
                if (temp.length > 1) {
                    results = professionalSpecialistDao.findByFullName(temp[0], temp[1]);
                } else {
                    results = professionalSpecialistDao.findByLastName(temp[0]);
                }
            } else if ("specialty".equals(searchMode)) {
                results = professionalSpecialistDao.findBySpecialty(keyword);
            } else if ("referral_no".equals(searchMode)) {
                results = professionalSpecialistDao.findByReferralNo(keyword);
            }
        }
        if (results == null) {
            results = professionalSpecialistDao.findAll();
        }

        List<SearchRefDocViewModel.SpecialistEntry> specialists = new ArrayList<>();
        for (ProfessionalSpecialist sp : results) {
            String referralNo = nullToEmpty(sp.getReferralNo());
            String surname = nullToEmpty(sp.getLastName());
            String givenName = nullToEmpty(sp.getFirstName());
            String specialty = nullToEmpty(sp.getSpecialtyType());
            String phone = nullToEmpty(sp.getPhoneNumber());
            String fax = nullToEmpty(sp.getFaxNumber());
            String toNameField = "Dr. " + givenName + " " + surname;
            String address = nullToEmpty(sp.getStreetAddress());

            String onClick;
            if (fld2.isPresent()) {
                // The param2-only branch in the legacy JSP rendered a
                // typeInData2 handler that takes (referralNo, "surname,givenName").
                onClick = "typeInData2(" + jsLit(referralNo) + "," + jsLit(surname + "," + givenName) + ")";
            } else {
                onClick = "typeInData3("
                        + jsLit(referralNo) + ", "
                        + jsLit(toNameField) + ", "
                        + jsLit(address) + ", "
                        + jsLit(phone) + ", "
                        + jsLit(fax) + ")";
            }

            specialists.add(new SearchRefDocViewModel.SpecialistEntry(
                    referralNo, surname, givenName, specialty, phone, fax, address, onClick));
        }

        return SearchRefDocViewModel.builder()
                .keyword(keyword == null ? "" : keyword)
                .fld1(fld1)
                .fld2(fld2)
                .fld3(fld3)
                .fld4(fld4)
                .fld5(fld5)
                .fld6(fld6)
                .specialists(specialists)
                .build();
    }

    /** Builds a single-quoted JS string literal with proper JS-attribute encoding. */
    private static String jsLit(String s) {
        return "'" + SafeEncode.forJavaScriptAttribute(s) + "'";
    }

    /**
     * Extracts the form index and element name from a JS path expression
     * like document.forms[0].elements[fieldname].value.
     * Returns an empty SearchRefDocViewModel.JsPath on no match
     * (so callers can isPresent()-test).
     */
    private SearchRefDocViewModel.JsPath extractJsPath(String value, String paramName) {
        if (value == null || value.isEmpty()) return new SearchRefDocViewModel.JsPath(null, null);
        Matcher m = PATH_PATTERN.matcher(value);
        if (m.matches()) return new SearchRefDocViewModel.JsPath(m.group(1), m.group(2));
        String truncated = value.length() > 120 ? value.substring(0, 120) + "..." : value;
        MiscUtils.getLogger().warn(
                "searchRefDoc: parameter " + paramName + " did not match expected JS path format (truncated): "
                + truncated + " length=" + value.length());
        return new SearchRefDocViewModel.JsPath(null, null);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
