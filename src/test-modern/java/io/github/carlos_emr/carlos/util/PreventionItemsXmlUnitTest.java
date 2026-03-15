/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 QuickTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PreventionItems.xml parsing.
 *
 * <p>Verifies that the PreventionItems.xml resource can be loaded from the classpath
 * and parsed correctly into prevention item elements with expected attributes.
 * Migrated from legacy JUnit 4 QuickTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("prevention")
@DisplayName("PreventionItems XML parsing unit tests")
class PreventionItemsXmlUnitTest {

    @Test
    @DisplayName("should parse PreventionItems XML and extract item descriptions")
    void shouldParsePreventionItemsXml_andExtractDescriptions() throws Exception {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("oscar/oscarPrevention/PreventionItems.xml")) {
            assertThat(is).as("PreventionItems.xml should be available on classpath").isNotNull();

            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();
            List<Element> items = root.getChildren("item");

            assertThat(items).as("Should contain prevention items").isNotEmpty();

            for (Element item : items) {
                List<Attribute> attributes = item.getAttributes();
                Map<String, String> attributeMap = new HashMap<>();
                for (Attribute attr : attributes) {
                    attributeMap.put(attr.getName(), attr.getValue());
                }
                assertThat(attributeMap).containsKey("name");
            }
        }
    }
}
