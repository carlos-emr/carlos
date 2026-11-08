/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.report.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;


/**
 * @author Toby
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.report.FixedDaySheetReportService;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class printLabDaySheet2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;
    private final FixedDaySheetReportService reportService;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    public printLabDaySheet2Action(SecurityInfoManager securityInfoManager, FixedDaySheetReportService reportService) {
        this.securityInfoManager = securityInfoManager;
        this.reportService = reportService;
    }

    @Override
    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }


        var style = FixedDaySheetReportService.Style.LAB;
        String requested = request.getParameter("xmlStyle");
        if (requested != null && !requested.isEmpty()) {
            var resolved = FixedDaySheetReportService.Style.find(FilenameUtils.getName(requested));
            if (resolved != null) style = resolved;
            else logger.warn("Invalid xmlStyle parameter rejected: {}", LogSafe.sanitize(requested));
        }
        try {
            reportService.writePdf(response, request.getParameter("input_date"), style);
        } catch (io.github.carlos_emr.carlos.report.FixedDaySheetReportService.InvalidDateException _) {
            sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception ex) {
            // Template/JDBC exceptions can contain patient values or configured SQL.
            logger.error("Day sheet generation failed: {}", io.github.carlos_emr.carlos.utility.LogSafe.exceptionTrace(ex));
            sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        // This action streams the PDF directly and its Struts mapping has no success result.
        // Returning SUCCESS makes Struts render the global error page over the PDF as "0".
        return NONE;

    }

    /** Leaves an already committed response unchanged. */
    private void sendError(int status) {
        if (!response.isCommitted()) {
            response.reset();
            response.setStatus(status);
        }
    }
}
