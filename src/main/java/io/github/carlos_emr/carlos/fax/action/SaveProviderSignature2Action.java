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
package io.github.carlos_emr.carlos.fax.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

/**
 * Saves the current provider's signature image for future use as a fax stamp.
 *
 * <p>Expects a JSON body {@code {"signatureData":"data:image/png;base64,..."}} or
 * a multipart form field {@code signatureData} containing a PNG data URL.
 *
 * <p>The signature image contains no PHI and is stored per-provider in
 * {@code {DOCUMENT_DIR}/signatures/provider_{providerNo}.png}.
 *
 * @since 2026-06
 */
public class SaveProviderSignature2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final int MAX_SIGNATURE_BYTES = 512 * 1024; // 512 KB
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }

        // Accept either a JSON body or a form field
        String signatureData = extractSignatureData(request);
        if (StringUtils.isBlank(signatureData)) {
            sendJsonError(response, "signatureData is required");
            return NONE;
        }

        // Strip the data URL prefix: "data:image/png;base64,<data>"
        String base64;
        int commaIdx = signatureData.indexOf(',');
        if (commaIdx >= 0) {
            String prefix = signatureData.substring(0, commaIdx).toLowerCase();
            if (!prefix.contains("image/png")) {
                sendJsonError(response, "Only PNG signatures are accepted");
                return NONE;
            }
            base64 = signatureData.substring(commaIdx + 1);
        } else {
            base64 = signatureData;
        }

        byte[] pngBytes;
        try {
            pngBytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            sendJsonError(response, "Invalid base64 signature data");
            return NONE;
        }

        if (pngBytes.length > MAX_SIGNATURE_BYTES) {
            sendJsonError(response, "Signature image is too large");
            return NONE;
        }

        // Verify PNG magic bytes
        if (pngBytes.length < 8 ||
                pngBytes[0] != (byte) 0x89 || pngBytes[1] != 'P' ||
                pngBytes[2] != 'N' || pngBytes[3] != 'G') {
            sendJsonError(response, "Uploaded data is not a valid PNG");
            return NONE;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String safeProviderNo = PathValidationUtils.validatePathComponent(providerNo, "providerNo");

        File sigDir = GetProviderSignature2Action.resolveSignatureDir();
        File sigFile = new File(sigDir, "provider_" + safeProviderNo + ".png");

        // Write atomically via temp file in the same directory
        Path tmpPath = Files.createTempFile(sigDir.toPath(), "sig_", ".tmp");
        try {
            Files.write(tmpPath, pngBytes);
            Files.move(tmpPath, sigFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // ATOMIC_MOVE fails across file systems; fall back to plain replace
            Files.write(sigFile.toPath(), pngBytes);
            Files.deleteIfExists(tmpPath);
        }

        logger.info("Provider {} saved signature image ({} bytes)", providerNo, pngBytes.length);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter w = response.getWriter()) {
            w.write("{\"success\":true}");
        }
        return NONE;
    }

    private static String extractSignatureData(HttpServletRequest request) throws Exception {
        String contentType = StringUtils.defaultString(request.getContentType()).toLowerCase();
        if (contentType.contains("application/json")) {
            // Read JSON body up to limit
            StringBuilder sb = new StringBuilder();
            try (InputStream is = request.getInputStream()) {
                byte[] buf = new byte[4096];
                int read;
                int total = 0;
                while ((read = is.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_SIGNATURE_BYTES * 2) break;
                    sb.append(new String(buf, 0, read, "UTF-8"));
                }
            }
            String json = sb.toString();
            // Simple extraction: find "signatureData":"<value>"
            int keyIdx = json.indexOf("\"signatureData\"");
            if (keyIdx < 0) return null;
            int colonIdx = json.indexOf(':', keyIdx);
            if (colonIdx < 0) return null;
            int startQuote = json.indexOf('"', colonIdx + 1);
            if (startQuote < 0) return null;
            int endQuote = json.indexOf('"', startQuote + 1);
            // Handle escaped quotes inside the value (unlikely for base64 but safe)
            while (endQuote > 0 && json.charAt(endQuote - 1) == '\\') {
                endQuote = json.indexOf('"', endQuote + 1);
            }
            if (endQuote < 0) return null;
            return json.substring(startQuote + 1, endQuote);
        }
        // Form field (multipart or URL-encoded)
        return request.getParameter("signatureData");
    }

    private static void sendJsonError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter w = response.getWriter()) {
            String json = objectMapper.writeValueAsString(
                java.util.Map.of("success", false, "error", message)
            );
            w.write(json);
        }
    }
}
