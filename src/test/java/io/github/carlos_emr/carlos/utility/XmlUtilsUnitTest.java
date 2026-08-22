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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for secure XML schema factory creation and classpath schema import
 * resolution.
 *
 * @since 2026-05-03
 */
@Tag("unit")
@Tag("security")
@DisplayName("XmlUtils secure schema factory")
class XmlUtilsUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should create secure schema factory for W3C XML schema")
    void shouldCreateSecureSchemaFactory_forW3cXmlSchema() throws Exception {
        SchemaFactory factory = XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        assertThat(factory).isNotNull();
        assertThat(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING)).isTrue();
    }

    @Test
    @DisplayName("should compile simple in-memory schema and create secure validator")
    void shouldCreateSecureValidator_withSimpleSchema() throws Exception {
        SchemaFactory factory = XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        String schemaContent = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;
        Schema schema = factory.newSchema(new StreamSource(new StringReader(schemaContent)));

        assertThat(XmlUtils.createSecureValidator(schema)).isNotNull();
    }

    @Test
    @DisplayName("should parse simple document through secure document builder")
    void shouldParseSimpleDocument_withToDocument() throws Exception {
        Document document = XmlUtils.toDocument(new ByteArrayInputStream("""
                <root><child>ok</child></root>
                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("root");
        assertThat(document.getElementsByTagName("child").item(0).getTextContent()).isEqualTo("ok");
    }

    @Test
    @DisplayName("should reject DOCTYPE declarations through toDocument")
    void shouldRejectDoctypeDeclarations_withToDocument() {
        assertThatThrownBy(() -> XmlUtils.toDocument(new ByteArrayInputStream("""
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root>&xxe;</root>
                """.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(SAXException.class);
    }

    @Test
    @DisplayName("should block external schema imports")
    void shouldBlockExternalSchemaImports_whenSchemaReferencesFile() throws Exception {
        SchemaFactory factory = XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        assertThatThrownBy(() -> factory.newSchema(new StreamSource(new StringReader("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="file:///should-not-be-read.xsd"/>
                </xs:schema>
                """))))
                .isInstanceOf(SAXException.class);
    }

    @Test
    @DisplayName("should compile export patient schema with allowlisted classpath import")
    void shouldCompileExportPatientSchema_withAllowlistedClasspathImport() throws Exception {
        SchemaFactory factory = XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(XmlUtils.createClasspathSchemaResolver(
                XmlUtilsUnitTest.class,
                "/omdDataMigration/",
                Set.of("EMR_Data_Migration_Schema_DT.xsd")));
        URL schemaUrl = XmlUtilsUnitTest.class.getResource("/omdDataMigration/EMR_Data_Migration_Schema.xsd");

        assertThat(schemaUrl)
                .as("Export Patient schema resource should exist at /omdDataMigration/EMR_Data_Migration_Schema.xsd")
                .isNotNull();
        assertThat(factory.newSchema(schemaUrl)).isNotNull();
    }

    @Test
    @DisplayName("should compile HRM schema with allowlisted classpath import")
    void shouldCompileHrmSchema_withAllowlistedClasspathImport() throws Exception {
        SchemaFactory factory = XmlUtils.createSecureSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(XmlUtils.createClasspathSchemaResolver(
                XmlUtilsUnitTest.class,
                "/xsd/hrm/1.1.2/",
                Set.of("ontariomd_hrm_dt.xsd")));
        URL schemaUrl = XmlUtilsUnitTest.class.getResource("/xsd/hrm/1.1.2/ontariomd_hrm.xsd");

        assertThat(schemaUrl)
                .as("HRM schema resource should exist at /xsd/hrm/1.1.2/ontariomd_hrm.xsd")
                .isNotNull();
        assertThat(factory.newSchema(schemaUrl)).isNotNull();
    }

    @Test
    @DisplayName("should reject non-simple allowlisted schema import names")
    void shouldRejectAllowlistedSchemaImportNames_whenNotSimpleFileName() {
        assertThatThrownBy(() -> XmlUtils.createClasspathSchemaResolver(
                XmlUtilsUnitTest.class,
                "/omdDataMigration/",
                Set.of("../EMR_Data_Migration_Schema_DT.xsd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("simple file name");
    }

    @Test
    @DisplayName("should reject null allowlisted schema import names")
    void shouldRejectAllowlistedSchemaImportNames_whenNull() {
        assertThatThrownBy(() -> XmlUtils.createClasspathSchemaResolver(
                XmlUtilsUnitTest.class,
                "/omdDataMigration/",
                Collections.singleton(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("simple file name");
    }
}
