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

import com.opensymphony.xwork2.ActionSupport;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.commons.io.IOUtils;
import org.apache.struts2.ServletActionContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves the logged-in provider's own signature image with per-provider authorization.
 *
 * <p>Unlike {@code DisplayImage2Action} (which serves any eForm image to any authenticated user),
 * this endpoint derives the provider number from the session — no {@code providerNo} parameter
 * is accepted. This prevents cross-provider signature access when only the requester's own
 * signature is needed.</p>
 *
 * <p>URL: {@code /provider/providerSignatureImage.do}</p>
 *
 * @since 2026-03-13
 */
public class ProviderSignatureImage2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private static final String SIGNATURE_PREFIX = "consult_sig_";

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "r", null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String signatureName = SIGNATURE_PREFIX + providerNo + ".png";

        InputStream fileStream = null;
        try {
            File imageFolder = new File(OscarProperties.getInstance().getEformImageDirectory());
            if (!imageFolder.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }

            File sigFile = PathValidationUtils.validatePath(signatureName, imageFolder);
            if (!sigFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }

            response.setContentType("image/png");
            response.setHeader("Content-disposition", "inline; filename=\"" + signatureName + "\"");

            fileStream = new FileInputStream(sigFile);
            OutputStream outputStream = response.getOutputStream();
            IOUtils.copy(fileStream, outputStream);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error serving provider signature image", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (Exception ignored) {
                    // Stream cleanup — nothing actionable
                }
            }
        }
        return NONE;
    }
}
