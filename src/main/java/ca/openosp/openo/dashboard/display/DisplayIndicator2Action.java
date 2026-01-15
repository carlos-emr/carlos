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

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.dashboard.display.beans.IndicatorBean;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DisplayIndicator2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private static DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    public String execute() {
        return getIndicator();
    }

    @SuppressWarnings("unused")
    public String getIndicator() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardDisplay", SecurityInfoManager.READ, null)) {
            return "unauthorized";
        }

        String indicatorId = request.getParameter("indicatorId");
        int id = 0;
        if (indicatorId != null && !indicatorId.isEmpty()) {
            id = Integer.parseInt(indicatorId);
        }

        String providerNo = null;
        if (dashboardManager.getRequestedProviderNo(loggedInInfo) != null) {
            providerNo = dashboardManager.getRequestedProviderNo(loggedInInfo);
        }

        IndicatorBean indicatorPanelBean;
        if (providerNo == null) {
            indicatorPanelBean = dashboardManager.getIndicatorPanel(loggedInInfo, id);
        } else {
            indicatorPanelBean = dashboardManager.getIndicatorPanelForProvider(loggedInInfo, providerNo, id);
        }

        request.setAttribute("indicatorPanel", indicatorPanelBean);

        return SUCCESS;
    }

}
