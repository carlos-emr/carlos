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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The invite slice of the portal API, driven through the exchange seam.
 *
 * <p>Invites are the workflow where a CARLOS mistake is least recoverable: the activation token is
 * returned once, a resend invalidates its predecessor immediately, and the portal never delivers
 * anything itself.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalService invite calls")
class PatientPortalInviteCallsUnitTest {

    private static final String TOKEN = "portal-service-token-value-000001";
    private static final String INVITE_TOKEN = "one-time-activation-token-abc123";

    private static final String INVITE_JSON =
            """
            {"id": 7, "clinic_id": "maplecreek", "demographic_no": 123, "status": "pending",
             "created_by_id": "999998", "created_by": "Dr Example", "issued_count": 1,
             "last_issued_at": "2026-08-19T12:00:00+00:00", "last_issued_by": "Dr Example",
             "expires_at": "2026-08-26T12:00:00+00:00", "accepted_account_id": null,
             "supersedes_invite_id": null, "invite_token": "one-time-activation-token-abc123"}
            """;

    /** Records what CARLOS sent and replays a canned portal reply. */
    private static final class RecordingExchange implements PatientPortalHttpExchange {
        private final int statusCode;
        private final String body;
        private ClassicHttpRequest captured;
        private IOException failure;

        RecordingExchange(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        RecordingExchange(IOException failure) {
            this.statusCode = 0;
            this.body = "";
            this.failure = failure;
        }

        @Override
        public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
            this.captured = request;
            if (failure != null) {
                throw failure;
            }
            return new PatientPortalHttpResponse(statusCode, body);
        }
    }

    private PatientPortalSettings settings() {
        return PatientPortalSettings.fromProperties(
                Map.of(
                        PatientPortalSettings.BASE_URL_KEY, "https://portal.clinic.example",
                        PatientPortalSettings.CLINIC_ID_KEY, "maplecreek",
                        PatientPortalSettings.SERVICE_TOKEN_KEY, TOKEN));
    }

    private PatientPortalStaffContext staff() {
        return new PatientPortalStaffContext(
                "999998", "Dr Example", Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));
    }

    private String bodyOf(ClassicHttpRequest request) throws Exception {
        return new String(request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should post the demographic identity proof to the patient's invite endpoint")
        void shouldPostIdentityProof_toPatientInviteEndpoint() throws Exception {
            RecordingExchange exchange = new RecordingExchange(201, INVITE_JSON);
            PatientPortalService service = new PatientPortalService(settings(), exchange);

            service.createInvite(
                    123, "patient@example.com", LocalDate.of(1980, 1, 1), "1234567890", staff());

            assertThat(exchange.captured.getMethod()).isEqualTo("POST");
            assertThat(exchange.captured.getUri().getPath())
                    .isEqualTo("/internal/carlos/patients/123/invites");
            assertThat(bodyOf(exchange.captured))
                    .contains("\"demographic_no\":123")
                    .contains("\"email\":\"patient@example.com\"")
                    .contains("\"date_of_birth\":\"1980-01-01\"")
                    .contains("\"health_card_number\":\"1234567890\"");
        }

        @Test
        @DisplayName("should return the one-time token and the invite record")
        void shouldReturnIssuedInvite_whenPortalAccepts() {
            PatientPortalService service =
                    new PatientPortalService(settings(), new RecordingExchange(201, INVITE_JSON));

            PatientPortalIssuedInviteDto issued =
                    service.createInvite(
                            123,
                            "patient@example.com",
                            LocalDate.of(1980, 1, 1),
                            "1234567890",
                            staff());

            assertThat(issued.inviteToken().expose()).isEqualTo(INVITE_TOKEN);
            assertThat(issued.invite().id()).isEqualTo(7L);
            assertThat(issued.invite().status()).isEqualTo("pending");
            assertThat(issued.invite().issuedCount()).isEqualTo(1);
            assertThat(issued.invite().expiresAt()).isNotNull();
            assertThat(issued.invite().acceptedAccountId()).isNull();
        }

        @Test
        @DisplayName("should surface an existing account or pending invite as a conflict")
        void shouldThrowConflict_whenPortalRejectsDuplicate() {
            PatientPortalService service =
                    new PatientPortalService(
                            settings(),
                            new RecordingExchange(409, "{\"detail\": \"portal account exists\"}"));

            assertThatThrownBy(
                            () ->
                                    service.createInvite(
                                            123,
                                            "patient@example.com",
                                            LocalDate.of(1980, 1, 1),
                                            "1234567890",
                                            staff()))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.CONFLICT);
        }

        @Test
        @DisplayName("should report a rejected token as ambiguous rather than as a missing patient")
        void shouldThrowAmbiguousKind_whenPortalReturnsNotFound() {
            PatientPortalService service =
                    new PatientPortalService(settings(), new RecordingExchange(404, ""));

            assertThatThrownBy(
                            () ->
                                    service.createInvite(
                                            123,
                                            "patient@example.com",
                                            LocalDate.of(1980, 1, 1),
                                            "1234567890",
                                            staff()))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.NOT_FOUND_OR_UNAUTHENTICATED);
        }

        @Test
        @DisplayName("should keep the service token out of a failure raised mid-call")
        void shouldOmitServiceToken_whenTransportFails() {
            PatientPortalService service =
                    new PatientPortalService(
                            settings(), new RecordingExchange(new IOException("connection refused")));

            assertThatThrownBy(
                            () ->
                                    service.createInvite(
                                            123,
                                            "patient@example.com",
                                            LocalDate.of(1980, 1, 1),
                                            "1234567890",
                                            staff()))
                    .isInstanceOf(PatientPortalException.class)
                    .hasMessageNotContaining(TOKEN)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.TRANSPORT_FAILURE);
        }
    }

    @Nested
    @DisplayName("list")
    class ListInvites {

        @Test
        @DisplayName("should parse every returned invite")
        void shouldParseInvites_whenPortalReturnsAList() {
            String listJson = "[" + INVITE_JSON + "]";
            PatientPortalService service =
                    new PatientPortalService(settings(), new RecordingExchange(200, listJson));

            List<PatientPortalInviteDto> invites = service.listInvites(123, 10, staff());

            assertThat(invites).hasSize(1);
            assertThat(invites.get(0).demographicNo()).isEqualTo(123);
            assertThat(invites.get(0).lastIssuedBy()).isEqualTo("Dr Example");
        }

        @Test
        @DisplayName("should clamp the page size to the portal's maximum")
        void shouldClampLimit_whenCallerAsksForMoreThanPortalAllows() throws Exception {
            RecordingExchange exchange = new RecordingExchange(200, "[]");
            PatientPortalService service = new PatientPortalService(settings(), exchange);

            service.listInvites(123, 5000, staff());

            assertThat(exchange.captured.getUri().getQuery()).isEqualTo("limit=100");
        }

        @Test
        @DisplayName("should ask for at least one record when given a nonsense page size")
        void shouldClampLimit_whenCallerAsksForZero() throws Exception {
            RecordingExchange exchange = new RecordingExchange(200, "[]");
            PatientPortalService service = new PatientPortalService(settings(), exchange);

            service.listInvites(123, 0, staff());

            assertThat(exchange.captured.getUri().getQuery()).isEqualTo("limit=1");
        }
    }

    @Nested
    @DisplayName("resend and revoke")
    class ResendAndRevoke {

        @Test
        @DisplayName("should post to the resend endpoint and return the replacement token")
        void shouldReturnReplacementToken_whenInviteIsResent() {
            RecordingExchange exchange = new RecordingExchange(200, INVITE_JSON);
            PatientPortalService service = new PatientPortalService(settings(), exchange);

            PatientPortalIssuedInviteDto issued = service.resendInvite(7L, staff());

            assertThat(exchange.captured.getMethod()).isEqualTo("POST");
            assertThat(exchange.captured.getRequestUri())
                    .contains("/internal/carlos/invites/7/resend");
            assertThat(issued.inviteToken().expose()).isEqualTo(INVITE_TOKEN);
        }

        @Test
        @DisplayName("should post to the revoke endpoint")
        void shouldPostToRevokeEndpoint_whenInviteIsRevoked() {
            RecordingExchange exchange = new RecordingExchange(200, INVITE_JSON);
            PatientPortalService service = new PatientPortalService(settings(), exchange);

            PatientPortalInviteDto invite = service.revokeInvite(7L, staff());

            assertThat(exchange.captured.getRequestUri())
                    .contains("/internal/carlos/invites/7/revoke");
            assertThat(invite.id()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("secret handling")
    class SecretHandling {

        /**
         * The activation token is a credential: whoever holds it can complete activation as the
         * patient. A record's generated toString would print it into any log line that rendered the
         * result of createInvite.
         */
        @Test
        @DisplayName("should never render the activation token")
        void shouldRedactInviteToken_inToStringOutput() {
            PatientPortalService service =
                    new PatientPortalService(settings(), new RecordingExchange(201, INVITE_JSON));

            PatientPortalIssuedInviteDto issued =
                    service.createInvite(
                            123,
                            "patient@example.com",
                            LocalDate.of(1980, 1, 1),
                            "1234567890",
                            staff());

            assertThat(issued.toString()).doesNotContain(INVITE_TOKEN);
            assertThat(issued.toString()).contains("REDACTED");
        }
    }
}
