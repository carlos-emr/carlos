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
 * Modifications by CARLOS Contributors, 2026.
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


/*
 * Factory.java
 *
 * Created on June 4, 2007, 10:46 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;

import io.github.carlos_emr.CarlosProperties;

public final class Factory {

    private static Logger logger = MiscUtils.getLogger();

    private Factory() {
        // static methods no need for instance
    }

    /**
     * Find the HL7 lab corresponding to segmentID and return its MessageHandler,
     * or null when the record is missing or cannot be parsed. IDs from legacy
     * PathNet and other lab stores must not be passed to this factory.
     */
    public static MessageHandler getHandler(String segmentID) {
        try {
            Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
            return getHandler(hl7TextMessageDao.find(Integer.parseInt(segmentID)));
        } catch (Exception e) {
            logger.error("Could not retrieve lab for segmentID(" + segmentID + ")", e);
        }

        return null;
    }


    public static MessageHandler getHandler(Hl7TextMessage hl7TextMessage) {
        try {
            String hl7Body = decodeHL7Body(hl7TextMessage);
            return hl7Body == null ? null : getHandler(hl7TextMessage.getType(), hl7Body);
        } catch (Exception e) {
            logger.error("Could not create handler for stored HL7 message", e);
        }
        return null;
    }

    private static String decodeHL7Body(Hl7TextMessage hl7TextMessage) {
        if (hl7TextMessage == null) {
            logger.warn("HL7 message was not found; cannot read or parse it");
            return null;
        }
        String encodedBody = hl7TextMessage.getBase64EncodedeMessage();
        if (encodedBody == null || encodedBody.isBlank()) {
            logger.warn("HL7 message {} has no encoded body; cannot read or parse it", hl7TextMessage.getId());
            return null;
        }
        return new String(Base64.decodeBase64(encodedBody), StandardCharsets.UTF_8);
    }

    public static String getHL7Body(String segmentID) {
        String ret = null;
        try {
            Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
            Hl7TextMessage hl7TextMessage = hl7TextMessageDao.find(Integer.parseInt(segmentID));

            ret = decodeHL7Body(hl7TextMessage);
        } catch (Exception e) {
            logger.error("Could not retrieve lab for segmentID(" + segmentID + ")", e);
        }
        return ret;
    }

    /*
     * Create and return the message handler corresponding to the message type
     */
    // PATH_TRAVERSAL_IN + IMPROPER_UNICODE: LAB_TYPES is admin-only config; case folding is not used for a security or authorization decision.
    @SuppressFBWarnings(
            value = {"PATH_TRAVERSAL_IN", "IMPROPER_UNICODE"},
            justification = "LAB_TYPES is admin-only config; case folding is not used for a security or authorization decision")
    public static MessageHandler getHandler(String type, String hl7Body) {
        if (hl7Body == null || hl7Body.isBlank()) {
            logger.warn("HL7 message body is missing; cannot create a message handler");
            return null;
        }
        // Imported CDS results require the precision-preserving adapter even when an existing
        // installation overrides LAB_TYPES with a configuration predating this built-in type.
        if ("CARLOS-CDS".equals(type)) {
            try {
                MessageHandler handler = new CdsLaboratoryMessageParser();
                handler.init(hl7Body);
                return handler;
            } catch (ca.uhn.hl7v2.HL7Exception invalid) {
                logger.error("Could not parse a stored CDS laboratory result");
                return null;
            }
        }
        Document doc = null;
        String msgType;
        String msgHandler = "";
        Path labTypesPath = null;

        try {
            labTypesPath = Paths.get(Factory.class.getClassLoader().getResource("io/github/carlos_emr/carlos/lab/ca/all/parsers/message_config.xml").toURI());
        } catch (URISyntaxException e2) {
            logger.error("Could not default Message configuration file ", e2);
        }

        String labTypesPathOverride = CarlosProperties.getInstance().getProperty("LAB_TYPES");

        if (labTypesPathOverride != null && !labTypesPathOverride.isEmpty()) {
            try {
                labTypesPath = PathValidationUtils.validateConfiguredFile(labTypesPathOverride, "LAB_TYPES").toPath();
            } catch (SecurityException e) {
                // Invalid/missing LAB_TYPES override: log and fall back to the bundled default
                // configuration resolved above rather than failing lab message parsing outright.
                logger.error("Configured LAB_TYPES override is invalid; using default message configuration instead", e);
            }
        }

        if (labTypesPath == null) {
            logger.error("Could not resolve Message configuration file. Using default message handler instead.");
            try {
                MessageHandler handler = new DefaultGenericHandler();
                handler.init(hl7Body);
                return handler;
            } catch (Exception e) {
                logger.error("Could not create default message handler", e);
                return null;
            }
        }

        try (InputStream is = Files.newInputStream(PathValidationUtils.validateConfiguredFile(labTypesPath.toString(), "message configuration file").toPath())) {

            // return default handler if the type is not specified
            if (type == null) {
                MessageHandler handler = new DefaultGenericHandler();
                handler.init(hl7Body);
                return (handler);
            } else {
                type = type.trim();
            }

            SAXBuilder parser = XmlUtils.createSecureSAXBuilder();
            doc = parser.build(is);

            Element root = doc.getRootElement();
            List<?> items = root.getChildren();

            for (int i = 0; i < items.size(); i++) {

                Element element = (Element) items.get(i);
                msgType = element.getAttributeValue("name");

                if (msgType.equalsIgnoreCase(type)) {
                    String className = element.getAttributeValue("className");

                    // in case we have dots in the handler class name (i.e. package
                    // is specified), don't assume default package
                    if (className.indexOf(".") != -1) {
                        msgHandler = className;
                    } else {
                        msgHandler = "io.github.carlos_emr.carlos.lab.ca.all.parsers." + className;
                    }
                }
            }

            // create and return the message handler
            if (msgHandler.equals("")) {
                logger.warn("No message handler specified for type: " + type + ". Using default message handler instead.");
                MessageHandler mh = new DefaultGenericHandler();
                mh.init(hl7Body);
                return (mh);
            } else {
                try {
                    Class<?> classRef = Class.forName(msgHandler);
                    MessageHandler mh = (MessageHandler) classRef.newInstance();
                    logger.debug("Message handler '" + msgHandler + "' created successfully");
                    logger.debug("Message: " + hl7Body);
                    mh.init(hl7Body);
                    return (mh);
                } catch (ClassNotFoundException e) {
                    logger.warn("Could not find message handler: " + msgHandler + ". Using default message handler instead.");
                    MessageHandler mh = new DefaultGenericHandler();
                    mh.init(hl7Body);
                    return (mh);
                } catch (Exception e1) {
                    logger.error("Could not create message handler: " + msgHandler + ". Using default message handler instead.", e1);
                    MessageHandler mh = new DefaultGenericHandler();
                    mh.init(hl7Body);
                    return (mh);
                }
            }
        } catch (NoSuchFileException e) {
            logger.error("Could not default Message configuration file ", e);
        } catch (Exception e) {
            logger.error("Could not create message handler", e);
        }

        return null;
    }
}
