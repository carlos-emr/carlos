/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.utility.RequestNegotiation;
import io.github.carlos_emr.carlos.utility.ScheduleNav;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The success redirect for the eForm group mutators (add group, add to group, remove from
 * group).
 *
 * <p>Each of them used to FORWARD its POST to {@code /eform/efmmanageformgroups}, whose gate
 * ({@code ViewEFormPage2Action}) refuses POST there, so every successful change ended on
 * "CARLOS Error: 405" (issue #4130, the same defect {@link EFormListRedirect} fixed for the
 * delete and restore actions). A redirect starts a new GET, and the group the operator was
 * working on travels as {@code group_view}, URL-encoded so request data cannot change the path
 * or add parameters.
 *
 * <p>Schedule mode ({@code scheduleNav=1}) is the Administration shell opened with the schedule's
 * top navigation bar. The standalone groups page cannot render that header, so a native (full
 * page) submission made there returns through the shell instead, as {@code DelImage2Action} does.
 * An AJAX submission (the shell's {@code registerFormSubmit}) has its response inserted into the
 * shell's panel, so it keeps the groups page and only carries the flag on.
 *
 * @since 2026-10-01
 */
final class EFormGroupRedirect {

    static final String GROUPS_PAGE = "/eform/efmmanageformgroups";

    /** The Administration shell, opened on its eForm Groups section (leftNav deep link). */
    static final String SHELL_GROUPS_SECTION = "/administration?show=FormsGroups";

    private EFormGroupRedirect() {
    }

    /**
     * @param groupName the group to reopen; null or empty lands on the group list
     * @return the application-relative groups page, never null
     */
    static String toGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return GROUPS_PAGE;
        }
        return GROUPS_PAGE + "?group_view=" + URLEncoder.encode(groupName, StandardCharsets.UTF_8);
    }

    /**
     * Like {@link #toGroup(String)}, but keeps schedule mode: a native submission made in schedule
     * mode returns to the Administration shell's eForm Groups section, and an AJAX one keeps the
     * groups page with the flag carried on.
     *
     * @param groupName the group to reopen; null or empty lands on the group list
     * @param request the group mutator's POST
     * @return the application-relative redirect target, never null
     */
    static String toGroup(String groupName, HttpServletRequest request) {
        if (!ScheduleNav.isActive(request)) {
            return toGroup(groupName);
        }
        if (RequestNegotiation.isAjax(request)) {
            return ScheduleNav.append(toGroup(groupName), request);
        }
        // The shell's deep-link loader passes group_view on to the groups panel, so the
        // group the user was working in reopens instead of the first one.
        String shellTarget = (groupName == null || groupName.isEmpty())
                ? SHELL_GROUPS_SECTION
                : SHELL_GROUPS_SECTION + "&group_view=" + URLEncoder.encode(groupName, StandardCharsets.UTF_8);
        return ScheduleNav.append(shellTarget, request);
    }
}
