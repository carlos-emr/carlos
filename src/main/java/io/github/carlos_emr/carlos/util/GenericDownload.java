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

    public GenericDownload() {
    }

    /**
     * The direct /Download endpoint is intentionally disabled. Concrete download
     * servlets must choose their server-side root explicitly before calling
     * {@link #transferFile(HttpServletResponse, ServletOutputStream, String, String)}.
     */
    @Deprecated(since = "2026-05-20", forRemoval = true)
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        log.warn("Rejected direct GenericDownload request from {}", req.getRemoteAddr());
        sendErrorIfPossible(res, HttpServletResponse.SC_GONE, "Direct download endpoint is no longer available.");

    }

    private void sendErrorIfPossible(HttpServletResponse res, int statusCode, String message) throws IOException {
        if (!res.isCommitted()) {
            res.sendError(statusCode, message);
        }
    }

    public void download(boolean bDownload, HttpServletResponse res, String dir, String filename, String contentType)
            throws IOException {
        if (bDownload) {
            ServletOutputStream stream = res.getOutputStream();
            transferFile(res, stream, dir, filename, contentType);
            stream.close();
        } else {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "You have no right to download the file(s).");
        }
    }

    protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename) throws IOException {
        transferFile(res, stream, dir, filename, null);
    }

    protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename,
                                String contentType) throws IOException {
        //faster than "transferFile" method - clocked at 1.1MB/s on a 10Mbps switch
        int BUFFER_SIZE = 2048;
        String setContentType = "application/octet-stream";
        if (contentType != null) {
            setContentType = contentType;
        }

        // Use PathValidationUtils for security validation
        // This sanitizes the filename and validates directory containment
        File directory = new File(dir).getCanonicalFile();
        File curfile = PathValidationUtils.validatePath(filename, directory);

        // Sanitize filename for HTTP header (prevent response splitting)
        String sanitizedFilename = curfile.getName().replaceAll("[\r\n]", "").replaceAll("[\\p{Cntrl}]", "");

        res.setContentType(setContentType);
        res.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizedFilename + "\"");
        
        int bufferSize;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (FileInputStream fis = new FileInputStream(curfile)) {
            while ((bufferSize = fis.read(buffer)) != -1) {
                stream.write(buffer, 0, bufferSize); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary file download buffer copy

            }
        }
        stream.flush();
        stream.close();
    }
}
