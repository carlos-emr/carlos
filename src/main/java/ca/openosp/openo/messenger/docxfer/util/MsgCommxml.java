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


package ca.openosp.openo.messenger.docxfer.util;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.binary.Base64;
import ca.openosp.openo.utility.MiscUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * XML utility class for the messaging and document transfer system.
 * 
 * <p>This class provides common XML operations used throughout the messenger module,
 * including document creation, parsing, serialization, and Base64 encoding/decoding.
 * It simplifies DOM manipulation and provides error-safe methods for XML processing.</p>
 * 
 * <p>Key functionality includes:
 * <ul>
 *   <li>Creating new XML documents</li>
 *   <li>Adding elements to DOM trees</li>
 *   <li>Converting DOM to XML strings</li>
 *   <li>Parsing XML strings to DOM</li>
 *   <li>Base64 encoding/decoding for binary data in XML</li>
 * </ul>
 * </p>
 * 
 * @version 1.0
 * @since 2002
 * @see MsgUtil
 */
public class MsgCommxml {
    /**
     * Creates a new empty XML document.
     * 
     * @return A new Document object, or null if creation fails
     */
    public static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adds a new element node to the specified parent.
     * 
     * <p>Convenience method that creates an empty element with the given name.</p>
     * 
     * @param parentNode The parent node to add the element to
     * @param name The name of the element to create
     * @return The newly created and appended Element
     */
    public static Element addNode(Node parentNode, String name) {
        return addNode(parentNode, name, null);
    }

    /**
     * Adds a new element node with text content to the specified parent.
     * 
     * <p>Creates an element with the given name and optional text value.
     * The method handles both Document nodes and Element nodes as parents.</p>
     * 
     * @param parentNode The parent node to add the element to
     * @param name The name of the element to create
     * @param value Optional text content for the element (null for no text)
     * @return The newly created and appended Element
     */
    public static Element addNode(Node parentNode, String name, String value) {
        Element node = null;

        // Create element based on parent node type
        if (parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            node = ((Document) parentNode).createElement(name);
        } else {
            node = parentNode.getOwnerDocument().createElement(name);
        }

        // Add text content if provided
        if (value != null) {
            node.appendChild(node.getOwnerDocument().createTextNode(value));
        }

        return (Element) parentNode.appendChild(node);
    }

    /**
     * Serializes a DOM node to an XML string.
     * 
     * <p>Converts a DOM Document or Element to its XML string representation
     * using XSLT transformation.</p>
     * 
     * @param xmlDoc The DOM node to serialize
     * @return The XML string representation, or empty string on error
     */
    public static String toXML(Node xmlDoc) {
        StringWriter ret = new StringWriter();

        DOMSource src = new DOMSource(xmlDoc);
        StreamResult rslt = new StreamResult(ret);

        try {
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.transform(src, rslt);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return ret.toString();
    }

    /**
     * Parses an XML string into a DOM Document.
     * 
     * <p>Converts an XML string representation back into a DOM Document
     * for manipulation and processing.</p>
     * 
     * @param xmlInput The XML string to parse
     * @return The parsed Document object, or null if parsing fails
     */
    public static Document parseXML(String xmlInput) {
        try {
            InputSource is = new InputSource(new StringReader(xmlInput));

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);

            return doc;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses an XML file into a DOM Document.
     * 
     * <p>Reads and parses an XML file from the filesystem into a DOM Document
     * for processing. Unlike {@link #parseXML(String)}, this method throws
     * exceptions for proper error handling.</p>
     * 
     * @param fileName The path to the XML file to parse
     * @return The parsed Document object
     * @throws java.io.FileNotFoundException if the file doesn't exist
     * @throws javax.xml.parsers.ParserConfigurationException if parser configuration fails
     * @throws java.io.IOException if there's an I/O error reading the file
     * @throws org.xml.sax.SAXException if the XML is malformed
     */
    public static Document parseXMLFile(String fileName) throws java.io.FileNotFoundException, javax.xml.parsers.ParserConfigurationException, java.io.IOException, org.xml.sax.SAXException {
        InputSource is = new InputSource(new java.io.FileReader(fileName));

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);

        return doc;
    }

    /**
     * Extracts all text content from a DOM node.
     * 
     * <p>Recursively collects all text node values from the specified node
     * and its children, concatenating them into a single string.</p>
     * 
     * @param node The DOM node to extract text from
     * @return The concatenated text content, or empty string if no text exists
     */
    public static String getText(Node node) {
        String ret = "";

        if (node.hasChildNodes()) {
            int i;
            for (i = 0; i < node.getChildNodes().getLength(); i++) {
                Node sub = node.getChildNodes().item(i);

                // Only process text nodes
                if (sub.getNodeType() == Node.TEXT_NODE) {
                    if (sub.getNodeValue() != null) {
                        ret += sub.getNodeValue();
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Encodes a string to Base64 format.
     * 
     * <p>Used for encoding binary data or special characters for safe
     * inclusion in XML documents, particularly for attachments.</p>
     * 
     * @param plainText The plain text string to encode
     * @return The Base64 encoded string
     */
    public static String encode64(String plainText) {
        return (new String(Base64.encodeBase64(plainText.getBytes())));
    }

    /**
     * Decodes a Base64 encoded string back to plain text.
     * 
     * <p>Used for decoding Base64 data retrieved from XML documents,
     * particularly for attachments and binary content.</p>
     * 
     * @param encodedText The Base64 encoded string to decode
     * @return The decoded plain text string
     */
    public static String decode64(String encodedText) {
        return (new String(Base64.decodeBase64(encodedText.getBytes())));
    }
}
