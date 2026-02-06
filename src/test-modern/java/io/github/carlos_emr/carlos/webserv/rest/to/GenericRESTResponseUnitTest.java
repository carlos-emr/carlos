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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GenericRESTResponse to verify current behavior before implementing JAX-RS exception mappers.
 * These tests ensure the mapper implementation doesn't break existing functionality.
 *
 * @since 2026-02-06
 */
@Tag("unit")
@Tag("rest")
@Tag("regression")
@DisplayName("GenericRESTResponse Unit Tests")
class GenericRESTResponseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should default to success true when using default constructor")
    void shouldDefaultToSuccessTrue_whenUsingDefaultConstructor() {
        // When
        GenericRESTResponse response = new GenericRESTResponse();

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    @DisplayName("should set success false with message when using parameterized constructor")
    void shouldSetSuccessFalseWithMessage_whenUsingParameterizedConstructor() {
        // Given
        String testMessage = "Operation failed";

        // When
        GenericRESTResponse response = new GenericRESTResponse(false, testMessage);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo(testMessage);
    }

    @Test
    @DisplayName("should serialize to JSON correctly with Jackson")
    void shouldSerializeToJsonCorrectly_whenUsingJackson() throws Exception {
        // Given
        GenericRESTResponse response = new GenericRESTResponse(false, "Test error");

        // When
        String json = objectMapper.writeValueAsString(response);

        // Then
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"Test error\"");
    }
}
