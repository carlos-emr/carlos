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
import java.io.PrintWriter;
import java.util.Set;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * @author Jay Gallagher
 */
public class GenericDownload extends HttpServlet {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Allowlist of CarlosProperties keys that are permitted as download directory roots.
     * Adding a key here allows clients to use it as a dir_property parameter.
     * Do not add keys that point to sensitive system directories.
     */
    private static final Set<String> ALLOWED_DIR_PROPERTIES = Set.of(
        "oscar_document_dir",
        "DOCUMENT_DIR",
        "BASE_DOCUMENT_DIR",
        "eform_image_dir",
        "fax_document_dir"
    );

    public GenericDownload() {
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            HttpSession session = req.getSession(false);

            CarlosProperties oscarProps = CarlosProperties.getInstance();

            String filename = req.getParameter("filename");
            String dir_property = req.getParameter("dir_property");
            String contentType = req.getParameter("contentType");
            String user = session != null ? (String) session.getAttribute("user") : null;

            // Validate dir_property against allowlist to prevent arbitrary filesystem root selection
            String dir = null;
            if (dir_property != null && ALLOWED_DIR_PROPERTIES.contains(dir_property)) {
                dir = oscarProps.getProperty(dir_property);
            }

            boolean bDo = false;
            if (filename != null && dir != null && user != null) {
                bDo = true;
            }
            download(bDo, res, dir, filename, contentType);
        } catch (Exception e) {
            logger.error("Error processing download request for {}", req.getRequestURI(), e);
            if (!res.isCommitted()) {
                try {
                    res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An error occurred processing your request.");
                } catch (IOException ioe) {
                    logger.error("Failed to send error response", ioe);
                }
            }
        }
    }

    public void download(boolean bDownload, HttpServletResponse res, String dir, String filename, String contentType)
            throws IOException {
        if (bDownload) {
            ServletOutputStream stream = res.getOutputStream();
            transferFile(res, stream, dir, filename, contentType);
            stream.close();
        } else {
            res.setContentType("text/html");
            PrintWriter out = res.getWriter();
            out.println("<html>");
            out.println("<head><body>You have no right to download the file(s).");
            out.println("</body>");
            out.println("</html>");
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
        
        FileInputStream fis = new FileInputStream(curfile);
        int bufferSize;
        byte[] buffer = new byte[BUFFER_SIZE];

        while ((bufferSize = fis.read(buffer)) != -1) {
            stream.write(buffer, 0, bufferSize);

        }
        fis.close();
        stream.flush();
        stream.close();
    }
}
