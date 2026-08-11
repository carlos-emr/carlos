/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.decision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * Validates and atomically stores the editable antenatal risk configuration.
 *
 * <p>The legacy editor copied the request body directly to a shared XML file.
 * This service instead parses with external resources and DTDs disabled,
 * validates the small document grammar consumed by
 * {@link DesAntenatalPlannerRisksHandler_99_12}, and serializes the DOM with an
 * XML transformer. The replacement is an atomic rename of a fully-written
 * same-directory temporary file, so readers never observe a partial document.
 *
 * @since 2026-08-11
 */
public final class AntenatalRiskConfigService {

    static final String FILE_NAME = "desantenatalplannerrisks_99_12.xml";
    static final int MAX_XML_BYTES = 1024 * 1024;

    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Set<String> SECTION_CHILDREN =
            Set.of("section_title", "subsection", "risk", "entry", "heading");
    private static final Set<String> SUBSECTION_CHILDREN =
            Set.of("subsection_title", "risk", "entry", "heading");

    private final Path target;
    private final AtomicMover atomicMover;

    /** Creates a service backed by the configured CARLOS document directory. */
    public AntenatalRiskConfigService() {
        this(configuredTarget(), AntenatalRiskConfigService::moveAtomically);
    }

    AntenatalRiskConfigService(Path target) {
        this(target, AntenatalRiskConfigService::moveAtomically);
    }

    AntenatalRiskConfigService(Path target, AtomicMover atomicMover) {
        this.target = target.toAbsolutePath().normalize();
        this.atomicMover = atomicMover;
    }

    /**
     * Validates and saves a complete antenatal risk-list document.
     *
     * @param submittedXml administrator-supplied XML document
     * @throws InvalidConfigurationException when the document is unsafe or outside the supported grammar
     * @throws IOException when the validated document cannot be stored atomically
     */
    public void save(String submittedXml) throws InvalidConfigurationException, IOException {
        Document document = parseAndValidate(submittedXml);
        byte[] serialized = serialize(document);
        writeAtomically(serialized);
    }

    private Document parseAndValidate(String submittedXml) throws InvalidConfigurationException {
        if (submittedXml == null || submittedXml.isBlank()) {
            throw new InvalidConfigurationException("The risk-list XML cannot be empty.");
        }
        if (submittedXml.getBytes(StandardCharsets.UTF_8).length > MAX_XML_BYTES) {
            throw new InvalidConfigurationException("The risk-list XML exceeds the 1 MiB limit.");
        }

        try {
            javax.xml.parsers.DocumentBuilderFactory factory = XmlUtils.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new RejectingErrorHandler());
            Document document = builder.parse(new InputSource(new StringReader(submittedXml)));
            validateDocument(document);
            return document;
        } catch (ParserConfigurationException e) {
            throw new InvalidConfigurationException("Secure XML parsing is unavailable.", e);
        } catch (SAXException e) {
            throw new InvalidConfigurationException(
                    "The risk-list XML must be well-formed and cannot contain a DOCTYPE.", e);
        } catch (IOException e) {
            // StringReader does not perform I/O, but preserve a closed failure mode.
            throw new InvalidConfigurationException("The risk-list XML could not be read.", e);
        }
    }

    private static void validateDocument(Document document) throws InvalidConfigurationException {
        Element root = document.getDocumentElement();
        if (root == null || !"riskFactors".equals(root.getTagName()) || root.getNamespaceURI() != null) {
            throw new InvalidConfigurationException("The root element must be <riskFactors> without a namespace.");
        }

        validateAttributes(root, Set.of(), Set.of());
        validateDocumentSiblings(document, root);

        Set<String> fieldNames = new HashSet<>();
        int sectionCount = validateContainer(root, Set.of("section"), fieldNames);
        if (sectionCount == 0) {
            throw new InvalidConfigurationException("The risk list must contain at least one <section>.");
        }
    }

    private static void validateDocumentSiblings(Document document, Element root)
            throws InvalidConfigurationException {
        NodeList children = document.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child == root || child.getNodeType() == Node.COMMENT_NODE) {
                continue;
            }
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                continue;
            }
            throw new InvalidConfigurationException("Only comments may appear outside <riskFactors>.");
        }
    }

    private static int validateContainer(Element container, Set<String> allowedChildren, Set<String> fieldNames)
            throws InvalidConfigurationException {
        int elementCount = 0;
        NodeList children = container.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                continue;
            }
            if (child.getNodeType() == Node.COMMENT_NODE) {
                continue;
            }
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                throw new InvalidConfigurationException("Unsupported XML node inside <" + container.getTagName() + ">.");
            }

            Element element = (Element) child;
            String name = element.getTagName();
            if (element.getNamespaceURI() != null || !allowedChildren.contains(name)) {
                throw new InvalidConfigurationException(
                        "Element <" + name + "> is not allowed inside <" + container.getTagName() + ">.");
            }
            elementCount++;
            validateElement(element, fieldNames);
        }
        return elementCount;
    }

    private static void validateElement(Element element, Set<String> fieldNames)
            throws InvalidConfigurationException {
        switch (element.getTagName()) {
            case "section" -> {
                validateAttributes(element, Set.of(), Set.of());
                validateContainer(element, SECTION_CHILDREN, fieldNames);
            }
            case "subsection" -> {
                validateAttributes(element, Set.of(), Set.of());
                validateContainer(element, SUBSECTION_CHILDREN, fieldNames);
            }
            case "section_title", "subsection_title" -> {
                validateAttributes(element, Set.of(), Set.of());
                validateTextOnly(element);
            }
            case "heading" -> {
                validateAttributes(element, Set.of(), Set.of("href"));
                validateHref(element);
                validateTextOnly(element);
            }
            case "risk", "entry" -> {
                validateAttributes(element, Set.of("name"), Set.of("href"));
                validateFieldName(element, fieldNames);
                validateHref(element);
                validateTextOnly(element);
            }
            default -> throw new InvalidConfigurationException(
                    "Element <" + element.getTagName() + "> is not supported.");
        }
    }

    private static void validateAttributes(Element element, Set<String> required, Set<String> optional)
            throws InvalidConfigurationException {
        for (String attribute : required) {
            if (!element.hasAttribute(attribute) || element.getAttribute(attribute).isBlank()) {
                throw new InvalidConfigurationException(
                        "Element <" + element.getTagName() + "> requires attribute " + attribute + ".");
            }
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            if (attribute.getNamespaceURI() != null || (!required.contains(name) && !optional.contains(name))) {
                throw new InvalidConfigurationException(
                        "Attribute " + name + " is not allowed on <" + element.getTagName() + ">.");
            }
        }
    }

    private static void validateFieldName(Element element, Set<String> fieldNames)
            throws InvalidConfigurationException {
        String fieldName = element.getAttribute("name");
        if (!FIELD_NAME.matcher(fieldName).matches()) {
            throw new InvalidConfigurationException(
                    "Risk and entry names must start with a letter and contain at most 64 letters, digits, '-' or '_'.");
        }
        if (!fieldNames.add(fieldName)) {
            throw new InvalidConfigurationException("Risk and entry names must be unique.");
        }
    }

    private static void validateHref(Element element) throws InvalidConfigurationException {
        if (!element.hasAttribute("href")) {
            return;
        }

        String href = element.getAttribute("href");
        if (href.isBlank() || href.length() > 2048 || href.startsWith("//") || href.indexOf('\\') >= 0) {
            throw new InvalidConfigurationException("Links must be HTTP(S) URLs or same-origin relative paths.");
        }
        try {
            URI uri = new URI(href);
            if (uri.isAbsolute()) {
                String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    throw new InvalidConfigurationException("Links must use HTTP or HTTPS.");
                }
            }
        } catch (URISyntaxException e) {
            throw new InvalidConfigurationException("Links must be valid URIs.", e);
        }
    }

    private static void validateTextOnly(Element element) throws InvalidConfigurationException {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            short type = children.item(i).getNodeType();
            if (type != Node.TEXT_NODE && type != Node.CDATA_SECTION_NODE && type != Node.COMMENT_NODE) {
                throw new InvalidConfigurationException(
                        "Element <" + element.getTagName() + "> may contain text only.");
            }
        }
    }

    private static byte[] serialize(Document document) throws InvalidConfigurationException {
        try {
            Transformer transformer = XmlUtils.createSecureTransformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (TransformerException | IllegalArgumentException e) {
            throw new InvalidConfigurationException("The validated risk-list XML could not be serialized safely.", e);
        }
    }

    private void writeAtomically(byte[] serialized) throws IOException {
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("The configured document directory is unavailable.");
        }

        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
        try {
            Files.write(temporary, serialized, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            atomicMover.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("The document directory does not support atomic configuration updates.", e);
        }
    }

    private static Path configuredTarget() {
        String documentDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (documentDirectory == null || documentDirectory.isBlank()) {
            throw new IllegalStateException("DOCUMENT_DIR is not configured");
        }
        return Path.of(documentDirectory).resolve(FILE_NAME);
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path destination) throws IOException;
    }

    /** A safe, user-displayable validation failure. */
    public static final class InvalidConfigurationException extends Exception {
        public InvalidConfigurationException(String message) {
            super(message);
        }

        public InvalidConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class RejectingErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
