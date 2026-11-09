/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("report")
class ReportEvaluatorUnitTest extends CarlosUnitTestBase {
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
