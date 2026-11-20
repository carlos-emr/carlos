/**
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for issue #4589: appointment writers that are hard to drive from a unit test
 * (the STAR HL7 handler and the chart's JSON sign path) must archive the appointment into
 * {@code appointmentArchive} <em>before</em> they change it, because the archive holds the
 * versions an appointment replaced. The check is a source-order assertion within each writer's
 * method body; the Ontario bill save is covered behaviourally in
 * {@code BillingOnLookupServiceUnitTest} and in the Playwright script
 * {@code echart-note-verify-appointment-status-billing}.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Appointment archive is written before status writers change the row (#4589)")
class AppointmentArchiveBeforeStatusWriteRegressionTest {

    private static final String MAIN = "src/main/java/io/github/carlos_emr/carlos/";

    private static String body(String file, String methodSignature, String nextMarker) throws IOException {
        String src = Files.readString(Path.of(MAIN + file));
        int start = src.indexOf(methodSignature);
        assertThat(start).as("method %s present in %s", methodSignature, file).isGreaterThanOrEqualTo(0);
        int end = src.indexOf(nextMarker, start + methodSignature.length());
        assertThat(end).as("end marker %s after %s", nextMarker, methodSignature).isGreaterThan(start);
        return src.substring(start, end);
    }

    private static void assertArchivedBefore(String body, String change) {
        int archive = body.indexOf("archiveAppointment(");
        int mutation = body.indexOf(change);
        assertThat(archive).as("archiveAppointment call").isGreaterThanOrEqualTo(0);
        assertThat(mutation).as("mutation %s", change).isGreaterThanOrEqualTo(0);
        assertThat(archive).as("archive must precede %s", change).isLessThan(mutation);
    }

    @Test
    void shouldArchiveAppointment_beforeStarStatusUpdate() throws IOException {
        String body = body("integration/hl7/handlers/PhsStarHandler.java",
                "public void updateAppointmentStatus(", "public void updateAppointmentAccountNumber(");
        assertArchivedBefore(body, "appt.setStatus(");
    }

    @Test
    void shouldArchiveAppointment_beforeStarReschedule() throws IOException {
        String body = body("integration/hl7/handlers/PhsStarHandler.java",
                "public void rescheduleAppointment(", "public void updateAppointmentStatus(");
        assertArchivedBefore(body, "appt.setProviderNo(");
        // The program mapping can throw HL7Exception; it must be validated before the archive row is
        // written, or a rejected message leaves a history version that was never replaced.
        assertThat(body.indexOf("findProgram2()")).as("program validation").isGreaterThanOrEqualTo(0);
        assertThat(body.indexOf("findProgram2()")).as("program validated before archiving")
                .isLessThan(body.indexOf("archiveAppointment("));
    }

    @Test
    void shouldArchiveAppointment_beforeChartSignAndExitStatusChange() throws IOException {
        String body = body("casemgmt/web/CaseManagementEntry2Action.java",
                "new ApptStatusData()", "appointmentDao.merge(appointment)");
        assertArchivedBefore(body, "appointment.setStatus(");
    }
}
