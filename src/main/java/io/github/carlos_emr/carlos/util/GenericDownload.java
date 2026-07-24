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
import jakarta.servlet.http.HttpSession;

import java.util.Set;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author Jay Gallagher
 */
public class GenericDownload extends HttpServlet {

    private static final Logger log = MiscUtils.getLogger();

    /**
     * Configured-directory property names this servlet is permitted to serve from. The caller names
     * the directory via the {@code dir_property} request parameter; allowing an arbitrary property
     * lets any authenticated request pick which configured directory is trusted (exports, backups,
     * integration artifacts). No caller in the codebase uses this servlet, so the allowlist is
     * intentionally empty — add a property key here deliberately, with its use case and an
     * appropriate privilege, only when a real caller needs it.
     *
     * <p>While this set is empty the {@code /Download} mapping (web.xml) is effectively
     * disabled-by-default and fail-closed: every {@code dir_property} request is rejected and no file
     * is served, including for admins. This is intentional — the endpoint stays inert until a real
     * caller and its property key are added here.</p>
     */
    private static final Set<String> ALLOWED_DIR_PROPERTIES = Set.of();

    public GenericDownload() {
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            HttpSession session = req.getSession(true);

            CarlosProperties oscarProps = CarlosProperties.getInstance();

            String filename = req.getParameter("filename");
            String dir_property = req.getParameter("dir_property");
            String user = (String) session.getAttribute("user");

            // Authorization: a non-null session user is NOT sufficient to read arbitrary configured
            // directories. Require an administrator, and restrict dir_property to the server-side
            // allowlist. The caller-supplied contentType is ignored — content type is forced to
            // application/octet-stream (in transferFile) to avoid MIME-sniffing / type confusion.
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
            SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
            boolean authorized = user != null
                    && loggedInInfo != null
                    && dir_property != null
                    && ALLOWED_DIR_PROPERTIES.contains(dir_property)
                    && securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null);

            String dir = authorized ? oscarProps.getProperty(dir_property) : null;

            boolean bDo = authorized && filename != null && dir != null;
            download(bDo, res, dir, filename, null);
        } catch (IOException e) {
            throw e;
        } catch (SecurityException e) {
            log.warn("SecurityException in GenericDownload: {}", e.getMessage());
            if (!res.isCommitted()) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            }
        } catch (Exception e) {
            log.error("Unexpected error in GenericDownload", e);
            if (!res.isCommitted()) {
                res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
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
        
        FileInputStream fis = new FileInputStream(curfile);
        int bufferSize;
        byte[] buffer = new byte[BUFFER_SIZE];

        while ((bufferSize = fis.read(buffer)) != -1) {
            stream.write(buffer, 0, bufferSize); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary file download buffer copy

        }
        fis.close();
        stream.flush();
        stream.close();
    }
}
