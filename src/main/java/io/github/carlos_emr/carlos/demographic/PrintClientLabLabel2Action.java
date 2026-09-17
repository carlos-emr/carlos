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
import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Streams the client lab label for a patient the provider may read.
 * Clinic template overrides take precedence over the bundled JasperReports template;
 * unavailable overrides fall back to the bundle. Printer settings belong to the
 * logged-in provider, and missing settings leave printing interactive.
 */
public class PrintClientLabLabel2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public PrintClientLabLabel2Action() {
    }

    /**
     * Checks patient-specific demographic read access and validates demographic_no
     * before generating the PDF. Authorized requests with invalid identifiers return
     * HTTP 400; access denial takes precedence. Report
     * generation failures return HTTP 500 before any successful PDF output.
     *
     * @return NONE because this action completes the response directly
     * @throws SecurityException if the provider lacks patient demographic read access
     * @throws IOException if the template or response stream cannot be read or written
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path derived from trusted configuration/constant/DB value, not user-controllable input")
    @Override
    public String execute() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = DemographicLabelAccess.authorizeRead(loggedInInfo,
                request.getParameter("demographic_no"), response, securityInfoManager);
        if (demographicNo == null) {
            return NONE;
        }

        UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty prop;
        String defaultPrinterName = "";
        prop = propertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL);
        if (prop != null) {
            defaultPrinterName = prop.getValue();
        }
        prop = propertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL_SILENT_PRINT);
        boolean silentPrint = prop != null && "yes".equalsIgnoreCase(prop.getValue());
        String exportPdfJavascript = null;

        if (defaultPrinterName != null && !defaultPrinterName.isEmpty()) {
            exportPdfJavascript = "var params = this.getPrintParams();"
                    + "params.pageHandling=params.constants.handling.none;"
                    + "params.printerName='" + io.github.carlos_emr.carlos.utility.SafeEncode.forJavaScript(defaultPrinterName) + "';";
            if (silentPrint) {
                exportPdfJavascript += "params.interactive=params.constants.interactionLevel.silent;";
            }
            exportPdfJavascript += "this.print(params);";
        }
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("demo", demographicNo);

        InputStream ins = null;
        try {
            File file = PathValidationUtils.resolveTrustedPath(new File(System.getProperty("user.home") + "/ClientLabLabel.xml"));
            ins = new FileInputStream(file);
        } catch (FileNotFoundException | SecurityException ex) {
            logger.debug("Client lab label override unavailable; using bundled template");
        }
        if (ins == null) {
            ins = getClass().getResourceAsStream("/oscar/oscarDemographic/ClientLabLabel.xml");
        }
        DemographicLabelPdf.write(response, parameters, ins, exportPdfJavascript);
        return NONE;
    }

}
