// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SpecialistContactAddressUnitTest {
    @Test
    void shouldReadCountryAndPostal_inWriterOrder() {
        var address = SpecialistContactAddress.parse("123 Synthetic St Unit 2, Toronto, M5V 1A1, ON, CA, ");
        assertThat(address).isEqualTo(new SpecialistContactAddress(
                "123 Synthetic St Unit 2", "Toronto", "M5V 1A1", "ON", "CA"));
    }

    @Test
    void shouldPreserveLegacyAddress_whenOnlyAnotherContactFieldChanges() {
        for (String original : new String[]{"123 Main St, Toronto, ON M5V 1A1", "123 Main St\nToronto ON", " 123 Main St\r\nToronto ON ", ",", ",,,,"}) {
            var parsed = SpecialistContactAddress.parse(original);
            ProfessionalContact contact = new ProfessionalContact();
            contact.setAddress(parsed.street()); contact.setCity(parsed.city()); contact.setPostal(parsed.postal());
            contact.setProvince(parsed.province()); contact.setCountry(parsed.country());
            contact.setFirstName("Changed name");
            assertThat(SpecialistContactAddress.update(original, contact)).as(original).isEqualTo(original);
        }
    }

    @Test
    void shouldRetainOtherComponents_whenPostalCodeIsEdited() {
        String original = "123 Main St, Toronto, M5V 1A1, XX, Canada, ";
        ProfessionalContact contact = new ProfessionalContact();
        contact.setAddress("123 Main St"); contact.setCity("Toronto"); contact.setPostal("M5V 2B2");
        contact.setProvince("XX"); contact.setCountry("Canada");
        assertThat(SpecialistContactAddress.update(original, contact))
                .isEqualTo("123 Main St, Toronto, M5V 2B2, XX, Canada, ");
    }

    @Test
    void shouldRefuseAmbiguousComponents_whenStructuredAddressIsEdited() {
        ProfessionalContact contact = new ProfessionalContact();
        contact.setAddress("street, unit"); contact.setCity("Toronto");
        assertThatThrownBy(() -> SpecialistContactAddress.update("old", contact)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldChangeVersion_whenAnyReplacedAddressOrContactValueChanges() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Original");
        String version = SpecialistContactEditVersion.of(specialist);
        specialist.setStreetAddress("Replacement");
        assertThat(SpecialistContactEditVersion.of(specialist)).isNotEqualTo(version);
        String replacement = SpecialistContactEditVersion.of(specialist);
        specialist.setLastName("Changed");
        assertThat(SpecialistContactEditVersion.of(specialist)).isNotEqualTo(replacement);
    }
}
