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

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every module package maps a refused request (java.lang.SecurityException) to the shared refusal page, so
 * CarlosExceptionMappingInterceptor answers it with HTTP 403 and an incident id. A package without the mapping lets the
 * refusal fall through to the container's error page as HTTP 500 (#4940: the BC billing, eForm, login and messenger
 * packages). Other exception mappings are each module's own choice and are not checked here.
 */
@DisplayName("Struts module packages map SecurityException to the refusal page")
@Tag("unit")
@Tag("fast")
@Tag("security")
class StrutsModuleRefusalMappingUnitTest {

    private static final Path CONFIG_DIR = Path.of("src", "main", "webapp", "WEB-INF", "classes");
    private static final Path WEBAPP = Path.of("src", "main", "webapp");
    private static final String REFUSAL_PAGE = "/WEB-INF/jsp/error/securityError.jsp";

    /** Every package of every module file (struts-*.xml; struts.xml holds only the abstract parent). */
    static Stream<Arguments> modulePackages() throws Exception {
        List<Arguments> packages = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONFIG_DIR)) {
            for (Path file : files.filter(f -> f.getFileName().toString().matches("struts-[a-z-]+\\.xml")).sorted().toList()) {
                NodeList list = parse(file).getElementsByTagName("package");
                for (int i = 0; i < list.getLength(); i++) {
                    Element pkg = (Element) list.item(i);
                    packages.add(Arguments.of(file.getFileName().toString(), pkg.getAttribute("name"), pkg));
                }
            }
        }
        return packages.stream();
    }

    @Test
    @DisplayName("should find the module packages, the five #4940 ones included")
    void shouldListModulePackages_forEveryModuleFile() throws Exception {
        List<String> names = modulePackages().map(a -> (String) a.get()[1]).toList();
        assertThat(names).contains("billing", "billing-on", "eform", "login", "messenger", "admin", "report");
    }

    @ParameterizedTest(name = "{0}: package {1}")
    @MethodSource("modulePackages")
    @DisplayName("should map SecurityException to a global result that renders the shared refusal page")
    void shouldMapSecurityException_toRefusalPage(String file, String name, Element pkg) {
        String result = "";
        for (Element mapping : children(pkg, "global-exception-mappings", "exception-mapping")) {
            if ("java.lang.SecurityException".equals(mapping.getAttribute("exception"))) {
                result = mapping.getAttribute("result");
            }
        }
        assertThat(result).as("%s package %s maps no java.lang.SecurityException", file, name).isNotEmpty();
        String page = "";
        for (Element global : children(pkg, "global-results", "result")) {
            if (result.equals(global.getAttribute("name"))) {
                page = global.getTextContent().trim();
            }
        }
        assertThat(page).as("%s package %s: result %s is not a global result", file, name, result).isEqualTo(REFUSAL_PAGE);
        assertThat(WEBAPP.resolve(page.substring(1))).as("the refusal page exists").isRegularFile();
    }

    /** The {@code child} elements of the package's own {@code block} element (not of nested actions). */
    private static List<Element> children(Element pkg, String block, String child) {
        List<Element> out = new ArrayList<>();
        for (Node n = pkg.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && block.equals(e.getTagName())) {
                NodeList items = e.getElementsByTagName(child);
                for (int i = 0; i < items.getLength(); i++) {
                    out.add((Element) items.item(i));
                }
            }
        }
        return out;
    }

    private static Document parse(Path configPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // Struts config files declare a DOCTYPE; external DTDs and entities stay disabled, and the resolver returns an
        // empty local source so parsing never touches the network.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (var in = Files.newInputStream(configPath)) {
            return db.parse(in);
        }
    }
}
