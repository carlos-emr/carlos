/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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

/**
 * Unit tests for {@link EConsult2Action}'s eConsult SSO return URL construction.
 *
 * <p>Covers the fix for issue #3018: the SSO {@code oscarReturnURL} must be built from
 * the trusted, configured {@code carlosBaseUrl} rather than from the client-controlled
 * request Host header.
 *
 * @since 2026-06-24
 */
@DisplayName("EConsult2Action Unit Tests")
@Tag("unit")
@Tag("consultation")
class EConsult2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("Builds the return URL from the configured base, not the request Host")
    void shouldBuildReturnUrl_withConfiguredBaseAndContextPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Keeps an explicit port from the configured base")
    void shouldBuildReturnUrl_withConfiguredPort() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com:8443", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Supports the root context (empty context path)")
    void shouldBuildReturnUrl_withRootContext() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "")).isEqualTo("https://emr.example.com/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com", null)).isEqualTo("https://emr.example.com/econsultSSOLogin");
    }

    @Test
    @DisplayName("Ignores any path on the configured base and uses the server context path")
    void shouldBuildReturnUrl_whenConfiguredBaseHasTrailingPath() {
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com/ignored", "/carlos");

        assertThat(returnUrl).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Return URL host never reflects the request context path argument")
    void shouldBuildReturnUrl_withoutLeakingSpoofedHostFromContextPath() {
        // Even if a hostile-looking value reaches the context-path argument, the origin is
        // fixed by the configured base; the value can only ever land in the path component.
        String returnUrl = EConsult2Action.buildSsoReturnUrl("https://emr.example.com", "/attacker.example.org");

        assertThat(returnUrl).startsWith("https://emr.example.com/");
        assertThat(returnUrl).isEqualTo("https://emr.example.com/attacker.example.org/econsultSSOLogin");
    }

    @Test
    @DisplayName("Fails closed when no base URL is configured")
    void shouldFailClosed_whenBaseUrlMissing() {
        assertThat(EConsult2Action.buildSsoReturnUrl(null, "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("   ", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base with a non-http(s) scheme")
    void shouldFailClosed_whenBaseUrlSchemeNotHttp() {
        assertThat(EConsult2Action.buildSsoReturnUrl("ftp://emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("javascript:alert(1)", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base with no host")
    void shouldFailClosed_whenBaseUrlHasNoHost() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https:///econsultSSOLogin", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("not a url", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects a configured base carrying credentials, query, or fragment")
    void shouldFailClosed_whenBaseUrlIsNotABareOrigin() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com?x=1", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr.example.com#frag", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Accepts an internal hostname containing an underscore")
    void shouldBuildReturnUrl_withUnderscoreHostname() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com", "/carlos")).isEqualTo("https://emr_dev.example.com/carlos/econsultSSOLogin");
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:8443", "/carlos")).isEqualTo("https://emr_dev.example.com:8443/carlos/econsultSSOLogin");
    }

    @Test
    @DisplayName("Still rejects credentials and bad ports on an underscore hostname")
    void shouldFailClosed_whenUnderscoreHostnameIsUnsafe() {
        assertThat(EConsult2Action.buildSsoReturnUrl("https://user:pass@emr_dev.example.com", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:notaport", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com?x=1", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Rejects signed, empty, or out-of-range fallback ports")
    void shouldFailClosed_whenUnderscoreHostnamePortIsMalformed() {
        // Integer.parseInt would tolerate the leading sign; the validator must not.
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:-443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:+443", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:", "/carlos")).isNull();
        assertThat(EConsult2Action.buildSsoReturnUrl("https://emr_dev.example.com:99999", "/carlos")).isNull();
    }

    @Test
    @DisplayName("Accepts an uppercase scheme regardless of default locale")
    void shouldBuildReturnUrl_withUppercaseScheme() {
        assertThat(EConsult2Action.buildSsoReturnUrl("HTTPS://emr.example.com", "/carlos")).isEqualTo("https://emr.example.com/carlos/econsultSSOLogin");
    }
}
