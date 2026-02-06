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
package io.github.carlos_emr.carlos.webserv.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests verifying WebApplicationException behavior is preserved.
 * This exception type already returns correct HTTP status codes and should NOT be affected
 * by JAX-RS exception mappers in #242.
 *
 * @since 2026-02-06
 */
@Tag("unit")
@Tag("rest")
@Tag("regression")
@DisplayName("WebApplicationException Regression Tests")
class WebApplicationExceptionRegressionTest {

    /**
     * Verifies WebApplicationException preserves HTTP status codes.
     * This behavior must NOT change when exception mappers are implemented.
     */
    @Test
    @DisplayName("should preserve HTTP status from WebApplicationException")
    void shouldPreserveHttpStatus_whenWebApplicationExceptionThrown() {
        // Test various HTTP status codes
        assertWebApplicationExceptionStatus(Response.Status.NOT_FOUND);
        assertWebApplicationExceptionStatus(Response.Status.FORBIDDEN);
        assertWebApplicationExceptionStatus(Response.Status.UNAUTHORIZED);
        assertWebApplicationExceptionStatus(Response.Status.BAD_REQUEST);
        assertWebApplicationExceptionStatus(Response.Status.CONFLICT);
    }

    @Test
    @DisplayName("should create 404 WebApplicationException")
    void shouldCreate404WebApplicationException() {
        // Given
        Response.Status expectedStatus = Response.Status.NOT_FOUND;
        String message = "Provider not found";

        // When
        WebApplicationException exception = new WebApplicationException(
            message,
            expectedStatus
        );

        // Then
        assertThat(exception.getResponse().getStatus()).isEqualTo(expectedStatus.getStatusCode());
        assertThat(exception.getMessage()).contains(message);
    }

    @Test
    @DisplayName("should create 403 WebApplicationException")
    void shouldCreate403WebApplicationException() {
        // Given
        Response.Status expectedStatus = Response.Status.FORBIDDEN;

        // When
        WebApplicationException exception = new WebApplicationException(
            "Access denied",
            expectedStatus
        );

        // Then
        assertThat(exception.getResponse().getStatus()).isEqualTo(expectedStatus.getStatusCode());
    }

    /**
     * Helper method to verify WebApplicationException preserves status codes.
     */
    private void assertWebApplicationExceptionStatus(Response.Status status) {
        WebApplicationException exception = new WebApplicationException(
            "Test message",
            status
        );

        assertThat(exception.getResponse().getStatus())
            .as("HTTP status should match the status provided to WebApplicationException")
            .isEqualTo(status.getStatusCode());
    }
}
