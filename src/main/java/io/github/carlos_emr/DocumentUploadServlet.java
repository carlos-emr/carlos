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
package io.github.carlos_emr;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet for handling document file uploads with path validation and security.
 *
 * <p>This servlet processes multipart/form-data file uploads and stores documents
 * in configured directories. Key features:</p>
 * <ul>
 *   <li>File upload processing using the Jakarta Servlet Part API</li>
 *   <li>Path traversal attack prevention via {@link PathValidationUtils}</li>
 *   <li>Configurable upload directories (documents, inbox, archive)</li>
 *   <li>Request forwarding to configured success pages</li>
 * </ul>
 *
 * <p>Configuration properties:</p>
 * <ul>
 *   <li><code>DOCUMENT_DIR</code> - Main document storage directory</li>
 *   <li><code>ONEDT_INBOX</code> - Inbox folder for incoming documents</li>
 *   <li><code>ONEDT_ARCHIVE</code> - Archive folder for processed documents</li>
 *   <li><code>RA_FORWORD</code> - Forward destination after upload</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Uses PathValidationUtils to prevent directory
 * traversal attacks. All uploaded files are validated before storage.</p>
 *
 * @see PathValidationUtils
 * @see CarlosProperties
 */
public class DocumentUploadServlet extends HttpServlet {

    /**
     * Handles HTTP requests for document uploads.
     * Processes multipart form data and stores uploaded files securely.
     *
     * @param request the HTTP servlet request containing the uploaded file
     * @param response the HTTP servlet response
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use")
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }
        if (!hasUploadPrivilege(loggedInInfo)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Upload privilege required");
            return;
        }

        String foldername = "", fileheader = "", forwardTo = "";
        forwardTo = CarlosProperties.getInstance().getProperty("RA_FORWORD");
        foldername = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        String inboxFolder = CarlosProperties.getInstance().getProperty("ONEDT_INBOX");
        String archiveFolder = CarlosProperties.getInstance().getProperty("ONEDT_ARCHIVE");

        if (forwardTo == null || forwardTo.length() < 1)
            return;

        String providedFilename = request.getParameter("filename");
        if (providedFilename != null) {
            providedFilename = URLDecoder.decode(providedFilename, "UTF-8");

            // Validate and sanitize the filename to prevent path traversal
            String sanitizedFilename = FilenameUtils.getName(providedFilename);
            if (sanitizedFilename == null || sanitizedFilename.isEmpty()) {
                MiscUtils.getLogger().error("Invalid filename provided: {}", LogSafe.sanitize(providedFilename)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                return;
            }

            File documentDirectory = new File(foldername);
            File inboxDir = new File(inboxFolder);
            File archiveDir = new File(archiveFolder);

            try {
                // Use PathValidationUtils for secure path validation
                File providedFile;
                try {
                    // First try inbox directory
                    providedFile = PathValidationUtils.validatePath(sanitizedFilename, inboxDir);

                    // If file doesn't exist in inbox, check archive
                    if (!providedFile.exists()) {
                        providedFile = PathValidationUtils.validatePath(sanitizedFilename, archiveDir);
                    }
                } catch (SecurityException e) {
                    MiscUtils.getLogger().error("File does not reside in a valid path: {}", LogSafe.sanitize(providedFilename), e);
                    return;
                }

                // Verify the file exists before copying
                if (!providedFile.exists()) {
                    MiscUtils.getLogger().error("File not found: {}", LogSafe.sanitize(sanitizedFilename)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                    return;
                }

                FileUtils.copyFileToDirectory(providedFile, documentDirectory);
                fileheader = sanitizedFilename;

            } catch (IOException e) {
                MiscUtils.getLogger().error("Error processing file: {}", LogSafe.sanitize(sanitizedFilename), e);
                return;
            }
        } else {

            try {
                for (Part part : request.getParts()) {
                    String submittedFilename = part.getSubmittedFileName();
                    if (submittedFilename == null || submittedFilename.isEmpty()) {
                        continue;
                    }

                    try {
                        // Use PathValidationUtils to sanitize and validate the destination path
                        File documentDir = new File(foldername);
                        File savedFile = PathValidationUtils.validatePath(submittedFilename, documentDir);

                        StoredUpload stored;
                        try (InputStream in = part.getInputStream()) {
                            stored = storeUpload(in, savedFile.toPath());
                        }
                        if (stored == StoredUpload.CONFLICT) {
                            // A different file already holds this name. Importing would process the stored
                            // file, not this one, and report success for a file that was never kept (#6333).
                            MiscUtils.getLogger().warn("Upload refused: a different file is already stored as {}",
                                    LogSafe.sanitize(savedFile.getName()));
                            request.setAttribute(UPLOAD_CONFLICT_ATTRIBUTE, Boolean.TRUE);
                            continue;
                        }
                        // The stored file is the one uploaded (a repeat of the same bytes is the same file).
                        fileheader = savedFile.getName();

                        if (stored == StoredUpload.STORED
                                && CarlosProperties.getInstance().isPropertyActive("moh_file_management_enabled")) {
                            File inboxDir = new File(inboxFolder);
                            FileUtils.copyFileToDirectory(savedFile, inboxDir);
                        }
                    } catch (SecurityException e) {
                        MiscUtils.getLogger().error("Invalid uploaded filename: {}", LogSafe.sanitize(submittedFilename), e);
                        continue;
                    } catch (IOException e) {
                        MiscUtils.getLogger().error("Error processing file: {}", LogSafe.sanitize(submittedFilename), e);
                        continue;
                    }
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        DocumentBean documentBean = new DocumentBean();
        request.setAttribute("documentBean", documentBean);
        documentBean.setFilename(fileheader);
        RequestDispatcher dispatch = getServletContext().getRequestDispatcher(forwardTo);
        dispatch.forward(request, response);
    }

    /** Request attribute the RA page reads to say that an upload was refused because its name was taken. */
    public static final String UPLOAD_CONFLICT_ATTRIBUTE = "raUploadConflict";

    /** What happened to an uploaded file's bytes. */
    enum StoredUpload {
        /** The file was new and is now stored. */
        STORED,
        /** A file with this name and exactly these bytes was already stored; nothing was written. */
        IDENTICAL_REPEAT,
        /** A file with this name but different bytes is stored; it was left as it was. */
        CONFLICT
    }

    /**
     * Stores an upload under a name that must not already exist, without ever replacing a stored
     * file. The create is exclusive, so of two concurrent uploads of one name exactly one writes.
     * When the name is taken, the stored bytes are compared with the upload: the same bytes are a
     * repeat (an RA re-sent unchanged imports idempotently), different bytes are a conflict the
     * caller must report. A write that fails part-way removes the partial file it created.
     *
     * @param upload the uploaded bytes, read at most once
     * @param target the validated destination
     * @return what was stored
     * @throws IOException if the upload cannot be read or written
     */
    static StoredUpload storeUpload(InputStream upload, Path target) throws IOException {
        OutputStream out;
        try {
            out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException taken) {
            return sameBytes(upload, target) ? StoredUpload.IDENTICAL_REPEAT : StoredUpload.CONFLICT;
        }
        boolean written = false;
        try (out) {
            upload.transferTo(out);
            written = true;
        } finally {
            if (!written) Files.deleteIfExists(target);
        }
        return StoredUpload.STORED;
    }

    private static boolean sameBytes(InputStream upload, Path stored) throws IOException {
        try (InputStream existing = new BufferedInputStream(Files.newInputStream(stored));
             InputStream incoming = new BufferedInputStream(upload)) {
            int a;
            int b;
            do {
                a = existing.read();
                b = incoming.read();
                if (a != b) return false;
            } while (a != -1);
            return true;
        }
    }

    private boolean hasUploadPrivilege(LoggedInInfo loggedInInfo) {
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null);
    }
}
