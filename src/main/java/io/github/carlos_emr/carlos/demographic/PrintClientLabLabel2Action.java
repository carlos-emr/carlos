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

package io.github.carlos_emr.carlos.demographic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarDocumentCreator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for generating and printing client laboratory specimen labels as PDF.
 *
 * <p>This action creates PDF labels used for identifying patient laboratory specimens.
 * It supports configurable printer settings per provider, including silent printing
 * mode for high-volume clinical environments. The label template is loaded from either
 * a custom file in the user's home directory ({@code ClientLabLabel.xml}) or from the
 * default classpath resource.</p>
 *
 * <p><b>Security:</b> Requires "_demographic" read privilege.</p>
 *
 * @see io.github.carlos_emr.OscarDocumentCreator
 * @see io.github.carlos_emr.carlos.commn.model.UserProperty
 * @since 2026-03-17
 */
public class PrintClientLabLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Constructs a new PrintClientLabLabel2Action instance.
     */
    public PrintClientLabLabel2Action() {
    }

    /**
     * Generates a client lab label PDF and streams it to the HTTP response.
     *
     * <p>Retrieves provider-specific printer preferences, loads the label template
     * from the user's home directory or classpath fallback, and generates the PDF
     * using JasperReports. Optionally injects JavaScript for automatic printing.</p>
     *
     * @return String SUCCESS after streaming the PDF
     * @throws SecurityException if the user lacks "_demographic" read privilege
     */
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        //patient
        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath == null)
            classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");
        System.setProperty("jasper.reports.compile.class.path", classpath);
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL_SILENT_PRINT);
        if (prop != null) {
            if (prop.getValue().equalsIgnoreCase("yes")) {
                silentPrint = true;
            }
        }
        String exportPdfJavascript = null;

        if (defaultPrinterName != null && !defaultPrinterName.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + defaultPrinterName + "';";
            if (silentPrint == true) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("demo", request.getParameter("demographic_no"));

        InputStream ins = null;
        try {
            logger.debug("user home: " + System.getProperty("user.home"));
            File file = new File(System.getProperty("user.home") + "/ClientLabLabel.xml");
            if (file.exists()) {
                ins = new FileInputStream(file);
            } else {
                ins = getClass().getResourceAsStream("/oscar/oscarDemographic/ClientLabLabel.xml");
                logger.debug("loading from : /oscar/oscarDemographic/ClientLabLabel.xml " + ins);
            }
            ServletOutputStream sos = response.getOutputStream();
            response.setHeader("Content-disposition", getHeader(response).toString());
            OscarDocumentCreator osc = new OscarDocumentCreator();
            osc.fillDocumentStream(parameters, sos, "pdf", ins, DbConnectionFilter.getThreadLocalDbConnection(), exportPdfJavascript);
        } catch (FileNotFoundException ex1) {
            logger.debug("Addresslabel.xml not found in user's home directory. Using default instead");
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        } catch (Exception ex1) {
            MiscUtils.getLogger().error("Error", ex1);
        } finally {
            IOUtils.closeQuietly(ins);
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
