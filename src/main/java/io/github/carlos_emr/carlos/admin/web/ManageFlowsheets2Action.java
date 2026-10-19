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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Admin action for managing flowsheet enable/disable state.
 *
 * <p>Viewing the list needs <em>read</em> on any of {@code _admin}, {@code _admin.misc} or
 * {@code _admin.flowsheet}; changing a flowsheet needs <em>write</em> on one of them. The read
 * gate matches the Administration left nav and {@code manageFlowsheets.jsp}, so every login
 * offered the link can open it (issue #4623: the seeded doctor role holds only
 * {@code _admin.flowsheet r} and used to be answered 403). A POST carrying a {@code method}
 * parameter is the mutation: it routes to {@code enable} or {@code disable}, applies the change via
 * {@link MeasurementTemplateFlowSheetConfig}, then redirects (PRG pattern). Anything else returns
 * SUCCESS to render the list view, and publishes {@value #WRITE_ACCESS_ATTRIBUTE} so the view
 * offers the enable/disable and upload controls only to a login that may use them.</p>
 *
 * @since 2026-04-05
 */
public class ManageFlowsheets2Action extends ActionSupport {

    /**
     * Request attribute read by {@code manageFlowsheets.jsp}: {@link Boolean#TRUE} when the login
     * holds write on a gate object. A view that does not receive it renders read-only.
     */
    static final String WRITE_ACCESS_ATTRIBUTE = "flowsheetWriteAccess";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "UNVALIDATED_REDIRECT"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String method = request.getParameter("method");
        boolean mutation = "POST".equalsIgnoreCase(request.getMethod()) && method != null;

        // Write satisfies read, so a writer needs no second lookup; a mutation never settles for read.
        boolean canWrite = hasFlowsheetAdminPrivilege(loggedInInfo, "w");
        if (!canWrite && (mutation || !hasFlowsheetAdminPrivilege(loggedInInfo, "r"))) {
            throw new SecurityException("missing required sec object (_admin, _admin.misc, or _admin.flowsheet)");
        }

        if (mutation) {
            String name = request.getParameter("name");
            if (name == null || name.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/admin/ManageFlowsheets");
                return NONE;
            }
            MeasurementTemplateFlowSheetConfig config = MeasurementTemplateFlowSheetConfig.getInstance();

            try {
                if ("disable".equalsIgnoreCase(method)) {
                    config.disableFlowsheet(name);
                } else if ("enable".equalsIgnoreCase(method)) {
                    config.enableFlowsheet(name);
                } else {
                    MiscUtils.getLogger().warn("Unknown flowsheet method parameter");
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error("Failed to update flowsheet state", e);
            }

            // PRG pattern: redirect after mutation to prevent duplicate submissions
            response.sendRedirect(request.getContextPath() + "/admin/ManageFlowsheets");
            return NONE;
        }

        request.setAttribute(WRITE_ACCESS_ATTRIBUTE, canWrite);
        return SUCCESS;
    }

    /** True when the login holds {@code right} on any object that may administer flowsheets. */
    private boolean hasFlowsheetAdminPrivilege(LoggedInInfo loggedInInfo, String right) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin", right, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", right, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.flowsheet", right, null);
    }
}
