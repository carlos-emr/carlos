/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.gate;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code admin/configureEmail.jsp}. Enforces {@code _admin}
 * {@code r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/admin/} location. Part of the admin module
 * security-hardening migration (defense in depth; matches the 2Action
 * gate pattern from #1109, #1629, #1632, #1644, #1662, #1663).
 *
 * <p>It also supplies the clinic's default email footer for the form that
 * {@code admin/saveClinicEmailFooter} saves, the fingerprint the form sends back so a save can tell
 * whether the footer changed after the page was opened, and whether saving replaces users' own
 * footers (the page's wording follows it).</p>
 * <p>The page also shows the clinic's email footer logo (issue #3981), which {@code _admin}
 * writers can replace or remove there.</p>
 *
 * @since 2026-04-13
 */
public final class ViewConfigureEmail2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterService emailFooterService;
    private final EmailFooterLogoService logoService;

    /** Used by Struts, which needs a no-argument constructor. */
    public ViewConfigureEmail2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class),
                SpringUtils.getBean(EmailFooterLogoService.class));
    }

    // Package-private so tests can supply the collaborators.
    ViewConfigureEmail2Action(SecurityInfoManager securityInfoManager, EmailFooterService emailFooterService,
            EmailFooterLogoService logoService) {
        this.securityInfoManager = securityInfoManager;
        this.emailFooterService = emailFooterService;
        this.logoService = logoService;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String clinicFooter = emailFooterService.clinicDefault();
        request.setAttribute("clinicFooter", clinicFooter);
        request.setAttribute("clinicFooterFingerprint", EmailFooterService.fingerprint(clinicFooter));
        request.setAttribute("ownFootersReplacedOnClinicChange", emailFooterService.ownFootersReplacedOnClinicChange());
        exposeClinicLogo(request, logoService);
        return SUCCESS;
    }

    /**
     * Gives the page the clinic email footer logo's state. Every path that renders the page calls
     * it, including the clinic footer save shown again (SaveClinicEmailFooter2Action), so the logo
     * card never claims there is no logo.
     *
     * @param request the request the page renders from
     * @param logoService the clinic logo
     */
    public static void exposeClinicLogo(HttpServletRequest request, EmailFooterLogoService logoService) {
        EmailFooterLogo logo = logoService.currentLogo();
        request.setAttribute("clinicLogoSet", logo != null);
        if (logo != null) {
            request.setAttribute("clinicLogoWidth", logo.getWidth());
            request.setAttribute("clinicLogoHeight", logo.getHeight());
        }
    }
}
