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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the demographiccust custom-field length check (issue #5869).
 *
 * <p>cust1 to cust4 are VARCHAR(255). Under an empty sql_mode MariaDB cut a longer value with only
 * a warning, so the end of a long patient alert was lost while the save reported success.</p>
 *
 * @since 2026-11-11
 */
@DisplayName("DemographicCust field length validation")
@Tag("unit")
@Tag("demographic")
class DemographicCustFieldLengthUnitTest {

    private static BiConsumer<DemographicCust, String> setterFor(String field) {
        return switch (field) {
            case "alert" -> DemographicCust::setAlert;
            case "nurse" -> DemographicCust::setNurse;
            case "resident" -> DemographicCust::setResident;
            case "midwife" -> DemographicCust::setMidwife;
            default -> throw new IllegalArgumentException(field);
        };
    }

    @ParameterizedTest
    @CsvSource({"alert,Alert,A", "nurse,Nurse,A", "resident,Resident,A", "midwife,Midwife,A",
            "alert,Alert,😀", "nurse,Nurse,😀", "resident,Resident,😀", "midwife,Midwife,😀"})
    void shouldAcceptTheFullColumn_andRefuseOneMoreCharacter(String field, String label, String character) {
        DemographicCust cust = new DemographicCust();
        String exact = character.repeat(255);
        setterFor(field).accept(cust, exact);
        assertThat(cust.validateFieldLengths()).isEmpty();

        setterFor(field).accept(cust, exact + character);
        assertThat(cust.validateFieldLengths())
                .containsExactly(label + " exceeds maximum length of 255 characters.");
    }

    @Test
    @DisplayName("should count a CR LF line break in the alert as two characters, as the column does")
    void shouldCountLineBreaksAsStored_forTheAlert() {
        DemographicCust cust = new DemographicCust();
        cust.setAlert("A".repeat(126) + "\r\n" + "A".repeat(127));
        assertThat(cust.validateFieldLengths()).isEmpty();

        cust.setAlert("A".repeat(127) + "\r\n" + "A".repeat(127));
        assertThat(cust.validateFieldLengths())
                .containsExactly("Alert exceeds maximum length of 255 characters.");
    }

    @Test
    @DisplayName("should report every over-long field without echoing the values or checking the notes")
    void shouldReportEachField_withoutValuesOrNotes() {
        DemographicCust cust = new DemographicCust();
        String tooLong = "FAKE-ALERT-".repeat(30);
        cust.setAlert(tooLong);
        cust.setNurse("N".repeat(256));
        cust.setResident(null);
        cust.setMidwife("");
        // content is MEDIUMTEXT, so a long note is not a length error.
        cust.setNotes("FAKE-NOTE ".repeat(1000));

        assertThat(cust.validateFieldLengths())
                .containsExactly("Alert exceeds maximum length of 255 characters.",
                        "Nurse exceeds maximum length of 255 characters.")
                .noneMatch(message -> message.contains("FAKE-"));
    }

    @Test
    @DisplayName("should keep the value whole while reporting the error")
    void shouldNotCutTheValue_whenItIsTooLong() {
        DemographicCust cust = new DemographicCust();
        String tooLong = "😀".repeat(256);
        cust.setAlert(tooLong);

        assertThat(cust.validateFieldLengths()).hasSize(1);
        assertThat(cust.getAlert()).isEqualTo(tooLong);
    }

    @Test
    @DisplayName("should accept a waiting-list note of 255 characters and refuse 256")
    void shouldCheckTheWaitingListNote_atItsColumnLength() {
        assertThat(WaitingList.validateNoteLength(null)).isEmpty();
        assertThat(WaitingList.validateNoteLength("😀".repeat(255))).isEmpty();
        assertThat(WaitingList.validateNoteLength("A".repeat(255))).isEmpty();
        assertThat(WaitingList.validateNoteLength("😀".repeat(256)))
                .containsExactly("Waiting list note exceeds maximum length of 255 characters.");
        assertThat(WaitingList.validateNoteLength("FAKE-NOTE-" + "A".repeat(250)))
                .singleElement().asString().doesNotContain("FAKE-NOTE");
    }
}
