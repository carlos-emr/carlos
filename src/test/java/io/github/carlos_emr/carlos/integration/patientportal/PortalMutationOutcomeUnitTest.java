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

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An HTTP success must acknowledge the requested security change and its documented identity. */
@Tag("unit")
@Tag("patient-portal")
class PortalMutationOutcomeUnitTest {
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext(
            "999998", "Synthetic Provider", Set.of(PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE,
                    PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK,
                    PatientPortalStaffContext.PERMISSION_INVITE_MANAGE,
                    PatientPortalStaffContext.PERMISSION_SECRET_MANAGE,
                    PatientPortalStaffContext.PERMISSION_CONTACT_REVIEW));

    private PatientPortalService service(String body) {
        var settings = new PatientPortalSettings("https://portal.example", "clinic",
                PortalSecret.of("synthetic-token"),
                PortalSecret.of(PortalTestKeys.PRIVATE_KEY), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Set.of());
        return new PatientPortalService(settings, request -> new PatientPortalHttpResponse(200, body));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"id\":1,\"clinic_id\":\"clinic\",\"demographic_no\":123,\"locked_at\":null,\"force_password_reset\":false}",
            "{\"id\":1,\"clinic_id\":\"clinic\",\"demographic_no\":123,\"locked_at\":\"2026-08-01T00:00:00Z\",\"force_password_reset\":true}",
            "{\"id\":1,\"locked_at\":null,\"force_password_reset\":true}"})
    void rejectsUnconfirmedUnlock(String body) {
        assertThatThrownBy(() -> service(body).unlockAccount(123, staff))
                .isInstanceOf(PatientPortalException.class)
                .extracting(e -> ((PatientPortalException) e).kind())
                .isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void rejectsOppositeAccessState(boolean enabled) {
        String opposite = enabled ? "disabled" : "active";
        assertThatThrownBy(() -> service("{\"id\":1,\"status\":\"" + opposite
                + "\",\"force_password_reset\":false}").setAccountAccess(123, enabled, "staff_action", staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"pending", "accepted"})
    void rejectsUnconfirmedRevocation(String status) {
        assertThatThrownBy(() -> service(invite(7, status)).revokeInvite(7, staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void rejectsRevocationForDifferentInvite() {
        assertThatThrownBy(() -> service(invite(8, "revoked")).revokeInvite(7, staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void rejectsResendThatDoesNotSupersedeTheSelectedInvite() {
        assertThatThrownBy(() -> service(issuedInvite(8, "pending", 6)).resendInvite(7, staff))
                .isInstanceOf(PatientPortalException.class)
                .extracting(error -> ((PatientPortalException) error).kind())
                .isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE);
    }

    @Test void rejectsUnlockSecretTransitionForDifferentRecordOrState() {
        assertThatThrownBy(
                        () -> service("{\"id\":12,\"status\":\"available\"}")
                                .publishUnlockSecret(11, staff))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(
                        () -> service("{\"id\":11,\"status\":\"pending\"}")
                                .publishUnlockSecret(11, staff))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(
                        () -> service("{\"id\":11,\"status\":\"available\"}")
                                .revokeUnlockSecret(11, "send_failed", staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void rejectsUnlockSecretCreatedForDifferentSourceOrState() {
        assertThatThrownBy(
                        () -> service(secret("other-message", "pending"))
                                .createUnlockSecret(123, "message-1", null, staff))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(
                        () -> service(secret("message-1", "available"))
                                .createUnlockSecret(123, "message-1", null, staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void rejectsContactDecisionThatDoesNotConfirmTheRequest() {
        assertThatThrownBy(
                        () -> service("{\"id\":4,\"status\":\"reviewed\","
                                        + "\"decision\":\"approved\"}")
                                .decideContactReview(3, true, "revision-1", staff))
                .isInstanceOf(PatientPortalException.class);
        assertThatThrownBy(
                        () -> service("{\"id\":3,\"status\":\"reviewed\","
                                        + "\"decision\":\"rejected\"}")
                                .decideContactReview(3, true, "revision-1", staff))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void acceptsConfirmedSecurityChanges() {
        assertThat(service("{\"id\":1,\"clinic_id\":\"clinic\",\"demographic_no\":123,"
                + "\"locked_at\":null,\"force_password_reset\":true}").unlockAccount(123, staff).forcePasswordReset()).isTrue();
        for (boolean enabled : new boolean[] {true, false}) {
            String status = enabled ? "active" : "disabled";
            assertThat(service("{\"id\":1,\"status\":\"" + status + "\",\"force_password_reset\":false}")
                    .setAccountAccess(123, enabled, "staff_action", staff).status()).isEqualTo(status);
        }
        assertThat(service(invite(7, "revoked")).revokeInvite(7, staff).status()).isEqualTo("revoked");
        assertThat(service(issuedInvite(8, "pending", 7)).resendInvite(7, staff)
                .invite().supersedesInviteId()).isEqualTo(7L);
        assertThat(service(secret("message-1", "pending"))
                .createUnlockSecret(123, " message-1 ", null, staff).sourceReference())
                .isEqualTo("message-1");
        assertThat(service("{\"id\":11,\"status\":\"available\"}")
                .publishUnlockSecret(11, staff).status()).isEqualTo("available");
        assertThat(service("{\"id\":11,\"status\":\"revoked\"}")
                .revokeUnlockSecret(11, "send_failed", staff).status()).isEqualTo("revoked");
        assertThat(service("{\"id\":3,\"status\":\"reviewed\","
                        + "\"decision\":\"approved\"}")
                .decideContactReview(3, true, "revision-1", staff).decision())
                .isEqualTo("approved");
    }

    private String invite(long id, String status) {
        return "{\"id\":" + id + ",\"clinic_id\":\"clinic\",\"demographic_no\":123,"
                + "\"status\":\"" + status + "\",\"created_by\":\"Dr Example\","
                + "\"issued_count\":1,\"last_issued_at\":\"2026-08-19T12:00:00Z\","
                + "\"last_issued_by\":\"Dr Example\","
                + "\"expires_at\":\"2026-08-26T12:00:00Z\"}";
    }

    private String issuedInvite(long id, String status, long supersedesInviteId) {
        return invite(id, status).replace(
                "}",
                ",\"supersedes_invite_id\":" + supersedesInviteId
                        + ",\"invite_token\":\"one-time-token\"}");
    }

    private String secret(String sourceReference, String status) {
        return "{\"id\":11,\"created\":true,\"secret\":\"unlock-code\","
                + "\"source_reference\":\"" + sourceReference + "\","
                + "\"status\":\"" + status + "\"}";
    }

    @Test void secretRenderingDoesNotExposeMessageReference() {
        var secret = new PatientPortalUnlockSecretDto(1, true, PortalSecret.of("synthetic-secret"),
                "patient-123-message-456", "pending");
        assertThat(secret.toString()).doesNotContain("synthetic-secret", "patient-123-message-456");
    }
}
