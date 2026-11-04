/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;

/**
 * The SOAP appointment objects carry the Location List code out (#3982) but never take one in: a
 * client sends location text, and a changed text clears a code that no longer names it.
 */
@DisplayName("Appointment SOAP transfer Location List code")
@Tag("unit")
@Tag("appointment")
class AppointmentTransferLocationUnitTest {

    private static Appointment booked() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentDate(new Date());
        appointment.setStartTime(new Date());
        appointment.setEndTime(new Date());
        appointment.setCreateDateTime(new Date());
        appointment.setUpdateDateTime(new Date());
        appointment.setLocation("Virtual");
        appointment.setLocationCode(11);
        return appointment;
    }

    @Test
    void shouldExposeLocationCode_whenConvertingAppointment() {
        AppointmentTransfer transfer = AppointmentTransfer.toTransfer(booked(), false);

        assertThat(transfer.getLocationCode()).isEqualTo(11);
        assertThat(transfer.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldExposeLocationCode_whenConvertingArchive() {
        AppointmentArchive archive = new AppointmentArchive();
        archive.setAppointmentDate(new Date());
        archive.setStartTime(new Date());
        archive.setEndTime(new Date());
        archive.setCreateDateTime(new Date());
        archive.setUpdateDateTime(new Date());
        archive.setLocationCode(12);

        assertThat(AppointmentArchiveTransfer.toTransfer(archive, false).getLocationCode()).isEqualTo(12);
    }

    @Test
    void shouldIgnoreClientCode_whenCopyingIntoAppointment() {
        Appointment target = booked();
        AppointmentTransfer transfer = new AppointmentTransfer();
        transfer.setLocation("Virtual");
        transfer.setLocationCode(999);

        transfer.copyTo(target);

        assertThat(target.getLocationCode()).isEqualTo(11);
        assertThat(target.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldClearCode_whenClientChangesLocationText() {
        Appointment target = booked();
        AppointmentTransfer transfer = new AppointmentTransfer();
        transfer.setLocation("Room from SOAP");

        transfer.copyTo(target);

        assertThat(target.getLocationCode()).isNull();
        assertThat(target.getLocation()).isEqualTo("Room from SOAP");
    }
}
