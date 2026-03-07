/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 AllergyTransferTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * Unit tests for {@link AllergyTransfer}.
 *
 * <p>Tests conversion from Allergy domain model to AllergyTransfer transfer object.
 * Migrated from legacy JUnit 4 AllergyTransferTest.
 *
 * @since 2014-01-01 (original)
 */
@Tag("unit")
@DisplayName("AllergyTransfer unit tests")
class AllergyTransferUnitTest {

    @Test
    @DisplayName("should convert Allergy to AllergyTransfer with correct fields")
    void shouldConvertAllergy_toAllergyTransferWithCorrectFields() {
        Allergy p = new Allergy();
        p.setId(12345);
        p.setDescription("testAllergy");
        p.setDemographicNo(555);

        AllergyTransfer pt = AllergyTransfer.toTransfer(p);

        assertThat(pt.getId()).isEqualTo(12345);
        assertThat(pt.getDescription()).isEqualTo("testAllergy");
    }
}
