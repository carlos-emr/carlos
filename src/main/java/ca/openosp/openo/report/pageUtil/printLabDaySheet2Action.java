//CHECKSTYLE:OFF
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
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.openosp.openo.report.pageUtil;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.OscarDocumentCreator;

/**
 * @author Toby
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class printLabDaySheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    
    // Whitelist pattern for allowed XML style files - alphanumeric, dash, underscore only, must end with .xml
    private static final Pattern ALLOWED_XML_STYLE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.xml$");

    public printLabDaySheet2Action() {
    }

    @Override
    public String execute() {

        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath == null)
            classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");
        System.setProperty("jasper.reports.compile.class.path", classpath);

        HashMap parameters = new HashMap();
        parameters.put("input_date", request.getParameter("input_date"));
        String xmlStyleFile = request.getParameter("xmlStyle");
        ServletOutputStream sos = null;
        InputStream ins = null;

        try {
            ins = new FileInputStream(System.getProperty("user.home") + "Addresslabel.xml");
        } catch (FileNotFoundException ex1) {
            logger.debug("Addresslabel.xml not found in user's home directory. Using default instead");
        }

        if (ins == null) {
            try {
                // Validate xmlStyleFile parameter to prevent path injection
                if (xmlStyleFile == null || xmlStyleFile.isEmpty()) {
                    // Use default file if parameter is not provided
                    xmlStyleFile = "labDaySheet.xml";
                } else {
                    // Remove any path traversal sequences
                    xmlStyleFile = xmlStyleFile.replaceAll("\\.\\.", "");
                    xmlStyleFile = xmlStyleFile.replaceAll("[\\\\/]", "");
                    
                    // Validate against whitelist pattern
                    if (!ALLOWED_XML_STYLE_PATTERN.matcher(xmlStyleFile).matches()) {
                        logger.error("Invalid xmlStyle parameter: " + xmlStyleFile);
                        // Fall back to default file for security
                        xmlStyleFile = "labDaySheet.xml";
                    }
                }
                
                ins = getClass().getResourceAsStream("/oscar/oscarReport/pageUtil/" + xmlStyleFile);
                logger.debug("loading from : /oscar/oscarReport/pageUtil/" + xmlStyleFile + " " + ins);
            } catch (Exception ex1) {
                MiscUtils.getLogger().error("Error", ex1);
            }
        }

        try {
            sos = response.getOutputStream();
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        response.setHeader("Content-disposition", getHeader(response).toString());
        OscarDocumentCreator osc = new OscarDocumentCreator();
        try {
            osc.fillDocumentStream(parameters, sos, "pdf", ins, DbConnectionFilter.getThreadLocalDbConnection());
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;

    }

    private StringBuilder getHeader(HttpServletResponse response) {
        StringBuilder strHeader = new StringBuilder();
        strHeader.append("label_");
        strHeader.append(".pdf");
        response.setHeader("Cache-Control", "max-age=0");
        response.setDateHeader("Expires", 0);
        response.setContentType("application/pdf");
        StringBuilder sbContentDispValue = new StringBuilder();
        sbContentDispValue.append("inline; filename="); //inline - display
        sbContentDispValue.append(strHeader);
        return sbContentDispValue;
    }

}
