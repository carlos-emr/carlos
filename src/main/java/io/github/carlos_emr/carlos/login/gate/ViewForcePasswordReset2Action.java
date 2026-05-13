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
import io.github.carlos_emr.carlos.login.LoginCredentialCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;

/**
 * Gate for the forced-password-reset page, which depends on the staged login
 * credential cache token before the password change is completed.
 *
 * @since 2026-04-15
 */
public final class ViewForcePasswordReset2Action extends BaseLoginPageView2Action {

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return redirectToExpiredSession(request);
        }
        Object tokenAttr = session.getAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
        if (!(tokenAttr instanceof String token) || LoginCredentialCache.getInstance().peek(token) == null) {
            session.removeAttribute(Login2Action.LOGIN_CREDENTIALS_TOKEN_ATTR);
            return redirectToExpiredSession(request);
        }

        return SUCCESS;
    }

    private String redirectToExpiredSession(HttpServletRequest request) throws IOException {
        HttpServletResponse response = ServletActionContext.getResponse();
        String redirectUrl = Login2Action.loginFailedRedirectUrl(request,
                Login2Action.message(request, "provider.providerchangepassword.errorSessionExpired"));
        response.sendRedirect(redirectUrl);
        return NONE;
    }
}
