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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.app.RateLimitFilter.FixedWindowCounter;
import io.github.carlos_emr.carlos.app.RateLimitFilter.RateConfig;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitFilter}.
 *
 * <p>Validates per-IP rate limiting, path-specific tiers, detect/enforce mode,
 * IP exemptions, Retry-After headers, counter reset after window expiry, and
 * the stale counter eviction mechanism.</p>
 *
 * <p>These tests do not require a Spring context or database; all dependencies
 * are satisfied via Mockito mocks and {@link CarlosUnitTestBase}.</p>
 *
 * @since 2026-04-05
 */
@Tag("unit")
@Tag("security")
@DisplayName("RateLimitFilter")
class RateLimitFilterTest extends CarlosUnitTestBase {

    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    private CarlosProperties mockProperties;

    private RateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        mockProperties = mock(CarlosProperties.class);

        // Mock CarlosProperties singleton (Mockito initialized by CarlosUnitTestBase)
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(mockProperties);

        filter = new RateLimitFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        when(request.getContextPath()).thenReturn("/carlos");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getRequestURI()).thenReturn("/carlos/someAction.do");
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) {
            carlosPropertiesMock.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Initialises the filter as enabled, with the given enforce flag and default rate.
     */
    private void initFilter(boolean enforcing, int requests, int windowSeconds) throws Exception {
        when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
        when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn(enforcing ? "enforce" : "detect");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn(String.valueOf(requests));
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn(String.valueOf(windowSeconds));
        when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1,0:0:0:0:0:0:0:1");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
        FilterConfig fc = mock(FilterConfig.class);
        filter.init(fc);
    }

    /**
     * Initialises the filter as disabled.
     */
    private void initFilterDisabled() throws Exception {
        when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(false);
        FilterConfig fc = mock(FilterConfig.class);
        filter.init(fc);
    }

    // -------------------------------------------------------------------------
    // Disabled
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("When filter is disabled")
    class WhenDisabled {

        @Test
        @DisplayName("should pass through all requests when disabled")
        void shouldPassThrough_whenDisabled() throws Exception {
            initFilterDisabled();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Global rate limiting
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Global rate limiting")
    class GlobalRateLimiting {

        @Test
        @DisplayName("should allow request when within global limit")
        void shouldAllowRequest_whenWithinGlobalLimit() throws Exception {
            initFilter(true, 100, 60);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should block with 429 when global limit exceeded in enforce mode")
        void shouldBlock429_whenGlobalLimitExceeded() throws Exception {
            initFilter(true, 5, 60); // limit of 5

            // Send 5 allowed requests
            for (int i = 0; i < 5; i++) {
                filter.doFilter(request, response, chain);
            }
            // 6th request should be blocked
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should include Retry-After header when blocked")
        void shouldIncludeRetryAfterHeader_whenBlocked() throws Exception {
            initFilter(true, 1, 60); // limit of 1

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — blocked

            verify(response).setHeader(eq("Retry-After"), anyString());
            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should fallback to global rate when no path matches")
        void shouldFallbackToGlobalRate_whenNoPathMatch() throws Exception {
            // Enable with path-specific rates that don't match the request URI
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("3");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login.do=10/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // Request URI doesn't match /login.do, so global limit of 3 applies
            when(request.getRequestURI()).thenReturn("/carlos/someOtherAction.do");

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, chain);
            }
            filter.doFilter(request, response, chain); // 4th — should be blocked by global

            verify(response).sendError(eq(429), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Path-specific rate limiting
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Path-specific rate limiting")
    class PathSpecificRateLimiting {

        @Test
        @DisplayName("should apply path-specific rate when path matches")
        void shouldApplyPathSpecificRate_whenPathMatches() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login.do=2/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // /login.do has a limit of 2, far below global 100
            when(request.getRequestURI()).thenReturn("/carlos/login.do");

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — allowed
            filter.doFilter(request, response, chain); // 3rd — blocked (path limit exceeded)

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should track separate counters per path tier for same IP")
        void shouldTrackSeparateCounters_perPathTier() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login.do=1/60,/mfa/=1/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // Exhaust /login.do counter
            when(request.getRequestURI()).thenReturn("/carlos/login.do");
            filter.doFilter(request, response, chain);

            // /mfa/ should still have its own counter and allow 1 request
            when(request.getRequestURI()).thenReturn("/carlos/mfa/validate");
            filter.doFilter(request, response, chain);

            // Both paths exhausted — next /mfa/ request should be blocked
            filter.doFilter(request, response, chain);
            verify(response).sendError(eq(429), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // IP exemptions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IP exemptions")
    class IpExemptions {

        @Test
        @DisplayName("should exempt configured IPs from rate limiting")
        void shouldExemptConfiguredIps_fromRateLimiting() throws Exception {
            initFilter(true, 1, 60); // very tight limit

            // Use an exempt IP
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // Should never be blocked, even beyond the limit
            for (int i = 0; i < 10; i++) {
                filter.doFilter(request, response, chain);
            }

            verify(response, never()).sendError(anyInt(), anyString());
            verify(chain, org.mockito.Mockito.times(10)).doFilter(request, response);
        }
    }

    // -------------------------------------------------------------------------
    // Detect mode
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Detect mode")
    class DetectMode {

        @Test
        @DisplayName("should log but allow request when detect mode and limit exceeded")
        void shouldLogButAllow_whenDetectMode() throws Exception {
            initFilter(false, 2, 60); // detect mode, limit of 2

            for (int i = 0; i < 3; i++) {
                filter.doFilter(request, response, chain);
            }

            // All 3 requests should pass through — detect mode never blocks
            verify(chain, org.mockito.Mockito.times(3)).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should block immediately when enforce mode and limit exceeded")
        void shouldBlockImmediately_whenEnforceMode() throws Exception {
            initFilter(true, 1, 60); // enforce mode, limit of 1

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — blocked

            verify(chain, org.mockito.Mockito.times(1)).doFilter(request, response);
            verify(response).sendError(eq(429), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Independent counters per IP
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Per-IP counter isolation")
    class PerIpCounterIsolation {

        @Test
        @DisplayName("should track separate counters per client IP")
        void shouldTrackSeparateCounters_perClientIp() throws Exception {
            initFilter(true, 1, 60); // limit of 1 per IP

            // First IP exhausts its limit
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            filter.doFilter(request, response, chain); // allowed
            filter.doFilter(request, response, chain); // blocked

            // Second IP still has its own fresh counter
            HttpServletRequest request2 = mock(HttpServletRequest.class);
            when(request2.getRemoteAddr()).thenReturn("10.0.0.2");
            when(request2.getRequestURI()).thenReturn("/carlos/someAction.do");
            when(request2.getContextPath()).thenReturn("/carlos");

            filter.doFilter(request2, response, chain); // allowed — different IP

            // Verify 2 chains proceeded (1 from IP1, 1 from IP2), and 1 was blocked
            verify(chain, org.mockito.Mockito.times(2)).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
            verify(response).sendError(eq(429), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Window reset
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Window reset")
    class WindowReset {

        @Test
        @DisplayName("should reset counter when window expires")
        void shouldResetCounter_whenWindowExpires() {
            AtomicLong fakeNow = new AtomicLong(0L);
            FixedWindowCounter counter = new FixedWindowCounter(2, 50L, fakeNow::get);

            assertThat(counter.tryAcquire()).isTrue();  // 1
            assertThat(counter.tryAcquire()).isTrue();  // 2
            assertThat(counter.tryAcquire()).isFalse(); // 3 — exceeds limit

            // Advance clock past the 50ms window — no Thread.sleep needed
            fakeNow.addAndGet(51L);

            // Counter should reset; first request in the new window is allowed
            assertThat(counter.tryAcquire()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Path rate config parsing
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Path rate config parsing")
    class PathRateConfigParsing {

        @Test
        @DisplayName("should parse valid path rate config")
        void shouldParsePathRateConfig_withValidFormat() {
            Map<String, RateConfig> result = filter.parsePathRates("/login.do=10/60,/ws/=200/60");

            assertThat(result).hasSize(2);
            assertThat(result.get("/login.do").requests).isEqualTo(10);
            assertThat(result.get("/login.do").windowSeconds).isEqualTo(60);
            assertThat(result.get("/ws/").requests).isEqualTo(200);
            assertThat(result.get("/ws/").windowSeconds).isEqualTo(60);
        }

        @Test
        @DisplayName("should ignore invalid path rate config entries with malformed format")
        void shouldIgnoreInvalidPathRateConfig_withMalformedFormat() {
            // Malformed entries should be skipped silently (with a warning log)
            Map<String, RateConfig> result = filter.parsePathRates("abc/xyz,/valid.do=5/30,,=10/60");

            // Only the valid entry should be parsed
            assertThat(result).hasSize(1);
            assertThat(result).containsKey("/valid.do");
            assertThat(result.get("/valid.do").requests).isEqualTo(5);
        }

        @Test
        @DisplayName("should return empty map for blank path config")
        void shouldReturnEmptyMap_forBlankPathConfig() {
            assertThat(filter.parsePathRates("")).isEmpty();
            assertThat(filter.parsePathRates(null)).isEmpty();
            assertThat(filter.parsePathRates("   ")).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Stale counter eviction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Stale counter eviction")
    class StaleCounterEviction {

        @Test
        @DisplayName("should evict stale counters on cleanup run")
        void shouldEvictStaleCounters_onCleanupRun() throws Exception {
            AtomicLong fakeNow = new AtomicLong(0L);
            // Inject clock before init so counters created by the filter use it
            filter.setClock(fakeNow::get);

            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("1"); // 1s = 1000ms
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // Register a counter (windowStart = fakeNow.get() = 0)
            filter.doFilter(request, response, chain);
            assertThat(filter.getCounters()).isNotEmpty();
            int sizeWithActive = filter.getCounters().size();

            // Advance clock by > 2 * windowMillis (window = 1000ms, stale threshold = 2000ms)
            // Deterministic — no Thread.sleep needed
            fakeNow.addAndGet(2100L);

            // Run eviction — all counters should be evicted
            filter.evictStaleCounters();

            assertThat(filter.getCounters().size()).isLessThan(sizeWithActive);
        }
    }

    // -------------------------------------------------------------------------
    // FixedWindowCounter unit tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("FixedWindowCounter")
    class FixedWindowCounterTests {

        @Test
        @DisplayName("should return true while within limit")
        void shouldReturnTrue_whileWithinLimit() {
            FixedWindowCounter counter = new FixedWindowCounter(3, 60_000L);

            assertThat(counter.tryAcquire()).isTrue();
            assertThat(counter.tryAcquire()).isTrue();
            assertThat(counter.tryAcquire()).isTrue();
        }

        @Test
        @DisplayName("should return false when limit exceeded")
        void shouldReturnFalse_whenLimitExceeded() {
            FixedWindowCounter counter = new FixedWindowCounter(2, 60_000L);

            counter.tryAcquire();
            counter.tryAcquire();

            assertThat(counter.tryAcquire()).isFalse();
        }

        @Test
        @DisplayName("should return positive retry-after seconds when blocked")
        void shouldReturnPositiveRetryAfterSeconds_whenBlocked() {
            FixedWindowCounter counter = new FixedWindowCounter(1, 60_000L);

            counter.tryAcquire(); // consume the only token

            assertThat(counter.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("should report stale after two window durations")
        void shouldReportStale_afterTwoWindowDurations() {
            AtomicLong fakeNow = new AtomicLong(0L);
            FixedWindowCounter counter = new FixedWindowCounter(10, 50L, fakeNow::get);
            // Advance clock by > 2 * 50ms — no Thread.sleep needed
            fakeNow.addAndGet(101L);
            assertThat(counter.isStale(fakeNow.get())).isTrue();
        }

        @Test
        @DisplayName("should not report stale within one window duration")
        void shouldNotReportStale_withinOneWindowDuration() {
            AtomicLong fakeNow = new AtomicLong(0L);
            FixedWindowCounter counter = new FixedWindowCounter(10, 60_000L, fakeNow::get);
            assertThat(counter.isStale(fakeNow.get())).isFalse();
        }
    }
}
