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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * View gate for {@code provider/providercontrol.jsp} (the provider dashboard /
 * schedule landing page). Enforces {@code _appointment} {@code r} privilege
 * before Struts renders the JSP. Entry point referenced from the login redirect
 * ({@code struts-login.xml}), {@code LoginFilter} refresh list,
 * {@code PersonaService} navbar, and numerous intra-module links.
 *
 * <p>This action should remain the schedule landing target after successful login. Forwarding the
 * JSP from a public filter would skip this provider gate and make unauthenticated behavior depend
 * on JSP scriptlets instead of the shared Struts security boundary.</p>
 *
 * @since 2026-04-13
 */
public final class ViewProviderControl2Action extends BaseProviderViewGate2Action {

    public ViewProviderControl2Action() {
        super();
    }

    ViewProviderControl2Action(SecurityInfoManager securityInfoManager) {
        super(securityInfoManager);
    }

    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "r";
    }
}
