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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Contract regressions for offered times (#3850): what CARLOS sends, and how the portal's answers read. */
@Tag("unit")
@Tag("patient-portal")
class PatientPortalBookingSyncCallsUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PatientPortalStaffContext MANAGE = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE));
    private static final PatientPortalStaffContext SYNC = new PatientPortalStaffContext(
            "-9", "Portal booking sync", Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_SYNC));
    private static final PatientPortalOfferedSlot SLOT = new PatientPortalOfferedSlot(
            "AbCdEfGh_ijklmnop-QRSTUVWXyz012345", OffsetDateTime.parse("2026-10-20T09:30:00-04:00"), 15,
            "in_person", null);
    private static final String PROMPT = """
            {"id":7,"demographic_no":123,"urgency":"soon","appointment_type":"follow_up",
             "suggested_by":null,"state":"sent","created_by":"Synthetic Provider",
             "created_at":"2026-10-01T12:00:00Z","expires_at":"2026-12-01T12:00:00Z",
             "notified_at":null,"read_at":null,"withdrawn_at":null,"withdrawn_by":null,"created":true}
            """;

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

    private static String body(ClassicHttpRequest request) throws Exception {
        return new String(request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String permissions(ClassicHttpRequest request) throws Exception {
        String signed = request.getFirstHeader(PortalStaffAssertionSigner.HEADER).getValue();
        return JSON.readTree(Base64.getUrlDecoder().decode(signed.split("\\.")[0])).get("permissions").toString();
    }

    @Test
    void shouldSendOfferedTimes_withExplicitOffsetAndNothingElse() throws Exception {
        Exchange exchange = new Exchange().reply(201, PROMPT);
        service(exchange).createBookingPrompt(123, new PatientPortalBookingPromptRequest(
                "operation-1", "soon", "follow_up", null, List.of(SLOT)), MANAGE);
        assertThat(JSON.readTree(body(exchange.sent.get(0))).get("offered_slots")).isEqualTo(JSON.readTree(
                "[{\"slot_id\":\"AbCdEfGh_ijklmnop-QRSTUVWXyz012345\",\"starts_at\":\"2026-10-20T09:30:00-04:00\","
                        + "\"duration_minutes\":15,\"visit_mode\":\"in_person\"}]"));
    }

    @Test
    void shouldOmitOfferedTimes_whenNoneAreOffered() throws Exception {
        Exchange exchange = new Exchange().reply(201, PROMPT);
        service(exchange).createBookingPrompt(123,
                new PatientPortalBookingPromptRequest("operation-1", "soon", "follow_up", null), MANAGE);
        assertThat(JSON.readTree(body(exchange.sent.get(0))).has("offered_slots")).isFalse();
    }

    @Test
    void shouldRefuseOffer_whenTimesAreTooManyOrRepeated() {
        List<PatientPortalOfferedSlot> nine = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            nine.add(new PatientPortalOfferedSlot("slot-" + index, SLOT.startsAt(), 15, "in_person", null));
        }
        assertThatThrownBy(() -> new PatientPortalBookingPromptRequest("operation-1", "soon", "follow_up", null, nine))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalBookingPromptRequest("operation-1", "soon", "follow_up", null,
                List.of(SLOT, SLOT))).isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalOfferedSlot("has space", SLOT.startsAt(), 15, "in_person", null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalOfferedSlot("ok", SLOT.startsAt(), 4, "in_person", null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalOfferedSlot("ok", SLOT.startsAt(), 15, "home", null))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldRefuseSyncPermission_whenCombinedWithAnother() {
        assertThatThrownBy(() -> new PatientPortalStaffContext("-9", "Portal booking sync", Set.of(
                PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_SYNC,
                PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE)))
                .isInstanceOf(PortalRequestPreparationException.class)
                .hasMessageContaining("only permission");
    }

    @Test
    void shouldListPendingPicks_signedWithSyncAlone() throws Exception {
        Exchange exchange = new Exchange().reply(200, """
                {"items":[{"prompt_id":7,"choice_id":11,"demographic_no":123,
                  "slot_id":"AbCdEfGh_ijklmnop-QRSTUVWXyz012345","chosen_at":"2026-10-08T15:00:00Z"}],
                 "has_more":true}
                """);
        var page = service(exchange).listPendingBookingChoices(100, SYNC);
        assertThat(exchange.sent.get(0).getRequestUri())
                .isEqualTo("/internal/carlos/booking-prompts/choices?state=pending&limit=100");
        assertThat(permissions(exchange.sent.get(0))).isEqualTo("[\"portal.booking_prompt.sync\"]");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).singleElement().satisfies(choice -> {
            assertThat(choice.promptId()).isEqualTo(7);
            assertThat(choice.choiceId()).isEqualTo(11);
            assertThat(choice.demographicNo()).isEqualTo(123);
            assertThat(choice.slotId()).isEqualTo(SLOT.slotId());
        });
    }

    @Test
    void shouldReportUnavailable_withReplacementTimes() throws Exception {
        Exchange exchange = new Exchange().reply(200, """
                {"prompt_id":7,"choice_id":11,"result":"slot_unavailable","state":"read","recorded":true,
                 "offered_slot_count":3}
                """);
        var result = service(exchange).recordBookingChoiceResult(7, 11, false, List.of(SLOT), SYNC);
        assertThat(exchange.sent.get(0).getRequestUri()).isEqualTo("/internal/carlos/booking-prompts/7/choice-result");
        var sent = JSON.readTree(body(exchange.sent.get(0)));
        assertThat(sent.get("choice_id").asLong()).isEqualTo(11);
        assertThat(sent.get("result").asText()).isEqualTo("slot_unavailable");
        assertThat(sent.get("offered_slots")).hasSize(1);
        assertThat(result.recorded()).isTrue();
        assertThat(result.offeredSlotCount()).isEqualTo(3);
    }

    @Test
    void shouldRefuseReplacements_withBookedResult() {
        assertThatThrownBy(() -> service(new Exchange()).recordBookingChoiceResult(7, 11, true, List.of(SLOT), SYNC))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldCarryWithdrawnDetail_whenPortalRefusesTheResult() {
        Exchange exchange = new Exchange().reply(409, "{\"detail\":\"booking choice was withdrawn\"}");
        assertThatThrownBy(() -> service(exchange).recordBookingChoiceResult(7, 11, true, List.of(), SYNC))
                .isInstanceOfSatisfying(PatientPortalException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.CONFLICT);
                    assertThat(failure.detail()).isEqualTo(PatientPortalService.CHOICE_WITHDRAWN_DETAIL);
                });
    }

    @Test
    void shouldRejectResult_whenPortalEchoesAnotherChoice() {
        Exchange exchange = new Exchange().reply(200, """
                {"prompt_id":7,"choice_id":12,"result":"booked","state":"booked","recorded":true,
                 "offered_slot_count":0}
                """);
        assertThatThrownBy(() -> service(exchange).recordBookingChoiceResult(7, 11, true, List.of(), SYNC))
                .isInstanceOfSatisfying(PatientPortalException.class, failure ->
                        assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }
}
