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
 * Migrated from legacy JUnit 4 test to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IndicatorTemplateHandler}.
 *
 * <p>Tests that the handler correctly parses a byte array into an XML Document,
 * an IndicatorTemplate entity, and an IndicatorTemplateXML bean.
 * Uses the diabetes_hba1c_test.xml template resource.
 *
 * <p>Migrated from legacy JUnit 4 IndicatorTemplateHandlerTest. The legacy
 * test had empty method bodies; this modern version adds actual assertions
 * for the same three methods.
 *
 * @since 2016-07-15 (original)
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("IndicatorTemplateHandler unit tests")
class IndicatorTemplateHandlerUnitTest {

    private static IndicatorTemplateHandler templateHandler;

    @BeforeAll
    static void setUpBeforeAll() throws IOException {
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource("indicatorXMLTemplates/diabetes_hba1c_test.xml");
        try (InputStream is = url.openStream()) {
            templateHandler = new IndicatorTemplateHandler(IOUtils.toByteArray(is));
        }
    }

    @Test
    @DisplayName("should return non-null indicator template document after parsing")
    void shouldReturnNonNullDocument_afterParsing() {
        assertThat(templateHandler.getIndicatorTemplateDocument()).isNotNull();
    }

    @Test
    @DisplayName("should return non-null indicator template entity after parsing")
    void shouldReturnNonNullEntity_afterParsing() {
        assertThat(templateHandler.getIndicatorTemplateEntity()).isNotNull();
    }

    @Test
    @DisplayName("should return non-null indicator template XML bean after parsing")
    void shouldReturnNonNullXmlBean_afterParsing() {
        assertThat(templateHandler.getIndicatorTemplateXML()).isNotNull();
    }
}
