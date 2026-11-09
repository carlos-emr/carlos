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

import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IllegalArgumentExceptionMapper}.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@Tag("rest")
@Tag("exception")
@DisplayName("IllegalArgumentExceptionMapper")
class IllegalArgumentExceptionMapperUnitTest {

    private final IllegalArgumentExceptionMapper mapper = new IllegalArgumentExceptionMapper();

    @Test
    @DisplayName("should return HTTP 400")
    void shouldReturn400Status_forErrorContract() {
        Response response = mapper.toResponse(new IllegalArgumentException("Unknown drug status: active"));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should return code VALIDATION_ERROR")
    void shouldReturnValidationErrorCode_forErrorContract() {
        Response response = mapper.toResponse(new IllegalArgumentException("Unknown drug status: active"));

        assertThat(((ErrorResponse) response.getEntity()).getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("should redact exception messages from client validation feedback")
    void shouldRedactExceptionMessage_forErrorContract() {
        Response response = mapper.toResponse(new IllegalArgumentException("Unknown drug status: active"));

        assertThat(((ErrorResponse) response.getEntity()).getMessage())
                .isEqualTo("One or more request parameters are invalid or malformed.");
    }
}
