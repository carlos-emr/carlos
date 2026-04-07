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


package io.github.carlos_emr.carlos.messenger.util;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Utility class providing XML document manipulation and processing functionality
 * for the messenger system. This class offers static methods for creating,
 * parsing, and transforming XML documents and elements.
 * 
 * @since 2003
 */
public class Msgxml {
    
    /**
     * Creates a new empty XML document using the default DocumentBuilderFactory.
     * This method provides a simple way to create a new XML document for manipulation.
     * 
     * @return Document a new empty XML document, or null if creation fails
     * @since 2003
     */
    public static Document newDocument() {
        try {
            return XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adds a new XML element with the specified name to a parent node.
     * This is a convenience method that calls addNode(parentNode, name, null).
     * 
     * @param parentNode Node the parent node to add the new element to
     * @param name String the name/tag of the new element to create
     * @return Element the newly created and added element
     * @since 2003
     */
    public static Element addNode(Node parentNode, String name) {
        return addNode(parentNode, name, null);
    }

    /**
     * Adds a new XML element with the specified name and text content to a parent node.
     * Creates the element using the appropriate document context and optionally adds
     * text content if a value is provided.
     * 
     * @param parentNode Node the parent node to add the new element to
     * @param name String the name/tag of the new element to create
     * @param value String the text content to add to the element, or null for empty element
     * @return Element the newly created and added element
     * @since 2003
     */
    public static Element addNode(Node parentNode, String name, String value) {
        Element node = null;

        // Create element using the appropriate document context
        if (parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            node = ((Document) parentNode).createElement(name);
        } else {
            node = parentNode.getOwnerDocument().createElement(name);
        }

        // Add text content if value is provided
        if (value != null) {
            node.appendChild(node.getOwnerDocument().createTextNode(value));
        }

        return (Element) parentNode.appendChild(node);
    }

    /**
     * Converts an XML document to its string representation.
     * Uses JAXP Transformer to serialize the document to XML text format.
     * 
     * @param xmlDoc Document the XML document to convert to string
     * @return String the XML document as a string, or empty string if transformation fails
     * @since 2003
     */
    public static String toXML(Document xmlDoc) {
        StringWriter ret = new StringWriter();

        // Set up DOM source and string result for transformation
        DOMSource src = new DOMSource(xmlDoc);
        StreamResult rslt = new StreamResult(ret);

        try {
            // Transform the document to string representation
            Transformer trans = XmlUtils.createSecureTransformerFactory().newTransformer();
            trans.transform(src, rslt);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return ret.toString();
    }

    /**
     * Parses an XML string and returns the corresponding Document object.
     * Uses DocumentBuilderFactory to parse the XML string into a DOM document.
     * 
     * @param xmlInput String containing XML text to parse
     * @return Document the parsed XML document, or null if parsing fails
     * @since 2003
     */
    public static Document parseXML(String xmlInput) {
        try {
            // Create input source from the XML string
            InputSource is = new InputSource(new StringReader(xmlInput));

            // Parse the XML string into a Document
            Document doc = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().parse(is);

            return doc;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts all text content from an XML node and its child text nodes.
     * Iterates through all child nodes and concatenates text from TEXT_NODE types.
     * 
     * @param node Node the XML node to extract text content from
     * @return String the concatenated text content of the node, or empty string if no text
     * @since 2003
     */
    public static String getText(Node node) {
        String ret = "";

        // Check if the node has child nodes to process
        if (node.hasChildNodes()) {
            // Iterate through all child nodes
            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node sub = node.getChildNodes().item(i);

                // Only process text nodes and concatenate their values
                if (sub.getNodeType() == Node.TEXT_NODE) {
                    ret += sub.getNodeValue();
                }
            }
        }

        return ret;
    }
}
