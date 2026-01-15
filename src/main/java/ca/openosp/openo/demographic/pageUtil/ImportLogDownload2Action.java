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


package ca.openosp.openo.demographic.pageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;

/**
 * @author jay
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;

public class ImportLogDownload2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();
    
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws FileNotFoundException, IOException {
        // Security check - user must have demographic read privileges
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", null)) {
            throw new SecurityException("missing required security object _demographic");
        }

        String importLogParam = request.getParameter("importlog");
        
        if (importLogParam == null || importLogParam.isEmpty()) {
            logger.error("Missing import log parameter");
            return "error";
        }
        
        // Get the temp directory from servlet context
        ServletContext servletContext = ServletActionContext.getServletContext();
        File tempDir = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
        
        if (tempDir == null) {
            logger.error("Unable to access temp directory");
            return "error";
        }
        
        try {
            // Sanitize the filename to prevent path traversal
            String sanitizedFilename = FilenameUtils.getName(importLogParam);
            
            if (sanitizedFilename == null || sanitizedFilename.isEmpty()) {
                logger.warn("Invalid import log filename: " + importLogParam);
                return "error";
            }
            
            // Construct the file path within the temp directory
            File importLogFile = new File(tempDir, sanitizedFilename);

            // Validate using PathValidationUtils to prevent directory traversal
            try {
                PathValidationUtils.validateExistingPath(importLogFile, tempDir);
            } catch (SecurityException e) {
                logger.error("Path is not in the correct directory: " + importLogParam);
                return "error";
            }

            // Check if file is readable
            if (!importLogFile.canRead()) {
                logger.warn("Import log file not readable: " + sanitizedFilename);
                return "error";
            }
            
            // Stream the file to the response
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeHeaderValue(sanitizedFilename) + "\"");
            response.setContentLength((int) importLogFile.length());
            
            try (InputStream in = new FileInputStream(importLogFile);
                 OutputStream out = response.getOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
            
        } catch (IOException e) {
            logger.error("Error downloading import log file", e);
        }
        
        return null;
    }
    
    /**
     * Sanitizes header values to prevent HTTP response splitting attacks.
     * Removes all control characters including CR (\r) and LF (\n) that could
     * be used to inject additional headers or split the HTTP response.
     * 
     * @param value The header value to sanitize
     * @return The sanitized header value safe for use in HTTP headers
     */
    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        
        // Remove all control characters including CR (\r) and LF (\n)
        // This prevents HTTP response splitting attacks
        // Also remove other control characters that could cause issues
        String sanitized = value
            .replaceAll("[\r\n\u0000-\u001F\u007F-\u009F]", "")  // Control chars
            .replaceAll("[\"';]", "");  // Quotes and semicolons
        
        // Ensure the filename is not empty after sanitization
        if (sanitized.trim().isEmpty()) {
            return "importlog";
        }
        
        return sanitized;
    }
}
