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
import java.text.SimpleDateFormat;

import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

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

    @Nested
    @DisplayName("XML Document parsing")
    class DocumentParsing {

        @Test
        @DisplayName("should parse valid XML into a Document with correct root element")
        void shouldParseXml_intoDocumentWithCorrectRootElement() {
            Document doc = templateHandler.getIndicatorTemplateDocument();
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("indicatorTemplateXML");
        }

        @Test
        @DisplayName("should mark XML as valid after successful parsing")
        void shouldMarkXmlAsValid_afterSuccessfulParsing() {
            assertThat(templateHandler.isValidXML()).isTrue();
        }

        @Test
        @DisplayName("should pass schema validation for well-formed template")
        void shouldPassSchemaValidation_forWellFormedTemplate() {
            assertThat(templateHandler.validate()).isTrue();
        }
    }

    @Nested
    @DisplayName("IndicatorTemplate entity generation")
    class IndicatorTemplateEntity {

        @Test
        @DisplayName("should create entity with correct heading fields from XML")
        void shouldCreateEntity_withCorrectHeadingFieldsFromXml() {
            IndicatorTemplate entity = templateHandler.getIndicatorTemplateEntity();
            assertThat(entity).isNotNull();
            assertThat(entity.getCategory()).isEqualTo("CDM");
            assertThat(entity.getSubCategory()).isEqualTo("Diabetes");
            assertThat(entity.getName()).isEqualTo("Diabetes with HbA1C Testing");
            assertThat(entity.getFramework()).startsWith("Based on and adapted from");
        }

        @Test
        @DisplayName("should set entity defaults for active and locked fields")
        void shouldSetEntityDefaults_forActiveAndLockedFields() {
            IndicatorTemplate entity = templateHandler.getIndicatorTemplateEntity();
            assertThat(entity.isActive()).isTrue();
            assertThat(entity.isLocked()).isFalse();
        }

        @Test
        @DisplayName("should parse framework version date from XML")
        void shouldParseFrameworkVersionDate_fromXml() {
            IndicatorTemplate entity = templateHandler.getIndicatorTemplateEntity();
            assertThat(entity.getFrameworkVersion()).isNotNull();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
            assertThat(sdf.format(entity.getFrameworkVersion())).isEqualTo("10-01-2015");
        }

        @Test
        @DisplayName("should include template XML content in entity")
        void shouldIncludeTemplateXmlContent_inEntity() {
            IndicatorTemplate entity = templateHandler.getIndicatorTemplateEntity();
            assertThat(entity.getTemplate()).isNotNull();
            assertThat(entity.getTemplate()).contains("indicatorTemplateXML");
            assertThat(entity.getTemplate()).contains("Diabetes with HbA1C Testing");
        }

        @Test
        @DisplayName("should populate definition and notes from XML")
        void shouldPopulateDefinitionAndNotes_fromXml() {
            IndicatorTemplate entity = templateHandler.getIndicatorTemplateEntity();
            assertThat(entity.getDefinition()).contains("patients with diabetes");
            assertThat(entity.getNotes()).contains("test template");
        }
    }

    @Nested
    @DisplayName("IndicatorTemplateXML bean generation")
    class IndicatorTemplateXMLBean {

        @Test
        @DisplayName("should parse XML heading elements into bean properties")
        void shouldParseXmlHeadingElements_intoBeanProperties() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean).isNotNull();
            assertThat(xmlBean.getCategory()).isEqualTo("CDM");
            assertThat(xmlBean.getSubCategory()).isEqualTo("Diabetes");
            assertThat(xmlBean.getName()).isEqualTo("Diabetes with HbA1C Testing");
            assertThat(xmlBean.getFramework()).startsWith("Based on and adapted from");
            assertThat(xmlBean.getFrameworkVersion()).isEqualTo("10-01-2015");
        }

        @Test
        @DisplayName("should parse author from XML")
        void shouldParseAuthor_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getAuthor()).contains("Colcamex Resources");
        }

        @Test
        @DisplayName("should parse indicator query version from XML")
        void shouldParseIndicatorQueryVersion_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getIndicatorQueryVersion()).isEqualTo("07-15-2016");
            assertThat(xmlBean.getDrilldownQueryVersion()).isEqualTo("07-20-2016");
        }

        @Test
        @DisplayName("should parse indicator query SQL from XML")
        void shouldParseIndicatorQuerySql_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            String query = xmlBean.getIndicatorQuery();
            assertThat(query).isNotEmpty();
            assertThat(query).contains("SELECT");
            assertThat(query).contains("demographic");
        }

        @Test
        @DisplayName("should parse indicator parameters from XML")
        void shouldParseIndicatorParameters_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getIndicatorParameters()).isNotNull();
            assertThat(xmlBean.getIndicatorParameters()).isNotEmpty();
            assertThat(xmlBean.getIndicatorParameters())
                    .extracting(p -> p.getId())
                    .contains("provider", "active", "dxcodelist");
        }

        @Test
        @DisplayName("should parse indicator ranges from XML")
        void shouldParseIndicatorRanges_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getIndicatorRanges()).isNotNull();
            assertThat(xmlBean.getIndicatorRanges()).hasSize(5);
            assertThat(xmlBean.getIndicatorRanges())
                    .extracting(r -> r.getId())
                    .contains("a1c");
        }

        @Test
        @DisplayName("should parse drilldown display columns from XML")
        void shouldParseDrilldownDisplayColumns_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getDrilldownDisplayColumns()).isNotNull();
            assertThat(xmlBean.getDrilldownDisplayColumns()).hasSize(5);
            assertThat(xmlBean.getDrilldownDisplayColumns())
                    .extracting(c -> c.getId())
                    .containsExactly("demographic", "firstName", "lastName", "dob", "a1c");
        }

        @Test
        @DisplayName("should parse drilldown actions from XML")
        void shouldParseDrilldownActions_fromXml() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getDrilldownActions()).isNotNull();
            assertThat(xmlBean.getDrilldownActions()).hasSize(3);
            assertThat(xmlBean.getDrilldownActions())
                    .extracting(a -> a.getId())
                    .containsExactly("tickler", "dxUpdate", "demoExcl");
        }

        @Test
        @DisplayName("should store raw template string in XML bean")
        void shouldStoreRawTemplateString_inXmlBean() {
            IndicatorTemplateXML xmlBean = templateHandler.getIndicatorTemplateXML();
            assertThat(xmlBean.getTemplate()).isNotNull();
            assertThat(xmlBean.getTemplate()).contains("indicatorTemplateXML");
        }
    }
}
