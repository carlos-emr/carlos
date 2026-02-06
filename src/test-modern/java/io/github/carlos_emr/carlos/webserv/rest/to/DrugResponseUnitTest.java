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

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DrugResponse to verify current behavior before implementing JAX-RS exception mappers.
 * These tests ensure the mapper implementation doesn't break existing functionality.
 *
 * @since 2026-02-06
 */
@Tag("unit")
@Tag("rest")
@Tag("regression")
@DisplayName("DrugResponse Unit Tests")
class DrugResponseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should extend GenericRESTResponse")
    void shouldExtendGenericRESTResponse() {
        // When
        DrugResponse response = new DrugResponse();

        // Then
        assertThat(response).isInstanceOf(GenericRESTResponse.class);
        assertThat(response.isSuccess()).isTrue(); // Inherited behavior
    }

    @Test
    @DisplayName("should serialize with drug field when using Jackson")
    void shouldSerializeWithDrugField_whenUsingJackson() throws Exception {
        // Given
        DrugResponse response = new DrugResponse();
        DrugTo1 drug = new DrugTo1();
        drug.setBrandName("Test Drug");
        response.setDrug(drug);

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"drug\":");
        assertThat(json).contains("\"brandName\":\"Test Drug\"");
    }
}
