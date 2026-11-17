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
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * View gate for {@code provider/tabAlertsRefresh.jsp}, the fragment the schedule polls to
 * refresh the new-item counts on its Inbox, Tickler, Msg and Consultation tabs.
 *
 * <p>The schedule entry privilege ({@code _appointment r}) opens the gate; each tab then needs
 * the privilege its schedule menu entry is rendered under (mainMenu.jsp and
 * appointmentprovideradminday.jsp), because the count is that module's data. Issue #2689: the
 * schedule refreshed every tab, including ones hidden from the user's role, and the Messenger
 * count threw for a receptionist without {@code _msg}, so the poll answered HTTP 500. An unknown
 * or missing tab id is a malformed request (400); a tab the role cannot see is refused (403).</p>
 *
 * @since 2026-04-13
 */
public final class ViewTabAlertsRefresh2Action extends BaseProviderViewGate2Action {

    /** Tab element id to the security object its menu entry is shown under (read right). */
    static final Map<String, String> TAB_SECURITY_OBJECTS = Map.of(
            "oscar_new_lab", "_appointment.doctorLink",
            "oscar_new_tickler", "_tickler",
            "oscar_new_msg", "_msg",
            "oscar_aged_consults", "_con",
            // Scratch pad: the window that refreshes it is itself a provider-area page.
            "oscar_scratch", "_appointment");

    public ViewTabAlertsRefresh2Action() {
        super();
    }

    ViewTabAlertsRefresh2Action(SecurityInfoManager securityInfoManager) {
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

    @Override
    protected String checkRequest(HttpServletRequest request, HttpServletResponse response,
                                  LoggedInInfo loggedInInfo) throws IOException {
        String tab = request.getParameter("id");
        String securityObject = tab == null ? null : TAB_SECURITY_OBJECTS.get(tab);
        if (securityObject == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        if (!securityInfoManager().hasPrivilege(loggedInInfo, securityObject, SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (" + securityObject + ")");
        }
        return null;
    }
}
