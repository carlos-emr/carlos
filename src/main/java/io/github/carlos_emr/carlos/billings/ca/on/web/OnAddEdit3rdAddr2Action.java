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

import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.Billing3rdPartyService;
import io.github.carlos_emr.carlos.billings.ca.on.data.OnAddEdit3rdAddrViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for {@code billing/CA/ON/onAddEdit3rdAddr.jsp}. Enforces {@code _billing}
 * w privilege AND POST-only before forwarding to the JSP. GET requests return
 * 405 Method Not Allowed.
 *
 * <p>The action also assembles the {@link OnAddEdit3rdAddrViewModel} the JSP
 * renders, replicating the search/save state machine the JSP scriptlet used
 * to run inline. The JSP body is now pure EL/JSTL.</p>
 *
 * @since 2026-04-13
 */
public class OnAddEdit3rdAddr2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final Billing3rdPartyService thirdPartyService;

    public OnAddEdit3rdAddr2Action(SecurityInfoManager securityInfoManager,
                                   Billing3rdPartyService thirdPartyService) {
        this.securityInfoManager = securityInfoManager;
        this.thirdPartyService = thirdPartyService;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        request.setAttribute("addrModel", assembleViewModel(request));

        return SUCCESS;
    }

    /**
     * Assembles the view model. Public so the JSP defensive fallback can
     * invoke it directly when the action chain wasn't traversed (callers
     * are responsible for the privilege check before invoking).
     *
     * @param request the live servlet request
     * @return populated view model (never null)
     */
    public OnAddEdit3rdAddrViewModel assembleViewModel(HttpServletRequest request) {
        String submit = request.getParameter("submit");
        String message = "Type in a name and search first to see if it is available.";
        String action = "search";
        Properties prop = new Properties();
        Billing3rdPartyService dbObj = thirdPartyService;

        if ("Save".equals(submit)) {
            String actionParam = request.getParameter("action");
            String companyName = request.getParameter("company_name");
            if (actionParam != null && actionParam.startsWith("edit")) {
                if (companyName != null && companyName.equals(actionParam.substring("edit".length()))) {
                    Properties val = collectFormValues(request);
                    boolean updated = dbObj.update3rdAddr(request.getParameter("id"), val);
                    if (updated) {
                        message = SafeEncode.forHtml(companyName) + " is updated.<br>"
                                + "Type in a name and search first to see if it is available.";
                        action = "search";
                        prop.setProperty("company_name", companyName);
                    } else {
                        message = SafeEncode.forHtml(companyName) + " is <font color='red'>NOT</font> updated. Action failed! Try edit it again.";
                        action = "edit" + companyName;
                        prop = val;
                        prop.setProperty("id", request.getParameter("id"));
                    }
                } else {
                    message = "You can <font color='red'>NOT</font> save the name - " + SafeEncode.forHtml(companyName)
                            + ". Please search the name first.";
                    action = "search";
                    prop.setProperty("company_name", companyName == null ? "" : companyName);
                }
            } else if (actionParam != null && actionParam.startsWith("add")) {
                if (companyName != null && companyName.equals(actionParam.substring("add".length()))) {
                    Properties val = collectFormValues(request);
                    int added = dbObj.addOne3rdAddrRecord(val);
                    if (added > 0) {
                        message = SafeEncode.forHtml(companyName) + " is added.<br>"
                                + "Type in a name and search first to see if it is available.";
                        action = "search";
                        prop.setProperty("company_name", companyName);
                    } else {
                        message = SafeEncode.forHtml(companyName) + " is <font color='red'>NOT</font> added. Action failed! Try edit it again.";
                        action = "add" + companyName;
                        prop = val;
                    }
                } else {
                    message = "You can <font color='red'>NOT</font> save the name - " + SafeEncode.forHtml(companyName)
                            + ". Please search the name first.";
                    action = "search";
                    prop.setProperty("company_name", companyName == null ? "" : companyName);
                }
            } else {
                message = "You can <font color='red'>NOT</font> save the name. Please search the name first.";
            }
        } else if ("Search".equals(submit)) {
            String companyName = request.getParameter("company_name");
            if (companyName == null) {
                message = "Please type in a right name.";
            } else {
                Properties found = dbObj.get3rdAddrProp(companyName);
                if (!found.getProperty("company_name", "").isEmpty()) {
                    prop = found;
                    message = "You can edit the name.";
                    action = "edit" + companyName;
                } else {
                    prop.setProperty("company_name", companyName);
                    message = "It is a NEW name. You can add it.";
                    action = "add" + companyName;
                }
            }
        }

        // The dropdown rendered at the top of the form lists every existing
        // address record so the operator can pick one and re-search.
        List<Properties> companyOptions = dbObj.get3rdAddrNameList();

        return OnAddEdit3rdAddrViewModel.builder()
                .message(message)
                .action(action)
                .prop(prop)
                .companyOptions(companyOptions)
                .build();
    }

    private Properties collectFormValues(HttpServletRequest request) {
        Properties val = new Properties();
        val.setProperty("attention", nullToEmpty(request.getParameter("attention")));
        val.setProperty("company_name", nullToEmpty(request.getParameter("company_name")));
        val.setProperty("address", nullToEmpty(request.getParameter("address")));
        val.setProperty("city", nullToEmpty(request.getParameter("city")));
        val.setProperty("province", nullToEmpty(request.getParameter("province")));
        val.setProperty("postcode", nullToEmpty(request.getParameter("postcode")));
        val.setProperty("telephone", nullToEmpty(request.getParameter("telephone")));
        val.setProperty("fax", nullToEmpty(request.getParameter("fax")));
        return val;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
