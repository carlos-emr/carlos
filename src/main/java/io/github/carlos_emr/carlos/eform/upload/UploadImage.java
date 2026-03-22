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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;

/**
 * Servlet that handles multipart image file uploads for the eForm image library.
 * Validates uploaded filenames using {@link PathValidationUtils} to prevent path
 * traversal attacks and saves files to the configured {@code EFORM_IMAGES_DIR}.
 *
 * <p>After successful upload, redirects to the image management page with a
 * one-second JavaScript delay.</p>
 *
 * @since 2006-05-25
 */
@MultipartConfig(maxFileSize = 52428800, maxRequestSize = 52428800, fileSizeThreshold = 1048576)
public class UploadImage extends HttpServlet {

    /**
     * Processes multipart upload requests, saving each uploaded file part to
     * the eForm image directory after path validation.
     *
     * @param request HttpServletRequest the multipart upload request
     * @param response HttpServletResponse the response to write confirmation HTML to
     * @throws IOException if an I/O error occurs during file saving
     * @throws ServletException if a servlet error occurs during part processing
     */
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String foldername = "", fileheader = "";

        Properties ap = CarlosProperties.getInstance();
        foldername = ap.getProperty("EFORM_IMAGES_DIR");

        File imageDir = new File(foldername);

        try {
            for (Part part : request.getParts()) {
                String submittedFilename = part.getSubmittedFileName();
                if (submittedFilename == null || submittedFilename.isEmpty()) {
                    continue;
                }

                File savedFile = PathValidationUtils.validatePath(submittedFilename, imageDir);
                fileheader = savedFile.getName();

                MiscUtils.getLogger().debug(fileheader + " uploaded to " + foldername);

                try (InputStream in = part.getInputStream()) {
                    Files.copy(in, savedFile.toPath());
                }
            }
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Path validation failed for uploaded image", e);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        String contextPath = Encode.forJavaScript(request.getContextPath());
        out.println("<head>");
        out.println("<script language=\"JavaScript\">");
        out.println("setTimeout(\"top.location.href = '" + contextPath + "/eform/uploadimages.jsp'\",1000);");
        out.println("</script>");
        out.println("</head>");
        out.println("<body>");
        out.println("File upload successfully.<br> Please wait for 1 seconds to go to \"modify\" page");
        out.println("</body>");
    }

}
