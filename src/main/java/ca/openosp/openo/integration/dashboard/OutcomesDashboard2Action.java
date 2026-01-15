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
package ca.openosp.openo.integration.dashboard;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.IndicatorTemplate;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.dashboard.handler.IndicatorTemplateHandler;
import ca.openosp.openo.managers.DashboardManager;
import ca.openosp.openo.managers.ProviderManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dashboardDisplay", "r", null)) {
            throw new SecurityException("missing required sec object (_dashboarDisplay)");
        }

        List<IndicatorTemplate> sharedIndicatorTemplates = dashboardManager.getIndicatorLibrary(LoggedInInfo.getLoggedInInfoFromSession(request));

        String data = request.getParameter("data");

        byte[] b = Base64.decodeBase64(data);

        ObjectNode jsonObject = (ObjectNode) objectMapper.readTree(new String(b));

        String username = jsonObject.get("username").asText();
        ArrayNode arr = (ArrayNode) jsonObject.get("queryList");
        for (int x = 0; x < arr.size(); x++) {
            String metricSetName = arr.get(x).asText();

            //need to find the right indicator!
            Provider provider = providerManager.getProvider(LoggedInInfo.getLoggedInInfoFromSession(request), username);
            IndicatorTemplate indicatorTemplate = findIndicatorTemplateBySharedMetricSetName(LoggedInInfo.getLoggedInInfoFromSession(request), sharedIndicatorTemplates, metricSetName);

            if (indicatorTemplate != null) {
                OutcomesDashboardUtils.sendProviderIndicatorData(LoggedInInfo.getLoggedInInfoFromSession(request), provider, indicatorTemplate);
            } else {
                logger.warn("shared indicator not found:" + metricSetName);
            }
        }

        ObjectNode o = objectMapper.createObjectNode();

        response.getWriter().print(o.toString());

        return null;
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
