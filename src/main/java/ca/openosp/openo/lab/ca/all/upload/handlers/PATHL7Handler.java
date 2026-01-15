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


/*
 * PATHL7Handler.java
 *
 * Created on May 23, 2007, 4:33 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ca.openosp.openo.lab.ca.all.upload.handlers;

import java.nio.file.Path;
import java.nio.file.Paths;

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

import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.ca.all.upload.MessageUploader;
import ca.openosp.openo.lab.ca.all.upload.RouteReportResults;

/**
 * @author wrighd
 */
public class PATHL7Handler implements MessageHandler {

    Logger logger = MiscUtils.getLogger();

	private Integer labNo = null;

	@Override
	public Integer getLastLabNo() {
		return labNo;
	}

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
    Document doc = null;
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("Filename cannot be null or empty");
            }

            // Base directory - validate using PathValidationUtils
            String baseDir = OscarProperties.getInstance().getDocumentDirectory();
            java.io.File baseDirFile = new java.io.File(baseDir);
            java.io.File targetFile = new java.io.File(fileName);

            // Validate the existing file is within the allowed directory
            PathValidationUtils.validateExistingPath(targetFile, baseDirFile);

            if (!targetFile.exists() || !targetFile.isFile()) {
                logger.error("File does not exist or is not a regular file: " + fileName);
                return null;
            }

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            // Disable DTDs and external entities for XXE prevention
            docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            docFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            docFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Disable XInclude
            docFactory.setXIncludeAware(false);
            // Disabled expansion of entity references
            docFactory.setExpandEntityReferences(false);
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            doc = docBuilder.parse(targetFile);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid file name: " + fileName, e);
            return null;
        } catch (ParserConfigurationException e) {
            logger.error("Failed to configure XML parser", e);
            return null;
        } catch (Exception e) {
            logger.error("Could not parse PATHL7 message", e);
        }

        if (doc != null) {
            int i = 0;
            try {
                Node messageSpec = doc.getFirstChild();
                NodeList messages = messageSpec.getChildNodes();
                for (i = 0; i < messages.getLength(); i++) {
                    RouteReportResults routeResults = new RouteReportResults();
                    String hl7Body = messages.item(i).getFirstChild().getTextContent();
                    MessageUploader.routeReport(loggedInInfo, serviceName, "PATHL7", hl7Body, fileId, routeResults);
                    labNo = routeResults.segmentId;
                }
            } catch (Exception e) {
                logger.error("Could not upload PATHL7 message", e);
                MiscUtils.getLogger().error("Error in Lab #" + (i + 1) + " in batch file " + fileName, e);
                MessageUploader.clean(fileId);
                return null;
            }
            return "success";
        } else {
            return null;
        }
    }

}
