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
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
 *   <li>500 "Upload failed" — I/O error writing the temp file</li>
 *   <li>500 "Save failed" — DB persistence failed after a successful upload
 *       (null return or propagated exception from
 *       {@link DigitalSignatureManager#processAndSaveDigitalSignature})</li>
 *   <li>200 — HTML fragment with hidden {@code signatureId} input</li>
 * </ul>
 * Partial temp files from rejected uploads and orphaned temp files from
 * failed persistence are deleted before returning.
 */
public final class SaveSignatureUpload2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied SaveSignatureUpload: no session");
            throw new SecurityException("missing required sec object (_con w)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "w", null)) {
            MiscUtils.getLogger().warn("Denied SaveSignatureUpload: provider={} lacks _con w",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_con w)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
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
            MiscUtils.getLogger().warn("Invalid signatureKey rejected: {}", LogSafe.sanitize(signatureKey)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
            MiscUtils.getLogger().warn("Path traversal attempt blocked for signatureKey: {}", LogSafe.sanitize(signatureKey)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid signature key");
            return NONE;
        }

        if ("IPAD".equalsIgnoreCase(uploadSource)) {
            if (imageString == null || imageString.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing signatureImage");
                return NONE;
            }
            int comma = imageString.indexOf(",");
            String encoded = comma >= 0 ? imageString.substring(comma + 1) : imageString;
            // Cap the encoded length so a large signatureImage form field cannot
            // allocate an unbounded byte[] during decode. Base64 expands 3 bytes
            // to 4 characters, so MAX_UPLOAD_BYTES decoded ≈ MAX_BASE64_CHARS
            // encoded. Reject before decode.
            if (encoded.length() > MAX_BASE64_CHARS) {
                MiscUtils.getLogger().warn("IPAD signatureImage exceeded {} base64 chars; rejecting", MAX_BASE64_CHARS);
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload too large");
                return NONE;
            }
            byte[] imageData;
            try {
                imageData = new Base64().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException e) {
                MiscUtils.getLogger().warn("Invalid Base64 signatureImage for key {}", LogSafe.sanitize(signatureKey)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid image data");
                return NONE;
            }
            if (imageData == null || imageData.length == 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid image data");
                return NONE;
            }
            // Belt-and-suspenders: Base64 decoders that strip non-alphabet chars
            // can still produce more than MAX_UPLOAD_BYTES on pathological input.
            if (imageData.length > MAX_UPLOAD_BYTES) {
                MiscUtils.getLogger().warn("IPAD signature decoded to {} bytes; rejecting", imageData.length); // NOSONAR javasecurity:S5145 - logs numeric/non-request metadata only
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload too large");
                return NONE;
            }
            try (FileOutputStream fos = new FileOutputStream(safeTarget)) {
                fos.write(imageData); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- writes decoded image bytes to validated file target, not HTTP response HTML
                MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", LogSafe.sanitize(filename), imageData.length); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error uploading signature from IPAD: {}", LogSafe.sanitize(filename), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
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
                MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", LogSafe.sanitize(filename), counter); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error uploading signature: {}", LogSafe.sanitize(filename), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upload failed");
                return NONE;
            } finally {
                if (!writeOk) {
                    deleteQuietly(safeTarget);
                }
            }
        } else {
            MiscUtils.getLogger().warn("Unknown signature upload source: {}", LogSafe.sanitize(uploadSource)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown source");
            return NONE;
        }

        if (saveToDB) {
            int demographicNo = -1;
            if (demographic != null && !demographic.isEmpty()) {
                try {
                    demographicNo = Integer.parseInt(demographic);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("Invalid demographicNo: {}", LogSafe.sanitize(demographic)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographicNo");
                    return NONE;
                }
            }
            DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
            DigitalSignature signature;
            try {
                signature = digitalSignatureManager.processAndSaveDigitalSignature(
                        loggedInInfo,
                        signatureKey,
                        demographicNo,
                        moduleType);
            } catch (RuntimeException e) {
                // Manager documents null-return on expected failures; any propagated
                // RuntimeException is an unexpected failure mode (e.g. encryption,
                // DB connectivity). Delete the orphan temp file before surfacing 500.
                MiscUtils.getLogger().error("Digital signature persist failed for key {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(signatureKey), e);
                deleteQuietly(safeTarget);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Save failed");
                return NONE;
            }
            if (signature == null) {
                // Manager returns null for: digital signatures disabled, temp file
                // missing/empty, path-validation failure, or any internally-caught
                // exception. The file upload succeeded but persistence did not —
                // surface 500 rather than returning an empty signatureId that the
                // caller has no way to distinguish from a successful save.
                MiscUtils.getLogger().warn("Digital signature persist returned null for key {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(signatureKey));
                deleteQuietly(safeTarget);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Save failed");
                return NONE;
            }
            signatureId = String.valueOf(signature.getId());
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
            MiscUtils.getLogger().warn("Failed to clean up partial upload: {}", LogSafe.sanitize(f.getName())); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
        }
    }

    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    // Ceil(MAX_UPLOAD_BYTES / 3) * 4 — max base64 encoded length that decodes to MAX_UPLOAD_BYTES.
    private static final int MAX_BASE64_CHARS = ((MAX_UPLOAD_BYTES + 2) / 3) * 4;
}
