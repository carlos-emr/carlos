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
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.Logger;
import org.jdom2.input.SAXBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSResourceResolver;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Static utility methods for XML parsing, serialization, and DOM manipulation.
 *
 * <p>Provides secure XML parser construction via {@link #createSecureSAXBuilder()},
 * {@link #createSecureDocumentBuilderFactory()}, {@link #createSecureSAXParserFactory()},
 * {@link #createSecureTransformerFactory()}, {@link #createSecureSchemaFactory(String)},
 * {@link #createSecureValidator(javax.xml.validation.Schema)},
 * and {@link #createSecureJaxbSource(InputStream)}. All factory methods restrict external
 * resource access to prevent XXE attacks (CWE-611).
 *
 * <p>Also includes DOM document building, node-to-string conversion, and element helper
 * methods used across CARLOS EMR for clinical data exchange (HL7, FHIR, e-forms).</p>
 *
 * @since 2012-01-12
 */
public final class XmlUtils {
    private static Logger logger = MiscUtils.getLogger();

    private XmlUtils() {
    }

    /**
     * Creates a {@link SAXBuilder} with XXE (XML External Entity) protections enabled.
     *
     * <p>Disables DOCTYPE declarations and external entity resolution to prevent XXE attacks.
     * Use this factory method instead of {@code new SAXBuilder()} throughout the codebase.
     *
     * <p>The critical {@code disallow-doctype-decl} feature is required — an
     * {@link IllegalStateException} is thrown if it cannot be applied so that callers
     * never receive an unprotected parser. The remaining defense-in-depth features are
     * applied on a best-effort basis; a warning is logged if any of them cannot be set.
     *
     * @return SAXBuilder configured with XXE protections
     * @throws IllegalStateException if the critical disallow-doctype-decl protection cannot be enabled
     */
    public static SAXBuilder createSecureSAXBuilder() {
        SAXBuilder parser = new SAXBuilder();
        // Critical protection — fail closed if it cannot be applied
        try {
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enable required XXE protection (disallow-doctype-decl)", ex);
        }
        // Defense-in-depth features — warn individually if unavailable
        try {
            parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ex) {
            logger.warn("Could not disable external-general-entities on SAXBuilder", ex);
        }
        try {
            parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ex) {
            logger.warn("Could not disable external-parameter-entities on SAXBuilder", ex);
        }
        try {
            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ex) {
            logger.warn("Could not disable load-external-dtd on SAXBuilder", ex);
        }
        try {
            parser.setExpandEntities(false);
        } catch (Exception ex) {
            logger.warn("Could not disable entity expansion on SAXBuilder", ex);
        }
        return parser;
    }

    /**
     * Creates a {@link DocumentBuilderFactory} with XXE protections enabled.
     *
     * <p>Disables DOCTYPE declarations and external entity resolution. Use this factory
     * method instead of {@code DocumentBuilderFactory.newInstance()} throughout the codebase.
     *
     * <p>The critical {@code disallow-doctype-decl} feature is required — a
     * {@link ParserConfigurationException} is thrown if it cannot be applied so that callers
     * never receive an unprotected factory. The remaining defense-in-depth features are
     * applied on a best-effort basis; a warning is logged if any of them cannot be set.
     *
     * @return DocumentBuilderFactory configured with XXE protections
     * @throws ParserConfigurationException if the critical disallow-doctype-decl protection cannot be enabled
     */
    public static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Critical protection — fail closed if it cannot be applied
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // Defense-in-depth features — warn individually if unavailable
        try {
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException ex) {
            logger.warn("Could not disable external-general-entities on DocumentBuilderFactory", ex);
        }
        try {
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException ex) {
            logger.warn("Could not disable external-parameter-entities on DocumentBuilderFactory", ex);
        }
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException ex) {
            logger.warn("Could not disable load-external-dtd on DocumentBuilderFactory", ex);
        }
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        return dbf;
    }

    /**
     * Creates a {@link TransformerFactory} with external access disabled.
     *
     * <p>Sets {@code ACCESS_EXTERNAL_DTD} and {@code ACCESS_EXTERNAL_STYLESHEET} to empty strings
     * so that no external DTD or stylesheet resources can be loaded during transformation.
     *
     * <p>The critical {@code ACCESS_EXTERNAL_DTD} attribute is required — an
     * {@link IllegalArgumentException} is propagated if it cannot be applied so that callers
     * never receive an unprotected factory. The {@code ACCESS_EXTERNAL_STYLESHEET} attribute
     * is applied on a best-effort basis; a warning is logged if it cannot be set.
     *
     * @return TransformerFactory configured with external-access restrictions
     * @throws IllegalArgumentException if the critical ACCESS_EXTERNAL_DTD attribute cannot be set
     */
    public static TransformerFactory createSecureTransformerFactory() {
        TransformerFactory tf = TransformerFactory.newInstance();
        // Critical protection — fail closed if it cannot be applied
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Defense-in-depth — warn if unavailable
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (IllegalArgumentException ex) {
            logger.warn("Could not restrict ACCESS_EXTERNAL_STYLESHEET on TransformerFactory", ex);
        }
        return tf;
    }

    /**
     * Creates a {@link SAXParserFactory} with XXE protections enabled.
     *
     * <p>Disables DOCTYPE declarations and external entity resolution. Use this factory
     * method instead of {@code SAXParserFactory.newInstance()} throughout the codebase.
     *
     * <p>The critical {@code disallow-doctype-decl} feature is required — an exception
     * is thrown if it cannot be applied so that callers never receive an unprotected factory.
     * The remaining defense-in-depth features are applied on a best-effort basis; a warning
     * is logged if any of them cannot be set.
     *
     * @return SAXParserFactory configured with XXE protections
     * @throws ParserConfigurationException if the critical disallow-doctype-decl protection cannot be enabled
     * @throws SAXException if the critical disallow-doctype-decl protection cannot be enabled
     */
    public static SAXParserFactory createSecureSAXParserFactory() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        // Critical protection — fail closed if it cannot be applied
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // Defense-in-depth features — warn individually if unavailable
        try {
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException | SAXException ex) {
            logger.warn("Could not disable external-general-entities on SAXParserFactory", ex);
        }
        try {
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException | SAXException ex) {
            logger.warn("Could not disable external-parameter-entities on SAXParserFactory", ex);
        }
        try {
            spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException | SAXException ex) {
            logger.warn("Could not enable FEATURE_SECURE_PROCESSING on SAXParserFactory", ex);
        }
        try {
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException | SAXException ex) {
            logger.warn("Could not disable load-external-dtd on SAXParserFactory", ex);
        }
        spf.setXIncludeAware(false);
        return spf;
    }

    /**
     * Creates a {@link javax.xml.validation.SchemaFactory} with external access disabled.
     *
     * <p>Sets {@code ACCESS_EXTERNAL_DTD} and {@code ACCESS_EXTERNAL_SCHEMA} to empty strings
     * so that no external DTD or schema resources can be loaded during schema compilation.
     * Use this factory method instead of {@code SchemaFactory.newInstance()} throughout the codebase.
     *
     * <p>The external-access properties are required — a {@link SAXException}
     * is thrown if they cannot be applied so that callers never receive an
     * unprotected factory.
     *
     * @param schemaLanguage the schema language URI (e.g. {@link XMLConstants#W3C_XML_SCHEMA_NS_URI})
     * @return SchemaFactory configured with external-access restrictions
     * @throws SAXException if the required security features or external-access properties cannot be set
     */
    public static javax.xml.validation.SchemaFactory createSecureSchemaFactory(String schemaLanguage) throws SAXException {
        javax.xml.validation.SchemaFactory sf = createSchemaFactoryInstance(schemaLanguage);
        try {
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (SAXException ex) {
            throw new SAXException("Failed to enable secure XML schema processing", ex);
        }
        try {
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (SAXException ex) {
            throw new SAXException("Failed to restrict external DTD access on SchemaFactory", ex);
        }
        try {
            sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException ex) {
            throw new SAXException("Failed to restrict external schema access on SchemaFactory", ex);
        }
        return sf;
    }

    /**
     * Selects the JDK W3C XML Schema factory so required JAXP external-access
     * security properties are recognized even when an older classpath Xerces
     * provider is present. Non-W3C schema languages still use the standard JAXP
     * provider lookup because the JDK default factory only supports W3C XML Schema.
     *
     * @param schemaLanguage the schema language URI
     * @return SchemaFactory instance for the requested schema language
     */
    private static javax.xml.validation.SchemaFactory createSchemaFactoryInstance(String schemaLanguage) {
        if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(schemaLanguage)) {
            return javax.xml.validation.SchemaFactory.newDefaultInstance();
        }
        return javax.xml.validation.SchemaFactory.newInstance(schemaLanguage);
    }

    /**
     * Creates an {@link LSResourceResolver} that resolves only explicitly
     * allowlisted schema files from a classpath resource directory.
     *
     * <p>Use this with {@link #createSecureSchemaFactory(String)} when a bundled
     * schema imports another bundled schema by relative file name. External
     * schema access remains disabled; any non-allowlisted import is delegated
     * back to the secured factory and will be rejected by the JAXP access
     * restrictions.</p>
     *
     * @param resourceClass Class used to load absolute classpath resources
     * @param resourceDirectory String absolute or relative classpath directory containing schemas
     * @param allowedSystemIds Set of simple schema file names allowed to be resolved
     * @return LSResourceResolver resolver for allowlisted classpath schema imports
     * @throws IllegalArgumentException if any allowed system identifier is not a simple file name
     */
    public static LSResourceResolver createClasspathSchemaResolver(
            Class<?> resourceClass,
            String resourceDirectory,
            Set<String> allowedSystemIds) {

        Objects.requireNonNull(resourceClass, "resourceClass");
        Objects.requireNonNull(resourceDirectory, "resourceDirectory");
        Objects.requireNonNull(allowedSystemIds, "allowedSystemIds");

        for (String allowedName : allowedSystemIds) {
            if (allowedName == null
                    || allowedName.isBlank()
                    || allowedName.contains("/")
                    || allowedName.contains("\\")
                    || allowedName.contains("..")
                    || allowedName.contains(":")) {
                throw new IllegalArgumentException("Allowed schema import must be a simple file name");
            }
        }
        Set<String> allowedNames = Set.copyOf(allowedSystemIds);

        String resourcePrefix = normalizeClasspathResourceDirectory(resourceDirectory);
        return (_type, _namespaceURI, publicId, systemId, baseURI) -> {
            if (systemId == null || !allowedNames.contains(systemId)) {
                // Delegate to the secured factory so non-allowlisted imports are rejected.
                return null;
            }

            String resourcePath = resourcePrefix + systemId;
            InputStream byteStream = resourceClass.getResourceAsStream(resourcePath);
            if (byteStream == null) {
                throw new IllegalStateException(
                        "Missing allowlisted classpath schema import resource '"
                                + resourcePath
                                + "' for systemId '"
                                + systemId
                                + "' (baseURI='"
                                + baseURI
                                + "') using "
                                + resourceClass.getName()
                                + ". This usually indicates a packaging or configuration problem.");
            }

            return new ClasspathSchemaInput(publicId, systemId, baseURI, byteStream);
        };
    }

    /**
     * Normalizes a classpath resource directory so class-relative resource
     * lookups use an absolute directory path with a trailing separator.
     *
     * @param resourceDirectory String classpath resource directory
     * @return String normalized classpath resource directory
     */
    private static String normalizeClasspathResourceDirectory(String resourceDirectory) {
        String leadingSlashDirectory = resourceDirectory.startsWith("/") ? resourceDirectory : "/" + resourceDirectory;
        return leadingSlashDirectory.endsWith("/") ? leadingSlashDirectory : leadingSlashDirectory + "/";
    }

    /**
     * Creates a {@link javax.xml.validation.Validator} with external access disabled.
     *
     * <p>Sets {@code ACCESS_EXTERNAL_DTD} and {@code ACCESS_EXTERNAL_SCHEMA} to empty strings
     * so that no external DTD or schema resources can be loaded during validation.
     * Use this factory method instead of {@code schema.newValidator()} throughout the codebase.
     *
     * <p>The critical {@code ACCESS_EXTERNAL_DTD} property is required — a
     * {@link SAXException} is thrown if it cannot be applied so that callers
     * never receive an unprotected validator. The {@code ACCESS_EXTERNAL_SCHEMA} property
     * is applied on a best-effort basis; a warning is logged if it cannot be set.
     *
     * @param schema the compiled {@link javax.xml.validation.Schema} to create the validator from
     * @return Validator configured with external-access restrictions
     * @throws SAXException if the critical ACCESS_EXTERNAL_DTD property cannot be set
     */
    public static javax.xml.validation.Validator createSecureValidator(javax.xml.validation.Schema schema) throws SAXException {
        javax.xml.validation.Validator validator = schema.newValidator();
        // Critical protection — fail closed if it cannot be applied
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Defense-in-depth — warn if unavailable
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException ex) {
            logger.warn("Could not restrict ACCESS_EXTERNAL_SCHEMA on Validator", ex);
        }
        return validator;
    }

    /**
     * Minimal {@link LSInput} implementation for schema imports resolved from
     * allowlisted classpath resources by {@link #createClasspathSchemaResolver(Class, String, Set)}.
     * The byte stream is populated from the classpath resource; all other
     * fields are maintained only to satisfy the DOM Load/Save interface.
     */
    private static final class ClasspathSchemaInput implements LSInput {
        private Reader characterStream;
        private InputStream byteStream;
        private String stringData;
        private String systemId;
        private String publicId;
        private String baseURI;
        private String encoding;
        private boolean certifiedText;

        private ClasspathSchemaInput(String publicId, String systemId, String baseURI, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;
            this.byteStream = byteStream;
        }

        @Override
        public Reader getCharacterStream() {
            return characterStream;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            this.characterStream = characterStream;
        }

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            this.byteStream = byteStream;
        }

        @Override
        public String getStringData() {
            return stringData;
        }

        @Override
        public void setStringData(String stringData) {
            this.stringData = stringData;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {
            this.systemId = systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {
            this.publicId = publicId;
        }

        @Override
        public String getBaseURI() {
            return baseURI;
        }

        @Override
        public void setBaseURI(String baseURI) {
            this.baseURI = baseURI;
        }

        @Override
        public String getEncoding() {
            return encoding;
        }

        @Override
        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        @Override
        public boolean getCertifiedText() {
            return certifiedText;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            this.certifiedText = certifiedText;
        }
    }

    /**
     * Creates a secure {@link SAXSource} suitable for passing to a JAXB {@code Unmarshaller}.
     *
     * <p>Wraps the given {@link InputStream} in a SAX reader that has DOCTYPE declarations
     * disabled, preventing XXE attacks when unmarshalling XML via JAXB.
     *
     * @param inputStream the XML input to parse
     * @return SAXSource backed by a secured XMLReader
     * @throws ParserConfigurationException if the parser cannot be created with the required security features
     * @throws SAXException if the XMLReader cannot be obtained
     */
    public static SAXSource createSecureJaxbSource(InputStream inputStream) throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = createSecureSAXParserFactory();
        spf.setNamespaceAware(true);
        XMLReader xr = spf.newSAXParser().getXMLReader(); // nosemgrep: xmlreader-xxe, xmlreader-xxe-parameter-entities -- XXE protection applied by createSecureSAXParserFactory() above
        return new SAXSource(xr, new InputSource(inputStream));
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
            try {
                is = new FileInputStream(PathValidationUtils.validateConfiguredFile(url, "XML document file"));
            } catch (SecurityException e) {
                // Preserve the declared IOException contract: validateConfiguredFile throws an
                // unchecked SecurityException for a missing/invalid path, but callers catch IOException.
                throw new IOException("Cannot read XML document file", e);
            }
        }

        try (InputStream input = is) {
            return toDocument(input);
        }
    }

    public static Document toDocument(String s) throws IOException, SAXException, ParserConfigurationException {
        return toDocument(s.getBytes("UTF-8"));
    }

    public static Document toDocument(byte[] x) throws IOException, SAXException, ParserConfigurationException {
        ByteArrayInputStream is = new ByteArrayInputStream(x, 0, x.length);
        return toDocument((InputStream) is);
    }

    public static Document toDocument(InputStream is) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(is);
        return document;
    }

    public static Document newDocument(String rootName) throws ParserConfigurationException {
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        doc.appendChild(doc.createElement(rootName));
        return doc;
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
