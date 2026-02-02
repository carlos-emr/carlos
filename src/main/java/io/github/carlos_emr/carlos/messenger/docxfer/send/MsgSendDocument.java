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


package io.github.carlos_emr.carlos.messenger.docxfer.send;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;

/**
 * Utility class for processing and filtering XML documents before transmission in the messaging system.
 * 
 * <p>This class handles the selective inclusion of document items based on user selections,
 * typically used when sending medical documents with optional sections. It processes XML
 * documents by removing unselected items and cleaning up empty table structures.</p>
 * 
 * <p>Key functionality includes:
 * <ul>
 *   <li>Parsing XML documents with selectable items</li>
 *   <li>Filtering items based on checkbox selections</li>
 *   <li>Removing empty table elements after filtering</li>
 *   <li>Preserving non-removable items regardless of selection</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgCommxml
 * @see MsgGenerate
 */
public class MsgSendDocument {
    /**
     * Filters an XML document based on selected checkboxes, removing unselected items.
     * 
     * <p>This method processes an XML document and removes items that were not selected
     * by the user (not present in the checks string). Items marked as non-removable
     * are preserved regardless of selection. After filtering, any empty table elements
     * are also removed.</p>
     * 
     * @param originalDocument The original XML document as a string
     * @param checks Comma-separated string of selected item IDs (e.g., "item1,item2,")
     * @return The filtered XML Document with only selected items
     */
    public Document parseChecks(String originalDocument, String checks) {
        Document doc = MsgCommxml.parseXML(originalDocument);
        Element root = doc.getDocumentElement();

        NodeList items = root.getElementsByTagName("item");

        // Process items in reverse to safely remove nodes while iterating
        for (int i = items.getLength() - 1; i >= 0; i--) {
            Element item = (Element) items.item(i);

            // Keep items marked as non-removable
            if (!item.getAttribute("removable").equalsIgnoreCase("false")) {
                // Remove item if its ID is not in the selection list
                if (checks.indexOf(item.getAttribute("itemId") + ",") < 0) {
                    item.getParentNode().removeChild(item);
                }
            }
        }

        // Clean up empty tables after item removal
        NodeList tbls = root.getElementsByTagName("table");
        for (int i = 0; i < tbls.getLength(); i++) {
            Element tbl = (Element) tbls.item(i);

            if (!tbl.hasChildNodes()) {
                tbl.getParentNode().removeChild(tbl);
            }
        }

        return doc;
    }

    /**
     * Filters an XML document and collects remaining table names.
     * 
     * <p>This method performs the same filtering as {@link #parseChecks(String, String)}
     * but additionally collects the names of all tables that remain after filtering
     * into the provided ArrayList.</p>
     * 
     * @param originalDocument The original XML document as a string
     * @param checks Comma-separated string of selected item IDs
     * @param aList ArrayList to populate with remaining table names
     * @return The filtered XML Document with only selected items
     */
    public Document parseChecks2(String originalDocument, String checks, java.util.ArrayList aList) {
        Document doc = MsgCommxml.parseXML(originalDocument);
        Element root = doc.getDocumentElement();

        NodeList items = root.getElementsByTagName("item");

        // since we're removing nodes, we must do this in reverse
        for (int i = items.getLength() - 1; i >= 0; i--) {
            Element item = (Element) items.item(i);

            if (!item.getAttribute("removable").equalsIgnoreCase("false")) {
                if (checks.indexOf(item.getAttribute("itemId") + ",") < 0) {
                    item.getParentNode().removeChild(item);
                }
            }
        }

        NodeList tbls = root.getElementsByTagName("table");
        for (int i = 0; i < tbls.getLength(); i++) {
            Element tbl = (Element) tbls.item(i);


            if (!tbl.hasChildNodes()) {
                tbl.getParentNode().removeChild(tbl);
            }
        }

        tbls = root.getElementsByTagName("table");
        for (int i = 0; i < tbls.getLength(); i++) {
            Element tbl = (Element) tbls.item(i);
            aList.add(tbl.getAttribute("name"));
        }

        return doc;
    }
}
