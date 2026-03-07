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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.dashboard.display.beans.GraphPlot;
import io.github.carlos_emr.carlos.dashboard.query.Parameter;
import io.github.carlos_emr.carlos.dashboard.query.RangeInterface;

/**
 * Unit tests for {@link IndicatorQueryHandler}.
 *
 * <p>Tests query building from XML template parameters and ranges, and
 * the creation of graph plot data from query result maps.
 * Uses the diabetes_hba1c_in_range_test.xml template resource.
 *
 * <p>Migrated from legacy JUnit 4 IndicatorQueryHandlerTest.
 *
 * @since 2016-07-15 (original)
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("IndicatorQueryHandler unit tests")
class IndicatorQueryHandlerUnitTest {

    private static final String EXPECTED_QUERY = "SELECT COUNT(fin.patient) AS \"DM Patients\", IF ( COUNT(fin.patient) > 0, "
            + "ROUND( SUM( CASE WHEN fin.a1c > 2 THEN 1 ELSE 0 END ) * 100 / COUNT(fin.patient) , 1 ), 0) AS \"HbA1c (%)\", "
            + "IF ( COUNT(fin.patient) > 0, ROUND( SUM( CASE WHEN fin.a1c > 0 THEN 1 ELSE 0 END )  * 100 / "
            + "COUNT(fin.patient) , 1 ), 0) AS \"HbA1c 2x (%)\" FROM ( SELECT d.demographic_no AS patient, "
            + "A1C.a1cnumber AS a1c, A1C9.a1c9number AS a1c9 FROM demographic d INNER JOIN dxresearch dxr "
            + "ON ( d.demographic_no = dxr.demographic_no) LEFT JOIN ( SELECT COUNT(*) AS a1cnumber, demographicNo "
            + "FROM measurements WHERE type LIKE \"A1C\" AND ( DATE(dateObserved) BETWEEN DATE('12-12-2012') AND now() ) "
            + "AND demographicNo > 0 AND providerNo LIKE '' GROUP BY demographicNo HAVING COUNT(demographicNo) > -1 ) "
            + "A1C ON (d.demographic_no = A1C.demographicNo) LEFT JOIN ( SELECT COUNT(*) AS 'a1c9number', demographicNo "
            + "FROM measurements WHERE type LIKE \"A1C\" AND demographicNo > 0 GROUP BY demographicNo "
            + "HAVING COUNT(demographicNo) > -1 ) A1C9 ON (d.demographic_no = A1C9.demographicNo) "
            + "WHERE d.patient_status LIKE \"%AC%\" AND dxr.coding_system LIKE \"icd9\" AND dxr.dxresearch_code "
            + "LIKE \"250\" AND dxr.status NOT LIKE \"%D%\" AND d.demographic_no > 0 AND d.last_name LIKE 'test' AND "
            + "( 30 <= ROUND( ABS( DATEDIFF( DATE( CONCAT(d.year_of_birth,\"-\",d.month_of_birth,\"-\",d.date_of_birth) ), "
            + "DATE( now() ) ) / 365.25 ) ) AND 75 >= ROUND( ABS( DATEDIFF( "
            + "DATE( CONCAT(d.year_of_birth,\"-\",d.month_of_birth,\"-\",d.date_of_birth) ), DATE( now() ) ) / 365.25 ) ) ) ) fin;";

    private static List<Parameter> parameters;
    private static List<RangeInterface> ranges;
    private static IndicatorQueryHandler indicatorQueryHandler;
    private static String altQueryString;
    private static List<GraphPlot[]> graphPlotList;

    @BeforeAll
    static void setUpBeforeAll() throws IOException {
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource("indicatorXMLTemplates/diabetes_hba1c_in_range_test.xml");
        byte[] byteArray;
        try (InputStream is = url.openStream()) {
            byteArray = IOUtils.toByteArray(is);
        }

        // Parse the template via IndicatorTemplateHandler
        IndicatorTemplateHandler indicatorTemplateHandler = new IndicatorTemplateHandler(byteArray);
        IndicatorTemplateXML indicatorTemplateXML = indicatorTemplateHandler.getIndicatorTemplateXML();
        String queryString = indicatorTemplateXML.getIndicatorQuery();

        parameters = indicatorTemplateXML.getIndicatorParameters();
        ranges = indicatorTemplateXML.getIndicatorRanges();

        indicatorQueryHandler = new IndicatorQueryHandler();

        // Build the query string step by step (same as legacy test)
        altQueryString = indicatorQueryHandler.filterQueryString(queryString);
        altQueryString = indicatorQueryHandler.addParameters(parameters, altQueryString);
        altQueryString = indicatorQueryHandler.addRanges(ranges, altQueryString);

        // Also set query via the handler's own setQuery (which calls buildQuery internally)
        indicatorQueryHandler.setParameters(parameters);
        indicatorQueryHandler.setRanges(ranges);
        indicatorQueryHandler.setQuery(queryString);

        // Create graph plots from mock results
        List<Object> results = new ArrayList<>();
        HashMap<Object, Object> resultmap = new HashMap<>();
        resultmap.put("", 1);
        resultmap.put("dennis", 2);
        resultmap.put("key", 3);
        resultmap.put("Unit", 4);
        resultmap.put("DOUBLE", 5);
        results.add(resultmap);

        graphPlotList = IndicatorQueryHandler.createGraphPlots(results);
    }

    @Test
    @DisplayName("should return parameters matching those set from template")
    void shouldReturnParameters_matchingThoseSetFromTemplate() {
        assertThat(indicatorQueryHandler.getParameters()).isEqualTo(parameters);
    }

    @Test
    @DisplayName("should return built query matching expected SQL string")
    void shouldReturnBuiltQuery_matchingExpectedSqlString() {
        assertThat(indicatorQueryHandler.getQuery()).isEqualTo(EXPECTED_QUERY);
    }

    @Test
    @DisplayName("should return ranges matching those set from template")
    void shouldReturnRanges_matchingThoseSetFromTemplate() {
        assertThat(indicatorQueryHandler.getRanges()).isEqualTo(ranges);
    }

    @Test
    @DisplayName("should return manually built query matching expected SQL string")
    void shouldReturnManuallyBuiltQuery_matchingExpectedSqlString() {
        assertThat(altQueryString).isEqualTo(EXPECTED_QUERY);
    }

    @Test
    @DisplayName("should return graph plots with total numerator of 14")
    void shouldReturnGraphPlots_withTotalNumeratorOf14() {
        Double total = 0.0;
        for (GraphPlot[] plot : graphPlotList) {
            Double subtotal = 0.0;
            for (GraphPlot graphPlot : plot) {
                subtotal = subtotal + graphPlot.getNumerator();
            }
            total = total + subtotal;
        }
        assertThat(total).isEqualTo(Double.valueOf(14));
    }
}
