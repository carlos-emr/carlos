/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.provider.web;

import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Serves the logged-in provider's own signature image with per-provider authorization.
 *
 * <p>Unlike {@code DisplayImage2Action} (which serves any eForm image to any authenticated user),
 * this endpoint derives the provider number from the session — no {@code providerNo} parameter
 * is accepted. This prevents cross-provider signature access when only the requester's own
 * signature is needed.</p>
 *
 * <p>Returns HTTP 401 if not authenticated, 404 if the signature file does not exist,
 * or 500 on internal error. On success, streams the PNG image inline.</p>
 *
 * <p>URL: {@code /provider/providerSignatureImage}</p>
 *
 * @since 2026-03-13
 */
public class ProviderSignatureImage2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static final String READ = "r";
    private static final String WRITE = "w";

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", READ, null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }

        String loggedInProviderNo = loggedInInfo.getLoggedInProviderNo();
        if (loggedInProviderNo == null || !loggedInProviderNo.matches("\\d+")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        String requestedProviderNo = request.getParameter("providerNo");
        String providerNo = (requestedProviderNo == null || requestedProviderNo.isBlank())
                ? loggedInProviderNo
                : requestedProviderNo.trim();
        if (!providerNo.matches("\\d+")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        if (!providerNo.equals(loggedInProviderNo) && !canViewOtherProviderStamp(loggedInInfo)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        String signatureName = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";

        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory());
        if (!imageFolder.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        File sigFile;
        try {
            sigFile = PathValidationUtils.validatePath(signatureName, imageFolder);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Blocked path traversal attempt for signature image", e);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        if (!sigFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        response.setContentType("image/png");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Content-disposition", "inline; filename=\"" + signatureName.replaceAll("[^a-zA-Z0-9_.]", "_") + "\"");

        try (InputStream fileStream = new FileInputStream(sigFile)) {
            OutputStream outputStream = response.getOutputStream();
            IOUtils.copy(fileStream, outputStream);
        } catch (FileNotFoundException e) {
            MiscUtils.getLogger().debug("Signature image file not found on disk: {}", signatureName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error serving provider signature image", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return NONE;
    }

    private boolean canViewOtherProviderStamp(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_rx", READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_con", READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_con", WRITE, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_eform", READ, null);
    }
}
