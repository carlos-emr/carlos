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
package ca.openosp.openo.dashboard.admin;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.TicklerCategory;
import ca.openosp.openo.commn.model.TicklerTextSuggest;
import ca.openosp.openo.dashboard.handler.TicklerHandler;
import ca.openosp.openo.managers.ProviderManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AssignTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        if ("saveTickler".equals(request.getParameter("method"))) {
            return saveTickler();
        }

        String demographics = request.getParameter("demographics");
        Tickler.PRIORITY[] priorities = Tickler.PRIORITY.values();
        List<TicklerTextSuggest> textSuggestions = ticklerManager.getActiveTextSuggestions(loggedInInfo);
        List<Provider> providers = providerManager.getProviders(loggedInInfo, Boolean.TRUE);
        List<TicklerCategory> ticklerCategories = ticklerManager.getActiveTicklerCategories(loggedInInfo);

        request.setAttribute("priorities", priorities);
        request.setAttribute("textSuggestions", textSuggestions);
        request.setAttribute("providers", providers);
        request.setAttribute("ticklerCategories", ticklerCategories);
        request.setAttribute("demographics", demographics);

        return SUCCESS;
    }


    @SuppressWarnings({"unchecked", "unused"})
    public String saveTickler() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        TicklerHandler ticklerHandler = new TicklerHandler(loggedInInfo, ticklerManager);
        ticklerHandler.createMasterTickler(request.getParameterMap());
        ObjectNode jsonObject = objectMapper.createObjectNode();

        if (ticklerHandler.addTickler(request.getParameter("demographics"))) {
            jsonObject.put("success", "true");
        } else {
            jsonObject.put("success", "false");
        }

        try {
            response.getWriter().write(jsonObject.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().error("JSON response failed", e);
            return "error";
        }

        return null;
    }
}
