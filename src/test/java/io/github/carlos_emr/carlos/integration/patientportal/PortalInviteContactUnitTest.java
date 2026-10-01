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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PortalInviteContact")
class PortalInviteContactUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should normalise the chart values the portal matches at activation")
    void shouldNormaliseContact_fromTheChart() {
        PortalInviteContact contact = PortalInviteContact.from(patient(" patient@example.com ", "5", "1234-567 890ab"));

        assertThat(contact.email()).isEqualTo("patient@example.com");
        assertThat(contact.dateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 20));
        assertThat(contact.healthCardNumber()).isEqualTo("1234567890AB");
    }

    @Test
    @DisplayName("should refuse a date of birth that is not a real date")
    void shouldRefuse_whenTheDateOfBirthIsImpossible() {
        assertThatThrownBy(() -> PortalInviteContact.from(patient("patient@example.com", "13", "1234567890")))
                .isInstanceOfSatisfying(PortalInviteException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.INCOMPLETE_DATE_OF_BIRTH));
    }

    @Test
    @DisplayName("should keep the patient's details out of its string form")
    void shouldRedactDetails_fromToString() {
        PortalInviteContact contact = PortalInviteContact.from(patient("patient@example.com", "5", "1234567890"));

        assertThat(contact.toString()).doesNotContain("patient@example.com", "1234567890", "1980");
    }

    @Test
    void shouldAcceptInclusiveDateBounds_withUtcToday() {
        LocalDate today = LocalDate.of(2024, 2, 29);
        assertThat(contactBorn(LocalDate.of(1898, 2, 28), today).dateOfBirth()).isEqualTo(today.minusYears(126));
        assertThat(contactBorn(LocalDate.of(2024, 3, 1), today).dateOfBirth()).isEqualTo(today.plusDays(1));
        for (LocalDate invalid : java.util.List.of(LocalDate.of(1898, 2, 27), LocalDate.of(2024, 3, 2))) {
            assertThatThrownBy(() -> contactBorn(invalid, today))
                    .isInstanceOfSatisfying(PortalInviteException.class,
                            exception -> assertThat(exception.reason()).isEqualTo(Reason.INVALID_DATE_OF_BIRTH));
        }
    }

    @Test
    void shouldNormalizeAllPortalWhitespace_withAsciiCardBounds() {
        Demographic patient = patient("patient@example.com", "5", "\u00a012-\t34\u0085\u200734\n");
        assertThat(PortalInviteContact.from(patient).healthCardNumber()).isEqualTo("123434");
        patient.setHin("a".repeat(64));
        assertThat(PortalInviteContact.from(patient).healthCardNumber()).isEqualTo("A".repeat(64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234!", "1234é", "1234漢", "1234_"})
    void shouldRejectInvalidHealthCard_afterNormalization(String hin) {
        assertThatThrownBy(() -> PortalInviteContact.from(patient("patient@example.com", "5", hin)))
                .isInstanceOfSatisfying(PortalInviteException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.INVALID_HEALTH_CARD));
    }

    @Test
    void shouldRejectTooLongHealthCard_afterNormalization() {
        assertThatThrownBy(() -> PortalInviteContact.from(patient("patient@example.com", "5", "a".repeat(65))))
                .isInstanceOfSatisfying(PortalInviteException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.INVALID_HEALTH_CARD));
    }

    @Test
    void shouldProvideSafeMessage_forDefaultConsentRefusal() {
        assertThat(new PortalInviteException(Reason.CONSENT_BLOCKED)).hasMessage("Email consent does not permit this invitation.");
    }

    private static PortalInviteContact contactBorn(LocalDate born, LocalDate today) {
        Demographic patient = patient("patient@example.com", String.valueOf(born.getMonthValue()), "1234");
        patient.setYearOfBirth(String.valueOf(born.getYear()));
        patient.setDateOfBirth(String.valueOf(born.getDayOfMonth()));
        return PortalInviteContact.from(patient, today);
    }

    private static Demographic patient(String email, String month, String hin) {
        Demographic patient = new Demographic();
        patient.setEmail(email);
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth(month);
        patient.setDateOfBirth("20");
        patient.setHin(hin);
        return patient;
    }
}
