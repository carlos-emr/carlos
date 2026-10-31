/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.xml.sax.SAXException;

import java.io.StringReader;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

/** Behavioral checks for the JDOM boundary retained by CARLOS. Uses synthetic XML only. */
@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("JDOM XML security and compatibility")
class Jdom2SecurityUnitTest {

    @Test
    @DisplayName("should refuse to return a builder when required protections cannot be applied")
    void shouldFailCreation_whenSecurityConfigurationFails() throws Exception {
        JDOMException failure = new JDOMException("Required SAX feature is unsupported");
        try (MockedConstruction<SAXBuilder> construction = mockConstruction(SAXBuilder.class,
                (builder, context) -> doThrow(failure).when(builder).buildEngine())) {
            assertThatThrownBy(XmlUtils::createSecureSAXBuilder)
                    .isInstanceOf(IllegalStateException.class)
                    .hasCause(failure);
            assertThat(construction.constructed()).hasSize(1);
        }
    }

    @Test
    @DisplayName("should reject unsupported feature changes when the parsing engine is rebuilt")
    void shouldRejectUnsupportedFeature_whenRebuildingEngine() {
        SAXBuilder builder = XmlUtils.createSecureSAXBuilder();
        builder.setFeature("urn:carlos:test:unsupported-feature", true);

        assertThatThrownBy(() -> builder.build(new StringReader("<root/>")))
                .isInstanceOf(JDOMException.class)
                .hasMessageContaining("urn:carlos:test:unsupported-feature");
    }

    @Test
    @DisplayName("should leave XInclude references unexpanded")
    void shouldLeaveXIncludeUnexpanded_withExternalHref() throws Exception {
        var root = XmlUtils.createSecureSAXBuilder().build(new StringReader("""
                <root xmlns:xi="http://www.w3.org/2001/XInclude">
                    <xi:include href="file:///must-not-be-read" parse="text"/>
                </root>
                """)).getRootElement();

        assertThat(root.getChildren()).hasSize(1);
        assertThat(root.getChildren().getFirst().getName()).isEqualTo("include");
        assertThat(root.getChildren().getFirst().getAttributeValue("href"))
                .isEqualTo("file:///must-not-be-read");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!DOCTYPE root><root/>",
            "<!DOCTYPE root [<!ENTITY local SYSTEM 'file:///synthetic-jdom-marker'>]><root>&local;</root>",
            "<!DOCTYPE root [<!ENTITY remote SYSTEM 'http://127.0.0.1:9/xxe'>]><root>&remote;</root>",
            "<!DOCTYPE root [<!ENTITY % external SYSTEM 'http://127.0.0.1:9/parameter'>%external;]><root/>",
            "<!DOCTYPE root SYSTEM 'http://127.0.0.1:9/external.dtd'><root/>",
            "<!DOCTYPE root [<!ENTITY a 'expansion'><!ENTITY b '&a;&a;&a;'>]><root>&b;</root>"
    })
    void shouldRejectDoctype_beforeResolvingAnyEntity(String xml) {
        SAXBuilder builder = XmlUtils.createSecureSAXBuilder();
        AtomicInteger resolutions = new AtomicInteger();
        builder.setEntityResolver((publicId, systemId) -> {
            resolutions.incrementAndGet();
            throw new SAXException("External resource resolution must not be attempted");
        });

        assertThatThrownBy(() -> builder.build(new StringReader(xml)))
                .isInstanceOf(JDOMException.class)
                .hasMessageContaining("DOCTYPE");
        assertThat(resolutions.get()).isZero();
    }

    @Test
    void shouldKeepProtections_whenBuilderIsReusedAfterSuccessAndFailure() throws Exception {
        SAXBuilder builder = XmlUtils.createSecureSAXBuilder();
        assertThat(builder.build(new StringReader("<root>first</root>"))
                .getRootElement().getText()).isEqualTo("first");

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> builder.build(new StringReader("<!DOCTYPE root><root/>")))
                    .isInstanceOf(JDOMException.class)
                    .hasMessageContaining("DOCTYPE");
            assertThat(builder.build(new StringReader("<root>safe</root>"))
                    .getRootElement().getText()).isEqualTo("safe");
        }
    }

    @Test
    void shouldPreserveNamespacesCdataUnicodeAndEscapes_whenXmlIsRoundTripped() throws Exception {
        String xml = """
                <report xmlns="urn:carlos:test" id="A&amp;B">
                    <item>café &amp; Ω &lt; 5</item>
                    <note><![CDATA[a < b && c > d]]></note>
                </report>
                """;
        var document = XmlUtils.createSecureSAXBuilder().build(new StringReader(xml));
        String serialized = new XMLOutputter().outputString(document);
        var root = XmlUtils.createSecureSAXBuilder().build(new StringReader(serialized)).getRootElement();
        Namespace namespace = Namespace.getNamespace("urn:carlos:test");

        assertThat(root.getNamespaceURI()).isEqualTo("urn:carlos:test");
        assertThat(root.getAttributeValue("id")).isEqualTo("A&B");
        assertThat(root.getChildText("item", namespace)).isEqualTo("café & Ω < 5");
        assertThat(root.getChildText("note", namespace)).isEqualTo("a < b && c > d");
        assertThat(serialized).contains("<![CDATA[a < b && c > d]]>");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/oscar/prevention/PreventionItems.xml",
            "/oscar/prevention/PreventionConfigSets.xml",
            "/io/github/carlos_emr/carlos/lab/ca/all/upload/message_config.xml",
            "/io/github/carlos_emr/carlos/lab/ca/all/parsers/message_config.xml",
            "/oscar/encounter/oscarMeasurements/flowsheets/diabetesFlowsheet.xml",
            "/oscar/encounter/oscarMeasurements/flowsheets/healthTracker.xml"
    })
    void shouldParseBundledClinicalConfiguration_withRequiredProtections(String resource) throws Exception {
        try (var input = Jdom2SecurityUnitTest.class.getResourceAsStream(resource)) {
            assertThat(input).as("Bundled resource %s", resource).isNotNull();
            assertThat(XmlUtils.createSecureSAXBuilder().build(input).getRootElement()).isNotNull();
        }
    }
}
