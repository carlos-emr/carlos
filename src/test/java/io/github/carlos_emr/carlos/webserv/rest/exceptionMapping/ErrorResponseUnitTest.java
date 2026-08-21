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

package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ErrorResponse} JSON contract used by the REST exception mappers.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@DisplayName("ErrorResponse")
class ErrorResponseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("should populate an ISO-8601 UTC timestamp on construction")
    void shouldSetTimestampOnConstruction() {
        ErrorResponse response = ErrorResponse.of("VALIDATION_ERROR", "bad input");

        assertThat(response.getTimestamp())
                .isNotNull()
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    @DisplayName("should serialize to the documented JSON shape and field order")
    void shouldSerializeToJsonCorrectly() throws Exception {
        ErrorResponse response = ErrorResponse.of("VALIDATION_ERROR", "bad input");

        String json = MAPPER.writeValueAsString(response);

        assertThat(json)
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"message\":\"bad input\"")
                .contains("\"timestamp\":");
        // Field order is pinned by @JsonPropertyOrder: code before message before timestamp.
        assertThat(json.indexOf("\"code\"")).isLessThan(json.indexOf("\"message\""));
        assertThat(json.indexOf("\"message\"")).isLessThan(json.indexOf("\"timestamp\""));
    }

    @Test
    @DisplayName("should include details when provided")
    void shouldIncludeDetailsWhenProvided() throws Exception {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", "status");
        ErrorResponse response = ErrorResponse.of("VALIDATION_ERROR", "bad input").withDetails(details);

        assertThat(response.getDetails()).containsEntry("parameter", "status");
        assertThat(MAPPER.writeValueAsString(response))
                .contains("\"details\":")
                .contains("\"parameter\":\"status\"");
    }

    @Test
    @DisplayName("should omit details from the JSON when null")
    void shouldExcludeDetailsWhenNull() throws Exception {
        ErrorResponse response = ErrorResponse.of("INTERNAL_ERROR", "boom");

        assertThat(response.getDetails()).isNull();
        assertThat(MAPPER.writeValueAsString(response)).doesNotContain("details");
    }
}
