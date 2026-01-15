//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.hospitalReportManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.DefaultHandler;
import ca.openosp.openo.lab.ca.all.util.Utilities;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class HRMUploadKey2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() {
        String filename = importFileFileName;
        String proNo = (String) request.getSession().getAttribute("user");
        String outcome = "failure";

        try {
            InputStream is = Files.newInputStream(importFile.toPath());

            String type = request.getParameter("type");


            String filePath = Utilities.saveFile(is, filename);
            is.close();
            File file = new File(filePath);

            is = new FileInputStream(filePath);
            int checkFileUploadedSuccessfully = FileUploadCheck.addFile(file.getName(), is, proNo);
            is.close();

            if (checkFileUploadedSuccessfully != FileUploadCheck.UNSUCCESSFUL_SAVE) {
                logger.debug("filePath" + filePath);
                logger.debug("Type :" + type);

                DefaultHandler defaultHandler = HandlerClassFactory.getDefaultHandler();
                if (defaultHandler != null) {
                    logger.debug("MESSAGE HANDLER " + defaultHandler.getClass().getName());
                }
                if ((defaultHandler.readTextFile(filePath)) != null) {
                    outcome = "success";
                } else {
                    outcome = "uploaded previously";
                }
            }
            request.setAttribute("filePath", filePath);
            request.setAttribute("type", type);
        } catch (Exception e) {
            logger.error("Error: " + e);
            outcome = "exception";
        }

        request.setAttribute("outcome", outcome);

        return SUCCESS;
    }

    private File importFile; // Uploaded file
    private String importFileFileName; // Name of the uploaded file
    private String importFileContentType; // Content type of the uploaded file

}
