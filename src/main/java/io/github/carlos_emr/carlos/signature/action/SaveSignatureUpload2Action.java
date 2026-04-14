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
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.codec.binary.Base64;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

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
 *
 * <p><b>Response contract:</b>
 * <ul>
 *   <li>405 — non-POST method</li>
 *   <li>400 — missing/non-alphanumeric {@code signatureKey}, or malformed
 *       {@code demographicNo} when {@code saveToDB=true}</li>
 *   <li>400 — malformed {@code data:} URI or non-Base64 {@code signatureImage}
 *       on the IPAD branch</li>
 *   <li>413 — raw-stream upload exceeds {@value #MAX_UPLOAD_BYTES} bytes</li>
 *   <li>500 — generic "Upload failed" (full exception logged server-side)</li>
 *   <li>200 — HTML fragment with hidden {@code signatureId} input</li>
 * </ul>
 * Partial temp files from rejected uploads are deleted before returning.
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

        if (signatureKey == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing signature key");
            return NONE;
        }

        String filename = DigitalSignatureUtils.getTempFilePath(signatureKey);

        // Defence-in-depth: confirm the resolved path is within the temp directory,
        // and reuse the validated File as the sink for the FileOutputStreams below.
        File safeTarget;
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            safeTarget = PathValidationUtils.validateExistingPath(new File(filename), tmpDir);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Path traversal attempt blocked for signatureKey: {}", LogSanitizer.sanitize(signatureKey));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid signature key");
            return NONE;
        }

        if ("IPAD".equalsIgnoreCase(uploadSource) && imageString != null && !imageString.isEmpty()) {
            int comma = imageString.indexOf(",");
            String encoded = comma >= 0 ? imageString.substring(comma + 1) : imageString;
            byte[] imageData;
            try {
                imageData = new Base64().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException e) {
                MiscUtils.getLogger().warn("Invalid Base64 signatureImage for key {}", LogSanitizer.sanitize(signatureKey));
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid image data");
                return NONE;
            }
            if (imageData == null || imageData.length == 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid image data");
                return NONE;
            }
            try (FileOutputStream fos = new FileOutputStream(safeTarget)) {
                fos.write(imageData);
                MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", LogSanitizer.sanitize(filename), imageData.length);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error uploading signature from IPAD: {}", LogSanitizer.sanitize(filename), e);
                deleteQuietly(safeTarget);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upload failed");
                return NONE;
            }
        } else if (uploadSource == null) {
            boolean writeOk = false;
            try (FileOutputStream fos = new FileOutputStream(safeTarget);
                 InputStream is = request.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                int counter = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    counter += bytesRead;
                    if (counter > MAX_UPLOAD_BYTES) {
                        MiscUtils.getLogger().warn("Signature upload exceeded {} bytes; rejecting", MAX_UPLOAD_BYTES);
                        response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload too large");
                        return NONE;
                    }
                    fos.write(buffer, 0, bytesRead);
                }
                writeOk = true;
                MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", LogSanitizer.sanitize(filename), counter);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error uploading signature: {}", LogSanitizer.sanitize(filename), e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upload failed");
                return NONE;
            } finally {
                if (!writeOk) {
                    deleteQuietly(safeTarget);
                }
            }
        }

        if (saveToDB) {
            int demographicNo = -1;
            if (demographic != null && !demographic.isEmpty()) {
                try {
                    demographicNo = Integer.parseInt(demographic);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("Invalid demographicNo: {}", LogSanitizer.sanitize(demographic));
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographicNo");
                    return NONE;
                }
            }
            DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
            DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(
                    loggedInInfo,
                    signatureKey,
                    demographicNo,
                    moduleType);
            if (signature != null) {
                signatureId = String.valueOf(signature.getId());
            }
        }

        response.setStatus(HttpServletResponse.SC_OK);

        // Preserve the JSP's HTML-fragment response contract so existing
        // iframe-scraping JS callers continue to work unchanged. signatureId
        // is a numeric DB id; encode for the HTML attribute context as
        // defence-in-depth if that type ever widens.
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.write("<input type=\"hidden\" name=\"signatureId\" value=\"");
            out.write(Encode.forHtmlAttribute(signatureId));
            out.write("\"/>");
        }
        return NONE;
    }

    private static void deleteQuietly(File f) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException ignored) {
            MiscUtils.getLogger().warn("Failed to clean up partial upload: {}", LogSanitizer.sanitize(f.getName()));
        }
    }

    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
}
