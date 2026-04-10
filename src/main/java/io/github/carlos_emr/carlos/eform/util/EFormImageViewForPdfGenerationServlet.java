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
 * The purpose of this servlet is to allow a local process to access eform images.
 */
public final class EFormImageViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EformPdfServlet request from : {}", remoteAddress);

        if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormImageViewForPdfGenerationServlet from address : {}", remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        try {
            request.setAttribute("prepareForFax", true);
            RequestDispatcher requestDispatcher = request.getRequestDispatcher("/eform/displayImage.do");
            requestDispatcher.forward(request, response);
        } catch (ServletException | IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in EFormImageViewForPdfGenerationServlet", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }
}
