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
package ca.openosp.openo.login;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.OscarProperties;

/**
 * This class safely fetches static image resources for the login Index page.
 * <p>
 * Resources are expected to be at OscarDocument/login.  The resource path
 * [oscar context]/loginResource/myImage.png will fetch the myImage.png image
 * from OscarDocument/login.
 * <p>
 * It's easiest to follow a standard naming convention for each element.
 */
public class LoginResourceAction extends HttpServlet {

    private String images;

    public void init() throws ServletException {
        String oscarDocument = OscarProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        // all image files for the login page go into OscarDocuments/login
        this.images = oscarDocument + File.separator + "login";
    }

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        String logoImage = request.getPathInfo();
        File image = null;
        String contentType = null;

        if (logoImage != null) {
            // Decode the path and extract just the filename without any directory components
            String decodedPath = URLDecoder.decode(logoImage, "UTF-8");
            
            // Remove leading slash if present
            if (decodedPath.startsWith("/")) {
                decodedPath = decodedPath.substring(1);
            }
            
            // Use FilenameUtils.getName to extract just the filename, removing any path components
            String sanitizedFilename = FilenameUtils.getName(decodedPath);
            
            // Reject empty or invalid filenames
            if (sanitizedFilename == null || sanitizedFilename.isEmpty() || 
                sanitizedFilename.contains("..") || sanitizedFilename.contains("/") || 
                sanitizedFilename.contains("\\")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid resource path");
                return;
            }
            
            // Construct and validate the file path using PathValidationUtils
            try {
                File imagesDir = new File(images);
                image = PathValidationUtils.validatePath(sanitizedFilename, imagesDir);
            } catch (SecurityException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid resource path");
                return;
            }
        }

        // Get content type by filename.        
        if (image != null && image.exists()) {
            contentType = getServletContext().getMimeType(image.getName());
        }

        if (contentType != null && contentType.startsWith("image")) {
            response.reset();
            response.setContentType(contentType);
            response.setHeader("Content-Length", String.valueOf(image.length()));

            // Write image content to response.
            Files.copy(image.toPath(), response.getOutputStream());
        }
    }

    protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        return;
    }

}
