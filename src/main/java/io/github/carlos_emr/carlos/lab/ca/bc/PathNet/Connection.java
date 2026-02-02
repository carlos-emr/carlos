//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Andromedia. All Rights Reserved.
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
 * This software was written for
 * Andromedia, to be provided as
 * part of the OSCAR McMaster
 * EMR System
 */


package io.github.carlos_emr.carlos.lab.ca.bc.PathNet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.Communication.HTTP;

/*
 * @author Jesse Bank
 * For The Oscar McMaster Project
 * Developed By Andromedia
 * www.andromedia.ca
 */

/*
 * Created on Mar 17, 2004
 *
 */

public class Connection {
    private static Logger logger = MiscUtils.getLogger();

    private boolean secure;
    private String url;
    private static final
    String LoginQuery = "Page=Login&Mode=Silent&UserID=@username&Password=@password",
            RequestNewQuery = "Page=HL7&Query=NewRequests",
            RequestNewPendingQuery = "Page=HL7&Query=NewRequests&Pending=Yes",
            PositiveAckQuery = "Page=HL7&ACK=Positive",
            NegativeAckQuery = "Page=HL7&ACK=Negative",
            LogoutQuery = "Logout=Yes";
    private HTTP http;

    public Connection() {
        this.url = OscarProperties.getInstance().getProperty("pathnet_url");
        this.http = new HTTP(this.url);
    }

    public boolean Open(String username, String password) {
        boolean success = true;
        try {
            String response = this.CreateString(LoginQuery.replaceAll("@username", username).replaceAll("@password", password));
            success = (response.toUpperCase().indexOf("ACCESSGRANTED") > -1);
        } catch (Exception ex) {
            logger.error("Error - oscar.PathNet.Connection.Open - Message: " + ex.getMessage(), ex);
            success = false;
        }
        return success;
    }

    public void Close() {
        try {
            this.CreateInputStream(LogoutQuery).close();
        } catch (Exception ex) {
            logger.error("Error - oscar.PathNet.Connection.Close - Message: " + ex.getMessage(), ex);
        }
    }

    public ArrayList<String> Retrieve() {
        ArrayList<String> messages = null;
        try {
            Document document = this.CreateDocument(this.CreateInputStream(RequestNewQuery));

            if (document.getDocumentElement().getAttribute("MessageFormat").toUpperCase().equals("ORUR01") && document.getDocumentElement().getAttribute("Version").toUpperCase().equals("2.3")) {
                if (document.getDocumentElement().getAttribute("MessageCount").equals(String.valueOf(document.getDocumentElement().getChildNodes().getLength()))) {
                    messages = new ArrayList<String>(document.getDocumentElement().getChildNodes().getLength());
                    for (int i = 0; i < document.getDocumentElement().getChildNodes().getLength(); i++) {
                        messages.add(document.getDocumentElement().getChildNodes().item(i).getFirstChild().getNodeValue());
                    }
                } else {
                    this.Acknowledge(false);
                }
            }
        } catch (Exception ex) {
            logger.error("Error - oscar.PathNet.Connection.Retrieve - Message: " + ex.getMessage(), ex);
        }
        return messages;
    }

    public ArrayList<String> Retrieve(InputStream is) {
        ArrayList<String> messages = null;
        try {
            Document document = this.CreateDocument(is);

            if (document.getDocumentElement().getAttribute("MessageFormat").toUpperCase().equals("ORUR01") && document.getDocumentElement().getAttribute("Version").toUpperCase().equals("2.3")) {
                if (document.getDocumentElement().getAttribute("MessageCount").equals(String.valueOf(document.getDocumentElement().getChildNodes().getLength()))) {
                    messages = new ArrayList<String>(document.getDocumentElement().getChildNodes().getLength());
                    for (int i = 0; i < document.getDocumentElement().getChildNodes().getLength(); i++) {
                        messages.add(document.getDocumentElement().getChildNodes().item(i).getFirstChild().getNodeValue());
                    }
                } else {
                    this.Acknowledge(false);
                }
            }
        } catch (Exception ex) {
            logger.error("Error - oscar.PathNet.Connection.Retrieve - Message: " + ex.getMessage(), ex);
        }
        return messages;
    }


    public void Acknowledge(boolean success) {
        try {
            this.CreateInputStream((success ? PositiveAckQuery : NegativeAckQuery)).close();
        } catch (Exception ex) {
            logger.error("Error - oscar.PathNet.Connection.Acknowledge - Message: " + ex.getMessage(), ex);
        }
    }

    public Document CreateDocument(InputStream input) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            // Disable external entities
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            // Disable XInclude
            factory.setXIncludeAware(false);
            
            // Disable expansion of entity references
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            logger.error ("Failed ton configure XML parser to prevent XXE", e);
            return null;
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(input);
    }

    private InputStream CreateInputStream(String queryString) throws IOException {
        return this.http.Get(queryString);
    }

    private String CreateString(String queryString) throws IOException {
        return this.http.GetString(queryString);
    }
}
