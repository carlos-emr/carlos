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
import java.time.LocalDate;
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

/**
 * Contract regressions for the payment request client calls: the request is closed vocabulary and
 * whole cents, a retry is confirmed rather than duplicated, and a reply that changes an amount,
 * patient or state is refused as malformed.
 */
@Tag("unit")
@Tag("patient-portal")
class PatientPortalPaymentRequestCallsUnitTest {
    private static final String PAYMENT = """
            {"id":7,"demographic_no":123,"invoice_ref":"inv-7f3a9c","category":"uninsured_service",
             "payment_method":"clinic_instructions","currency":"CAD","amount_cents":4500,"tax_cents":518,
             "due_on":"2026-12-20","state":"sent","created_by":"Synthetic Provider",
             "created_at":"2026-11-27T12:00:00Z","expires_at":"2027-02-25T12:00:00Z",
             "read_at":null,"paid_at":null,"withdrawn_at":null,"withdrawn_by":null}
            """;
    private static final PatientPortalStaffContext STAFF = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_PAYMENT_MANAGE));
    private static final PatientPortalPaymentRequestRequest REQUEST = new PatientPortalPaymentRequestRequest(
            "operation-1", "inv-7f3a9c", "uninsured_service", 4500, 518, LocalDate.parse("2026-12-20"));

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
        String payment = PAYMENT.strip();
        return payment.substring(0, payment.length() - 1) + ",\"created\":" + created + "}";
    }

    private String body(ClassicHttpRequest request) throws Exception {
        return new String(request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "", "a/b", "ünï"})
    void shouldRefuseTheRequest_whenTheInvoiceReferenceIsNotOpaque(String reference) {
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", reference, "other", 100, 0, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldRefuseTheRequest_whenTheInvoiceReferenceIsTooLong() {
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "x".repeat(65), "other", 100, 0, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 10_000_001})
    void shouldRefuseTheRequest_whenTheAmountIsOutOfRange(long amount) {
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "inv-1", "other", amount, 0, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldRefuseTheRequest_whenTaxExceedsTheAmountOrIsNegative() {
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "inv-1", "other", 100, 101, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "inv-1", "other", 100, -1, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldRefuseTheRequest_whenTheCategoryOrOperationOrDueDateIsInvalid() {
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "inv-1", "free text about a diagnosis", 100, 0, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "bad operation", "inv-1", "other", 100, 0, LocalDate.parse("2026-12-20")))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThatThrownBy(() -> new PatientPortalPaymentRequestRequest(
                "operation-1", "inv-1", "other", 100, 0, null))
                .isInstanceOf(PortalRequestPreparationException.class);
    }

    @Test
    void shouldNotRenderAmountsOrReferences_whenTheRequestOrReplyIsPrinted() {
        assertThat(REQUEST.toString()).doesNotContain("4500", "inv-7f3a9c", "operation-1");
        PatientPortalPaymentRequestDto dto = PatientPortalPaymentRequestDto.fromJson(parse(PAYMENT));
        assertThat(dto.toString()).doesNotContain("4500", "inv-7f3a9c", "Synthetic Provider", "123");
    }

    private static com.fasterxml.jackson.databind.JsonNode parse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception invalid) {
            throw new AssertionError(invalid);
        }
    }

    @Test
    void shouldSendOnlyTheClosedFieldsSignedWithThePaymentPermission_whenCreating() throws Exception {
        Exchange exchange = new Exchange().reply(201, created(true));

        PatientPortalPaymentRequestDto.Creation result = service(exchange).createPaymentRequest(123, REQUEST, STAFF);

        assertThat(result.created()).isTrue();
        assertThat(result.request().amountCents()).isEqualTo(4500);
        assertThat(result.request().taxCents()).isEqualTo(518);
        assertThat(result.request().dueOn()).isEqualTo(LocalDate.parse("2026-12-20"));
        assertThat(result.request().createdAt()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
        var sent = exchange.sent.getFirst();
        assertThat(sent.getMethod()).isEqualTo("POST");
        assertThat(sent.getRequestUri()).isEqualTo("/internal/carlos/patients/123/payment-requests");
        assertThat(new ObjectMapper().readTree(body(sent))).isEqualTo(new ObjectMapper().readTree(
                "{\"operation_id\":\"operation-1\",\"invoice_ref\":\"inv-7f3a9c\",\"category\":\"uninsured_service\","
                        + "\"payment_method\":\"clinic_instructions\",\"currency\":\"CAD\",\"amount_cents\":4500,"
                        + "\"tax_cents\":518,\"due_on\":\"2026-12-20\"}"));
        String assertion = sent.getFirstHeader(PortalStaffAssertionSigner.HEADER).getValue();
        var claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[0]));
        assertThat(claims.get("permissions").toString()).isEqualTo("[\"portal.payment.manage\"]");
    }

    @Test
    void shouldAcceptOnlyMatchingReceipts_forTheSharedSettlementContract() throws Exception {
        var mapper = new ObjectMapper();
        var contract = mapper.readTree(getClass().getResource("/patientportal/payment-settlement-contract.json"));
        var request = contract.get("request");
        var receipt = contract.get("receipt");
        String operation = request.get("operation_id").asText();
        String reference = request.get("invoice_ref").asText();
        int patient = receipt.get("demographic_no").intValue();
        var job = PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.PAYMENT_SETTLEMENT,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-settlement"));
        Exchange exchange = new Exchange().reply(200, receipt.toString());
        assertThat(service(exchange).reportPaidOperation(patient, operation, reference, null, job))
                .isEqualTo(receipt.get("request_id").longValue());
        var sent = exchange.sent.getFirst();
        assertThat(sent.getMethod()).isEqualTo(contract.get("method").asText());
        assertThat(sent.getRequestUri()).isEqualTo(contract.get("path").asText());
        assertThat(mapper.readTree(body(sent))).isEqualTo(request);
        assertThat(sent.containsHeader(PortalStaffAssertionSigner.HEADER)).isFalse();
        String assertion = sent.getFirstHeader(PortalJobAssertionSigner.HEADER).getValue();
        var claims = mapper.readTree(Base64.getUrlDecoder().decode(assertion.substring(0, assertion.indexOf('.'))));
        assertThat(claims.get("job")).isEqualTo(contract.get("job"));
        assertThat(claims.get("permission")).isEqualTo(contract.get("permission"));

        for (String field : List.of("demographic_no", "request_id", "invoice_ref", "operation_id", "state")) {
            var wrong = (com.fasterxml.jackson.databind.node.ObjectNode) receipt.deepCopy();
            if (wrong.get(field).isNumber()) {
                wrong.put(field, 999);
            } else {
                wrong.put(field, "other");
            }
            Exchange bad = new Exchange().reply(200, wrong.toString());
            assertThatThrownBy(() -> service(bad).reportPaidOperation(patient, operation, reference, 1L, job))
                    .isInstanceOf(PatientPortalException.class);
        }
        Exchange denied = new Exchange();
        assertThatThrownBy(() -> service(denied).reportPaidOperation(patient, operation, reference, null, null))
                .isInstanceOf(PortalRequestPreparationException.class);
        assertThat(denied.sent).isEmpty();
    }

    @Test
    void shouldLookUpTheOriginalOperationWithoutCreatingANewRequest() throws Exception {
        Exchange exchange = new Exchange().reply(201, created(false));
        assertThat(service(exchange).lookupPaymentOperation(123, REQUEST, STAFF).id()).isEqualTo(7);
        assertThat(body(exchange.sent.getFirst())).contains("\"lookup_only\":true");
        Exchange wrong = new Exchange().reply(201, created(true));
        assertThatThrownBy(() -> service(wrong).lookupPaymentOperation(123, REQUEST, STAFF))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test
    void shouldConfirmTheOriginalRequest_whenCreationIsRetried() throws Exception {
        Exchange exchange = new Exchange().reply(201, created(true)).reply(201, created(false));
        PatientPortalService service = service(exchange);

        assertThat(service.createPaymentRequest(123, REQUEST, STAFF).created()).isTrue();
        assertThat(service.createPaymentRequest(123, REQUEST, STAFF).created()).isFalse();

        assertThat(body(exchange.sent.get(0))).isEqualTo(body(exchange.sent.get(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "amount", "tax", "category", "reference", "due", "vocabulary", "method",
            "status", "flag", "new_state"})
    void shouldRejectUnconfirmedCreation_whenReplyDoesNotMatch(String mismatch) {
        String reply = created(true);
        int status = 201;
        switch (mismatch) {
            case "patient" -> reply = reply.replace("\"demographic_no\":123", "\"demographic_no\":456");
            case "amount" -> reply = reply.replace("\"amount_cents\":4500", "\"amount_cents\":4501");
            case "tax" -> reply = reply.replace("\"tax_cents\":518", "\"tax_cents\":519");
            case "category" -> reply = reply.replace("uninsured_service", "other");
            case "reference" -> reply = reply.replace("inv-7f3a9c", "inv-other");
            case "due" -> reply = reply.replace("2026-12-20", "2026-12-21");
            case "vocabulary" -> reply = reply.replace("\"currency\":\"CAD\"", "\"currency\":\"USD\"");
            case "method" -> reply = reply.replace("clinic_instructions", "hosted_checkout");
            case "status" -> status = 200;
            case "flag" -> reply = reply.replace("\"created\":true", "\"created\":\"true\"");
            case "new_state" -> reply = reply.replace("\"state\":\"sent\"", "\"state\":\"read\"");
            default -> throw new AssertionError(mismatch);
        }
        PatientPortalService service = service(new Exchange().reply(status, reply));

        assertThatThrownBy(() -> service.createPaymentRequest(123, REQUEST, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }

    @Test
    void shouldReadTheStateAndTimes_whenListingRequests() {
        String paid = PAYMENT.replace("\"state\":\"sent\"", "\"state\":\"paid\"")
                .replace("\"read_at\":null", "\"read_at\":\"2026-11-28T08:00:00Z\"")
                .replace("\"paid_at\":null", "\"paid_at\":\"2026-11-29T09:30:00Z\"");
        Exchange exchange = new Exchange().reply(200, "[" + paid + "]");

        PatientPortalPaymentRequestDto request = service(exchange).listPaymentRequests(123, STAFF).getFirst();

        assertThat(exchange.sent.getFirst().getMethod()).isEqualTo("GET");
        assertThat(exchange.sent.getFirst().getRequestUri()).isEqualTo("/internal/carlos/patients/123/payment-requests");
        assertThat(request.state()).isEqualTo("paid");
        assertThat(request.readAt()).isEqualTo(Instant.parse("2026-11-28T08:00:00Z"));
        assertThat(request.paidAt()).isEqualTo(Instant.parse("2026-11-29T09:30:00Z"));
        assertThat(request.withdrawnAt()).isNull();
    }

    @Test
    void shouldRejectAMixedPatientListing_whenAnEntryHasForeignScope() {
        PatientPortalService service = service(new Exchange().reply(200,
                "[" + PAYMENT + "," + PAYMENT.replace("\"demographic_no\":123", "\"demographic_no\":456") + "]"));

        assertThatThrownBy(() -> service.listPaymentRequests(123, STAFF)).isInstanceOf(PatientPortalException.class);
    }

    @Test
    void shouldRejectANonArrayListing() {
        PatientPortalService service = service(new Exchange().reply(200, PAYMENT));

        assertThatThrownBy(() -> service.listPaymentRequests(123, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE));
    }

    @Test
    void shouldAcknowledgeTheWithdrawal_onlyForTheSelectedRequestAndPatient() {
        String withdrawn = PAYMENT.replace("\"state\":\"sent\"", "\"state\":\"withdrawn\"");

        PatientPortalPaymentRequestDto request = service(new Exchange().reply(200, withdrawn))
                .withdrawPaymentRequest(123, 7, STAFF);
        assertThat(request.state()).isEqualTo("withdrawn");

        assertThatThrownBy(() -> service(new Exchange().reply(200, withdrawn)).withdrawPaymentRequest(123, 8, STAFF))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(() -> service(new Exchange().reply(200, withdrawn)).withdrawPaymentRequest(124, 7, STAFF))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(() -> service(new Exchange().reply(200, PAYMENT)).withdrawPaymentRequest(123, 7, STAFF))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test
    void shouldPostTheWithdrawalToTheRequestPath() {
        Exchange exchange = new Exchange().reply(200, PAYMENT.replace("\"state\":\"sent\"", "\"state\":\"withdrawn\""));

        service(exchange).withdrawPaymentRequest(123, 7, STAFF);

        assertThat(exchange.sent.getFirst().getMethod()).isEqualTo("POST");
        assertThat(exchange.sent.getFirst().getRequestUri()).isEqualTo("/internal/carlos/payment-requests/7/withdraw");
    }

    @Test
    void shouldConfirmThePayment_onlyWhenThePortalReportsItPaid() {
        String paid = PAYMENT.replace("\"state\":\"sent\"", "\"state\":\"paid\"");
        Exchange exchange = new Exchange().reply(200, paid);

        PatientPortalPaymentRequestDto request = service(exchange).markPaymentRequestPaid(123, 7, STAFF);

        assertThat(request.state()).isEqualTo("paid");
        assertThat(exchange.sent.getFirst().getRequestUri()).isEqualTo("/internal/carlos/payment-requests/7/paid");
        assertThatThrownBy(() -> service(new Exchange().reply(200, PAYMENT)).markPaymentRequestPaid(123, 7, STAFF))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(() -> service(new Exchange().reply(200, paid)).markPaymentRequestPaid(124, 7, STAFF))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test
    void shouldKeepThePortalsRefusalDetails_whenTheyAreOnTheSafeList() {
        PatientPortalService conflict = service(new Exchange().reply(409,
                "{\"detail\":\"" + PatientPortalService.PAYMENT_REQUEST_STATE_CONFLICT_DETAIL + "\"}"));

        assertThatThrownBy(() -> conflict.markPaymentRequestPaid(123, 7, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.CONFLICT);
                    assertThat(failure.detail()).isEqualTo(PatientPortalService.PAYMENT_REQUEST_STATE_CONFLICT_DETAIL);
                });
        PatientPortalService absent = service(new Exchange().reply(404,
                "{\"detail\":\"" + PatientPortalException.ACCOUNT_NOT_FOUND_DETAIL + "\"}"));
        assertThatThrownBy(() -> absent.createPaymentRequest(123, REQUEST, STAFF))
                .isInstanceOfSatisfying(PatientPortalException.class,
                        failure -> assertThat(failure.isAccountAbsent()).isTrue());
    }

    @Test
    void shouldAcceptThePaymentPermissionInAStaffContext() {
        assertThat(STAFF.permissions()).containsExactly("portal.payment.manage");
    }
}
