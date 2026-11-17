/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Hashtable;
import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import io.github.carlos_emr.carlos.mds.data.ProviderData;
import io.github.carlos_emr.carlos.utility.SpreadsheetCsv;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("report")
class ReportEvaluatorUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"FAKE,=1+1", "FAKE \"quoted\" provider", "FAKE\r\nsecond line", "=1+1", "+SUM(1,2)", "-1+1", "@SUM(1)", "\uFF1D1+1", "FAKE provider"})
    void shouldKeepProviderNamesInOneGuardedCsvCell_withStandardNumericColumns(String name) throws Exception {
        var evaluator = new ReportEvaluator();
        evaluator.denominator = mock(Denominator.class);
        evaluator.numeratorCount = 12;
        evaluator.denominatorCount = 20;
        when(evaluator.denominator.hasReplaceableValues()).thenReturn(true);
        Hashtable<String, String> values = new Hashtable<>();
        values.put("provider_no", "FAKE-provider");
        when(evaluator.denominator.getReplaceableValues()).thenReturn(values);
        try (var provider = mockStatic(ProviderData.class)) {
            provider.when(() -> ProviderData.getProviderName("FAKE-provider")).thenReturn(name);
            try (var parser = CSVFormat.DEFAULT.parse(new StringReader(evaluator.getCSV()))) {
                var rows = parser.getRecords();
                assertThat(rows).hasSize(1);
                assertThat(rows.getFirst()).containsExactly(SpreadsheetCsv.sanitizeCell(name), "12", "20", "60");
            }
        }
    }

    @Test
    void shouldWriteOnlyNumericCsvColumns_whenNoProviderIsSelected() throws Exception {
        var evaluator = new ReportEvaluator();
        evaluator.denominator = mock(Denominator.class);
        evaluator.numeratorCount = 12;
        evaluator.denominatorCount = 20;
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(evaluator.getCSV()))) {
            assertThat(parser.getRecords()).singleElement().satisfies(row ->
                    assertThat(row).containsExactly("12", "20", "60"));
        }
    }

    @Test
    void shouldPreserveIndependentNumeratorAndDenominator_whenAdditionalFiltersRejectRows() {
        var denominator = mock(Denominator.class);
        var numerator = mock(Numerator.class);
        var extra = mock(Numerator.class);
        var login = mock(LoggedInInfo.class);
        when(denominator.getDenominatorList()).thenReturn(List.of("101", "102", "103"));
        when(numerator.evaluate(login, "101")).thenReturn(true);
        when(numerator.evaluate(login, "103")).thenReturn(true);
        when(extra.evaluate(login, "101")).thenReturn(true);
        var filters = new Numerator[11]; filters[0] = extra;
        var evaluator = new ReportEvaluator();
        evaluator.evaluate(login, denominator, numerator, filters, null, false);
        assertThat(evaluator.getDenominatorCount()).isEqualTo(3);
        assertThat(evaluator.getNumeratorCount()).isEqualTo(1);
        assertThat(evaluator.getPercentage()).isCloseTo(100f / 3f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(evaluator.getReportResultList()).hasSize(1);
        assertThat(evaluator.getReportResultList().getFirst()).containsEntry("_demographic_no", "101");
    }

    @Test
    void shouldPreserveNamedMeasurementFields_whenUsingMapEntryCompatibilityPath() {
        var denominator = mock(Denominator.class);
        var numerator = mock(Numerator.class);
        var login = mock(LoggedInInfo.class);
        when(denominator.getDenominatorList()).thenReturn(List.of("101"));
        when(numerator.evaluate(login, "101")).thenReturn(true);
        var measurement = mock(EctMeasurementsDataBean.class);
        try (var construction = mockConstruction(EctMeasurementsDataBeanHandler.class, (mock, context) -> {
            assertThat(context.arguments()).isEqualTo(List.of(101, "FAKE-LOINC", false));
            when(mock.getMeasurementsDataVector()).thenReturn(new Vector<>(List.of(measurement)));
        })) {
            var evaluator = new ReportEvaluator();
            evaluator.evaluate(login, denominator, numerator, new Numerator[11],
                    List.of(Map.entry("FAKE-field", "FAKE-LOINC")), true);
            assertThat(construction.constructed()).hasSize(1);
            assertThat(evaluator.getReportResultList().getFirst()).containsEntry("FAKE-field", measurement);
        }
    }
}
