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
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.OscarProperties;

public final class Factory {

    private static Logger logger = MiscUtils.getLogger();

    private Factory() {
        // static methods no need for instance
    }

    /**
     * Find the lab corresponding to segmentID and return the appropriate MessageHandler for it
     */
    public static MessageHandler getHandler(String segmentID) {
        try {
            Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
            Hl7TextMessage hl7TextMessage = hl7TextMessageDao.find(Integer.parseInt(segmentID));

            String type = hl7TextMessage.getType();
            String hl7Body = new String(Base64.decodeBase64(hl7TextMessage.getBase64EncodedeMessage()), MiscUtils.DEFAULT_UTF8_ENCODING);
            return getHandler(type, hl7Body);
        } catch (Exception e) {
            logger.error("Could not retrieve lab for segmentID(" + segmentID + ")", e);
        }

        return getHandler("", "");
    }


	public static MessageHandler getHandler(Hl7TextMessage hl7TextMessage) {
		try {
			String type = hl7TextMessage.getType();
			String hl7Body = new String(Base64.decodeBase64(hl7TextMessage.getBase64EncodedeMessage()), MiscUtils.DEFAULT_UTF8_ENCODING);
			return getHandler(type, hl7Body);
		} catch (Exception e) {
			logger.error("Could not retrieve lab for segmentID(" + hl7TextMessage.getId() + ")", e);
		}

		return getHandler("", "");
	}

    public static String getHL7Body(String segmentID) {
        String ret = null;
        try {
            Hl7TextMessageDao hl7TextMessageDao = (Hl7TextMessageDao) SpringUtils.getBean(Hl7TextMessageDao.class);
            Hl7TextMessage hl7TextMessage = hl7TextMessageDao.find(Integer.parseInt(segmentID));

            ret = new String(Base64.decodeBase64(hl7TextMessage.getBase64EncodedeMessage()), MiscUtils.DEFAULT_UTF8_ENCODING);
        } catch (Exception e) {
            logger.error("Could not retrieve lab for segmentID(" + segmentID + ")", e);
        }
        return ret;
    }

    /*
     * Create and return the message handler corresponding to the message type
     */
    public static MessageHandler getHandler(String type, String hl7Body) {
        Document doc = null;
        String msgType;
        String msgHandler = "";
        Path labTypesPath = null;

        try {
            labTypesPath = Paths.get(Factory.class.getClassLoader().getResource("io/github/carlos_emr/carlos/lab/ca/all/parsers/message_config.xml").toURI());
        } catch (URISyntaxException e2) {
            logger.error("Could not default Message configuration file ", e2);
        }

        String labTypesPathOverride = OscarProperties.getInstance().getProperty("LAB_TYPES");

        if (labTypesPathOverride != null && !labTypesPathOverride.isEmpty()) {
            labTypesPath = Paths.get(labTypesPathOverride);
        }

        try (InputStream is = Files.newInputStream(labTypesPath)) {

            // return default handler if the type is not specified
            if (type == null) {
                MessageHandler handler = new DefaultGenericHandler();
                handler.init(hl7Body);
                return (handler);
            } else {
                type = type.trim();
            }

            SAXBuilder parser = new SAXBuilder();
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
                logger.debug("No message handler specified for type: " + type + "\nUsing default message handler instead");
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
                    logger.debug("Could not find message handler: " + msgHandler + "\nUsing default message handler instead");
                    MessageHandler mh = new DefaultGenericHandler();
                    mh.init(hl7Body);
                    return (mh);
                } catch (Exception e1) {
                    logger.debug("Could not create message handler: " + msgHandler + "\nUsing default message handler instead", e1);
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
