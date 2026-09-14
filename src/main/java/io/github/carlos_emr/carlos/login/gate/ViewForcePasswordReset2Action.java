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
package io.github.carlos_emr.carlos.login.gate;

import java.io.IOException;

import io.github.carlos_emr.carlos.login.Login2Action;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.apache.logging.log4j.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Gate for the forced-password-reset page, which depends on the staged login
 * credential cache token before the password change is completed.
 *
 * <p>The view is intentionally GET/HEAD-only. The password update itself must go through the
 * dedicated POST action, where CSRFGuard, old-password validation, server-side password policy,
 * and terminal token consumption are enforced. This action is the canonical owner for the
 * extensionless {@code /forcepasswordreset} route; do not add a filter-level direct JSP forward
 * for this page.</p>
 *
 * @since 2026-04-15
 */
public final class ViewForcePasswordReset2Action extends BaseLoginPageView2Action {

    private static final Logger LOGGER = MiscUtils.getLogger();

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            LOGGER.info("Rejected /forcepasswordreset: unsupported method={}, uri={}, remote={}",
                    LogSafe.sanitize(method),
                    LogSafe.sanitizeUri(request.getRequestURI()),
                    LogSafe.sanitize(request.getRemoteAddr()));
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            LOGGER.info("Rejected /forcepasswordreset: missing session, remote={}",
                    LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToExpiredSession(request);
        }
        Object tokenAttr = session.getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
        if (!(tokenAttr instanceof String) || !Login2Action.hasValidLoginCredentialsToken(request)) {
            session.removeAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
            session.removeAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
            LOGGER.info("Rejected /forcepasswordreset: missing or stale credential token, remote={}",
                    LogSafe.sanitize(request.getRemoteAddr()));
            return redirectToExpiredSession(request);
        }

        copyForcePasswordResetError(request, session);
        return SUCCESS;
    }

    /**
     * Moves a retryable forced-reset validation error from session scope to request scope.
     *
     * <p>The reset POST redirects back to the GET view to avoid refresh resubmission. This gives
     * the JSP one render of the error and then clears it so stale messages do not survive a later
     * successful retry.</p>
     */
    private void copyForcePasswordResetError(HttpServletRequest request, HttpSession session) {
        Object error = session.getAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
        if (error instanceof String) {
            request.setAttribute("errormsg", error);
            session.removeAttribute(Login2Action.FORCE_PASSWORD_RESET_ERROR_ATTR);
        }
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    private String redirectToExpiredSession(HttpServletRequest request) throws IOException {
        HttpServletResponse response = ServletActionContext.getResponse();
        String redirectUrl = Login2Action.loginFailedRedirectUrl(request,
                Login2Action.message(request, "provider.providerchangepassword.errorSessionExpired"));
        try {
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            LOGGER.warn("Unable to redirect expired forced-password-reset session: remote={}",
                    LogSafe.sanitize(request.getRemoteAddr()), e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } else {
                LOGGER.error("Forced-password-reset expired-session redirect failed after response commit", e);
            }
        }
        return NONE;
    }
}
