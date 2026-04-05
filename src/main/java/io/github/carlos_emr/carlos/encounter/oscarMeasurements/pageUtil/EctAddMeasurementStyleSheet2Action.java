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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctAddMeasurementStyleSheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static MeasurementCSSLocationDao dao = SpringUtils.getBean(MeasurementCSSLocationDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {


            ArrayList<String> messages = new ArrayList<String>();
            String contextPath = request.getContextPath();

            if (!saveFile(file, fileName)) {
                addActionError(getText("errors.fileNotAdded"));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return INPUT;
            } else {
                write2Database(fileName);
                String msg = getText("encounter.oscarMeasurement.msgAddedStyleSheet", fileName);
                messages.add(msg);
                request.setAttribute("messages", messages);
                return SUCCESS;
            }

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }

    /**
     * Save an uploaded file to the configured measurement CSS upload directory.
     *
     * @param file     File the uploaded temporary file from Struts2 file upload interceptor
     * @param fileName String the original filename of the uploaded file
     * @return boolean true if the file was saved successfully; false on failure or duplicate
     */
    public boolean saveFile(File file, String fileName) {
        boolean isAdded = true;

        try {
            // Validate and sanitize the filename first
            if (fileName == null || fileName.trim().isEmpty()) {
                throw new IllegalArgumentException("fileName cannot be null or empty");
            }
            
            // Sanitize filename to prevent path traversal - extract just the filename without any path
            String sanitizedFileName = FilenameUtils.getName(fileName);
            
            // Additional validation: ensure no directory traversal characters
            if (sanitizedFileName.contains("..") || sanitizedFileName.contains("/") || sanitizedFileName.contains("\\")) {
                MiscUtils.getLogger().error("Attempted path traversal detected in filename: " + fileName);
                throw new SecurityException("Invalid filename - path traversal detected");
            }
            
            // Check if the file already exists in the database using sanitized filename
            List<MeasurementCSSLocation> locs = dao.findByLocation(sanitizedFileName);
            if (!locs.isEmpty()) {
                return false;
            }

            // Retrieve the target directory from properties
            String uploadPath = CarlosProperties.getInstance().getProperty("oscarMeasurement_css_upload_path");
            
            if (uploadPath == null || uploadPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Upload path not configured");
            }

            // Create the upload directory if it doesn't exist
            File uploadDir = new File(uploadPath);
            uploadDir.mkdirs();
            
            // Create and validate the destination file using PathValidationUtils
            File destinationFile = PathValidationUtils.validatePath(sanitizedFileName, uploadDir);

            // Write the file to the validated destination
            try (FileInputStream fis = new FileInputStream(file)) {
                Files.copy(fis, destinationFile.toPath());
            }

        } catch (IOException e) {
            MiscUtils.getLogger().error("Error saving file", e);
            isAdded = false;
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Security error saving file", e);
            isAdded = false;
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().error("Invalid upload configuration or filename", e);
            isAdded = false;
        }

        return isAdded;
    }

    /**
     * Write to database
     *
     * @param fileName - the filename to store
     */
    private void write2Database(String fileName) {
        // Sanitize the filename before storing in database
        String sanitizedFileName = FilenameUtils.getName(fileName);
        
        MeasurementCSSLocation m = new MeasurementCSSLocation();
        m.setLocation(sanitizedFileName);
        dao.persist(m);
    }

    private File file;
    private String fileName; // Name of the uploaded file

    public File getFile() {
        return file;
    }

    @StrutsParameter
    public void setFile(File file) {
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    @StrutsParameter
    public void setFileFileName(String fileName) {
        this.fileName = fileName;
    }
}
