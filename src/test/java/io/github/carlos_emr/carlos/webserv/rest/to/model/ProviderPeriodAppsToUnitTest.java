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

import java.util.Date;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("rest")
@Tag("regression")
class ProviderPeriodAppsToUnitTest {

    @Test
    void shouldIncludeOnlySafeIdentifiers_whenToStringIsCalled() {
        ProviderPeriodAppsTo appointment = new ProviderPeriodAppsTo();
        appointment.setAppointmentNo(12345);
        appointment.setProviderNo("provider-abc");
        appointment.setAppointmentDate(new Date(1_718_000_000_000L));
        appointment.setDemographicNo(67890);
        appointment.setNotes("clinical note: chest pain and referral reason");
        appointment.setLocation("exam room 7");
        appointment.setResources("ultrasound room");
        appointment.setStatus("A");
        appointment.setLastUpdateUser("scheduler-user");
        appointment.setUpdateDatetime(1_718_000_000L);
        appointment.setName("Jane Patient");

        String result = appointment.toString();

        assertThat(result).isEqualTo("ProviderPeriodAppsTo[appointmentNo=12345, providerNo=provider-abc]");
        assertThat(result)
                .doesNotContain("clinical note")
                .doesNotContain("chest pain")
                .doesNotContain("Jane Patient")
                .doesNotContain("67890")
                .doesNotContain("exam room 7")
                .doesNotContain("ultrasound room")
                .doesNotContain("scheduler-user")
                .doesNotContain("updateDatetime=");
    }

    @Test
    void shouldHandleNullIdentifiers_whenToStringIsCalled() {
        ProviderPeriodAppsTo appointment = new ProviderPeriodAppsTo();

        assertThat(appointment.toString()).isEqualTo("ProviderPeriodAppsTo[appointmentNo=null, providerNo=null]");
    }
}
