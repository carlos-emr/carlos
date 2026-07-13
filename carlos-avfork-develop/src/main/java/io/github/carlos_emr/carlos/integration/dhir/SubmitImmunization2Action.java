/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.integration.dhir;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Gate action for the DHIR (Digital Health Immunization Registry) submission flow.
 * Forwards to {@code /WEB-INF/jsp/prevention/dhirSubmission.jsp}, whose scriptlet
 * performs {@code DHIRSubmissionManager.save(log)} / {@code update(log)} at lines
 * 135, 389, 418, 449 of the JSP. Because the JSP itself carries no taglib-based
 * authorization, this action is the single enforcement point for the DHIR
 * submission surface.
 *
 * <p>The struts mapping already constrains HTTP method to POST via the
 * {@code httpMethod} interceptor. This class adds the remaining two guards:
 * <ol>
 *   <li>A null-session check so an unauthenticated request cannot reach the JSP.</li>
 *   <li>An {@code _prevention w} privilege check, aligning DHIR submission with
 *       the rest of the prevention module's mutation surface.</li>
 * </ol>
 *
 * @since 2026-04-14
 */
public class SubmitImmunization2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("DHIR submission requires an authenticated session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "w", null)) {
            throw new SecurityException("missing required sec object (_prevention)");
        }
        return SUCCESS;
    }
}
