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
package io.github.carlos_emr.carlos.providers.gate;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.login.Login2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PasswordPolicy;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * View gate for {@code provider/providerupdatepassword.jsp}. Enforces
 * {@code _appointment} {@code w} privilege before forwarding to the JSP. POST-only (GET returns 405).
 *
 * <p>The gate is also where the configured password policy is enforced for a provider's own
 * password change. The change-password form checks the policy in the browser, but that is
 * feedback only: a POST sent without the form would otherwise reach the page that stores the
 * password. A new password that breaks {@link PasswordPolicy}, the same rules the forced reset
 * and the administrator's security record apply, or a new PIN shorter than the configured
 * minimum, is refused here, before that page runs, so a refused request stores nothing.</p>
 *
 * @since 2026-04-13
 */
public final class ViewProviderUpdatePassword2Action extends BaseProviderViewGate2Action {

    private static final Logger LOGGER = MiscUtils.getLogger();

    /** The form the refusal returns to; it prints {@code errormsg}, encoded, above its fields. */
    private static final String CHANGE_PASSWORD_FORM = "/provider/ViewProviderChangePassword";

    private final Supplier<CarlosProperties> properties;

    public ViewProviderUpdatePassword2Action() {
        super();
        this.properties = CarlosProperties::getInstance;
    }

    ViewProviderUpdatePassword2Action(SecurityInfoManager securityInfoManager) {
        this(securityInfoManager, CarlosProperties::getInstance);
    }

    ViewProviderUpdatePassword2Action(SecurityInfoManager securityInfoManager, Supplier<CarlosProperties> properties) {
        super(securityInfoManager);
        this.properties = properties;
    }

    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "w";
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    /**
     * Refuses a new password or PIN that breaks the configured policy before the update page runs.
     *
     * <p>An empty new password or PIN is not judged: the update page treats it as "not changing
     * this one". The refusal is a redirect to the change-password form carrying the same text the
     * form's own check shows for the broken rule. It never echoes the submitted value, and it
     * covers the whole request: a valid PIN sent with a refused password is not stored either.</p>
     */
    @Override
    protected String checkRequest(HttpServletRequest request, HttpServletResponse response,
                                  LoggedInInfo loggedInInfo) throws IOException {
        PasswordPolicy.Validation refusal = policyRefusal(request.getParameter("mypassword"),
                request.getParameter("newpin"), properties.get());
        if (refusal == null) {
            return null;
        }
        // The symbolic reason only: never the candidate, its length or the groups it used.
        LOGGER.info("Refused a provider password or PIN change that breaks the configured policy: provider={}, reason={}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                LogSafe.sanitize(loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo()),
                refusal.auditReason());
        String message = refusal.message(key -> Login2Action.message(request, key));
        response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_FORM + "?errormsg="
                + SafeEncode.forUriComponent(message));
        return NONE;
    }

    /** The first rule the submitted password or PIN breaks, or {@code null} when it breaks none. */
    private static PasswordPolicy.Validation policyRefusal(String newPassword, String newPin,
                                                           CarlosProperties properties) {
        if (newPassword != null && !newPassword.isEmpty()) {
            PasswordPolicy.Validation password = PasswordPolicy.validate(newPassword, properties);
            if (!password.isValid()) {
                return password;
            }
        }
        if (newPin != null && !newPin.isEmpty()) {
            PasswordPolicy.Validation pin = PasswordPolicy.validatePinLength(newPin, properties);
            if (!pin.isValid()) {
                return pin;
            }
        }
        return null;
    }
}
