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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sec.AuthenticationRejectionHandler;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnMriViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;

/**
 * View gate for {@code billing/CA/ON/billingONMRI.jsp}, the "Generate OHIP
 * File" page (Medical Records Interchange / OHIP claim diskette — not
 * magnetic resonance imaging).
 *
 * <p>Enforces {@code _billing r} privilege, then assembles a
 * {@link BillingOnMriViewModel} via {@link BillingOnMriViewModelAssembler}
 * so the JSP can read pre-resolved records instead of doing 4 inline
 * {@code SpringUtils.getBean} lookups.</p>
 *
 * @since 2026-04-13
 */
public class ViewBillingOnMri2Action extends ActionSupport {
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnMriViewModelAssembler billingONMRIAssembler;
    public ViewBillingOnMri2Action(SecurityInfoManager securityInfoManager,
                                    BillingOnMriViewModelAssembler billingONMRIAssembler) {
        this.securityInfoManager = securityInfoManager;
        this.billingONMRIAssembler = billingONMRIAssembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        // Reject sessionless requests up front. SecurityInfoManagerImpl.hasPrivilege
        // dereferences loggedInInfo and emits an internal ERROR log on null, which
        // pollutes the log signal for real privilege denials.
        if (loggedInInfo == null) {
            try {
                AuthenticationRejectionHandler.rejectUnauthenticatedRequest(request, response);
            } catch (IOException e) {
                LOGGER.warn(
                        "Unable to reject unauthenticated billing MRI request: method={}, uri={}, remote={}",
                        LogSanitizer.sanitize(request.getMethod()),
                        LogSanitizer.sanitize(request.getRequestURI()),
                        LogSanitizer.sanitize(request.getRemoteAddr()),
                        e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } else {
                    LOGGER.error(
                            "Unable to reject unauthenticated billing MRI request after response commit: "
                                    + "method={}, uri={}, remote={}",
                            LogSanitizer.sanitize(request.getMethod()),
                            LogSanitizer.sanitize(request.getRequestURI()),
                            LogSanitizer.sanitize(request.getRemoteAddr()),
                            e);
                }
            }
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingOnMriViewModel model;
        try {
            model = billingONMRIAssembler.assemble(request, loggedInInfo);
        } catch (SecurityException | BillingDataLoadException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BillingDataLoadException("Failed to load OHIP report view model", e);
        }
        request.setAttribute("mriModel", model);

        // Replicates the legacy session-attribute the JSP scriptlet set so the
        // download servlet (OscarDownload) can resolve homepath=ohipdownload to
        // the OHIP file directory. Now lives in the action so the JSP body
        // remains scriptlet-free.
        request.getSession().setAttribute("ohipdownload",
                CarlosProperties.getInstance().getProperty("HOME_DIR"));

        return SUCCESS;
    }
}
