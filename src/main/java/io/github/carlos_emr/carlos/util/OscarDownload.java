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
package io.github.carlos_emr.carlos.util;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingImportedFiles;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class OscarDownload extends GenericDownload {

    private static final long serialVersionUID = 1L;
    private static final Logger log = MiscUtils.getLogger();

    private static final Set<String> ALLOWED_HOMEPATH_KEYS = Set.of(
            "homepath", "ohipdownload", "obecdownload"
    );

    /**
     * Serves a validated basename from an authenticated user's session-approved directory.
     * Billing paths, including aliases to HOME_DIR, require reconciled import evidence
     * before the response stream opens. Audited downloads serve a verified private
     * snapshot so replacement of the billing pathname cannot change response bytes.
     *
     * @throws IOException if response or file IO fails
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String rawFilename = req.getParameter("filename");
            String filename = rawFilename == null ? null : PathValidationUtils.validateStrictFileName(rawFilename);
            String homepath = req.getParameter("homepath");

            if (filename == null || filename.isBlank()) {
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required filename parameter.");
                return;
            }
            if (homepath == null || !ALLOWED_HOMEPATH_KEYS.contains(homepath)) {
                log.warn("OscarDownload rejected invalid homepath key from {}", LogSafe.sanitize(req.getRemoteAddr()));
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid download path key.");
                return;
            }
            HttpSession session = req.getSession(false);
            if (session == null) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "You have no right to download the file(s).");
                return;
            }

            String backupfilepath = (String) session.getAttribute(homepath); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): homepath is user-supplied but allowlist-validated against ALLOWED_HOMEPATH_KEYS before use as session lookup key; session value is server-side data
            if (backupfilepath != null
                    && !backupfilepath.isEmpty()
                    && ((String) session.getAttribute("user")) != null) {
                // nosemgrep: semgrep.carlos.httpservlet-path-traversal -- Directory comes from an authenticated server-side session under one of three allowlisted keys; client filenames are strictly validated and contained separately below.
                File downloadDir = new File(backupfilepath).getCanonicalFile();
                if (!downloadDir.isDirectory()) {
                    log.warn("OscarDownload rejected non-directory path from {}", LogSafe.sanitize(req.getRemoteAddr()));
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid download directory.");
                    return;
                }
                String billingHome = CarlosProperties.getInstance().getProperty("HOME_DIR");
                boolean billingDirectory = billingHome != null && !billingHome.isBlank()
                        && downloadDir.equals(new File(billingHome).getCanonicalFile());
                if ("ohipdownload".equals(homepath) || billingDirectory) {
                    if (!filename.equals(rawFilename)) {
                        requireBillingFile(rawFilename, PathValidationUtils.validatePath(rawFilename, downloadDir).toPath());
                    }
                    try (var reviewed = reviewedBillingFile(filename,
                            PathValidationUtils.validatePath(filename, downloadDir).toPath())) {
                        if (reviewed != null) {
                            String headerFilename = filename.replaceAll("[\\r\\n\\p{Cntrl}]", "");
                            res.setContentType("application/octet-stream");
                            res.setHeader("X-Content-Type-Options", "nosniff");
                            res.setHeader("Content-Disposition", "attachment;filename=\"" + headerFilename + "\"");
                            res.setContentLengthLong(reviewed.size());
                            reviewed.input().transferTo(res.getOutputStream()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- verified binary response
                            return;
                        }
                    }
                }
                ServletOutputStream stream = res.getOutputStream();
                transferFile(res, stream, backupfilepath, filename);
            } else {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "You have no right to download the file(s).");
            }
        } catch (BillingFileWriteException e) {
            sendErrorForCaughtException(res, HttpServletResponse.SC_CONFLICT, e.reason().publicMessage());
        } catch (FileValidationException e) {
            log.warn("OscarDownload rejected invalid filename from {}", LogSafe.sanitize(req.getRemoteAddr()));
            sendErrorForCaughtException(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid filename parameter.");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in OscarDownload", e);
            if (!res.isCommitted()) {
                res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    protected void requireBillingFile(String filename, java.nio.file.Path path) {
        BillingFilenameImportDao dao = SpringUtils.getBean(BillingFilenameImportDao.class);
        BillingImportedFiles.requireResolved(dao.findFileReviews(filename), filename, path);
    }

    /** Opens the verified snapshot used for both validation and delivery of an audited file. */
    protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String filename, java.nio.file.Path path) {
        BillingFilenameImportDao dao = SpringUtils.getBean(BillingFilenameImportDao.class);
        return BillingImportedFiles.openReviewed(dao.findFileReviews(filename), filename, path);
    }

    private void sendErrorForCaughtException(HttpServletResponse res, int statusCode, String message) {
        if (res.isCommitted()) {
            return;
        }
        try {
            res.sendError(statusCode, message);
        } catch (IOException e) {
            log.warn("Unable to send OscarDownload error response (status: {}, message: {})", statusCode, message, e);
        }
    }
}
