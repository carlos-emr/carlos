//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.casemgmt.web;

import com.opensymphony.xwork2.ActionSupport;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.casemgmt.model.ClientImage;
import ca.openosp.openo.casemgmt.service.ClientImageManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.nio.file.Files;

public class ClientImage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private File clientImage;
    private String clientImageFileName;

    private static Logger log = MiscUtils.getLogger();

    private ClientImageManager clientImageManager = SpringUtils.getBean(ClientImageManager.class);

    // Execute on struts action call
    public String execute() {
        return saveImage();
    }

    public String saveImage() {
        HttpSession session = request.getSession(true);
        String id = (String) session.getAttribute("clientId");

        log.info("client image upload: id=" + id);

        // Get file extension from original filename
        String type = null;
        if (clientImageFileName != null && clientImageFileName.contains(".")) {
            type = clientImageFileName.substring(clientImageFileName.lastIndexOf('.') + 1).toLowerCase();
        }

        log.info("extension = " + type);

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

    public void setClientImage(File clientImage) { 
        this.clientImage = clientImage; 
    }

    public void setClientImageFileName(String name) { 
        this.clientImageFileName = name; 
    }
}
