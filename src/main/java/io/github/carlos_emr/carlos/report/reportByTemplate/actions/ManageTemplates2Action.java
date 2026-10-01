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

//Action that takes place when adding or editing template XML
/*
 * GenerateReport.java
 *
 * Created on March 02/2007, 10:47 PM
 *
 */

package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Saves, edits and deletes Report by Template definitions from the template editor's
 * textarea ({@code xmltext}) and the configuration page's Delete button.
 *
 * <p>Every operation that changes a template ({@code action=add|edit|delete}) is POST-only and
 * requires {@code _report} write; reading the editor needs {@code _report} read. The SQL a
 * template carries is checked by {@link ReportManager} before it is stored (see
 * {@code ReportTemplateSqlValidator}), which is the control the packaged WAF exclusion for
 * {@code ARGS:xmltext} on this route depends on.</p>
 *
 * @since 2007-03-02
 */
public class ManageTemplates2Action extends ActionSupport {
    private static final Set<String> MUTATING_ACTIONS = Set.of("add", "edit", "delete");

    private final SecurityInfoManager securityInfoManager;
    private final Supplier<ReportManager> reportManagerFactory;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public ManageTemplates2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), ReportManager::new);
    }

    ManageTemplates2Action(SecurityInfoManager securityInfoManager, Supplier<ReportManager> reportManagerFactory) {
        this.securityInfoManager = securityInfoManager;
        this.reportManagerFactory = reportManagerFactory;
    }

    /**
     * Applies one template operation and returns to the editor or the configuration page.
     *
     * @return {@code "done"}, {@code "deleted"}, {@link #SUCCESS}, or {@link #NONE} after a 405
     * @throws IOException if the 405 response cannot be written
     * @throws SecurityException if the user lacks {@code _report} read, or write for a mutation
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        String action = request.getParameter("action");
        if (action != null && MUTATING_ACTIONS.contains(action)) {
            // Refuse a cross-site GET before anything is written: these operations replace or
            // remove stored SQL that later runs against the clinical database.
            if (!"POST".equals(request.getMethod())) {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return NONE;
            }
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.WRITE, null)) {
                throw new SecurityException("missing required sec object (_report)");
            }
        }

        String templateId = request.getParameter("templateid");
        String xmltext = request.getParameter("xmltext");
        String uuid = request.getParameter("uuid");
        String message = "Error: Improper request - Action param missing";
        if ("delete".equals(action)) {
            message = reportManagerFactory.get().deleteTemplate(templateId, loggedInInfo);
            if (message.equals("")) return "deleted";
        } else if ("add".equals(action)) {
            message = reportManagerFactory.get().addTemplate(uuid, xmltext, loggedInInfo);
        } else if ("edit".equals(action)) {
            message = reportManagerFactory.get().updateTemplate(uuid, templateId, xmltext, loggedInInfo);
        }
        boolean failed = message.toLowerCase().startsWith("error")
                || message.toLowerCase().startsWith("exception");
        request.setAttribute("message", message);
        request.setAttribute("action", action);
        request.setAttribute("templateid", request.getParameter("templateid"));
        request.setAttribute("opentext", request.getParameter("opentext"));
        if (failed && xmltext != null) {
            // A refused save re-shows what the author typed instead of the stored copy, so the
            // message can be acted on without retyping the template.
            request.setAttribute("submittedXml", xmltext);
        }

        if (request.getParameter("done") != null
                && "done".equalsIgnoreCase(request.getParameter("done"))
                && !failed) {
            return "done";
        }

        return SUCCESS;
    }

}
