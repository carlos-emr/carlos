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
package ca.openosp;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;

/**
 * Servlet for handling document file uploads with path validation and security.
 * 
 * <p>This servlet processes multipart/form-data file uploads and stores documents
 * in configured directories. Key features:</p>
 * <ul>
 *   <li>File upload processing using Apache Commons FileUpload</li>
 *   <li>Path traversal attack prevention via {@link PathValidationUtils}</li>
 *   <li>Configurable upload directories (documents, inbox, archive)</li>
 *   <li>Request forwarding to configured success pages</li>
 * </ul>
 * 
 * <p>Configuration properties:</p>
 * <ul>
 *   <li><code>DOCUMENT_DIR</code> - Main document storage directory</li>
 *   <li><code>ONEDT_INBOX</code> - Inbox folder for incoming documents</li>
 *   <li><code>ONEDT_ARCHIVE</code> - Archive folder for processed documents</li>
 *   <li><code>RA_FORWORD</code> - Forward destination after upload</li>
 * </ul>
 * 
 * <p><strong>Security:</strong> Uses PathValidationUtils to prevent directory
 * traversal attacks. All uploaded files are validated before storage.</p>
 * 
 * @see PathValidationUtils
 * @see OscarProperties
 */
public class DocumentUploadServlet extends HttpServlet {

    /** Buffer size for file operations */
    final static int BUFFER = 4096;

    /**
     * Handles HTTP requests for document uploads.
     * Processes multipart form data and stores uploaded files securely.
     * 
     * @param request the HTTP servlet request containing the uploaded file
     * @param response the HTTP servlet response
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String foldername = "", fileheader = "", forwardTo = "";
        forwardTo = OscarProperties.getInstance().getProperty("RA_FORWORD");
        foldername = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");

        String inboxFolder = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
        String archiveFolder = OscarProperties.getInstance().getProperty("ONEDT_ARCHIVE");

        if (forwardTo == null || forwardTo.length() < 1)
            return;

        String providedFilename = request.getParameter("filename");
        if (providedFilename != null) {
            providedFilename = URLDecoder.decode(providedFilename, "UTF-8");
            
            // Validate and sanitize the filename to prevent path traversal
            String sanitizedFilename = FilenameUtils.getName(providedFilename);
            if (sanitizedFilename == null || sanitizedFilename.isEmpty()) {
                MiscUtils.getLogger().error("Invalid filename provided: " + providedFilename);
                return;
            }
            
            File documentDirectory = new File(foldername);
            File inboxDir = new File(inboxFolder);
            File archiveDir = new File(archiveFolder);

            try {
                // Use PathValidationUtils for secure path validation
                File providedFile;
                try {
                    // First try inbox directory
                    providedFile = PathValidationUtils.validatePath(sanitizedFilename, inboxDir);

                    // If file doesn't exist in inbox, check archive
                    if (!providedFile.exists()) {
                        providedFile = PathValidationUtils.validatePath(sanitizedFilename, archiveDir);
                    }
                } catch (SecurityException e) {
                    MiscUtils.getLogger().error("File does not reside in a valid path: " + providedFilename);
                    return;
                }
                
                // Verify the file exists before copying
                if (!providedFile.exists()) {
                    MiscUtils.getLogger().error("File not found: " + sanitizedFilename);
                    return;
                }
                
                FileUtils.copyFileToDirectory(providedFile, documentDirectory);
                fileheader = sanitizedFilename;
                
            } catch (IOException e) {
                MiscUtils.getLogger().error("Error processing file: " + sanitizedFilename, e);
                return;
            }
        } else {

            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();

            // Configure size threshold: small files (<1MB) stay in memory, larger ones go to disk
            factory.setSizeThreshold(1024 * 1024); // 1 MB threshold

            // Configure a controlled temporary directory for larger file items
            String systemTempDir = System.getProperty("java.io.tmpdir");
            File uploadTempDir = new File(systemTempDir, "openoemr-uploads");

            try {
                // Create the temp directory if it doesn't exist
                if (!uploadTempDir.exists()) {
                    if (!uploadTempDir.mkdirs()) {
                        throw new ServletException("Failed to create upload temp directory: " + uploadTempDir.getAbsolutePath());
                    }
                }

                // Validate the temp directory is within allowed system temp path
                PathValidationUtils.validateExistingPath(uploadTempDir, new File(systemTempDir));
                factory.setRepository(uploadTempDir);

            } catch (SecurityException e) {
                MiscUtils.getLogger().error("Security validation failed for upload temp directory", e);
                throw new ServletException("Upload configuration error: invalid temp directory path", e);
            }

            // Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setHeaderEncoding("UTF-8");

            // Set file size limits to prevent DoS attacks (50 MB limit for MOH billing files)
            upload.setFileSizeMax(52428800); // 50 MB per file
            upload.setSizeMax(52428800);     // 50 MB total request size

            try {
                // Parse the request
                @SuppressWarnings("unchecked")
                List<FileItem> /* FileItem */items = upload.parseRequest(request);
                // Process the uploaded items
                Iterator<FileItem> iter = items.iterator();
                while (iter.hasNext()) {
                    FileItem item = iter.next();

                    if (item.isFormField()) { //
                    } else {
                        String pathName = item.getName();

                        try {
                            // Use PathValidationUtils to sanitize and validate the destination path
                            File documentDir = new File(foldername);
                            File savedFile = PathValidationUtils.validatePath(pathName, documentDir);

                            fileheader = savedFile.getName();
                            item.write(savedFile);

                            if (OscarProperties.getInstance().isPropertyActive("moh_file_management_enabled")) {
                                File inboxDir = new File(inboxFolder);
                                FileUtils.copyFileToDirectory(savedFile, inboxDir);
                            }
                        } catch (SecurityException e) {
                            MiscUtils.getLogger().error("Invalid uploaded filename: " + pathName);
                            continue; // Skip this file
                        } catch (IOException e) {
                            MiscUtils.getLogger().error("Error processing file: " + pathName, e);
                            continue; // Skip this file
                        }
                    }
                }
            } catch (FileUploadException e) {
                MiscUtils.getLogger().error("Error", e);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
        }

        DocumentBean documentBean = new DocumentBean();
        request.setAttribute("documentBean", documentBean);
        documentBean.setFilename(fileheader);
        RequestDispatcher dispatch = getServletContext().getRequestDispatcher(forwardTo);
        dispatch.forward(request, response);
    }
}
