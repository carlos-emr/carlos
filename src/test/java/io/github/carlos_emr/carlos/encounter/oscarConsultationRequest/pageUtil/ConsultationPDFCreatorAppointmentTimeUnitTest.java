/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The appointment time a printed referral shows ({@link ConsultationPDFCreator#formatAppointmentTime}), from
 * the 12-hour parts the request form posts (issue #4787).
 */
@Tag("unit")
@DisplayName("ConsultationPDFCreator appointment time")
class ConsultationPDFCreatorAppointmentTimeUnitTest {

    @ParameterizedTest(name = "{0}:{1} {2} prints {3}")
    @CsvSource({
            // Print posts the minute without its leading zero.
            "3, 5, PM, 3:05 PM",
            "4, 0, PM, 4:00 PM",
            // The saved record already pads it.
            "3, 05, PM, 3:05 PM",
            "11, 59, AM, 11:59 AM",
    })
    @DisplayName("should print the minute with two digits")
    void shouldPadMinute_whenFormattingAppointmentTime(String hour, String minute, String half, String printed) {
        assertThat(ConsultationPDFCreator.formatAppointmentTime(hour, minute, half)).isEqualTo(printed);
    }

    @ParameterizedTest(name = "{0}:{1} {2} prints {3}")
    @CsvSource({
            "12, 5, AM, 12:05 AM",
            "12, 0, AM, 12:00 AM",
            "12, 5, PM, 12:05 PM",
            "12, 0, PM, 12:00 PM",
    })
    @DisplayName("should keep 12 and its AM/PM label around midnight and noon")
    void shouldKeepTwelveAndLabel_whenTimeIsAroundMidnightOrNoon(String hour, String minute, String half, String printed) {
        assertThat(ConsultationPDFCreator.formatAppointmentTime(hour, minute, half)).isEqualTo(printed);
    }

    @Test
    @DisplayName("should print nothing when there is no hour, even with the form's default AM")
    void shouldPrintNothing_whenHourIsMissing() {
        assertThat(ConsultationPDFCreator.formatAppointmentTime("", "", "AM")).isEmpty();
        assertThat(ConsultationPDFCreator.formatAppointmentTime(null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("should print the hour and label when there is no minute")
    void shouldPrintHourAndLabel_whenMinuteIsMissing() {
        assertThat(ConsultationPDFCreator.formatAppointmentTime("9", "", "AM")).isEqualTo("9 AM");
        assertThat(ConsultationPDFCreator.formatAppointmentTime("9", null, null)).isEqualTo("9");
    }

    @Test
    @DisplayName("should print unexpected parts as given rather than hide them")
    void shouldPrintAsGiven_whenPartsAreOutOfRange() {
        assertThat(ConsultationPDFCreator.formatAppointmentTime("13", "5", "PM")).isEqualTo("13:5 PM");
        assertThat(ConsultationPDFCreator.formatAppointmentTime("3", "75", "PM")).isEqualTo("3:75 PM");
        assertThat(ConsultationPDFCreator.formatAppointmentTime("x", "5", "PM")).isEqualTo("x:5 PM");
    }
}
