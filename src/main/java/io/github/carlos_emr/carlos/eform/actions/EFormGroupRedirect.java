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
 * @since 2026-10-01
 */
final class EFormGroupRedirect {

    static final String GROUPS_PAGE = "/eform/efmmanageformgroups";

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
}
