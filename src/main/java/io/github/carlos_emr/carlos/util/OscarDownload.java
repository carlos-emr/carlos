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
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingImportedFiles;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnFileAccessService;
import io.github.carlos_emr.carlos.report.pageUtil.Obec2Action;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.BoundedSessionAttributes;
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

    private static final Set<String> BC_BILLING_REGIONS = Set.of("BC", "Bc", "bC", "bc");

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

                File downloadDir;
                try {
                    downloadDir = new File(backupfilepath).getCanonicalFile(); // nosemgrep: semgrep.carlos.httpservlet-path-traversal -- Directory comes from an authenticated server-side session under one of three allowlisted keys; client filenames are strictly validated and contained separately below.
                } catch (IOException unavailable) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Download file not found.");
                    return;
                }
                if (!downloadDir.isDirectory()) {
                    if (!downloadDir.exists()) {
                        DownloadStorageWarnings.reportIfUnavailable(downloadDir);
                        res.sendError(HttpServletResponse.SC_NOT_FOUND, "Download file not found.");
                        return;
                    }
                    log.warn("OscarDownload rejected non-directory path from {}", LogSafe.sanitize(req.getRemoteAddr()));
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid download directory.");
                    return;
                }
                String billingHome = CarlosProperties.getInstance().getProperty("HOME_DIR");
                boolean billingDirectory;
                try {
                    billingDirectory = billingHome != null && !billingHome.isBlank()
                            && downloadDir.equals(new File(billingHome).getCanonicalFile());
                } catch (IOException unavailable) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Download file not found.");
                    return;
                }
                if ("ohipdownload".equals(homepath) || billingDirectory) {
                    // Only the BC Teleplan route shares these download keys. An absent or malformed
                    // province must not disable the Ontario ownership check on its generated files.
                    String region = CarlosProperties.getInstance().getProperty("billregion", "");
                    if (!BC_BILLING_REGIONS.contains(StringUtils.trimToEmpty(region))) {
                        boolean generatedObec = "obecdownload".equals(homepath) && filename.equals(rawFilename)
                                && BoundedSessionAttributes.containsKey(session, Obec2Action.DOWNLOAD_CATEGORY,
                                        Obec2Action.DOWNLOAD_PREFIX + filename);
                        BillingOnFileAccessService access = SpringUtils.getBean(BillingOnFileAccessService.class);
                        LoggedInInfo login = LoggedInInfo.getLoggedInInfoFromSession(session);
                        var permission = access.downloadAccess(login, filename, generatedObec);
                        if (permission == BillingOnFileAccessService.DownloadAccess.VERIFY_OWNERS) {
                            serveScopedDownload(res, access, login, downloadDir, filename, rawFilename);
                            return;
                        }
                        if (permission != BillingOnFileAccessService.DownloadAccess.GRANTED) {
                            res.sendError(HttpServletResponse.SC_FORBIDDEN);
                            return;
                        }
                    }
                    if (!filename.equals(rawFilename)) {
                        requireBillingFile(rawFilename, PathValidationUtils.validatePath(rawFilename, downloadDir).toPath());
                    }
                    try (var reviewed = reviewedBillingFile(filename,
                            PathValidationUtils.validatePath(filename, downloadDir).toPath())) {
                        if (reviewed != null) {
                            sendOpenedFile(res, filename, reviewed.input(), reviewed.size());
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

    private record ScopedBillingFile(FileChannel input, List<FileReview> reviews) implements Closeable {
        private static final ScopedBillingFile DENIED = new ScopedBillingFile(null, List.of());

        @Override public void close() throws IOException {
            if (input != null) input.close();
        }
    }

    private void serveScopedDownload(HttpServletResponse res, BillingOnFileAccessService access, LoggedInInfo login,
                                     File directory, String filename, String rawFilename) throws IOException {
        Path path = PathValidationUtils.validatePath(filename, directory).toPath();
        try (var file = access.openScopedDownload(() -> {
            if (!access.canDownload(login, filename, false)
                    || (!filename.equals(rawFilename) && !access.canDownload(login, rawFilename, false))) {
                return ScopedBillingFile.DENIED;
            }
            // A reconciled file is pinned by its recorded digest. Its private snapshot can be made after releasing the guard.
            var reviews = billingFileReviews(filename);
            if (!reviews.isEmpty()) return new ScopedBillingFile(null, reviews);
            try {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new FileNotFoundException();
                return new ScopedBillingFile(FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), List.of());
            } catch (IOException unavailable) {
                throw new UncheckedIOException(unavailable);
            }
        })) {
            if (file == ScopedBillingFile.DENIED) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            if (!filename.equals(rawFilename)) {
                requireBillingFile(rawFilename, PathValidationUtils.validatePath(rawFilename, directory).toPath());
            }
            if (file.input() != null) {
                sendOpenedFile(res, filename, Channels.newInputStream(file.input()), file.input().size());
            } else {
                try (var reviewed = BillingImportedFiles.openReviewed(file.reviews(), filename, path)) {
                    sendOpenedFile(res, filename, reviewed.input(), reviewed.size());
                }
            }
        } catch (UncheckedIOException missing) {
            DownloadStorageWarnings.reportIfUnavailable(directory);
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Download file not found.");
        }
    }

    private static void sendOpenedFile(HttpServletResponse res, String filename, InputStream input, long size) throws IOException {
        String headerFilename = filename.replaceAll("[\\r\\n\\p{Cntrl}]", "");
        res.setContentType("application/octet-stream");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Disposition", "attachment;filename=\"" + headerFilename + "\"");
        res.setContentLengthLong(size);
        input.transferTo(res.getOutputStream()); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- validated binary response
    }

    private List<FileReview> billingFileReviews(String filename) {
        return SpringUtils.getBean(BillingFilenameImportDao.class).findFileReviews(filename);
    }

    protected void requireBillingFile(String filename, java.nio.file.Path path) {
        BillingImportedFiles.requireResolved(billingFileReviews(filename), filename, path);
    }

    /** Opens the verified snapshot used for both validation and delivery of an audited file. */
    protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String filename, java.nio.file.Path path) {
        return BillingImportedFiles.openReviewed(billingFileReviews(filename), filename, path);
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
