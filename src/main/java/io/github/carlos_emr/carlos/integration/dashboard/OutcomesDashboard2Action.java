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
package io.github.carlos_emr.carlos.integration.dashboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateHandler;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.JsonResponseWriter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Publishes the requesting provider's indicator results to the shared outcomes dashboard.
 *
 * <p>Route: {@code POST /web/dashboard/OutcomesDashboard}. The only caller is the launcher page
 * {@code web/dashboard/display/sharedOutcomesDashboard.jsp}, which posts here when the dashboard
 * window asks for a metric-set refresh. The request carries one {@code data} parameter: Base64 of
 * {@code {"username": "<provider no>", "queryList": ["<sharedMetricSetName>", ...]}}.</p>
 *
 * <p>Every matched metric set is one outbound HTTP POST to
 * {@code shared_outcomes_dashboard_send_url}, so the route is a mutator: it requires
 * {@code _dashboardDisplay} read, and answers anything but POST with 405 before any other work (the
 * verb is checked first, so the refusal does not depend on the caller's grants).
 * The {@code method} query parameter is not a dispatch selector and is ignored.</p>
 *
 * <p>Responses are JSON: {@code 200 {"published":n}} (a requested metric name no template matches
 * is logged and skipped, not an error), {@code 400} for a malformed request, {@code 403} when the
 * indicator library is withheld ({@code _dashboardManager} write, the right
 * {@code DashboardManager.getIndicatorLibrary} enforces), and {@code 502} when a matched metric set
 * could not be delivered, so the launcher never reports a refresh that did not happen.</p>
 *
 * @since 2026-11-26
 */
public class OutcomesDashboard2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);

    ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);

    Logger logger = MiscUtils.getLogger();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        return refreshIndicators();
    }

    public String refreshIndicators() throws Exception {
        // Publishing is an outbound HTTP POST per metric set; a GET must not trigger it. The method is checked
        // before the privilege so a read verb is answered 405 whatever the caller's grants (the refusal reveals
        // nothing: it depends on the verb alone).
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_dashboardDisplay", "r", null)) {
            throw new SecurityException("missing required sec object (_dashboardDisplay)");
        }

        ObjectNode jsonObject = readRequest(request.getParameter("data"));
        if (jsonObject == null) {
            return writeError(HttpServletResponse.SC_BAD_REQUEST, "invalid_request");
        }

        List<IndicatorTemplate> sharedIndicatorTemplates = dashboardManager.getIndicatorLibrary(loggedInInfo);
        if (sharedIndicatorTemplates == null) {
            // The manager answers null (and audits it) when the caller lacks _dashboardManager write.
            // Without the library no metric name can be resolved, so refuse rather than fail on a null list.
            return writeError(HttpServletResponse.SC_FORBIDDEN, "forbidden");
        }

        String username = jsonObject.get("username").asText();
        ArrayNode arr = (ArrayNode) jsonObject.get("queryList");
        int published = 0;
        int failed = 0;
        for (int x = 0; x < arr.size(); x++) {
            String metricSetName = arr.get(x).asText();

            //need to find the right indicator!
            Provider provider = providerManager.getProvider(loggedInInfo, username);
            IndicatorTemplate indicatorTemplate = findIndicatorTemplateBySharedMetricSetName(loggedInInfo, sharedIndicatorTemplates, metricSetName);

            if (indicatorTemplate != null) {
                try {
                    if (OutcomesDashboardUtils.sendProviderIndicatorData(loggedInInfo, provider, indicatorTemplate)) {
                        published++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    // Keep the cause out of the message: transport errors can echo the destination URL.
                    logger.warn("Shared outcomes publication for requested indicator at index {} failed: {}", x, e.getClass().getSimpleName());
                    failed++;
                }
            } else {
                logger.warn("Requested shared indicator at index {} was not found", x);
            }
        }

        if (failed > 0) {
            return writeError(HttpServletResponse.SC_BAD_GATEWAY, "publish_failed");
        }

        ObjectNode o = objectMapper.createObjectNode();
        o.put("published", published);
        JsonResponseWriter.write(response, o);

        return NONE;
    }

    /**
     * Decodes the {@code data} parameter.
     *
     * @return the request object with a text {@code username} and an array {@code queryList}, or
     *         {@code null} when the parameter is absent, not Base64 JSON, or not that shape (every list member must be text)
     */
    private static ObjectNode readRequest(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(new String(Base64.decodeBase64(data), StandardCharsets.UTF_8));
            if (node != null && node.isObject() && node.path("username").isTextual() && node.path("queryList").isArray()) {
                // Jackson's asText() would turn a number or object member into a name; refuse the request instead.
                for (JsonNode member : node.get("queryList")) {
                    if (!member.isTextual()) {
                        return null;
                    }
                }
                return (ObjectNode) node;
            }
        } catch (IOException e) {
            // Falls through: a malformed body is reported as a bad request without echoing it.
        }
        return null;
    }

    private String writeError(int status, String code) throws IOException {
        response.setStatus(status);
        ObjectNode o = objectMapper.createObjectNode();
        o.put("error", code);
        JsonResponseWriter.write(response, o);
        return NONE;
    }

    protected IndicatorTemplate findIndicatorTemplateBySharedMetricSetName(LoggedInInfo x, List<IndicatorTemplate> templates, String sharedMetricSetName) {

        for (IndicatorTemplate template : templates) {
            IndicatorTemplateHandler ith = new IndicatorTemplateHandler(x, template.getTemplate().getBytes());

            String metricSetName = null;
            NodeList nl = ith.getIndicatorTemplateDocument().getElementsByTagName("sharedMetricSetName");
            if (nl != null && nl.getLength() > 0) {
                metricSetName = nl.item(0).getTextContent();
            }

            if (sharedMetricSetName.equals(metricSetName)) {
                return template;
            }
        }
        return null;
    }
}
