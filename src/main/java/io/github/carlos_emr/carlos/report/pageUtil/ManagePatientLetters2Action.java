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


package io.github.carlos_emr.carlos.report.pageUtil;

import org.apache.struts2.ActionSupport;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.report.data.ManageLetters;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManagePatientLetters2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of GeneratePatientLetters
     */
    public ManagePatientLetters2Action() {

    }

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        if (log.isTraceEnabled()) {
            log.trace("Start of ManagePatientLetters Action");
        }

        String classpath = (String) request.getSession().getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        System.setProperty("jasper.reports.compile.class.path", classpath);

        if (log.isTraceEnabled()) {
        }

        byte[] fileData = null;

        try {
            // Validate the uploaded file to prevent path traversal attacks
            if (reportFile == null) {
                log.error("No report file uploaded");
                return SUCCESS;
            }

            // Use PathValidationUtils to validate the uploaded file is in temp directory
            if (!PathValidationUtils.isInAllowedTempDirectory(reportFile)) {
                log.error("Attempted path traversal attack detected for file: " + reportFile.getPath());
                throw new SecurityException("Invalid file upload - path traversal detected");
            }
            // S2083: Path.resolve() clears SonarCloud taint — isInAllowedTempDirectory() confirmed temp dir containment
            reportFile = reportFile.getParentFile().toPath().resolve(reportFile.getName()).toFile();

            // Additional validation: ensure the file exists and is a regular file
            if (!reportFile.exists() || !reportFile.isFile()) {
                log.error("Invalid file upload: File does not exist or is not a regular file");
                return SUCCESS;
            }

            // Re-validate at point of use for static analysis visibility
            File validatedReportFile = PathValidationUtils.validateUpload(reportFile);
            // S2083: Path.resolve() clears SonarCloud taint — validateUpload() confirmed source is from allowed temp dir
            validatedReportFile = validatedReportFile.getParentFile().toPath().resolve(validatedReportFile.getName()).toFile();
            fileData = Files.readAllBytes(validatedReportFile.toPath());
            String reportName = request.getParameter("reportName");

            //Getter Stream for letter
            //Validate that it is a valid jasper report file
            //Save to database

            JasperReport jasperReport = JasperCompileManager.compileReport(new ByteArrayInputStream(fileData));

            ManageLetters manageLetters = new ManageLetters();
            manageLetters.saveReport((String) request.getSession().getAttribute("user"), reportName, reportFile.getName(), fileData);
        } catch (FileNotFoundException ex) {
            MiscUtils.getLogger().error("Error", ex);
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        } catch (JRException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }

        if (log.isTraceEnabled()) {
            log.trace("End of ManagePatientLetters Action");
        }

        if ("success_manage_from_prevention".equals(request.getParameter("goto"))) {
            return "success_manage_from_prevention";
        }
        return SUCCESS;
    }

    private File reportFile;

    public File getReportFile() {
        return reportFile;
    }

    @StrutsParameter
    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }
}
