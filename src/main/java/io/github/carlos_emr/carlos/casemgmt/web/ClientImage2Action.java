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
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Saves and clears a patient's photo for the Client Image Manager popup ({@code uploadimage.jsp}).
 *
 * <p>The patient is the {@code demographicNo} the popup's forms post, and the login's write access to
 * that patient is checked before anything else touches it. The session's {@code clientId} is never
 * read: it held whichever popup was opened last, so with two patients' popups open Clear Photo in the
 * first cleared the second patient's photo (issue #4546).</p>
 */
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
        if (!isPostRequest()) return NONE;

        String method = request.getParameter("method");
        if ("deleteImage".equals(method)) {
            return doDeleteImage();
        }
        return doSaveImage();
    }

    public String saveImage() {
        validateWritePrivilege();
        if (!isPostRequest()) return NONE;
        return doSaveImage();
    }

    private String doSaveImage() {
        Integer demographicNo = postedDemographicNo();

        log.info("client image upload requested");

        if (demographicNo == null) {
            addActionError("No client selected.");
            return ERROR;
        }
        validatePatientWriteAccess(demographicNo);

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
            clientImageObj.setDemographic_no(demographicNo);
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
        if (!isPostRequest()) return NONE;
        return doDeleteImage();
    }

    private String doDeleteImage() {
        Integer demographicNo = postedDemographicNo();

        log.info("client image delete requested");

        if (demographicNo == null) {
            log.warn("client image delete requested without a valid patient number");
            addActionError("No client selected.");
            return ERROR;
        }
        validatePatientWriteAccess(demographicNo);

        try {
            clientImageManager.deleteClientImage(demographicNo);
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            LogAction.addLogSynchronous(loggedInInfo, "ClientImage2Action.deleteImage", "clientId=" + demographicNo);
        } catch (Exception e) {
            log.error("Error deleting image", e);
            addActionError("Error deleting image.");
            return ERROR;
        }

        request.setAttribute("success", true);
        return SUCCESS;
    }

    /** Refuses photo mutations from safe-method requests, including direct action entry points. */
    private boolean isPostRequest() {
        if ("POST".equals(request.getMethod())) return true;
        response.setHeader("Allow", "POST");
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return false;
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
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }

    /**
     * Reads the patient the Client Image Manager was opened for, which both of its forms post as
     * {@code demographicNo}. A missing, repeated or non-numeric value names no patient: the action
     * must not guess one, and the session's {@code clientId} is deliberately not a fallback.
     *
     * @return the posted demographic number, or {@code null} when the request does not name exactly
     *         one positive integer patient number
     */
    private Integer postedDemographicNo() {
        String[] values = request.getParameterValues("demographicNo");
        if (values == null || values.length != 1 || values[0] == null || !values[0].matches("[1-9]\\d{0,9}")) {
            return null;
        }
        try {
            return Integer.valueOf(values[0]);
        } catch (NumberFormatException _) {
            // Ten digits above Integer.MAX_VALUE.
            return null;
        }
    }

    /**
     * Checks the login may change this patient's record: {@code _demographic} write evaluated for the
     * patient (a patient-specific override takes priority over the role's general right) and the
     * patient-record access check that honours patient locks. Runs before the photo is read or written.
     *
     * @param demographicNo the posted patient number
     * @throws SecurityException if either check refuses
     */
    private void validatePatientWriteAccess(Integer demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", String.valueOf(demographicNo))) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        if (!securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, demographicNo)) {
            throw new SecurityException("Unauthorized access to patient record");
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
