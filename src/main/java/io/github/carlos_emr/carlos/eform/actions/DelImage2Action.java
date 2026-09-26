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

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    @Override
    public String execute() throws IOException {

        // CSRFGuard validates POST/PUT/DELETE/PATCH, not GET/HEAD, so without this
        // guard an authenticated _eform writer could delete an image via a
        // token-less GET (e.g. a forged <img src>). Checked before privilege and
        // before any file operation, same shape as the sibling DelEForm2Action.
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_eform", "w", null)) {
            throw new SecurityException("missing required sec object (_eform)");
        }

        String imgname = request.getParameter("filename");
        
        // Validate input parameter. struts-eform.xml maps only "success" for this route, so a
        // named "error" result would resolve to a Struts "No result defined" page; report the
        // outcome on the response directly instead (direct-response contract).
        if (imgname == null || imgname.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "filename is required");
            return NONE;
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

        } catch (SecurityException _) {
            // Path validation failed: the name did not resolve inside the image directory.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid image name");
            return NONE;
        } catch (IOException e) {
            MiscUtils.getLogger().error("Error deleting the image file: " + imgpath, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "the image could not be deleted");
            return NONE;
        }
        
        return SUCCESS;
    }

    /**
     * Returns schedule-navigation requests to the Administration shell, which renders the
     * schedule header and loads Image Library. The standalone JSP does not render that header.
     * Other callers retain their standalone Image Library destination.
     *
     * @return the application-relative destination for the POST/redirect/GET result
     */
    public String getRedirectTarget() {
        return ScheduleNav.isActive(request)
                ? ScheduleNav.append("/administration?show=ImageUpload", request)
                : "/eform/efmimagemanager";
    }

}
