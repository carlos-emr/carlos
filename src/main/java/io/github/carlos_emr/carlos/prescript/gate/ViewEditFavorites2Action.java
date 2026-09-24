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
package io.github.carlos_emr.carlos.prescript.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for the "Edit favourites" page ({@code rx/ViewEditFavorites2} to EditFavorites2.jsp).
 * <p>
 * The favourites side link used to open the page through {@code rx/updateFavorite}, the favourite
 * <em>write</em> action, which had no favourite to update and failed; it is POST-only now. This
 * read-only gate is the page's entry point instead (#3908).
 *
 * @since 2026-09-24
 */
public final class ViewEditFavorites2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Admits the request to its view only with global {@code _rx} read and, when the request names a
     * patient ({@code demographicNo} / {@code demographic_no}), the same privilege for that patient plus
     * access to the patient's record ({@link RxRequestedPatientAccess#require}). A malformed or
     * conflicting patient is refused.
     *
     * @return {@code success} to render the view
     * @throws SecurityException when the caller may not view the module or the named patient
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }
        // The JSP renders the patient the request names; authorise that patient too.
        RxRequestedPatientAccess.require(securityInfoManager, loggedInInfo, request, "_rx", "r");

        return SUCCESS;
    }
}
