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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Contract regressions for patient scope, confirmed retries, and withdrawal acknowledgement. */
@Tag("unit")
@Tag("patient-portal")
class PatientPortalBookingPromptCallsUnitTest {
    private static final String PROMPT = """
            {"id":7,"demographic_no":123,"urgency":"soon","appointment_type":"follow_up",
             "suggested_by":null,"state":"sent","created_by":"Synthetic Provider",
             "created_at":"2026-10-01T12:00:00Z","expires_at":"2026-12-01T12:00:00Z",
             "notified_at":null,"read_at":null,"withdrawn_at":null,"withdrawn_by":null}
            """;
    private static final PatientPortalStaffContext STAFF = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE));
    private static final PatientPortalBookingPromptRequest REQUEST =
            new PatientPortalBookingPromptRequest("operation-1", "soon", "follow_up", null);

    private static final class Exchange implements PatientPortalHttpExchange {
        private final List<PatientPortalHttpResponse> replies = new ArrayList<>();
        private final List<ClassicHttpRequest> sent = new ArrayList<>();
        Exchange reply(int status, String body) {
            replies.add(new PatientPortalHttpResponse(status, body));
            return this;
        }
        @Override
        public PatientPortalHttpResponse send(ClassicHttpRequest request) {
            if (sent.size() >= replies.size()) {
                throw new AssertionError("unscripted portal call");
            }
            sent.add(request);
            return replies.get(sent.size() - 1);
        }
    }

    private PatientPortalService service(Exchange exchange) {
        return new PatientPortalService(PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "test-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN)), exchange);
    }

    private String created(boolean created) {
        String prompt = PROMPT.strip();
        return prompt.substring(0, prompt.length() - 1) + ",\"created\":" + created + "}";
    }

    private String body(ClassicHttpRequest request) throws Exception {
        return new String(request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void shouldConfirmOriginalPrompt_whenCreationIsRetried() throws Exception {
        Exchange exchange = new Exchange().reply(201, created(true)).reply(201, created(false));
        PatientPortalService service = service(exchange);
        assertThat(service.createBookingPrompt(123, REQUEST, STAFF).created()).isTrue();
        assertThat(service.createBookingPrompt(123, REQUEST, STAFF).created()).isFalse();
        assertThat(exchange.sent).hasSize(2);
        assertThat(exchange.sent.get(0).getMethod()).isEqualTo("POST");
        assertThat(exchange.sent.get(0).getRequestUri()).isEqualTo("/internal/carlos/patients/123/booking-prompts");
        assertThat(body(exchange.sent.get(0))).isEqualTo(body(exchange.sent.get(1)));
        assertThat(new ObjectMapper().readTree(body(exchange.sent.get(0)))).isEqualTo(new ObjectMapper().readTree(
                "{\"operation_id\":\"operation-1\",\"urgency\":\"soon\",\"appointment_type\":\"follow_up\",\"suggested_by\":null}"));
        String signed = exchange.sent.get(0).getFirstHeader(PortalStaffAssertionSigner.HEADER).getValue();
        var claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(signed.split("\\.")[0]));
        assertThat(claims.get("permissions").toString()).isEqualTo("[\"portal.booking_prompt.manage\"]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "vocabulary", "urgency", "suggested_by", "status", "flag", "new_state"})
    void shouldRejectUnconfirmedCreation_whenReplyDoesNotMatch(String mismatch) {
        String reply = created(true);
        int status = 201;
        switch (mismatch) {
            case "patient" -> reply = reply.replace("123", "456");
            case "vocabulary" -> reply = reply.replace("follow_up", "annual_exam");
            case "urgency" -> reply = reply.replace("\"urgency\":\"soon\"", "\"urgency\":\"routine\"");
            case "suggested_by" -> reply = reply.replace("\"suggested_by\":null", "\"suggested_by\":\"Dr Example\"");
            case "status" -> status = 200;
            case "flag" -> reply = reply.replace("true", "\"true\"");
            case "new_state" -> reply = reply.replace("sent", "read");
            default -> throw new AssertionError(mismatch);
        }
        PatientPortalService service = service(new Exchange().reply(status, reply));
        assertThatThrownBy(() -> service.createBookingPrompt(123, REQUEST, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }

    @Test
    void shouldRejectMixedPatientListing_whenAnEntryHasForeignScope() {
        PatientPortalService service = service(new Exchange().reply(200,
                "[" + PROMPT + "," + PROMPT.replace("123", "456") + "]"));
        assertThatThrownBy(() -> service.listBookingPrompts(123, STAFF))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test
    void shouldReadStatusesAndTimestamps_whenListingPrompts() {
        Exchange exchange = new Exchange().reply(200, "[" + PROMPT + "]");
        PatientPortalBookingPromptDto prompt = service(exchange).listBookingPrompts(123, STAFF).getFirst();
        assertThat(exchange.sent.getFirst().getMethod()).isEqualTo("GET");
        assertThat(exchange.sent.getFirst().getRequestUri()).isEqualTo("/internal/carlos/patients/123/booking-prompts");
        assertThat(prompt.id()).isEqualTo(7);
        assertThat(prompt.demographicNo()).isEqualTo(123);
        assertThat(prompt.urgency()).isEqualTo("soon");
        assertThat(prompt.appointmentType()).isEqualTo("follow_up");
        assertThat(prompt.state()).isEqualTo("sent");
        assertThat(prompt.createdAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(prompt.expiresAt()).isEqualTo(Instant.parse("2026-12-01T12:00:00Z"));
        assertThat(prompt.notifiedAt()).isNull();
        assertThat(prompt.readAt()).isNull();
        assertThat(prompt.withdrawnAt()).isNull();
        assertThat(prompt.toString()).doesNotContain("123", "Synthetic Provider", "follow_up");
    }

    @Test
    void shouldReadNoticeAndWithdrawalTimes_whenListingWithdrawnPrompt() {
        String withdrawn = PROMPT.replace("\"state\":\"sent\"", "\"state\":\"withdrawn\"")
                .replace("\"notified_at\":null", "\"notified_at\":\"2026-10-01T12:05:00Z\"")
                .replace("\"read_at\":null", "\"read_at\":\"2026-10-02T08:00:00Z\"")
                .replace("\"withdrawn_at\":null", "\"withdrawn_at\":\"2026-10-03T09:30:00Z\"")
                .replace("\"withdrawn_by\":null", "\"withdrawn_by\":\"Synthetic Provider\"");
        PatientPortalBookingPromptDto prompt = service(new Exchange().reply(200, "[" + withdrawn + "]"))
                .listBookingPrompts(123, STAFF).getFirst();
        assertThat(prompt.state()).isEqualTo("withdrawn");
        assertThat(prompt.notifiedAt()).isEqualTo(Instant.parse("2026-10-01T12:05:00Z"));
        assertThat(prompt.readAt()).isEqualTo(Instant.parse("2026-10-02T08:00:00Z"));
        assertThat(prompt.withdrawnAt()).isEqualTo(Instant.parse("2026-10-03T09:30:00Z"));
        assertThat(prompt.withdrawnBy()).isEqualTo("Synthetic Provider");
    }

    @ParameterizedTest
    @ValueSource(strings = {"urgency", "appointment_type", "state"})
    void shouldRejectListing_whenAnEntryUsesUnknownVocabulary(String field) {
        String reply = switch (field) {
            case "urgency" -> PROMPT.replace("\"urgency\":\"soon\"", "\"urgency\":\"whenever\"");
            case "appointment_type" -> PROMPT.replace("\"follow_up\"", "\"spa_day\"");
            case "state" -> PROMPT.replace("\"state\":\"sent\"", "\"state\":\"archived\"");
            default -> throw new AssertionError(field);
        };
        PatientPortalService service = service(new Exchange().reply(200, "[" + reply + "]"));
        assertThatThrownBy(() -> service.listBookingPrompts(123, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "patient", "state"})
    void shouldRefuseWrongWithdrawal_whenAcknowledgementIsUnrelated(String mismatch) {
        String reply = PROMPT.replace("\"state\":\"sent\"", "\"state\":\"withdrawn\"");
        reply = switch (mismatch) {
            case "id" -> reply.replace("\"id\":7", "\"id\":8");
            case "patient" -> reply.replace("\"demographic_no\":123", "\"demographic_no\":456");
            // A well-formed prompt that is simply not withdrawn: only the state check can refuse it.
            case "state" -> reply.replace("\"state\":\"withdrawn\"", "\"state\":\"sent\"");
            default -> throw new AssertionError(mismatch);
        };
        PatientPortalService service = service(new Exchange().reply(200, reply));
        assertThatThrownBy(() -> service.withdrawBookingPrompt(123, 7, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }

    @Test
    void shouldConfirmWithdrawal_whenPromptAndPatientMatch() {
        Exchange exchange = new Exchange().reply(200, PROMPT.replace("sent", "withdrawn"));
        assertThat(service(exchange).withdrawBookingPrompt(123, 7, STAFF).state()).isEqualTo("withdrawn");
        assertThat(exchange.sent.getFirst().getMethod()).isEqualTo("POST");
        assertThat(exchange.sent.getFirst().getRequestUri()).isEqualTo("/internal/carlos/booking-prompts/7/withdraw");
    }

    @Test
    void shouldProtectRequestDetails_whenRenderedInLogs() {
        var request = new PatientPortalBookingPromptRequest("operation-secret", "soon", "follow_up", "Dr Example");
        assertThat(request.toString()).doesNotContain("operation-secret", "Dr Example");
        assertThatThrownBy(() -> new PatientPortalBookingPromptRequest("bad/id", "soon", "follow_up", null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalBookingPromptRequest("valid", "urgent", "follow_up", null))
                .isInstanceOf(PortalRequestPreparationException.class);
    }
}
