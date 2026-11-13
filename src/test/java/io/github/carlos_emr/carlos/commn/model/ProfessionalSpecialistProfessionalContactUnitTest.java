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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the consultant a health-care-team professional contact becomes on a consultation letter:
 * the chart's Print ({@code DemographicContactCreator}) and the CDS export's printed letter both
 * apply {@link ProfessionalSpecialist#applyProfessionalContact}, so they show the same values.
 */
@Tag("unit")
class ProfessionalSpecialistProfessionalContactUnitTest {
    private static ProfessionalContact contact() {
        var contact = new ProfessionalContact();
        contact.setId(40);
        contact.setFirstName(" Nora ");
        contact.setLastName("NURSE");
        contact.setAddress(" 8 Care Rd ");
        contact.setAddress2("Suite 2");
        contact.setCity("Testville");
        contact.setPostal("A1A 1A1");
        contact.setProvince("ON");
        contact.setCountry("Canada");
        contact.setWorkPhone("555-0500");
        contact.setWorkPhoneExtension("12");
        contact.setFax("555-0501");
        contact.setEmail("nurse@example.invalid");
        contact.setCpso(" 12345 ");
        contact.setSpecialty("Nurse Practitioner");
        contact.setResidencePhone("555-0502");
        contact.setCellPhone("555-0503");
        return contact;
    }

    @Test
    void shouldTakeTheContactsDetails_andKeepTheSalutationAndLetters() {
        var specialist = new ProfessionalSpecialist();
        specialist.setSalutation("Dr.");
        specialist.setProfessionalLetters("NP");
        specialist.setFirstName("Other");

        specialist.applyProfessionalContact(contact());

        assertThat(specialist.getFormattedTitle()).isEqualTo("Dr. NURSE, Nora NP");
        assertThat(specialist.getStreetAddress()).isEqualTo("8 Care Rd Suite 2, Testville, A1A 1A1, ON, Canada,");
        assertThat(specialist.getPhoneNumber()).isEqualTo("555-0500 ext 12");
        assertThat(specialist.getFaxNumber()).isEqualTo("555-0501");
        assertThat(specialist.getEmailAddress()).isEqualTo("nurse@example.invalid");
        assertThat(specialist.getReferralNo()).isEqualTo("12345");
        assertThat(specialist.getSpecialtyType()).isEqualTo("Nurse Practitioner");
        assertThat(specialist.getPrivatePhoneNumber()).isEqualTo("555-0502");
        assertThat(specialist.getCellPhoneNumber()).isEqualTo("555-0503");
    }

    @Test
    void shouldJoinWhatTheChartAlwaysJoined_whenTheContactLacksAPhoneOrAddress() {
        var contact = contact();
        contact.setAddress(null);
        contact.setAddress2(null);
        contact.setPostal(null);
        contact.setCountry(null);
        contact.setWorkPhone(null);
        contact.setWorkPhoneExtension("");
        var specialist = new ProfessionalSpecialist();

        specialist.applyProfessionalContact(contact);

        assertThat(specialist.getStreetAddress()).isEqualTo(", Testville, , ON, ,");
        assertThat(specialist.getPhoneNumber()).isEqualTo("null");
    }

    @Test
    void shouldFail_whenTheContactHasNoCpsoNumber() {
        var contact = contact();
        contact.setCpso(null);

        assertThatThrownBy(() -> new ProfessionalSpecialist().applyProfessionalContact(contact))
                .isInstanceOf(NullPointerException.class);
    }
}
