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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ca.openosp.openo.lab.ca.all.upload.MessageUploader;
import ca.openosp.openo.lab.ca.all.upload.RouteReportResults;
import ca.openosp.OscarProperties;


public class ExcellerisOntarioHandler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();

	private Integer labNo = null;

	@Override
	public Integer getLastLabNo() {
		return labNo;
	}

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        Document doc = null;
        try {
            // Validate the file path to prevent path traversal attacks
            // First check if fileName is null or empty
            if (fileName == null || fileName.trim().isEmpty()) {
                logger.error("Invalid file name: null or empty");
                return null;
            }
            
            // Get expected document directory for validation
            OscarProperties props = OscarProperties.getInstance();
            String documentDir = props.getProperty("DOCUMENT_DIR");
            if (documentDir == null || documentDir.isEmpty()) {
                logger.error("DOCUMENT_DIR property not configured");
                return null;
            }
            
            // Use PathValidationUtils for validation
            File docDir = new File(documentDir).getCanonicalFile();
            if (!docDir.exists() || !docDir.isDirectory()) {
                logger.error("Document directory does not exist or is not a directory: " + documentDir);
                return null;
            }

            // Create file object and validate using PathValidationUtils
            File file = new File(fileName);
            try {
                PathValidationUtils.validateExistingPath(file, docDir);
            } catch (SecurityException e) {
                logger.error("Attempted path traversal detected - file outside document directory: " + fileName);
                throw new SecurityException("Access denied: file outside permitted directory");
            }

            // Now safe to check if file exists and is a regular file
            if (!file.exists()) {
                logger.error("File does not exist: " + fileName);
                return null;
            }

            if (!file.isFile()) {
                logger.error("Path is not a regular file: " + fileName);
                return null;
            }

            // Ensure file is readable
            if (!file.canRead()) {
                logger.error("File is not readable: " + fileName);
                return null;
            }
            
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing to prevent XXE attacks
            docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            docFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            docFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            // Disable XInclude
            docFactory.setXIncludeAware(false);
            
            // Disabled expansion of entity references
            docFactory.setExpandEntityReferences(false);
            
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            // Use the validated file object - safe after all validation checks
            doc = docBuilder.parse(file);
        } catch (SecurityException e) {
            logger.error("Security violation in Excelleris ON message handling", e);
            throw e; // Re-throw security exceptions
        } catch (ParserConfigurationException e) {
            logger.error("XML parser configuration error", e);
        } catch (Exception e) {
            logger.error("Could not parse Excelleris ON message", e);
        }

        RouteReportResults routeResults;
        StringBuilder audit = new StringBuilder();

        if (doc != null) {
            int i = 0;
            try {
                Node messageSpec = doc.getFirstChild();
                NodeList messages = messageSpec.getChildNodes();
                for (i = 0; i < messages.getLength(); i++) {
					routeResults = new RouteReportResults();
                    String hl7Body = messages.item(i).getFirstChild().getTextContent();
					MessageUploader.routeReport(loggedInInfo, serviceName, "ExcellerisON", hl7Body, fileId, routeResults);
					labNo = routeResults.segmentId;
                }
            } catch (Exception e) {
                logger.error("Could not upload Excelleris Ontario message", e);
                MiscUtils.getLogger().error("Error", e);
                MessageUploader.clean(fileId);
                return null;
            }
            return ("success");
        } else {
            return null;
        }

    }

}
