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
        Date original = Date.valueOf("2019-05-01");
        assertThat(PartialDateExplanation.forSave("2019-05-01", original, "YYYY-MM", false)).isEqualTo("2019-05");
        assertThat(PartialDateExplanation.forSave("2019-05-01", original, "YYYY-MM", true)).isEqualTo("2019-05-01");
        assertThat(PartialDateExplanation.forSave("2019-05-14", original, "YYYY-MM", false)).isEqualTo("2019-05-14");
        assertThat(PartialDateExplanation.forSave(null, original, "YYYY-MM", false)).isNull();
    }
}
