/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
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

import java.io.File;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingLegacyReportViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2Action gate / data assembler for {@code billingLreport.jsp}, the
 * Ontario MOH "L"-prefixed EDT response renderer.
 *
 * <p>The legacy JSP body resolved {@code filename} from request attribute
 * (set by {@code BillingDocumentErrorReportUpload2Action}'s {@code outside}
 * result) or from a query parameter, read the selected EDT folder file into
 * a string, picked an XSL stylesheet
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
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
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
        String safeFilename = FilenameUtils.getName(filename);
        if (!safeFilename.equals(filename)) {
            ServletActionContext.getResponse().sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid report filename.");
            return NONE;
        }

        // Match the legacy heuristic for choosing the XSL stylesheet:
        // chars [2..4) of the filename are "OU" → outpatient stylesheet,
        // anything else → ES (error summary).
        String xslName = "ES";
        if (filename.length() >= 4 && "OU".equals(filename.substring(2, 4))) {
            xslName = "OU";
        }

        String fileContents = "";
        if (!filename.isEmpty()) {
            try {
                String folderPath = selectedFolderPath(request);
                if (folderPath != null && !folderPath.isEmpty()) {
                    File folderDir = new File(folderPath);
                    File target = PathValidationUtils.validatePath(safeFilename, folderDir);
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
                        LogSafe.sanitize(filename), e);
                fileContents = "";
                request.setAttribute("readError",
                        "Could not read selected MOH response file.");
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

    private static String selectedFolderPath(HttpServletRequest request) {
        String folderParam = request.getParameter("folder");
        EDTFolder folder = EDTFolder.getFolder(folderParam);
        return CarlosProperties.getInstance().getProperty("ONEDT_" + folder.name());
    }
}
