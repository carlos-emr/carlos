//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.dashboard.display;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.dashboard.display.beans.DashboardBean;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.ProviderManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DisplayDashboard2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
    private ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);
    private static Logger logger = MiscUtils.getLogger();

    public String execute() {
        return getDashboard();
    }

    @SuppressWarnings("unused")
    public String getDashboard() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardDisplay", SecurityInfoManager.READ, null)) {
            return "unauthorized";
        }
        Boolean canChgDashboardUser = false;
        if (securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardChgUser", SecurityInfoManager.READ, null)) {
            canChgDashboardUser = true;
        }

        String dashboardId = request.getParameter("dashboardId");
        int id = 0;
        if (dashboardId != null && !dashboardId.isEmpty()) {
            id = Integer.parseInt(dashboardId);
        }

        Provider preferredProvider = loggedInInfo.getLoggedInProvider();
        List<Provider> providers = new ArrayList<Provider>();

        if (canChgDashboardUser) {
            String requestedProviderNo = request.getParameter("providerNo");
            if (requestedProviderNo != null && !requestedProviderNo.isEmpty()) {
                logger.info("DashboardDisplay of provider_no " + requestedProviderNo + " requested by provider_no " + loggedInInfo.getLoggedInProviderNo());
                preferredProvider = providerManager.getProvider(loggedInInfo, requestedProviderNo);
                dashboardManager.setRequestedProviderNo(loggedInInfo, requestedProviderNo);
            } else if (dashboardManager.getRequestedProviderNo(loggedInInfo) != null) {
                preferredProvider = providerManager.getProvider(loggedInInfo, dashboardManager.getRequestedProviderNo(loggedInInfo));
            }
            providers = providerManager.getProviders(loggedInInfo, Boolean.TRUE);
        }

        request.setAttribute("preferredProvider", preferredProvider);
        request.setAttribute("providers", providers);

        DashboardBean dashboard = dashboardManager.getDashboard(loggedInInfo, id);

        request.setAttribute("dashboard", dashboard);

        return SUCCESS;
    }
}
