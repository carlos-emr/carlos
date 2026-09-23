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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalException transport failures")
class PatientPortalExceptionTransportUnitTest {

    static Stream<Arguments> causes() {
        return Stream.of(
                Arguments.of(new SocketTimeoutException(PatientPortalHttpClientExchange.DEADLINE_EXCEEDED),
                        "request deadline exceeded"),
                Arguments.of(new SocketTimeoutException("Read timed out"), "read timeout"),
                Arguments.of(new SSLHandshakeException("pin mismatch for portal.example"), "TLS handshake"),
                Arguments.of(new UnknownHostException("portal.example"), "host lookup"),
                Arguments.of(new ConnectException("Connection refused"), "connection refused"),
                Arguments.of(new NoHttpResponseException("The target server failed to respond"),
                        "connection closed without a response"),
                Arguments.of(new IOException("HTTP/1.1 999 secret=abc"), "HTTP exchange (IOException)"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("causes")
    @DisplayName("should name a PHI-free category for each transport failure")
    void shouldNameTheCategory_withoutTheCauseText(Throwable cause, String category) {
        PatientPortalException failure = PatientPortalException.ofTransportFailure("/x/{id}", cause);

        assertThat(failure.getCause().getMessage()).isEqualTo("portal transport failed: " + category);
        assertThat(failure.getCause().getMessage()).doesNotContain("portal.example", "secret");
        assertThat(failure.isRequestNotSent()).isFalse();
    }

    @Test
    @DisplayName("should mark a request that never left CARLOS as not sent")
    void shouldReportNotSent_whenTheRequestNeverLeftCarlos() {
        PatientPortalException failure = PatientPortalException.ofTransportFailure(
                "/x/{id}", new PortalRequestNotSentException("portal transport is busy or closed"));

        assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.TRANSPORT_FAILURE);
        assertThat(failure.isRequestNotSent()).isTrue();
    }
}
