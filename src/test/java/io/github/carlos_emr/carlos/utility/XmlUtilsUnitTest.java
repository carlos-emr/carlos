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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link XmlUtils} DOM helper methods and secure XML schema utilities.
 *
 * @since 2026-05-04
 */
@DisplayName("XmlUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("security")
class XmlUtilsUnitTest {

    private Document createTestDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("root");
        doc.appendChild(root);
        Element child = doc.createElement("child");
        child.setAttribute("id", "1");
        child.setAttribute("name", "test");
        child.setTextContent("Hello");
        root.appendChild(child);
        Element child2 = doc.createElement("child");
        child2.setAttribute("id", "2");
        child2.setTextContent("World");
        root.appendChild(child2);
        return doc;
    }

    @Nested
    @DisplayName("DOM helpers")
    class DomHelpers {

        @Test
        @DisplayName("should create non-null document with root element")
        void shouldCreateDocument_withRootElement() throws ParserConfigurationException {
            Document doc = XmlUtils.newDocument("root");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
        }

        @Test
        @DisplayName("should serialize document to byte array")
        void shouldSerializeDocument_toByteArray() throws Exception {
            Document doc = createTestDocument();
            byte[] bytes = XmlUtils.toBytes(doc, false);
            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
            String xml = new String(bytes, StandardCharsets.UTF_8);
            assertThat(xml).contains("root");
            assertThat(xml).contains("child");
        }

        @Test
        @DisplayName("should serialize document to string")
        void shouldSerializeDocument_toString() throws Exception {
            Document doc = createTestDocument();
            String xml = XmlUtils.toString(doc, false);
            assertThat(xml).isNotNull();
            assertThat(xml).contains("root");
            assertThat(xml).contains("Hello");
        }

        @Test
        @DisplayName("should parse XML string to document")
        void shouldParseXmlString_toDocument() throws Exception {
            Document doc = XmlUtils.toDocument("<root><item>value</item></root>");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
        }

        @Test
        @DisplayName("should return attribute value from node")
        void shouldReturnAttributeValue() throws Exception {
            Document doc = createTestDocument();
            Element child = (Element) doc.getDocumentElement().getElementsByTagName("child").item(0);
            String value = XmlUtils.getAttributeValue(child, "id");
            assertThat(value).isEqualTo("1");
        }

        @Test
        @DisplayName("should find child node by name")
        void shouldFindChild_byName() throws Exception {
            Document doc = createTestDocument();
            Node child = XmlUtils.getChildNode(doc.getDocumentElement(), "child");
            assertThat(child).isNotNull();
            assertThat(child.getTextContent()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("should return null for non-existent child")
        void shouldReturnNull_forNonExistentChild() throws Exception {
            Document doc = createTestDocument();
            Node child = XmlUtils.getChildNode(doc.getDocumentElement(), "nonexistent");
            assertThat(child).isNull();
        }

        @Test
        @DisplayName("should return list of matching child nodes")
        void shouldReturnMatchingChildren() throws Exception {
            Document doc = createTestDocument();
            ArrayList<Node> children = XmlUtils.getChildNodes(doc.getDocumentElement(), "child");
            assertThat(children).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for non-existent children")
        void shouldReturnEmptyList_forNonExistentChildren() throws Exception {
            Document doc = createTestDocument();
            ArrayList<Node> children = XmlUtils.getChildNodes(doc.getDocumentElement(), "nonexistent");
            assertThat(children).isEmpty();
        }

        @Test
        @DisplayName("should return text content of named child")
        void shouldReturnTextContent() throws Exception {
            Document doc = createTestDocument();
            String text = XmlUtils.getChildNodeTextContents(doc.getDocumentElement(), "child");
            assertThat(text).isEqualTo("Hello");
        }

        @Test
        @DisplayName("should append child element with text to root")
        void shouldAppendChild_withText() throws ParserConfigurationException {
            Document doc = XmlUtils.newDocument("root");
            XmlUtils.appendChildToRoot(doc, "name", "John");
            String text = XmlUtils.getChildNodeTextContents(doc.getDocumentElement(), "name");
            assertThat(text).isEqualTo("John");
        }
    }

    @Nested
    @DisplayName("secure schema factory")
    class SecureSchemaFactoryTests {

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
}
