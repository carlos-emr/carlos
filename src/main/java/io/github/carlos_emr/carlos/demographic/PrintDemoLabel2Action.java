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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action that generates and prints patient demographic labels as PDF documents.
 *
 * <p>This 2Action class handles the generation of patient demographic labels for printing,
 * typically used for chart filing, specimen labeling, or patient identification purposes
 * in a healthcare setting. The action supports configurable label templates and printer
 * settings per provider.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>PDF label generation from XML templates using JasperReports</li>
 *   <li>Per-provider default printer configuration</li>
 *   <li>Silent printing support (automatic printing without user interaction)</li>
 *   <li>Multiple label template support (MRP labels, appointment provider labels)</li>
 *   <li>Integration with patient demographic and appointment data</li>
 * </ul>
 *
 * <p>Security: Requires "_demographic" read access for the requested patient
 * before loading printer preferences or generating the report.</p>
 *
 * <p>Label templates are configurable via CarlosProperties:</p>
 * <ul>
 *   <li><code>pdfLabelMRP</code> - Path to the MRP (Most Responsible Provider) label template</li>
 *   <li><code>pdfLabelApptProvider</code> - Path to the appointment provider label template</li>
 * </ul>
 *
 * <p>Default printer settings are managed per provider using UserProperty entries:</p>
 * <ul>
 *   <li><code>DEFAULT_PRINTER_PDF_LABEL</code> - Default printer name for PDF labels</li>
 *   <li><code>DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT</code> - Silent print flag (yes/no)</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.commn.model.UserProperty
 * @see DemographicLabelPdf
 * @see io.github.carlos_emr.carlos.managers.SecurityInfoManager
 * @since 2026-01-24
 */
public class PrintDemoLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor for PrintDemoLabel2Action.
     *
     * <p>Initializes the action with Spring-injected dependencies via SpringUtils.
     * The HttpServletRequest and HttpServletResponse are obtained from ServletActionContext
     * following the Struts2 2Action pattern.</p>
     */
    public PrintDemoLabel2Action() {
    }

    /**
     * Executes the main action logic to generate and stream a PDF label for a patient.
     *
     * <p>This method performs the following workflow:</p>
     * <ol>
     *   <li>Validates security privilege for demographic data access</li>
     *   <li>Retrieves provider-specific printer settings from UserProperty</li>
     *   <li>Determines the appropriate label template (MRP or appointment provider)</li>
     *   <li>Loads the label template XML from configured path or default classpath resource</li>
     *   <li>Generates PDF using JasperReports with authorized patient demographic data</li>
     *   <li>Streams the PDF to the response output with appropriate headers</li>
     *   <li>Optionally injects JavaScript for automatic/silent printing</li>
     * </ol>
     *
     * <p>Request parameters:</p>
     * <ul>
     *   <li><code>demographic_no</code> - String the patient demographic number (required)</li>
     *   <li><code>appointment_no</code> - Integer the appointment number (optional, triggers
     *       appointment provider label if configured)</li>
     * </ul>
     *
     * <p>The method uses the provider's configured printer settings to determine whether
     * to enable automatic printing and which printer to use. If silent printing is enabled,
     * the PDF will include JavaScript to automatically print to the specified printer
     * without user interaction.</p>
     *
     * <p>Label template resolution follows this order:</p>
     * <ol>
     *   <li>If appointment_no provided and pdfLabelApptProvider configured: use appointment provider template</li>
     *   <li>Otherwise: use pdfLabelMRP template (default: ~/label.xml)</li>
     *   <li>Fallback: use bundled /oscar/oscarDemographic/label.xml resource</li>
     * </ol>
     *
     * <p>The generated PDF is streamed directly to the HttpServletResponse with
     * Content-Type: application/pdf and Content-Disposition: inline, causing
     * the browser to display the PDF inline rather than prompting for download.</p>
     *
     * @return String always returns NONE after streaming the PDF
     * @throws SecurityException if the current user lacks "_demographic" read privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    // FindSecBugs CRLF_INJECTION_LOGS: logged labelPath comes from CarlosProperties (pdfLabelMRP / pdfLabelApptProvider) or a user.home-derived default; trusted server config, not request input.
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN", "CRLF_INJECTION_LOGS"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input; logged labelPath is from trusted CARLOS properties/config, no attacker-controlled CR/LF")
    public String execute() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = DemographicLabelAccess.authorizeRead(loggedInInfo,
                request.getParameter("demographic_no"), response, securityInfoManager);
        if (demographicNo == null) {
            return NONE;
        }

        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT);
        if (prop != null) {
            if ("yes".equalsIgnoreCase(prop.getValue())) {
                silentPrint = true;
            }
        }
        String exportPdfJavascript = null;

        if (defaultPrinterName != null && !defaultPrinterName.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + io.github.carlos_emr.carlos.utility.SafeEncode.forJavaScript(defaultPrinterName) + "';";
            if (silentPrint == true) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("demo", demographicNo);

        Integer apptNo = null;
        try {
            apptNo = Integer.parseInt(request.getParameter("appointment_no"));
        } catch (NumberFormatException e) {
        }

        String defaultLabelPath = System.getProperty("user.home") + "/label.xml";
        String labelPath = CarlosProperties.getInstance().getProperty("pdfLabelMRP", defaultLabelPath);
        String apptProviderLabelPath = CarlosProperties.getInstance().getProperty("pdfLabelApptProvider", "");

        if (apptNo != null && !apptProviderLabelPath.isEmpty()) {
            parameters.put("appt", String.valueOf(apptNo));
            labelPath = apptProviderLabelPath;
        }

        InputStream ins = null;


        logger.debug("user home: " + System.getProperty("user.home"));
        try {
            ins = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(labelPath)));
            logger.debug("loading from :" + labelPath + " " + ins);
        } catch (FileNotFoundException | SecurityException ex1) {
            logger.warn("label xml file not found at " + labelPath + " using default instead", ex1);
        }
        if (ins == null) {
            try {
//                ServletContext context = getServlet().getServletContext();
                ins = getClass().getResourceAsStream("/oscar/oscarDemographic/label.xml");
                logger.debug("loading from : /oscar/oscarDemographic/label.xml " + ins);
            } catch (Exception ex1) {
                MiscUtils.getLogger().error("Error", ex1);
            }
        }

        DemographicLabelPdf.write(response, parameters, ins, exportPdfJavascript);
        // The response is complete; do not append a Struts result page.
        return NONE;
    }

}
