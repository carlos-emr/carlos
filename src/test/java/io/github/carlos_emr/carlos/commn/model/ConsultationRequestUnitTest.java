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

import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsultationRequest} mapping metadata.
 *
 * @since 2026-05-30
 */
@DisplayName("ConsultationRequest Unit Tests")
@Tag("unit")
@Tag("consultation")
class ConsultationRequestUnitTest {

    @Test
    @DisplayName("should use lazy fetch for optional detail relationships")
    void shouldUseLazyFetch_whenRelationshipMappingsInspected() throws Exception {
        assertThat(fetchType("professionalSpecialist")).isEqualTo(FetchType.LAZY);
        assertThat(fetchType("demographicContact")).isEqualTo(FetchType.LAZY);
        assertThat(fetchType("lookupListItem")).isEqualTo(FetchType.LAZY);
    }

    private static FetchType fetchType(String fieldName) throws NoSuchFieldException {
        return ConsultationRequest.class.getDeclaredField(fieldName)
                .getAnnotation(ManyToOne.class)
                .fetch();
    }
}
