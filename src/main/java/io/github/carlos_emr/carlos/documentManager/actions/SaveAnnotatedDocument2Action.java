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

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.CarlosProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Receives an annotated PDF from PDF.js (sent as a multipart file upload),
 * overwrites the original document file on disk with the annotated version,
 * and writes an audit log entry that itemises which annotation tool types
 * were used — without recording any content that could constitute PHI.
 *
 * <p>File upload is read via {@code request.getPart("pdfFile")} rather than
 * Struts2's FileUploadInterceptor setter injection. The interceptor approach
 * fails because {@link io.github.carlos_emr.carlos.app.MultiReadHttpServletRequest}
 * caches the body for CSRF token extraction, and the Struts2 stream-based
 * multipart parser ({@code jakarta-stream}) cannot reliably re-read from the
 * cached stream. {@code MultiReadHttpServletRequest.getParts()} parses directly
 * from the same cached bytes and is the correct entry point here.</p>
 *
 * <p>Security requirements:
 * <ul>
 *   <li>Requires {@code _edoc w} privilege (enforced in this action). The upstream
 *       {@link FaxDocument2Action} gate also requires {@code _edoc r} and
 *       {@code _fax r} before the annotation viewer is reached.</li>
 *   <li>The uploaded bytes must start with the {@code %PDF} magic header.</li>
 *   <li>The resolved file path must lie within the document's own directory
 *       (validated via {@link PathValidationUtils#validateExistingPath}).</li>
 * </ul>
 *
 * <p>The {@code annotationTypes} form field is a comma-separated subset of:
 * {@code signed}, {@code text}, {@code drawn}, {@code highlighted}.
 * The log entry records only these labels — never annotation content.
 *
 * @since 2026-06
 */
public class SaveAnnotatedDocument2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F'};
    private static final List<String> ALLOWED_ANNOTATION_TYPES =
            List.of("signed", "text", "drawn", "highlighted");

    /** 50 MB — matches struts.multipart.maxSize and MultiReadHttpServletRequest.MAX_BODY_SIZE. */
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        String docIdStr = StringUtils.trimToNull(request.getParameter("docId"));
        if (docIdStr == null) {
            sendJsonError(response, "docId is required");
            return NONE;
        }

        int docId;
        try {
            docId = Integer.parseInt(docIdStr);
        } catch (NumberFormatException e) {
            sendJsonError(response, "Invalid docId");
            return NONE;
        }

        // Read the uploaded file via the Part API. MultiReadHttpServletRequest.getParts()
        // parses from its cached body bytes, so this works regardless of whether the CSRF
        // filter already consumed the original input stream.
        Part filePart;
        try {
            filePart = request.getPart("pdfFile");
        } catch (ServletException | IOException e) {
            logger.error("Failed to parse multipart parts for docId={}", docId, e);
            sendJsonError(response, "Failed to read uploaded file");
            return NONE;
        }

        if (filePart == null || filePart.getSize() == 0L) {
            sendJsonError(response, "No PDF data received");
            return NONE;
        }

        if (filePart.getSize() > MAX_UPLOAD_BYTES) {
            logger.warn("Rejecting oversized annotated PDF for docId={}: {} bytes", docId, filePart.getSize());
            sendJsonError(response, "Uploaded file exceeds maximum allowed size");
            return NONE;
        }

        // Check %PDF magic header using a separate stream open so the main copy
        // still reads from byte 0. Part.getInputStream() can be opened multiple times
        // (DiskFileItem opens a fresh FileInputStream; in-memory returns ByteArrayInputStream).
        byte[] header = new byte[4];
        try (InputStream is = filePart.getInputStream()) {
            int totalRead = 0;
            while (totalRead < 4) {
                int r = is.read(header, totalRead, 4 - totalRead);
                if (r < 0) break;
                totalRead += r;
            }
            if (totalRead < 4 || !Arrays.equals(header, PDF_MAGIC)) {
                logger.warn("SaveAnnotatedDocument: upload for docId={} failed PDF magic check", docId);
                sendJsonError(response, "Uploaded file is not a valid PDF");
                return NONE;
            }
        }

        EDoc doc = EDocUtil.getDoc(String.valueOf(docId));
        if (doc == null || StringUtils.isBlank(doc.getFilePath())) {
            sendJsonError(response, "Document not found");
            return NONE;
        }

        Path targetPath = Paths.get(doc.getFilePath());
        try {
            // Validate against the configured document root, not just the file's own parent.
            // Using the parent as the trust boundary would allow a corrupted DB row with an
            // out-of-tree path to overwrite arbitrary files on the filesystem.
            java.io.File documentDir = new java.io.File(
                    CarlosProperties.getInstance().getProperty(
                            "DOCUMENT_DIR", "/var/lib/OscarDocument/"));
            PathValidationUtils.validateExistingPath(targetPath.toFile(), documentDir);
        } catch (SecurityException e) {
            logger.error("Path traversal attempt for docId={}", docId);
            sendJsonError(response, "Invalid document path");
            return NONE;
        }

        // Overwrite the document file with the annotated version atomically.
        // Write to a temporary file first, then move atomically to avoid corruption on failure.
        Path tempFile = null;
        try (InputStream is = filePart.getInputStream()) {
            tempFile = Files.createTempFile(targetPath.getParent(), ".annotated-", ".pdf.tmp");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Atomic move with fallback if ATOMIC_MOVE is not supported on this filesystem
            try {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (UnsupportedOperationException e) {
                logger.warn("ATOMIC_MOVE not supported for docId={}, falling back to standard move", docId);
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null; // Successfully moved, no cleanup needed
        } catch (IOException e) {
            logger.error("Failed to write annotated document for docId={}", docId, e);
            sendJsonError(response, "Unable to save annotated document");
            return NONE;
        } finally {
            // Clean up temp file if move failed
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    logger.warn("Failed to delete temp file after error: {}", tempFile, e);
                }
            }
        }

        // Parse and sanitise annotation type labels before logging
        String annotationTypesCsv = StringUtils.trimToEmpty(request.getParameter("annotationTypes"));
        List<String> usedTypes = buildSafeAnnotationList(annotationTypesCsv);

        String demographicNoStr = StringUtils.defaultString(doc.getModuleId(), "0");

        if (!usedTypes.isEmpty()) {
            String summary = String.join(", ", usedTypes);
            LogAction.addLog(
                    loggedInInfo,
                    "DOCUMENT_ANNOTATED",
                    "document",
                    String.valueOf(docId),
                    demographicNoStr,
                    "Provider added annotations before fax. Annotation types used: " + summary
            );
            logger.info("Document {} annotated by provider {} — types: {}",
                    docId, loggedInInfo.getLoggedInProviderNo(), summary);
        } else {
            LogAction.addLog(
                    loggedInInfo,
                    "DOCUMENT_SAVED_FAX_PREP",
                    "document",
                    String.valueOf(docId),
                    demographicNoStr,
                    "Provider saved document from fax annotation viewer (no annotations added)"
            );
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter w = response.getWriter()) {
            // docId was already parsed as an int above, so it is inherently safe to embed
            w.write("{\"success\":true,\"docId\":" + docId + "}");
        }
        return NONE;
    }

    /**
     * Filters the client-supplied annotation type list against the known-safe allowlist,
     * preventing injection of arbitrary strings into log entries.
     */
    private static List<String> buildSafeAnnotationList(String csv) {
        if (StringUtils.isBlank(csv)) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim().toLowerCase();
            if (ALLOWED_ANNOTATION_TYPES.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static void sendJsonError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter w = response.getWriter()) {
            // message is a hard-coded string from this class, never user input
            w.write("{\"success\":false,\"error\":\"" + message + "\"}");
        }
    }
}
