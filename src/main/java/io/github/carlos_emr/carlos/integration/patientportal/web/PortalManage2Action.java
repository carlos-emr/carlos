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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * View gate for {@code demographic/portalManage.jsp}, the staff page for a patient's portal access.
 *
 * <p>The page itself holds no patient data: it loads the panel and performs every action through the
 * JSON routes, which check their own privileges. This gate refuses a caller who could use none of
 * them, and tells the page which controls to render, so staff are not offered buttons the server
 * would refuse.
 *
 * @since 2026-09-22
 */
public final class PortalManage2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;
    static final String DEMOGRAPHIC_ATTRIBUTE = "portalDemographicNo";

    private final transient SecurityInfoManager securityInfoManager;

    public PortalManage2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    PortalManage2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo session = LoggedInInfo.getLoggedInInfoFromSession(request);
        int demographicNo = PortalJsonAction.positiveInt(request.getParameter("demographicNo"));
        if (demographicNo <= 0) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        PortalJsonAction.requirePatientAccess(securityInfoManager, session, demographicNo);
        boolean readsInvites = allowed(session, PortalStaffContextResolver.OBJECT_INVITE, SecurityInfoManager.READ,
                demographicNo);
        boolean readsAccount = allowed(session, PortalStaffContextResolver.OBJECT_ACCOUNT, SecurityInfoManager.READ,
                demographicNo);
        if (!readsInvites && !readsAccount) {
            throw new SecurityException("missing required sec object (_portal.invite)");
        }
        request.setAttribute(DEMOGRAPHIC_ATTRIBUTE, demographicNo);
        boolean managesInvites =
                allowed(session, PortalStaffContextResolver.OBJECT_INVITE, SecurityInfoManager.WRITE, demographicNo);
        // The same rules PortalInvite2Action applies, so a button is shown only where it will work.
        request.setAttribute("portalCanInvite",
                managesInvites && PortalInvite2Action.maySend(securityInfoManager, session));
        request.setAttribute("portalCanRecover",
                managesInvites && PortalInvite2Action.mayResolve(securityInfoManager, session));
        request.setAttribute("portalCanRevoke", managesInvites);
        request.setAttribute("portalCanSetAccess",
                allowed(session, PortalStaffContextResolver.OBJECT_ACCOUNT, SecurityInfoManager.WRITE, demographicNo));
        request.setAttribute("portalCanUnlock", allowed(session, PortalStaffContextResolver.OBJECT_ACCOUNT_UNLOCK,
                SecurityInfoManager.WRITE, demographicNo));
        return SUCCESS;
    }

    private boolean allowed(LoggedInInfo session, String object, String right, int demographicNo) {
        return securityInfoManager.hasPrivilege(session, object, right, String.valueOf(demographicNo));
    }
}
