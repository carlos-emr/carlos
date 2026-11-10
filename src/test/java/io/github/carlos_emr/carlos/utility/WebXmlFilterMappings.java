/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the {@code <filter-mapping>} elements of the production {@code web.xml} in document
 * order, which is the order the container builds the filter chain in. Shared by the tests that
 * pin where the request-boundary filters sit (issue #5305).
 */
final class WebXmlFilterMappings {

    static final Path PRODUCTION_WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");

    /**
     * One {@code <filter-mapping>}. An empty {@code dispatchers} list means the specification
     * default, REQUEST only.
     */
    record Mapping(String filterName, List<String> urlPatterns, List<String> dispatchers) {

        /** Dispatcher types this mapping applies to, with the REQUEST default made explicit. */
        List<String> effectiveDispatchers() {
            return dispatchers.isEmpty() ? List.of("REQUEST") : dispatchers;
        }
    }

    private WebXmlFilterMappings() {
    }

    /** The production web.xml's filter mappings, in chain order. */
    static List<Mapping> production() {
        try (InputStream in = Files.newInputStream(PRODUCTION_WEB_XML)) {
            return parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + PRODUCTION_WEB_XML.toAbsolutePath(), e);
        }
    }

    static List<Mapping> parse(InputStream webXml) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(webXml);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Cannot parse web.xml", e);
        }

        List<Mapping> mappings = new ArrayList<>();
        NodeList nodes = document.getElementsByTagNameNS("*", "filter-mapping");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element mapping = (Element) nodes.item(i);
            mappings.add(new Mapping(
                    childText(mapping, "filter-name").get(0),
                    childText(mapping, "url-pattern"),
                    childText(mapping, "dispatcher")));
        }
        return Collections.unmodifiableList(mappings);
    }

    /** Position of the first mapping for {@code filterName}, or -1. */
    static int indexOf(List<Mapping> mappings, String filterName) {
        for (int i = 0; i < mappings.size(); i++) {
            if (mappings.get(i).filterName().equals(filterName)) {
                return i;
            }
        }
        return -1;
    }

    /** Every mapping for {@code filterName}. */
    static List<Mapping> mappingsFor(List<Mapping> mappings, String filterName) {
        return mappings.stream().filter(m -> m.filterName().equals(filterName)).toList();
    }

    private static List<String> childText(Element parent, String localName) {
        List<String> values = new ArrayList<>();
        NodeList children = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < children.getLength(); i++) {
            values.add(children.item(i).getTextContent().trim());
        }
        return values;
    }
}
