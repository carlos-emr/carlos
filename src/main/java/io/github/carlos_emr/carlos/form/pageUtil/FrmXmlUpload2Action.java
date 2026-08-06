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


package io.github.carlos_emr.carlos.form.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.eform.EFormExportZip;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FrmXmlUpload2Action extends ActionSupport implements UploadedFilesAware {
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final String ADMIN_SECURITY_OBJECT = "_admin";
    private static final String ADMIN_EFORM_SECURITY_OBJECT = "_admin.eform";
    private static final String WRITE_PRIVILEGE = "w";
    private static final String EFORM_IMPORT_FAILURE_KEY = "form.xmlUpload.failure";

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Processes a POSTed form/eForm XML archive upload.
     *
     * <p>Rejects non-POST requests with {@link #NONE} and a 405 response. Requires
     * {@code _admin.eform} or {@code _admin} write privilege; unauthorized callers get a
     * {@link SecurityException}, which the surrounding struts-form.xml mapping routes to
     * the security error view. A rejected upload (e.g. an invalid filename caught by
     * {@link #withUploadedFiles}) or a failed import returns {@link #ERROR} with the
     * failure reasons published as request attribute {@code actionErrors} for
     * {@code formXmlUpload.jsp} to render. On success the archive has been fully imported
     * and {@link #SUCCESS} is returned.</p>
     *
     * @return {@link #NONE} for a rejected HTTP method, {@link #ERROR} for a validation or
     *         import failure, or {@link #SUCCESS} once the archive is imported
     * @throws SecurityException if the caller lacks the required administrative privilege
     * @throws IllegalStateException if the servlet container's temp directory is unavailable
     * @throws IllegalArgumentException if the uploaded file path fails validation
     * @throws Exception if the legacy entry-by-entry import path fails
     */
    public String execute()
            throws Exception {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_EFORM_SECURITY_OBJECT, WRITE_PRIVILEGE, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, ADMIN_SECURITY_OBJECT, WRITE_PRIVILEGE, null)) {
            throw new SecurityException("missing required sec object (_admin.eform or _admin)");
        }
        if (uploadValidationError != null) {
            addActionError(uploadValidationError);
            return forwardActionErrors();
        }

        int BUFFER = 2048;

        // Temporary file handling
        File tmpFile = File.createTempFile("tmp", ".zip");
        tmpFile.deleteOnExit();

        // Get context of the temp directory, get the file path to the the temp directory
        ServletContext servletContext = ServletActionContext.getServletContext();

        // Validate the paths
        File safeDir = (File) servletContext.getAttribute("jakarta.servlet.context.tempdir"); // Use a safe directory
        
        if (safeDir == null) {
            throw new IllegalStateException("Temporary directory attribute is not set.");
        }
        
        File normalizedFile = file1.toPath().normalize().toFile();

        // Validate file path using PathValidationUtils
        try {
            normalizedFile = PathValidationUtils.validateExistingPath(normalizedFile, safeDir);
        } catch (SecurityException e) {
            throw new IllegalArgumentException("Invalid file path: " + normalizedFile.getPath());
        }

       try (InputStream is = new FileInputStream(normalizedFile);
            OutputStream fos = new FileOutputStream(tmpFile)) {
            byte[] data = new byte[BUFFER];
            int count;
            while ((count = is.read(data)) != -1) {
                fos.write(data, 0, count);
            }
        }

        // eForm export ZIPs contain eform.properties. Import them through the eForm
        // service so the template is persisted to the Manage eForms library; retain the
        // established entry-by-entry importer for legacy clinical form data ZIPs.
        try (ZipFile zf = new ZipFile(tmpFile)) {
            if (isEFormArchive(zf)) {
                List<String> errors;
                try (InputStream is = new FileInputStream(tmpFile)) {
                    errors = new EFormExportZip().importForm(is);
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    LOGGER.error("Unable to import eForm archive.", e);
                    addActionError(getEformImportFailureMessage());
                    return forwardActionErrors();
                }
                if (!errors.isEmpty()) {
                    errors.forEach(this::addActionError);
                    return forwardActionErrors();
                }
            } else {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream zis = zf.getInputStream(entry)) {
                        JDBCUtil.toDataBase(zis, entry.getName());
                    }
                }
            }
        }

        return SUCCESS;
    }

    /**
     * Publishes the current Struts action errors onto the request so the plain-JSP
     * error view ({@code formXmlUpload.jsp}, which has no Struts tag library) can
     * render them; {@code addActionError()} alone only populates the value stack.
     *
     * @return {@link #ERROR}
     */
    private String forwardActionErrors() {
        request.setAttribute("actionErrors", getActionErrors());
        return ERROR;
    }

    /**
     * Looks up the localized generic eForm import failure message from the
     * {@code oscarResources} bundle, matching the {@code fmt:message} lookups the
     * success page uses. Falls back to the English default if the key or bundle is
     * unavailable so a resource-loading gap never blocks the retry flow.
     */
    private String getEformImportFailureMessage() {
        try {
            return ResourceBundle.getBundle("oscarResources", request.getLocale())
                    .getString(EFORM_IMPORT_FAILURE_KEY);
        } catch (MissingResourceException e) {
            return "Unable to import eForm archive.";
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive match against the fixed ASCII eForm
    // export marker filename, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match against the fixed ASCII eForm export marker filename; not a security or authorization decision")
    private static boolean isEFormArchive(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            String entryName = entries.nextElement().getName().replace('\\', '/');
            if (entryName.equalsIgnoreCase("eform.properties")
                    || entryName.toLowerCase(Locale.ROOT).endsWith("/eform.properties")) {
                return true;
            }
        }
        return false;
    }

    private File file1; // Uploaded file
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file
    private String uploadValidationError;


    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.file1 = PathValidationUtils.validateUploadContent(uploaded.getContent());
            this.file1ContentType = uploaded.getContentType();
            try {
                this.file1FileName = PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
            } catch (FileValidationException e) {
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
                this.file1FileName = null;
            }
        }
    }

    // Setters and Getters for file upload properties
    public File getFile1() {
        return file1;
    }

    public void setFile1(File file1) {
        this.file1 = file1;
    }

    public String getFile1FileName() {
        return file1FileName;
    }

    public void setFile1FileName(String file1FileName) {
        this.file1FileName = file1FileName;
    }

    public String getFile1ContentType() {
        return file1ContentType;
    }

    public void setFile1ContentType(String file1ContentType) {
        this.file1ContentType = file1ContentType;
    }
}
