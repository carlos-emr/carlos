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

import io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Path LOOKUP_LIST_INDEX_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "admin", "lookUpLists", "index.jsp");
    private static final Path LOOKUP_LIST_MANAGER_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "admin", "lookUpLists", "manageLookUpLists.jsp");
    private static final Path SECURITY_DELETE_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "admin", "securitydelete.jsp");
    private static final String LOOKUP_LIST_MANAGER_FRAGMENT =
            "/WEB-INF/jsp/admin/lookUpLists/manageLookUpLists.jsp";
    private static final String LOOKUP_LIST_ITEM_FRAGMENT = "/WEB-INF/jsp/admin/lookUpLists/lookupList.jsp";
    private static final String LAB_FORWARDING_RULES_ACTION =
            "io.github.carlos_emr.carlos.admin.gate.ViewLabForwardingRules2Action";
    private static final String LAB_FORWARDING_RULES_JSP = "/WEB-INF/jsp/admin/labforwardingrules.jsp";
    private static final Pattern C_IMPORT_URL_PATTERN = Pattern.compile(
            "<c:import\\b[^>]*\\burl\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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

    @Test
    @DisplayName("lookup list JSP fragments should import protected JSPs without action dispatch")
    void shouldImportProtectedJspFragments_forLookupListFragments() throws Exception {
        String indexJsp = readProjectFile(LOOKUP_LIST_INDEX_JSP);
        String managerJsp = readProjectFile(LOOKUP_LIST_MANAGER_JSP);

        assertThat(extractImportUrls(indexJsp))
                .as("lookupListManagerAction forwards to WEB-INF; nested fragments must use absolute "
                        + "protected JSP imports, not relative paths or extra action dispatches")
                .containsExactly(LOOKUP_LIST_MANAGER_FRAGMENT);
        assertThat(extractImportUrls(managerJsp))
                .as("both lookupLists and manageSingle branches must include lookupList.jsp directly")
                .containsExactly(LOOKUP_LIST_ITEM_FRAGMENT, LOOKUP_LIST_ITEM_FRAGMENT);
    }

    @Test
    @DisplayName("SecurityDelete action class attribute should match the Spring bean name")
    void shouldUseSpringBeanName_forSecurityDeleteActionClass() {
        Element action = findAction("admin/SecurityDelete");
        assertThat(action)
                .as("struts-admin.xml must declare admin/SecurityDelete")
                .isNotNull();
        assertThat(action.getAttribute("class"))
                .as("Struts class attribute must equal SPRING_BEAN_NAME so the Spring ObjectFactory "
                        + "resolves the method-security proxy instead of constructing a raw instance")
                .isEqualTo(SecurityDelete2Action.SPRING_BEAN_NAME);
    }

    @Test
    @DisplayName("admin security exceptions should render the existing security error page")
    void shouldMapAdminSecurityExceptions_toSecurityErrorPage() {
        assertThat(findGlobalResultPath("securityError"))
                .as("admin package should reuse the legacy CARLOS security-error page")
                .isEqualTo("/WEB-INF/jsp/error/securityError.jsp");
        assertThat(findGlobalExceptionResult("org.springframework.security.access.AccessDeniedException"))
                .as("Spring method-security denials should render like legacy security denials")
                .isEqualTo("securityError");
        assertThat(findGlobalExceptionResult("java.lang.SecurityException"))
                .as("legacy admin SecurityException handling should remain consistent")
                .isEqualTo("securityError");
        assertThat(findGlobalExceptionResult("java.lang.Exception"))
                .as("method-security routing should not add an admin-wide generic exception handler")
                .isEmpty();
    }

    @Test
    @DisplayName("SecurityDelete result JSP should stay aligned with action security")
    void shouldAlignSecurityDeleteJsp_withActionSecurity() throws Exception {
        String jsp = readProjectFile(SECURITY_DELETE_JSP);

        assertThat(jsp)
                .as("SecurityDelete2Action requires admin write access before forwarding to this JSP")
                .contains("objectName=\"_admin,_admin.userAdmin\" rights=\"w\"")
                .as("result-only JSP should not load unused global JavaScript helpers")
                .doesNotContain("/js/global.js");
    }

    @Test
    @DisplayName("lab forwarding rules admin link should render the migrated WEB-INF JSP")
    void shouldRenderMigratedWebInfJsp_forLabForwardingRulesAction() {
        Element action = findAction("admin/labForwardingRules");

        assertThat(action)
                .as("struts-admin.xml must declare admin/labForwardingRules")
                .isNotNull();
        assertThat(action.getAttribute("class")).isEqualTo(LAB_FORWARDING_RULES_ACTION);
        assertThat(extractSuccessResultPath(action))
                .as("/admin/labForwardingRules should render the admin lab forwarding UI")
                .isEqualTo(LAB_FORWARDING_RULES_JSP);
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

    private String findGlobalResultPath(String resultName) {
        NodeList results = adminConfig.getElementsByTagName("global-results");
        if (results.getLength() == 0) {
            return "";
        }
        NodeList resultNodes = ((Element) results.item(0)).getElementsByTagName("result");
        for (int i = 0; i < resultNodes.getLength(); i++) {
            Element result = (Element) resultNodes.item(i);
            if (resultName.equals(result.getAttribute("name"))) {
                return result.getTextContent().trim();
            }
        }
        return "";
    }

    private String findGlobalExceptionResult(String exceptionClass) {
        NodeList mappings = adminConfig.getElementsByTagName("global-exception-mappings");
        if (mappings.getLength() == 0) {
            return "";
        }
        NodeList exceptionMappings = ((Element) mappings.item(0)).getElementsByTagName("exception-mapping");
        for (int i = 0; i < exceptionMappings.getLength(); i++) {
            Element mapping = (Element) exceptionMappings.item(i);
            if (exceptionClass.equals(mapping.getAttribute("exception"))) {
                return mapping.getAttribute("result");
            }
        }
        return "";
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

    private static String readProjectFile(Path relativePath) throws Exception {
        return Files.readString(resolveProjectPath(relativePath), StandardCharsets.UTF_8);
    }

    private static List<String> extractImportUrls(String jspSource) {
        Matcher matcher = C_IMPORT_URL_PATTERN.matcher(jspSource);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
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
