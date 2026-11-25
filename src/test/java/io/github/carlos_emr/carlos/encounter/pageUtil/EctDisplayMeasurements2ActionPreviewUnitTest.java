/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.apache.struts2.ServletActionContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FlowsheetDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The eChart Measurements box preview of a reading, including long free-text values such as
 * OCR'd ECG and echocardiogram reports.
 */
@DisplayName("EctDisplayMeasurements2Action preview")
@Tag("unit")
@Tag("encounter")
@Tag("measurement")
class EctDisplayMeasurements2ActionPreviewUnitTest extends CarlosUnitTestBase {

    private static final String ECG_REPORT = "FAKE sinus rhythm, rate 72 bpm.\nPR 160 ms, QRS 90 ms.\n"
            + "No acute ST changes. " + "Interpretation continues. ".repeat(15);

    @Test
    @DisplayName("should show a short value whole")
    void shouldShowShortValueWhole_forNumericReading() {
        assertThat(EctDisplayMeasurements2Action.preview("128/82")).isEqualTo("128/82");
        assertThat(EctDisplayMeasurements2Action.preview("Sinus rhythm")).isEqualTo("Sinus rhythm");
    }

    @Test
    @DisplayName("should cut a long text value to the preview length with an ellipsis")
    void shouldCutLongTextValue_toPreviewLength() {
        assertThat(EctDisplayMeasurements2Action.preview(ECG_REPORT)).isEqualTo("FAKE sinus...");
    }

    @Test
    @DisplayName("should flatten line breaks and repeated spaces into single spaces")
    void shouldFlattenLineBreaks_inMultiLineValue() {
        assertThat(EctDisplayMeasurements2Action.preview("  NSR\n\n  72  ")).isEqualTo("NSR 72");
    }

    @Test
    @DisplayName("should preview a missing value as empty")
    void shouldPreviewEmpty_forNullValue() {
        assertThat(EctDisplayMeasurements2Action.preview(null)).isEmpty();
    }

    @Test
    @DisplayName("should keep the type name when sized against the preview of a long value")
    void shouldKeepTypeName_whenValueIsLongText() {
        createAndRegisterMock(SecurityInfoManager.class);
        registerMock(FlowsheetDao.class, mock(FlowsheetDao.class));
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
            context.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EctDisplayMeasurements2Action action = new EctDisplayMeasurements2Action();

            // Sized against the whole report, the name used to be replaced by ".".
            assertThat(action.padd("ECG Report", ECG_REPORT)).isEqualTo(".");
            assertThat(action.padd("ECG Report", EctDisplayMeasurements2Action.preview(ECG_REPORT)))
                    .isEqualTo("ECG Report");
        }
    }
}
