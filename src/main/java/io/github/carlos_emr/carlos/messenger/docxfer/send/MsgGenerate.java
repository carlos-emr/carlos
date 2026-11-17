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
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.messenger.docxfer.send;



import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.commn.dao.DocumentTransferDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;

/**
 * Generates XML documents containing patient demographic and clinical data for document transfer.
 * This class reads configuration from DocXferConfig.xml and generates structured XML documents
 * containing data from multiple database tables linked to a specific patient.
 * 
 * @since 2003
 */
public class MsgGenerate {
    private static Logger logger = MiscUtils.getLogger();

    /** The demographic number (patient ID) for the current document generation */
    int demographicNo;
    
    /** Sequential item identifier for tracking items within the generated document */
    int itemId;

    /**
     * Generates a complete XML document containing all configured data for a specific patient.
     * Reads the DocXferConfig.xml configuration file to determine which database tables
     * and fields should be included in the generated document.
     * 
     * @param demographicNo the unique identifier for the patient whose data should be included
     * @return Document the generated XML document containing all configured patient data
     * @throws java.sql.SQLException if database access fails during data retrieval
     * @since 2003
     */
    public Document getDocument(int demographicNo)
            throws java.sql.SQLException {
        this.demographicNo = demographicNo;
        this.itemId = 0;

        // Create the root XML document structure
        Document doc = MsgCommxml.newDocument();
        Element docRoot = MsgCommxml.addNode(doc, "root");

        // The export definition travels with the application in every WAR/deb installation.
        Document cfg;
        try (java.io.InputStream input = MsgGenerate.class.getResourceAsStream("/DocXferConfig.xml")) {
            if (input == null) throw new java.io.IOException("Missing document-transfer definition");
            cfg = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory()
                    .newDocumentBuilder().parse(input);
        } catch (Exception ex) {
            throw new java.sql.SQLException("Unable to load document-transfer definition", ex);
        }

        // Process each table configuration from the config file
        Element cfgRoot = cfg.getDocumentElement();
        NodeList cfgTables = cfgRoot.getChildNodes();
        for (int i = 0; i < cfgTables.getLength(); i++) {
            Node tbl = cfgTables.item(i);

            // Only process element nodes that represent table configurations
            if (tbl.getNodeType() == Node.ELEMENT_NODE) {
                if (((Element) tbl).getTagName().equals("table")) {
                    Element newTable = constructTable((Element) tbl, doc);
                    // Only add the table to the document if it contains data
                    if (newTable.hasChildNodes()) {
                        docRoot.appendChild(newTable);
                    }
                }
            }
        }
        return doc;
    }

    /**
     * Constructs an XML table element containing data from a database table as specified
     * in the configuration. Executes SQL queries to retrieve patient data and formats
     * it as XML elements with both content and raw data sections.
     * 
     * @param cfgTable Element containing the table configuration from DocXferConfig.xml
     * @param doc Document the parent XML document to create elements within
     * @return Element the constructed table element containing all retrieved data
     * @throws java.sql.SQLException if database access fails during data retrieval
     * @since 2003
     */
    private Element constructTable(Element cfgTable, Document doc)
            throws java.sql.SQLException {
        Element table = doc.createElement("table");

        // Copy all attributes from the configuration table to the result table
        NamedNodeMap map = cfgTable.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            Attr attr = (org.w3c.dom.Attr) map.item(i);
            table.setAttribute(attr.getNodeName(), attr.getNodeValue());
        }

        java.util.List<String> expressions = new java.util.ArrayList<>();
        Element configuredItem = (Element) cfgTable.getElementsByTagName("item").item(0);
        NodeList configuredFields = cfgTable.getElementsByTagName("fld");
        for (int i = 0; i < configuredFields.getLength(); i++) {
            expressions.add(((Element) configuredFields.item(i)).getAttribute("sql"));
        }
        DocumentTransferDao.Definition definition = new DocumentTransferDao.Definition(
                cfgTable.getAttribute("sqlFrom"), cfgTable.getAttribute("sqlLink"),
                cfgTable.getAttribute("sqlWhere"), cfgTable.getAttribute("sqlOrder"),
                configuredItem.getAttribute("sql"), expressions);
        {
            java.util.List<DocumentTransferDao.Row> rows = SpringUtils.getBean(DocumentTransferDao.class)
                    .read(definition, demographicNo);

            // Get the item and field configurations from the config
            Element cfgItem = (Element) cfgTable.getElementsByTagName("item").item(0);
            NodeList cfgFlds = cfgItem.getElementsByTagName("fld");

            // Process each row returned by the query
            for (DocumentTransferDao.Row row : rows) {
                Element item = MsgCommxml.addNode(table, "item");
                item.setAttribute("itemId", String.valueOf(itemId));
                itemId++; // Increment the sequential item ID
                item.setAttribute("name", cfgItem.getAttribute("name"));
                item.setAttribute("sql", cfgItem.getAttribute("sql"));
                item.setAttribute("removable", cfgItem.getAttribute("removable"));

                Element content = MsgCommxml.addNode(item, "content");
                Element data = MsgCommxml.addNode(item, "data");

                // Process all database columns except those starting with "fld"
                for (DocumentTransferDao.Column column : row.columns()) {
                    {
                        String name = column.qualifiedName();

                        // A failed clinical column read must fail the transfer, not export an
                        // apparently complete document with silently erased patient information.
                        String fldData = column.value();

                        Element fld = doc.createElement(name);

                        // Try to parse the field data as XML, fall back to text node
                        try {
                            Node tmp = MsgCommxml.parseXML(fldData).getDocumentElement();
                            Node fldSub = doc.importNode(tmp, true);
                            fld.appendChild(fldSub);
                        } catch (Exception ex) {
                            try {
                                Node tmp = doc.createTextNode(fldData);
                                fld.appendChild(tmp);
                            } catch (Exception ex2) {
                                // Ignore exceptions and leave field empty
                            }
                        }

                        data.appendChild(fld);
                    }
                }

                // Set the item value from the fldItem column
                {
                    String value = row.item();
                    if (value == null) value = "";
                    item.setAttribute("value", value);
                }

                // Process configured fields and add them to the content section
                for (int i = 0; i < cfgFlds.getLength(); i++) {
                    Element cfgFld = (Element) cfgFlds.item(i);
                    Element fld = MsgCommxml.addNode(content, "fld");

                    fld.setAttribute("name", cfgFld.getAttribute("name"));
                    fld.setAttribute("sql", cfgFld.getAttribute("sql"));
                    String value = row.fields().get(i);
                    if (value == null) value = "";
                    fld.setAttribute("value", value);
                }
            }
        }

        return table;
    }

}
