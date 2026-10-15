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
import io.github.carlos_emr.carlos.utility.JsonResponseWriter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ActionSupport;
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
        request.setAttribute("ticklerSubmission", TicklerSubmission.issue(request.getSession(), demographics));

        return SUCCESS;
    }


    @SuppressWarnings({"unchecked", "unused"})
    public String saveTickler() {

        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.WRITE, null)) {
            return "unauthorized";
        }

        ObjectNode jsonObject = objectMapper.createObjectNode();
        TicklerRequest parsed;
        String submission;
        try {
            parsed = TicklerRequest.parse(request.getParameterMap());
            submission = TicklerRequest.single(request.getParameterMap(), "ticklerSubmission");
        } catch (IllegalArgumentException invalid) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonObject.put("success", "false");
            // Messages come from TicklerRequest's own fixed validation text, never from request data.
            jsonObject.put("message", invalid.getMessage());
            return writeResult(jsonObject);
        }
        parsed.tickler().setCreator(loggedInInfo.getLoggedInProviderNo());
        Boolean saved = TicklerSubmission.execute(request.getSession(false), submission,
                request.getParameterMap(), () -> {
                    TicklerHandler ticklerHandler = new TicklerHandler(loggedInInfo, ticklerManager);
                    ticklerHandler.setMasterTickler(parsed.tickler());
                    return ticklerHandler.addTickler(parsed.patients());
                });
        if (saved == null) response.setStatus(HttpServletResponse.SC_CONFLICT);
        if (Boolean.TRUE.equals(saved)) {
            jsonObject.put("success", "true");
        } else {
            jsonObject.put("success", "false");
        }

        return writeResult(jsonObject);
    }

    private String writeResult(ObjectNode jsonObject) {
        try {
            JsonResponseWriter.write(response, jsonObject);
        } catch (IOException e) {
            MiscUtils.getLogger().error("JSON response failed", e);
            return "error";
        }

        return null;
    }
}
