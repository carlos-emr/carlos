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
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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

    public String execute()
            throws ServletException, IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, "_form", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_form)");
        }

        int BUFFER = 2048;

        if (file1 == null) {
            return reportImportError("Select a form XML archive to import.");
        }

        File normalizedFile;
        try {
            normalizedFile = PathValidationUtils.validateUpload(file1);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn(
                    "Rejected form XML archive path {}",
                    LogSafe.sanitize(file1.getPath()), e);
            return reportImportError("Unable to import form data. Check the uploaded archive and try again.");
        }

        // Temporary file handling
        File tmpFile = File.createTempFile("tmp", ".zip");
        try {
            try (InputStream is = new FileInputStream(normalizedFile);
                 OutputStream fos = new FileOutputStream(tmpFile)) {
                byte[] data = new byte[BUFFER];
                int count;
                while ((count = is.read(data)) != -1) {
                    fos.write(data, 0, count);
                }
            }

            // Unzip and process entries
            try (ZipFile zf = new ZipFile(tmpFile)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    JDBCUtil.FormImportTarget target;
                    try {
                        target = JDBCUtil.parseImportFileName(entry.getName());
                    } catch (JDBCUtil.XmlImportException e) {
                        MiscUtils.getLogger().warn(
                                "Rejected form XML import entry {}",
                                LogSafe.sanitize(entry.getName()), e);
                        return reportImportError("Unable to import form data. Check the uploaded archive and try again.");
                    }
                    if (!securityInfoManager.hasPrivilege(
                            loggedInInfo, "_form", SecurityInfoManager.WRITE, target.demographicNo())) {
                        MiscUtils.getLogger().warn(
                                "Rejected form XML import entry {} due to missing demographic form write privilege",
                                LogSafe.sanitize(entry.getName()));
                        return reportImportError("Unable to import form data. Check the uploaded archive and try again.");
                    }
                    try (InputStream zis = zf.getInputStream(entry)) {
                        JDBCUtil.toDataBase(zis, entry.getName());
                    } catch (JDBCUtil.XmlImportException e) {
                        MiscUtils.getLogger().warn(
                                "Rejected form XML import entry {}",
                                LogSafe.sanitize(entry.getName()), e);
                        return reportImportError("Unable to import form data. Check the uploaded archive and try again.");
                    }
                }
            }
        } catch (IOException e) {
            MiscUtils.getLogger().warn(
                    "Unable to read form XML archive {}",
                    LogSafe.sanitize(file1FileName), e);
            return reportImportError("Unable to import form data. Check the uploaded archive and try again.");
        } finally {
            if (tmpFile.exists() && !tmpFile.delete()) {
                tmpFile.deleteOnExit();
            }
        }

        return SUCCESS;
    }

    private String reportImportError(String message) {
        addActionError(message);
        request.setAttribute("actionErrors", List.of(message));
        return SUCCESS;
    }

    private File file1; // Uploaded file
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file


    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.file1 = new File(uploaded.getAbsolutePath());
            this.file1ContentType = uploaded.getContentType();
            this.file1FileName = uploaded.getOriginalName();
        }
    }

    // Setters and Getters for file upload properties
    public File getFile1() {
        return file1;
    }

    public String getFile1FileName() {
        return file1FileName;
    }

    @StrutsParameter
    public void setFile1FileName(String file1FileName) {
        this.file1FileName = file1FileName;
    }

    public String getFile1ContentType() {
        return file1ContentType;
    }

    @StrutsParameter
    public void setFile1ContentType(String file1ContentType) {
        this.file1ContentType = file1ContentType;
    }
}
