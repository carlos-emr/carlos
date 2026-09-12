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
    /**
     * The default {@code globalAllowedMethods} entries declared by the {@code struts-default}
     * package in {@code struts-default.xml} shipped by Apache Struts. Actions inheriting from
     * {@code struts-default} (directly or transitively) may invoke these methods under Strict
     * Method Invocation without a per-action {@code <allowed-methods>} entry.
     *
     * <p>Source: {@code org.apache.struts2/core/src/main/resources/struts-default.xml} —
     * {@code <global-allowed-methods>execute,input,back,cancel,browse,save,delete,list,index</global-allowed-methods>}.
     */
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

    /**
     * Every module package must extend {@code carlos-default} (declared in struts.xml), not
     * {@code struts-default} directly, and an action that names a stack must name one of the two
     * CARLOS stacks. Those stacks are struts-default's {@code defaultStack} and {@code basicStack}
     * with {@link io.github.carlos_emr.carlos.app.CarlosExceptionMappingInterceptor} in the
     * exception slot, and nothing else: this test reads {@code struts-default.xml} out of the Struts
     * jar on the classpath and compares entry for entry, parameter for parameter, so a Struts upgrade
     * that changes the stock stack fails here instead of silently leaving CARLOS on the old one. A
     * package or action that reaches past these stacks is one whose failures vanish into the error
     * page again.
     */
    @Test
    @DisplayName("module packages should inherit the exception-logging stacks from carlos-default")
    void shouldInheritExceptionLogging_fromCarlosDefaultPackage()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Document parent = parseXml(strutsXmlPath);
        Path strutsDir = strutsXmlPath.getParent();

        Element carlosDefault = null;
        NodeList parentPackages = parent.getElementsByTagName("package");
        for (int i = 0; i < parentPackages.getLength(); i++) {
            Element candidate = (Element) parentPackages.item(i);
            if ("carlos-default".equals(candidate.getAttribute("name"))) {
                carlosDefault = candidate;
            }
        }
        assertThat(carlosDefault).as("struts.xml should declare the carlos-default package").isNotNull();
        assertThat(carlosDefault.getAttribute("abstract")).isEqualTo("true");
        assertThat(carlosDefault.getAttribute("extends")).isEqualTo("struts-default");
        assertThat(carlosDefault.getAttribute("strict-method-invocation")).isEqualTo("true");

        Map<String, String> declaredInterceptors = new LinkedHashMap<>();
        NodeList interceptorNodes = carlosDefault.getElementsByTagName("interceptor");
        for (int i = 0; i < interceptorNodes.getLength(); i++) {
            Element interceptor = (Element) interceptorNodes.item(i);
            declaredInterceptors.put(interceptor.getAttribute("name"), interceptor.getAttribute("class"));
        }
        assertThat(declaredInterceptors)
                .as("carlos-default should declare exactly the CARLOS exception interceptor")
                .containsExactly(Map.entry("carlosException",
                        "io.github.carlos_emr.carlos.app.CarlosExceptionMappingInterceptor"));

        java.io.InputStream stockConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream("struts-default.xml");
        assertThat(stockConfig).as("struts-default.xml should be on the test classpath (struts2-core)").isNotNull();
        Document strutsDefault = newHardenedDocumentBuilder().parse(new InputSource(stockConfig));
        Map<String, List<String>> stockStacks = describeStacks(strutsDefault);
        Map<String, List<String>> carlosStacks = describeStacks(parent);
        assertThat(carlosStacks.keySet()).containsExactlyInAnyOrder("carlosDefaultStack", "carlosBasicStack");
        assertThat(carlosStacks.get("carlosDefaultStack"))
                .as("carlosDefaultStack must be struts-default's defaultStack with carlosException in the exception slot")
                .isEqualTo(withCarlosException(stockStacks.get("defaultStack")));
        assertThat(carlosStacks.get("carlosBasicStack"))
                .as("carlosBasicStack must be struts-default's basicStack with carlosException in the exception slot")
                .isEqualTo(withCarlosException(stockStacks.get("basicStack")));

        NodeList defaultRefs = carlosDefault.getElementsByTagName("default-interceptor-ref");
        assertThat(defaultRefs.getLength()).isEqualTo(1);
        assertThat(((Element) defaultRefs.item(0)).getAttribute("name")).isEqualTo("carlosDefaultStack");

        List<String> violations = new ArrayList<>();
        NodeList includes = parent.getElementsByTagName("include");
        for (int i = 0; i < includes.getLength(); i++) {
            if (includes.item(i) instanceof Element include) {
                String fileName = include.getAttribute("file");
                Document doc = parseXml(strutsDir.resolve(fileName));
                NodeList packages = doc.getElementsByTagName("package");
                for (int p = 0; p < packages.getLength(); p++) {
                    Element packageElement = (Element) packages.item(p);
                    String extendsValue = packageElement.getAttribute("extends");
                    if (!"carlos-default".equals(extendsValue)) {
                        violations.add(packageViolation(fileName, packageElement,
                                "extends " + extendsValue + " instead of carlos-default"));
                    }
                    if (packageElement.getElementsByTagName("default-interceptor-ref").getLength() > 0) {
                        violations.add(packageViolation(fileName, packageElement,
                                "overrides default-interceptor-ref"));
                    }
                }
                NodeList refs = doc.getElementsByTagName("interceptor-ref");
                for (int r = 0; r < refs.getLength(); r++) {
                    String refName = ((Element) refs.item(r)).getAttribute("name");
                    if ("defaultStack".equals(refName) || "basicStack".equals(refName)) {
                        violations.add(fileName + " references the struts-default stack " + refName
                                + " directly; name the carlos* stack so the exception interceptor still logs");
                    }
                }
            }
        }

        assertThat(violations)
                .as("Struts packages and actions must stay on the exception-logging stacks")
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

    @Test
    @DisplayName("Struts devMode should stay disabled globally")
    void shouldDisableStrutsDevMode_globally()
            throws IOException, ParserConfigurationException, SAXException {
        Path strutsXmlPath = resolveProjectPath(STRUTS_XML);
        Map<String, String> constants = collectConstants(parseXml(strutsXmlPath));

        assertThat(constants)
                .as("Production Struts config must hardcode devMode off")
                .containsEntry("struts.devMode", "false");
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
        // Also scan the root struts.xml so that a future non-default method added directly
        // (rather than via an included module) cannot bypass the SMI allowlist contract.
        collectNonDefaultMethodViolations(STRUTS_XML.getFileName().toString(), parent, violations);
        collectAllowedMethodOrderViolations(STRUTS_XML.getFileName().toString(), parent, violations);
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
                if (strictMethodInvocation.isBlank()) {
                    violations.add(packageViolation(
                            fileName, packageElement, "is missing strict-method-invocation"));
                } else if (!"true".equals(strictMethodInvocation.trim())) {
                    violations.add(packageViolation(
                            fileName, packageElement, "sets strict-method-invocation=" + strictMethodInvocation));
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
                    violations.add(actionViolation(fileName, actionName, "uses dynamic method pattern " + method));
                    continue;
                }
                Set<String> allowedMethods = collectAllowedMethods(action);
                if (!allowedMethods.contains(method)) {
                    violations.add(actionViolation(fileName, actionName,
                            "invokes " + method + " without matching <allowed-methods>"));
                }
            }
        }
    }

    private static Set<String> collectAllowedMethods(Element action) {
        LinkedHashSet<String> allowedMethods = new LinkedHashSet<>();
        // Only direct children: <allowed-methods> nested inside other descendants of <action>
        // are not part of this action's SMI contract.
        for (Element child : childElements(action)) {
            if (!"allowed-methods".equals(child.getTagName())) {
                continue;
            }
            String[] methods = child.getTextContent().split(",");
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
                int allowedMethodIndex = -1;
                int allowedMethodCount = 0;
                for (int j = 0; j < children.size(); j++) {
                    Element child = children.get(j);
                    if ("allowed-methods".equals(child.getTagName())) {
                        allowedMethodIndex = j;
                        allowedMethodCount++;
                    }
                }
                if (allowedMethodCount > 1) {
                    violations.add(actionViolation(
                            fileName, action.getAttribute("name"), "declares <allowed-methods> more than once"));
                }
                if (allowedMethodCount == 1 && allowedMethodIndex != children.size() - 1) {
                    violations.add(actionViolation(
                            fileName, action.getAttribute("name"),
                            "places <allowed-methods> before other child elements"));
                }
            }
        }
    }

    private static List<Element> childElements(Element element) {
        NodeList childNodes = element.getChildNodes();
        List<Element> children = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * One line per interceptor-ref of each stack, name plus its params in document order, so two
     * stacks compare as plain lists.
     */
    private static Map<String, List<String>> describeStacks(Document doc) {
        Map<String, List<String>> stacks = new LinkedHashMap<>();
        NodeList stackNodes = doc.getElementsByTagName("interceptor-stack");
        for (int i = 0; i < stackNodes.getLength(); i++) {
            Element stack = (Element) stackNodes.item(i);
            List<String> entries = new ArrayList<>();
            NodeList refs = stack.getElementsByTagName("interceptor-ref");
            for (int r = 0; r < refs.getLength(); r++) {
                Element ref = (Element) refs.item(r);
                StringBuilder entry = new StringBuilder(ref.getAttribute("name"));
                NodeList params = ref.getElementsByTagName("param");
                for (int p = 0; p < params.getLength(); p++) {
                    Element param = (Element) params.item(p);
                    entry.append(' ').append(param.getAttribute("name")).append('=').append(param.getTextContent().trim());
                }
                entries.add(entry.toString());
            }
            stacks.put(stack.getAttribute("name"), entries);
        }
        return stacks;
    }

    private static List<String> withCarlosException(List<String> stockStack) {
        assertThat(stockStack).as("struts-default.xml should be readable from the Struts jar").isNotNull();
        List<String> expected = new ArrayList<>();
        for (String entry : stockStack) {
            expected.add("exception".equals(entry) ? "carlosException" : entry);
        }
        return expected;
    }

    private static String packageViolation(String fileName, Element packageElement, String detail) {
        return "%s package %s %s".formatted(fileName, packageElement.getAttribute("name"), detail);
    }

    private static String actionViolation(String fileName, String actionName, String detail) {
        return "%s action %s %s".formatted(fileName, actionName, detail);
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
