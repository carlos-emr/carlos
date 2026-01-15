//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.lab.ca.all.pageUtil;


import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.olis.OLISResults2Action;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.all.parsers.Factory;
import ca.openosp.openo.lab.ca.all.parsers.MessageHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PrintOLISLab2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "r", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        try {
            String segmentId = request.getParameter("segmentID");
            String resultUuid = request.getParameter("uuid");
            MessageHandler handler = null;
            if (segmentId == null && segmentId.equals("0")) {
                // if viewing in preview from OLIS search, use uuid
                handler = OLISResults2Action.searchResultsMap.get(resultUuid);
            } else {
                handler = Factory.getHandler(segmentId);
            }
            response.setContentType("application/pdf");  //octet-stream
            response.setHeader("Content-Disposition", "attachment; filename=\"" + handler.getPatientName().replaceAll("\\s", "_") + "_OLISLabReport.pdf\"");
            OLISLabPDFCreator pdf = new OLISLabPDFCreator(request, response.getOutputStream());
            pdf.printPdf();

        } catch (IOException ioe) {
            logger.error("IOException occured insided OLISPrintLabsAction", ioe);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        } catch (Exception e) {
            logger.error("Unknown Exception occured insided OLISPrintLabsAction", e);
            request.setAttribute("printError", Boolean.valueOf(true));
            return "error";
        }


        return SUCCESS;
    }


}
