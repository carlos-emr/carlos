/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


// form_class - a part of class name
// c_lastVisited, formId - if the form has multiple pages
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.eform.util.EFormPrintPDFUtil;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public final class PrintPDF2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String execute() throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "r", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        int newID = 0;
        try {
            Properties props = new Properties();

            for (Enumeration<?> e = request.getParameterNames(); e.hasMoreElements(); ) {
                String name = (String) e.nextElement();
                props.setProperty(name, request.getParameter(name));
            }

            String submit = request.getParameter("submit");
            log.info("SUBMIT {}", LogSafe.sanitize(submit)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe

            Optional<String> actionValue = findActionValue(submit);
            if (!actionValue.isPresent()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported submit action");
                return NONE;
            }
            String strAction = actionValue.get();

            //if we are graphing, we need to grab info from db and add it to request object
            if ("graph".equals(strAction)) {
                props = EFormPrintPDFUtil.getFrmRourkeGraph(loggedInInfo, props);

                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    String name = (String) e.nextElement();
                    request.setAttribute(name, props.getProperty(name));
                }
            }
            //if we are printing all pages of form, grab info from db and merge with current page info
            else if ("printAll".equals(strAction)) {
                String name;
                for (Enumeration<?> e = props.propertyNames(); e.hasMoreElements(); ) {
                    name = (String) e.nextElement();
                    if (request.getParameter(name) == null)
                        request.setAttribute(name, props.getProperty(name));
                }
            }

            String createPdfPath = request.getContextPath() + "/eform/createpdf";
            String demographicNo = request.getParameter("demographic_no");
            String redirectUrl = createActionURL(createPdfPath, strAction, demographicNo, "" + newID);
            response.sendRedirect(redirectUrl);
        } catch (Exception ex) {
            throw new ServletException(ex);
        }

        return NONE;
    }


    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private Optional<String> findActionValue(String submit) {
        if (submit != null && submit.equalsIgnoreCase("graph")) {
            return Optional.of("graph");
        } else if (submit != null && submit.equalsIgnoreCase("printall")) {
            return Optional.of("printAll");
        } else {
            return Optional.empty();
        }
    }

    private String createActionURL(String where, String action, String demoId, String formId) {
        String temp = null;

        if (action.equals("printAll")) {
            temp = where
                    + "?demographic_no=" + SafeEncode.forUriComponent(demoId)
                    + "&formId=" + SafeEncode.forUriComponent(formId);
        } else {
            temp = where;
        }

        return temp;
    }

}
