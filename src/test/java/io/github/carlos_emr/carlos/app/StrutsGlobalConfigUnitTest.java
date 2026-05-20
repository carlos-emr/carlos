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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
class StrutsGlobalConfigUnitTest extends CarlosUnitTestBase {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final String EXPECTED_STRUTS_DOCTYPE =
            "<!DOCTYPE struts PUBLIC \"-//Apache Software Foundation//DTD Struts Configuration 6.5//EN\" "
                    + "\"https://struts.apache.org/dtds/struts-6.5.dtd\">";
    private static final String EXPECTED_ALLOWLIST_PACKAGE = "io.github.carlos_emr.carlos";
    private static final Path STRUTS_XML =
            Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts.xml");
    private static final Set<String> DEFAULT_STRUTS_GLOBAL_ALLOWED_METHODS =
            Set.of("execute", "input", "back", "cancel", "browse", "save", "delete", "list", "index");

    @Test
    @DisplayName("Strict Method Invocation should be enabled globally")
    void shouldEnableStrictMethodInvocation_forStrutsPackages()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Document parent = parseXml(strutsXmlPath);
        Path strutsDir = strutsXmlPath.getParent();

        NodeList includes = parent.getElementsByTagName("include");
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < includes.getLength(); i++) {
            if (includes.item(i) instanceof Element include) {
                String fileName = include.getAttribute("file");
                Path includedPath = strutsDir.resolve(fileName);
                assertThat(includedPath)
                        .as("Included Struts config %s referenced from %s should exist", fileName, STRUTS_XML)
                        .exists();
                collectStrictMethodInvocationViolations(fileName, parseXml(includedPath), violations);
            }
        }

        assertThat(violations)
                .as("Struts packages must explicitly enable strict-method-invocation")
                .isEmpty();
    }

    @Test
    @DisplayName("OGNL allowlist should be enabled for CARLOS packages")
    void shouldEnableOgnlAllowlist_forCarlosPackages()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Map<String, String> constants = collectConstants(parseXml(strutsXmlPath));

        assertThat(constants)
                .as("Struts 7 uses struts.allowlist.* constants for strict OGNL allowlisting")
                .containsEntry("struts.allowlist.enable", "true")
                .containsEntry("struts.allowlist.packageNames", EXPECTED_ALLOWLIST_PACKAGE);
    }

    /**
     * Guards against a future edit in any included struts-*.xml file accidentally weakening
     * the OGNL allowlist. Struts processes &lt;include&gt; files after the parent, so a
     * {@code struts.allowlist.*} constant in any included config could silently override the
     * parent setting and remove or narrow OGNL protection at runtime — while the sibling test
     * above would still pass (it only reads struts.xml). This test closes that gap by
     * iterating every &lt;include&gt; referenced from struts.xml and asserting none of them
     * carry dangerous allowlist overrides.
     */
    @Test
    @DisplayName("no included struts config should weaken OGNL allowlist settings")
    void shouldNotWeakenOgnlAllowlistSettings_inIncludedConfigs()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Document parent = parseXml(strutsXmlPath);
        Path strutsDir = strutsXmlPath.getParent();

        NodeList includes = parent.getElementsByTagName("include");
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < includes.getLength(); i++) {
            if (includes.item(i) instanceof Element include) {
                String fileName = include.getAttribute("file");
                Path includedPath = strutsDir.resolve(fileName);
                assertThat(includedPath)
                        .as("Included Struts config %s referenced from %s should exist", fileName, STRUTS_XML)
                        .exists();
                Map<String, String> constants = collectConstants(parseXml(includedPath));
                String enableValue = constants.get("struts.allowlist.enable");
                if (isStrutsFalseValue(enableValue)) {
                    violations.add(fileName + " sets struts.allowlist.enable=" + enableValue);
                }
                String packageNames = constants.get("struts.allowlist.packageNames");
                if (packageNames != null && !EXPECTED_ALLOWLIST_PACKAGE.equals(packageNames.trim())) {
                    violations.add(fileName + " sets struts.allowlist.packageNames=" + packageNames);
                }
                String classes = constants.get("struts.allowlist.classes");
                if (classes != null && !classes.trim().isEmpty()) {
                    violations.add(fileName + " sets struts.allowlist.classes=" + classes);
                }
            }
        }

        assertThat(violations)
                .as("These included Struts configs weaken global OGNL allowlist settings")
                .isEmpty();
    }

    @Test
    @DisplayName("Struts actions with non-default methods should declare allowed-methods")
    void shouldAllowlistConfiguredNonDefaultMethods_forStrutsActions()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Document parent = parseXml(strutsXmlPath);
        Path strutsDir = strutsXmlPath.getParent();

        NodeList includes = parent.getElementsByTagName("include");
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < includes.getLength(); i++) {
            if (includes.item(i) instanceof Element include) {
                String fileName = include.getAttribute("file");
                Path includedPath = strutsDir.resolve(fileName);
                assertThat(includedPath)
                        .as("Included Struts config %s referenced from %s should exist", fileName, STRUTS_XML)
                        .exists();
                collectNonDefaultMethodViolations(fileName, parseXml(includedPath), violations);
                collectAllowedMethodOrderViolations(fileName, parseXml(includedPath), violations);
            }
        }

        assertThat(violations)
                .as("Non-default Struts action methods must be explicitly allowlisted for Strict Method Invocation")
                .isEmpty();
    }

    private static void collectStrictMethodInvocationViolations(
            String fileName, Document doc, List<String> violations) {
        NodeList packages = doc.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            if (packages.item(i) instanceof Element packageElement) {
                String strictMethodInvocation = packageElement.getAttribute("strict-method-invocation");
                if (!"true".equals(strictMethodInvocation.trim())) {
                    violations.add(fileName + " package " + packageElement.getAttribute("name")
                            + " sets strict-method-invocation=" + strictMethodInvocation);
                }
            }
        }
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

    private static boolean isStrutsFalseValue(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "false", "no", "off", "f", "n", "0" -> true;
            default -> false;
        };
    }

    private static void collectNonDefaultMethodViolations(
            String fileName, Document doc, List<String> violations) {
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element action) {
                String method = action.getAttribute("method").trim();
                if (method.isEmpty() || DEFAULT_STRUTS_GLOBAL_ALLOWED_METHODS.contains(method)) {
                    continue;
                }
                String actionName = action.getAttribute("name");
                if (method.contains("{") || method.contains("}")) {
                    violations.add(fileName + " action " + actionName + " uses dynamic method pattern " + method);
                    continue;
                }
                Set<String> allowedMethods = collectAllowedMethods(action);
                if (!allowedMethods.contains(method)) {
                    violations.add(fileName + " action " + actionName + " invokes " + method
                            + " without matching <allowed-methods>");
                }
            }
        }
    }

    private static Set<String> collectAllowedMethods(Element action) {
        NodeList allowedMethodNodes = action.getElementsByTagName("allowed-methods");
        LinkedHashSet<String> allowedMethods = new LinkedHashSet<>();
        for (int i = 0; i < allowedMethodNodes.getLength(); i++) {
            String[] methods = allowedMethodNodes.item(i).getTextContent().split(",");
            for (String method : methods) {
                String trimmed = method.trim();
                if (!trimmed.isEmpty()) {
                    allowedMethods.add(trimmed);
                }
            }
        }
        return allowedMethods;
    }

    private static void collectAllowedMethodOrderViolations(
            String fileName, Document doc, List<String> violations) {
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element action) {
                List<Element> children = childElements(action);
                for (int j = 0; j < children.size(); j++) {
                    Element child = children.get(j);
                    if ("allowed-methods".equals(child.getTagName()) && j != children.size() - 1) {
                        violations.add(fileName + " action " + action.getAttribute("name")
                                + " places <allowed-methods> before other child elements");
                    }
                }
            }
        }
    }

    private static List<Element> childElements(Element element) {
        NodeList childNodes = element.getChildNodes();
        List<Element> children = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE
                    && childNodes.item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    private static Document parseXml(Path absolutePath)
            throws IOException, ParserConfigurationException, SAXException {
        assertThat(absolutePath)
                .as("Struts config file not found — run tests from project root or set -Dbasedir=<project-root>")
                .exists();
        String xml = Files.readString(absolutePath, StandardCharsets.UTF_8);
        assertThat(xml)
                .as("%s should declare the expected Struts 6.5 DTD", absolutePath.getFileName())
                .contains(EXPECTED_STRUTS_DOCTYPE);
        DocumentBuilder db = newHardenedDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xml)));
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
        setAttributeIfSupported(dbf, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(dbf, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // Struts config files intentionally declare the project-standard Struts 6.5 DTD.
        // External DTD loading and entity expansion remain disabled above.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }

    private static void setAttributeIfSupported(DocumentBuilderFactory dbf, String name, String value) {
        try {
            dbf.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
            // Some bundled Xerces implementations do not expose JAXP accessExternal* attributes.
        }
    }
}
