/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.demographic;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarDocumentCreator;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action for generating and printing PDF address labels for patient demographics.
 *
 * <p>This action generates address labels using JasperReports with customizable templates.
 * It supports both interactive and silent printing to configured printers, with user-specific
 * printer preferences stored in UserProperty settings.</p>
 *
 * <p>The action loads an address label template (Addresslabel.xml) from either the user's
 * home directory for customization or from the default classpath resource. The generated
 * PDF is streamed directly to the HTTP response with optional JavaScript to trigger
 * automatic printing in the client's browser.</p>
 *
 * <p><b>Security:</b> Requires "_demographic" read privilege to access patient information.</p>
 *
 * <p><b>Healthcare Context:</b> Address labels are commonly used for patient correspondence,
 * lab specimen labels, and medical record filing in Canadian healthcare settings.</p>
 *
 * @see UserProperty
 * @see OscarDocumentCreator
 * @see UserPropertyDAO
 * @since 2026-01-24
 */
public class PrintDemoAddressLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Constructs a new PrintDemoAddressLabel2Action instance.
     *
     * <p>This constructor initializes the action with default settings. Request and response
     * objects are automatically injected by Struts2 via ServletActionContext.</p>
     */
    public PrintDemoAddressLabel2Action() {
    }

    /**
     * Executes the address label generation and printing workflow.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates security privileges for demographic access</li>
     *   <li>Retrieves user-specific printer preferences (printer name and silent print mode)</li>
     *   <li>Loads the address label template (Addresslabel.xml) from user home directory or classpath</li>
     *   <li>Configures JasperReports parameters with demographic number</li>
     *   <li>Generates PDF output and streams to HTTP response</li>
     *   <li>Optionally injects JavaScript for automatic printing to configured printer</li>
     * </ol>
     *
     * <p><b>Request Parameters:</b></p>
     * <ul>
     *   <li>demographic_no - String the unique identifier of the patient demographic record</li>
     * </ul>
     *
     * <p><b>User Properties Used:</b></p>
     * <ul>
     *   <li>DEFAULT_PRINTER_PDF_ADDRESS_LABEL - String the printer name for address labels</li>
     *   <li>DEFAULT_PRINTER_PDF_ADDRESS_LABEL_SILENT_PRINT - String "yes" to enable silent printing</li>
     * </ul>
     *
     * <p><b>Security:</b> Requires "_demographic" read privilege. Throws SecurityException if not authorized.</p>
     *
     * <p><b>Template Resolution:</b> First attempts to load custom template from
     * ${user.home}/Addresslabel.xml, falls back to classpath resource
     * /oscar/oscarDemographic/Addresslabel.xml if custom template not found.</p>
     *
     * @return String "success" constant from ActionSupport indicating successful execution
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
        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL_SILENT_PRINT);
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
        HashMap parameters = new HashMap();
        parameters.put("demo", request.getParameter("demographic_no"));
        ServletOutputStream sos = null;


        InputStream ins = null;

        logger.debug("user home: " + System.getProperty("user.home"));

        try {
            ins = new FileInputStream(System.getProperty("user.home") + "/Addresslabel.xml");
        } catch (FileNotFoundException ex1) {
            logger.debug("Addresslabel.xml not found in user's home directory. Using default instead");
        }

        if (ins == null) {
            try {

                ins = getClass().getResourceAsStream("/oscar/oscarDemographic/Addresslabel.xml");
                logger.debug("loading from : /oscar/oscarDemographic/Addresslabel.xml " + ins);
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
            osc.fillDocumentStream(parameters, sos, "pdf", ins, DbConnectionFilter.getThreadLocalDbConnection(), exportPdfJavascript);
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return SUCCESS;
    }

    /**
     * Constructs HTTP headers for the PDF response.
     *
     * <p>This method generates the Content-Disposition header value for inline PDF display
     * in the browser with a default filename of "label_.pdf". It also sets cache control
     * headers to prevent caching of the generated document.</p>
     *
     * <p><b>Headers Set:</b></p>
     * <ul>
     *   <li>Cache-Control: max-age=0 - Prevents caching of the PDF</li>
     *   <li>Expires: 0 - Sets expiration to epoch time</li>
     *   <li>Content-Type: application/pdf - Indicates PDF content</li>
     *   <li>Content-Disposition: inline; filename=label_.pdf - Displays PDF inline with filename</li>
     * </ul>
     *
     * @param response HttpServletResponse the HTTP response object to configure headers on
     * @return StringBuilder the Content-Disposition header value including filename
     */
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
