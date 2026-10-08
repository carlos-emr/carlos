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
package io.github.carlos_emr.carlos.email.action;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Saves the logged-in user's own email footer (follow-up to #3981). A user can only change their
 * own; the provider comes from the session, never from the request.
 *
 * <p>POST only, with {@code _email} write; GET and HEAD get 405 before anything is read or saved.
 * The {@code footerAction} parameter picks one of:</p>
 * <ul>
 *   <li>{@code save}: save {@code myFooter} as the user's own footer (empty means the clinic
 *       footer);</li>
 *   <li>{@code useClinicDefault}: drop the own footer and follow the clinic default;</li>
 *   <li>{@code restorePrevious}: make the footer the user had before a clinic change their own;</li>
 *   <li>{@code keepCurrent}: dismiss the clinic-change notice.</li>
 * </ul>
 * <p>A footer over the limit, or a save that collided with another (two tabs saving at once), is
 * shown again for editing. Changes to the footer are audited without the text.</p>
 *
 * @since 2026-10-07
 */
public final class SaveMyEmailFooter2Action extends ActionSupport {

    static final String FOOTER_PARAM = "myFooter";
    static final String ACTION_PARAM = "footerAction";
    static final String CLINIC_SHOWN_PARAM = "clinicFooterShown";

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final EmailFooterService emailFooterService;

    public SaveMyEmailFooter2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(EmailFooterService.class));
    }

    // Package-private so tests can supply the collaborators.
    SaveMyEmailFooter2Action(SecurityInfoManager securityInfoManager, EmailFooterService emailFooterService) {
        this.securityInfoManager = securityInfoManager;
        this.emailFooterService = emailFooterService;
    }

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_email", "w", null)) {
            throw new SecurityException("missing required sec object (_email)");
        }
        // HTTP method names are case-sensitive and upper case.
        if (!"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String footerAction = request.getParameter(ACTION_PARAM);
        if (footerAction == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        try {
            return apply(request, response, providerNo, footerAction);
        } catch (ConcurrencyFailureException e) {
            // Another save of this user's footer ran at the same moment; this one was rolled back.
            logger.warn("Email footer save collided with another save; asked the user to retry ({})",
                    e.getClass().getSimpleName());
            ViewMyEmailFooter2Action.exposeSettings(request, emailFooterService.settingsFor(providerNo));
            String typed = request.getParameter(FOOTER_PARAM);
            if ("save".equals(footerAction) && typed != null) {
                request.setAttribute(FOOTER_PARAM, typed);
                request.setAttribute("followsClinicDefault", false);
            }
            request.setAttribute("myFooterSaveConflict", true);
            return INPUT;
        }
    }

    private String apply(HttpServletRequest request, HttpServletResponse response, String providerNo,
            String footerAction) throws IOException {
        switch (footerAction) {
            case "save" -> {
                String footer = request.getParameter(FOOTER_PARAM);
                String clinicFooterShown = request.getParameter(CLINIC_SHOWN_PARAM);
                if (footer == null || !EmailFooterService.isFingerprint(clinicFooterShown)) {
                    // A post without the field is not a request to follow the clinic footer, and one
                    // without the clinic footer the page showed cannot be checked against a later change.
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return NONE;
                }
                try {
                    if (!emailFooterService.saveOwnFooter(providerNo, footer, clinicFooterShown)) {
                        // The clinic changed its footer after the page was opened: show the notice and
                        // keep the text, so the user decides with the change in front of them. The
                        // status line keeps saying which footer is in use now.
                        ViewMyEmailFooter2Action.exposeSettings(request, emailFooterService.settingsFor(providerNo));
                        request.setAttribute(FOOTER_PARAM, footer);
                        request.setAttribute("myFooterChangedSinceShown", true);
                        return INPUT;
                    }
                } catch (EmailFooterService.FooterTooLongException e) {
                    ViewMyEmailFooter2Action.exposeSettings(request, emailFooterService.settingsFor(providerNo));
                    request.setAttribute(FOOTER_PARAM, footer);
                    request.setAttribute("followsClinicDefault", false);
                    request.setAttribute("myFooterTooLong", true);
                    return INPUT;
                }
                audit(request, providerNo);
            }
            // The buttons below do not check clinicFooterShown. "Use the clinic footer" and "Keep the
            // clinic footer" pick the clinic footer as it is now. "Use my previous footer" puts back
            // the footer the notice holds now: from a page opened before a later clinic change (its
            // notice answered elsewhere meanwhile), that can be a footer the page did not show. Known
            // gap, listed on the PR; only "save" checks the clinic footer the page showed.
            case "useClinicDefault" -> {
                emailFooterService.useClinicDefault(providerNo);
                audit(request, providerNo);
            }
            case "restorePrevious" -> {
                if (!emailFooterService.restorePreviousFooter(providerNo)) {
                    // Nothing to restore (already done in another tab): show the page as it is.
                    response.sendRedirect(request.getContextPath() + "/email/myEmailFooter");
                    return NONE;
                }
                audit(request, providerNo);
            }
            case "keepCurrent" -> {
                if (!emailFooterService.dismissClinicChangeNotice(providerNo)) {
                    response.sendRedirect(request.getContextPath() + "/email/myEmailFooter");
                    return NONE;
                }
            }
            default -> {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
        }
        response.sendRedirect(request.getContextPath() + "/email/myEmailFooter?saved=true");
        return NONE;
    }

    private static void audit(HttpServletRequest request, String providerNo) {
        LogAction.addLog(providerNo, LogConst.UPDATE, "emailFooterOwn", "", request.getRemoteAddr());
    }
}
