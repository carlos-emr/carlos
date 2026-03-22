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

import java.io.BufferedOutputStream;
import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Servlet that provides localhost-only access to eForm digital signatures for
 * PDF generation. Retrieves the digital signature image from the database and
 * streams it as JPEG content. Restricted to requests from {@code 127.0.0.1}.
 *
 * @see EFormViewForPdfGenerationServlet
 * @see io.github.carlos_emr.carlos.managers.DigitalSignatureManager
 * @since 2008-01-01
 */
public final class EFormSignatureViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Validates localhost origin, retrieves the digital signature by ID, and
     * streams the signature image as JPEG to the response.
     *
     * @param request HttpServletRequest containing the {@code digitalSignatureId} parameter
     * @param response HttpServletResponse to write the signature image to
     * @throws ServletException if a servlet error occurs
     * @throws IOException if writing to the response fails
     */
    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EformPdfServlet request from : " + remoteAddress);

        if (!"127.0.0.1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormSignatureViewForPdfGenerationServlet from address : " + remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }


        // https://127.0.0.1:8443/oscar/eform/efmshowform_data.jsp?fdid=2&parentAjaxId=eforms
        try {
            // get image
			DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
			DigitalSignature digitalSignature = digitalSignatureManager
					.getDigitalSignature(Integer.parseInt(request.getParameter("digitalSignatureId")));
            if (digitalSignature != null) {
                //renderImage(response, digitalSignature.getSignatureImage(), "jpeg");

                byte[] image = digitalSignature.getSignatureImage();
                String imageType = "jpeg";
                response.setContentType("image/" + imageType);
                if (image != null)
                    response.setContentLength(image.length);
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream());
                bos.write(image);
                bos.flush();

                return;
            }
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
        }
    }
}
