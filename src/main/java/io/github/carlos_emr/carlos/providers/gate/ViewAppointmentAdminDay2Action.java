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
 * View gate for {@code provider/appointmentprovideradminday.jsp}. Enforces
 * {@code _appointment} {@code r} privilege before Struts renders the JSP.
 *
 * <p>The day-view JSP contains its own legacy session assumptions, but those are not a substitute
 * for the Struts gate. Keep direct requests routed through this action so unauthenticated or
 * underprivileged users are handled before JSP execution begins.</p>
 *
 * @since 2026-04-13
 */
public final class ViewAppointmentAdminDay2Action extends BaseProviderViewGate2Action {

    public ViewAppointmentAdminDay2Action() {
        super();
    }

    ViewAppointmentAdminDay2Action(SecurityInfoManager securityInfoManager) {
        super(securityInfoManager);
    }

    @Override
    protected String getSecurityObject() {
        return ProviderAppointmentReadGate.SECURITY_OBJECT;
    }

    @Override
    protected String getAccessRight() {
        return ProviderAppointmentReadGate.ACCESS_RIGHT;
    }
}
