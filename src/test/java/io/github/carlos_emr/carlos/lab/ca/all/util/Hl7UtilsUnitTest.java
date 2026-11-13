/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

/** Reading an HL7 field never logs the failure's message, which can quote the lab message. */
@Tag("unit")
class Hl7UtilsUnitTest {

    private static final String FIELD_TEXT = "FAKE-LAB-VALUE-4711";

    @Test
    void shouldLogOnlyTheExceptionClass_whenAnHl7AccessFails() {
        try (LogCapture logs = LogCapture.forLogger(Hl7Utils.class)) {
            String value = Hl7Utils.safeHl7String(() -> {
                throw new HL7Exception("Can't read field " + FIELD_TEXT);
            });

            assertThat(value).isEmpty();
            assertThat(logs.messages()).singleElement().satisfies(message -> {
                assertThat(message).contains("HL7Exception");
                assertThat(message).doesNotContain(FIELD_TEXT);
            });
        }
    }

    @Test
    void shouldLogOnlyTheExceptionClass_whenAnUnexpectedFailureQuotesTheField() {
        try (LogCapture logs = LogCapture.forLogger(Hl7Utils.class)) {
            String value = Hl7Utils.safeHl7String(() -> {
                throw new NumberFormatException("For input string: \\"" + FIELD_TEXT + "\\"");
            });

            assertThat(value).isEmpty();
            assertThat(logs.messages()).singleElement().satisfies(message -> {
                assertThat(message).contains("NumberFormatException");
                assertThat(message).doesNotContain(FIELD_TEXT);
            });
        }
    }

    @Test
    void shouldTrimTheValueAndLogNothing_whenTheFieldReads() {
        try (LogCapture logs = LogCapture.forLogger(Hl7Utils.class)) {
            assertThat(Hl7Utils.safeHl7String(() -> "  FAKE value ")).isEqualTo("FAKE value");
            assertThat(Hl7Utils.safeHl7String(() -> null)).isEmpty();
            assertThat(logs.events()).isEmpty();
        }
    }
}
