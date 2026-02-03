/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.BufferedOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
