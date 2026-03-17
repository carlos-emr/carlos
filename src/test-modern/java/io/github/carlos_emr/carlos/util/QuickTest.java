/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.util;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests XML parsing of PreventionItems.xml, verifying that prevention item
 * definitions can be loaded and parsed from the classpath resource.
 *
 * <p>Migrated from legacy JUnit 4 {@code QuickTest}. The original test parsed the
 * prevention items XML and printed each item's description; this modern version
 * adds assertions to verify the parsing succeeds and produces valid results.</p>
 *
 * @since 2006-01-01
 */
@Tag("unit")
@Tag("prevention")
@Tag("read")
@DisplayName("PreventionItems XML Parsing Tests")
class QuickTest {

    private static Document doc;
    private static List<Element> items;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUpBeforeAll() throws Exception {
        try (InputStream is = QuickTest.class.getClassLoader()
                .getResourceAsStream("oscar/oscarPrevention/PreventionItems.xml")) {
            assertThat(is).as("PreventionItems.xml should be on the classpath").isNotNull();
            SAXBuilder parser = new SAXBuilder();
            doc = parser.build(is);
        }
        Element root = doc.getRootElement();
        items = root.getChildren("item");
    }

    @Nested
    @DisplayName("XML document structure")
    class XmlDocumentStructure {

        @Test
        @DisplayName("should have 'preventions' as root element")
        void shouldHavePreventions_asRootElement() {
            assertThat(doc.getRootElement().getName()).isEqualTo("preventions");
        }

        @Test
        @DisplayName("should contain multiple prevention items")
        void shouldContainMultiplePreventionItems() {
            assertThat(items).hasSizeGreaterThan(10);
        }
    }

    @Nested
    @DisplayName("Prevention item attributes")
    class PreventionItemAttributes {

        @Test
        @DisplayName("should have required 'name' attribute on every item")
        void shouldHaveRequiredNameAttribute_onEveryItem() {
            for (Element item : items) {
                assertThat(item.getAttributeValue("name"))
                        .as("item should have 'name' attribute")
                        .isNotNull()
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("should have required 'desc' attribute on every item")
        void shouldHaveRequiredDescAttribute_onEveryItem() {
            for (Element item : items) {
                assertThat(item.getAttributeValue("desc"))
                        .as("item '%s' should have 'desc' attribute", item.getAttributeValue("name"))
                        .isNotNull();
            }
        }

        @Test
        @DisplayName("should have unique names across all items")
        void shouldHaveUniqueNames_acrossAllItems() {
            Set<String> names = new HashSet<>();
            for (Element item : items) {
                String name = item.getAttributeValue("name");
                assertThat(names.add(name))
                        .as("item name '%s' should be unique", name)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should contain known immunization types")
        void shouldContainKnownImmunizationTypes() {
            Set<String> names = new HashSet<>();
            for (Element item : items) {
                names.add(item.getAttributeValue("name"));
            }
            // Standard Canadian immunizations that should be present
            assertThat(names).contains("DTaP");
        }

        @Test
        @DisplayName("should parse all attributes into map for each item")
        void shouldParseAllAttributes_intoMapForEachItem() {
            for (Element item : items) {
                List<Attribute> attrs = item.getAttributes();
                assertThat(attrs).as("item '%s' should have attributes",
                        item.getAttributeValue("name")).isNotEmpty();

                Map<String, String> attrMap = new HashMap<>();
                for (Attribute attr : attrs) {
                    attrMap.put(attr.getName(), attr.getValue());
                }
                // Each item should have at least name and desc
                assertThat(attrMap).containsKeys("name", "desc");
            }
        }

        @Test
        @DisplayName("should have headingName attribute for categorization")
        void shouldHaveHeadingNameAttribute_forCategorization() {
            // At least some items should have headingName for UI grouping
            long withHeading = items.stream()
                    .filter(item -> item.getAttributeValue("headingName") != null)
                    .count();
            assertThat(withHeading).as("items with headingName attribute").isGreaterThan(0);
        }
    }
}
