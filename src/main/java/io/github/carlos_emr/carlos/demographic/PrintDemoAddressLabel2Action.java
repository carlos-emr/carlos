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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarDocumentCreator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
    private final transient SecurityInfoManager securityInfoManager;
    /**
     * Constructs the action with its injected security dependency. Request and response
     * objects are obtained from {@link ServletActionContext} following the Struts2 2Action pattern.
     *
     * @param securityInfoManager manager used to authorize demographic label access
     */
    public PrintDemoAddressLabel2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Legacy no-arg entry point used by Struts; delegates to the injected constructor
     * via {@link SpringUtils} so the action remains instantiable under the default
     * Struts Spring autowire strategy.
     */
    public PrintDemoAddressLabel2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
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
     * @return String NONE constant after streaming the direct PDF response
     * @throws SecurityException if the user lacks "_demographic" read privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input")
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
            ins = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(System.getProperty("user.home") + "/Addresslabel.xml")));
        } catch (FileNotFoundException | SecurityException ex1) {
            logger.debug("Addresslabel.xml not found in user's home directory. Using default instead", ex1);
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
            try (InputStream templateStream = ins;
                 Connection connection = LegacyJdbcQuery.getConnection()) {
                osc.fillDocumentStream(parameters, sos, "pdf", templateStream, connection, exportPdfJavascript);
            }
        } catch (IOException | SQLException e) {
            MiscUtils.getLogger().error("Error generating demographic label PDF", e);
            if (!response.isCommitted()) {
                try {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating demographic label PDF");
                } catch (IOException sendErrorException) {
                    MiscUtils.getLogger().error("Unable to send demographic label PDF error response", sendErrorException);
                }
            }
        }

        // Action writes PDF bytes directly to response.getOutputStream() above, so return
        // NONE to suppress Struts2 result resolution. The mapping in struts-demographic.xml
        // has no <result name="success">; returning SUCCESS would raise ConfigurationException
        // and the global exception result would render errorpage.jsp on top of the PDF bytes
        // already written to the response (visible as a stray "0" from errorData.statusCode).
        return NONE;
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
