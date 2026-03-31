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
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;

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
        @DisplayName("should create non-null document with root element")
        void shouldCreateDocument_withRootElement() throws ParserConfigurationException {
            Document doc = XmlUtils.newDocument("root");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
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
            String xml = XmlUtils.toString(doc, false);
            assertThat(xml).isNotNull();
            assertThat(xml).contains("root");
            assertThat(xml).contains("Hello");
        }
    }

    @Nested
    @DisplayName("toDocument from string")
    class ToDocument {

        @Test
        @DisplayName("should parse XML string to Document")
        void shouldParseXmlString_toDocument() throws Exception {
            Document doc = XmlUtils.toDocument("<root><item>value</item></root>");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
        }
    }

    @Nested
    @DisplayName("getAttributeValue")
    class GetAttributeValue {

        @Test
        @DisplayName("should return attribute value from node")
        void shouldReturnAttributeValue() throws Exception {
            Document doc = createTestDocument();
            Element child = (Element) doc.getDocumentElement().getElementsByTagName("child").item(0);
            String value = XmlUtils.getAttributeValue(child, "id");
            assertThat(value).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("getChildNode")
    class GetChildNode {

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
        void shouldReturnNull_forNonExistent() throws Exception {
            Document doc = createTestDocument();
            Node child = XmlUtils.getChildNode(doc.getDocumentElement(), "nonexistent");
            assertThat(child).isNull();
        }
    }

    @Nested
    @DisplayName("getChildNodes")
    class GetChildNodes {

        @Test
        @DisplayName("should return list of matching child nodes")
        void shouldReturnMatchingChildren() throws Exception {
            Document doc = createTestDocument();
            ArrayList<Node> children = XmlUtils.getChildNodes(doc.getDocumentElement(), "child");
            assertThat(children).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list for non-existent children")
        void shouldReturnEmptyList_forNonExistent() throws Exception {
            Document doc = createTestDocument();
            ArrayList<Node> children = XmlUtils.getChildNodes(doc.getDocumentElement(), "nonexistent");
            assertThat(children).isEmpty();
        }
    }

    @Nested
    @DisplayName("getChildNodeTextContents")
    class GetChildNodeTextContents {

        @Test
        @DisplayName("should return text content of named child")
        void shouldReturnTextContent() throws Exception {
            Document doc = createTestDocument();
            String text = XmlUtils.getChildNodeTextContents(doc.getDocumentElement(), "child");
            assertThat(text).isEqualTo("Hello");
        }
    }

    @Nested
    @DisplayName("appendChildToRoot")
    class AppendChildToRoot {

        @Test
        @DisplayName("should append child element with text to root")
        void shouldAppendChild_withText() throws ParserConfigurationException {
            Document doc = XmlUtils.newDocument("root");
            XmlUtils.appendChildToRoot(doc, "name", "John");
            String text = XmlUtils.getChildNodeTextContents(doc.getDocumentElement(), "name");
            assertThat(text).isEqualTo("John");
        }
    }
}
