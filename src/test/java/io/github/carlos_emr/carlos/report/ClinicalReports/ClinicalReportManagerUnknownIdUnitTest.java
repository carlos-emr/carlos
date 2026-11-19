/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Looking up a numerator or denominator by an id that ClinicalReports.xml does not have returns nothing,
 * rather than failing, so the report page can say the report is not in the list.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Clinical report numerators and denominators by id")
class ClinicalReportManagerUnknownIdUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should return nothing for an id that is not in the shipped reports")
    void shouldReturnNothing_forAnIdNotInTheReports() {
        assertThat(ClinicalReportManager.getInstance().getNumeratorById("FAKE-no-such-numerator")).isNull();
    }

    @Test
    @DisplayName("should return no denominator for an unknown, empty or missing id")
    void shouldReturnNoDenominator_forAnUnknownOrMissingId() {
        var reports = ClinicalReportManager.getInstance();

        assertThat(reports.getDenominatorById("FAKE-no-such-denominator")).isNull();
        assertThat(reports.getDenominatorById("")).isNull();
        assertThat(reports.getDenominatorById(null)).isNull();
    }

    @Test
    @DisplayName("should still return a numerator and a denominator the shipped reports list")
    void shouldReturnTheReport_forAListedId() {
        var reports = ClinicalReportManager.getInstance();
        String numerator = reports.getNumeratorList().getFirst().getId();
        String denominator = reports.getDenominatorList().getFirst().getId();

        assertThat(reports.getNumeratorById(numerator)).isNotNull();
        assertThat(reports.getDenominatorById(denominator)).isNotNull();
    }
}
