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

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

public class OscarDownload extends GenericDownload {
    private static final Logger log = MiscUtils.getLogger();
    private static final String INTERNAL_ERROR_MESSAGE =
            "An internal error occurred. Please try again or contact your system administrator.";

    private static final Set<String> ALLOWED_HOMEPATH_KEYS = Set.of(
            "homepath", "ohipdownload", "obecdownload"
    );

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            HttpSession session = req.getSession(true);
            String rawFilename = req.getParameter("filename");
            String homepath = req.getParameter("homepath");

            if (rawFilename == null || rawFilename.isBlank()) {
                sendErrorIfPossible(res, HttpServletResponse.SC_BAD_REQUEST, "Missing required filename parameter.");
                return;
            }
            if (homepath == null || !ALLOWED_HOMEPATH_KEYS.contains(homepath)) {
                log.warn("OscarDownload rejected invalid homepath key from {}", req.getRemoteAddr());
                sendErrorIfPossible(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid download path key.");
                return;
            }

            String backupfilepath = (String) session.getAttribute(homepath); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): homepath is user-supplied but allowlist-validated against ALLOWED_HOMEPATH_KEYS before use as session lookup key; session value is server-side data
            if (backupfilepath != null
                    && !backupfilepath.isEmpty()
                    && ((String) session.getAttribute("user")) != null) {
                File downloadDir = new File(backupfilepath).getCanonicalFile();
                if (!downloadDir.isDirectory()) {
                    log.warn("OscarDownload rejected non-directory path from {}", req.getRemoteAddr());
                    sendErrorIfPossible(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid download directory.");
                    return;
                }
                String filename = PathValidationUtils.validateUserFilePath(rawFilename, downloadDir).getName();
                try (ServletOutputStream stream = res.getOutputStream()) {
                    transferFile(res, stream, backupfilepath, filename);
                }
            } else {
                sendErrorIfPossible(res, HttpServletResponse.SC_FORBIDDEN, "You have no right to download the file(s).");
            }
        } catch (IOException e) {
            throw e;
        } catch (FileValidationException e) {
            log.warn("OscarDownload rejected invalid filename from {}", req.getRemoteAddr());
            sendErrorIfPossible(res, HttpServletResponse.SC_BAD_REQUEST, "Invalid filename parameter.");
        } catch (Exception e) {
            log.error("Unexpected error in OscarDownload", e);
            sendErrorIfPossible(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
        }
    }
}
