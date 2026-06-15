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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * GET-only view gate that prepares an inbox document for faxing.
 *
 * <p>Validates that:
 * <ul>
 *   <li>The caller has {@code _edoc r} and {@code _fax r} privileges.</li>
 *   <li>At least one active fax account is configured.</li>
 *   <li>The document exists on disk as a regular file.</li>
 *   <li>The document is a PDF (only PDFs can be sent directly; non-PDFs
 *       produce a user-visible error result).</li>
 * </ul>
 *
 * <p>On success, forwards to {@code CoverPage.jsp} via the {@code "preview"} result,
 * with the following request attributes set:
 * <ul>
 *   <li>{@code faxFilePath} – absolute path to the document PDF</li>
 *   <li>{@code transactionType} – {@code "DOCUMENT"}</li>
 *   <li>{@code transactionId} – the document number (int)</li>
 *   <li>{@code demographicNo} – the linked demographic number, or 0 if unlinked</li>
 *   <li>{@code accounts} – list of active {@link FaxConfig} accounts</li>
 * </ul>
 *
 * @since 2026-06
 */
public class FaxDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"GET".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }

        String docIdStr = StringUtils.trimToNull(request.getParameter("docId"));
        if (docIdStr == null) {
            request.setAttribute("message", "Document ID is required.");
            return "noFax";
        }

        int docId;
        try {
            docId = Integer.parseInt(docIdStr);
        } catch (NumberFormatException e) {
            request.setAttribute("message", "Invalid document ID.");
            return "noFax";
        }

        // Check fax accounts before loading the document
        List<FaxConfig> accounts = faxManager.getFaxGatewayAccounts(loggedInInfo);
        if (accounts.isEmpty()) {
            request.setAttribute("message", "No active fax accounts are configured. Contact your system administrator.");
            return "noFax";
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null) {
            request.setAttribute("message", "Document not found.");
            return "noFax";
        }

        // Only PDF documents can be faxed directly
        String contentType = StringUtils.defaultString(doc.getContentType());
        if (!contentType.toLowerCase().contains("pdf")) {
            request.setAttribute("message",
                "Only PDF documents can be faxed directly. This document is a " + contentType + " file. " +
                "Please convert it to PDF before faxing.");
            return "noFax";
        }

        String filePath = doc.getFilePath();
        if (StringUtils.isBlank(filePath)) {
            request.setAttribute("message", "Document file path is not available.");
            return "noFax";
        }

        java.io.File documentDir = new java.io.File(
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));
        Path docPath = Paths.get(filePath);
        try {
            PathValidationUtils.validateExistingPath(docPath.toFile(), documentDir);
        } catch (SecurityException e) {
            logger.error("Path validation failed for docId={}: {}", docId, LogSafe.sanitize(filePath, 1024));
            request.setAttribute("message", "Invalid document path.");
            return "noFax";
        }

        if (!Files.exists(docPath) || !Files.isRegularFile(docPath)) {
            logger.error("Document file not found on disk for docId={}: {}", docId, LogSafe.sanitize(filePath, 1024));
            request.setAttribute("message", "Document file is not available on the server.");
            return "noFax";
        }

        // Determine the linked demographic number (0 = unlinked)
        String moduleId = StringUtils.defaultString(doc.getModuleId());
        int demographicNo = 0;
        if (!moduleId.isEmpty() && !"-1".equals(moduleId) && !"null".equalsIgnoreCase(moduleId)) {
            try {
                demographicNo = Integer.parseInt(moduleId);
            } catch (NumberFormatException e) {
                logger.debug("Non-numeric moduleId '{}' for docId={}, treating as unlinked", moduleId, docId);
            }
        }

        // faxReady=true means annotations have already been saved; go directly to fax composition.
        // Otherwise, open the annotation viewer so the provider can review and annotate first.
        boolean faxReady = "true".equalsIgnoreCase(request.getParameter("faxReady"));

        request.setAttribute("transactionType", FaxManager.TransactionType.DOCUMENT.name());
        request.setAttribute("transactionId", docId);
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("accounts", accounts);

        if (faxReady) {
            request.setAttribute("faxFilePath", filePath);
            return "preview";
        }

        request.setAttribute("docId", docId);
        return "annotate";
    }
}
