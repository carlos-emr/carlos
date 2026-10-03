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

import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PatientDirectiveExceptionMapper}.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@DisplayName("PatientDirectiveExceptionMapper")
class PatientDirectiveExceptionMapperUnitTest {

    private final PatientDirectiveExceptionMapper mapper = new PatientDirectiveExceptionMapper();

    @Test
    @DisplayName("should return HTTP 403")
    void shouldReturn403Status() {
        Response response = mapper.toResponse(new PatientDirectiveException("blocked by directive"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should return code PATIENT_DIRECTIVE")
    void shouldReturnPatientDirectiveCode() {
        Response response = mapper.toResponse(new PatientDirectiveException("blocked by directive"));

        assertThat(((ErrorResponse) response.getEntity()).getCode()).isEqualTo("PATIENT_DIRECTIVE");
    }

    @Test
    @DisplayName("should log at INFO level, not WARN or ERROR")
    void shouldLogAtInfoLevel() {
        try (LogCapture capture = LogCapture.forLogger(PatientDirectiveExceptionMapper.class)) {
            mapper.toResponse(new PatientDirectiveException("blocked by directive"));

            assertThat(capture.events()).isNotEmpty();
            assertThat(capture.events()).allSatisfy(event ->
                    assertThat(event.getLevel()).isEqualTo(Level.INFO));
        }
    }
}
