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
    void shouldShowTheReminderStatusEncoded_underTheBoxOnEdit() throws IOException {
        assertThat(page("editappointment.jsp")).containsSubsequence(
                "<input type=\"hidden\" name=\"emailPtShown\" value=\"1\">",
                "AppointmentReminderChoice.statusLine(loggedInInfo,",
                "apptFromRequest, (java.util.Locale) pageContext.getAttribute(\"appointmentLocale\"));",
                "<fmt:message key=\"${emailReminderStatusKey}\" var=\"emailReminderStatusText\"><fmt:param value=\"${emailReminderStatusArgument}\"/></fmt:message>",
                "<carlos:encode value=\"${emailReminderStatusText}\"/>",
                "<% } else { %>");
    }

    /** Manage Emails labels the reminder job's emails and can list them on their own. */
    @Test
    void shouldLabelAndFilterAppointmentReminders_onManageEmails() throws IOException {
        Path admin = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "admin");
        String filter = Files.readString(admin.resolve("manageEmails.jsp"), StandardCharsets.UTF_8);
        String card = Files.readString(admin.resolve("emailStatusResults.jspf"), StandardCharsets.UTF_8);

        assertThat(filter).containsSubsequence("<select class=\"form-select\" name=\"emailType\" id=\"emailType\">",
                "<option value=\"-1\">", "<option value=\"${ emailReminderType }\">",
                "admin.manageEmails.type.appointmentReminder", "</select>");
        assertThat(card).containsSubsequence("<c:if test=\"${emailStatusResult.appointmentReminder}\">", "<tr>",
                "admin.manageEmails.type.appointmentReminder", "</tr>", "</c:if>");
    }

    @Test
    void shouldSendTheMarker_onlyWithTheBox() throws IOException {
        for (String name : new String[]{"addappointment.jsp", "editappointment.jsp"}) {
            assertThat(page(name).split("name=\"emailPtShown\"", -1)).as(name).hasSize(2);
            assertThat(page(name).split("name=\"smsPtShown\"", -1)).as(name).hasSize(2);
        }
    }

    /**
     * #884: the Text Reminder box follows the email block, after it closes, on the switch the reminder job reads
     * for texts; so a clinic can turn on texts without email.
     */
    @Test
    void shouldSendTheTextMarkerWithTheTextBox_onAdd() throws IOException {
        assertThat(page("addappointment.jsp")).containsSubsequence(
                "pros.isPropertyActive(\"emailApptReminder\")",
                "<input type=\"hidden\" name=\"emailPtShown\" value=\"1\">",
                "<% } %>",
                "pros.isPropertyActive(io.github.carlos_emr.carlos.appointment.reminder.SmsReminderChannel.ENABLED_PROPERTY)",
                "<fmt:message key=\"Appointment.formTextReminder\"/>",
                "name=\"smsPt\" id=\"smsPt\" value=\"text reminder\"",
                "AppointmentReminderChoice.isTicked(request,",
                "ReminderChannel.SMS) ? \"checked\" : \"\" %>>",
                "<input type=\"hidden\" name=\"smsPtShown\" value=\"1\">",
                "<% } %>");
    }

    @Test
    void shouldShowTheStoredTextChoiceAndItsStatus_onEdit() throws IOException {
        assertThat(page("editappointment.jsp")).containsSubsequence(
                "pros.isPropertyActive(\"emailApptReminder\")",
                "<% } else { %>",
                "<% }%>",
                "pros.isPropertyActive(io.github.carlos_emr.carlos.appointment.reminder.SmsReminderChannel.ENABLED_PROPERTY)",
                "AppointmentReminderChoice.isOnForm(request,",
                "ReminderChannel.SMS)",
                "AppointmentReminderChoice.isRequested(apptFromRequest.getId(),",
                "ReminderChannel.SMS); %>",
                "<fmt:message key=\"Appointment.formTextReminder\"/>",
                "name=\"smsPt\" id=\"smsPt\" value=\"text reminder\" <%= smsReminderTicked ? \"checked\" : \"\" %>>",
                "<input type=\"hidden\" name=\"smsPtShown\" value=\"1\">",
                "AppointmentReminderChoice.statusLine(loggedInInfo,",
                "apptFromRequest, io.github.carlos_emr.carlos.appointment.reminder.ReminderChannel.SMS,",
                "<fmt:message key=\"${smsReminderStatusKey}\" var=\"smsReminderStatusText\"><fmt:param value=\"${smsReminderStatusArgument}\"/></fmt:message>",
                "<carlos:encode value=\"${smsReminderStatusText}\"/>",
                "<% } %>");
    }

    /**
     * Group Appt and Group Update post the Add/Edit form here (every field copied forward, the box included).
     * The page records the box after booking the group, and Group Update moves each reminder to the appointment
     * it books in place of the old one before applying the box to the edited appointment.
     */
    @Test
    void shouldKeepTheReminderChoice_onGroupAddAndGroupUpdate() throws IOException {
        String group = page("appointmentgrouprecords.jsp");

        assertThat(group).containsSubsequence("equals(\"Add Group Appointment\")", "appointmentDao.persist(a);",
                "groupBookings.add(a);", "AppointmentReminderChoice.recordForGroup(request,", "groupBookings, curProvider_no);",
                "if (rowsAffected == 1) bSucc = true;");
        assertThat(group).containsSubsequence("equals(\"Group Update\")", "replacedNo = appt.getId();",
                "appointmentDao.persist(a);", "AppointmentReminderChoice.moveReminders(request, groupUser, replacedNo, a);",
                "AppointmentReminderChoice.recordAfterGroupUpdate(request, groupUser, edited);");
        // Each booking there is already saved: the reminder calls never stop the page (no transaction around it).
        assertThat(group).doesNotContain("AppointmentReminderChoice.record(request");
    }

    /**
     * When a reminder call there failed, the result says the appointment was saved but its reminder was not, and
     * the window stays open to read it; otherwise it closes as before.
     */
    @Test
    void shouldWarnThatTheReminderWasNotRecorded_onTheGroupPagesResult() throws IOException {
        String group = page("appointmentgrouprecords.jsp");

        assertThat(group).containsSubsequence(
                "if (bSucc && AppointmentReminderChoice.notRecorded(request)) {",
                "appointment.appointmentgrouprecords.msgAddSuccess",
                "<p role=\"alert\" id=\"reminderNotRecorded\"><fmt:message key=\"appointment.reminder.notRecorded\"/></p>",
                "onclick=\"self.close()\"",
                "self.opener.refresh();",
                "} else if (bSucc) {",
                "self.opener.refresh();",
                "self.close();");
        String warned = group.substring(group.indexOf("if (bSucc && AppointmentReminderChoice.notRecorded(request))"),
                group.indexOf("} else if (bSucc) {"));
        assertThat(warned).as("the warning is not closed away").doesNotContain("self.close();\n");
    }
}
