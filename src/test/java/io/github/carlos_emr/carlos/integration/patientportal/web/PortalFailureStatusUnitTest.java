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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The status a portal failure becomes, which decides whether an outage is visible to anyone.
 *
 * <p>Every kind used to be answered as {@code 409 Conflict}. That is a business outcome, so a total
 * portal outage produced no {@code 5xx} anywhere in CARLOS: nothing in a Tomcat access log and
 * nothing in an uptime rule keyed on {@code 5xx} could see it, for as long as it lasted. A rate
 * limit was also indistinguishable from a permanent conflict, so a generic retry layer would give
 * up on something it should have retried.
 *
 * <p>Nothing pinned the mapping, which is why five status codes could be changed here without a
 * single existing test moving.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal failure status mapping")
class PortalFailureStatusUnitTest {

    /** The abstract class under test needs a concrete subclass; it contributes no behaviour. */
    private static final class TestAction extends PortalJsonAction {
        private static final long serialVersionUID = 1L;
    }

    private int statusFor(PatientPortalException exception) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TestAction().portalFailure(response, exception);
        return response.getStatus();
    }

    @ParameterizedTest(name = "portal {0} becomes CARLOS {1}")
    @CsvSource({
        "400, 400", // BAD_REQUEST
        "403, 403", // PERMISSION_DENIED
        "404, 404", // NOT_FOUND_OR_UNAUTHENTICATED
        "409, 409", // CONFLICT — the one that was always right
        "422, 400", // VALIDATION_FAILED: CARLOS sent something the portal would not take
        "429, 429", // THROTTLED
        "500, 502", // UNEXPECTED_STATUS: a fault at the portal, reported as one
        "503, 502"
    })
    @DisplayName("should answer a status that means what the failure means")
    void shouldMapEachPortalStatus_toADistinguishableCarlosStatus(int portalStatus, int expected)
            throws IOException {
        assertThat(statusFor(PatientPortalException.ofStatus(portalStatus, "/x/{id}", null)))
                .isEqualTo(expected);
    }

    /**
     * Why {@code PrintWriter.write} here is not the XSS_SERVLET the scanner reports.
     *
     * <p>Three things have to hold, and none was pinned: the body is produced by Jackson, so a
     * hostile value is a JSON string rather than markup and survives a round trip byte for byte;
     * the content type is {@code application/json}, so a browser has no reason to parse it as a
     * document; and {@code ResponseDefaultsFilter} — mapped to {@code /*} and ordered ahead of
     * Struts — adds {@code X-Content-Type-Options: nosniff}, which removes the sniffing path
     * this detector is really about. The filter is out of scope for a unit test, so the first
     * two are asserted here and the third is named.
     */
    @Test
    @DisplayName("should emit hostile values as JSON data, never as markup")
    void shouldSerializeHostileValues_asEscapedJson() throws Exception {
        String hostile = "</script><script>alert(document.cookie)</script>";
        MockHttpServletResponse response = new MockHttpServletResponse();
        TestAction action = new TestAction();
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        payload.put("reason", hostile);

        action.write(response, 200, payload);

        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        com.fasterxml.jackson.databind.JsonNode parsed =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response.getContentAsString());
        assertThat(parsed.get("reason").asText()).isEqualTo(hostile);
        // The document is one JSON object; the payload never becomes a second element in it.
        assertThat(response.getContentAsString()).startsWith("{").endsWith("}");
    }

    @Test
    @DisplayName("should answer 504 when the portal could not be reached at all")
    void shouldAnswerGatewayTimeout_whenTheCallNeverReachedThePortal() throws IOException {
        assertThat(
                        statusFor(
                                PatientPortalException.ofTransportFailure(
                                        "/x/{id}", new IOException("connect timed out"))))
                .isEqualTo(504);
    }

    @Test
    @DisplayName("should answer 502 when the portal replied in an unreadable shape")
    void shouldAnswerBadGateway_whenTheResponseCouldNotBeRead() throws IOException {
        assertThat(statusFor(PatientPortalException.ofMalformedResponse(200, "/x/{id}", new IOException("no body"))))
                .isEqualTo(502);
    }

    /**
     * The mapping must stay total. A new {@link PatientPortalException.Kind} that nobody classifies
     * would otherwise inherit whatever arm happens to be last, which is how "the portal rejected
     * this request" came to describe a portal that was simply down.
     */
    @Test
    @DisplayName("should classify every kind, so a new one cannot inherit an arm by accident")
    void shouldAnswerADeliberateStatus_forEveryKind() throws IOException {
        Set<PatientPortalException.Kind> covered = EnumSet.noneOf(PatientPortalException.Kind.class);
        for (int portalStatus : new int[] {400, 403, 404, 409, 422, 429, 500}) {
            covered.add(PatientPortalException.ofStatus(portalStatus, "/x/{id}", null).kind());
        }
        covered.add(
                PatientPortalException.ofTransportFailure("/x/{id}", new IOException("x")).kind());
        covered.add(PatientPortalException.ofMalformedResponse(200, "/x/{id}", new IOException("x")).kind());

        assertThat(covered)
                .withFailMessage(
                        "a Kind is not exercised here, so its status is whatever the switch"
                                + " happens to do")
                .containsExactlyInAnyOrderElementsOf(
                        EnumSet.allOf(PatientPortalException.Kind.class));
    }
}
