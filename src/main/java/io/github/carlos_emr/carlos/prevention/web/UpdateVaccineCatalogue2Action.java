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
package io.github.carlos_emr.carlos.prevention.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.prevention.nvc.NvcBundleException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Downloads the National Vaccine Catalogue (NVC) v2 bundle and replaces the local vaccine
 * catalogue, then redirects back to {@link ViewVaccineCatalogue2Action} (post/redirect/get).
 *
 * <p>POST-only and gated on {@code _admin} write: the update deletes and rewrites every
 * catalogue row. A failed download or unusable bundle leaves the installed catalogue untouched
 * (see {@link CanadianVaccineCatalogueManager#update}); the browser only learns the outcome
 * class, the cause goes to the server log.
 *
 * @since 2026-09-24
 */
public class UpdateVaccineCatalogue2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    static final String RESULT_UPDATED = "updated";
    static final String RESULT_FAILED = "failed";
    static final String RESULT_UNAVAILABLE = "unavailable";

    private final SecurityInfoManager securityInfoManager;
    private final CanadianVaccineCatalogueManager catalogueManager;
    private final Runnable preventionTypeRefresh;

    public UpdateVaccineCatalogue2Action() {
        // PreventionDisplayConfig caches the prevention type list (which includes every NVC
        // generic) for the life of the JVM; rebuild it so new vaccines appear without a restart.
        this(SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(CanadianVaccineCatalogueManager.class),
                () -> PreventionDisplayConfig.getInstance().loadPreventions());
    }

    UpdateVaccineCatalogue2Action(SecurityInfoManager securityInfoManager,
                                  CanadianVaccineCatalogueManager catalogueManager,
                                  Runnable preventionTypeRefresh) {
        this.securityInfoManager = securityInfoManager;
        this.catalogueManager = catalogueManager;
        this.preventionTypeRefresh = preventionTypeRefresh;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        // Servlet containers report the method in upper case; compare exactly.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        try {
            catalogueManager.update(loggedInInfo);
        } catch (IOException e) {
            logger.error("NVC catalogue download failed; installed catalogue left unchanged", e);
            return RESULT_UNAVAILABLE;
        } catch (SecurityException e) {
            throw e;
        } catch (NvcBundleException | RuntimeException e) {
            logger.error("NVC catalogue update failed; installed catalogue left unchanged", e);
            return RESULT_FAILED;
        }

        preventionTypeRefresh.run();
        return RESULT_UPDATED;
    }
}
