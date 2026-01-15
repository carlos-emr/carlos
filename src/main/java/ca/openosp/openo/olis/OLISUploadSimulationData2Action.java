//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.olis;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.DefaultFileItemFactory;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.openo.olis1.Driver;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class OLISUploadSimulationData2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    @Override
    public String execute() {

        Logger logger = MiscUtils.getLogger();

        String simulationData = null;
        boolean simulationError = false;

        try {
            FileUpload upload = new FileUpload(new DefaultFileItemFactory());
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(request);
            for (FileItem item : items) {
                if (item.isFormField()) {
                    String name = item.getFieldName();
                    if (name.equals("simulateError")) {
                        simulationError = true;
                    }
                } else {
                    if (item.getFieldName().equals("simulateFile")) {
                        InputStream is = item.getInputStream();
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(is, writer, "UTF-8");
                        simulationData = writer.toString();
                    }
                }
            }

            if (simulationData != null && simulationData.length() > 0) {
                if (simulationError) {
                    Driver.readResponseFromXML(LoggedInInfo.getLoggedInInfoFromSession(request), request, simulationData);
                    simulationData = (String) request.getAttribute("olisResponseContent");
                    request.getSession().setAttribute("errors", request.getAttribute("errors"));
                }
                request.getSession().setAttribute("olisResponseContent", simulationData);
                request.setAttribute("result", "File successfully uploaded");
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }
}
