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

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.UploadedFileUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;

/**
 * Struts2 action that handles file upload submission for measurement CSS stylesheets.
 * Validates the uploaded file, stores it to the configured upload directory using
 * {@link PathValidationUtils}, and records the location in the database.
 * Form rendering is handled by {@link EctSetupAddMeasurementStyleSheet2Action}.
 *
 * @since 2004-03-12
 */
public class EctAddMeasurementStyleSheet2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static MeasurementCSSLocationDao dao = SpringUtils.getBean(MeasurementCSSLocationDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {


            ArrayList<String> messages = new ArrayList<String>();
            String validatedFileName;
            try {
                validatedFileName = PathValidationUtils.validateFileName(fileName);
            } catch (SecurityException e) {
                addActionError(getText("errors.fileNotAdded"));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return INPUT;
            }

            if (!saveFile(fileUpload, validatedFileName)) {
                addActionError(getText("errors.fileNotAdded"));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return INPUT;
            } else {
                write2Database(validatedFileName);
                String msg = getText("encounter.oscarMeasurement.msgAddedStyleSheet", validatedFileName);
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
     * @param fileUpload UploadedFile the file from Struts2 file upload interceptor
     * @param fileName String the original filename of the uploaded file
     * @return boolean true if the file was saved successfully; false on failure or duplicate
     */
    public boolean saveFile(UploadedFile fileUpload, String fileName) {
        boolean isAdded = true;

        try {
            if (fileUpload == null) {
                MiscUtils.getLogger().debug("No file provided for measurement stylesheet upload");
                return false;
            }
            File validatedUpload = PathValidationUtils.validateUpload(UploadedFileUtils.getUploadedFile(fileUpload));

            String sanitizedFileName = PathValidationUtils.validateFileName(fileName);
            
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
            Files.createDirectories(uploadDir.toPath());
            
            // Create and validate the destination file using PathValidationUtils
            File destinationFile = PathValidationUtils.validateUserFilePath(sanitizedFileName, uploadDir);

            // Write the file to the validated destination
            Files.copy(validatedUpload.toPath(), destinationFile.toPath()); // codeql[java/path-injection] -- source and destination are canonicalized and allowlist validated.

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
     * Sanitizes the filename and persists a new {@link MeasurementCSSLocation} record.
     *
     * @param fileName String the raw filename to sanitize and store as a CSS location record
     */
    private void write2Database(String fileName) {
        String sanitizedFileName = PathValidationUtils.validateFileName(fileName);
        
        MeasurementCSSLocation m = new MeasurementCSSLocation();
        m.setLocation(sanitizedFileName);
        dao.persist(m);
    }

    private UploadedFile fileUpload;
    private String fileName; // Name of the uploaded file

    public File getFile() {
        return UploadedFileUtils.getUploadedFileOrNull(fileUpload);
    }

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles == null) {
            return;
        }
        for (UploadedFile uploaded : uploadedFiles) {
            if ("file".equals(uploaded.getInputName())) {
                this.fileUpload = uploaded;
                this.fileName = uploaded.getOriginalName();
                return;
            }
        }
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileFileName(String fileName) {
        this.fileName = fileName;
    }

}
