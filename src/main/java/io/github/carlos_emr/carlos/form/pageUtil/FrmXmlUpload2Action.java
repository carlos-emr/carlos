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

import org.apache.commons.io.FileUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.UploadedFileUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.JDBCUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FrmXmlUpload2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private String uploadValidationError;

    public String execute()
            throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }

        // Temporary file handling
        File tmpFile = File.createTempFile("tmp", ".zip");
        tmpFile.deleteOnExit();

        if (uploadValidationError != null) {
            throw new IllegalArgumentException(uploadValidationError);
        }
        if (file1 == null) {
            throw new IllegalArgumentException("Invalid file upload");
        }

        try {
            copyValidatedUploadToTemp(file1, tmpFile);

            // Unzip and process entries
            try (ZipFile zf = new ZipFile(tmpFile)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String safeEntryName;
                    try {
                        JDBCUtil.FormImportTarget importTarget = JDBCUtil.parseImportFileName(entryName);
                        safeEntryName = String.format("%s_%s_%s.xml", importTarget.formName(), importTarget.demographicNo(),
                                importTarget.timeStamp());
                    } catch (JDBCUtil.XmlImportException e) {
                        MiscUtils.getLogger().warn("Skipping invalid form import entry: {}", LogSanitizer.sanitize(entryName), e);
                        throw e;
                    }
                    try (InputStream zis = zf.getInputStream(entry)) {
                        JDBCUtil.toDataBase(zis, safeEntryName);
                    }
                }
            }
        } catch (JDBCUtil.XmlImportException e) {
            throw new ServletException("Error importing XML form data", e);
        }

        return SUCCESS;
    }

    private void copyValidatedUploadToTemp(File upload, File destination) throws IOException {
        File validatedUpload = PathValidationUtils.validateUpload(upload);
        FileUtils.copyFile(validatedUpload, destination);
    }

    private File file1; // Uploaded file
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file


    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            try {
                this.file1 = PathValidationUtils.validateUpload(UploadedFileUtils.getUploadedFile(uploaded));
                this.uploadValidationError = null;
            } catch (SecurityException e) {
                this.file1 = null;
                this.uploadValidationError = "Invalid file upload";
            }
            this.file1ContentType = uploaded.getContentType();
            this.file1FileName = uploaded.getOriginalName();
        }
    }

    // Setters and Getters for file upload properties
    public File getFile1() {
        return file1;
    }

    public void setFile1(File file1) {
        try {
            this.file1 = PathValidationUtils.validateUpload(file1);
            this.uploadValidationError = null;
        } catch (SecurityException e) {
            this.file1 = null;
            this.uploadValidationError = "Invalid file upload";
        }
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
