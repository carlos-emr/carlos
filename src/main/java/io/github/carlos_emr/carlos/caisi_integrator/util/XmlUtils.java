package io.github.carlos_emr.carlos.caisi_integrator.util;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import javax.xml.transform.TransformerConfigurationException;
import java.io.StringWriter;
import org.w3c.dom.Element;
import org.apache.commons.codec.binary.Base64;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import java.io.IOException;
import org.w3c.dom.Document;
import java.io.ByteArrayOutputStream;
import javax.xml.transform.TransformerException;
import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.TransformerFactory;
import java.io.OutputStream;
import org.w3c.dom.Node;

public class XmlUtils
{
    public static void writeNode(final Node node, final OutputStream os) throws TransformerException {
        final TransformerFactory factory = TransformerFactory.newInstance();
        final Transformer transformer = factory.newTransformer();
        final DOMSource domSource = new DOMSource(node);
        final StreamResult streamResult = new StreamResult(os);
        transformer.transform(domSource, streamResult);
    }
    
    public static String toString(final Node node) throws TransformerException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeNode(node, baos);
        return baos.toString();
    }
    
    public static Document toDocument(final String s) throws IOException, SAXException, ParserConfigurationException {
        return toDocument(s.getBytes());
    }
    
    public static Document toDocument(final byte[] x) throws IOException, SAXException, ParserConfigurationException {
        final ByteArrayInputStream is = new ByteArrayInputStream(x, 0, x.length);
        return toDocument(is);
    }
    
    public static Document toDocument(final InputStream is) throws ParserConfigurationException, SAXException, IOException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document document = builder.parse(is);
        return document;
    }
    
    public static Document newDocument(final String rootName) throws ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document doc = builder.newDocument();
        doc.appendChild(doc.createElement(rootName));
        return doc;
    }
    
    public static void appendChildToRoot(final Document doc, final String childName, final byte[] childContents) {
        appendChild(doc, doc.getFirstChild(), childName, new String(Base64.encodeBase64(childContents)));
    }
    
    public static void appendChildToRoot(final Document doc, final String childName, final String childContents) {
        appendChild(doc, doc.getFirstChild(), childName, childContents);
    }
    
    public static void appendChild(final Document doc, final Node parentNode, final String childName, final String childContents) {
        if (childContents == null) {
            throw new NullPointerException("ChildNode is null.");
        }
        final Element child = doc.createElement(childName);
        child.setTextContent(childContents);
        parentNode.appendChild(child);
    }
    
    public static String toFormattedString(final Document doc) throws IOException {
        final DOMSource domSource = new DOMSource(doc);
        final StringWriter stringWriter = new StringWriter();
        final StreamResult streamResult = new StreamResult(stringWriter);
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        String formattedString = "";
        try {
            final Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(domSource, streamResult);
        }
        catch (final TransformerConfigurationException e) {
            MiscUtils.getLogger().error((Object) e);
        }
        catch (final TransformerException e2) {
            MiscUtils.getLogger().error((Object) e2);
        }
        finally {
            if (stringWriter != null) {
                formattedString = stringWriter.toString();
                stringWriter.close();
            }
        }
        MiscUtils.getLogger().info((Object) ("Serializing XML file " + formattedString));
        return formattedString;
    }
    
    public static Node getChildNode(final Node node, final String name) {
        final NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); ++i) {
            final Node temp = nodeList.item(i);
            if (temp.getNodeType() == 1) {
                if (name.equals(temp.getLocalName()) || name.equals(temp.getNodeName())) {
                    return temp;
                }
            }
        }
        return null;
    }
    
    public static String getChildNodeTextContents(final Node node, final String name) {
        final Node tempNode = getChildNode(node, name);
        if (tempNode != null) {
            return tempNode.getTextContent();
        }
        return null;
    }
    
    public static String getAttributeValue(final Node node, final String attributeName) {
        final NamedNodeMap attributes = node.getAttributes();
        if (attributes == null) {
            return null;
        }
        final Node tempNode = attributes.getNamedItem(attributeName);
        if (tempNode == null) {
            return null;
        }
        return tempNode.getNodeValue();
    }
    
    public static void main(final String... argv) throws Exception {
        final Document doc = newDocument("testRoot");
        appendChildToRoot(doc, "testChild1", "test child< bla< > contents");
        appendChildToRoot(doc, "testChild2", "test child contents 2");
        MiscUtils.getLogger().info((Object) toString(doc));
    }
}
