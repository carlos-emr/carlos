/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.provider.web;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ViewDao;
import io.github.carlos_emr.carlos.commn.model.View;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author rjonasz
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class ProviderView2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ViewDao userViewDAO = SpringUtils.getBean(ViewDao.class);

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }


        return save();
    }

    /**
     * Saves the submitted work view settings for the signed-in provider (#2778).
     *
     * <p>Only the settings and values {@link WorkViewSettings} allows are stored, under the role and
     * provider number of the session. A request the allowlist rejects is answered with HTTP 400 and
     * stores nothing. The response body is written here (there is none), so the result is {@code NONE}.</p>
     *
     * @return {@code NONE}, always
     */
    public String save() {
        String role = (String) request.getSession().getAttribute("userrole");
        String providerNo = (String) request.getSession().getAttribute("user");

        String view_name = request.getParameter("view_name");
        Map<String, String> settings;
        try {
            settings = WorkViewSettings.accept(view_name, request.getParameterMap());
        } catch (WorkViewSettings.Rejected rejected) {
            // the message names the setting, never the submitted value
            MiscUtils.getLogger().warn("Rejected work view save for view " + LogSafe.sanitize(view_name) + ": "
                    + rejected.getMessage());
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            } catch (IOException e) {
                MiscUtils.getLogger().error("Could not send the work view rejection", e);
            }
            return NONE;
        }

        Map<String, View> currentview = this.userViewDAO.getView(view_name, role, providerNo);
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            View view = currentview.get(setting.getKey());
            if (view == null) {
                view = new View();
                view.setProviderNo(providerNo);
                view.setView_name(view_name);
                view.setRole(role);
                view.setName(setting.getKey());
            }
            view.setValue(setting.getValue());
            userViewDAO.saveView(view);
        }

        return NONE;
    }

}
