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
package ca.openosp.openo.mds.pageUtil;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.TicklerDao;
import ca.openosp.openo.commn.dao.TicklerLinkDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.TicklerLink;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.LabResultData;


import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ReportMacro2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private TicklerDao ticklerDao = SpringUtils.getBean(TicklerDao.class);
    private TicklerLinkDao ticklerLinkDao = SpringUtils.getBean(TicklerLinkDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws ServletException, IOException {
        ObjectNode result = objectMapper.createObjectNode();

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        String name = request.getParameter("name");

        logger.info("RunMacro called with name = " + name);

        if (name == null) {
            result.put("success", false);
            result.put("error", "No macro name provided");
            response.getWriter().write(result.toString());
            return null;
        }

        UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty up = upDao.getProp(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), UserProperty.LAB_MACRO_JSON);

        boolean success = false;

        //find and run specific macro
        if (up != null && !StringUtils.isEmpty(up.getValue())) {
            ArrayNode macros = (ArrayNode) objectMapper.readTree(up.getValue());
            if (macros != null) {
                for (int x = 0; x < macros.size(); x++) {
                    ObjectNode macro = (ObjectNode) macros.get(x);
                    if (name.equals(macro.get("name").asText())) {
                        success = runMacro(macro, request);
                    }
                }
            }
        } else {
            result.put("success", false);
            result.put("error", "No macros defined in provider preferences");
            response.getWriter().write(result.toString());
            return null;
        }


        result.put("success", success);
        response.getWriter().write(result.toString());
        return null;
    }

    protected boolean runMacro(ObjectNode macro, HttpServletRequest request) {
        logger.info("running macro " + macro.get("name").asText());
        String segmentID = request.getParameter("segmentID");
        String providerNo = request.getParameter("providerNo");
        String labType = request.getParameter("labType");
        String demographicNo = request.getParameter("demographicNo");

        if (macro.has("acknowledge")) {
            logger.info("Acknowledging lab " + labType + ":" + segmentID);
            ObjectNode jAck = (ObjectNode) macro.get("acknowledge");
            String comment = jAck.get("comment").asText();
            CommonLabResultData.updateReportStatus(Integer.parseInt(segmentID), providerNo, 'A', comment, labType, skipComment(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo()));
        }
        if (macro.has("tickler") && !StringUtils.isEmpty(demographicNo)) {
            ObjectNode jTickler = (ObjectNode) macro.get("tickler");

            if (jTickler.has("taskAssignedTo") && jTickler.has("message")) {
                logger.info("Sending Tickler");
                Tickler t = new Tickler();
                t.setTaskAssignedTo(jTickler.get("taskAssignedTo").asText());
                t.setDemographicNo(Integer.parseInt(demographicNo));
                t.setMessage(jTickler.get("message").asText());
                t.setCreator(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
                ticklerDao.persist(t);

                TicklerLink tl = new TicklerLink();
                tl.setTableId(Long.valueOf(segmentID));
                tl.setTableName(LabResultData.HL7TEXT);
                tl.setTicklerNo(t.getId());
                ticklerLinkDao.persist(tl);
            } else {
                logger.info("Cannot sent tickler. Not enough information in macro definition. providers taskAssignedTo and message");
            }

        }

        return true;
    }

    private boolean skipComment(String providerNo) {
        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty uProp = userPropertyDAO.getProp(providerNo, UserProperty.LAB_ACK_COMMENT);
        boolean skipComment = false;
        if (uProp != null && uProp.getValue().equalsIgnoreCase("yes")) {
            skipComment = true;
        }
        return skipComment;
    }
}
