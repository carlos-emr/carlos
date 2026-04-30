/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.File;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingLegacyReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.apache.commons.io.FileUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2Action gate / data assembler for {@code billingLreport.jsp}, the
 * Ontario MOH "L"-prefixed EDT response renderer.
 *
 * <p>The legacy JSP body resolved {@code filename} from request attribute
 * (set by {@code BillingDocumentErrorReportUpload2Action}'s {@code outside}
 * result) or from a query parameter, read
 * {@code ONEDT_INBOX/{filename}} into a string, picked an XSL stylesheet
 * via the third/fourth filename character ({@code "OU"} vs {@code "ES"}),
 * and embedded the bytes into JS. All of that work moves to this action,
 * which exposes {@code ${lreportModel}} so the JSP renders pure EL/JSTL.</p>
 *
 * <p>Authorization: {@code _admin.billing} {@code w} (matches the
 * BillingOnUpload2Action / BillingDocumentErrorReportUpload2Action gates
 * that drive this view).</p>
 *
 * @since 2026-04-25
 */
public class BillingLegacyReport2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public BillingLegacyReport2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        String filename = (String) request.getAttribute("filename");
        if (filename == null) {
            filename = request.getParameter("filename");
        }
        if (filename == null) {
            filename = "";
        }

        // Match the legacy heuristic for choosing the XSL stylesheet:
        // chars [2..4) of the filename are "OU" → outpatient stylesheet,
        // anything else → ES (error summary).
        String xslName = "ES";
        if (filename.length() >= 4 && "OU".equals(filename.substring(2, 4))) {
            xslName = "OU";
        }

        // Mirror the legacy guard: refuse names containing ".." (the
        // pattern was {@code .matches(".*\\.\\..*")}). PathValidationUtils
        // gives us a stronger canonicalisation check on top.
        String fileContents = "";
        if (!filename.isEmpty() && !filename.contains("..")) {
            try {
                String inbox = CarlosProperties.getInstance().getProperty("ONEDT_INBOX");
                if (inbox != null && !inbox.isEmpty()) {
                    File inboxDir = new File(inbox);
                    File target = PathValidationUtils.validatePath(filename, inboxDir);
                    if (target.exists() && target.isFile()) {
                        fileContents = FileUtils.readFileToString(target, StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                // Failing to read an admin-selected MOH response file is an
                // operator-facing problem, not a debug detail — log at ERROR
                // and stash a message on the request so the JSP can render an
                // explicit banner instead of a silent blank report.
                MiscUtils.getLogger().error("billingLreport: failed to read MOH response file {} from ONEDT_INBOX",
                        LogSanitizer.sanitize(filename), e);
                fileContents = "";
                request.setAttribute("readError",
                        "Could not read MOH response file: " + filename);
            }
        }

        BillingLegacyReportViewModel model = BillingLegacyReportViewModel.builder()
                .filename(filename)
                .xslName(xslName)
                .fileContents(fileContents)
                .build();
        request.setAttribute("lreportModel", model);

        return SUCCESS;
    }
}
