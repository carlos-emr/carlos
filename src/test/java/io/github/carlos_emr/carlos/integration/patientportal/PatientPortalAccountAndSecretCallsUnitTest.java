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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Account, unlock-secret, and contact-review calls.
 *
 * <p>The unlock-secret and contact-review workflows each have an ordering that fails silently when
 * inverted — publishing before a send confirms, confirming a review before the eChart is updated —
 * so the tests here pin what each call does rather than only that it returns.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalService account and secret calls")
class PatientPortalAccountAndSecretCallsUnitTest {

    private static final String TOKEN = "portal-service-token-value-000001";
    private static final String PASSPHRASE = "correct-horse-battery-staple";
    private static final String PATIENT_EMAIL = "patient@example.com";

    /** Replays canned portal replies in order and records every request sent. */
    private static final class ScriptedExchange implements PatientPortalHttpExchange {
        private final List<PatientPortalHttpResponse> replies = new ArrayList<>();
        private final List<ClassicHttpRequest> sent = new ArrayList<>();
        private int index;

        ScriptedExchange reply(int statusCode, String body) {
            replies.add(new PatientPortalHttpResponse(statusCode, body));
            return this;
        }

        @Override
        public PatientPortalHttpResponse send(ClassicHttpRequest request) {
            sent.add(request);
            return replies.get(Math.min(index++, replies.size() - 1));
        }
    }

    private PatientPortalService service(PatientPortalHttpExchange exchange) {
        return new PatientPortalService(
                PatientPortalSettings.fromProperties(
                        Map.of(
                                PatientPortalSettings.BASE_URL_KEY, "https://portal.clinic.example",
                                PatientPortalSettings.CLINIC_ID_KEY, "maplecreek",
                                PatientPortalSettings.SERVICE_TOKEN_KEY, TOKEN)),
                exchange);
    }

    private PatientPortalStaffContext staff(String permission) {
        return new PatientPortalStaffContext("999998", "Dr Example", Set.of(permission));
    }

    private String bodyOf(ClassicHttpRequest request) throws Exception {
        return request.getEntity() == null
                ? ""
                : new String(
                        request.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("account")
    class Account {

        @Test
        @DisplayName("should report the forced reset that follows an unlock")
        void shouldReportForcedReset_whenAccountIsUnlocked() {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(
                                    200,
                                    "{\"id\":5,\"locked_at\":null,\"force_password_reset\":true}");

            PatientPortalAccountAcknowledgementDto account =
                    service(exchange)
                            .unlockAccount(
                                    123,
                                    staff(PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK));

            assertThat(account.forcePasswordReset()).isTrue();
            assertThat(account.locked()).isFalse();
            assertThat(exchange.sent.get(0).getRequestUri())
                    .contains("/internal/carlos/patients/123/unlock");
        }

        @Test
        @DisplayName("should read the full account status")
        void shouldParseAccountStatus_whenPortalReturnsIt() {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(
                                    200,
                                    "{\"id\":5,\"clinic_id\":\"maplecreek\",\"demographic_no\":123,"
                                            + "\"status\":\"disabled\",\"locked\":true,"
                                            + "\"force_password_reset\":false,"
                                            + "\"disabled_at\":\"2026-08-19T12:00:00Z\","
                                            + "\"disabled_reason\":\"staff_action\"}");

            PatientPortalAccountDto account =
                    service(exchange)
                            .findAccount(
                                    123,
                                    staff(PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE));

            assertThat(account.status()).isEqualTo("disabled");
            assertThat(account.locked()).isTrue();
            assertThat(account.disabledAt()).isNotNull();
            assertThat(account.disabledReason()).isEqualTo("staff_action");
        }

        @Test
        @DisplayName("should send the enabled flag and reason when access changes")
        void shouldSendEnabledAndReason_whenAccessIsChanged() throws Exception {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(200, "{\"id\":5,\"status\":\"disabled\","
                                    + "\"force_password_reset\":false}");

            service(exchange)
                    .setAccountAccess(
                            123,
                            false,
                            "left_practice",
                            staff(PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE));

            assertThat(bodyOf(exchange.sent.get(0)))
                    .contains("\"enabled\":false")
                    .contains("\"reason\":\"left_practice\"");
        }
    }

    @Nested
    @DisplayName("unlock secrets")
    class UnlockSecrets {

        @Test
        @DisplayName("should create a passphrase that is pending until published")
        void shouldReturnPendingSecret_whenCreated() throws Exception {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(
                                    201,
                                    "{\"id\":11,\"created\":true,\"secret\":\"" + PASSPHRASE
                                            + "\",\"source_reference\":\"doc-42\","
                                            + "\"status\":\"pending\"}");

            PatientPortalUnlockSecretDto secret =
                    service(exchange)
                            .createUnlockSecret(
                                    123,
                                    "doc-42",
                                    "Lab results",
                                    staff(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE));

            assertThat(secret.status()).isEqualTo("pending");
            assertThat(secret.created()).isTrue();
            assertThat(secret.secret()).isEqualTo(PASSPHRASE);
            assertThat(bodyOf(exchange.sent.get(0)))
                    .contains("\"source_reference\":\"doc-42\"")
                    .contains("\"secret_type\":\"email\"")
                    .contains("\"label\":\"Lab results\"");
        }

        /**
         * A CARLOS retry must not mint a second passphrase for one message, or the patient would be
         * sent one passphrase while the message was encrypted with another.
         */
        @Test
        @DisplayName("should report a reused passphrase when the source reference repeats")
        void shouldReportNotCreated_whenSourceReferenceAlreadyExists() {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(
                                    201,
                                    "{\"id\":11,\"created\":false,\"secret\":\"" + PASSPHRASE
                                            + "\",\"source_reference\":\"doc-42\","
                                            + "\"status\":\"pending\"}");

            PatientPortalUnlockSecretDto secret =
                    service(exchange)
                            .createUnlockSecret(
                                    123,
                                    "doc-42",
                                    null,
                                    staff(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE));

            assertThat(secret.created()).isFalse();
            assertThat(secret.secret()).isEqualTo(PASSPHRASE);
        }

        @Test
        @DisplayName("should publish and revoke against the secret's own endpoints")
        void shouldTargetSecretEndpoints_whenPublishedOrRevoked() throws Exception {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(200, "{\"id\":11,\"status\":\"available\"}")
                            .reply(200, "{\"id\":12,\"status\":\"revoked\"}");
            PatientPortalService service = service(exchange);
            PatientPortalStaffContext staff =
                    staff(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE);

            assertThat(service.publishUnlockSecret(11L, staff).status()).isEqualTo("available");
            assertThat(service.revokeUnlockSecret(12L, "send_failed", staff).status())
                    .isEqualTo("revoked");

            assertThat(exchange.sent.get(0).getRequestUri())
                    .contains("/internal/carlos/unlock-secrets/11/publish");
            assertThat(exchange.sent.get(1).getRequestUri())
                    .contains("/internal/carlos/unlock-secrets/12/revoke");
            assertThat(bodyOf(exchange.sent.get(1))).contains("\"reason\":\"send_failed\"");
        }

        @Test
        @DisplayName("should surface a revoked source reference as a conflict")
        void shouldThrowConflict_whenSourceReferenceWasRevoked() {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(409, "{\"detail\": \"source reference was revoked\"}");

            assertThatThrownBy(
                            () ->
                                    service(exchange)
                                            .createUnlockSecret(
                                                    123,
                                                    "doc-42",
                                                    null,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_SECRET_MANAGE)))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.CONFLICT);
        }

        @Test
        @DisplayName("should never render the passphrase")
        void shouldRedactPassphrase_inToStringOutput() {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(
                                    201,
                                    "{\"id\":11,\"created\":true,\"secret\":\"" + PASSPHRASE
                                            + "\",\"source_reference\":\"doc-42\","
                                            + "\"status\":\"pending\"}");

            PatientPortalUnlockSecretDto secret =
                    service(exchange)
                            .createUnlockSecret(
                                    123,
                                    "doc-42",
                                    null,
                                    staff(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE));

            assertThat(secret.toString()).doesNotContain(PASSPHRASE);
            assertThat(secret.toString()).contains("REDACTED");
            assertThat(secret.toString()).contains("doc-42");
        }
    }

    @Nested
    @DisplayName("contact reviews")
    class ContactReviews {

        private static final String REVIEW_PAGE =
                "{\"items\":[{\"id\":3,\"clinic_id\":\"maplecreek\",\"demographic_no\":123,"
                        + "\"email_before\":\"old@example.com\",\"email_after\":"
                        + "\"patient@example.com\",\"phone_number_before\":null,"
                        + "\"phone_number_after\":null,\"requested_at\":\"2026-08-19T12:00:00Z\","
                        + "\"revision\":\"rev-abc\"}],\"limit\":50,\"offset\":0,\"total\":1,"
                        + "\"next_offset\":null}";

        @Test
        @DisplayName("should read the queue page and its revision tokens")
        void shouldParseReviewPage_whenPortalReturnsItems() {
            ScriptedExchange exchange = new ScriptedExchange().reply(200, REVIEW_PAGE);

            PatientPortalContactReviewPageDto page =
                    service(exchange)
                            .listContactReviews(
                                    50,
                                    0,
                                    staff(PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW));

            assertThat(page.total()).isEqualTo(1);
            assertThat(page.nextOffset()).isNull();
            assertThat(page.items()).hasSize(1);
            assertThat(page.items().get(0).revision()).isEqualTo("rev-abc");
            assertThat(page.items().get(0).emailAfter()).isEqualTo(PATIENT_EMAIL);
        }

        @Test
        @DisplayName("should clamp the page size and offset to what the portal accepts")
        void shouldClampPaging_whenCallerAsksOutOfRange() {
            ScriptedExchange exchange = new ScriptedExchange().reply(200, REVIEW_PAGE);

            service(exchange)
                    .listContactReviews(
                            9999, -5, staff(PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW));

            assertThat(exchange.sent.get(0).getRequestUri()).contains("limit=100&offset=0");
        }

        @Test
        @DisplayName("should echo the exact revision back on the decision")
        void shouldSendRevision_whenDecisionIsRecorded() throws Exception {
            ScriptedExchange exchange =
                    new ScriptedExchange()
                            .reply(200, "{\"id\":3,\"status\":\"reviewed\","
                                    + "\"decision\":\"approved\"}");

            PatientPortalService.PatientPortalContactReviewDecision decision =
                    service(exchange)
                            .decideContactReview(
                                    3L,
                                    true,
                                    "rev-abc",
                                    staff(PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW));

            assertThat(bodyOf(exchange.sent.get(0)))
                    .contains("\"approve\":true")
                    .contains("\"revision\":\"rev-abc\"");
            assertThat(exchange.sent.get(0).getRequestUri())
                    .contains("/internal/carlos/contact-reviews/3/decision");
            assertThat(decision.decision()).isEqualTo("approved");
        }

        /**
         * A stale revision means the request moved underneath the reviewer. Retrying would confirm
         * a change the reviewer never saw, so this has to be distinguishable from a transport
         * error the caller might reasonably retry.
         */
        @Test
        @DisplayName("should surface a stale revision as a conflict, not a retryable failure")
        void shouldThrowConflict_whenRevisionIsStale() {
            ScriptedExchange exchange = new ScriptedExchange().reply(409, "{\"detail\":\"stale\"}");

            assertThatThrownBy(
                            () ->
                                    service(exchange)
                                            .decideContactReview(
                                                    3L,
                                                    true,
                                                    "rev-old",
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_CONTACT_REVIEW)))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.CONFLICT);
        }

        @Test
        @DisplayName("should keep patient contact details out of a rendered review")
        void shouldRedactContactDetails_inToStringOutput() {
            ScriptedExchange exchange = new ScriptedExchange().reply(200, REVIEW_PAGE);

            PatientPortalContactReviewPageDto page =
                    service(exchange)
                            .listContactReviews(
                                    50,
                                    0,
                                    staff(PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW));

            String rendered = page.items().get(0).toString();
            assertThat(rendered).doesNotContain(PATIENT_EMAIL);
            assertThat(rendered).doesNotContain("old@example.com");
            assertThat(rendered).contains("REDACTED");
        }
    }

    @Nested
    @DisplayName("transport")
    class Transport {

        /**
         * An earlier revision threw a {@code RuntimeException} wrapper here, so {@code send}'s
         * {@code catch (IOException)} never ran and the assertion passed for any exception at all —
         * it would have passed with the bearer token in the message. The seam declares {@code throws
         * IOException}, so the wrapper was never needed.
         */
        @Test
        @DisplayName("should map a transport failure and keep the service token out of it")
        void shouldMapTransportFailure_whenTheSocketFails() {
            PatientPortalHttpExchange failing =
                    request -> {
                        throw new IOException("connection reset");
                    };
            PatientPortalService service = service(failing);

            assertThatThrownBy(
                            () ->
                                    service.findAccount(
                                            123,
                                            staff(
                                                    PatientPortalStaffContext
                                                            .PERMISSION_ACCOUNT_MANAGE)))
                    .isInstanceOf(PatientPortalException.class)
                    .hasMessageNotContaining(TOKEN)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.TRANSPORT_FAILURE);
        }

        @Test
        @DisplayName("should keep the patient identifier out of the failure message")
        void shouldOmitDemographicNumber_fromFailureMessage() {
            assertThatThrownBy(
                            () ->
                                    service(new ScriptedExchange().reply(409, "{}"))
                                            .findAccount(
                                                    123,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_ACCOUNT_MANAGE)))
                    .isInstanceOf(PatientPortalException.class)
                    .hasMessageNotContaining("123")
                    .hasMessageContaining("{id}");
        }

        @Test
        @DisplayName("should reject a success status carrying an empty body")
        void shouldMapMalformedResponse_whenSuccessBodyIsEmpty() {
            assertThatThrownBy(
                            () ->
                                    service(new ScriptedExchange().reply(200, ""))
                                            .findAccount(
                                                    123,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_ACCOUNT_MANAGE)))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.MALFORMED_RESPONSE);
        }

        @Test
        @DisplayName("should reject a success body missing a required identifier")
        void shouldMapMalformedResponse_whenRequiredFieldIsAbsent() {
            assertThatThrownBy(
                            () ->
                                    service(new ScriptedExchange().reply(200, "{\"status\":\"ok\"}"))
                                            .findAccount(
                                                    123,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_ACCOUNT_MANAGE)))
                    .isInstanceOf(PatientPortalException.class)
                    .extracting(exception -> ((PatientPortalException) exception).kind())
                    .isEqualTo(Kind.MALFORMED_RESPONSE);
        }

        @Test
        @DisplayName("should carry the portal detail string when it is a plain string")
        void shouldCarryDetail_whenPortalReportsOne() {
            PatientPortalException failure =
                    org.assertj.core.api.Assertions.catchThrowableOfType(
                            PatientPortalException.class,
                            () ->
                                    service(
                                                    new ScriptedExchange()
                                                            .reply(
                                                                    409,
                                                                    "{\"detail\":\"portal account"
                                                                        + " already exists\"}"))
                                            .findAccount(
                                                    123,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_ACCOUNT_MANAGE)));

            assertThat(failure.detail()).isEqualTo("portal account already exists");
        }

        /**
         * The portal's validation layer answers a 422 with a list of objects that can echo the
         * offending input — a patient email or health card number. That shape is dropped rather
         * than parsed, so 422 is the one status with no detail available.
         */
        @Test
        @DisplayName("should withhold a structured validation detail that could echo patient input")
        void shouldWithholdDetail_whenPortalReturnsStructuredValidationErrors() {
            PatientPortalException failure =
                    org.assertj.core.api.Assertions.catchThrowableOfType(
                            PatientPortalException.class,
                            () ->
                                    service(
                                                    new ScriptedExchange()
                                                            .reply(
                                                                    422,
                                                                    "{\"detail\":[{\"loc\":[\"body\","
                                                                        + "\"email\"],\"input\":"
                                                                        + "\"patient@example.com\"}]}"))
                                            .findAccount(
                                                    123,
                                                    staff(
                                                            PatientPortalStaffContext
                                                                    .PERMISSION_ACCOUNT_MANAGE)));

            assertThat(failure.detail()).isNull();
            assertThat(failure.getMessage()).doesNotContain("patient@example.com");
        }
    }
}
