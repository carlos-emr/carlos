/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The structured dose (take 1-2) is shown on the Rx record, the Rx page's staging card, the BC
 * health passport forms and the VT form. It was rounded to three decimal places on the way, so a
 * stored 0.0625 read "0.062"; the Rx page then posted that text back as the dose (#6185).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Structured dose display")
class RxPrescriptionDataDoseDisplayUnitTest extends CarlosUnitTestBase {

    private static RxPrescriptionData.Prescription prescription(float takeMin, float takeMax) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData().newPrescription("999998", 817263);
        rx.setBrandName("FAKE BRAND");
        rx.setTakeMin(takeMin);
        rx.setTakeMax(takeMax);
        rx.setFrequencyCode("BID");
        return rx;
    }

    @ParameterizedTest(name = "take {0}-{1} -> {2}")
    @CsvSource({
        "1, 1, 1",
        "0.5, 0.5, 0.5",
        "0.0625, 0.0625, 0.0625",
        "500, 500, 500",
        "1.0E7, 1.0E7, 10000000",
        "1, 2, 1-2",
        "0.5, 1, 0.5-1",
        "0.0625, 0.125, 0.0625-0.125"
    })
    @DisplayName("should show the stored dose in full on a prescription")
    void shouldShowStoredDose_forPrescription(float takeMin, float takeMax, String expected) {
        RxPrescriptionData.Prescription rx = prescription(takeMin, takeMax);

        assertThat(rx.getDosageDisplay()).isEqualTo(expected);
        assertThat(rx.getFullFrequency()).isEqualTo(expected + " BID");
        assertThat(rx.getRxDisplay()).startsWith("FAKE BRAND " + expected + " BID");
    }

    @Test
    @DisplayName("should give the Rx page the text that reads back as the stored dose")
    void shouldGiveTextThatReadsBack_forMinimumAndMaximumFields() {
        RxPrescriptionData.Prescription rx = prescription(0.0625f, 0.3125f);

        // The staging card renders these two strings and posts them back on blur.
        assertThat(rx.getTakeMinString()).isEqualTo("0.0625");
        assertThat(rx.getTakeMaxString()).isEqualTo("0.3125");
        assertThat(Float.parseFloat(rx.getTakeMinString())).isEqualTo(rx.getTakeMin());
        assertThat(Float.parseFloat(rx.getTakeMaxString())).isEqualTo(rx.getTakeMax());
    }

    @ParameterizedTest(name = "take {0}-{1} -> {2}")
    @CsvSource({
        "1, 1, 1",
        "0.0625, 0.0625, 0.0625",
        "1.0E7, 1.0E7, 10000000",
        "1, 2, 1-2",
        "0.0625, 0.125, 0.0625-0.125"
    })
    @DisplayName("should show the stored dose in full on the Rx record")
    void shouldShowStoredDose_forDrugRecord(float takeMin, float takeMax, String expected) {
        Drug drug = new Drug();
        drug.setTakeMin(takeMin);
        drug.setTakeMax(takeMax);

        assertThat(drug.getDosageDisplay()).isEqualTo(expected);
    }
}
