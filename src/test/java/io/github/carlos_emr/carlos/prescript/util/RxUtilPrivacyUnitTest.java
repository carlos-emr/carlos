/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Prescription utility diagnostic privacy")
class RxUtilPrivacyUnitTest extends CarlosUnitTestBase {
    private static final String CLINICAL_TEXT = "Take one tablet BID for private-fixture-condition";

    @Test
    @DisplayName("should preserve trimmed instructions without logging them when debug is enabled")
    void shouldPreserveInstructionsPrivately_whenTrimmingPrescription() {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "000000", 0);
        rx.setSpecial(CLINICAL_TEXT + " Qty: 12 Repeats: 2");
        try (LogCapture logs = LogCapture.forLogger(RxUtil.class)) {
            LogManager.getLogger(RxUtil.class).debug("privacy capture control");
            assertThat(RxUtil.trimSpecial(rx)).isEqualTo(CLINICAL_TEXT);
            assertThat(logs.messages()).containsExactly("privacy capture control");
        }
    }

    @Test
    @DisplayName("should preserve parsed dosing without logging instructions when debug is enabled")
    void shouldParseInstructionsPrivately_whenParsingPrescription() {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "000000", 0);
        rx.setSpecial("Take one tablet BID");
        try (LogCapture logs = LogCapture.forLogger(RxUtil.class)) {
            LogManager.getLogger(RxUtil.class).debug("privacy capture control");
            RxUtil.instrucParser(rx);
            assertThat(rx.getFrequencyCode()).isEqualToIgnoringCase("BID");
            assertThat(logs.messages()).containsExactly("privacy capture control");
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("should keep staged drugs private when legacy diagnostic APIs are called")
    void shouldKeepStagedDrugsPrivate_whenLegacyDiagnosticsAreCalled() {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "000000", 0);
        rx.setSpecial(CLINICAL_TEXT);
        RxSessionBean bean = mock(RxSessionBean.class);
        when(bean.getStashSize()).thenReturn(1);
        when(bean.getStashItem(0)).thenReturn(rx);
        try (LogCapture logs = LogCapture.forLogger(RxUtil.class)) {
            LogManager.getLogger(RxUtil.class).debug("privacy capture control");
            RxUtil.p(CLINICAL_TEXT);
            RxUtil.p("instruction", CLINICAL_TEXT);
            RxUtil.printStashContent(bean);
            assertThat(logs.messages()).containsExactly("privacy capture control");
            assertThat(bean.getStashItem(0).getSpecial()).isEqualTo(CLINICAL_TEXT);
        }
    }
}
