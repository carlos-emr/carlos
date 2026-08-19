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
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The authenticated envelope every portal call shares.
 *
 * <p>These assert the request CARLOS puts on the wire rather than the response it gets back. The
 * envelope is what the portal authorizes against, so it is the part that has to be provable without
 * standing up a portal.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PatientPortalService")
class PatientPortalServiceUnitTest {

    private static final String TOKEN = "portal-service-token-value-000001";
    private static final String INVITE_PATH = "/internal/carlos/patients/123/invites";

    private PatientPortalService service() {
        return new PatientPortalService(
                PatientPortalSettings.fromProperties(
                        Map.of(
                                PatientPortalSettings.BASE_URL_KEY,
                                "https://portal.clinic.example",
                                PatientPortalSettings.CLINIC_ID_KEY,
                                "maplecreek",
                                PatientPortalSettings.SERVICE_TOKEN_KEY,
                                TOKEN)));
    }

    private PatientPortalStaffContext staff() {
        return new PatientPortalStaffContext(
                "999998",
                "Dr Example",
                Set.of(PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));
    }

    private String header(ClassicHttpRequest request, String name) {
        return request.getFirstHeader(name) == null
                ? null
                : request.getFirstHeader(name).getValue();
    }

    @Nested
    @DisplayName("authenticated envelope")
    class AuthenticatedEnvelope {

        @Test
        @DisplayName("should present the service token as a bearer credential")
        void shouldSendBearerToken_onEveryRequest() {
            ClassicHttpRequest request =
                    service().buildRequest("POST", INVITE_PATH, "{}", staff());

            assertThat(header(request, PatientPortalService.AUTHORIZATION_HEADER))
                    .isEqualTo("Bearer " + TOKEN);
        }

        @Test
        @DisplayName("should send all four CARLOS identity headers")
        void shouldSendIdentityHeaders_forTheActingProvider() {
            ClassicHttpRequest request =
                    service().buildRequest("POST", INVITE_PATH, "{}", staff());

            assertThat(header(request, PatientPortalService.PROVIDER_ID_HEADER)).isEqualTo("999998");
            assertThat(header(request, PatientPortalService.PROVIDER_NAME_HEADER))
                    .isEqualTo("Dr Example");
            assertThat(header(request, PatientPortalService.CLINIC_ID_HEADER))
                    .isEqualTo("maplecreek");
            assertThat(header(request, PatientPortalService.PERMISSIONS_HEADER))
                    .isEqualTo("portal.invite.manage");
        }

        /**
         * The portal rejects a request whose clinic id does not match its own configuration. Taking
         * that value from configuration rather than from the caller means a caller cannot even
         * attempt to act for another clinic, so the portal's check is a backstop rather than the
         * only control.
         */
        @Test
        @DisplayName("should take the clinic id from configuration, not from the caller")
        void shouldUseConfiguredClinicId_ratherThanCallerSuppliedValue() {
            ClassicHttpRequest request =
                    service().buildRequest("GET", INVITE_PATH, null, staff());

            assertThat(header(request, PatientPortalService.CLINIC_ID_HEADER))
                    .isEqualTo("maplecreek");
        }

        @Test
        @DisplayName("should send only the permissions the provider actually holds")
        void shouldSendGrantedPermissionsOnly_whenProviderHoldsASubset() {
            PatientPortalStaffContext limited =
                    new PatientPortalStaffContext(
                            "999998",
                            "Dr Example",
                            Set.of(
                                    PatientPortalStaffContext.PERMISSION_ACCOUNT_UNLOCK,
                                    PatientPortalStaffContext.PERMISSION_INVITE_MANAGE));

            ClassicHttpRequest request = service().buildRequest("GET", INVITE_PATH, null, limited);

            assertThat(header(request, PatientPortalService.PERMISSIONS_HEADER))
                    .isEqualTo("portal.account.unlock,portal.invite.manage");
            assertThat(header(request, PatientPortalService.PERMISSIONS_HEADER))
                    .doesNotContain(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE);
        }

        @Test
        @DisplayName("should target the configured portal origin")
        void shouldResolveRequestUri_againstConfiguredBaseUrl() throws Exception {
            ClassicHttpRequest request =
                    service().buildRequest("POST", INVITE_PATH, "{}", staff());

            assertThat(request.getUri().toString())
                    .isEqualTo("https://portal.clinic.example/internal/carlos/patients/123/invites");
        }

        @Test
        @DisplayName("should omit a body when none is supplied")
        void shouldSendNoEntity_whenBodyIsNull() {
            ClassicHttpRequest request = service().buildRequest("GET", INVITE_PATH, null, staff());

            assertThat(request.getEntity()).isNull();
        }

        @Test
        @DisplayName("should send a JSON body when one is supplied")
        void shouldSendJsonEntity_whenBodyIsPresent() {
            ClassicHttpRequest request =
                    service().buildRequest("POST", INVITE_PATH, "{\"a\":1}", staff());

            assertThat(request.getEntity()).isNotNull();
            assertThat(request.getEntity().getContentType()).contains("application/json");
        }
    }

    @Nested
    @DisplayName("fail closed on construction")
    class FailClosedOnConstruction {

        @Test
        @DisplayName("should refuse to build without settings")
        void shouldThrow_whenSettingsAreAbsent() {
            assertThatThrownBy(() -> new PatientPortalService(null))
                    .isInstanceOf(PatientPortalConfigurationException.class);
        }
    }

    @Nested
    @DisplayName("status mapping")
    class StatusMapping {

        @Test
        @DisplayName("should map each documented portal status to its outcome")
        void shouldMapStatus_toDocumentedKind() {
            assertThat(PatientPortalException.kindForStatus(400)).isEqualTo(Kind.BAD_REQUEST);
            assertThat(PatientPortalException.kindForStatus(403)).isEqualTo(Kind.PERMISSION_DENIED);
            assertThat(PatientPortalException.kindForStatus(409)).isEqualTo(Kind.CONFLICT);
            assertThat(PatientPortalException.kindForStatus(422))
                    .isEqualTo(Kind.VALIDATION_FAILED);
            assertThat(PatientPortalException.kindForStatus(429)).isEqualTo(Kind.THROTTLED);
            assertThat(PatientPortalException.kindForStatus(500))
                    .isEqualTo(Kind.UNEXPECTED_STATUS);
        }

        /**
         * The portal answers a bad service token, missing identity headers, or a clinic mismatch
         * with the same 404 it uses for an unknown record, so an unauthenticated caller cannot
         * probe which. CARLOS must not present this to staff as "no such patient".
         */
        @Test
        @DisplayName("should treat 404 as ambiguous between missing and unauthenticated")
        void shouldMapNotFound_toAmbiguousKind() {
            assertThat(PatientPortalException.kindForStatus(404))
                    .isEqualTo(Kind.NOT_FOUND_OR_UNAUTHENTICATED);
        }
    }

    @Nested
    @DisplayName("secret handling")
    class SecretHandling {

        @Test
        @DisplayName("should keep the service token out of failure messages")
        void shouldOmitServiceToken_fromExceptionMessage() {
            PatientPortalException exception =
                    new PatientPortalException(Kind.PERMISSION_DENIED, 403, INVITE_PATH);

            assertThat(exception.getMessage()).doesNotContain(TOKEN);
            assertThat(exception.getMessage()).contains(INVITE_PATH);
            assertThat(exception.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("should report a transport failure without a status")
        void shouldReportZeroStatus_whenNoResponseArrived() {
            PatientPortalException exception =
                    new PatientPortalException(INVITE_PATH, new java.io.IOException("refused"));

            assertThat(exception.kind()).isEqualTo(Kind.TRANSPORT_FAILURE);
            assertThat(exception.statusCode()).isZero();
            assertThat(exception.getMessage()).doesNotContain(TOKEN);
        }
    }
}
