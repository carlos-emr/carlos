/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import java.sql.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@org.junit.jupiter.api.parallel.Isolated("Temporarily changes the default locale")
class PartialDateExplanationUnitTest {
    @Test void shouldExplainExplicitPartialDates() {
        assertThat(PartialDateExplanation.explain(Date.valueOf("2019-01-01"), "YYYY"))
                .isEqualTo("Recorded year only: 2019. January 1 is a placeholder; the exact date is unknown.");
        assertThat(PartialDateExplanation.explain(Date.valueOf("2019-05-01"), "YYYY-MM"))
                .isEqualTo("Recorded year and month only: May 2019. The first day is a placeholder; the exact day is unknown.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2019-01-01", "2019-05-01", "2019-05-14", "2019-01-01 00:00", "2019-13", "2019-00", "<script>"})
    void shouldNeverInferPrecisionFromAnAnchorOrInvalidValue(String value) {
        assertThat(PartialDateExplanation.explain(value)).isEmpty();
    }

    @Test void shouldRequireExplicitMetadataAndKeepUnknownDatesUnknown() {
        assertThat(PartialDateExplanation.explain(Date.valueOf("2019-01-01"), null)).isEmpty();
        assertThat(PartialDateExplanation.explain(null, "YYYY")).isEmpty();
        assertThat(PartialDateExplanation.forSave("2019-01-01", Date.valueOf("2019-01-01"), null, false))
                .isEqualTo("2019-01-01");
        assertThat(PartialDateExplanation.forSave("", Date.valueOf("2019-01-01"), "YYYY", false)).isEmpty();
    }

    @Test void shouldRetainUnchangedAnchorsUntilExplicitlyConfirmed() {
        assertThat(PartialDateExplanation.forSave("2019-01-01", Date.valueOf("2019-01-01"), "YYYY", false)).isEqualTo("2019");
        Date original = Date.valueOf("2019-05-01");
        assertThat(PartialDateExplanation.forSave("2019-05-01", original, "YYYY-MM", false)).isEqualTo("2019-05");
        assertThat(PartialDateExplanation.forSave("2019-05-01", original, "YYYY-MM", true)).isEqualTo("2019-05-01");
        assertThat(PartialDateExplanation.forSave("2019-05-14", original, "YYYY-MM", false)).isEqualTo("2019-05-14");
        assertThat(PartialDateExplanation.forSave(null, original, "YYYY-MM", false)).isNull();
    }

    @Test void shouldNormalizePartialDisplayValuesForNativeDatePickers() {
        assertThat(PartialDateExplanation.pickerValue("2019")).isEqualTo("2019-01-01");
        assertThat(PartialDateExplanation.pickerValue("2019-05")).isEqualTo("2019-05-01");
        assertThat(PartialDateExplanation.pickerValue("2019-01-01")).isEqualTo("2019-01-01");
        assertThat(PartialDateExplanation.pickerValue(null)).isNull();
    }

    @Test void shouldKeepIsoDatesStableUnderNonGregorianDefaultLocales() {
        Date original = Date.valueOf("2019-01-01");
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("th-TH"));
            assertThat(PartialDateExplanation.recordedValue(original, "YYYY")).isEqualTo("2019");
            assertThat(PartialDateExplanation.forSave("2019-01-01", original, "YYYY", false)).isEqualTo("2019");
            assertThat(PartialDateExplanation.explain("2019-05", java.util.Locale.FRENCH)).contains("mai 2019", "jour exact est inconnu");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
