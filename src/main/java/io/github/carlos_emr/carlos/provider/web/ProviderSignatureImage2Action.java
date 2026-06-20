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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves provider signature stamp images with per-provider authorization.
 *
 * <p>Preference pages can request the logged-in provider's own stamp without a
 * {@code providerNo} parameter. Clinical callers may request a specific provider's
 * stamp by supplying {@code providerNo}, but only when they have the relevant
 * clinical privileges.</p>
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

        String loggedInProviderNo = getNumericProviderNo(loggedInInfo.getLoggedInProviderNo(), response);
        if (loggedInProviderNo == null) {
            return NONE;
        }

        String providerNo = getRequestedProviderNo(request, loggedInProviderNo, response);
        if (providerNo == null) {
            return NONE;
        }

        if (!canAccessRequestedProvider(loggedInInfo, request, loggedInProviderNo, providerNo, response)) {
            return NONE;
        }

        String signatureName = PathValidationUtils.validatePathComponent(
                UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png", "signatureName");
        File sigFile = getValidatedSignatureFile(signatureName, response);
        if (sigFile == null) {
            return NONE;
        }

        streamSignature(response, sigFile, signatureName);
        return NONE;
    }

    private String getNumericProviderNo(String providerNo, HttpServletResponse response) {
        if (providerNo == null || !providerNo.matches("\\d+")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        return providerNo;
    }

    private String getRequestedProviderNo(HttpServletRequest request, String loggedInProviderNo, HttpServletResponse response) {
        String requestedProviderNo = request.getParameter("providerNo");
        String providerNo = (requestedProviderNo == null || requestedProviderNo.isBlank())
                ? loggedInProviderNo
                : requestedProviderNo.trim();
        return getNumericProviderNo(providerNo, response);
    }

    private boolean canAccessRequestedProvider(LoggedInInfo loggedInInfo, HttpServletRequest request, String loggedInProviderNo,
                                               String providerNo, HttpServletResponse response) {
        boolean hasExplicitProviderRequest = hasExplicitProviderRequest(request);
        boolean hasClinicalStampAccess = hasExplicitProviderRequest && canViewProviderStampFromClinicalFlow(loggedInInfo);
        if (!providerNo.equals(loggedInProviderNo) && !hasClinicalStampAccess) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (providerNo.equals(loggedInProviderNo)
                && !hasPreferenceAccess(loggedInInfo)
                && !hasClinicalStampAccess
                && !hasVisualEditorAccess(loggedInInfo)) {
            throw new SecurityException("missing required sec object (_pref, _rx, _con, or _eform)");
        }
        return true;
    }

    private boolean hasExplicitProviderRequest(HttpServletRequest request) {
        String requestedProviderNo = request.getParameter("providerNo");
        return requestedProviderNo != null && !requestedProviderNo.isBlank();
    }

    private boolean hasPreferenceAccess(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_pref", READ, null);
    }

    private boolean hasVisualEditorAccess(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin.eform", READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.eform", WRITE, null);
    }

    private File getValidatedSignatureFile(String signatureName, HttpServletResponse response) {
        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory());
        if (!imageFolder.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        File sigFile;
        try {
            sigFile = PathValidationUtils.validatePath(signatureName, imageFolder);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Blocked path traversal attempt for signature image", e);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        if (!sigFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return sigFile;
    }

    private void streamSignature(HttpServletResponse response, File sigFile, String signatureName) {
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
    }

    private boolean canViewProviderStampFromClinicalFlow(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_rx", READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_con", READ, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_con", WRITE, null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_eform", READ, null);
    }
}
