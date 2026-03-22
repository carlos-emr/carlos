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


package io.github.carlos_emr.carlos.web.eform;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Servlet that allows a local process to convert an HTML eForm page into a PDF file,
 * similar to viewing a page in a browser and selecting "print to file".
 *
 * <p>Restricted to localhost access only for security. Forwards the request to the
 * eForm data display JSP, whose rendered HTML output can then be captured for PDF conversion.
 *
 * @since 2012-08-13
 */
public final class EformViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Handles GET requests by forwarding to the eForm data display JSP for PDF generation.
     *
     * <p>Only accepts requests from the local machine (127.0.0.1). All remote requests
     * receive a 403 Forbidden response.
     *
     * @param request HttpServletRequest the incoming request, which must originate from localhost
     * @param response HttpServletResponse the response to write to
     * @throws ServletException if the forwarded JSP encounters an error
     * @throws IOException if an I/O error occurs during forwarding or response writing
     */
    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EformPdfServlet request from : " + remoteAddress);
        if (!"127.0.0.1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EformPdfServlet from address : " + remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // https://127.0.0.1:8443/oscar/eform/efmshowform_data.jsp?fdid=2&parentAjaxId=eforms
        RequestDispatcher requestDispatcher = request.getRequestDispatcher("/eform/efmshowform_data.jsp");
        requestDispatcher.forward(request, response);
    }
}
