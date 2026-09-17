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
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSafe;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for generating and printing patient demographic chart labels in PDF format.
 *
 * This action handles the creation of various types of chart labels for patient records,
 * including standard chart labels and specialized labels such as sexual health clinic labels.
 * The generated PDFs can be automatically printed to a configured printer with optional
 * silent printing mode.
 *
 * The action supports:
 * <ul>
 *   <li>Multiple label types via configurable XML templates (ChartLabel, SexualHealthClinicLabel)</li>
 *   <li>User-specific printer configuration and silent print mode preferences</li>
 *   <li>Program-based context information for patient labels</li>
 *   <li>PDF generation using JasperReports with custom JavaScript for print automation</li>
 *   <li>Template loading from user home directory or classpath fallback</li>
 * </ul>
 *
 * Healthcare Context:
 * Chart labels are physical labels printed for patient files containing demographic information,
 * current program assignment, and other identifying information used in paper-based medical
 * record management within healthcare facilities.
 *
 * Security:
 * Requires "_demographic" read privilege to access patient demographic information.
 *
 * @see io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO
 * @see io.github.carlos_emr.carlos.managers.ProgramManager2
 * @see io.github.carlos_emr.carlos.managers.SecurityInfoManager
 * @see DemographicLabelPdf
 * @since 2026-01-24
 */
public class PrintDemoChartLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Default constructor for PrintDemoChartLabel2Action.
     *
     * Initializes the action with default state. Spring and Struts2 dependencies
     * are injected via field initialization and ServletActionContext.
     */
    public PrintDemoChartLabel2Action() {
    }

    /**
     * Main action execution method that generates and streams a patient chart label PDF.
     *
     * This method performs the following operations:
     * <ol>
     *   <li>Validates user has "_demographic" read privilege</li>
     *   <li>Retrieves user printer preferences (default printer name and silent print mode)</li>
     *   <li>Determines which label template to use (ChartLabel or SexualHealthClinicLabel)</li>
     *   <li>Loads the label template XML from user home directory or classpath</li>
     *   <li>Gathers demographic and program context parameters</li>
     *   <li>Generates PDF using JasperReports</li>
     *   <li>Streams PDF to response with optional JavaScript for automatic printing</li>
     * </ol>
     *
     * Request Parameters:
     * <ul>
     *   <li>demographic_no (String) - The patient demographic identifier to generate label for</li>
     *   <li>labelName (String, optional) - The type of label to generate (e.g., "ChartLabel", "SexualHealthClinicLabel")</li>
     * </ul>
     *
     * User Properties Consulted:
     * <ul>
     *   <li>DEFAULT_PRINTER_PDF_CHART_LABEL - Configured printer name for automatic printing</li>
     *   <li>DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT - Whether to print silently without dialog ("yes"/"no")</li>
     * </ul>
     *
     * Template Resolution:
     * First attempts to load template from user's home directory, then falls back to
     * classpath resource at /oscar/oscarDemographic/{labelFile}.
     *
     * PDF Generation:
     * The generated PDF is streamed directly to the response output stream with content
     * type "application/pdf" and inline disposition. If a default printer is configured,
     * embedded JavaScript in the PDF will trigger automatic printing on open.
     *
     * @return String ActionSupport result constant, always returns NONE for direct PDF responses
     * @throws SecurityException if user lacks "_demographic" read privilege
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String demographicNo = DemographicLabelAccess.authorizeRead(loggedInInfo,
                request.getParameter("demographic_no"), response, securityInfoManager);
        if (demographicNo == null) {
            return NONE;
        }

        Provider provider = loggedInInfo.getLoggedInProvider();
        String curUser_no = loggedInInfo.getLoggedInProviderNo();
        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        Boolean silentPrint = false;
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL_SILENT_PRINT);
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
        Map<String, String> nameToFileMap = new HashMap<String, String>();
        nameToFileMap.put("ChartLabel", "Chartlabel.xml");
        nameToFileMap.put("SexualHealthClinicLabel", "SexualHealthClinicLabel.xml");

        String labelFile = nameToFileMap.get("ChartLabel");

        if (request.getParameter("labelName") != null) {
            labelFile = nameToFileMap.get(request.getParameter("labelName"));
        }

        if (labelFile == null) {
            logger.warn("requested invalid label : {}", LogSafe.sanitize(request.getParameter("labelName"))); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown label template");
            return NONE;
        }


        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("demo", demographicNo);

        ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);

        parameters.put("program", "N/A");
        ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, provider.getProviderNo());
        if (pp != null) {
            Program program = programManager2.getProgram(loggedInInfo, pp.getProgramId().intValue());
            if (program != null) {
                parameters.put("program", program.getName());
            }
        }

        InputStream ins = null;

        try {
            ins = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(System.getProperty("user.home") + File.separator + labelFile)));
        } catch (FileNotFoundException | SecurityException ex) {
            logger.debug("Chart label override absent; using bundled template");
        }
        if (ins == null) {
            ins = getClass().getResourceAsStream("/oscar/oscarDemographic/" + labelFile);
        }

        DemographicLabelPdf.write(response, parameters, ins, exportPdfJavascript);
        return NONE;
    }

}
