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
package io.github.carlos_emr.carlos.admin;

import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards admin Struts routes that point to JSPs relocated behind
 * {@code /WEB-INF/jsp/}. These mappings must stay aligned with the migrated
 * JSP locations so existing admin links keep rendering CARLOS EMR UI instead
 * of returning 404 responses.
 *
 * @since 2026-05-03
 */
@DisplayName("struts-admin.xml Config Tests")
@Tag("unit")
@Tag("fast")
@Tag("admin")
class StrutsAdminConfigTest {

    private static final Path ADMIN_CONFIG = Path.of(
            "src", "main", "webapp", "WEB-INF", "classes", "struts-admin.xml");
    private static final int MAX_PARENT_SEARCH_DEPTH = 8;

    private static Document adminConfig;

    @BeforeAll
    static void parseAdminConfig() throws Exception {
        adminConfig = parse(ADMIN_CONFIG);
    }

    @Test
    @DisplayName("lookup list manager should render the migrated WEB-INF JSP")
    void shouldRenderMigratedWebInfJsp_forLookupListManagerAction() throws Exception {
        Element action = findAction("lookupListManagerAction");

        assertThat(action)
                .as("struts-admin.xml must declare lookupListManagerAction")
                .isNotNull();
        assertThat(action.getAttribute("class"))
                .isEqualTo("io.github.carlos_emr.carlos.admin.lookUpLists.LookupListManager2Action");
        assertThat(extractSuccessResultPath(action))
                .as("/lookupListManagerAction?method=manage should render the lookup list manager UI")
                .isEqualTo("/WEB-INF/jsp/admin/lookUpLists/index.jsp");
    }

    private Element findAction(String actionName) {
        NodeList actions = adminConfig.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            if (actionName.equals(action.getAttribute("name"))) {
                return action;
            }
        }
        return null;
    }

    private static String extractSuccessResultPath(Element action) {
        NodeList results = action.getElementsByTagName("result");
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            if ("success".equals(result.getAttribute("name"))) {
                return result.getTextContent().trim();
            }
        }
        return "";
    }

    private static Document parse(Path configPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // Struts config files declare a DOCTYPE; external DTD loading and all
        // external entities stay disabled above, and the resolver below returns
        // an empty local source so the parser never performs network access.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((_publicId, _systemId) ->
                new InputSource(new java.io.StringReader("")));

        try (InputStream in = Files.newInputStream(resolveProjectPath(configPath))) {
            return db.parse(in);
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        int checkedParents = 0;
        while (current != null && checkedParents < MAX_PARENT_SEARCH_DEPTH) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
            checkedParents++;
        }
        throw new IllegalStateException("Unable to locate " + relativePath
                + " within " + MAX_PARENT_SEARCH_DEPTH + " parent directories from "
                + System.getProperty("user.dir"));
    }
}
