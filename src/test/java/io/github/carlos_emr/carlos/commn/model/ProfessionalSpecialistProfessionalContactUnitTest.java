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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins how a professional contact becomes a specialist: the consultant a consultation letter shows
 * for a health-care-team contact ({@link ProfessionalSpecialist#healthCareTeamConsultant}, used by
 * the chart's Print and the CDS export's printed letter) and the directory specialist the contact
 * editor saves ({@link ProfessionalSpecialist#applyProfessionalContact}).
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
    void shouldShowOnlyTheContactsOwnValues_asAHealthCareTeamConsultant() {
        var consultant = ProfessionalSpecialist.healthCareTeamConsultant(contact());

        // A contact has no salutation or letters, and none are borrowed from a directory row.
        assertThat(consultant.getId()).isNull();
        assertThat(consultant.getSalutation()).isNull();
        assertThat(consultant.getProfessionalLetters()).isNull();
        assertThat(consultant.getFormattedTitle()).isEqualTo("NURSE, Nora");
        assertThat(consultant.getStreetAddress()).isEqualTo("8 Care Rd Suite 2, Testville, A1A 1A1, ON, Canada");
        assertThat(consultant.getPhoneNumber()).isEqualTo("555-0500 ext 12");
        assertThat(consultant.getFaxNumber()).isEqualTo("555-0501");
        assertThat(consultant.getEmailAddress()).isEqualTo("nurse@example.invalid");
        assertThat(consultant.getReferralNo()).isEqualTo("12345");
        assertThat(consultant.getSpecialtyType()).isEqualTo("Nurse Practitioner");
        assertThat(consultant.getPrivatePhoneNumber()).isEqualTo("555-0502");
        assertThat(consultant.getCellPhoneNumber()).isEqualTo("555-0503");
    }

    @Test
    void shouldLeaveMissingValuesEmpty_withoutACpsoNumber() {
        // As a CDS import creates a team contact: names only.
        var contact = new ProfessionalContact();
        contact.setFirstName("Nora");
        contact.setLastName("NURSE");
        contact.setWorkPhoneExtension("12");

        var consultant = ProfessionalSpecialist.healthCareTeamConsultant(contact);

        assertThat(consultant.getFormattedTitle()).isEqualTo("NURSE, Nora");
        assertThat(consultant.getStreetAddress()).isEmpty();
        assertThat(consultant.getPhoneNumber()).isNull();
        assertThat(consultant.getFaxNumber()).isNull();
        assertThat(consultant.getReferralNo()).isNull();

        contact.setCity("Testville");
        contact.setProvince("ON");
        assertThat(ProfessionalSpecialist.healthCareTeamConsultant(contact).getStreetAddress())
                .isEqualTo("Testville, ON");
    }

    @Test
    void shouldKeepTheEditedSpecialistsTitleAndAddressPositions_whenTheContactEditorSavesIt() {
        // The contact editor edits a directory specialist shown as a contact: its own row.
        var specialist = new ProfessionalSpecialist();
        specialist.setSalutation("Dr.");
        specialist.setProfessionalLetters("NP");
        specialist.setFirstName("Other");

        specialist.applyProfessionalContact(contact());

        assertThat(specialist.getFormattedTitle()).isEqualTo("Dr. NURSE, Nora NP");
        assertThat(specialist.getStreetAddress()).isEqualTo("8 Care Rd Suite 2, Testville, A1A 1A1, ON, Canada,");
        assertThat(specialist.getPhoneNumber()).isEqualTo("555-0500 ext 12");
        assertThat(specialist.getReferralNo()).isEqualTo("12345");
    }

    @Test
    void shouldStoreNoPhoneOrReferralNumber_whenTheEditorFormHasNone() {
        var contact = contact();
        contact.setWorkPhone(null);
        contact.setCpso(null);
        var specialist = new ProfessionalSpecialist();

        specialist.applyProfessionalContact(contact);

        assertThat(specialist.getPhoneNumber()).isNull();
        assertThat(specialist.getReferralNo()).isNull();
    }
}
