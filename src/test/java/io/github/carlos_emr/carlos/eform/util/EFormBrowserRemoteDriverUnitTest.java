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
package io.github.carlos_emr.carlos.eform.util;

import java.net.ConnectException;
import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;

import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for connecting the eForm renderer to an already-running chromedriver.
 *
 * <p>The renderer used to spawn chromedriver in-process, which meant Chromium inherited
 * carlos-emr.service's cgroup and could never use its own sandbox. It now connects over loopback
 * instead. These pin the parts of that change whose failure modes are quiet.
 *
 * @since 2026-08-26
 */
@DisplayName("eForm renderer remote chromedriver")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormBrowserRemoteDriverUnitTest {

    @Test
    @DisplayName("should accept loopback chromedriver URLs, with or without a url-base prefix")
    void shouldAcceptLoopbackUrls_whenServiceUrlIsValidated() {
        assertThat(EFormBrowserPdfService.validateBrowserServiceUrl("http://127.0.0.1:9515"))
                .isEqualTo(URI.create("http://127.0.0.1:9515"));
        assertThat(EFormBrowserPdfService.validateBrowserServiceUrl("http://localhost:9515"))
                .isEqualTo(URI.create("http://localhost:9515"));
        assertThat(EFormBrowserPdfService.validateBrowserServiceUrl("http://[::1]:9515"))
                .isEqualTo(URI.create("http://[::1]:9515"));
        // The url-base capability token is a path prefix and MUST be accepted.
        assertThat(EFormBrowserPdfService.validateBrowserServiceUrl("http://127.0.0.1:9515/a1b2c3"))
                .isEqualTo(URI.create("http://127.0.0.1:9515/a1b2c3"));
    }

    @Test
    @DisplayName("should reject a chromedriver URL that is not loopback")
    void shouldRejectNonLoopback_whenServiceUrlIsValidated() {
        // The browser must share the JVM's loopback: the render URL is gated to it in several
        // places, and the dead-proxy bypass list permits exactly that one origin.
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("http://10.0.0.5:9515"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("http://host.example:9515"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a chromedriver URL with no explicit port")
    void shouldRejectMissingPort_whenServiceUrlIsValidated() {
        // Without this, a missing port would default to 80 and silently point the renderer at
        // Tomcat or nginx. chromedriver has no default port of its own.
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("http://127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }

    @Test
    @DisplayName("should reject https, credentials, queries and blank chromedriver URLs")
    void shouldRejectUnusableShapes_whenServiceUrlIsValidated() {
        // https is not pedantry: chromedriver serves plaintext only, so it could never work.
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("https://127.0.0.1:9515"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("http://u:p@127.0.0.1:9515"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("http://127.0.0.1:9515/?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.validateBrowserServiceUrl("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should classify a refused connection as an unreachable render browser service")
    void shouldClassifyRefusedConnection_asServiceUnreachable() {
        // By TYPE, never by message: WebDriver messages are assembled from
        // getAdditionalInformation() and are not a stable discriminator.
        Throwable refused = new WebDriverException("could not start a new session",
                new ConnectException("Connection refused"));

        assertThat(EFormBrowserPdfService.isServiceUnreachable(refused)).isTrue();
    }

    @Test
    @DisplayName("should not classify a reachable driver that failed to launch a browser as unreachable")
    void shouldNotClassifyLaunchFailure_asServiceUnreachable() {
        // Reached chromedriver, but the browser would not start. That is a sandbox/binary problem
        // and must produce the Chromium startup message, not "service unavailable".
        assertThat(EFormBrowserPdfService.isServiceUnreachable(
                new SessionNotCreatedException("probably user data directory is already in use"))).isFalse();
    }

    @Test
    @DisplayName("should terminate classification on a self-referential cause chain")
    void shouldTerminate_whenCauseChainIsSelfReferential() {
        // Java forbids a throwable causing ITSELF, but not a cycle between two. A guard written as
        // `c != c.getCause()` misses this and spins forever on a failure path.
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        // Preemptive timeout: a regression to the old `c != c.getCause()` guard must FAIL
        // this test, not hang the whole build on an infinite walk.
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(5),
                () -> EFormBrowserPdfService.isServiceUnreachable(first));
    }

    @Test
    @DisplayName("should keep the chromedriver endpoint out of the unavailable-service message")
    void shouldNotLeakEndpoint_whenServiceIsUnavailable() {
        PDFGenerationException e = EFormBrowserPdfService.browserServiceUnavailable();

        // This message reaches clinician-visible surfaces, and the configured URL carries the
        // url-base capability token. Remediation belongs in the log line, which names the property
        // key and the unit rather than the value.
        assertThat(e.getMessage()).doesNotContain("://").doesNotContain("9515").doesNotContain("/usr");
        // Never chains a cause, matching chromiumStartupFailure: a downstream handler that logs the
        // chain would re-emit whatever the WebDriver message embedded.
        assertThat(e.getCause()).isNull();
    }

    @Test
    @DisplayName("should redact a chromedriver URL carrying the url-base secret")
    void shouldRedactUrlBaseSecret_whenLoggingThirdPartyText() {
        String leaked = "Could not start a new session at "
                + "http://127.0.0.1:9515/9f3c1d2e4b5a6c7d/session";

        String redacted = RenderLogRedaction.redactUrls(leaked);

        assertThat(redacted).doesNotContain("9f3c1d2e4b5a6c7d");
    }

    @Test
    @DisplayName("should refuse a renderer browser handle without the endpoint and session it must tear down")
    void shouldRequireTeardownHandles_whenRendererBrowserIsConstructed() {
        // Replaces the old "service is non-null on both paths" invariant. Both are captured at
        // creation: the session id because quit() nulls its own even when it FAILS, and the URI
        // because re-reading the property at teardown could send the force-delete elsewhere.
        // Three cases, each nulling exactly ONE component with the other two real: the
        // all-null form only ever exercised the FIRST requireNonNull, so deleting the
        // serviceUri or sessionId check — the two invariants that replaced the old
        // non-null-service one — left the test green.
        org.openqa.selenium.chromium.ChromiumDriver realDriver =
                org.mockito.Mockito.mock(org.openqa.selenium.chromium.ChromiumDriver.class);
        java.net.URI realUri = java.net.URI.create("http://127.0.0.1:9515/token");
        org.openqa.selenium.remote.SessionId realSession =
                new org.openqa.selenium.remote.SessionId("abc123");

        assertThatThrownBy(() -> new EFormBrowserPdfService.RendererBrowser(null, realUri, realSession))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("driver");
        assertThatThrownBy(() -> new EFormBrowserPdfService.RendererBrowser(realDriver, null, realSession))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("serviceUri");
        assertThatThrownBy(() -> new EFormBrowserPdfService.RendererBrowser(realDriver, realUri, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId");
    }
}
