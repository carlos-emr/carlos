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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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
            String contextPath = request.getContextPath();

            if (uploadValidationError != null) {
                addActionError(uploadValidationError);
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return INPUT;
            }

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
            throw new SecurityException("missing required sec object (_admin)");
        }
    }

    /**
     * Save an uploaded file to the configured measurement CSS upload directory.
     *
     * @param file     File the uploaded temporary file from Struts2 file upload interceptor
     * @param fileName String the original filename of the uploaded file
     * @return boolean true if the file was saved successfully; false on failure or duplicate
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public boolean saveFile(File file, String fileName) {
        boolean isAdded = true;

        try {
            if (file == null) {
                MiscUtils.getLogger().debug("No file provided for measurement stylesheet upload");
                return false;
            }

            String sanitizedFileName = PathValidationUtils.validateStrictFileName(fileName);
            
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
            
            File destinationFile = PathValidationUtils.validateUpload(file, sanitizedFileName, uploadDir);

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
     * Sanitizes the filename and persists a new {@link MeasurementCSSLocation} record.
     *
     * @param fileName String the raw filename to sanitize and store as a CSS location record
     */
    private void write2Database(String fileName) {
        // Sanitize the filename before storing in database
        String sanitizedFileName = PathValidationUtils.validateStrictFileName(fileName);
        
        MeasurementCSSLocation m = new MeasurementCSSLocation();
        m.setLocation(sanitizedFileName);
        dao.persist(m);
    }

    private File file;
    private String fileName; // Name of the uploaded file
    private String uploadValidationError;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            try {
                this.file = PathValidationUtils.validateUploadContent(uploaded.getContent());
            } catch (SecurityException e) {
                this.file = null;
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
                this.fileName = null;
                return;
            }
            try {
                this.fileName = PathValidationUtils.validateStrictFileName(uploaded.getOriginalName());
            } catch (FileValidationException e) {
                this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
                this.fileName = null;
            }
        }
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileFileName(String fileName) {
        this.fileName = fileName;
    }
}
