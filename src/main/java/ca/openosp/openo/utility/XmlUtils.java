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
package ca.openosp.openo.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public final class XmlUtils {
    private static Logger logger = MiscUtils.getLogger();

    public XmlUtils() {
    }

    public static void setLsSeriliserToFormatted(LSSerializer lsSerializer) {
        lsSerializer.getDomConfig().setParameter("format-pretty-print", true);
    }

    public static void writeNode(Node node, OutputStream os, boolean formatted) throws ClassCastException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        DOMImplementationRegistry domImplementationRegistry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS domImplementationLS = (DOMImplementationLS) domImplementationRegistry.getDOMImplementation("LS");
        LSOutput lsOutput = domImplementationLS.createLSOutput();
        lsOutput.setEncoding("UTF-8");
        lsOutput.setByteStream(os);
        LSSerializer lsSerializer = domImplementationLS.createLSSerializer();
        if (formatted) {
            setLsSeriliserToFormatted(lsSerializer);
        }

        lsSerializer.write(node, lsOutput);
    }

    public static byte[] toBytes(Node node, boolean formatted) throws ClassCastException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeNode(node, baos, formatted);
        return baos.toByteArray();
    }

    public static String toString(Node node, boolean formatted) throws ClassCastException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeNode(node, baos, formatted);
        return baos.toString();
    }

    public static Document toDocumentFromFile(String url) throws ParserConfigurationException, SAXException, IOException {
        InputStream is = XmlUtils.class.getResourceAsStream(url);
        if (is == null) {
            is = new FileInputStream(url);
        }

        Document var2;
        try {
            var2 = toDocument((InputStream) is);
        } finally {
            ((InputStream) is).close();
        }

        return var2;
    }

    public static Document toDocument(String s) throws IOException, SAXException, ParserConfigurationException {
        return toDocument(s.getBytes("UTF-8"));
    }

    public static Document toDocument(byte[] x) throws IOException, SAXException, ParserConfigurationException {
        ByteArrayInputStream is = new ByteArrayInputStream(x, 0, x.length);
        return toDocument((InputStream) is);
    }

    public static Document toDocument(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(is);
        return document;
    }

    public static Document newDocument(String rootName) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        doc.appendChild(doc.createElement(rootName));
        return doc;
    }

    /**
     * Validates and sanitizes XML content by parsing with XXE protections and re-serializing.
     *
     * <p>This method provides OWASP-compliant XXE (XML External Entity) protection by
     * round-tripping XML through a secure parser. Any external entity references are
     * neutralized (resolved to empty) during parsing, and the clean result is returned.</p>
     *
     * <p>This approach:</p>
     * <ul>
     *   <li>Neutralizes SYSTEM entity declarations (they resolve to empty)</li>
     *   <li>Preserves valid XML structure for JasperReports templates</li>
     *   <li>Returns sanitized bytes safe for storage and later compilation</li>
     * </ul>
     *
     * @param xmlData the XML content as a byte array to sanitize
     * @return sanitized XML bytes with external entities neutralized
     * @throws IllegalArgumentException if the XML content is invalid
     * @throws SecurityException if the parser cannot be configured securely
     * @since 2026-01-22
     * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html">
     *      OWASP XXE Prevention Cheat Sheet</a>
     */
    public static byte[] sanitizeXml(byte[] xmlData) throws IllegalArgumentException {
        if (xmlData == null || xmlData.length == 0) {
            throw new IllegalArgumentException("XML data cannot be null or empty");
        }

        try {
            // Configure secure DocumentBuilderFactory
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            // Parse with secure settings (external entities become empty)
            DocumentBuilder builder = dbf.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlData));

            // Serialize back to bytes
            return toBytes(doc, false);

        } catch (ParserConfigurationException e) {
            logger.error("Failed to configure secure XML parser: {}", e.getMessage());
            throw new SecurityException("XML parser configuration error", e);
        } catch (SAXException e) {
            logger.warn("XML parsing failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid XML content", e);
        } catch (IOException e) {
            logger.warn("Failed to read XML content: {}", e.getMessage());
            throw new IllegalArgumentException("Cannot read XML content", e);
        } catch (Exception e) {
            logger.error("Failed to serialize sanitized XML: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to process XML content", e);
        }
    }

    public static void appendChildToRoot(Document doc, String childName, byte[] childContents) {
        appendChild(doc, doc.getFirstChild(), childName, new String(Base64.encodeBase64(childContents)));
    }

    public static void appendChildToRoot(Document doc, String childName, String childContents) {
        appendChild(doc, doc.getFirstChild(), childName, childContents);
    }

    public static void appendChildToRootIgnoreNull(Document doc, String childName, String childContents) {
        if (childContents != null) {
            appendChildToRoot(doc, childName, childContents);
        }
    }

    public static void appendChild(Document doc, Node parentNode, String childName, String childContents) {
        if (childContents == null) {
            throw new NullPointerException("ChildNode is null.");
        } else {
            Element child = doc.createElement(childName);
            child.setTextContent(childContents);
            parentNode.appendChild(child);
        }
    }

    public static void replaceChild(Document doc, Node parentNode, String childName, String childContents) {
        Node node = getChildNode(parentNode, childName);
        if (childContents == null) {
            if (node != null) {
                parentNode.removeChild(node);
            }

        } else {
            if (node == null) {
                appendChild(doc, parentNode, childName, childContents);
            } else {
                node.setTextContent(childContents);
            }

        }
    }

    public static Node getChildNode(Node node, String name) {
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node temp = nodeList.item(i);
            if (temp.getNodeType() == 1 && (name.equals(temp.getLocalName()) || name.equals(temp.getNodeName()))) {
                return temp;
            }
        }

        return null;
    }

    public static ArrayList<Node> getChildNodes(Node node, String name) {
        ArrayList<Node> results = new ArrayList();
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node temp = nodeList.item(i);
            if (temp.getNodeType() == 1 && (name.equals(temp.getLocalName()) || name.equals(temp.getNodeName()))) {
                results.add(temp);
            }
        }

        return results;
    }

    public static String getChildNodeTextContents(Node node, String name) {
        Node tempNode = getChildNode(node, name);
        return tempNode != null ? tempNode.getTextContent() : null;
    }

    public static Long getChildNodeLongContents(Node node, String name) {
        String s = getChildNodeTextContents(node, name);
        return s != null ? Long.valueOf(s) : null;
    }

    public static Integer getChildNodeIntegerContents(Node node, String name) {
        String s = getChildNodeTextContents(node, name);
        return s != null ? Integer.valueOf(s) : null;
    }

    public static Boolean getChildNodeBooleanContents(Node node, String name) {
        String s = getChildNodeTextContents(node, name);
        return s != null ? Boolean.valueOf(s) : null;
    }

    public static ArrayList<String> getChildNodesTextContents(Node node, String name) {
        ArrayList<String> results = new ArrayList();
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node temp = nodeList.item(i);
            if (temp.getNodeType() == 1 && (name.equals(temp.getLocalName()) || name.equals(temp.getNodeName()))) {
                results.add(temp.getTextContent());
            }
        }

        return results;
    }

    public static void removeAllChildNodes(Node node, String name) {
        ArrayList<Node> removeList = getChildNodes(node, name);
        Iterator i$ = removeList.iterator();

        while (i$.hasNext()) {
            Node temp = (Node) i$.next();
            node.removeChild(temp);
        }

    }

    public static String getAttributeValue(Node node, String attributeName) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return null;
        } else {
            Node tempNode = attributes.getNamedItem(attributeName);
            return tempNode == null ? null : tempNode.getNodeValue();
        }
    }

    public static <V> Document toXml(Map<String, V> map) throws ParserConfigurationException {
        Document doc = newDocument("XmlMap");
        Node rootNode = doc.getFirstChild();
        Iterator i$ = map.entrySet().iterator();

        while (i$.hasNext()) {
            Entry<String, V> mapEntry = (Entry) i$.next();
            Element xmlEntry = doc.createElement("entry");
            rootNode.appendChild(xmlEntry);
            Element xmlKey = doc.createElement("key");
            xmlKey.setTextContent((String) mapEntry.getKey());
            xmlEntry.appendChild(xmlKey);
            Element xmlValue = doc.createElement("value");
            xmlValue.setTextContent(mapEntry.getValue().toString());
            xmlEntry.appendChild(xmlValue);
            if (!String.class.equals(mapEntry.getValue().getClass())) {
                Element xmlValueType = doc.createElement("valueType");
                xmlValueType.setTextContent(mapEntry.getValue().getClass().getName());
                xmlEntry.appendChild(xmlValueType);
            }
        }

        return doc;
    }

    public static HashMap<String, Object> toMap(Document doc) {
        HashMap<String, Object> result = new HashMap();
        copyToMap(doc, result);
        return result;
    }

    public static void copyToMap(Document doc, Map<String, Object> map) {
        Node rootNode = doc.getFirstChild();
        ArrayList<Node> entries = getChildNodes(rootNode, "entry");
        Iterator i$ = entries.iterator();

        while (i$.hasNext()) {
            Node node = (Node) i$.next();
            String key = getChildNodeTextContents(node, "key");
            String value = getChildNodeTextContents(node, "value");
            String valueType = getChildNodeTextContents(node, "valueType");
            if (valueType == null) {
                map.put(key, value);
            } else if (Boolean.class.getName().equals(valueType)) {
                map.put(key, Boolean.valueOf(value));
            } else if (Integer.class.getName().equals(valueType)) {
                map.put(key, Integer.valueOf(value));
            } else if (Long.class.getName().equals(valueType)) {
                map.put(key, Long.valueOf(value));
            } else if (Float.class.getName().equals(valueType)) {
                map.put(key, Float.valueOf(value));
            } else {
                logger.error("Missed type key/value/type=" + key + '/' + value + '/' + valueType);
            }
        }

    }
}