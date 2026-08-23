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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author Jay Gallagher
 */
public class GenericDownload extends HttpServlet {

    private static final long serialVersionUID = 1L;
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
            HttpSession session = req.getSession(false);
            if (session == null) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                return;
            }

            CarlosProperties oscarProps = CarlosProperties.getInstance();

            String filename = req.getParameter("filename");
            String dir_property = req.getParameter("dir_property");
            String user = (String) session.getAttribute("user");

            // Generic downloads require an administrator and a server-approved directory property.
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
            SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
            boolean authorized = user != null
                    && loggedInInfo != null
                    && dir_property != null
                    && ALLOWED_DIR_PROPERTIES.contains(dir_property)
                    && securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null);

            String dir = authorized ? oscarProps.getProperty(dir_property) : null;

            boolean bDo = authorized && filename != null && dir != null;
            download(bDo, res, dir, filename);
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

    public void download(boolean bDownload, HttpServletResponse res, String dir, String filename)
            throws IOException {
        if (bDownload) {
            ServletOutputStream stream = res.getOutputStream();
            transferFile(res, stream, dir, filename);
        } else {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "You have no right to download the file(s).");
        }
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "PathValidationUtils validates filename containment under the canonical configured directory before opening the file")
    protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename) throws IOException {
        int bufferSizeBytes = 8192;

        // Use PathValidationUtils for security validation
        // This sanitizes the filename and validates directory containment
        File directory = new File(dir).getCanonicalFile();
        File curfile = PathValidationUtils.validatePath(filename, directory);

        // Sanitize filename for HTTP header (prevent response splitting)
        String sanitizedFilename = curfile.getName().replaceAll("[\r\n]", "").replaceAll("[\\p{Cntrl}]", "");

        res.setContentType("application/octet-stream");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Disposition", "attachment;filename=\"" + sanitizedFilename + "\"");
        res.setContentLengthLong(curfile.length());

        byte[] buffer = new byte[bufferSizeBytes];
        try (FileInputStream input = new FileInputStream(curfile)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                stream.write(buffer, 0, bytesRead); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- binary response
            }
        }
    }
}
