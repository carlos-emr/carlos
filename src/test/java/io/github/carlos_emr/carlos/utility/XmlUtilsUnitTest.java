/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link XmlUtils} XML DOM manipulation utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("XmlUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
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
    @DisplayName("newDocument")
    class NewDocument {

        @Test
        @DisplayName("should create non-null empty document")
        void shouldCreateNonNullDocument() {
            Document doc = XmlUtils.newDocument();
            assertThat(doc).isNotNull();
        }
    }

    @Nested
    @DisplayName("toBytes")
    class ToBytes {

        @Test
        @DisplayName("should serialize document to byte array")
        void shouldSerializeDocument_toByteArray() throws Exception {
            Document doc = createTestDocument();
            byte[] bytes = XmlUtils.toBytes(doc, false);
            assertThat(bytes).isNotNull();
            assertThat(bytes.length).isGreaterThan(0);
            String xml = new String(bytes, "UTF-8");
            assertThat(xml).contains("root");
            assertThat(xml).contains("child");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("should serialize document to string")
        void shouldSerializeDocument_toString() throws Exception {
            Document doc = createTestDocument();
            String xml = XmlUtils.toString(doc);
            assertThat(xml).isNotNull();
            assertThat(xml).contains("<root>");
            assertThat(xml).contains("Hello");
        }
    }

    @Nested
    @DisplayName("toDocument")
    class ToDocument {

        @Test
        @DisplayName("should parse XML string to Document")
        void shouldParseXmlString_toDocument() {
            Document doc = XmlUtils.toDocument("<root><item>value</item></root>");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
        }

        @Test
        @DisplayName("should return null for invalid XML")
        void shouldReturnNull_forInvalidXml() {
            Document doc = XmlUtils.toDocument("not xml at all");
            assertThat(doc).isNull();
        }
    }

    @Nested
    @DisplayName("getAttributeValue")
    class GetAttributeValue {

        @Test
        @DisplayName("should return attribute value from element")
        void shouldReturnAttributeValue() throws Exception {
            Document doc = createTestDocument();
            Element child = (Element) doc.getDocumentElement().getElementsByTagName("child").item(0);
            String value = XmlUtils.getAttributeValue(child, "id");
            assertThat(value).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("getChildElements")
    class GetChildElements {

        @Test
        @DisplayName("should return list of child elements")
        void shouldReturnChildElements() throws Exception {
            Document doc = createTestDocument();
            ArrayList<Element> children = XmlUtils.getChildElements(doc.getDocumentElement());
            assertThat(children).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getAllAttributeValues")
    class GetAllAttributeValues {

        @Test
        @DisplayName("should return map of all attributes")
        void shouldReturnAttributeMap() throws Exception {
            Document doc = createTestDocument();
            Element child = (Element) doc.getDocumentElement().getElementsByTagName("child").item(0);
            Map<String, String> attrs = XmlUtils.getAllAttributeValues(child);
            assertThat(attrs).containsEntry("id", "1");
            assertThat(attrs).containsEntry("name", "test");
        }
    }

    @Nested
    @DisplayName("Base64 round-trip")
    class Base64RoundTrip {

        @Test
        @DisplayName("should round-trip document through Base64")
        void shouldRoundTrip_throughBase64() throws Exception {
            Document doc = createTestDocument();
            String base64 = XmlUtils.toBase64String(doc);
            assertThat(base64).isNotNull().isNotEmpty();

            Document restored = XmlUtils.fromBase64String(base64);
            assertThat(restored).isNotNull();
            assertThat(restored.getDocumentElement().getTagName()).isEqualTo("root");
        }
    }

    @Nested
    @DisplayName("getChildNodeByTagName")
    class GetChildNodeByTagName {

        @Test
        @DisplayName("should find child node by tag name")
        void shouldFindChild_byTagName() throws Exception {
            Document doc = createTestDocument();
            Node child = XmlUtils.getChildNodeByTagName(doc.getDocumentElement(), "child");
            assertThat(child).isNotNull();
            assertThat(child.getTextContent()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("should return null for non-existent tag")
        void shouldReturnNull_forNonExistentTag() throws Exception {
            Document doc = createTestDocument();
            Node child = XmlUtils.getChildNodeByTagName(doc.getDocumentElement(), "nonexistent");
            assertThat(child).isNull();
        }
    }
}
