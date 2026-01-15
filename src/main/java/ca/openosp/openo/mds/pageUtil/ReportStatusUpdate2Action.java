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
import java.util.Calendar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.PatientLabRoutingDao;
import ca.openosp.openo.commn.model.PatientLabRouting;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.util.ConversionUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ReportStatusUpdate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public ReportStatusUpdate2Action() {
    }

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws ServletException, IOException {
        if ("addComment".equals(request.getParameter("method"))) {
            return addComment();
        }
        return executemain();
    }

    public String executemain() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        int labNo = Integer.parseInt(request.getParameter("segmentID"));
        String multiID = request.getParameter("multiID");
        String providerNo = request.getParameter("providerNo");
        char status = request.getParameter("status").charAt(0);
        String comment = request.getParameter("comment");
        String lab_type = request.getParameter("labType");
        String ajaxcall = request.getParameter("ajaxcall");

        if (status == 'A') {
            String demographicID = getDemographicIdFromLab(lab_type, labNo);
            LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.ACK, LogConst.CON_HL7_LAB, "" + labNo, request.getRemoteAddr(), demographicID);
        }

        try {
            CommonLabResultData.updateReportStatus(labNo, providerNo, status, comment, lab_type);
            if (multiID != null) {
                String[] id = multiID.split(",");
                int i = 0;
                int idNum = Integer.parseInt(id[i]);
                while (idNum != labNo) {
                    CommonLabResultData.updateReportStatus(idNum, providerNo, 'F', "", lab_type);
                    i++;
                    idNum = Integer.parseInt(id[i]);
                }

            }
            if (ajaxcall != null && ajaxcall.equals("yes"))
                return null;
            else
                return SUCCESS;
        } catch (Exception e) {
            logger.error("exception in ReportStatusUpdate2Action", e);
            return "failure";
        }
    }

    public String addComment() {
        int labNo = Integer.parseInt(request.getParameter("segmentID"));
        String providerNo = request.getParameter("providerNo");
        char status = request.getParameter("status").charAt(0);
        String comment = request.getParameter("comment");
        String lab_type = request.getParameter("labType");

        try {

            CommonLabResultData.updateReportStatus(labNo, providerNo, status, comment, lab_type);

        } catch (Exception e) {
            logger.error("exception in setting comment", e);
            return "failure";
        }

        String now = ConversionUtils.toDateString(Calendar.getInstance().getTime(), "dd-MMM-yy HH mm");
        ObjectNode json = objectMapper.createObjectNode();
        json.put("date", now);
        logger.info("JSON " + json.toString());
        response.setContentType("application/json");
        try {
            response.getWriter().write(json.toString());
            response.flushBuffer();
        } catch (IOException e) {
            logger.error("FAILED TO RETURN DATE", e);
        }

        return null;
    }

    private static String getDemographicIdFromLab(String labType, int labNo) {
        String demographicID = "";
        PatientLabRoutingDao dao = SpringUtils.getBean(PatientLabRoutingDao.class);
        for (PatientLabRouting r : dao.findByLabNoAndLabType(labNo, labType)) {
            demographicID = "" + r.getDemographicNo();
        }
        return demographicID;
    }
}
