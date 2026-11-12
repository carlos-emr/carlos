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
package io.github.carlos_emr.carlos.appointment.reminder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5887: Add and Edit Appointment send the "box was shown" marker with the Email Reminder box, inside the same
 * clinic switch, and Edit shows the stored choice (finding 329: reopening Edit lost a ticked box).
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Email Reminder box markup on Add/Edit Appointment")
class AppointmentReminderBoxJspRegressionTest {

    private static final Path PAGES = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "appointment");

    private static String page(String name) throws IOException {
        // Comments do not count: markup left inside <%-- --%> would not render.
        return Files.readString(PAGES.resolve(name), StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
    }

    @Test
    void shouldSendTheMarkerWithTheBox_onAdd() throws IOException {
        assertThat(page("addappointment.jsp")).containsSubsequence(
                "pros.isPropertyActive(\"emailApptReminder\")",
                "name=\"emailPt\" id=\"emailPt\" value=\"email reminder\"",
                "AppointmentReminderChoice.isTicked(request)",
                "<input type=\"hidden\" name=\"emailPtShown\" value=\"1\">",
                "<% } %>");
    }

    @Test
    void shouldShowTheStoredChoiceAndSendTheMarker_onEdit() throws IOException {
        assertThat(page("editappointment.jsp")).containsSubsequence(
                "pros.isPropertyActive(\"emailApptReminder\")",
                "AppointmentReminderChoice.isOnForm(request)",
                "AppointmentReminderChoice.isRequested(apptFromRequest.getId())",
                "name=\"emailPt\" id=\"emailPt\" value=\"email reminder\" <%= emailReminderTicked ? \"checked\" : \"\" %>>",
                "<input type=\"hidden\" name=\"emailPtShown\" value=\"1\">",
                "<% } else { %>");
    }

    @Test
    void shouldSendTheMarker_onlyWithTheBox() throws IOException {
        for (String name : new String[]{"addappointment.jsp", "editappointment.jsp"}) {
            assertThat(page(name).split("name=\"emailPtShown\"", -1)).as(name).hasSize(2);
        }
    }
}
