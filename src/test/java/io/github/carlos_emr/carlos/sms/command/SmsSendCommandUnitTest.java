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
package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** #884: the appointment reminder's text, and the time limit after which a text is never sent. FAKE data only. */
@Tag("unit")
@Tag("command")
@DisplayName("SMS send command: appointment reminders and time limits")
class SmsSendCommandUnitTest {

    private static final Instant LIMIT = Instant.parse("2026-11-18T15:30:00Z");

    @Test
    @DisplayName("should make an appointment reminder to the cell number, its purpose set here")
    void shouldMakeAReminder_toTheCellNumberWithItsPurposeSetHere() {
        SmsSendCommand reminder = SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder", "999998",
                501, LIMIT);

        assertThat(reminder.messagePurpose()).isEqualTo(SmsMessagePurpose.APPOINTMENT_REMINDER);
        assertThat(reminder.recipientPhoneType()).isEqualTo(SmsRecipientPhoneType.CELL);
        assertThat(reminder.appointmentNo()).isEqualTo(501);
        assertThat(reminder.requestedByHealthcareProviderNo()).isEqualTo("999998");
        assertThat(reminder.requestedBySecurityNo()).isNull();
        assertThat(reminder.expiresAt()).isEqualTo(LIMIT);
        assertThat(reminder).hasToString("SmsSendCommand[redacted]");
    }

    @Test
    @DisplayName("should keep texts made the old way without a time limit")
    void shouldHaveNoTimeLimit_forTextsMadeTheOldWay() {
        SmsSendCommand message = new SmsSendCommand(123, "416-555-1212", SmsRecipientPhoneType.CELL, "FAKE text",
                SmsMessagePurpose.PATIENT_MESSAGE, "999998", null, null);

        assertThat(message.expiresAt()).isNull();
        assertThat(message.expiredAt(Instant.MAX)).isFalse();
        assertThat(SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE text", "999998").expiresAt()).isNull();
    }

    @Test
    @DisplayName("should count a text as expired from its limit on, not before")
    void shouldCountATextAsExpired_fromItsLimitOn() {
        SmsSendCommand reminder = SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder", "999998",
                501, LIMIT);

        assertThat(reminder.expiredAt(LIMIT.minusMillis(1))).isFalse();
        assertThat(reminder.expiredAt(LIMIT)).isTrue();
        assertThat(reminder.expiredAt(LIMIT.plusSeconds(60))).isTrue();
    }

    @Test
    @DisplayName("should refuse a reminder that names no appointment or no time limit")
    void shouldRefuseAReminder_withoutItsAppointmentOrTimeLimit() {
        SmsSendValidator validator = new SmsSendValidator();

        assertThat(validator.validate(SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder",
                "999998", 501, LIMIT)).valid()).isTrue();
        assertThat(validator.validate(SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder",
                "999998", null, LIMIT)).messages()).containsExactly("An appointment reminder must name its appointment.");
        assertThat(validator.validate(SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder",
                "999998", 0, LIMIT)).messages()).containsExactly("An appointment reminder must name its appointment.");
        assertThat(validator.validate(SmsSendCommand.appointmentReminder(123, "416-555-1212", "FAKE reminder",
                "999998", 501, null)).messages())
                .containsExactly("An appointment reminder must say when it is too late to send.");
        // A patient message needs neither.
        assertThat(validator.validate(SmsSendCommand.patientMessage(123, "416-555-1212", "FAKE text", "999998"))
                .valid()).isTrue();
    }
}
