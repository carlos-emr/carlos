/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.web;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class ProviderServiceReportCsvUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1)", "  =1+1", "\t=1+1", "\r=1+1"})
    void shouldPreserveFormulaLabelsAsText(String label) {
        assertEquals(org.apache.commons.text.StringEscapeUtils.escapeCsv("'" + label),
                ProviderServiceReportUIBean.csvLabel(label));
    }

    @Test
    void shouldPreserveOrdinaryLabelsAndCsvQuoting() {
        assertEquals("", ProviderServiceReportUIBean.csvLabel(null));
        assertEquals("", ProviderServiceReportUIBean.csvLabel(""));
        assertEquals("Clinic", ProviderServiceReportUIBean.csvLabel("Clinic"));
        assertEquals("\"Clinic, \"\"North\"\"\"", ProviderServiceReportUIBean.csvLabel("Clinic, \"North\""));
    }
}
