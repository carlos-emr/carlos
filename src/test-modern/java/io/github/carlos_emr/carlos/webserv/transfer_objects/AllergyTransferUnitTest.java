/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.test.builders.AllergyTestBuilder;

/**
 * Unit tests for {@link AllergyTransfer}.
 *
 * <p>Tests the static conversion from {@link Allergy} domain model
 * to {@link AllergyTransfer} transfer object.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("AllergyTransfer")
class AllergyTransferUnitTest {

    @Test
    @DisplayName("should convert Allergy to AllergyTransfer with correct id and description")
    void shouldConvertAllergy_toTransferObject() {
        Allergy allergy = AllergyTestBuilder.anAllergy()
                .withDescription("Penicillin")
                .build();

        // Set ID via reflection since it's auto-generated
        setIdViaReflection(allergy, 42);

        AllergyTransfer transfer = AllergyTransfer.toTransfer(allergy);

        assertThat(transfer).isNotNull();
        assertThat(transfer.getId()).isEqualTo(42);
        assertThat(transfer.getDescription()).isEqualTo("Penicillin");
    }

    @Test
    @DisplayName("should handle allergy with null description")
    void shouldHandleAllergy_withNullDescription() {
        Allergy allergy = AllergyTestBuilder.anAllergy()
                .withDescription(null)
                .build();
        setIdViaReflection(allergy, 1);

        AllergyTransfer transfer = AllergyTransfer.toTransfer(allergy);
        assertThat(transfer).isNotNull();
        assertThat(transfer.getId()).isEqualTo(1);
    }

    private void setIdViaReflection(Allergy allergy, Integer id) {
        try {
            java.lang.reflect.Field field = Allergy.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(allergy, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set allergy ID", e);
        }
    }
}
