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
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.naming.OperationNotSupportedException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the REST {@code ExceptionMapper} providers.
 *
 * <p>Each mapper is exercised directly (no CXF/Spring context): the {@code @Context UriInfo}
 * field is left unset, which the mappers null-guard, so a plain constructor call is enough.
 * Assertions pin the three parts of the contract that matter to clients: the HTTP status,
 * the JSON media type (so error bodies never fall through to the XML provider), and the
 * stable machine-readable {@code code}. Dedicated cases guard the PHI boundary.
 *
 * @since 2026-06-21
 */
@Tag("unit")
@DisplayName("REST exception mappers")
class ExceptionMapperUnitTest {

    private static ErrorResponse entityOf(Response response) {
        assertThat(response.getEntity()).isInstanceOf(ErrorResponse.class);
        return (ErrorResponse) response.getEntity();
    }

    @Nested
    @DisplayName("GenericExceptionMapper")
    class Generic {

        private final GenericExceptionMapper mapper = new GenericExceptionMapper();

        @Test
        @DisplayName("should map any Throwable to a 500 JSON body")
        void shouldReturnInternalError_forArbitraryThrowable() {
            Response response = mapper.toResponse(new RuntimeException("boom"));

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("INTERNAL_ERROR");
        }

        @Test
        @DisplayName("should not echo the raw exception message to the client")
        void shouldOmitRawMessage_forArbitraryThrowable() {
            Response response = mapper.toResponse(new RuntimeException("secret internal detail"));

            assertThat(entityOf(response).getMessage()).doesNotContain("secret internal detail");
        }
    }

    @Nested
    @DisplayName("WebApplicationExceptionMapper")
    class WebApplication {

        private final WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();

        @Test
        @DisplayName("should preserve the original 404 status and derive its code")
        void shouldPreserveStatus_whenNotFound() {
            Response response = mapper.toResponse(new WebApplicationException(Response.Status.NOT_FOUND));

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("should preserve a 4xx status other than 404")
        void shouldPreserveStatus_whenBadRequest() {
            Response response = mapper.toResponse(new WebApplicationException(Response.Status.BAD_REQUEST));

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(entityOf(response).getCode()).isEqualTo("BAD_REQUEST");
        }
    }

    @Nested
    @DisplayName("AccessDeniedExceptionMapper")
    class AccessDenied {

        private final AccessDeniedExceptionMapper mapper = new AccessDeniedExceptionMapper();

        @Test
        @DisplayName("should map AccessDeniedException to a 403 JSON body")
        void shouldReturnForbidden_whenAccessDenied() {
            Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("should expose permission and action but not the subject")
        void shouldSurfacePermissionAndAction_withoutSubject() {
            Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

            ErrorResponse body = entityOf(response);
            assertThat(body.getDetails())
                    .containsEntry("permission", "_rx")
                    .containsEntry("action", "r")
                    .doesNotContainKey("subject");
        }

        @Test
        @DisplayName("should keep the PHI-correlating subject out of the client body entirely")
        void shouldOmitSubject_fromMessageAndDetails() {
            Response response = mapper.toResponse(new AccessDeniedException("_rx", "r", 42));

            ErrorResponse body = entityOf(response);
            assertThat(body.getMessage()).doesNotContain("42");
            assertThat(String.valueOf(body.getDetails())).doesNotContain("42");
        }

        @Test
        @DisplayName("should omit details when permission and action are absent")
        void shouldOmitDetails_whenNoPermissionOrAction() {
            Response response = mapper.toResponse(new AccessDeniedException());

            assertThat(entityOf(response).getDetails()).isNull();
        }
    }

    @Nested
    @DisplayName("SecurityExceptionMapper")
    class Security {

        private final SecurityExceptionMapper mapper = new SecurityExceptionMapper();

        @Test
        @DisplayName("should map SecurityException to a 403 JSON body")
        void shouldReturnForbidden_whenSecurityException() {
            Response response = mapper.toResponse(
                    new SecurityException("missing required sec object (_rx)"));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("should not echo the internal security object name to the client")
        void shouldOmitSecurityObjectName_fromBody() {
            Response response = mapper.toResponse(
                    new SecurityException("missing required sec object (_rx)"));

            assertThat(entityOf(response).getMessage()).doesNotContain("_rx");
        }
    }

    @Nested
    @DisplayName("IllegalArgumentExceptionMapper")
    class IllegalArgument {

        private final IllegalArgumentExceptionMapper mapper = new IllegalArgumentExceptionMapper();

        @Test
        @DisplayName("should map the unknown-drug-status defect to a 400 JSON body")
        void shouldReturnBadRequest_whenUnknownEnumValue() {
            // Reproduces the issue #242 flow: RxStatus.valueOf("ACTIVE") throws this.
            Response response = mapper.toResponse(
                    new IllegalArgumentException(
                            "No enum constant io.github.carlos_emr.carlos.managers.RxStatus.ACTIVE"));

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("should not leak internal class names from the raw message")
        void shouldOmitInternalClassName_fromBody() {
            Response response = mapper.toResponse(
                    new IllegalArgumentException(
                            "No enum constant io.github.carlos_emr.carlos.managers.RxStatus.ACTIVE"));

            assertThat(entityOf(response).getMessage()).doesNotContain("io.github.carlos_emr");
        }

        @Test
        @DisplayName("should also cover NumberFormatException as a subtype")
        void shouldReturnBadRequest_whenNumberFormatException() {
            Response response = mapper.toResponse(new NumberFormatException("For input string: \"abc\""));

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(entityOf(response).getCode()).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("OperationNotSupportedExceptionMapper")
    class OperationNotSupported {

        private final OperationNotSupportedExceptionMapper mapper =
                new OperationNotSupportedExceptionMapper();

        @Test
        @DisplayName("should map OperationNotSupportedException to a 400 JSON body")
        void shouldReturnBadRequest_whenOperationNotSupported() {
            Response response = mapper.toResponse(new OperationNotSupportedException());

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getMediaType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
            assertThat(entityOf(response).getCode()).isEqualTo("UNSUPPORTED_OPERATION");
        }
    }
}
