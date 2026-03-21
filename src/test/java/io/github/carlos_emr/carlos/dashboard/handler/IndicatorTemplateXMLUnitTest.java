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
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.dashboard.query.DrillDownAction;
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface;
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface.Limit;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Unit tests for {@link IndicatorTemplateXML}.
 *
 * <p>Tests XML parsing of indicator templates using the diabetes_hba1c_test.xml
 * template resource. Validates that all heading, indicator query, and drilldown
 * query elements are correctly parsed from the XML document.
 *
 * <p>Migrated from legacy JUnit 4 IndicatorTemplateXMLTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("IndicatorTemplateXML unit tests")
class IndicatorTemplateXMLUnitTest {

    private static IndicatorTemplateXML indicatorTemplateXML;
    private static Document xmlDocument;
    private static MockedStatic<SpringUtils> springUtilsMock;

    @BeforeAll
    static void setUpBeforeAll() throws ParserConfigurationException, SAXException, IOException {
        springUtilsMock = Mockito.mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(DashboardManager.class))
                .thenReturn(Mockito.mock(DashboardManager.class));
        springUtilsMock.when(() -> SpringUtils.getBean(DemographicExtDao.class))
                .thenReturn(Mockito.mock(DemographicExtDao.class));
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource("indicatorXMLTemplates/diabetes_hba1c_test.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        xmlDocument = builder.parse(url.openStream());
        indicatorTemplateXML = new IndicatorTemplateXML(xmlDocument);
    }

    @AfterAll
    static void tearDownAfterAll() {
        indicatorTemplateXML = null;
        xmlDocument = null;
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    /** Tests for heading element parsing. */
    @Nested
    @DisplayName("Heading elements")
    class HeadingElements {

        @Test
        @DisplayName("should return author from XML template")
        void shouldReturnAuthor_fromXmlTemplate() {
            assertThat(indicatorTemplateXML.getAuthor())
                    .isEqualTo("Colcamex Resources Inc. Copyright 2016");
        }

        @Test
        @DisplayName("should return category from heading element")
        void shouldReturnCategory_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getCategory()).isEqualTo("CDM");
        }

        @Test
        @DisplayName("should return sub-category from heading element")
        void shouldReturnSubCategory_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getSubCategory()).isEqualTo("Diabetes");
        }

        @Test
        @DisplayName("should return framework description from heading element")
        void shouldReturnFramework_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getFramework())
                    .isEqualTo("Based on and adapted from HQO's PCPM: Priority Measures for System and Practice Levels (Oct 2015)");
        }

        @Test
        @DisplayName("should return framework version from heading element")
        void shouldReturnFrameworkVersion_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getFrameworkVersion()).isEqualTo("10-01-2015");
        }

        @Test
        @DisplayName("should return indicator name from heading element")
        void shouldReturnName_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getName()).isEqualTo("Diabetes with HbA1C Testing");
        }

        @Test
        @DisplayName("should return definition from heading element")
        void shouldReturnDefinition_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getDefinition())
                    .isEqualTo("% of patients with diabetes aged 40 years and older who have had two or more HbA1C tests within the past 12 months");
        }

        @Test
        @DisplayName("should return notes from heading element")
        void shouldReturnNotes_fromHeadingElement() {
            assertThat(indicatorTemplateXML.getNotes())
                    .isEqualTo("This is a test template for the Diabetes with HbA1C Testing Indicator query");
        }
    }

    /** Tests for indicator query element parsing. */
    @Nested
    @DisplayName("Indicator query elements")
    class IndicatorQueryElements {

        @Test
        @DisplayName("should return indicator query version")
        void shouldReturnIndicatorQueryVersion_fromIndicatorQueryElement() {
            assertThat(indicatorTemplateXML.getIndicatorQueryVersion()).isEqualTo("07-15-2016");
        }

        @Test
        @DisplayName("should return non-null indicator query string")
        void shouldReturnIndicatorQuery_whenParsed() {
            assertThat(indicatorTemplateXML.getIndicatorQuery()).isNotNull();
            assertThat(indicatorTemplateXML.getIndicatorQuery()).contains("SELECT");
            assertThat(indicatorTemplateXML.getIndicatorQuery()).contains("demographic");
        }

        @Test
        @DisplayName("should return provider as first indicator parameter")
        void shouldReturnProvider_asFirstIndicatorParameter() {
            assertThat(indicatorTemplateXML.getIndicatorParameters().get(0).getId())
                    .isEqualTo("provider");
        }

        @Test
        @DisplayName("should return excludedPatient as fourth indicator parameter")
        void shouldReturnExcludedPatient_asFourthIndicatorParameter() {
            assertThat(indicatorTemplateXML.getIndicatorParameters().get(3).getId())
                    .isEqualTo("excludedPatient");
        }

        @Test
        @DisplayName("should return four indicator parameters")
        void shouldReturnFourParameters_fromIndicatorQuery() {
            assertThat(indicatorTemplateXML.getIndicatorParameters()).hasSize(4);
        }
    }

    /** Tests for indicator range parsing. */
    @Nested
    @DisplayName("Indicator ranges")
    class IndicatorRanges {

        @Test
        @DisplayName("should contain a1c range in indicator ranges")
        void shouldContainA1cRange_inIndicatorRanges() {
            boolean found = false;
            for (RangeInterface range : indicatorTemplateXML.getIndicatorRanges()) {
                if ("a1c".equals(range.getId())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("should return five indicator ranges")
        void shouldReturnFiveRanges_fromIndicatorQuery() {
            assertThat(indicatorTemplateXML.getIndicatorRanges()).hasSize(5);
        }

        @Test
        @DisplayName("should contain RangeUpperLimit type in indicator ranges")
        void shouldContainRangeUpperLimit_inIndicatorRanges() {
            boolean found = false;
            for (RangeInterface range : indicatorTemplateXML.getIndicatorRanges()) {
                if (Limit.RangeUpperLimit.name().equals(range.getClass().getSimpleName())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("should contain RangeLowerLimit type in indicator ranges")
        void shouldContainRangeLowerLimit_inIndicatorRanges() {
            boolean found = false;
            for (RangeInterface range : indicatorTemplateXML.getIndicatorRanges()) {
                if (Limit.RangeLowerLimit.name().equals(range.getClass().getSimpleName())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    /** Tests for drilldown query element parsing. */
    @Nested
    @DisplayName("Drilldown query elements")
    class DrilldownQueryElements {

        @Test
        @DisplayName("should return non-null drilldown query string")
        void shouldReturnDrilldownQuery_whenParsed() {
            assertThat(indicatorTemplateXML.getDrilldownQuery()).isNotNull();
            assertThat(indicatorTemplateXML.getDrilldownQuery()).contains("SELECT");
            assertThat(indicatorTemplateXML.getDrilldownQuery()).contains("demographic");
        }

        @Test
        @DisplayName("should return drilldown query version")
        void shouldReturnDrilldownQueryVersion_fromDrilldownElement() {
            assertThat(indicatorTemplateXML.getDrilldownQueryVersion()).isEqualTo("07-20-2016");
        }

        @Test
        @DisplayName("should return provider as first drilldown parameter")
        void shouldReturnProvider_asFirstDrilldownParameter() {
            assertThat(indicatorTemplateXML.getDrilldownParameters("null").get(0).getId())
                    .isEqualTo("provider");
        }

        @Test
        @DisplayName("should return excludedPatient as fourth drilldown parameter")
        void shouldReturnExcludedPatient_asFourthDrilldownParameter() {
            assertThat(indicatorTemplateXML.getDrilldownParameters("null").get(3).getId())
                    .isEqualTo("excludedPatient");
        }

        @Test
        @DisplayName("should return four drilldown parameters")
        void shouldReturnFourParameters_fromDrilldownQuery() {
            assertThat(indicatorTemplateXML.getDrilldownParameters("null")).hasSize(4);
        }
    }

    /** Tests for drilldown display and export column parsing. */
    @Nested
    @DisplayName("Drilldown columns")
    class DrilldownColumns {

        @Test
        @DisplayName("should return demographic as first display column")
        void shouldReturnDemographic_asFirstDisplayColumn() {
            assertThat(indicatorTemplateXML.getDrilldownDisplayColumns().get(0).getId())
                    .isEqualTo("demographic");
        }

        @Test
        @DisplayName("should return five display columns")
        void shouldReturnFiveDisplayColumns_fromDrilldownQuery() {
            assertThat(indicatorTemplateXML.getDrilldownDisplayColumns()).hasSize(5);
        }

        @Test
        @DisplayName("should return lastName as third export column")
        void shouldReturnLastName_asThirdExportColumn() {
            assertThat(indicatorTemplateXML.getDrilldownExportColumns().get(2).getId())
                    .isEqualTo("lastName");
        }

        @Test
        @DisplayName("should return five export columns")
        void shouldReturnFiveExportColumns_fromDrilldownQuery() {
            assertThat(indicatorTemplateXML.getDrilldownExportColumns()).hasSize(5);
        }
    }

    /** Tests for drilldown action parsing. */
    @Nested
    @DisplayName("Drilldown actions")
    class DrilldownActions {

        @Test
        @DisplayName("should contain dxUpdate action with value 250")
        void shouldContainDxUpdateAction_withValue250() {
            boolean found = false;
            for (DrillDownAction action : indicatorTemplateXML.getDrilldownActions()) {
                if ("dxUpdate".equals(action.getId()) && "250".equals(action.getValue())) {
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("should return three drilldown actions")
        void shouldReturnThreeActions_fromDrilldownQuery() {
            assertThat(indicatorTemplateXML.getDrilldownActions()).hasSize(3);
        }
    }
}
