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


package ca.openosp.openo.encounter.oscarMeasurements.pageUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import ca.openosp.openo.commn.dao.MeasurementCSSLocationDao;
import ca.openosp.openo.commn.model.MeasurementCSSLocation;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;


import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
                response.sendRedirect(contextPath + "/oscarEncounter/oscarMeasurements/AddMeasurementStyleSheet.jsp");
                return NONE;
            } else {
                write2Database(fileName);
                String msg = getText("oscarEncounter.oscarMeasurement.msgAddedStyleSheet", fileName);
                messages.add(msg);
                request.setAttribute("messages", messages);
                return SUCCESS;
            }

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }

    /**
     * Save a Jakarta FormFile to a preconfigured place.
     *
     * @param file
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
            String uploadPath = OscarProperties.getInstance().getProperty("oscarMeasurement_css_upload_path");
            
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
