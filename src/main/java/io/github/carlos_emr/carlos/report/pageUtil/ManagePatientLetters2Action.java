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
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.FileValidationException;
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
import java.util.List;

/**
 * Uploads a JasperReports letter template ({@code .jrxml}) for the Generate Letters workflow.
 *
 * <p>Implements {@link UploadedFilesAware}: under Struts 7 the {@code actionFileUpload}
 * interceptor delivers multipart files only through {@link #withUploadedFiles(List)}; it does not
 * call {@code setReportFile}/{@code setReportFileFileName} setters by naming convention.</p>
 *
 * <p>Requires {@code _report} read, matching the other letter actions, and POST (the upload
 * persists a template).</p>
 *
 * @since 2026-02-03
 */
public class ManagePatientLetters2Action extends ActionSupport implements UploadedFilesAware {
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

        // Saving a template is a mutation: refuse GET/HEAD before touching the upload or the DAO.
        if (!"POST".equals(request.getMethod())) {
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                log.warn("Unable to send 405 for non-POST letter template upload", e);
            }
            return NONE;
        }

        if (log.isTraceEnabled()) {
            log.trace("Start of ManagePatientLetters Action");
        }

        configureJasperCompileClasspath(request);

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

            // Additional validation: ensure the file exists and is a regular file
            if (!reportFile.exists() || !reportFile.isFile()) {
                log.error("Invalid file upload: File does not exist or is not a regular file");
                return SUCCESS;
            }

            // Re-validate at point of use for static analysis visibility
            File validatedReportFile = PathValidationUtils.validateUpload(reportFile);
            fileData = Files.readAllBytes(validatedReportFile.toPath());
            String reportName = request.getParameter("reportName");

            //Getter Stream for letter
            //Validate that it is a valid jasper report file
            //Save to database

            JasperReport jasperReport = JasperCompileManager.compileReport(new ByteArrayInputStream(fileData));

            ManageLetters manageLetters = new ManageLetters();
            manageLetters.saveReport((String) request.getSession().getAttribute("user"), reportName, resolveStoredFileName(), fileData); // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): reads authenticated provider from own session (set by Login2Action post-auth)
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

    /**
     * Passes the JSP compiler classpath to JasperReports when the container publishes one.
     *
     * <p>Tomcat publishes it as the {@code org.apache.catalina.jsp_classpath} context attribute.
     * Other containers (and unit tests) may not, and {@link System#setProperty(String, String)}
     * rejects a {@code null} value with a {@code NullPointerException}; in that case the property
     * is left alone and JasperReports uses its own classpath.</p>
     *
     * @param request HttpServletRequest the current request
     */
    static void configureJasperCompileClasspath(HttpServletRequest request) {
        Object classpath = request.getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
        if (classpath instanceof String && !((String) classpath).isEmpty()) {
            System.setProperty("jasper.reports.compile.class.path", (String) classpath);
        }
    }

    /**
     * Returns the name to store for the uploaded template.
     *
     * <p>Struts hands the action the multipart temp file, whose name is a server-generated
     * {@code upload_*.tmp}; storing that was issue #3963. The name the user chose arrives with the
     * upload in {@link #withUploadedFiles(List)}. That value is client-controlled and is
     * later listed on Manage Letters and echoed into a {@code Content-Disposition} header by
     * {@code DownloadPatientLetters2Action}, so it is reduced to a safe basename with
     * {@link PathValidationUtils#validateFileName(String)} (path stripped; only
     * {@code [A-Za-z0-9._-]} kept) and capped at the {@code report_letters.file_name} column width.</p>
     *
     * @return String the sanitized original file name, or {@link #FALLBACK_FILE_NAME} when the
     *         browser sent none or it cannot be made safe
     */
    String resolveStoredFileName() {
        if (reportFileFileName == null || reportFileFileName.trim().isEmpty()) {
            return FALLBACK_FILE_NAME;
        }
        try {
            String safeName = PathValidationUtils.validateFileName(reportFileFileName);
            if (safeName.length() > MAX_FILE_NAME_LENGTH) {
                safeName = safeName.substring(safeName.length() - MAX_FILE_NAME_LENGTH);
            }
            return safeName;
        } catch (FileValidationException e) {
            log.warn("Uploaded letter template name rejected; storing fallback name");
            return FALLBACK_FILE_NAME;
        }
    }

    /** Stored when the upload carried no usable original file name. */
    static final String FALLBACK_FILE_NAME = "letter-template.jrxml";

    /** Width of {@code report_letters.file_name}. */
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private File reportFile;
    private String reportFileFileName;

    public File getReportFile() {
        return reportFile;
    }

    /**
     * Sets the uploaded template file. Deliberately not a {@code @StrutsParameter}: the file comes
     * only from {@link #withUploadedFiles(List)}, never from a request parameter naming a path.
     *
     * @param reportFile File the multipart temp file
     */
    public void setReportFile(File reportFile) {
        this.reportFile = reportFile;
    }

    /**
     * Receives the multipart upload from Struts 7's {@code ActionFileUploadInterceptor}.
     * Only the {@code reportFile} input is accepted; its temp file is checked against the allowed
     * upload roots here and again in {@link #execute()} before it is read.
     *
     * @param uploadedFiles List&lt;UploadedFile&gt; the files in the request; may be {@code null}
     */
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles == null) {
            return;
        }
        for (UploadedFile uploaded : uploadedFiles) {
            if (uploaded != null && "reportFile".equals(uploaded.getInputName())) {
                this.reportFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
                this.reportFileFileName = uploaded.getOriginalName();
                return;
            }
        }
    }

    public String getReportFileFileName() {
        return reportFileFileName;
    }

    /**
     * Sets the browser-supplied original name of {@code reportFile}. Normally populated by
     * {@link #withUploadedFiles(List)}; not a {@code @StrutsParameter}, so a plain request
     * parameter cannot replace the name that arrived with the file.
     *
     * @param reportFileFileName String the untrusted client file name
     */
    public void setReportFileFileName(String reportFileFileName) {
        this.reportFileFileName = reportFileFileName;
    }
}
