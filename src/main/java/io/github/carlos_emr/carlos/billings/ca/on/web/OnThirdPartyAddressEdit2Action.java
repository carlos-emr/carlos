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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingThirdPartyService;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnThirdPartyAddressEditViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Mutation gate for {@code billing/CA/ON/onAddEdit3rdAddr.jsp}. Enforces {@code _billing}
 * w privilege AND POST-only before forwarding to the JSP. GET requests return
 * 405 Method Not Allowed.
 *
 * <p>The action also assembles the {@link OnThirdPartyAddressEditViewModel} the JSP
 * renders, replicating the search/save state machine the JSP scriptlet used
 * to run inline. The JSP body is now pure EL/JSTL.</p>
 *
 * @since 2026-04-13
 */
public class OnThirdPartyAddressEdit2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingThirdPartyService thirdPartyService;

    public OnThirdPartyAddressEdit2Action(SecurityInfoManager securityInfoManager,
                                   BillingThirdPartyService thirdPartyService) {
        this.securityInfoManager = securityInfoManager;
        this.thirdPartyService = thirdPartyService;
    }
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
    public OnThirdPartyAddressEditViewModel assembleViewModel(HttpServletRequest request) {
        String submit = request.getParameter("submit");
        String message = "Type in a name and search first to see if it is available.";
        String action = "search";
        Properties prop = new Properties();
        if ("Save".equals(submit)) {
            String actionParam = request.getParameter("action");
            String companyName = request.getParameter("company_name");
            String displayCompanyName = nullToEmpty(companyName);
            if (actionParam != null && actionParam.startsWith("edit")) {
                if (companyName != null && companyName.equals(actionParam.substring("edit".length()))) {
                    Properties val = collectFormValues(request);
                    boolean updated = thirdPartyService.update3rdAddr(request.getParameter("id"), val);
                    if (updated) {
                        message = displayCompanyName + " is updated. "
                                + "Type in a name and search first to see if it is available.";
                        action = "search";
                        prop.setProperty("company_name", displayCompanyName);
                    } else {
                        message = displayCompanyName + " is NOT updated. Action failed! Try edit it again.";
                        action = "edit" + companyName;
                        prop = val;
                        prop.setProperty("id", request.getParameter("id"));
                    }
                } else {
                    message = "You can NOT save the name - " + displayCompanyName
                            + ". Please search the name first.";
                    action = "search";
                    prop.setProperty("company_name", displayCompanyName);
                }
            } else if (actionParam != null && actionParam.startsWith("add")) {
                if (companyName != null && companyName.equals(actionParam.substring("add".length()))) {
                    Properties val = collectFormValues(request);
                    int added = thirdPartyService.addOne3rdAddrRecord(val);
                    if (added > 0) {
                        message = displayCompanyName + " is added. "
                                + "Type in a name and search first to see if it is available.";
                        action = "search";
                        prop.setProperty("company_name", displayCompanyName);
                    } else {
                        message = displayCompanyName + " is NOT added. Action failed! Try edit it again.";
                        action = "add" + companyName;
                        prop = val;
                    }
                } else {
                    message = "You can NOT save the name - " + displayCompanyName
                            + ". Please search the name first.";
                    action = "search";
                    prop.setProperty("company_name", displayCompanyName);
                }
            } else {
                message = "You can NOT save the name. Please search the name first.";
            }
        } else if ("Search".equals(submit)) {
            String companyName = request.getParameter("company_name");
            if (companyName == null) {
                message = "Please type in a right name.";
            } else {
                Properties found = thirdPartyService.get3rdAddrProp(companyName);
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
        List<Properties> companyOptions = thirdPartyService.get3rdAddrNameList();

        return OnThirdPartyAddressEditViewModel.builder()
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
