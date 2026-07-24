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

package io.github.carlos_emr.carlos.eform.upload;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.SafeEncode;

public class UploadEFormAttachment2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_edoc", "w", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }
        if (uploadValidationError != null) {
            writeError("Invalid filename");
            return NONE;
        }
        // This action has NO uploaded-file binding, so the attachment bytes were never stored: the
        // previous implementation persisted Document/CtlDocument rows and reported "Uploaded
        // Successfully" for a file that was never saved, leaving orphan document metadata. Fail honestly
        // instead of creating orphan rows. The route (eform/eFormAttachmentForm) currently has no caller;
        // a real implementation must add a validated multipart File field and store the bytes via
        // PathValidationUtils.validateUpload before persisting any metadata.
        MiscUtils.getLogger().warn("eForm attachment upload invoked but is not implemented (no file storage); rejecting without persisting metadata");
        response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        writeError("eForm attachment upload is not available.");
        return NONE;
    }
    private String uploadFileName = null;
    private String uploadValidationError;

    public String getUploadFileName() {
        return uploadFileName;
    }

    @StrutsParameter
    public void setUploadFileName(String uploadFileName) {
        try {
            this.uploadFileName = PathValidationUtils.validateStrictFileName(uploadFileName);
        } catch (FileValidationException e) {
            this.uploadValidationError = PathValidationUtils.INVALID_FILENAME_MESSAGE;
            this.uploadFileName = null;
        }
    }

    private void writeError(String message) {
        String errorMsg = "<div id=\"error\">error</div><div id=\"message\">" + SafeEncode.forHtmlContent(message) + "</div>";
        try {
            response.getOutputStream().write(errorMsg.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (IOException e1) {
            // Client went away before the error body could be written; nothing more to do but record it.
            MiscUtils.getLogger().debug("Could not write eForm attachment error response (client likely disconnected)");
        }
    }
}
