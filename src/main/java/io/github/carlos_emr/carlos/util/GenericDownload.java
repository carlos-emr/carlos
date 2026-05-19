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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;


import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author Jay Gallagher
 */
public class GenericDownload extends HttpServlet {

    private static final Logger log = MiscUtils.getLogger();
    private static final String DEFAULT_DOWNLOAD_FILENAME = "download";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public GenericDownload() {
    }

    /**
     * Rejects direct requests to the legacy generic download servlet. Concrete
     * download servlets must perform their own authorization and call
     * {@link #download(boolean, HttpServletResponse, String, String)}.
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        log.warn("Rejected direct GenericDownload request from {}", req.getRemoteAddr());
        sendErrorIfPossible(res, HttpServletResponse.SC_GONE, "This download endpoint is no longer available.");
    }

    public void download(boolean bDownload, HttpServletResponse res, String dir, String filename)
            throws IOException {
        if (bDownload) {
            try (ServletOutputStream stream = res.getOutputStream()) {
                transferFile(res, stream, dir, filename);
            }
        } else {
            sendErrorIfPossible(res, HttpServletResponse.SC_FORBIDDEN, "You have no right to download the file(s).");
        }
    }

    protected static void sendErrorIfPossible(HttpServletResponse res, int status, String message) {
        try {
            if (!res.isCommitted()) {
                res.sendError(status, message);
            }
        } catch (IOException e) {
            log.error("Could not send error response", e);
        }
    }

    protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename) throws IOException {
        //faster than "transferFile" method - clocked at 1.1MB/s on a 10Mbps switch
        int BUFFER_SIZE = 2048;

        // Use PathValidationUtils for security validation
        // This sanitizes the filename and validates directory containment
        File directory = new File(dir).getCanonicalFile();
        File curfile = PathValidationUtils.validatePath(filename, directory);

        String sanitizedFilename = sanitizeAttachmentFilename(curfile.getName());

        res.setContentType(resolveContentType(curfile));
        res.setHeader("X-Content-Type-Options", "nosniff");
        // RFC 6266 / RFC 5987: the filename= token is ISO-8859-1; non-ASCII names
        // require filename*=UTF-8''<percent-encoded> for broad browser compatibility.
        // Filenames stored in CARLOS pass through PathValidationUtils normalisation
        // which strips non-ASCII, so this form is safe for current usage.
        res.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizedFilename + "\"");

        int bufferSize;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (FileInputStream fis = new FileInputStream(curfile)) {
            while ((bufferSize = fis.read(buffer)) != -1) {
                stream.write(buffer, 0, bufferSize); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary file download buffer copy
            }
        }
        stream.flush();
    }

    private static String resolveContentType(File file) {
        String knownType = resolveKnownContentType(file.getName());
        if (knownType != null) {
            return knownType;
        }

        try {
            String detectedType = Files.probeContentType(file.toPath());
            if (detectedType != null && !detectedType.isBlank()) {
                return detectedType;
            }
        } catch (IOException e) {
            log.debug("Could not detect content type for {}", file.getName(), e);
        }
        return DEFAULT_CONTENT_TYPE;
    }

    private static String resolveKnownContentType(String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "text/html";
        }
        if (lowerName.endsWith(".txt")) {
            return "text/plain";
        }
        if (lowerName.endsWith(".csv")) {
            return "text/csv";
        }
        if (lowerName.endsWith(".xml")) {
            return "application/xml";
        }
        if (lowerName.endsWith(".zip")) {
            return "application/zip";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }
        return null;
    }

    private static String sanitizeAttachmentFilename(String filename) {
        if (filename == null) {
            return DEFAULT_DOWNLOAD_FILENAME;
        }

        String sanitized = filename
                .replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "")
                .replaceAll("[\"\\\\;]", "_");

        if (sanitized.trim().isEmpty()) {
            return DEFAULT_DOWNLOAD_FILENAME;
        }
        return sanitized;
    }
}
