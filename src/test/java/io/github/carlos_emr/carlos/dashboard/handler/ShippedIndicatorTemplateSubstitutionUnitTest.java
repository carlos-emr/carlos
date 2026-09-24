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
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.w3c.dom.Document;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.dashboard.query.Column;
import io.github.carlos_emr.carlos.dashboard.query.Parameter;
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Regression guard for the placeholder-substitution hardening in {@link AbstractQueryHandler}:
 * every indicator template shipped in {@code src/main/resources/indicatorXMLTemplates} must build
 * exactly the same SQL as the pre-hardening algorithm (raw {@code replaceAll} with unescaped
 * multi-value tokens). Shipped templates contain no {@code $}, backslash or quote inside
 * multi-value tokens, so any difference here means the hardening changed live indicator SQL.
 *
 * @since 2026-09-24
 */
@DisplayName("Shipped indicator templates substitution parity")
@Tag("unit")
@Tag("dashboard")
class ShippedIndicatorTemplateSubstitutionUnitTest {

    private static final Path TEMPLATE_DIR = Paths.get("src", "main", "resources", "indicatorXMLTemplates");
    private static final String PLACE_HOLDER_PATTERN = "(\\$){1}(\\{){1}( )*##( )*(\\}){1}";
    private static final String PROVIDER_NO = "999998";

    private static MockedStatic<SpringUtils> springUtilsMock;

    @BeforeAll
    static void setUpSpringUtils() {
        springUtilsMock = Mockito.mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(DashboardManager.class))
                .thenReturn(Mockito.mock(DashboardManager.class));
        springUtilsMock.when(() -> SpringUtils.getBean(DemographicExtDao.class))
                .thenReturn(Mockito.mock(DemographicExtDao.class));
    }

    @AfterAll
    static void closeSpringUtils() {
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    static Stream<Path> shippedTemplates() throws Exception {
        List<Path> templates = new ArrayList<>();
        try (Stream<Path> files = Files.list(TEMPLATE_DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".xml"))
                    // The authoring skeleton has empty placeholder attributes, not a real indicator.
                    .filter(p -> !p.getFileName().toString().equals("IndicatorXMLTemplate.xml"))
                    .sorted()
                    .forEach(templates::add);
        }
        assertThat(templates).as("shipped indicator templates").isNotEmpty();
        return templates.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedTemplates")
    @DisplayName("should build the same SQL as the legacy substitution for each shipped template")
    void shouldBuildIdenticalSql_forShippedTemplate(Path template) throws Exception {
        IndicatorTemplateXML xml = load(template);

        List<Parameter> indicatorParameters = xml.getIndicatorParameters();
        List<RangeInterface> indicatorRanges = xml.getIndicatorRanges();
        List<Parameter> drilldownParameters = xml.getDrilldownParameters(null);
        List<RangeInterface> drilldownRanges = xml.getDrilldownRanges();

        String indicatorSql = assertParity(xml.getIndicatorQuery(), indicatorParameters, indicatorRanges);
        // Guards against a vacuous pass: every placeholder must have been filled. (Some templates,
        // e.g. patient_status.xml, have an indicator query with no placeholders at all.)
        assertThat(indicatorSql).doesNotContain("${");
        assertParity(xml.getDrilldownQuery(), drilldownParameters, drilldownRanges);

        // Column definitions are substituted too (columns are expanded before parameters).
        StringBuilder columnText = new StringBuilder();
        for (List<Column> columns : List.of(nullSafe(xml.getDrilldownDisplayColumns()),
                nullSafe(xml.getDrilldownExportColumns()))) {
            for (Column column : columns) {
                columnText.append(column.getName()).append(" AS '").append(column.getTitle()).append("'\n");
            }
        }
        assertParity(columnText.toString(), drilldownParameters, drilldownRanges);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static IndicatorTemplateXML load(Path template) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document;
        try (InputStream in = Files.newInputStream(template)) {
            document = factory.newDocumentBuilder().parse(in);
        }
        IndicatorTemplateXML xml = new IndicatorTemplateXML(document);
        // Stands in for the logged-in provider alias so no LoggedInInfo is needed.
        xml.setProviderNo(PROVIDER_NO);
        return xml;
    }

    private static String assertParity(String query, List<Parameter> parameters, List<RangeInterface> ranges) {
        if (query == null) {
            return null;
        }
        IndicatorQueryHandler handler = new IndicatorQueryHandler();
        String actual = query;
        String expected = query;
        if (parameters != null) {
            actual = handler.addParameters(parameters, actual);
            for (Parameter parameter : parameters) {
                expected = expected.replaceAll(pattern(parameter.getId()), legacyParseParameterValue(parameter.getValue()));
            }
        }
        if (ranges != null) {
            actual = handler.addRanges(ranges, actual);
            for (RangeInterface range : ranges) {
                String prefix = "RangeLowerLimit".equals(range.getClass().getSimpleName())
                        ? IndicatorTemplateXML.RangeType.lowerLimit.name()
                        : IndicatorTemplateXML.RangeType.upperLimit.name();
                expected = expected.replaceAll(pattern(prefix + "\\." + range.getId().trim()), range.getValue());
            }
        }
        assertThat(actual).isEqualTo(expected);
        return actual;
    }

    private static String pattern(String id) {
        return PLACE_HOLDER_PATTERN.replace("##", id.trim());
    }

    /** Verbatim copy of the pre-hardening {@code parseParameterValue}. */
    private static String legacyParseParameterValue(String[] values) {
        if (values.length > 1) {
            StringBuilder stringBuilder = new StringBuilder("(");
            for (String value : values) {
                stringBuilder.append("'").append(value).append("',");
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.append(")");
            return stringBuilder.toString();
        }
        return values[0].trim();
    }
}
