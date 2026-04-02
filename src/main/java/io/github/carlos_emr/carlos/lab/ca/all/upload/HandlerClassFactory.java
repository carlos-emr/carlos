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


/*
 * HandlerClassFactory.java
 *
 * Created on May 23, 2007, 11:52 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload;

import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.DefaultHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;

import java.io.InputStream;
import java.util.List;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

public final class HandlerClassFactory {

    private static final Logger logger = MiscUtils.getLogger();

    private HandlerClassFactory() {
        // don't instantiate
    }

    /*
     *  Create and return the message handler corresponding to the message type
     */
    public static MessageHandler getHandler(String type) {
        Document doc = null;
        String msgType;
        String msgHandler = "";

        logger.info("HandlerClassFactory.getHandler: Getting handler for type: {}", LogSanitizer.sanitize(type));
        
        if (type == null || type.equals("")) {
            logger.debug("Type not specified using Default Handler");
            return (new DefaultHandler());
        }
        try (InputStream is = HandlerClassFactory.class.getClassLoader().getResourceAsStream("io/github/carlos_emr/carlos/lab/ca/all/upload/message_config.xml")) {
            logger.info("HandlerClassFactory.getHandler: Loading config from io/github/carlos_emr/carlos/lab/ca/all/upload/message_config.xml, stream is: " + (is != null ? "valid" : "null"));

            SAXBuilder parser = new SAXBuilder();
            doc = parser.build(is);
            Element root = doc.getRootElement();

            @SuppressWarnings("unchecked")
            List items = root.getChildren();
            logger.info("HandlerClassFactory.getHandler: Found " + items.size() + " handler configs");
            for (int i = 0; i < items.size(); i++) {
                Element e = (Element) items.get(i);
                msgType = e.getAttributeValue("name");
                String className = e.getAttributeValue("className");
                logger.debug("HandlerClassFactory.getHandler: Checking handler - name: " + msgType + ", className: " + className + ", looking for: " + type);
                if (msgType.equals(type) && (className.indexOf(".") == -1)) {
                    msgHandler = "io.github.carlos_emr.carlos.lab.ca.all.upload.handlers." + e.getAttributeValue("className");
                    logger.info("HandlerClassFactory.getHandler: Found matching handler for " + type + ", will use class: " + msgHandler);
                }
                if (msgType.equals(type) && (className.indexOf(".") != -1)) {
                    msgHandler = className;
                    logger.info("HandlerClassFactory.getHandler: Found matching handler for " + type + ", will use fully qualified class: " + msgHandler);
                }
            }
        } catch (Exception e) {
            logger.error("Could not parse config file", e);
        }
        // create and return the message handler
        if (msgHandler.equals("")) {
            logger.warn("HandlerClassFactory.getHandler: No handler found for type '" + type + "', using DefaultHandler");
            return (new DefaultHandler());
        } else {
            try {
                logger.info("HandlerClassFactory.getHandler: Attempting to create handler class: " + msgHandler);
                @SuppressWarnings("unchecked")
                Class classRef = Class.forName(msgHandler);
                MessageHandler mh = (MessageHandler) classRef.newInstance();
                logger.info("HandlerClassFactory.getHandler: Message handler '" + msgHandler + "' created successfully");
                return (mh);
            } catch (Exception e) {
                logger.error("HandlerClassFactory.getHandler: Could not create message handler: " + msgHandler + ", Using default message handler instead", e);
                return (new DefaultHandler());
            }
        }
    }

    // this method is added to get the DefaultHandler during Private/Decryption Key upload in HRM
    public static DefaultHandler getDefaultHandler() {
        logger.debug("Type not specified using Default Handler");
        return (new DefaultHandler());
    }

}
