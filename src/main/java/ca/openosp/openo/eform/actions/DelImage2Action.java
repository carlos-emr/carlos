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


package ca.openosp.openo.eform.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DelImage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

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
        
        String imgpath = OscarProperties.getInstance().getEformImageDirectory();
        
        // Construct the file using the base directory and sanitized filename only
        File imageDir = new File(imgpath);
        File image = new File(imageDir, sanitizedFilename);
        
        try {
            // Validate using PathValidationUtils to ensure the path is within the expected directory
            PathValidationUtils.validateExistingPath(image, imageDir);

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

}
