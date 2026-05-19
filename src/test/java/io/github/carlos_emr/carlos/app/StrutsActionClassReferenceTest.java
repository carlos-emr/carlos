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
package io.github.carlos_emr.carlos.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Struts action class reference Tests")
@Tag("unit")
@Tag("fast")
class StrutsActionClassReferenceTest {

    private static final Path STRUTS_CONFIG_DIR = Path.of("src/main/webapp/WEB-INF/classes");

    @Test
    void shouldResolveConfiguredActionClasses_fromStrutsConfigs() throws Exception {
        List<String> missingClasses = new ArrayList<>();

        for (Path configPath : strutsConfigPaths()) {
            Document document = parse(configPath);
            NodeList actions = document.getElementsByTagName("action");
            for (int i = 0; i < actions.getLength(); i++) {
                Element action = (Element) actions.item(i);
                String className = action.getAttribute("class");
                if (isFullyQualifiedClassName(className) && classResource(className) == null) {
                    missingClasses.add(configPath + " action " + action.getAttribute("name") + " -> " + className);
                }
            }
        }

        assertThat(missingClasses)
                .as("Struts action class attributes must reference classes available on the application classpath")
                .isEmpty();
    }

    private List<Path> strutsConfigPaths() throws Exception {
        try (Stream<Path> paths = Files.list(STRUTS_CONFIG_DIR)) {
            return paths
                    .filter(path -> path.getFileName().toString().matches("struts(?:-[A-Za-z]+)?\\.xml"))
                    .sorted()
                    .toList();
        }
    }

    private Document parse(Path configPath) throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = Files.newInputStream(configPath)) {
            return db.parse(in);
        }
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }

    private boolean isFullyQualifiedClassName(String className) {
        return className != null
                && className.contains(".")
                && !className.contains("*")
                && !className.contains("$");
    }

    private java.net.URL classResource(String className) {
        return Thread.currentThread()
                .getContextClassLoader()
                .getResource(className.replace('.', '/') + ".class");
    }
}
