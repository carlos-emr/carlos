/*
 * Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression for #4543: accepted prescription directions must never become diagnostic log text. */
@Tag("unit")
@Tag("prescription")
@DisplayName("Prescription form instruction privacy")
class RxWriteScriptFormLogPrivacyUnitTest {
    @ParameterizedTest(name = "accepted instruction value {index} remains unchanged and is not logged")
    @NullAndEmptySource
    @ValueSource(strings = {"1 od", "prn", " 1 od ", "1 od\r\n", "Take one synthetic tablet daily.",
            "Synthetic directions line one.\nSynthetic directions line two."})
    void shouldPreserveAcceptedInstructions_withoutLoggingTheirContentOrAnError(String instructions) {
        var form = new RxWriteScriptForm();
        try (LogCapture capture = LogCapture.forLogger(RxWriteScriptForm.class)) {
            form.setSpecial(instructions);

            assertThat(form.getSpecial()).isEqualTo(instructions);
            assertThat(capture.events()).isEmpty();
        }
    }
}
