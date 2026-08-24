/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests pinning the PHI-safe {@link AppointmentExtTo#toString()}
 * contract so patient-identifying fields cannot leak into application logs.
 *
 * @since 2026-06-23
 */
@DisplayName("AppointmentExtTo PHI-safe toString tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class AppointmentExtToUnitTest {

    private AppointmentExtTo appointmentWithPatientDetails() {
        AppointmentExtTo to = new AppointmentExtTo();
        to.setAppointmentNo(12345);
        to.setLastName("Tremblay");
        to.setFirstName("Genevieve");
        to.setPhone("604-555-0147");
        to.setPhone2("604-555-0199");
        to.setEmail("genevieve.tremblay@example.com");
        to.setDemoCell("604-555-0123");
        to.setNotes("Follow-up regarding lab results");
        return to;
    }

    @Test
    @DisplayName("should exclude patient name, phone, and email from toString")
    void shouldExcludePatientDetails_fromToString() {
        String result = appointmentWithPatientDetails().toString();

        assertThat(result)
                .doesNotContain("Tremblay")
                .doesNotContain("Genevieve")
                .doesNotContain("604-555-0147")
                .doesNotContain("604-555-0199")
                .doesNotContain("genevieve.tremblay@example.com")
                .doesNotContain("604-555-0123")
                .doesNotContain("Follow-up regarding lab results");
    }

    @Test
    @DisplayName("should include only appointment number in toString")
    void shouldIncludeAppointmentNumber_inToString() {
        String result = appointmentWithPatientDetails().toString();

        assertThat(result).isEqualTo("AppointmentExtTo[appointmentNo=12345]");
    }

    @Test
    @DisplayName("should render null appointment number without throwing")
    void shouldRenderNullAppointmentNumber_withoutThrowing() {
        String result = new AppointmentExtTo().toString();

        assertThat(result).isEqualTo("AppointmentExtTo[appointmentNo=null]");
    }
}
