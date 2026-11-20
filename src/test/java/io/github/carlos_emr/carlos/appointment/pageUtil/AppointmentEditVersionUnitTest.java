/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.util.Date;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppointmentEditVersionUnitTest {
    @Test
    void statusChangesAreDetectedEvenWhenTheLegacyWriterLeavesTheTimestampUntouched() {
        Appointment appointment = new Appointment();
        appointment.setId(42);
        appointment.setStatus("t");
        appointment.setUpdateDateTime(new Date(1000));
        String original = AppointmentEditVersion.of(appointment, "");
        appointment.setStatus("C");
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(original);
        appointment.setStatus("t");
        assertThat(AppointmentEditVersion.of(appointment, "")).isEqualTo(original);
        appointment.setUpdateDateTime(new java.sql.Timestamp(1000));
        assertThat(AppointmentEditVersion.of(appointment, "")).isEqualTo(original);
    }

    @Test
    void shouldChangeVersion_whenOnlyLocationCodeChanges() {
        // #3982: a form opened before another user picked a different Location List item is stale,
        // even when both items have the same label.
        Appointment appointment = new Appointment();
        appointment.setLocation("Room 1");
        appointment.setLocationCode(11);
        String original = AppointmentEditVersion.of(appointment, "");

        appointment.setLocationCode(12);
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(original);
        appointment.setLocationCode(null);
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(original);
        appointment.setLocationCode(11);
        assertThat(AppointmentEditVersion.of(appointment, "")).isEqualTo(original);
    }

    @Test
    void fieldBoundariesNullsIdentityAndAppointmentMetadataCannotCollide() {
        Appointment appointment = new Appointment();
        appointment.setId(42);
        appointment.setName("ab");
        appointment.setReason("c");
        String original = AppointmentEditVersion.of(appointment, "");
        appointment.setName("a");
        appointment.setReason("bc");
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(original);
        appointment.setName("ab");
        appointment.setReason("c");
        assertThat(AppointmentEditVersion.of(appointment, "changed metadata")).isNotEqualTo(original);
        assertThat(AppointmentEditVersion.of(appointment, null)).isNotEqualTo(original);
        appointment.setId(43);
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(original);
        appointment.setId(42);
        appointment.setNotes(null);
        String absentNotes = AppointmentEditVersion.of(appointment, "");
        appointment.setNotes("");
        assertThat(AppointmentEditVersion.of(appointment, "")).isNotEqualTo(absentNotes);
    }
}
