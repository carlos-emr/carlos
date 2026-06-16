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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Returns the current provider's signature image (PNG) for use as a stamp in the
 * fax annotation viewer.
 *
 * <p>Responds with:
 * <ul>
 *   <li>{@code 200 image/png} — signature file exists and is streamed.</li>
 *   <li>{@code 204 No Content} — provider has not yet saved a signature.</li>
 * </ul>
 *
 * <p>Signature images are stored at
 * {@code {DOCUMENT_DIR}/signatures/provider_{providerNo}.png} — isolated from
 * patient documents and containing no PHI.
 *
 * @since 2026-06
 */
public class GetProviderSignature2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            throw new SecurityException("missing required sec object (_fax)");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String safeProviderNo = PathValidationUtils.validatePathComponent(providerNo, "providerNo");

        File sigDir = resolveSignatureDir();
        File sigFile = new File(sigDir, "provider_" + safeProviderNo + ".png");

        if (!sigFile.exists() || !sigFile.isFile()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return NONE;
        }

        try {
            PathValidationUtils.validateExistingPath(sigFile, sigDir);
        } catch (SecurityException e) {
            logger.error("Signature path validation failed for provider {}", providerNo);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        response.setContentType("image/png");
        response.setContentLengthLong(sigFile.length());
        response.setHeader("Cache-Control", "no-store, private");

        try (OutputStream os = response.getOutputStream()) {
            Files.copy(sigFile.toPath(), os);
        }
        return NONE;
    }

    static File resolveSignatureDir() throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();   
        File docDir = PathValidationUtils.resolveConfiguredDirectory(
                props.getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
        Path sigPath = docDir.toPath().resolve("signatures");
        Files.createDirectories(sigPath);
        if (!Files.isDirectory(sigPath)) {
            throw new IOException("Signatures path is not a directory: " + sigPath);
        }  
        if (!Files.isWritable(sigPath)) {
            throw new IOException("Signatures directory is not writable: " + sigPath);
        }    
        return sigPath.toFile();
    }
}
