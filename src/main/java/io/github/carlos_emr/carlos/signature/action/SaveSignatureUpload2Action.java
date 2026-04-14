/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.signature.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.codec.binary.Base64;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * POST-only endpoint that receives a Base64-encoded signature image (IPAD
 * branch) or raw-stream upload, writes it to a temp file, and optionally
 * persists the signature record to the database via
 * {@link DigitalSignatureManager#processAndSaveDigitalSignature}.
 *
 * <p>Replaces the scriptlet in the former
 * {@code /signature_pad/uploadSignature.jsp}. Preserves the JSP's
 * HTML-fragment response contract — writes
 * {@code <input type="hidden" name="signatureId" value="…"/>} so existing
 * iframe-scraping JS callers continue to work without modification.
 *
 * <p>Enforces POST + {@code _con w}. Path-traversal is defended via
 * {@link PathValidationUtils#validateExistingPath}, matching the original.
 */
public final class SaveSignatureUpload2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null
                || !securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null)) {
            throw new SecurityException("missing required sec object (_con w)");
        }

        String signatureId = "";
        String uploadSource = request.getParameter("source");
        String imageString = request.getParameter("signatureImage");
        String demographic = request.getParameter("demographicNo");
        boolean saveToDB = "true".equals(request.getParameter("saveToDB"));
        String signatureKey = request.getParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY);
        ModuleType moduleType = ModuleType.getByName(request.getParameter(ModuleType.class.getSimpleName()));

        // Reject signatureKey values containing anything other than alphanumeric characters
        // to prevent path traversal (e.g. "../" sequences) from escaping the temp directory.
        if (signatureKey != null && !signatureKey.matches("[a-zA-Z0-9]+")) {
            MiscUtils.getLogger().warn("Invalid signatureKey rejected: {}", LogSanitizer.sanitize(signatureKey));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid signature key");
            return NONE;
        }

        if (signatureKey != null) {
            String filename = DigitalSignatureUtils.getTempFilePath(signatureKey);

            // Defence-in-depth: confirm the resolved path is within the temp directory
            try {
                File tmpDir = new File(System.getProperty("java.io.tmpdir"));
                PathValidationUtils.validateExistingPath(new File(filename), tmpDir);
            } catch (SecurityException e) {
                MiscUtils.getLogger().warn("Path traversal attempt blocked for signatureKey: {}", LogSanitizer.sanitize(signatureKey));
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid signature key");
                return NONE;
            }

            if ("IPAD".equalsIgnoreCase(uploadSource) && imageString != null && !imageString.isEmpty()) {
                try (FileOutputStream fos = new FileOutputStream(filename)) {
                    imageString = imageString.substring(imageString.indexOf(",") + 1);
                    Base64 b64 = new Base64();
                    byte[] imageByteData = imageString.getBytes();
                    byte[] imageData = b64.decode(imageByteData);
                    if (imageData != null) {
                        fos.write(imageData);
                        MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", filename, imageData.length);
                    }
                } catch (Exception e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    MiscUtils.getLogger().error("Error uploading signature from IPAD: {}", filename, e);
                    return NONE;
                }
            } else if (uploadSource == null) {
                try (FileOutputStream fos = new FileOutputStream(filename);
                     InputStream is = request.getInputStream()) {
                    int i;
                    int counter = 0;
                    while ((i = is.read()) != -1) {
                        fos.write(i);
                        counter++;
                    }
                    MiscUtils.getLogger().debug("Signature uploaded : " + filename + ", size=" + counter);
                } catch (Exception e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    MiscUtils.getLogger().error("Error uploading signature: {}", filename, e);
                    return NONE;
                }
            }

            if (saveToDB) {
                int demographicNo = -1;
                if (demographic != null && !demographic.isEmpty()) {
                    demographicNo = Integer.parseInt(demographic);
                }
                DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
                DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(
                        loggedInInfo,
                        signatureKey,
                        demographicNo,
                        moduleType);
                if (signature != null) {
                    signatureId = "" + signature.getId();
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }

        // Preserve the JSP's HTML-fragment response contract so existing
        // iframe-scraping JS callers continue to work unchanged.
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.write("<input type=\"hidden\" name=\"signatureId\" value=\"");
            out.write(signatureId);
            out.write("\"/>");
        }
        return NONE;
    }
}
