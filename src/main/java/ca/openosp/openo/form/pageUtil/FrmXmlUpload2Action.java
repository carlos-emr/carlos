//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.form.pageUtil;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.util.JDBCUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FrmXmlUpload2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_form", "r", null)) {
            throw new SecurityException("missing required sec object (_form)");
        }

        int BUFFER = 2048;

        // Temporary file handling
        File tmpFile = File.createTempFile("tmp", ".zip");
        tmpFile.deleteOnExit();

        // Get context of the temp directory, get the file path to the the temp directory
        ServletContext servletContext = ServletActionContext.getServletContext();

        // Validate the paths
        File safeDir = (File) servletContext.getAttribute("javax.servlet.context.tempdir"); // Use a safe directory
        
        if (safeDir == null) {
            throw new IllegalStateException("Temporary directory attribute is not set.");
        }
        
        File normalizedFile = file1.toPath().normalize().toFile();

        // Validate file path using PathValidationUtils
        try {
            PathValidationUtils.validateExistingPath(normalizedFile, safeDir);
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
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file


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
