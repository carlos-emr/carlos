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
package io.github.carlos_emr.carlos.fax.ringcentral;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RingCentral OAuth credential validation.
 *
 * @since 2026-05-05
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralAuthService Unit Tests")
class RingCentralAuthServiceTest extends CarlosUnitTestBase {

    private final RingCentralAuthService authService = new RingCentralAuthService();

    @Test
    @DisplayName("should throw RingCentralException when client ID is missing")
    void shouldThrowRingCentralException_whenClientIdIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("client ID");
    }

    @Test
    @DisplayName("should throw RingCentralException when client secret is missing")
    void shouldThrowRingCentralException_whenClientSecretIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    @DisplayName("should throw RingCentralException when JWT token is missing")
    void shouldThrowRingCentralException_whenJwtTokenIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("JWT token");
    }

    @Test
    @DisplayName("should not throw when RingCentral credentials are present")
    void shouldNotThrow_whenCredentialsArePresent() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatCode(() -> authService.validateCredentials(config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should return cached token when credentials are unchanged")
    void shouldReturnCachedToken_whenCredentialsAreUnchanged() throws Exception {
        FaxConfig config = config(1, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        when(connector.authenticate("client", "secret", "jwt")).thenReturn(token("cached-token", 3600));

        String firstToken = authService.getAccessToken(config, connector);
        String secondToken = authService.getAccessToken(config, connector);

        assertThat(firstToken).isEqualTo("cached-token");
        assertThat(secondToken).isEqualTo("cached-token");
        verify(connector, times(1)).authenticate("client", "secret", "jwt");
    }

    @Test
    @DisplayName("should keep cached token while inside the 60-second skew window")
    void shouldKeepCachedToken_whileInsideSkewWindow() throws Exception {
        MutableClock clock = new MutableClock();
        RingCentralAuthService clockedAuthService = new RingCentralAuthService(clock);
        FaxConfig config = config(2, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        // expires_in=120 with TOKEN_EXPIRY_SKEW_SECONDS=60 means cache window = 60s
        when(connector.authenticate("client", "secret", "jwt")).thenReturn(token("first-token", 120));

        String firstToken = clockedAuthService.getAccessToken(config, connector);
        clock.advanceSeconds(59);
        String secondToken = clockedAuthService.getAccessToken(config, connector);

        assertThat(firstToken).isEqualTo("first-token");
        assertThat(secondToken).isEqualTo("first-token");
        verify(connector, times(1)).authenticate("client", "secret", "jwt");
    }

    @Test
    @DisplayName("should refresh cached token after the skew-adjusted expiry passes")
    void shouldRefreshToken_afterSkewAdjustedExpiryPasses() throws Exception {
        MutableClock clock = new MutableClock();
        RingCentralAuthService clockedAuthService = new RingCentralAuthService(clock);
        FaxConfig config = config(2, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        // expires_in=120, skew=60s -> cache window of 60s. Advancing 61s puts us past it.
        when(connector.authenticate("client", "secret", "jwt"))
                .thenReturn(token("first-token", 120))
                .thenReturn(token("second-token", 3600));

        String firstToken = clockedAuthService.getAccessToken(config, connector);
        clock.advanceSeconds(61);
        String secondToken = clockedAuthService.getAccessToken(config, connector);

        assertThat(firstToken).isEqualTo("first-token");
        assertThat(secondToken).isEqualTo("second-token");
        verify(connector, times(2)).authenticate("client", "secret", "jwt");
    }

    @Test
    @DisplayName("should not reuse cached token when credentials change")
    void shouldNotReuseCachedToken_whenCredentialsChange() throws Exception {
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        when(connector.authenticate("client-one", "secret", "jwt")).thenReturn(token("first-token", 3600));
        when(connector.authenticate("client-two", "secret", "jwt")).thenReturn(token("second-token", 3600));

        String firstToken = authService.getAccessToken(config(3, "client-one", "secret", "jwt"), connector);
        String secondToken = authService.getAccessToken(config(3, "client-two", "secret", "jwt"), connector);

        assertThat(firstToken).isEqualTo("first-token");
        assertThat(secondToken).isEqualTo("second-token");
        verify(connector, times(1)).authenticate("client-one", "secret", "jwt");
        verify(connector, times(1)).authenticate("client-two", "secret", "jwt");
    }

    @Test
    @DisplayName("should re-authenticate after invalidateToken evicts cached entry")
    void shouldReauthenticate_afterInvalidateTokenEvictsCachedEntry() throws Exception {
        FaxConfig config = config(99, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        when(connector.authenticate("client", "secret", "jwt"))
                .thenReturn(token("first-token", 3600))
                .thenReturn(token("second-token", 3600));

        String first = authService.getAccessToken(config, connector);
        authService.invalidateToken(config);
        String second = authService.getAccessToken(config, connector);

        assertThat(first).isEqualTo("first-token");
        assertThat(second).isEqualTo("second-token");
        verify(connector, times(2)).authenticate("client", "secret", "jwt");
    }

    @Test
    @DisplayName("should ignore invalidateToken for null or unsaved fax config")
    void shouldIgnoreInvalidateToken_forNullOrUnsavedFaxConfig() {
        authService.invalidateToken(null);
        FaxConfig unsaved = mock(FaxConfig.class);
        when(unsaved.getId()).thenReturn(null);
        authService.invalidateToken(unsaved);
        // No interactions expected; test passes if no exception is thrown.
    }

    @Test
    @DisplayName("should cache fallback-expiry token for the documented one-hour window minus skew")
    void shouldCacheFallbackExpiry_forOneHourMinusSkew() throws Exception {
        MutableClock clock = new MutableClock();
        RingCentralAuthService clockedAuthService = new RingCentralAuthService(clock);
        FaxConfig config = config(50, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        // expires_in=0 -> fallback DEFAULT_TOKEN_LIFETIME_SECONDS=3600. Effective cache window
        // is 3600 - 60 (skew) = 3540s. Verify both sides of that boundary.
        when(connector.authenticate("client", "secret", "jwt"))
                .thenReturn(token("fallback-token", 0))
                .thenReturn(token("refreshed", 3600));

        String first = clockedAuthService.getAccessToken(config, connector);
        clock.advanceSeconds(3539);
        String second = clockedAuthService.getAccessToken(config, connector);
        clock.advanceSeconds(2);
        String third = clockedAuthService.getAccessToken(config, connector);

        assertThat(first).isEqualTo("fallback-token");
        assertThat(second).isEqualTo("fallback-token");
        assertThat(third).isEqualTo("refreshed");
        verify(connector, times(2)).authenticate("client", "secret", "jwt");
    }

    @Test
    @DisplayName("should share token request when concurrent calls miss cache")
    void shouldShareTokenRequest_whenConcurrentCallsMissCache() throws Exception {
        FaxConfig config = config(4, "client", "secret", "jwt");
        RingCentralApiConnector connector = mock(RingCentralApiConnector.class);
        CountDownLatch authenticateEntered = new CountDownLatch(1);
        CountDownLatch releaseAuthenticate = new CountDownLatch(1);
        when(connector.authenticate("client", "secret", "jwt")).thenAnswer(invocation -> {
            authenticateEntered.countDown();
            if (!releaseAuthenticate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release mocked RingCentral authentication");
            }
            return token("shared-token", 3600);
        });

        ExecutorService executorService = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executorService.submit(() -> {
                    start.await();
                    return authService.getAccessToken(config, connector);
                }));
            }

            start.countDown();
            assertThat(authenticateEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseAuthenticate.countDown();
            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("shared-token");
            }
        } finally {
            // Always shut down the pool; without this an early assertion failure leaves
            // worker threads parked on the unreleased latch and the JVM hangs. Surface a
            // shutdown timeout to stderr so a leaked thread is visible to the next test
            // without re-asserting in finally (which would mask the original failure).
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("WARN: executor failed to terminate within 5s after shutdownNow");
            }
        }
        verify(connector, times(1)).authenticate("client", "secret", "jwt");
    }

    private FaxConfig config(Integer id, String clientId, String clientSecret, String jwtToken) {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getId()).thenReturn(id);
        when(config.getRingCentralClientId()).thenReturn(clientId);
        when(config.getRingCentralClientSecret()).thenReturn(clientSecret);
        when(config.getRingCentralJwtToken()).thenReturn(jwtToken);
        return config;
    }

    private RingCentralResponse.Token token(String accessToken, long expiresIn) {
        return new RingCentralResponse.Token(accessToken, expiresIn);
    }

    private static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-05-06T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
