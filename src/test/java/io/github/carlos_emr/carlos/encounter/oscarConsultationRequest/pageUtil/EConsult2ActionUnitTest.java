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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EConsult2Action}'s eConsult redirect construction.
 *
 * <p>Covers both the hardened configured-base redirect builders ({@code frontendRedirectUrl},
 * {@code loginRedirectUrl}, {@code isValidTask}) and the SSO return URL construction
 * ({@code buildSsoReturnUrl}, {@code econsultBaseUrlMisconfigured}). The SSO {@code oscarReturnURL}
 * must be built from the trusted, configured {@code carlosBaseUrl} rather than from the
 * client-controlled request Host header.
 *
 * @since 2026-06-24
 */
@DisplayName("EConsult2Action redirects")
@Tag("unit")
@Tag("security")
@Tag("consultation")
class EConsult2ActionUnitTest extends CarlosUnitTestBase {

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

    @Test
    @DisplayName("should build the return URL from the configured base, not the request Host")
    void shouldBuildReturnUrl_withConfiguredBaseAndContextPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should keep an explicit port from the configured base")
    void shouldBuildReturnUrl_withConfiguredPort() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com:8443", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should reject an out-of-range port on a normal hostname")
    void shouldFailClosed_whenConfiguredPortOutOfRange() {
        // java.net.URI#getPort() does not range-check, so the validator must.
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com:99999", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com:70000", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should support the root context (empty context path)")
    void shouldBuildReturnUrl_withRootContext() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "")).isEqualTo("https://emr.example.com/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", null)).isEqualTo("https://emr.example.com/econsultSSOLogin");
    }

    @Test
    @DisplayName("should ignore any path on the configured base and use the server context path")
    void shouldBuildReturnUrl_whenConfiguredBaseHasTrailingPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com/ignored", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should never reflect a spoofed host from the request context path argument")
    void shouldBuildReturnUrl_withoutLeakingSpoofedHostFromContextPath() {
        // Even if a hostile-looking value reaches the context-path argument, the origin is
        // fixed by the configured base; the value can only ever land in the path component.
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/attacker.example.org");

        assertThat(returnUrl).startsWith("https://emr.example.com/");
        assertThat(returnUrl).isEqualTo("https://emr.example.com/attacker.example.org/econsultSSOLogin");
    }

    @Test
    @DisplayName("should fail closed when no base URL is configured")
    void shouldFailClosed_whenBaseUrlMissing() {
        assertThat(EConsult2Action.buildSsoReturnUrl(null, "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("   ", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should reject a configured base with a non-http(s) scheme")
    void shouldFailClosed_whenBaseUrlSchemeNotHttp() {
        assertThat(EConsult2Action.buildSsoReturnUrl("ftp://emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("javascript:alert(1)", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should reject a configured base with no host")
    void shouldFailClosed_whenBaseUrlHasNoHost() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https:///econsultSSOLogin", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("not a url", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should reject a configured base carrying credentials, query, or fragment")
    void shouldFailClosed_whenBaseUrlIsNotABareOrigin() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com?x=1", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com#frag", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should accept an internal hostname containing an underscore")
    void shouldBuildReturnUrl_withUnderscoreHostname() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com", "/carlos")).isEqualTo("https://emr_dev.example.com/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:8443", "/carlos")).isEqualTo("https://emr_dev.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should still reject credentials and bad ports on an underscore hostname")
    void shouldFailClosed_whenUnderscoreHostnameIsUnsafe() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr_dev.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:notaport", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com?x=1", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should reject signed, empty, or out-of-range fallback ports")
    void shouldFailClosed_whenUnderscoreHostnamePortIsMalformed() {
        // Integer.parseInt would tolerate the leading sign; the validator must not.
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:-443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:+443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:99999", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should accept an uppercase scheme regardless of default locale")
    void shouldBuildReturnUrl_withUppercaseScheme() {
        assertThat(EConsult2Action.buildSsoReturnUrl("HTTPS://emr.example.com", "/carlos")).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("HtTp://emr.example.com", "/carlos")).isEqualTo("http://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should reject a malformed multi-colon authority on an underscore hostname")
    void shouldFailClosed_whenUnderscoreHostnameAuthorityHasExtraColon() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:8080:9090", "/carlos")).isNull();
    }

    @Test
    @DisplayName("should accept a bracketed IPv6 configured base")
    void shouldBuildReturnUrl_withIpv6Host() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://[::1]", "/carlos")).isEqualTo("https://[::1]/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://[2001:db8::1]:9443", "/carlos")).isEqualTo("https://[2001:db8::1]:9443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("should flag misconfiguration when eConsult is set but the base URL is missing")
    void shouldReportMisconfigured_whenEconsultConfiguredWithoutBaseUrl() {
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", null)).isTrue();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "")).isTrue();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "   ")).isTrue();
    }

    @Test
    @DisplayName("should not flag misconfiguration when the base URL is set or eConsult is unused")
    void shouldNotReportMisconfigured_whenBaseUrlPresentOrEconsultUnused() {
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("https://econsult.example.com", "https://emr.example.com")).isFalse();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured(null, null)).isFalse();
        assertThat(EConsult2Action.econsultBaseUrlMisconfigured("", "")).isFalse();
    }
}
