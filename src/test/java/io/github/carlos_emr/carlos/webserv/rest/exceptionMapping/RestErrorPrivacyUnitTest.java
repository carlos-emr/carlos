/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("rest")
@Tag("exception")
@Tag("security")
class RestErrorPrivacyUnitTest {
    @Test
    void shouldRedactPayloadsAndSourceFilenames_fromDiagnosticCauseChains() {
        RuntimeException error = new RuntimeException("SYNTHETIC-PATIENT-MARKER");
        IllegalStateException cause = new IllegalStateException("SYNTHETIC-CREDENTIAL-MARKER");
        error.initCause(cause);
        cause.initCause(error);
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("Fixture", "save", "SYNTHETIC-FILENAME", 7)});
        try (LogCapture logs = LogCapture.forLogger(GeneralExceptionMapper.class)) {
            var response = new GeneralExceptionMapper().toResponse(error);
            assertThat(((ErrorResponse) response.getEntity()).getMessage()).doesNotContain("SYNTHETIC");
            assertThat(logs.events()).isNotEmpty();
            for (var event : logs.events()) {
                assertThat(event.getMessage().getFormattedMessage()).doesNotContain("SYNTHETIC");
                Throwable safe = event.getThrown();
                assertThat(safe).isNotNull().isNotSameAs(error);
                for (int count = 0; safe != null; count++, safe = safe.getCause()) {
                    assertThat(count).isLessThan(8);
                    assertThat(safe.getMessage()).doesNotContain("SYNTHETIC");
                    for (var frame : safe.getStackTrace()) assertThat(frame.getFileName()).isNull();
                }
            }
        }
    }

    @Test
    void shouldPreserveExistingStatusesEntitiesAndHeaders_forWebApplicationExceptions() {
        for (int status : new int[]{400, 401, 403, 404, 409, 415, 429, 503}) {
            Response existing = Response.status(status).entity("existing contract").header("X-Fixture", "kept").build();
            Response mapped = new GeneralExceptionMapper().toResponse(new jakarta.ws.rs.WebApplicationException(existing));
            assertThat(mapped).isSameAs(existing);
            assertThat(mapped.getHeaderString("X-Fixture")).isEqualTo("kept");
        }
    }

    @Test
    void shouldOmitPatientSpecificPermissionNames_fromAccessDenialDetails() {
        var failure = new io.github.carlos_emr.carlos.commn.exception.AccessDeniedException(
                "_demographic$SYNTHETIC", "r", "SYNTHETIC");
        var response = new AccessDeniedExceptionMapper().toResponse(failure);
        assertThat(((ErrorResponse) response.getEntity()).getDetails()).doesNotContainKey("permission");
    }
}
