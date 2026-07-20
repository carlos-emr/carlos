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
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.EFormData;
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
    // Accept the reference as a first query parameter ("?digitalSignatureId="), a raw subsequent
    // parameter ("&digitalSignatureId="), or its HTML-escaped form ("&amp;digitalSignatureId=").
    // The "amp;" is scoped to the "&" branch only, so a malformed "?amp;digitalSignatureId=" (neither
    // a raw parameter nor a valid escape) is NOT accepted (cubic HOdU/SIt57).
    private static final Pattern DIGITAL_SIGNATURE_ID_REFERENCE = Pattern.compile("(?:\\?|&(?:amp;)?)digitalSignatureId=(\\d+)");

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
                // Patient-scope check on top of the form-reference binding: a signature bound to a concrete
                // demographic may only be embedded in a render for that same demographic. Without this, a
                // crafted eForm that *references* another patient's digitalSignatureId (which passes
                // isSignatureReferencedByEform) could pull that patient's signature image (PHI) into the PDF.
                if (!signatureBelongsToRenderDemographic(grant.fdid(), digitalSignature)) {
                    logger.warn("Rejected signature fetch whose demographic does not match the render's eForm");
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                //renderImage(response, digitalSignature.getSignatureImage(), "jpeg");

                byte[] image = digitalSignature.getSignatureImage();
                if (image == null) {
                    // Referenced signature row exists but carries no image bytes. No id in the message
                    // (signatures are PHI); a blank signature in a PDF is now traceable to this branch.
                    logger.debug("eForm signature fetch: referenced signature has no image data (404)");
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Signature image data is missing");
                    return;
                }
                String imageType = "jpeg";
                response.setContentType("image/" + imageType);
                response.setContentLength(image.length);
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream());
                bos.write(image); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- image/jpeg binary write
                bos.flush();

                logger.debug("Streamed eForm signature image to render browser ({} bytes)", image.length);
                return;
            }
            // The id is referenced by the render's eForm but no signature row exists for it (e.g. the
            // signature was deleted). Fail deterministically with 404 rather than falling through to an
            // empty 200, so the renderer sees a clear missing-resource result and logs are unambiguous
            // (copilot SJD9p).
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Signature not found");
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
    /**
     * Returns true when {@code signature} may be embedded in the render identified by {@code fdid}:
     * either the signature is not patient-bound (null/0 demographic — a provider-scoped signature that
     * carries no patient linkage) or its demographic matches the render's eForm demographic. This adds a
     * patient-scope check on top of {@link #isSignatureReferencedByEform}: the reference binding alone
     * confirms the crafted form *mentions* the id, but not that the underlying signature row belongs to the
     * same patient. Fails closed (returns false) when the render's eForm cannot be resolved.
     */
    static boolean signatureBelongsToRenderDemographic(int fdid, DigitalSignature signature) {
        Integer signatureDemographic = signature.getDemographicId();
        if (signatureDemographic == null || signatureDemographic == 0) {
            // Not bound to a patient (e.g. a provider signature); the form-reference binding is the boundary.
            return true;
        }
        EFormDataDao eFormDataDao = SpringUtils.getBean(EFormDataDao.class);
        EFormData eFormData = eFormDataDao.findByFormDataId(fdid);
        if (eFormData == null) {
            return false;
        }
        return signatureDemographic.equals(eFormData.getDemographicId());
    }

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
