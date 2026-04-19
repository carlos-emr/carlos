/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Appointment JSP Routing Unit Tests")
@Tag("unit")
@Tag("appointment")
class AppointmentJspRoutingTest {

    @Test
    void shouldUseExtensionlessRoutes_whenSubmittingAppointmentEditFlows() throws IOException {
        String editAppointment = read("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String addAppointment = read("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");
        String editRepeat = read("src/main/webapp/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp");
        String repeat = read("src/main/webapp/WEB-INF/jsp/appointment/appointmentrepeatbooking.jsp");
        String groupRecords = read("src/main/webapp/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp");

        assertThat(editAppointment).contains("/appointment/appointmentcontrol");
        assertThat(editAppointment).contains("/appointment/appointmenteditrepeatbooking");
        assertThat(editAppointment).doesNotContain("ACTION=\"appointmentcontrol.jsp\"");
        assertThat(editAppointment).doesNotContain("window.location='appointmentcontrol.jsp");
        assertThat(editAppointment).doesNotContain("action = \"appointmenteditrepeatbooking.jsp\"");

        assertThat(addAppointment).contains("/appointment/appointmentrepeatbooking");
        assertThat(addAppointment).doesNotContain("action = \"appointmentrepeatbooking.jsp\"");

        assertThat(editRepeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmenteditrepeatbooking\"");
        assertThat(editRepeat).doesNotContain("action=\"appointmenteditrepeatbooking.jsp\"");

        assertThat(repeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentrepeatbooking\"");
        assertThat(repeat).doesNotContain("action=\"appointmentrepeatbooking.jsp\"");

        assertThat(groupRecords).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentgrouprecords\"");
        assertThat(groupRecords).doesNotContain("action=\"appointmentgrouprecords.jsp\"");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
