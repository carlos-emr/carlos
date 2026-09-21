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


package io.github.carlos_emr.carlos.eform.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.ScheduleNav;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DelImage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an HTTP method constant; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an HTTP method constant; not a security or authorization decision")
    public String execute() throws IOException {

        // CSRFGuard validates POST/PUT/DELETE/PATCH, not GET/HEAD, so without this
        // guard an authenticated _eform writer could delete an image via a
        // token-less GET (e.g. a forged <img src>). Checked before privilege and
        // before any file operation, same shape as the sibling DelEForm2Action.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        String imgname = request.getParameter("filename");
        
        // Validate input parameter
        if (imgname == null || imgname.trim().isEmpty()) {
            return ERROR;
        }
        
        // Use FilenameUtils.getName to extract just the filename, removing any path components
        String sanitizedFilename = FilenameUtils.getName(imgname);
        
        String imgpath = CarlosProperties.getInstance().getEformImageDirectory();
        
        // Construct the file using the base directory and sanitized filename only
        File imageDir = new File(imgpath);
        File image = new File(imageDir, sanitizedFilename);
        
        try {
            // Validate using PathValidationUtils to ensure the path is within the expected directory
            image = PathValidationUtils.validateExistingPath(image, imageDir);

            // Delete the file
            Path imagePath = image.toPath();
            Files.delete(imagePath);

        } catch (SecurityException e) {
            // Path validation failed
            return ERROR;
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error deleting the image file: " + imgpath, e);
            return ERROR;
        }
        
        return SUCCESS;
    }

    /**
     * Redirect target for the {@code success} result, read by struts-eform.xml via
     * OGNL ({@code ${redirectTarget}}). A redirect starts a new request, so the
     * {@code scheduleNav=1} flag the delete POST carried would otherwise be lost and
     * the operator would land back on the Image Library with the administration
     * shell's top nav bar gone.
     *
     * @return {@code /eform/efmimagemanager}, with {@code ?scheduleNav=1} appended
     *         when the deleting request carried the flag
     */
    public String getRedirectTarget() {
        return ScheduleNav.append("/eform/efmimagemanager", request);
    }

}
