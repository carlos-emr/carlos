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


package io.github.carlos_emr.carlos.util;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * Utility class for XML document manipulation using the W3C DOM API.
 * Provides methods for creating, parsing, serializing, and traversing XML documents,
 * as well as extracting text content and attribute values into {@link Properties} maps.
 * Also includes XML escaping and unescaping routines.
 *
 * @since 2001-01-01
 */
public class UtilXML {

    /**
     * Creates a new empty XML {@link Document}.
     *
     * @return Document a new empty document, or null if creation fails
     */
    public static Document newDocument() {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            return document;
        } catch (Exception e) {
            Document document1 = null;
            return document1;
        }
    }

    /**
     * Adds a child element with the specified name to the parent node.
     *
     * @param parentNode Node the parent node to append the new element to
     * @param name String the tag name for the new element
     * @return Element the newly created and appended element
     */
    public static Element addNode(Node parentNode, String name) {
        return addNode(parentNode, name, null);
    }

    /**
     * Adds a child element with the specified name and optional text content to the parent node.
     *
     * @param parentNode Node the parent node to append the new element to
     * @param name String the tag name for the new element
     * @param value String the text content for the element, or null for an empty element
     * @return Element the newly created and appended element
     */
    public static Element addNode(Node parentNode, String name, String value) {
        Element node = null;
        if (parentNode.getNodeType() == 9)
            node = ((Document) parentNode).createElement(name);
        else
            node = parentNode.getOwnerDocument().createElement(name);
        if (value != null)
            node.appendChild(node.getOwnerDocument().createTextNode(value));
        return (Element) parentNode.appendChild(node);
    }

    /**
     * Serializes an XML {@link Document} to its string representation.
     *
     * @param xmlDoc Document the XML document to serialize
     * @return String the XML string
     */
    public static String toXML(Document xmlDoc) {
        StringWriter ret = new StringWriter();
        DOMSource src = new DOMSource(xmlDoc);
        StreamResult rslt = new StreamResult(ret);
        try {
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            //trans.setOutputProperty(OutputKeys.INDENT, "yes");
            //trans.setOutputProperty("{http://xml.apache.org/xslt}baseIndent-amount", "1");
            trans.transform(src, rslt);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return ret.toString();
    }

    /**
     * Serializes an XML {@link Document} to its string representation with a DOCTYPE system identifier.
     *
     * @param xmlDoc Document the XML document to serialize
     * @param dtdname String the DOCTYPE SYSTEM identifier to include in the output
     * @return String the XML string with DOCTYPE declaration
     */
    public static String toXML(Document xmlDoc, String dtdname) {
        StringWriter ret = new StringWriter();
        DOMSource src = new DOMSource(xmlDoc);
        StreamResult rslt = new StreamResult(ret);
        try {
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            trans.setOutputProperty(javax.xml.transform.OutputKeys.DOCTYPE_SYSTEM, dtdname);
            //trans.setOutputProperty("{http://xml.apache.org/xslt}baseIndent-amount", "1");
            trans.transform(src, rslt);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return ret.toString();
    }

    /**
     * Serializes an XML {@link Node} to its string representation.
     *
     * @param xmlDoc Node the XML node to serialize
     * @return String the XML string
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
     * Parses an XML string into a {@link Document}. Disables external entities and DTD
     * processing to prevent XXE attacks.
     *
     * @param xmlInput String the XML content to parse
     * @return Document the parsed document, or null if parsing fails
     */
    public static Document parseXML(String xmlInput) {
        Document document;
        try {
            InputSource is = new InputSource(new StringReader(xmlInput));
            DocumentBuilderFactory docBuilder = DocumentBuilderFactory.newInstance();

            // Disable external entities to prevent XXE attacks
            docBuilder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            docBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
            docBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            docBuilder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            
            // Disable XInclude
            docBuilder.setXIncludeAware(false);
            
            // Disabled expansion of entity references
            docBuilder.setExpandEntityReferences(false);
            
            Document doc = docBuilder.newDocumentBuilder().parse(is);
            Document document1 = doc;
            return document1;
        } catch (Exception e) {
            document = null;
        }
        return document;
    }

    /**
     * Parses an XML file into a {@link Document}.
     *
     * @param fileName String the path to the XML file
     * @return Document the parsed document
     * @throws IOException if the file cannot be read
     * @throws FileNotFoundException if the file does not exist
     * @throws Exception if XML parsing fails
     */
    public static Document parseXMLFile(String fileName)
            throws IOException, FileNotFoundException, Exception {
        InputSource is = new InputSource(new FileReader(fileName));
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        return doc;
    }


    /**
     * Extracts the text content from a node, including serialized XML of any child elements.
     *
     * @param node Node the node to extract text from
     * @return String the concatenated text content
     */
    public static String getText(Node node) {
        String ret = "";
        if (node.hasChildNodes()) {

            for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                Node sub = node.getChildNodes().item(i);
                if (sub.getNodeType() == 3)
                    ret += sub.getNodeValue();
                if (sub.getNodeType() == 1)
                    ret += toXML(sub);
            }

        }
        return ret;
    }

    /**
     * Appends a text node with the specified content to the given node and normalizes it.
     *
     * @param node Node the node to append text to
     * @param text String the text content to append
     */
    public static void setText(Node node, String text) {
        Text txt = node.getOwnerDocument().createTextNode(text);
        node.appendChild(txt);
        node.normalize();
    }


    static Properties prop = null;

    /**
     * Extracts tag name/text content pairs from the node tree into a {@link Properties} map.
     * Keys are tag names, values are the text content of those tags.
     *
     * @param node Node the root node to traverse
     * @return Properties the extracted name-value pairs
     */
    public static Properties getPropText(Node node) {
        prop = new Properties();
        setPropText(node);
        return prop;
    }

    /**
     * Recursively traverses the node tree and populates the static {@code prop} field
     * with tag name/text content pairs.
     *
     * @param node Node the current node being traversed
     */
    public static void setPropText(Node node) {
        NodeList list = node.getChildNodes();

        if (list != null) {
            for (int i = 0; i < list.getLength(); i++) {
                setPropText(list.item(i));
            }
        }

        if (node.getNodeType() == Node.TEXT_NODE) {
            prop.setProperty(node.getParentNode().getNodeName(), node.getNodeValue());
        }
    }

    /**
     * Extracts attribute value pairs from elements matching the specified tag name.
     * For each matching element, the value of {@code attrName1} becomes the key
     * and the value of {@code attrName2} becomes the value in the returned properties.
     *
     * @param node Node the root node to traverse
     * @param tagName String the tag name to match
     * @param attrName1 String the attribute whose value becomes the property key
     * @param attrName2 String the attribute whose value becomes the property value
     * @return Properties the extracted attribute pairs
     */
    public static Properties getPropText(Node node, String tagName, String attrName1, String attrName2) {
        prop = new Properties();
        setPropText(node, tagName, attrName1, attrName2);
        return prop;
    }

    /**
     * Recursively traverses the node tree and populates the static {@code prop} field
     * with pairs of attribute values from matching elements.
     *
     * @param node Node the current node being traversed
     * @param tag String the tag name to match
     * @param attr1 String the first attribute name (becomes property key)
     * @param attr2 String the second attribute name (becomes property value)
     */
    public static void setPropText(Node node, String tag, String attr1, String attr2) {
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        NodeList list = node.getChildNodes();

        if (list != null) {
            for (int i = 0; i < list.getLength(); i++) {
                setPropText(list.item(i), tag, attr1, attr2);
            }
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            if (node.getNodeName().equals(tag)) {
                NamedNodeMap attrib = node.getAttributes();
                for (int i = 0; i < attrib.getLength(); i++) {
                    Node curAttr = attrib.item(i);
                    if (curAttr.getNodeName().equals(attr1)) sb1 = new StringBuilder(curAttr.getNodeValue());
                    if (curAttr.getNodeName().equals(attr2)) sb2 = new StringBuilder(curAttr.getNodeValue());
                }
                prop.setProperty(sb1.toString(), sb2.toString());
            }
        }
    }

    /**
     * Extracts attribute-value/text-content pairs from elements matching the specified tag name.
     * For each matching element, the value of {@code attrName1} becomes the key
     * and the element's text content becomes the value.
     *
     * @param node Node the root node to traverse
     * @param tagName String the tag name to match
     * @param attrName1 String the attribute whose value becomes the property key
     * @return Properties the extracted pairs
     */
    public static Properties getPropText(Node node, String tagName, String attrName1) {
        prop = new Properties();
        setPropText(node, tagName, attrName1);
        return prop;
    }

    /**
     * Recursively traverses the node tree and populates the static {@code prop} field
     * with attribute value/text content pairs from matching elements.
     *
     * @param node Node the current node being traversed
     * @param tag String the tag name to match
     * @param attr1 String the attribute name whose value becomes the property key
     */
    public static void setPropText(Node node, String tag, String attr1) {
        String attrName = "";
        NodeList list = node.getChildNodes();

        if (list != null) {
            for (int i = 0; i < list.getLength(); i++) {
                setPropText(list.item(i), tag, attr1);
            }
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            if (node.getNodeName().equals(tag)) {
                NamedNodeMap attrib = node.getAttributes();
                for (int i = 0; i < attrib.getLength(); i++) {
                    Node curAttr = attrib.item(i);
                    if (curAttr.getNodeName().equals(attr1)) {
                        attrName = curAttr.getNodeValue();
                        NodeList oldlist = node.getChildNodes();
                        for (int j = 0; j < oldlist.getLength(); j++) {

                            if (oldlist.item(j).getNodeType() == Node.TEXT_NODE) {
                                prop.setProperty(attrName, oldlist.item(j).getNodeValue());
                            }

                        }

                    }
                }
            }
        }
    }

    /**
     * Finds the first element matching the specified tag name and attribute value,
     * and returns its serialized XML string.
     *
     * @param node Node the root node to search
     * @param tag String the tag name to match
     * @param attrName String the attribute name to check
     * @param attr1 String the attribute value to match
     * @return String the serialized XML of the matching element, or null if not found
     */
    public static String getNodeXML(Node node, String tag, String attrName, String attr1) {
        String ret = null;
        NodeList list = node.getChildNodes();

        if (list != null) {
            for (int i = 0; i < list.getLength(); i++) {
                ret = getNodeXML(list.item(i), tag, attrName, attr1);
                if (ret != null) return ret;
            }
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            if (node.getNodeName().equals(tag)) {
                NamedNodeMap attrib = node.getAttributes();
                for (int i = 0; i < attrib.getLength(); i++) {
                    Node curAttr = attrib.item(i);
                    if (curAttr.getNodeName().equals(attrName) && curAttr.getNodeValue().equals(attr1)) {
                        ret = toXML(node);
                        break;
                    }
                }
            }
        }
        return ret;
    }
   
   /*Escapes prepared xml, in other words it would escape something like this:
    <query>select * from books where dateEntered >=${BottomDate} and dateEntered<=${TopDate}<query>'
    *escapes '&' and '<'
    *-Paul
   */

    /**
     * Escapes ampersands and unmatched opening angle brackets in pre-formed XML content.
     * Matched XML tags (those with corresponding closing tags) are preserved.
     *
     * @param xml String the XML content to escape
     * @return String the escaped XML content
     */
    public static String escapeXML(String xml) {
        xml = xml.replaceAll("&", "&amp;");
        int pointer1 = 0;
        int pointer2 = 0;
        while ((pointer1 = xml.indexOf("<", pointer1)) != -1) {
            if (xml.charAt(pointer1 + 1) == '/' || xml.charAt(pointer1 + 1) == '?') {
                pointer1++;
                continue;
            }
            pointer2 = xml.indexOf(">", pointer1);
            if (xml.indexOf(" ", pointer1) < pointer2) {
                pointer2 = xml.indexOf(" ", pointer1);
            }
            String tag = xml.substring(pointer1, pointer2);
            String closetag = tag.substring(0, 1) + "/" + tag.substring(1);
            closetag += ">";

            if (xml.indexOf(closetag) == -1) {
                xml = xml.substring(0, pointer1) + "&lt;" + xml.substring(pointer1 + 1);

            }
            pointer1++;
        }
        return xml;
    }

    /**
     * Escapes all ampersands and angle brackets in the given string.
     *
     * @param xml String the string to escape
     * @return String the fully escaped string
     */
    public static String escapeAllXML(String xml) {
        xml = xml.replaceAll("&", "&amp;");
        xml = xml.replaceAll("<", "&lt;");
        return xml;
    }

    //escapes all xml inside and including a certain tag (Good to use before parsing with jdom)
    //'tag' parameter must be in the form <mytag>; no attributes;
    //must be a complete tag
    //Example:  <param><attr>hello</attr></param>  -->   &lt;param>&lt;attr>hello&lt;/attr>&lt;/param>
    //-Paul A
    public static String escapeAllXML(String xml, String tag) {
        String closetag = tag.substring(0, 1) + "/" + tag.substring(1);
        String opentag = tag.substring(0, tag.indexOf(">"));
        int pointer1 = 0;
        int pointer2 = 0;
        while ((pointer1 = xml.indexOf(opentag, pointer1)) != -1) {
            char terminationChar = xml.charAt(pointer1 + tag.length() - 1);
            if ((terminationChar != '>') && (terminationChar != ' ')) {
                pointer1++;
                continue;
            }
            //pointer1 = xml.indexOf(">", pointer1);
            pointer2 = xml.indexOf(closetag, pointer1) + closetag.length();
            String innerText = xml.substring(pointer1, pointer2);
            innerText = escapeAllXML(innerText);
            xml = xml.substring(0, pointer1) + innerText + xml.substring(pointer2);
            pointer1++;
        }
        return xml;
    }

    //reverses escapeAllXML()
    //Paul A
    public static String unescapeXML(String xml) {
        xml = xml.replaceAll("&amp;", "&");
        xml = xml.replaceAll("&lt;", "<");
        xml = xml.replaceAll("&gt;", ">");
        return xml;
    }

}
