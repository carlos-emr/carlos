/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.web;

import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.ActionSupport;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.casemgmt.model.ClientImage;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class ClientImage2Action extends ActionSupport implements UploadedFilesAware {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private File clientImage;
    private String clientImageFileName;

    private static Logger log = MiscUtils.getLogger();

    private ClientImageManager clientImageManager = SpringUtils.getBean(ClientImageManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // Execute on struts action call — routes to saveImage or deleteImage based on method parameter
    public String execute() {
        validateWritePrivilege();

        String method = request.getParameter("method");
        if ("deleteImage".equals(method)) {
            return doDeleteImage();
        }
        return doSaveImage();
    }

    public String saveImage() {
        validateWritePrivilege();
        return doSaveImage();
    }

    private String doSaveImage() {
        HttpSession session = request.getSession(true);
        String id = (String) session.getAttribute("clientId");

        log.info("client image upload requested");

        if (id == null || id.isBlank()) {
            addActionError("No client selected.");
            return ERROR;
        }

        if (clientImage == null || clientImageFileName == null || clientImageFileName.isBlank()) {
            addActionError("Please select an image to upload.");
            return ERROR;
        }

        // Get file extension from original filename
        String type = null;
        if (clientImageFileName.contains(".")) {
            type = ClientImage.getRenderableImageType(clientImageFileName.substring(clientImageFileName.lastIndexOf('.') + 1));
        }

        log.info("extension = " + type);

        if (!"jpeg".equals(type) && !"gif".equals(type)) {
            addActionError("Only GIF and JPG image types are allowed for the client photo.");
            return ERROR;
        }

        // Ensure that the upload directory is correct and create a new image object that will be saved to the client
        try {
            // Validate that the uploaded file is in an allowed temp directory
            if (!PathValidationUtils.isInAllowedTempDirectory(clientImage)) {
                throw new IllegalArgumentException("Invalid file path: " + clientImage.getPath());
            }

            // Re-validate at point of use for static analysis visibility
            File validatedImage = PathValidationUtils.validateUpload(clientImage);
            byte[] imageData = Files.readAllBytes(validatedImage.toPath());

            ClientImage clientImageObj = new ClientImage();
            clientImageObj.setDemographic_no(Integer.parseInt(id));
            clientImageObj.setImage_data(imageData);
            clientImageObj.setImage_type(type);

            clientImageManager.saveClientImage(clientImageObj);

        } catch (Exception e) {
            log.error("Error saving image", e);
            addActionError("Error saving image.");
            return ERROR;
        }

        request.setAttribute("success", true);
        return SUCCESS;
    }

    public String deleteImage() {
        validateWritePrivilege();
        return doDeleteImage();
    }

    private String doDeleteImage() {
        HttpSession session = request.getSession(true);
        String id = (String) session.getAttribute("clientId");

        log.info("client image delete requested");

        if (id == null || id.isEmpty()) {
            log.error("No clientId found in session for image delete");
            addActionError("No client selected.");
            return ERROR;
        }

        try {
            clientImageManager.deleteClientImage(Integer.parseInt(id));
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            LogAction.addLogSynchronous(loggedInInfo, "ClientImage2Action.deleteImage", "clientId=" + id);
        } catch (Exception e) {
            log.error("Error deleting image", e);
            addActionError("Error deleting image.");
            return ERROR;
        }

        request.setAttribute("success", true);
        return SUCCESS;
    }

    /**
     * Validates that the current session has demographic write access before
     * allowing client photo changes.
     *
     * @throws SecurityException if the user session is invalid or lacks
     *                           {@code _demographic} write privileges
     */
    private void validateWritePrivilege() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("User session is not valid");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required security object (_demographic)");
        }
    }

    /**
     * Receives uploaded files from the Struts 7 multipart upload lifecycle and
     * stores the first uploaded file reference for later validation and processing.
     *
     * @param uploadedFiles List<UploadedFile> the uploaded files supplied by Struts
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.clientImage = new File(uploaded.getAbsolutePath());
            this.clientImageFileName = uploaded.getOriginalName();
        }
    }

    @StrutsParameter
    public void setClientImage(File clientImage) {
        this.clientImage = clientImage; 
    }

    @StrutsParameter
    public void setClientImageFileName(String name) { 
        this.clientImageFileName = name; 
    }
}
