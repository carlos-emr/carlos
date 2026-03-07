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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests XML parsing of PreventionItems.xml, verifying that prevention item
 * definitions can be loaded and parsed from the classpath resource.
 *
 * <p>Migrated from legacy JUnit 4 {@code QuickTest}. The original test parsed the
 * prevention items XML and printed each item's description; this modern version
 * adds assertions to verify the parsing succeeds and produces results.</p>
 *
 * @since 2006-01-01
 */
@Tag("unit")
@Tag("prevention")
@Tag("read")
@DisplayName("PreventionItems XML Parsing Tests")
class QuickTest {

    private static final Logger logger = LogManager.getLogger(QuickTest.class);

    @Test
    @DisplayName("should parse prevention items from XML resource")
    @SuppressWarnings("unchecked")
    void shouldParsePreventionItems_fromXmlResource() {
        InputStream is = null;
        try {
            is = this.getClass().getClassLoader()
                .getResourceAsStream("oscar/oscarPrevention/PreventionItems.xml");

            assertThat(is).as("PreventionItems.xml should be on the classpath").isNotNull();

            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();
            List<Element> items = root.getChildren("item");

            assertThat(items).as("PreventionItems.xml should contain item elements").isNotEmpty();

            for (int i = 0; i < items.size(); i++) {
                Element e = items.get(i);
                List<Attribute> attr = e.getAttributes();
                HashMap<String, String> h = new HashMap<String, String>();
                for (int j = 0; j < attr.size(); j++) {
                    Attribute att = attr.get(j);
                    h.put(att.getName(), att.getValue());
                }
                logger.info(h.get("desc"));
            }

        } catch (Exception e) {
            logger.error("Error", e);
            throw new AssertionError("Failed to parse PreventionItems.xml", e);
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                logger.error("Failed to close input stream", e);
            }
        }
    }
}
