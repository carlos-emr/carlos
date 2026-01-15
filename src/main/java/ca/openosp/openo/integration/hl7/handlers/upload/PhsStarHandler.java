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


package ca.openosp.openo.integration.hl7.handlers.upload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.OscarProperties;

import ca.openosp.openo.lab.ca.all.upload.MessageUploader;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;

public class PhsStarHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();

    @Override
    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        logger.info("received PHS/STAR message");

        ca.openosp.openo.integration.hl7.handlers.PhsStarHandler handler = new ca.openosp.openo.integration.hl7.handlers.PhsStarHandler();
        BufferedReader in = null;
        try {
            // Validate the file path to prevent path traversal attacks
            File file = new File(fileName);
            
            // Ensure the file exists and is a regular file
            if (!file.exists() || !file.isFile()) {
                logger.error("File does not exist or is not a regular file: " + fileName);
                MessageUploader.clean(fileId);
                throw new IOException("Invalid file: " + fileName);
            }

            // Use PathValidationUtils to validate file is within document directory
            OscarProperties props = OscarProperties.getInstance();
            String documentDir = props.getProperty("DOCUMENT_DIR");
            if (documentDir != null && !documentDir.isEmpty()) {
                File docDir = new File(documentDir).getCanonicalFile();
                try {
                    PathValidationUtils.validateExistingPath(file, docDir);
                } catch (SecurityException e) {
                    logger.error("Attempted to access file outside document directory: " + fileName);
                    MessageUploader.clean(fileId);
                    throw new SecurityException("Access denied: file outside permitted directory");
                }
            }
            
            // Use the validated file object instead of creating FileReader with raw path
            in = new BufferedReader(new FileReader(file));
            String line = null;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            handler.init(sb.toString());

            return ("success");
        } catch (SecurityException e) {
            logger.error("Security violation: " + e.getMessage());
            MessageUploader.clean(fileId);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            MessageUploader.clean(fileId);
            throw (new RuntimeException(e));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.warn("Failed to close BufferedReader", e);
                }
            }
        }

    }

}
