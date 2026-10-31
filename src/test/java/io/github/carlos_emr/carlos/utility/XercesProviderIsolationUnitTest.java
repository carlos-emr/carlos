/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import java.nio.file.Path;
import java.util.Collections;
import java.util.jar.JarFile;
import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** Verify discovery on the complete application classpath, including CDS's private-use Xerces. */
@Tag("unit")
class XercesProviderIsolationUnitTest {
    @Test
    void shouldUseJdkFactories_whenOrdinaryXmlConsumersDiscoverProviders() throws Exception {
        assertAll(
                () -> assertThat(DocumentBuilderFactory.newInstance().getClass())
                        .isEqualTo(DocumentBuilderFactory.newDefaultInstance().getClass()),
                () -> assertThat(SAXParserFactory.newInstance().getClass())
                        .isEqualTo(SAXParserFactory.newDefaultInstance().getClass()),
                () -> assertThat(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).getClass())
                        .isEqualTo(SchemaFactory.newDefaultInstance().getClass()),
                () -> assertThat(DatatypeFactory.newInstance().getClass())
                        .isEqualTo(DatatypeFactory.newDefaultInstance().getClass()));
    }

    @Test
    void shouldRetainWoodstoxEvents_whenRemovingTheXercesEventRegistration() {
        assertThat(XMLEventFactory.newFactory())
                .isInstanceOf(com.ctc.wstx.stax.WstxEventFactory.class);
    }

    @Test
    void shouldUseJdkDomImplementation_whenDiscoveringLoadSaveSupport() throws Exception {
        var implementation = DOMImplementationRegistry.newInstance().getDOMImplementation("LS 3.0");
        assertThat(implementation).isInstanceOf(DOMImplementationLS.class);
        assertThat(implementation.getClass().getModule().getName()).isEqualTo("java.xml");
    }

    @Test
    void shouldRetainOnlyOneExplicitXercesImplementation_withoutAutomaticRegistrations() throws Exception {
        var factoryClass = org.apache.xerces.jaxp.SAXParserFactoryImpl.class;
        assertThat(Collections.list(factoryClass.getClassLoader()
                .getResources("org/apache/xerces/jaxp/SAXParserFactoryImpl.class"))).hasSize(1);
        Path artifact = Path.of(factoryClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertThat(jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith("META-INF/services/"))).isEmpty();
            assertThat(jar.getJarEntry("META-INF/LICENSE")).isNotNull();
            assertThat(jar.getJarEntry("META-INF/NOTICE")).isNotNull();
            assertThat(jar.getJarEntry("META-INF/CARLOS-MODIFICATIONS.txt")).isNotNull();
        }
        // CDS constructs these explicitly; removing registrations must preserve the API.
        assertThat(new org.apache.xerces.jaxp.SAXParserFactoryImpl()
                .newSAXParser().getXMLReader().getClass().getModule().isNamed()).isFalse();
        assertThat(new org.apache.xerces.jaxp.validation.XMLSchemaFactory()
                .isSchemaLanguageSupported(XMLConstants.W3C_XML_SCHEMA_NS_URI)).isTrue();
    }
}
