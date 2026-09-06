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
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentPatientLink;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * GET-only gate that sends an inbox document into the shared fax pipeline.
 *
 * <p>Validates that:
 * <ul>
 *   <li>The caller has {@code _edoc r} and {@code _fax r} privileges, and access to the
 *       linked patient record when the document is patient-linked.</li>
 *   <li>At least one active fax account is configured.</li>
 *   <li>The document exists on disk as a regular file inside the document store.</li>
 *   <li>The document is a PDF; only PDFs can be sent directly.</li>
 * </ul>
 *
 * <p>On success it redirects to {@code /fax/faxAction?method=prepareFax} rather than
 * forwarding to the cover page itself. That matters for security, not tidiness:
 * {@code prepareFax} stages its own copy of the file under the application temp root and
 * records a session claim which {@code queue()} consumes, so the path that is eventually
 * faxed is one the server chose. Forwarding to the cover page with a document-store path in
 * a form field would let a client substitute a path of its own.
 *
 * <p>Every refusal resolves to the {@code noFax} result with a user-visible reason in the
 * {@code message} request attribute.
 *
 * @since 2026-06
 */
public class FaxDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    /** Request attribute the {@code noFax} view renders. */
    private static final String MESSAGE_ATTRIBUTE = "message";

    /** Result name for every user-visible refusal on this gate. */
    private static final String NO_FAX = "noFax";

    private static final String INVALID_PATH_MESSAGE = "Invalid document path.";

    private final transient SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final transient FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

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

        Integer docId = parseDocId(request.getParameter("docId"));
        if (docId == null) {
            return refuse(request, "A valid document ID is required.");
        }

        // Checked before the document is loaded: with no account configured there is nothing
        // this screen could do with it.
        if (faxManager.getFaxGatewayAccounts(loggedInInfo).isEmpty()) {
            return refuse(request,
                    "No active fax accounts are configured. Contact your system administrator.");
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null) {
            return refuse(request, "Document not found.");
        }

        String problem = faxabilityProblem(doc, docId);
        if (problem != null) {
            return refuse(request, problem);
        }

        int demographicNo = resolveDemographicNo(doc, docId);
        if (demographicNo > 0
                && !securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("Unauthorized access to patient record");
        }

        return redirectToPreparedFax(request, response, docId, demographicNo);
    }

    private static Integer parseDocId(String raw) {
        String trimmed = StringUtils.trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return a user-visible reason this document cannot be faxed, or {@code null} when it can.
     *         Reasons are deliberately generic: the caller is authorised for {@code _edoc}, but a
     *         stored path is still infrastructure detail that does not belong in the browser.
     */
    private static String faxabilityProblem(EDoc doc, int docId) {
        // Exact match, not contains("pdf"): "application/pdfx" is a different format, and the
        // staging path this gate redirects into requires application/pdf exactly — so a
        // substring test only moved the refusal to a later, less explicable error.
        String contentType = StringUtils.trimToEmpty(doc.getContentType());
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            return "Only PDF documents can be faxed directly. This document is a " + contentType
                    + " file. Please convert it to PDF before faxing.";
        }

        String filePath = doc.getFilePath();
        if (StringUtils.isBlank(filePath)) {
            return "Document file path is not available.";
        }

        java.io.File documentDir = new java.io.File(
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "/var/lib/OscarDocument/"));
        Path docPath;
        try {
            docPath = Paths.get(filePath);
            PathValidationUtils.validateExistingPath(docPath.toFile(), documentDir);
        } catch (java.nio.file.InvalidPathException | SecurityException e) {
            logger.error("Document path rejected for docId={}: {}", docId, LogSafe.sanitize(filePath, 1024));
            return INVALID_PATH_MESSAGE;
        }

        if (!Files.exists(docPath) || !Files.isRegularFile(docPath)) {
            logger.error("Document file not found on disk for docId={}: {}", docId, LogSafe.sanitize(filePath, 1024));
            return "Document file is not available on the server.";
        }

        return null;
    }

    /** @return the linked demographic number, or 0 when the document is not patient-linked. */
    private static int resolveDemographicNo(EDoc doc, int docId) {
        int demographicNo = DocumentPatientLink.demographicNoOf(doc);
        if (demographicNo == 0) {
            logger.debug("Document {} is not linked to a patient; faxing it as unfiled", docId);
        }
        return demographicNo;
    }

    /**
     * Hands off to the shared fax pipeline rather than forwarding to the cover page with this
     * document's stored path. {@code prepareFax} stages its own copy under the application temp
     * root and records a session claim that {@code queue()} must consume, so a client can never
     * name a document-store path of its own choosing on the cover-page form. Mirrors
     * {@code AddEForm2Action.redirectToPreparedFax}.
     */
    private String redirectToPreparedFax(HttpServletRequest request, HttpServletResponse response,
                                         int docId, int demographicNo) {
        String target = request.getContextPath()
                + "/fax/faxAction?method=prepareFax"
                + "&transactionType="
                + URLEncoder.encode(FaxManager.TransactionType.DOCUMENT.name(), StandardCharsets.UTF_8)
                + "&transactionId="
                + URLEncoder.encode(String.valueOf(docId), StandardCharsets.UTF_8)
                + "&demographicNo="
                + URLEncoder.encode(String.valueOf(demographicNo), StandardCharsets.UTF_8);
        try {
            response.sendRedirect(target);
        } catch (java.io.IOException e) {
            logger.error("Could not redirect document {} into the fax pipeline", docId, e);
            return refuse(request, "The fax screen could not be opened.");
        }
        return NONE;
    }

    private String refuse(HttpServletRequest request, String reason) {
        request.setAttribute(MESSAGE_ATTRIBUTE, reason);
        return NO_FAX;
    }
}
