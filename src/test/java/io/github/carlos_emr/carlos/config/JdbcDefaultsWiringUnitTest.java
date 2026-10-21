/*
 * Copyright (C) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.config;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Resolve the actual production Spring definitions without opening a database connection. */
@Tag("unit")
@ResourceLock("SYSTEM_PROPERTIES")
class JdbcDefaultsWiringUnitTest {
    @TempDir Path temporary;
    private static final String KEY = "db_default_use_information_schema";

    @Test
    void ordinaryWarRetainsDriverDefaults() throws Exception {
        assertThat(resolve(null, null, Map.of())).doesNotContain("useInformationSchema=");
    }

    @Test
    void debianDefaultAppliesToExistingOperatorFilesWithoutTheNewKey() throws Exception {
        assertThat(resolve(debianDefaults(), null, Map.of()))
                .contains("useInformationSchema=false");
    }

    @Test
    void operatorOverrideWins() throws Exception {
        assertThat(resolve(debianDefaults(), null, Map.of(KEY, "true")))
                .contains("useInformationSchema=true");
    }

    @Test
    void explicitEmptyOperatorValueDisablesTheDistributionDefault() throws Exception {
        assertThat(resolve(debianDefaults(), null, Map.of(KEY, ""))).doesNotContain("useInformationSchema=");
    }

    @Test
    void classpathCarlosPropertiesAlsoOutranksDistributionDefaults() throws Exception {
        assertThat(resolve(debianDefaults(), "\n" + KEY + "=true\n", Map.of()))
                .contains("useInformationSchema=true");
    }

    @Test
    void operatorValueOutranksClasspathOverride() throws Exception {
        assertThat(resolve(debianDefaults(), "\n" + KEY + "=true\n", Map.of(KEY, "")))
                .doesNotContain("useInformationSchema=");
    }

    @Test
    void jvmPropertyCannotSilentlyOverrideTheDocumentedPropertySources() throws Exception {
        String previous = System.getProperty(KEY);
        try {
            System.setProperty(KEY, "true");
            assertThat(resolve(debianDefaults(), null, Map.of())).contains("useInformationSchema=false");
        } finally {
            if (previous == null) System.clearProperty(KEY); else System.setProperty(KEY, previous);
        }
    }

    @Test
    void existingExplicitUrlOptionOutranksThePackageDefault() throws Exception {
        assertThat(resolve(debianDefaults(), null, Map.of("db_uri", "jdbc:mysql://127.0.0.1/",
                "db_name", "fixture?useInformationSchema=true")))
                .contains("useInformationSchema=true").doesNotContain("useInformationSchema=false");
    }

    @Test
    void invalidOperatorDefaultFailsBeanCreationInsteadOfBeingIgnored() {
        assertThatThrownBy(() -> resolve(debianDefaults(), null, Map.of(KEY, "invalid-boolean")))
                .isInstanceOf(org.springframework.beans.BeansException.class);
    }

    private byte[] debianDefaults() throws Exception {
        return Files.readAllBytes(Path.of("debian/assets/jdbc-defaults.properties"));
    }

    private String resolve(byte[] defaults, String classpathOverride, Map<String, String> operator) throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        Path defaultsPath = temporary.resolve("jdbc-defaults.properties");
        if (defaults != null) Files.write(defaultsPath, defaults);
        Path propertiesPath = temporary.resolve("carlos.properties");
        if (classpathOverride != null) {
            try (InputStream original = parent.getResourceAsStream("carlos.properties")) {
                assertThat(original).isNotNull();
                Files.writeString(propertiesPath, new String(original.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) + classpathOverride);
            }
        }
        ClassLoader resources = new ClassLoader(parent) {
            @Override public URL getResource(String name) {
                try {
                    if (defaults != null && name.equals("jdbc-defaults.properties")) return defaultsPath.toUri().toURL();
                    if (classpathOverride != null && name.equals("carlos.properties")) return propertiesPath.toUri().toURL();
                } catch (java.net.MalformedURLException impossible) { throw new IllegalStateException(impossible); }
                return super.getResource(name);
            }
        };
        CarlosProperties privateProperties = mock(CarlosProperties.class);
        when(privateProperties.get(any())).thenAnswer(call -> operator.get(call.getArgument(0)));
        try (MockedStatic<CarlosProperties> singleton = mockStatic(CarlosProperties.class);
             GenericApplicationContext context = new GenericApplicationContext()) {
            singleton.when(CarlosProperties::getInstance).thenReturn(privateProperties);
            context.setClassLoader(resources);
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
            reader.registerBeanDefinitions(onlyBean("/applicationContext.xml", "propertyConfigurer"), null);
            reader.registerBeanDefinitions(onlyBean("/spring_jpa.xml", "dataSource"), null);
            context.refresh();
            OscarTrackingBasicDataSource source = context.getBean("dataSource", OscarTrackingBasicDataSource.class);
            assertThat(source.getNumActive()).isZero();
            return source.getUrl();
        }
    }

    private Document onlyBean(String resource, String id) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();document = factory.newDocumentBuilder().parse(input);
        }
        Element root = document.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            if (!(child instanceof Element element) || !id.equals(element.getAttribute("id"))) root.removeChild(child);
            child = next;
        }
        return document;
    }
}
