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


package io.github.carlos_emr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Properties;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Servlet for handling document management system file uploads.
 *
 * <p>This servlet processes multipart/form-data file uploads for the document
 * management system, providing functionality for:</p>
 * <ul>
 *   <li>File upload processing with timestamp-based naming</li>
 *   <li>Temporary file storage during processing</li>
 *   <li>Document categorization and metadata handling</li>
 *   <li>Integration with the document management repository</li>
 * </ul>
 *
 * <p>Files are timestamped upon upload using the format yyyyMMddHmmss to ensure
 * unique filenames and proper chronological ordering.</p>
 *
 * <p><strong>Security:</strong> Uses {@link PathValidationUtils} to prevent directory
 * traversal attacks. All uploaded files are validated before storage.</p>
 *
 * @see DocumentUploadServlet
 * @see PathValidationUtils
 */
public class DocumentMgtUploadServlet extends HttpServlet {

    /**
     * Handles HTTP requests for document management uploads.
     * Processes multipart form data and stores uploaded documents with timestamps.
     *
     * @param request the HTTP servlet request containing the uploaded file
     * @param response the HTTP servlet response
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHmmss");
        String output = formatter.format(new java.util.Date());

        String foldername = "", fileheader = "", forwardTo = "";

        Properties ap = CarlosProperties.getInstance();

        forwardTo = ap.getProperty("DOC_FORWARD");
        foldername = ap.getProperty("DOCUMENT_DIR");

        if (forwardTo == null || forwardTo.length() < 1) return;

        File documentDir = new File(foldername);

        try {
            for (Part part : request.getParts()) {
                String submittedFilename = part.getSubmittedFileName();
                if (submittedFilename == null || submittedFilename.isEmpty()) {
                    continue;
                }

                String timestampedName = output + submittedFilename;

                File savedFile = PathValidationUtils.validatePath(timestampedName, documentDir);
                // S2083: Path.resolve() clears SonarCloud taint — validatePath() sanitized filename and confirmed containment
                savedFile = documentDir.toPath().resolve(savedFile.getName()).toFile();
                fileheader = savedFile.getName();

                try (InputStream in = part.getInputStream()) {
                    Files.copy(in, savedFile.toPath());
                }
            }
        } catch (SecurityException e) {
            MiscUtils.getLogger().error("Path validation failed for uploaded file", e);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        DocumentBean documentBean = new DocumentBean();
        request.setAttribute("documentBean", documentBean);
        documentBean.setFilename(fileheader);

        RequestDispatcher dispatch = getServletContext().getRequestDispatcher(forwardTo);
        dispatch.forward(request, response);
    }

}
