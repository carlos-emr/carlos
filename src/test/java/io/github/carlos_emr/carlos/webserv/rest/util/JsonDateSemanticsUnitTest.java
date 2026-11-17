/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("rest")
@ResourceLock("java.util.TimeZone")
class JsonDateSemanticsUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Toronto", "America/Vancouver", "Pacific/Apia"})
    void shouldPreserveDeclaredDateAndClockKinds_onEpochDaysAndAcrossServerZones(String zone) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var calendar = new GregorianCalendar(); calendar.clear(); calendar.set(1970, Calendar.JANUARY, 1, 13, 17, 23);
            var value = calendar.getTime();
            var patient = new DemographicTo1();
            for (String property : List.of("PatientStatusDate", "DateJoined", "HcRenewDate", "EndDate", "EffDate", "RosterDate", "RosterTerminationDate")) {
                DemographicTo1.class.getMethod("set" + property, java.util.Date.class).invoke(patient, value);
            }
            patient.setLastUpdateDate(value);
            var oauth = ProductionRestObjectMappers.oauthSurface().valueToTree(patient);
            var session = ProductionRestObjectMappers.sessionSurface().valueToTree(patient);
            for (String name : List.of("patientStatusDate", "dateJoined", "hcRenewDate", "endDate", "effDate", "rosterDate", "rosterTerminationDate")) {
                assertThat(oauth.get(name).asText()).as(zone + ":" + name).isEqualTo("1970-01-01");
                assertThat(session.get(name).isIntegralNumber()).as(zone + ":" + name).isTrue();
                assertThat(session.get(name).longValue()).isEqualTo(value.getTime());
            }
            assertThat(oauth.get("lastUpdateDate").longValue()).isEqualTo(value.getTime());
            assertThat(oauth.get("lastUpdateDate").isIntegralNumber()).isTrue();
            var appointment = new AppointmentTo1(); appointment.setStartTime(value); appointment.setEndTime(value);
            var clock = ProductionRestObjectMappers.oauthSurface().valueToTree(appointment);
            assertThat(clock.get("startTime").asText()).isEqualTo("13:17:23");
            assertThat(clock.get("endTime").asText()).isEqualTo("13:17:23");
        } finally { TimeZone.setDefault(previous); }
    }
}
