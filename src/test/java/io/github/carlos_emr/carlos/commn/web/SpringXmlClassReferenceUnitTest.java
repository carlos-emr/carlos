/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Pins the Spring XML configuration after the dead-code removal of commit ae9da405e99a (#5479) and
 * the release 2026.08 wiring fixes (#3947), as part of the #6336 gap tests.
 *
 * <ul>
 *   <li>Every CARLOS class named by a bean {@code class} attribute or a {@code <value>} in
 *       {@code applicationContext*.xml} / {@code spring_*.xml} still resolves, so a removed class
 *       cannot leave a dangling bean (the packaged WAR would otherwise fail at start-up).</li>
 *   <li>The {@code wl.prepared} tickler types and the {@code preparedTicklerManagerT} bean are gone
 *       with no reference left.</li>
 *   <li>{@code applicationContextJobs.xml} still loads, declares no bean and no {@code oscarJobTypes}
 *       job-type list, and the {@code Jobs} module still resolves to it.</li>
 *   <li>{@code programManager} keeps its {@code admissionDao} property and the three dashboard query
 *       handlers stay prototype-scoped.</li>
 * </ul>
 *
 * <p>Only bean definitions are registered; no context is refreshed, so no database is needed.</p>
 */
@Tag("unit")
@Tag("config")
@DisplayName("Spring XML class references and removed dead code")
class SpringXmlClassReferenceUnitTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern CARLOS_CLASS = Pattern.compile("io\\.github\\.carlos_emr\\.[\\w.]*\\.[A-Z][\\w$]*");

    private static List<Path> springFiles() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.filter(p -> p.getFileName().toString().matches("(applicationContext\\w*|spring_\\w+)\\.xml"))
                    .sorted().toList();
        }
    }

    private static Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (InputStream in = Files.newInputStream(file)) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    /** The {@code <bean id="...">} element, or null when the file declares none. */
    private static Element beanById(Document document, String id) {
        NodeList beans = document.getElementsByTagNameNS("*", "bean");
        for (int i = 0; i < beans.getLength(); i++) {
            Element bean = (Element) beans.item(i);
            if (id.equals(bean.getAttribute("id"))) {
                return bean;
            }
        }
        return null;
    }

    private static List<Path> sourceFiles(String... roots) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String root : roots) {
            try (Stream<Path> walk = Files.walk(Path.of(root))) {
                walk.filter(Files::isRegularFile).filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".jsp")
                            || name.endsWith(".properties");
                }).forEach(files::add);
            }
        }
        return files;
    }

    @Test
    @DisplayName("should resolve every CARLOS class a Spring XML bean or value names")
    void shouldResolveEveryCarlosClass_namedInSpringXml() throws Exception {
        List<String> unresolved = new ArrayList<>();
        int checked = 0;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (Path file : springFiles()) {
            Document document = parse(file);
            NodeList all = document.getElementsByTagName("*");
            TreeSet<String> names = new TreeSet<>();
            for (int i = 0; i < all.getLength(); i++) {
                Element element = (Element) all.item(i);
                String className = element.getAttribute("class");
                if (CARLOS_CLASS.matcher(className).matches()) {
                    names.add(className);
                }
                if ("value".equals(element.getLocalName())) {
                    String text = element.getTextContent().trim();
                    if (CARLOS_CLASS.matcher(text).matches()) {
                        names.add(text);
                    }
                }
            }
            for (String name : names) {
                checked++;
                try {
                    Class.forName(name, false, loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    unresolved.add(file.getFileName() + " -> " + name);
                }
            }
        }
        assertThat(checked).as("CARLOS classes referenced from Spring XML").isGreaterThan(100);
        assertThat(unresolved).as("Spring XML class names that do not resolve").isEmpty();
    }

    @Test
    @DisplayName("should leave no wl.prepared tickler type, bean or reference after the removal")
    void shouldLeaveNoWlPreparedReference_afterRemoval() throws Exception {
        // Removed with commit ae9da405e99a (#5479) as unused dead code.
        for (String type : List.of("PreparedTickler", "PreparedTicklerManager", "PreparedTicklerManagerImpl")) {
            String name = "io.github.carlos_emr.carlos.wl.prepared." + type;
            assertThat(Path.of("src/main/java/" + name.replace('.', '/') + ".java")).doesNotExist();
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> Class.forName(name, false, loader))
                    .isInstanceOf(ClassNotFoundException.class);
        }
        assertThat(Path.of("src/main/java/io/github/carlos_emr/carlos/wl/prepared")).doesNotExist();

        List<String> offenders = new ArrayList<>();
        for (Path file : sourceFiles("src/main/resources", "src/main/webapp/WEB-INF", "src/main/java")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.contains("carlos.wl.prepared") || text.contains("preparedTicklerManagerT")) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).as("files still naming the removed wl.prepared types or bean").isEmpty();

        for (Path file : springFiles()) {
            assertThat(beanById(parse(file), "preparedTicklerManagerT")).as(file.toString()).isNull();
        }
    }

    @Test
    @DisplayName("should load applicationContextJobs.xml with no bean and no oscarJobTypes list")
    void shouldLoadJobsContext_withNoJobTypeList() throws Exception {
        Path jobs = RESOURCES.resolve("applicationContextJobs.xml");
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        int registered = new XmlBeanDefinitionReader(factory).registerBeanDefinitions(parse(jobs), new FileSystemResource(jobs));

        assertThat(registered).isZero();
        assertThat(parse(jobs).getElementsByTagNameNS("*", "bean").getLength()).isZero();
        assertThat(factory.containsBeanDefinition("oscarJobTypes")).isFalse();
        assertThat(OscarSpringContextLoader.resolveConfigLocations("Jobs")).containsExactly(
                "classpath:applicationContext.xml",
                "classpath:applicationContextREST.xml",
                "classpath:applicationContextJobs.xml");

        List<String> offenders = new ArrayList<>();
        for (Path file : sourceFiles("src/main/resources", "src/main/webapp/WEB-INF", "src/main/java")) {
            if (Files.readString(file, StandardCharsets.UTF_8).contains("oscarJobTypes")) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).as("files still referencing the removed oscarJobTypes list").isEmpty();
    }

    @Test
    @DisplayName("should keep programManager wired to its admission DAO and the dashboard handlers prototype")
    void shouldKeepProgramManagerAndHandlerWiring_inApplicationContext() throws Exception {
        // DOM only: registering the whole file would run its component-scan and placeholder elements.
        Document context = parse(RESOURCES.resolve("applicationContext.xml"));

        Element programManager = beanById(context, "programManager");
        assertThat(programManager).isNotNull();
        List<String> properties = new ArrayList<>();
        NodeList children = programManager.getElementsByTagName("property");
        for (int i = 0; i < children.getLength(); i++) {
            properties.add(((Element) children.item(i)).getAttribute("name"));
        }
        assertThat(properties).contains("admissionDao");
        for (String handler : List.of("drilldownQueryHandler", "indicatorQueryHandler", "exportQueryHandler")) {
            Element bean = beanById(context, handler);
            assertThat(bean).as(handler).isNotNull();
            assertThat(bean.getAttribute("scope")).as(handler).isEqualTo(BeanDefinition.SCOPE_PROTOTYPE);
        }
    }

    @Test
    @DisplayName("should keep the unloaded QuatroShelter bundle gone and unreferenced")
    void shouldKeepQuatroShelterBundleGone_withoutReferences() throws Exception {
        // Deleted in ae9da405e99a (#5479). The word survives only as a UI label value in the
        // oscarResources_*.properties bundles, so only code and descriptors are scanned.
        assertThat(Path.of("src/main/webapp/WEB-INF/QuatroShelter.properties")).doesNotExist();
        List<String> offenders = new ArrayList<>();
        for (Path file : sourceFiles("src/main/resources", "src/main/webapp/WEB-INF", "src/main/java")) {
            String name = file.getFileName().toString();
            if (name.endsWith(".properties")) {
                continue;
            }
            if (Files.readString(file, StandardCharsets.UTF_8).contains("QuatroShelter")) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).as("sources naming the removed QuatroShelter bundle").isEmpty();
    }
}
