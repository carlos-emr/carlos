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
package io.github.carlos_emr.carlos.login;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.CarlosProperties;

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

    private static final Logger log = MiscUtils.getLogger();
    private String images;

    public void init() throws ServletException {
        String oscarDocument = CarlosProperties.getInstance().getProperty("BASE_DOCUMENT_DIR");
        // all image files for the login page go into OscarDocuments/login
        this.images = oscarDocument + File.separator + "login";
    }

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        try {
            String logoImage = request.getPathInfo();
            File image = null;
            String contentType = null;

            if (logoImage != null) {
                // Decode the path and extract just the filename without any directory components
                String decodedPath = URLDecoder.decode(logoImage, java.nio.charset.StandardCharsets.UTF_8);
                
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

            // Send 404 if no valid image path was provided or file doesn't exist
            if (image == null || !image.exists()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Get content type by filename
            contentType = getServletContext().getMimeType(image.getName());

            if (contentType == null || !contentType.startsWith("image")) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            response.reset();
            response.setContentType(contentType);
            response.setContentLengthLong(image.length());

            // Write image content to response.
            Files.copy(image.toPath(), response.getOutputStream());
        } catch (ServletException | IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in LoginResourceAction", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An internal error occurred. Please try again or contact your system administrator.");
            }
        }
    }

    protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        return;
    }

}
