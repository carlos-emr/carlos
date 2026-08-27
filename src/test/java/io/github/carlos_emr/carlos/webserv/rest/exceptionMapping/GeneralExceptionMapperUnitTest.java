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

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GeneralExceptionMapper}, the catch-all {@code Throwable} mapper.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@DisplayName("GeneralExceptionMapper")
class GeneralExceptionMapperUnitTest {

    private final GeneralExceptionMapper mapper = new GeneralExceptionMapper();

    @Test
    @DisplayName("should return HTTP 500")
    void shouldReturn500Status() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("should return code INTERNAL_ERROR")
    void shouldReturnInternalErrorCode() {
        Response response = mapper.toResponse(new RuntimeException("boom"));

        assertThat(((ErrorResponse) response.getEntity()).getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("should not expose the exception message to the client")
    void shouldNotExposeExceptionMessage() {
        Response response = mapper.toResponse(new RuntimeException("secret internal detail"));

        assertThat(((ErrorResponse) response.getEntity()).getMessage())
                .doesNotContain("secret internal detail");
    }

    @Test
    @DisplayName("should log the full exception with stack trace at ERROR")
    void shouldLogFullStackTrace() {
        try (LogCapture capture = LogCapture.forLogger(GeneralExceptionMapper.class)) {
            RuntimeException boom = new RuntimeException("boom");
            mapper.toResponse(boom);

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrown()).isSameAs(boom);
            });
        }
    }
}
