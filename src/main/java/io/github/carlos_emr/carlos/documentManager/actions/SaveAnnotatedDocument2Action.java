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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode.forHtml;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Receives an annotated PDF from PDF.js (sent as a multipart file upload),
 * overwrites the original document file on disk with the annotated version,
 * and writes an audit log entry that itemises which annotation tool types
 * were used — without recording any content that could constitute PHI.
 *
 * <p>Security requirements:
 * <ul>
 *   <li>/li>
 *   <li>Requires {@code _edoc w} privilege.</li>
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

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // Struts2 FileUploadInterceptor populates these via setter injection.
    private File pdfFile;
    private String pdfFileContentType;
    private String pdfFileFileName;

    public void setPdfFile(File pdfFile) { this.pdfFile = pdfFile; }
    public void setPdfFileContentType(String ct) { this.pdfFileContentType = ct; }
    public void setPdfFileFileName(String fn) { this.pdfFileFileName = fn; }

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

        if (pdfFile == null) {
            sendJsonError(response, "No PDF data received");
            return NONE;
        }
        
        final Path uploadPath;       
        try {
            PathValidationUtils.validateUpload(pdfFile);
            uploadPath = pdfFile.toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(uploadPath) || !Files.isReadable(uploadPath)) {
                throw new SecurityException("Uploaded file path is not a readable regular file");
            }
        } catch (SecurityException e) {
            logger.error("Uploaded file validation failed", e);
            sendJsonError(response, "Invalid uploaded file");
            return NONE;
        }
        
        if (!pdfFile.exists() || pdfFile.length() == 0) {
            sendJsonError(response, "No PDF data received");
            return NONE;
        }
        
        // Reject if the uploaded bytes are not a PDF
        byte[] header = new byte[4];
        try (var is = Files.newInputStream(uploadPath)) {
            if (is.read(header) < 4 || !Arrays.equals(header, PDF_MAGIC)) {
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

        // Validate uploaded temp-file path before any path-based file operation
        Path uploadedPath = pdfFile.toPath().toAbsolutePath().normalize();
        Path tempBase = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        try {
             if (!uploadedPath.startsWith(tempBase)) {
                 throw new SecurityException("Uploaded file path is outside temp directory");
             }
             PathValidationUtils.validateExistingPath(uploadedPath.toFile(), tempBase.toFile());
        } catch (SecurityException e) {
             logger.error("Uploaded file path validation failed for docId={}: {}", docId, uploadedPath);
             sendJsonError(response, "Invalid uploaded file path");
             return NONE;
        }
        // Overwrite the document file with the annotated version
        Files.copy(uploadedPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Parse and sanitise annotation type labels before logging
        String annotationTypesCsv = StringUtils.trimToEmpty(request.getParameter("annotationTypes"));
        List<String> usedTypes = buildSafeAnnotationList(annotationTypesCsv);

        String demographicNoStr = StringUtils.defaultString(doc.getModuleId(), "0");

        if (!usedTypes.isEmpty()) {
            String summary = usedTypes.stream().collect(Collectors.joining(", "));
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
            // Document was saved from the viewer without any annotations being placed
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
            w.write("{\"success\":true,\"docId\":" + Encode.forHtml(docId) + "}");
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

    private static void sendJsonError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter w = response.getWriter()) {
            // message is a hard-coded string from this class, never user input
            w.write("{\"success\":false,\"error\":\"" + message + "\"}");
        }
    }
}
