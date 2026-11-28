/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Active Tomcat context, systemd mount-namespace isolation, and systemd manager environment
 * inspection for {@link PackagedReportLeaseCheck}.
 *
 * @since 2026-11-27
 */
final class PackagedReportLeaseContext {
    private static final String SERVICE_UNIT = "carlos-emr.service";
    private static final String CONTEXT_DESCRIPTOR = "carlos.xml";
    private static final String SYSTEM_CONF = "system.conf";
    private static final String SYSTEM_CONF_DROPIN_DIR = "system.conf.d";
    private static final Set<String> SAFE_CONTEXT_ELEMENTS = Set.of(
            "Resources", "WatchedResource", "CookieProcessor", "Manager", "Valve", "JarScanner");
    private static final List<String> PRIVATE_TMP_PREFIXES = List.of("/tmp", "/var/tmp");
    private static final List<String> PROTECT_HOME_PREFIXES = List.of("/home", "/root", "/run/user");
    private static final Set<String> DISABLED_VALUES = Set.of("no", "false", "0", "off");

    record ServiceIsolation(boolean privateTmp, boolean protectHome) { }

    private record HostContextPaths(String engineName, String hostName, String appBase, String xmlBase) { }

    private PackagedReportLeaseContext() {
    }

    static ServiceIsolation readPackagedUnitIsolation(List<Path> systemdDirs) throws IOException {
        boolean privateTmp = false;
        boolean protectHome = false;
        for (Path dir : systemdDirs) {
            if (PackagedReportLeaseCheck.isPackagedSystemdDir(dir)) {
                Path unit = dir.resolve(SERVICE_UNIT);
                if (!Files.notExists(unit, LinkOption.NOFOLLOW_LINKS)
                        && !PackagedReportLeaseCheck.isMaskSymlink(unit)) {
                    ServiceIsolation parsed = parseUnitServiceIsolation(unit);
                    privateTmp |= parsed.privateTmp();
                    protectHome |= parsed.protectHome();
                }
            }
        }
        return new ServiceIsolation(privateTmp, protectHome);
    }

    private static ServiceIsolation parseUnitServiceIsolation(Path unitFile) throws IOException {
        boolean privateTmp = false;
        boolean protectHome = false;
        String section = "";
        for (String line : PackagedReportLeaseCheck.logicalDropInLines(unitFile)) {
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line;
            } else if ("[Service]".equals(section)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String val = asciiLower(line.substring(eq + 1).trim());
                    if ("PrivateTmp".equals(key)) {
                        privateTmp = !DISABLED_VALUES.contains(val);
                    } else if ("ProtectHome".equals(key)) {
                        protectHome = !DISABLED_VALUES.contains(val);
                    }
                }
            }
        }
        return new ServiceIsolation(privateTmp, protectHome);
    }

    private static String asciiLower(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            out.append(ch >= 'A' && ch <= 'Z' ? (char) (ch + 32) : ch);
        }
        return out.toString();
    }

    static void verifyServiceFilesystemView(
            Path selectedProperties,
            Path selectedHome,
            Path deployedClasses,
            ServiceIsolation isolation) throws IOException {
        if (!isolation.privateTmp() && !isolation.protectHome()) {
            return;
        }
        if (isServiceIsolated(selectedHome, isolation)) {
            throw new IOException("Home configuration path is isolated in service mount namespace");
        }
        if (isServiceIsolated(selectedProperties, isolation)) {
            throw new IOException("Selected properties path is isolated in service mount namespace");
        }
        if (isServiceIsolated(deployedClasses, isolation)) {
            throw new IOException("Deployed webapp path is isolated in service mount namespace");
        }
    }

    static boolean isServiceIsolated(Path path, ServiceIsolation isolation) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (matchesIsolatedRoot(normalized, isolation)) {
            return true;
        }
        Path ancestor = deepestExistingAncestor(normalized);
        if (ancestor == null) {
            return false;
        }
        Path realAncestor = ancestor.toRealPath();
        Path resolved = ancestor.equals(normalized)
                ? realAncestor
                : realAncestor.resolve(ancestor.relativize(normalized)).normalize();
        return matchesIsolatedRoot(resolved, isolation);
    }

    private static Path deepestExistingAncestor(Path normalized) {
        Path current = normalized;
        while (current != null && Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        return current;
    }

    private static boolean matchesIsolatedRoot(Path candidate, ServiceIsolation isolation) {
        String text = candidate.toString();
        if (isolation.privateTmp() && hasPathPrefix(text, PRIVATE_TMP_PREFIXES)) {
            return true;
        }
        return isolation.protectHome() && hasPathPrefix(text, PROTECT_HOME_PREFIXES);
    }

    private static boolean hasPathPrefix(String pathText, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (pathText.equals(prefix) || pathText.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    static void loadManagerEnvironment(PackagedReportLeaseCheck.LaunchConfig launch, Map<String, String> vars)
            throws IOException {
        if (launch.managerEnvUnavailable()) {
            throw new IOException("Systemd manager environment could not be established");
        }
        // The live manager reports its loaded defaults together with runtime overrides.
        if (launch.managerEnvFile() != null) {
            parseNullDelimitedEnvironment(launch.managerEnvFile(), vars);
        } else {
            loadSystemdManagerDefaults(launch.systemdManagerConfDirs(), vars);
        }
    }

    private static void parseNullDelimitedEnvironment(Path file, Map<String, String> vars) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) {
            throw new IOException("Manager environment dump is not a regular file");
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.isEmpty()) {
            return;
        }
        for (String entry : content.split("\0")) {
            if (!entry.isEmpty()) {
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    throw new IOException("Malformed manager environment entry");
                }
                vars.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
    }

    private static void loadSystemdManagerDefaults(List<Path> managerConfDirs, Map<String, String> vars)
            throws IOException {
        if (managerConfDirs.isEmpty()) {
            return;
        }
        Map<String, String> defaultEnv = new HashMap<>();
        Path mainConf = findMainSystemConf(managerConfDirs);
        if (mainConf != null) {
            parseManagerConfFile(mainConf, defaultEnv);
        }
        for (Path dropIn : collectManagerConfDropIns(managerConfDirs)) {
            parseManagerConfFile(dropIn, defaultEnv);
        }
        vars.putAll(defaultEnv);
    }

    private static Path findMainSystemConf(List<Path> managerConfDirs) throws IOException {
        // Higher-priority directories (/etc/systemd before /run/systemd before /usr/lib/systemd) win;
        // managerConfDirs is supplied from lowest to highest priority so the last existing file wins.
        Path selected = null;
        for (Path dir : managerConfDirs) {
            Path candidate = dir.resolve(SYSTEM_CONF);
            if (!Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                selected = PackagedReportLeaseCheck.isMaskSymlink(candidate) ? null : candidate;
            }
        }
        return selected;
    }

    private static List<Path> collectManagerConfDropIns(List<Path> baseDirs) throws IOException {
        // baseDirs is ordered lowest-to-highest priority (/usr/lib -> /run -> /etc); higher overrides same name.
        Map<String, Path> byName = new HashMap<>();
        Set<String> masked = new HashSet<>();
        for (Path baseDir : baseDirs) {
            Path dir = baseDir.resolve(SYSTEM_CONF_DROPIN_DIR);
            if (!Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
                collectConfDirectoryEntries(dir, byName, masked);
            }
        }
        return byName.entrySet().stream()
                .filter(entry -> !masked.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static void collectConfDirectoryEntries(
            Path dir, Map<String, Path> byName, Set<String> masked) throws IOException {
        if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir)) {
            throw new IOException("Unexpected configuration drop-in directory entry");
        }
        try (var stream = Files.list(dir)) {
            for (Path entry : stream.toList()) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".conf")) {
                    if (PackagedReportLeaseCheck.isMaskSymlink(entry)) {
                        masked.add(name);
                        byName.remove(name);
                    } else {
                        masked.remove(name);
                        byName.put(name, entry);
                    }
                }
            }
        }
    }

    private static void parseManagerConfFile(Path confFile, Map<String, String> defaultEnv) throws IOException {
        String section = "";
        for (String line : PackagedReportLeaseCheck.logicalDropInLines(confFile)) {
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line;
            } else if ("[Manager]".equals(section)) {
                parseManagerDirective(line, defaultEnv);
            }
        }
    }

    private static void parseManagerDirective(String directive, Map<String, String> defaultEnv) throws IOException {
        int eq = directive.indexOf('=');
        if (eq <= 0) {
            throw new IOException("Malformed systemd manager directive");
        }
        String key = directive.substring(0, eq).trim();
        if ("DefaultEnvironment".equals(key)) {
            applyDefaultEnvironment(directive.substring(eq + 1).trim(), defaultEnv);
        }
    }

    private static void applyDefaultEnvironment(String value, Map<String, String> defaultEnv) throws IOException {
        if (value.isEmpty()) {
            defaultEnv.clear();
            return;
        }
        int index = PackagedReportLeaseCheck.skipWhitespace(value, 0);
        while (index < value.length()) {
            var parsed = PackagedReportLeaseCheck.readEnvironmentToken(value, index);
            String token = parsed.token();
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new IOException("Malformed DefaultEnvironment assignment");
            }
            defaultEnv.put(token.substring(0, eq), token.substring(eq + 1));
            index = PackagedReportLeaseCheck.skipWhitespace(value, parsed.nextIndex());
        }
    }

    static Path resolveDeployedClasses(Path catalinaBase, Path catalinaHome, Path fallbackClasses) throws IOException {
        if (catalinaBase == null) {
            return fallbackClasses;
        }
        Path confDir = catalinaBase.resolve("conf");
        HostContextPaths host = resolveHostContextPaths(confDir);
        Path contextXml = confDir.resolve("context.xml");
        if (!Files.notExists(contextXml, LinkOption.NOFOLLOW_LINKS)) {
            verifyContextElement(parseXmlRoot(contextXml, "Context"));
        }
        Path xmlBaseDir = host.xmlBase() != null
                ? resolveAgainstBase(catalinaBase, expandCatalinaVars(host.xmlBase(), catalinaBase, catalinaHome))
                : confDir.resolve(host.engineName()).resolve(host.hostName());
        Element carlosContext = parseXmlRoot(xmlBaseDir.resolve(CONTEXT_DESCRIPTOR), "Context");
        verifyContextElement(carlosContext);
        Path appBaseDir = resolveAgainstBase(
                catalinaBase, expandCatalinaVars(host.appBase(), catalinaBase, catalinaHome));
        String rawDocBase = expandCatalinaVars(carlosContext.getAttribute("docBase"), catalinaBase, catalinaHome);
        Path docBase = rawDocBase.isEmpty()
                ? appBaseDir.resolve("carlos")
                : resolveAgainstBase(appBaseDir, rawDocBase);
        Path classesDir = docBase.resolve("WEB-INF").resolve("classes");
        if (!Files.isDirectory(docBase) || !Files.isDirectory(classesDir)) {
            throw new IOException("Deployed Context docBase does not expose an exploded WEB-INF/classes directory");
        }
        return classesDir;
    }

    private static HostContextPaths resolveHostContextPaths(Path confDir) throws IOException {
        Path serverXml = confDir.resolve("server.xml");
        if (Files.notExists(serverXml, LinkOption.NOFOLLOW_LINKS)) {
            return new HostContextPaths("Catalina", "localhost", "webapps", null);
        }
        Element server = parseXmlRoot(serverXml, "Server");
        Element service = singleChildElement(server, "Service");
        Element engine = singleChildElement(service, "Engine");
        String engineName = requireAttribute(engine, "name");
        String defaultHost = requireAttribute(engine, "defaultHost");
        Element host = findMatchingHost(engine, defaultHost);
        if (!childElements(host, "Context").isEmpty()) {
            throw new IOException("Inline Context in server.xml cannot be verified");
        }
        String appBase = host.getAttribute("appBase").isEmpty() ? "webapps" : host.getAttribute("appBase");
        String xmlBase = host.getAttribute("xmlBase").isEmpty() ? null : host.getAttribute("xmlBase");
        return new HostContextPaths(engineName, host.getAttribute("name"), appBase, xmlBase);
    }

    private static Element findMatchingHost(Element engine, String defaultHost) throws IOException {
        Element matched = null;
        for (Element host : childElements(engine, "Host")) {
            if (matchesHost(host, defaultHost)) {
                if (matched != null) {
                    throw new IOException("Ambiguous Host definition in server.xml");
                }
                matched = host;
            }
        }
        if (matched == null || matched.getAttribute("name").isEmpty()) {
            throw new IOException("Default Host not found in server.xml");
        }
        return matched;
    }

    private static boolean matchesHost(Element host, String defaultHost) {
        if (defaultHost.equals(host.getAttribute("name"))) {
            return true;
        }
        for (Element alias : childElements(host, "Alias")) {
            if (defaultHost.equals(alias.getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static void verifyContextElement(Element context) throws IOException {
        if (!context.getAttribute("className").isEmpty()) {
            throw new IOException("Custom Context implementation cannot be verified");
        }
        for (Element child : allChildElements(context)) {
            String tag = child.getTagName();
            if (!SAFE_CONTEXT_ELEMENTS.contains(tag)) {
                throw new IOException("Unsupported Context child element: " + tag);
            }
            if ("Resources".equals(tag)
                    && (!child.getAttribute("className").isEmpty() || !allChildElements(child).isEmpty())) {
                throw new IOException("Custom Context Resources configuration cannot be verified");
            }
        }
    }

    private static Element parseXmlRoot(Path xmlFile, String expectedRoot) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(xmlFile, BasicFileAttributes.class);
        if (!attrs.isRegularFile()) {
            throw new IOException("XML configuration is not a regular file");
        }
        try (InputStream stream = Files.newInputStream(xmlFile)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(stream);
            Element root = doc.getDocumentElement();
            if (root == null || !expectedRoot.equals(root.getTagName())) {
                throw new IOException("Unexpected XML root element");
            }
            return root;
        } catch (ParserConfigurationException | SAXException failure) {
            throw new IOException("Failed to parse Tomcat XML configuration", failure);
        }
    }

    private static Element singleChildElement(Element parent, String tagName) throws IOException {
        List<Element> matches = childElements(parent, tagName);
        if (matches.size() != 1) {
            throw new IOException("Expected single <" + tagName + "> element");
        }
        return matches.get(0);
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static List<Element> allChildElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String requireAttribute(Element element, String attribute) throws IOException {
        String value = element.getAttribute(attribute);
        if (value.isEmpty()) {
            throw new IOException("Missing required XML attribute: " + attribute);
        }
        return value;
    }

    private static String expandCatalinaVars(String raw, Path catalinaBase, Path catalinaHome) throws IOException {
        String expanded = raw.replace("${catalina.base}", catalinaBase.toString());
        if (catalinaHome != null) {
            expanded = expanded.replace("${catalina.home}", catalinaHome.toString());
        }
        if (expanded.contains("${")) {
            throw new IOException("Unresolved Tomcat property placeholder in context path");
        }
        return expanded;
    }

    private static Path resolveAgainstBase(Path base, String rawPath) {
        Path candidate = Path.of(rawPath);
        return candidate.isAbsolute() ? candidate.normalize() : base.resolve(candidate).normalize();
    }
}
