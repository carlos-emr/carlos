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
package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only endpoint that removes a patient from a waiting list and renders a
 * small close-the-popup HTML page. Replaces the scriptlet-driven
 * {@code waitinglist/RemoveFromWaitingList.jsp} which ran
 * {@code removeFromWaitingList} against any GET request without privilege or
 * HTTP-method enforcement.
 */
public final class WLRemoveFromWaitingList2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic w)");
        }

        String waitingListId = normalizePositiveId(request.getParameter("listId"));
        String demographicNo = normalizePositiveId(request.getParameter("demographicNo"));

        if (waitingListId == null || demographicNo == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        WLWaitingListUtil.removeFromWaitingList(waitingListId, demographicNo);

        return SUCCESS;
    }

    private static String normalizePositiveId(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String trimmedValue = rawValue.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }

        try {
            int parsedValue = Integer.parseInt(trimmedValue);
            return parsedValue > 0 ? Integer.toString(parsedValue) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
