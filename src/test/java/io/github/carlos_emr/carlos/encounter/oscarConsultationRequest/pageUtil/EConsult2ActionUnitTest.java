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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EConsult2Action redirects")
@Tag("unit")
@Tag("security")
class EConsult2ActionUnitTest {

    @Test
    @DisplayName("should build frontend redirect under configured eConsult base")
    void shouldBuildFrontendRedirect_underConfiguredBase() {
        String redirect = EConsult2Action.frontendRedirectUrl(
                "https://econsult.example/app",
                "provider+clinic@example.com",
                "delegate@example.com",
                "draft",
                "123");

        assertThat(redirect).isEqualTo(
                "https://econsult.example/app/?oneid_email=provider%2Bclinic%40example.com"
                        + "&delegate_oneid_email=delegate%40example.com#!/draft?patient_id=123");
    }

    @Test
    @DisplayName("should build login redirect with encoded return URL and separate query parameters")
    void shouldBuildLoginRedirect_withEncodedReturnUrlAndLoginStart() {
        String redirect = EConsult2Action.loginRedirectUrl(
                "https://econsult.example/sso/",
                "https://emr.example/carlos/econsultSSOLogin",
                1770000000L);

        assertThat(redirect).isEqualTo(
                "https://econsult.example/sso/SAML2/login"
                        + "?oscarReturnURL=https%3A%2F%2Femr.example%2Fcarlos%2FeconsultSSOLogin"
                        + "&loginStart=1770000000")
                .doesNotContain("?loginStart=");
    }

    @Test
    @DisplayName("should reject unsafe eConsult base URLs")
    void shouldRejectConfiguredBase_whenUnsafe() {
        assertThatThrownBy(() -> EConsult2Action.loginRedirectUrl(
                "javascript:alert(1)",
                "https://emr.example/carlos/econsultSSOLogin",
                1770000000L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EConsult2Action.frontendRedirectUrl(
                "https://econsult.example@evil.example/app",
                "provider@example.com",
                null,
                "draft",
                "123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EConsult2Action.loginRedirectUrl(
                "https://econsult.example/sso?next=https://evil.example",
                "https://emr.example/carlos/econsultSSOLogin",
                1770000000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject task values that could change redirect shape")
    void shouldRejectTask_whenRedirectShapeCouldChange() {
        assertThat(EConsult2Action.isValidTask("patientSummary")).isTrue();
        assertThat(EConsult2Action.isValidTask("referral/draft-1")).isTrue();
        assertThat(EConsult2Action.isValidTask("../admin")).isFalse();
        assertThat(EConsult2Action.isValidTask("//evil.example")).isFalse();
        assertThat(EConsult2Action.isValidTask("http://evil.example")).isFalse();
    }

    @Test
    @DisplayName("should handle optional eConsult redirect parameters")
    void shouldHandleOptionalParameters_whenBuildingFrontendRedirect() {
        String redirect = EConsult2Action.frontendRedirectUrl(
                "HTTPS://ECONSULT.EXAMPLE/app/",
                null,
                null,
                null,
                null);

        assertThat(redirect).isEqualTo("HTTPS://ECONSULT.EXAMPLE/app/?oneid_email=#!/");
    }
}
