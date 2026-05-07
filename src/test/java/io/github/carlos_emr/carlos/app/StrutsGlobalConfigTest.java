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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Guards global Struts configuration hardening that must apply across all
 * CARLOS EMR modules.
 *
 * @since 2026-05-06
 */
@DisplayName("struts.xml global config Tests")
@Tag("unit")
@Tag("fast")
class StrutsGlobalConfigTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path STRUTS_XML =
            Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts.xml");

    @Test
    @DisplayName("OGNL allowlist should be enabled for CARLOS packages")
    void shouldEnableOgnlAllowlist_forCarlosPackages()
            throws IOException, ParserConfigurationException, SAXException {
        Map<String, String> constants = collectConstants(parseProjectXml(STRUTS_XML));

        assertThat(constants)
                .as("Struts 7 uses struts.allowlist.* constants for strict OGNL allowlisting")
                .containsEntry("struts.allowlist.enable", "true")
                .containsEntry("struts.allowlist.packageNames", "io.github.carlos_emr.carlos");
    }

    private static Map<String, String> collectConstants(Document doc) {
        NodeList constants = doc.getElementsByTagName("constant");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < constants.getLength(); i++) {
            if (constants.item(i) instanceof Element element) {
                out.put(element.getAttribute("name"), element.getAttribute("value"));
            }
        }
        return out;
    }

    private static Document parseProjectXml(Path relativeConfigPath)
            throws IOException, ParserConfigurationException, SAXException {
        String xml = Files.readString(resolveProjectPath(relativeConfigPath), StandardCharsets.UTF_8);
        String xmlWithoutDoctype = stripStrutsDoctype(xml);
        DocumentBuilder db = newHardenedDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xmlWithoutDoctype)));
    }

    private static String stripStrutsDoctype(String xml) {
        return xml.replaceFirst("(?m)^\\s*<!DOCTYPE\\s+struts\\b[^>]*>\\s*\\R?", "");
    }

    private static Path resolveProjectPath(Path relativePath) {
        return Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .resolve(relativePath)
                .normalize();
    }

    private static DocumentBuilder newHardenedDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((_publicId, _systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }
}
