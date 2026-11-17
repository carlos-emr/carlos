/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("appointment")
class AppointmentTextUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    void shouldPreserveFullTextareaContents_whenBrowserSubmitsLineBreaks(String newline) {
        Appointment appointment = new Appointment();
        String reason = "r".repeat(78) + newline + "Z";
        String notes = "n".repeat(253) + newline + "Z";

        appointment.setReason(reason);
        appointment.setNotes(notes);

        assertThat(appointment.getReason()).isEqualTo("r".repeat(78) + "\nZ").hasSize(80);
        assertThat(appointment.getNotes()).isEqualTo("n".repeat(253) + "\nZ").hasSize(255);
    }

    @Test
    void shouldPreserveUnicodeWhitespaceAndEmptyValues_whenNormalizingText() {
        Appointment appointment = new Appointment();
        String text = "  O'Brien é 中文 🩺 &amp; \\\t\r\n\rLast\n ";
        appointment.setReason(text);
        appointment.setNotes(text);
        assertThat(appointment.getReason()).isEqualTo("  O'Brien é 中文 🩺 &amp; \\\t\n\nLast\n ");
        assertThat(appointment.getNotes()).isEqualTo(appointment.getReason());
        appointment.setReason(appointment.getReason());
        assertThat(appointment.getReason()).isEqualTo(appointment.getNotes());
        appointment.setReason(null);
        appointment.setNotes("");
        assertThat(appointment.getReason()).isNull();
        assertThat(appointment.getNotes()).isEmpty();
    }
}
