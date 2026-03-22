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
package io.github.carlos_emr.carlos.dashboard.admin;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerCategory;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.dashboard.handler.TicklerHandler;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for assigning ticklers to multiple patients from the dashboard.
 *
 * <p>Handles both the display of the tickler assignment form (populating priorities,
 * text suggestions, providers, and categories) and the persistence of new ticklers
 * across a set of demographic IDs received as a JSON array. Requires {@code _tickler}
 * write privilege.</p>
 *
 * @since 2026-03-17
 */
public class AssignTickler2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
    private ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dispatches to either the tickler form view or the save handler based on the
     * {@code method} request parameter. Populates form data (priorities, providers,
     * text suggestions, categories) for display when no method is specified.
     *
     * @return String "unauthorized" if the user lacks {@code _tickler} write privilege,
     *         {@link #SUCCESS} for the form view, or delegates to {@link #saveTickler()}
     */
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


    /**
     * Creates and saves ticklers for the specified demographics. Reads tickler parameters
     * from the request, creates a master tickler via {@link TicklerHandler}, then applies
     * it to all demographic IDs. Writes a JSON success/failure response directly to the
     * output stream.
     *
     * @return String {@code null} on successful JSON write, "unauthorized" if privilege
     *         check fails, or "error" if the JSON response cannot be written
     */
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
