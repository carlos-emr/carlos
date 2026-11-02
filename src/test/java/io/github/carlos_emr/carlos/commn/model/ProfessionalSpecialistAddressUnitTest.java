/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProfessionalSpecialistAddressUnitTest {
    @Test
    void shouldPreserveSeparators_whenUpdatingAnIncompleteAddress() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setStreetAddress("Synthetic street,Synthetic city");
        specialist.setCity("Replacement city");
        specialist.setPostal("A1A 1A1");
        specialist.setProvince("ON");
        assertThat(specialist.getStreetAddress()).isEqualTo("Synthetic street,Replacement city,A1A 1A1,ON,,");
        assertThat(specialist.getAddressArray()).hasSize(5);
    }
}
