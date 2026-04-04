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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.OscarDocumentCreator;

/**
 * @author Toby
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class printLabDaySheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    // Allowlist of permitted XML style filenames mapped to their exact classpath resource names.
    // Using a Map (key=user input, value=trusted constant) ensures the value used for resource
    // loading is never derived from user input, which breaks CodeQL's taint chain.
    // Keys and values are intentionally identical; the Map lookup is what matters for security.
    private static final Map<String, String> ALLOWED_XML_STYLE_FILES;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("labDaySheet.xml", "labDaySheet.xml");
        m.put("billDaySheet.xml", "billDaySheet.xml");
        ALLOWED_XML_STYLE_FILES = Collections.unmodifiableMap(m);
    }

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
                // Validate xmlStyleFile against an explicit allowlist.
                // The Map value (not the user-supplied input) is used for resource loading,
                // which breaks CodeQL's taint chain.
                String safeXmlStyleFile = "labDaySheet.xml";
                if (xmlStyleFile != null && !xmlStyleFile.isEmpty()) {
                    String baseName = FilenameUtils.getName(xmlStyleFile);
                    String resolved = ALLOWED_XML_STYLE_FILES.get(baseName);
                    if (resolved != null) {
                        safeXmlStyleFile = resolved;
                    } else {
                        logger.error("Invalid xmlStyle parameter rejected: {}", Encode.forJava(baseName));
                    }
                }
                
                ins = getClass().getResourceAsStream("/oscar/oscarReport/pageUtil/" + safeXmlStyleFile);
                logger.debug("loading from : /oscar/oscarReport/pageUtil/" + safeXmlStyleFile + " " + ins);
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
