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

import java.util.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ViewDao;
import io.github.carlos_emr.carlos.commn.model.View;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Struts2 action for saving provider-specific view configuration settings.
 *
 * <p>Persists customizable display parameters for a named view (e.g., schedule columns,
 * appointment display options). View settings are scoped by view name, provider number,
 * and user role, allowing different configurations per provider and role combination.</p>
 *
 * <p>Request parameters (excluding {@code method}) are treated as key-value pairs and
 * merged with existing view settings, then saved via {@link ViewDao}.</p>
 *
 * @since 2026-03-17
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ProviderView2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private ViewDao userViewDAO = SpringUtils.getBean(ViewDao.class);

    public String execute() {

        return save();
    }

    public String save() {

        String view_name = request.getParameter("view_name");
        String role = (String) request.getSession().getAttribute("userrole");
        String providerNo = (String) request.getSession().getAttribute("user");
        Map<String, View> currentview = this.userViewDAO.getView(view_name, role, providerNo);

        Map<String, String[]> parameterMap = new HashMap<>(request.getParameterMap());
        parameterMap.remove("method");
        for (Map.Entry<String, String[]> parameter : parameterMap.entrySet()) {
            View view = currentview.get(parameter.getKey());
            if (view == null) {
                view = new View();
            }
            view.setProviderNo(providerNo);
            view.setView_name(view_name);
            view.setRole(role);
            view.setName(parameter.getKey());
            view.setValue(parameter.getValue()[0]);
            currentview.put(parameter.getKey(), view);
        }
        if (currentview != null && !currentview.isEmpty()) {
            for (View value : currentview.values()) {
                userViewDAO.saveView(value);
            }
        }

        return null;
    }

}
