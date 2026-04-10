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
 * The purpose of this servlet is to allow a local process to access eform signatures.
 */
public final class EFormSignatureViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EFormSignatureViewForPdfGenerationServlet request from : {}", remoteAddress);

        if (!"127.0.0.1".equals(remoteAddress) && !"0:0:0:0:0:0:0:1".equals(remoteAddress) && !"::1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormSignatureViewForPdfGenerationServlet from address : {}", remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }


        try {
            // get signature image by digitalSignatureId
            String signatureIdParam = request.getParameter("digitalSignatureId");
            if (signatureIdParam == null || !signatureIdParam.matches("\\d+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid digitalSignatureId");
                return;
            }
			DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
			DigitalSignature digitalSignature = digitalSignatureManager
					.getDigitalSignature(Integer.parseInt(signatureIdParam));
            if (digitalSignature != null) {
                //renderImage(response, digitalSignature.getSignatureImage(), "jpeg");

                byte[] image = digitalSignature.getSignatureImage();
                if (image == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Signature image data is missing");
                    return;
                }
                String imageType = "jpeg";
                response.setContentType("image/" + imageType);
                response.setContentLength(image.length);
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream());
                bos.write(image);
                bos.flush();

                return;
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in EFormSignatureViewForPdfGenerationServlet", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }
}
