/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Servlet that provides localhost-only access to eForm images for PDF generation.
 * Forwards requests to the standard {@code displayImage.do} action with a
 * {@code prepareForFax} flag set. Restricted to requests from {@code 127.0.0.1}.
 *
 * @see EFormViewForPdfGenerationServlet
 * @see io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action
 * @since 2008-01-01
 */
public final class EFormImageViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Validates localhost origin and forwards to the eForm image display action.
     *
     * @param request HttpServletRequest containing the {@code imagefile} parameter
     * @param response HttpServletResponse for the forwarded image content
     * @throws ServletException if forwarding fails
     * @throws IOException if an I/O error occurs
     */
    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EformPdfServlet request from : " + remoteAddress);

        if (!"127.0.0.1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormImageViewForPdfGenerationServlet from address : " + remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }

        request.setAttribute("prepareForFax", true);
        RequestDispatcher requestDispatcher = request.getRequestDispatcher("/eform/displayImage.do");
        requestDispatcher.forward(request, response);
    }
}
