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
 * DefaultHandler.java
 *
 * Created on May 23, 2007, 4:30 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ca.openosp.openo.lab.ca.all.upload.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.LoggedInInfo;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import ca.openosp.openo.lab.ca.all.upload.MessageUploader;
import ca.openosp.openo.lab.ca.all.util.Utilities;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.OscarProperties;

public class DefaultHandler implements MessageHandler {
    Logger logger = MiscUtils.getLogger();
    String hl7Type = null;

    public DefaultHandler() {
        logger.warn("DefaultHandler: Created DefaultHandler instance with null hl7Type");
    }

    String getHl7Type() {
        logger.warn("DefaultHandler.getHl7Type: Returning hl7Type = " + hl7Type);
        return hl7Type;
    }

    public String parse(LoggedInInfo loggedInInfo, String serviceName, String fileName, int fileId, String ipAddr) {
        logger.info("DefaultHandler.parse: Called with serviceName=" + serviceName + ", fileName=" + fileName + ", fileId=" + fileId);
        Document xmlDoc = getXML(fileName);

        /*
         *  If the message is in xml format parse through all the nodes looking for
         *  data that contains a pid segment
         */
        if (xmlDoc != null) {
            String hl7Body = null;
            int msgCount = 0;
            try {
                NodeList allNodes = xmlDoc.getElementsByTagNameNS("*", "*");
                for (int i = 1; i < allNodes.getLength(); i++) {
                    hl7Body = allNodes.item(i).getFirstChild().getTextContent();

                    if (hl7Body != null && hl7Body.indexOf("\nPID|") > 0) {
                        msgCount++;
                        logger.debug("using xml HL7 Type " + getHl7Type());
                        MessageUploader.routeReport(loggedInInfo, serviceName, getHl7Type(), hl7Body, fileId);
                    }
                }
            } catch (Exception e) {
                MessageUploader.clean(fileId);
                logger.error("ERROR:", e);
                return null;
            }
        } else {
            int i = 0;
            try {
                ArrayList<String> messages = Utilities.separateMessages(fileName);
                for (i = 0; i < messages.size(); i++) {
                    String msg = messages.get(i);
                    String typeToUse = getHl7Type() != null ? getHl7Type() : serviceName;
                    logger.info("using HL7 Type " + typeToUse + " (original: " + getHl7Type() + ", serviceName: " + serviceName + ")");
                    MessageUploader.routeReport(loggedInInfo, serviceName, typeToUse, msg, fileId);
                }
            } catch (Exception e) {
                MessageUploader.clean(fileId);
                logger.error("ERROR:", e);
                return null;
            }
        }
        return ("success");
    }


    /*
     *  Return the message as an xml document if it is in the xml format
     */
    private Document getXML(String fileName) {
        try {
            // Validate the file path using PathValidationUtils
            File file = new File(fileName);

            // Ensure the file exists and is a regular file
            if (!file.exists() || !file.isFile()) {
                logger.error("File does not exist or is not a regular file: " + fileName);
                return null;
            }

            // Validate the file is within the expected document directory
            OscarProperties props = OscarProperties.getInstance();
            String documentDir = props.getProperty("DOCUMENT_DIR");
            if (documentDir != null && !documentDir.isEmpty()) {
                File docDir = new File(documentDir).getCanonicalFile();
                PathValidationUtils.validateExistingPath(file, docDir);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Use the validated file object instead of creating a new FileInputStream with the raw path
            Document doc = factory.newDocumentBuilder().parse(file);
            return (doc);

            // Ignore exceptions and return false
        } catch (Exception e) {
            logger.error("Error parsing XML file: " + fileName, e);
            return (null);
        }
    }


    //TODO: Dont think this needs to be in this class.  Better as a util method
    public String readTextFile(String fullPathFilename) throws IOException {
        // Validate the file path using PathValidationUtils
        File file = new File(fullPathFilename);

        // Ensure the file exists and is a regular file
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist or is not a regular file: " + fullPathFilename);
        }

        // Validate the file is within the expected document directory
        OscarProperties props = OscarProperties.getInstance();
        String documentDir = props.getProperty("DOCUMENT_DIR");
        if (documentDir != null && !documentDir.isEmpty()) {
            File docDir = new File(documentDir).getCanonicalFile();
            PathValidationUtils.validateExistingPath(file, docDir);
        }

        StringBuilder sb = new StringBuilder(1024);
        // Use the validated file object instead of the raw path
        BufferedReader reader = new BufferedReader(new FileReader(file));

        char[] chars = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(chars)) > -1) {
            sb.append(String.valueOf(chars));
        }

        reader.close();

        return sb.toString();
    }

}
