/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appt;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

@Tag("unit")
class AppointmentMailerHeaderUnitTest {
    @Test void shouldRetainColonsAndLegacyWhitespace_inTheCompleteHeaderValue() {
        var target = new SimpleMailMessage();
        AppointmentMailer.applyTemplateHeader(target,"From: test@example.invalid");
        AppointmentMailer.applyTemplateHeader(target,"Subject: Reminder: follow up");
        assertThat(target.getFrom()).isEqualTo(" test@example.invalid");
        assertThat(target.getSubject()).isEqualTo(" Reminder: follow up");
    }
    @Test void shouldRejectMissingHeaderValues_withAFixedDiagnostic_insteadOfAnArrayBoundsFailure() {
        for (String line : java.util.List.of("From","Subject","From:","Subject:")) {
            assertThatThrownBy(() -> AppointmentMailer.applyTemplateHeader(new SimpleMailMessage(),line))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid appointment reminder template header");
        }
    }
    @Test void shouldRetainTheExistingTreatmentOfUnrecognizedTemplateHeaders() {
        var target = new SimpleMailMessage(); target.setSubject("Unchanged");
        AppointmentMailer.applyTemplateHeader(target,"Other");
        AppointmentMailer.applyTemplateHeader(target,":");
        assertThat(target.getSubject()).isEqualTo("Unchanged");
    }
}
