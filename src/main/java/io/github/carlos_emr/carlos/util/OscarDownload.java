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

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet for downloading files based on a filename and home path stored in the HTTP session.
 * Extends {@link GenericDownload} to handle the actual file transfer. The file directory
 * is resolved from a session attribute identified by the {@code homepath} request parameter.
 *
 * @since 2001-01-01
 */
public class OscarDownload extends GenericDownload {

    /**
     * Handles GET requests by resolving the file path from session attributes and
     * transferring the file to the response output stream.
     *
     * @param req HttpServletRequest containing {@code filename} and {@code homepath} parameters
     * @param res HttpServletResponse the response to write the file to
     * @throws IOException if a file transfer error occurs
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(true);
        String filename = req.getParameter("filename") != null ? req.getParameter("filename") : "null";
        String homepath = req.getParameter("homepath") != null ? req.getParameter("homepath") : "null";

        String backupfilepath = ((String) session.getAttribute(homepath)) != null ? ((String) session.getAttribute(homepath)) : "null";
        if (filename != null && backupfilepath != null && ((String) session.getAttribute("user")) != null) {
            ServletOutputStream stream = res.getOutputStream();
            transferFile(res, stream, backupfilepath, filename);
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
}
