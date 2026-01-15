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


package ca.openosp.openo.lab.ca.all.upload.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.hl7.v2.oscar_to_oscar.OscarToOscarUtils;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.OscarProperties;

import ca.openosp.openo.lab.ca.all.upload.MessageUploader;
import ca.openosp.openo.lab.ca.all.upload.handlers.OscarToOscarHl7V2.AdtA09Handler;
import ca.uhn.hl7v2.model.AbstractMessage;
import ca.uhn.hl7v2.model.v26.message.ADT_A09;

public class OscarToOscarHl7V2Handler implements MessageHandler {
    private Logger logger = MiscUtils.getLogger();

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {

        try {
            // Validate and sanitize the file path to prevent path traversal attacks
            File validatedFile = validateFilePath(fileName);
            if (validatedFile == null) {
                logger.error("Invalid file path provided: " + fileName);
                MessageUploader.clean(fileId);
                return null;
            }
            
            byte[] dataBytes = FileUtils.readFileToByteArray(validatedFile);
            String dataString = new String(dataBytes, MiscUtils.DEFAULT_UTF8_ENCODING);
            logger.debug("Incoming HL7 Message : \n" + dataString);

            AbstractMessage message = OscarToOscarUtils.pipeParserParse(dataString);

            if (message instanceof ADT_A09) {
                AdtA09Handler.handle(loggedInInfo, (ADT_A09) message);
            } else {
                MessageUploader.routeReport(loggedInInfo, serviceName, OscarToOscarUtils.UPLOAD_MESSAGE_TYPE, dataString, fileId);
            }

            return ("success");
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            MessageUploader.clean(fileId);
            throw (new RuntimeException(e));
        }
    }
    
    /**
     * Validates that the provided file path is within the allowed document directory
     * and does not contain path traversal attempts.
     * 
     * @param fileName the file path to validate
     * @return a validated File object if the path is safe, null otherwise
     */
    private File validateFilePath(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            logger.error("File name is null or empty");
            return null;
        }
        
        try {
            // Get the base document directory from configuration
            String baseDocDir = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
            if (baseDocDir == null || baseDocDir.isEmpty()) {
                logger.error("DOCUMENT_DIR not configured");
                return null;
            }
            
            // Validate file path using PathValidationUtils
            File baseDir = new File(baseDocDir);
            File canonicalFile = new File(fileName).getCanonicalFile();
            try {
                PathValidationUtils.validateExistingPath(canonicalFile, baseDir);
            } catch (SecurityException e) {
                logger.error("File path is outside allowed directory. Base: " + baseDocDir + ", File: " + canonicalFile.getPath());
                return null;
            }
            
            // Check if the file exists and is readable
            if (!canonicalFile.exists()) {
                logger.error("File does not exist: " + canonicalFile.getAbsolutePath());
                return null;
            }
            
            if (!canonicalFile.canRead()) {
                logger.error("File is not readable: " + canonicalFile.getAbsolutePath());
                return null;
            }
            
            if (!canonicalFile.isFile()) {
                logger.error("Path is not a regular file: " + canonicalFile.getAbsolutePath());
                return null;
            }
            
            return canonicalFile;
            
        } catch (IOException e) {
            logger.error("Error validating file path: " + fileName, e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error validating file path: " + fileName, e);
            return null;
        }
    }
}
