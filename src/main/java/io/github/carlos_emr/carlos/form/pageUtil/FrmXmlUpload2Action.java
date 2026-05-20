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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import java.nio.file.Files;
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

        int BUFFER = 2048;

        // Temporary file handling
        File tmpFile = File.createTempFile("tmp", ".zip");
        tmpFile.deleteOnExit();

        if (uploadValidationError != null) {
            throw new IllegalArgumentException(uploadValidationError);
        }
        if (file1Upload == null) {
            throw new IllegalArgumentException("Invalid file upload");
        }

        try (InputStream is = file1Upload.openStream();
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
                try (InputStream zis = zf.getInputStream(entry)) {
                    JDBCUtil.toDataBase(zis, entry.getName());
                }
            }
        }

        return SUCCESS;
    }

    private File file1; // Uploaded file
    private ValidatedUpload file1Upload;
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file


    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            try {
                this.file1Upload = ValidatedUpload.from(uploaded);
                this.file1 = this.file1Upload.file();
                this.uploadValidationError = null;
            } catch (SecurityException e) {
                this.file1 = null;
                this.file1Upload = null;
                this.uploadValidationError = "Invalid file upload";
            }
            this.file1ContentType = uploaded.getContentType();
            this.file1FileName = uploaded.getOriginalName();
        }
    }

    private static final class ValidatedUpload {
        private final File file;

        private ValidatedUpload(File file) {
            this.file = file;
        }

        private static ValidatedUpload from(UploadedFile uploaded) {
            return from(UploadedFileUtils.getUploadedFile(uploaded));
        }

        private static ValidatedUpload from(File uploadFile) {
            return new ValidatedUpload(PathValidationUtils.validateUpload(uploadFile));
        }

        private File file() {
            return file;
        }

        private InputStream openStream() throws IOException {
            return Files.newInputStream(file.toPath());
        }
    }

    // Setters and Getters for file upload properties
    public File getFile1() {
        return file1;
    }

    public void setFile1(File file1) {
        this.file1Upload = ValidatedUpload.from(file1);
        this.file1 = this.file1Upload.file();
        this.uploadValidationError = null;
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
