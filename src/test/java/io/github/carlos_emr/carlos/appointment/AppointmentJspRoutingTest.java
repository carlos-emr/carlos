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
    void shouldUseExtensionlessRoutes_forAllAppointmentWorkflows() throws IOException {
        String editAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp");
        String addAppointment = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/addappointment.jsp");
        String editRepeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmenteditrepeatbooking.jsp");
        String repeat = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentrepeatbooking.jsp");
        String groupRecords = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp");
        String addRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentaddarecord.jsp");
        String updateRecord = readJspContent("src/main/webapp/WEB-INF/jsp/appointment/appointmentupdatearecord.jsp");
        String providerDay = readJspContent("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp");

        assertThat(editAppointment).contains("/appointment/appointmentcontrol");
        assertThat(editAppointment).contains("/appointment/appointmenteditrepeatbooking");
        assertThat(editAppointment).doesNotContain("ACTION=\"appointmentcontrol.jsp\"");
        assertThat(editAppointment).doesNotContain("window.location='appointmentcontrol.jsp");
        assertThat(editAppointment).doesNotContain("appointmenteditrepeatbooking.jsp");

        assertThat(addAppointment).contains("/appointment/appointmentrepeatbooking");
        assertThat(addAppointment).doesNotContain("appointmentrepeatbooking.jsp");

        assertThat(editRepeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmenteditrepeatbooking\"");
        assertThat(editRepeat).doesNotContain("action=\"appointmenteditrepeatbooking.jsp\"");

        assertThat(repeat).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentrepeatbooking\"");
        assertThat(repeat).doesNotContain("action=\"appointmentrepeatbooking.jsp\"");

        assertThat(groupRecords).contains("action=\"<%=request.getContextPath() %>/appointment/appointmentgrouprecords\"");
        assertThat(groupRecords).doesNotContain("action=\"appointmentgrouprecords.jsp\"");

        assertThat(addRecord).contains("/appointment/printappointment?appointment_no=");
        assertThat(addRecord).contains("pageContext.request.contextPath");
        assertThat(addRecord).contains("carlos:forJavaScript(carlos:forUriComponent(apptId))");
        assertThat(addRecord).doesNotContain("printappointment.jsp?appointment_no=");

        assertThat(updateRecord).contains("/appointment/printappointment?appointment_no=");
        assertThat(updateRecord).containsAnyOf("request.getContextPath()", "pageContext.request.contextPath");
        assertThat(updateRecord).contains("pageContext.request.contextPath");
        assertThat(updateRecord).contains("carlos:forJavaScript(carlos:forUriComponent(appointmentNo))");
        assertThat(updateRecord).doesNotContain("printappointment.jsp?appointment_no=");

        assertThat(providerDay).contains("/appointment/addappointment?");
        assertThat(providerDay).contains("return ctx + '/appointment/addappointment'");
        assertThat(providerDay).doesNotContain("/appointment/addappointment.jsp");
    }

    private String readJspContent(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
