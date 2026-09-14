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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeSearchAjaxViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCodeSearchAjaxViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * AJAX endpoint for jQuery-UI Autocomplete on OHIP service codes.
 * Replaces the former {@code billingCodeSearchAjax.jsp} controller-in-a-JSP.
 *
 * <p>Reads the {@code term} request parameter, builds a
 * {@link BillingCodeSearchAjaxViewModel} via
 * {@link BillingCodeSearchAjaxViewModelAssembler}, and writes the JSON array
 * body directly using a Jackson {@link ArrayNode} — no JSP forward
 * needed. Each suggestion is rendered as
 * {@code {"value": "...", "label": "...", "code": "...", "description": "..."}}.</p>
 *
 * @since 2026-04-26
 */
public class ViewBillingCodeSearchAjax2Action extends ActionSupport {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager;
    private final BillingCodeSearchAjaxViewModelAssembler assembler;

    public ViewBillingCodeSearchAjax2Action(SecurityInfoManager securityInfoManager,
                                     BillingCodeSearchAjaxViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        BillingCodeSearchAjaxViewModel model = assembler.assemble(request.getParameter("term"));

        ArrayNode array = JSON_MAPPER.createArrayNode();
        for (BillingCodeSearchAjaxViewModel.Suggestion s : model.getSuggestions()) {
            ObjectNode node = JSON_MAPPER.createObjectNode();
            node.put("value", s.value());
            node.put("label", s.label());
            node.put("code", s.code());
            node.put("description", s.description());
            array.add(node);
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().print(array.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to write billing code search response", e);
        }
        return NONE;
    }
}
