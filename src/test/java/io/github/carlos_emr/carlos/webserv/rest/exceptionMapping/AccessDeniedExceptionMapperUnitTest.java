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

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AccessDeniedExceptionMapper}.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@DisplayName("AccessDeniedExceptionMapper")
class AccessDeniedExceptionMapperUnitTest {

    private final AccessDeniedExceptionMapper mapper = new AccessDeniedExceptionMapper();

    @Test
    @DisplayName("should return HTTP 403")
    void shouldReturn403Status() {
        Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should return code ACCESS_DENIED")
    void shouldReturnAccessDeniedErrorCode() {
        Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

        assertThat(((ErrorResponse) response.getEntity()).getCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("should include permission and action in details but not the subject")
    void shouldIncludePermissionInDetails() {
        Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertThat(body.getDetails())
                .containsEntry("permission", "_rx")
                .containsEntry("action", "r")
                .doesNotContainKey("subject");
    }

    @Test
    @DisplayName("should log the full exception with stack trace at WARN")
    void shouldLogFullExceptionWithStackTrace() {
        try (LogCapture capture = LogCapture.forLogger(AccessDeniedExceptionMapper.class)) {
            mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrown()).isInstanceOf(AccessDeniedException.class);
            });
        }
    }
}
