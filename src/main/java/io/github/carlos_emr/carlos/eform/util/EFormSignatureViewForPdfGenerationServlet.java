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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The purpose of this servlet is to allow a local process to access eform signatures.
 */
public final class EFormSignatureViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    /** Matches a {@code digitalSignatureId=<n>} reference in a stored eForm value's query string. */
    private static final Pattern DIGITAL_SIGNATURE_ID_REFERENCE = Pattern.compile("[?&]digitalSignatureId=(\\d+)");

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

        // Digital signatures are PHI. The only live consumer is the server-side PDF renderer, whose
        // sessionless browser fetches signature images over loopback under a render-scoped grant
        // (minted only after an _eform privilege check, invalidated when the render finishes). Require
        // that grant so this loopback endpoint is no longer a bare, always-open enumeration surface
        // for any local process. The grant rides the signature URL the render servlet emits.
        String token = request.getParameter(EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM);
        EFormRenderTokenService.RenderGrant grant = EFormRenderTokenService.getInstance().peek(token);
        if (grant == null) {
            logger.warn("Rejected EFormSignatureViewForPdfGenerationServlet request lacking a valid render grant");
            // Handle the sendError IOException locally so it never escapes the servlet method.
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (IOException ioException) {
                logger.debug("Unable to send unauthorized response for EFormSignatureViewForPdfGenerationServlet", ioException);
            }
            return;
        }


        try {
            // get signature image by digitalSignatureId
            String signatureIdParam = request.getParameter("digitalSignatureId");
            if (signatureIdParam == null || !signatureIdParam.matches("\\d+")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid digitalSignatureId");
                return;
            }
            int digitalSignatureId = Integer.parseInt(signatureIdParam);
            // Bind the fetch to the render's own eForm: a grant is minted for one fdid, so it may only
            // retrieve a signature that eForm actually references. Without this, a valid render token
            // (or any local process holding one) could enumerate arbitrary signature ids and pull an
            // unrelated patient's signature image into the generated PDF.
            if (!isSignatureReferencedByEform(grant.fdid(), signatureIdParam)) {
                logger.warn("Rejected signature fetch for a digitalSignatureId not referenced by the render's eForm");
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
			DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
			DigitalSignature digitalSignature = digitalSignatureManager
					.getDigitalSignature(digitalSignatureId);
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
                bos.write(image); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- image/jpeg binary write
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

    /**
     * Returns true when the saved eForm identified by {@code fdid} references {@code signatureId} in
     * any of its stored values. This binds the render-scoped grant to the eForm it was minted for:
     * the renderer only ever embeds signatures the form itself declares (in its {@code signatureValue}
     * or letter content), so authorizing exactly that set prevents a crafted form from fetching an
     * unrelated signature. The comparison is on the numeric string so an unbounded stored id cannot
     * throw while parsing.
     */
    static boolean isSignatureReferencedByEform(int fdid, String signatureId) {
        EFormValueDao eFormValueDao = SpringUtils.getBean(EFormValueDao.class);
        List<EFormValue> storedValues = eFormValueDao.findByFormDataId(fdid);
        if (storedValues == null) {
            // Fail closed: an absent value set cannot reference the requested signature, so a null
            // return denies the fetch rather than throwing an NPE that would surface as a 500.
            return false;
        }
        for (EFormValue value : storedValues) {
            String stored = value.getVarValue();
            if (stored == null) {
                continue;
            }
            Matcher matcher = DIGITAL_SIGNATURE_ID_REFERENCE.matcher(stored);
            while (matcher.find()) {
                if (signatureId.equals(matcher.group(1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
