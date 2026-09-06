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


package io.github.carlos_emr.carlos.mds.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Calendar;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import org.apache.struts2.ActionSupport;
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
        // Session-derived, NOT the posted providerNo: this writes the acknowledgement into the
        // clinical audit trail, and a posted value let any user with _lab write record it
        // against a colleague. Every real caller already posts the logged-in provider (the
        // inbox builds its links from the session), so nothing legitimate changes; the macro
        // path in ReportMacro2Action has always done it this way.
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
        char status = request.getParameter("status").charAt(0);
        String comment = request.getParameter("comment");
        String lab_type = request.getParameter("labType");
        String ajaxcall = request.getParameter("ajaxcall");

        if (status == 'A') {
            String demographicID = getDemographicIdFromLab(lab_type, labNo);
            LogAction.addLog(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo(), LogConst.ACK, LogConst.CON_HL7_LAB, "" + labNo, request.getRemoteAddr(), demographicID);
        }

        try {
            // A real acknowledgement failure throws (handled below); updateReportStatus otherwise
            // persists the status. Its boolean is not an ack-success signal — do not gate the
            // response on it.
            // Whatever the status, the older versions of the same lab are filed with it: that
            // is what actually clears the collapsed inbox row, and it is what this endpoint has
            // always done. Shared with the macro path so the two ways of acknowledging a lab
            // cannot drift apart again.
            CommonLabResultData.updateReportStatusWithOlderVersions(
                    labNo, providerNo, status, comment, lab_type, false, multiID);
            if (ajaxcall != null && ajaxcall.equals("yes"))
                return null;
            else
                return SUCCESS;
        } catch (Exception e) {
            logger.error("exception in ReportStatusUpdate2Action", e);
            return "failure";
        }
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    public String addComment() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        int labNo = Integer.parseInt(request.getParameter("segmentID"));
        // Session-derived for the same reason as executemain(): a comment on a lab is signed
        // by the provider it is recorded against.
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo();
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
