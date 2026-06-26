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
        when(request.getRequestURI()).thenReturn("/carlos/someAction");
    }

    @AfterEach
    void tearDown() {
        // Always destroy the filter to shut down the ScheduledExecutorService started by init()
        filter.destroy();
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
     * Initialises the filter as enabled with the given enforce flag, global rate, and
     * a path-specific tier configuration string (e.g. {@code "/ws/=2/60"}).
     */
    private void initFilterWithPaths(boolean enforcing, int requests, int windowSeconds, String paths)
            throws Exception {
        when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
        when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn(enforcing ? "enforce" : "detect");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn(String.valueOf(requests));
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn(String.valueOf(windowSeconds));
        when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn(paths);
        when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1,0:0:0:0:0:0:0:1");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
        filter.init(mock(FilterConfig.class));
    }

    private HttpServletRequest requestForIp(String ip) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getContextPath()).thenReturn("/carlos");
        when(r.getRemoteAddr()).thenReturn(ip);
        when(r.getRequestURI()).thenReturn("/carlos/someAction");
        return r;
    }

    /**
     * Initialises the filter as disabled (WAF_RATE_LIMIT_ENABLED explicitly set to "false").
     */
    private void initFilterDisabled() throws Exception {
        when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("false");
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
    // Default-enabled behaviour
    // -------------------------------------------------------------------------

    /**
     * Stubs all rate-limit properties except WAF_RATE_LIMIT_ENABLED and initialises the filter.
     * Used by DefaultEnabled tests that need to vary only the enabled property value.
     */
    private void stubDefaultRateLimitPropertiesAndInit() throws Exception {
        when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("detect");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1,0:0:0:0:0:0:0:1");
        when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
        FilterConfig fc = mock(FilterConfig.class);
        filter.init(fc);
    }

    @Nested
    @DisplayName("Default-enabled behaviour")
    class DefaultEnabled {

        @Test
        @DisplayName("should be enabled when WAF_RATE_LIMIT_ENABLED property is absent")
        void shouldBeEnabled_whenPropertyAbsent() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn(null);
            stubDefaultRateLimitPropertiesAndInit();

            assertThat(filter.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should be enabled when WAF_RATE_LIMIT_ENABLED property is blank")
        void shouldBeEnabled_whenPropertyBlank() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("  ");
            stubDefaultRateLimitPropertiesAndInit();

            assertThat(filter.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should be enabled when WAF_RATE_LIMIT_ENABLED contains an unrecognized value")
        void shouldBeEnabled_whenPropertyIsUnrecognizedValue() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("ture");
            stubDefaultRateLimitPropertiesAndInit();

            assertThat(filter.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should be disabled when WAF_RATE_LIMIT_ENABLED is set to false")
        void shouldBeDisabled_whenPropertyIsFalse() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("false");
            FilterConfig fc = mock(FilterConfig.class);
            filter.init(fc);

            assertThat(filter.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should be disabled when WAF_RATE_LIMIT_ENABLED is set to no")
        void shouldBeDisabled_whenPropertyIsNo() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("no");
            FilterConfig fc = mock(FilterConfig.class);
            filter.init(fc);

            assertThat(filter.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should be disabled when WAF_RATE_LIMIT_ENABLED is set to off")
        void shouldBeDisabled_whenPropertyIsOff() throws Exception {
            when(mockProperties.getProperty("WAF_RATE_LIMIT_ENABLED")).thenReturn("off");
            FilterConfig fc = mock(FilterConfig.class);
            filter.init(fc);

            assertThat(filter.isEnabled()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Mode configuration
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Mode configuration")
    class ModeConfiguration {

        @Test
        @DisplayName("should default to detect mode when mode value is unrecognized typo")
        void shouldDefaultToDetectMode_whenModeIsUnrecognizedTypo() throws Exception {
            // "enforced" (not "enforce") should NOT activate blocking — must default to detect
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforced");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            assertThat(filter.isEnforcing()).isFalse();
        }

        @Test
        @DisplayName("should default to detect mode when mode is arbitrary garbage value")
        void shouldDefaultToDetectMode_whenModeIsGarbageValue() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("BLOCKER");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("5");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // With detect mode (due to unrecognized value), requests over limit are logged but not blocked
            for (int i = 0; i < 6; i++) {
                filter.doFilter(request, response, chain);
            }

            verify(chain, org.mockito.Mockito.times(6)).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("should activate enforce mode only when mode is explicitly 'enforce'")
        void shouldActivateEnforceMode_whenModeIsExplicitlyEnforce() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("ENFORCE");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            assertThat(filter.isEnforcing()).isTrue();
        }

        @Test
        @DisplayName("should pass through non-HTTP requests without rate limiting")
        void shouldPassThrough_forNonHttpRequests() throws Exception {
            initFilter(true, 1, 60); // enforce mode, very tight limit

            // Pass a raw ServletRequest (not HttpServletRequest) — should bypass rate limiting entirely
            jakarta.servlet.ServletRequest rawRequest = mock(jakarta.servlet.ServletRequest.class);
            jakarta.servlet.ServletResponse rawResponse = mock(jakarta.servlet.ServletResponse.class);

            filter.doFilter(rawRequest, rawResponse, chain);

            verify(chain).doFilter(rawRequest, rawResponse);
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
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=10/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // Request URI doesn't match /login, so global limit of 3 applies
            when(request.getRequestURI()).thenReturn("/carlos/someOtherAction");

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
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=2/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // /login has a limit of 2, far below global 100
            when(request.getRequestURI()).thenReturn("/carlos/login");

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
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=1/60,/mfa/=1/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            // Exhaust /login counter
            when(request.getRequestURI()).thenReturn("/carlos/login");
            filter.doFilter(request, response, chain);

            // /mfa/ should still have its own counter and allow 1 request
            when(request.getRequestURI()).thenReturn("/carlos/mfa/validate");
            filter.doFilter(request, response, chain);

            // Both paths exhausted — next /mfa/ request should be blocked
            filter.doFilter(request, response, chain);
            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should apply forced-reset submit rate to submit endpoint")
        void shouldApplyForcedResetSubmitRate_whenSubmitPathMatches() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS"))
                    .thenReturn("/forcepasswordreset=20/60,/forcepasswordresetSubmit=2/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            when(request.getRequestURI()).thenReturn("/carlos/forcepasswordresetSubmit");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        /**
         * Boundary check: an attacker cookie-rewriting their session as a
         * matrix-param suffix on /login (e.g., {@code /login;jsessionid=…})
         * must still hit the /login rate-limit tier — appending a path
         * parameter is not a rate-limit bypass. Locks in the {@code ;}
         * boundary handling added at {@code RateLimitFilter#findMatchingPath}.
         */
        @Test
        @DisplayName("should match /login path-rate when request is /login;jsessionid=...")
        void shouldMatchLoginRate_whenJsessionidMatrixParamPresent() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=2/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            when(request.getRequestURI())
                    .thenReturn("/carlos/login;jsessionid=TEST_SESSION_ID_4Q5R");

            // /login limit of 2 should still apply — three requests on the
            // matrix-param-suffixed path should yield a 429 on the third.
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        /**
         * Boundary check: paths that begin with {@code /login} but extend
         * with a non-{@code /;?} character (e.g., {@code /loginfailed}) MUST
         * NOT match the /login path-rate tier — they fall to the global
         * default. Locks in the prefix-boundary check that distinguishes
         * "/login*" patterns from "/login" exactly.
         */
        @Test
        @DisplayName("should NOT match /login path-rate for /loginfailed")
        void shouldNotMatchLoginRate_whenPathIsLoginfailed() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("5");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            // Set /login VERY tight (1/60). If /loginfailed leaks into this
            // tier, the second request will block. The global default of 5
            // must be the actual cap.
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=1/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            when(request.getRequestURI()).thenReturn("/carlos/loginfailed");

            // Three requests — would be blocked if /loginfailed matched
            // the /login=1/60 tier. Should pass under the global 5/60.
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response, never()).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should match /login path-rate when query string is separate")
        void shouldMatchLoginRate_whenQueryStringIsSeparate() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("5");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("/login=1/60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            filter.init(mock(FilterConfig.class));

            when(request.getRequestURI()).thenReturn("/carlos/login");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }
    }

    // -------------------------------------------------------------------------
    // API /ws path tiers (SOAP, session REST, OAuth setup, OAuth REST)
    // -------------------------------------------------------------------------

    /**
     * Locks in how the broad {@code /ws/} tier matches real CARLOS API request URIs after
     * context-path stripping. The servlet container reports the context path (e.g.
     * {@code /carlos}) separately from {@link HttpServletRequest#getRequestURI()}, and the
     * query string (e.g. {@code ?wsdl}) is not part of the request URI, so the filter only
     * ever matches against the context-stripped path (e.g. {@code /ws/ScheduleService}).
     *
     * <p>These tests cover the four representative API families called out in the rate-limit
     * hardening investigation: SOAP {@code /ws/ScheduleService}, session REST {@code /ws/rs},
     * OAuth setup {@code /ws/oauth}, and OAuth-protected REST {@code /ws/services}. They prove
     * each family falls under the single shared {@code /ws/} bucket and exercise the 429 +
     * {@code Retry-After} enforce path and the detect-mode pass-through on those paths.</p>
     */
    @Nested
    @DisplayName("API /ws path tiers")
    class ApiWebServicePathRateLimiting {

        @Test
        @DisplayName("should apply /ws tier to SOAP /ws/ScheduleService after context-path stripping")
        void shouldApplyWsTier_forSoapScheduleServicePath() throws Exception {
            // global is deliberately high (100) so any 429 must come from the /ws/=2/60 tier
            initFilterWithPaths(true, 100, 60, "/ws/=2/60");

            // ?wsdl lives in the query string, not the request URI
            when(request.getRequestURI()).thenReturn("/carlos/ws/ScheduleService");

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — allowed
            filter.doFilter(request, response, chain); // 3rd — blocked by /ws/ tier

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should apply /ws tier to session REST /ws/rs path")
        void shouldApplyWsTier_forSessionRestPath() throws Exception {
            initFilterWithPaths(true, 100, 60, "/ws/=2/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/rs/appointment/123");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should apply /ws tier to OAuth setup /ws/oauth path")
        void shouldApplyWsTier_forOauthInitiatePath() throws Exception {
            initFilterWithPaths(true, 100, 60, "/ws/=2/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/oauth/initiate");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should apply /ws tier to OAuth-protected REST /ws/services path")
        void shouldApplyWsTier_forOauthRestServicesPath() throws Exception {
            initFilterWithPaths(true, 100, 60, "/ws/=2/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/services/ScheduleService");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should return 429 with Retry-After when /ws tier exceeded in enforce mode")
        void shouldReturn429WithRetryAfter_whenWsTierExceededInEnforceMode() throws Exception {
            initFilterWithPaths(true, 100, 60, "/ws/=1/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/ScheduleService");

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — blocked

            verify(response).setHeader(eq("Retry-After"), anyString());
            verify(response).sendError(eq(429), anyString());
        }

        @Test
        @DisplayName("should log but pass through when /ws tier exceeded in detect mode")
        void shouldLogButPassThrough_whenWsTierExceededInDetectMode() throws Exception {
            initFilterWithPaths(false, 100, 60, "/ws/=1/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/ScheduleService");

            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);
            filter.doFilter(request, response, chain);

            // detect mode never blocks — all three requests proceed down the chain
            verify(chain, org.mockito.Mockito.times(3)).doFilter(request, response);
            verify(response, never()).sendError(anyInt(), anyString());
        }

        /**
         * Locks in (does NOT change) the current global-before-path interaction flagged in
         * the investigation: the global counter is acquired first, so when the shipped global
         * default is stricter than the {@code /ws/} tier, global enforcement blocks API traffic
         * before the broader {@code /ws/} tier is ever consulted. Here global {@code 2/60} is
         * stricter than {@code /ws/=200/60}; the third request is blocked even though the
         * {@code /ws/} tier alone would allow 200. This is a regression guard documenting
         * present behavior so a future redesign is a deliberate, reviewed decision.
         */
        @Test
        @DisplayName("should block on stricter global counter before /ws tier is reached")
        void shouldBlockOnGlobalCounter_whenGlobalStricterThanWsTier() throws Exception {
            initFilterWithPaths(true, 2, 60, "/ws/=200/60");

            when(request.getRequestURI()).thenReturn("/carlos/ws/ScheduleService");

            filter.doFilter(request, response, chain); // 1st — allowed
            filter.doFilter(request, response, chain); // 2nd — allowed
            filter.doFilter(request, response, chain); // 3rd — blocked by global, not /ws/

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
            when(request2.getRequestURI()).thenReturn("/carlos/someAction");
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
            Map<String, RateConfig> result = filter.parsePathRates("/login=10/60,/ws/=200/60");

            assertThat(result).hasSize(2);
            assertThat(result.get("/login").requests).isEqualTo(10);
            assertThat(result.get("/login").windowSeconds).isEqualTo(60);
            assertThat(result.get("/ws/").requests).isEqualTo(200);
            assertThat(result.get("/ws/").windowSeconds).isEqualTo(60);
        }

        @Test
        @DisplayName("should ignore invalid path rate config entries with malformed format")
        void shouldIgnoreInvalidPathRateConfig_withMalformedFormat() {
            // Malformed entries should be skipped silently (with a warning log)
            Map<String, RateConfig> result = filter.parsePathRates("abc/xyz,/valid=5/30,,=10/60");

            // Only the valid entry should be parsed
            assertThat(result).hasSize(1);
            assertThat(result).containsKey("/valid");
            assertThat(result.get("/valid").requests).isEqualTo(5);
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

        @Test
        @DisplayName("should cap stored counters when many client IPs rotate")
        void shouldCapStoredCounters_whenManyClientIpsRotate() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("detect");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MAX_COUNTERS")).thenReturn("2");
            filter.init(mock(FilterConfig.class));

            for (int i = 1; i <= 5; i++) {
                when(request.getRemoteAddr()).thenReturn("10.0.0." + i);
                filter.doFilter(request, response, chain);
            }

            assertThat(filter.getCounters()).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should keep new clients isolated when counter cap is reached")
        void shouldKeepNewClientsIsolated_whenCounterCapReached() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("enforce");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MAX_COUNTERS")).thenReturn("2");
            filter.init(mock(FilterConfig.class));

            filter.doFilter(requestForIp("10.0.0.1"), response, chain);
            filter.doFilter(requestForIp("10.0.0.2"), response, chain);

            HttpServletResponse thirdResponse = mock(HttpServletResponse.class);
            filter.doFilter(requestForIp("10.0.0.3"), thirdResponse, chain);
            filter.doFilter(requestForIp("10.0.0.3"), thirdResponse, chain);
            verify(thirdResponse).sendError(eq(429), anyString());

            HttpServletResponse fourthResponse = mock(HttpServletResponse.class);
            filter.doFilter(requestForIp("10.0.0.4"), fourthResponse, chain);

            verify(fourthResponse, never()).sendError(eq(429), anyString());
            assertThat(filter.getCounters()).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should cap forwarded-address warning suppression set")
        void shouldCapForwardedWarningSet_whenProxyIpsRotate() throws Exception {
            when(mockProperties.isPropertyActive("WAF_RATE_LIMIT_ENABLED")).thenReturn(true);
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MODE")).thenReturn("detect");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS")).thenReturn("100");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_DEFAULT_WINDOW_SECONDS")).thenReturn("60");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_PATHS")).thenReturn("");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_EXEMPT_IPS")).thenReturn("127.0.0.1,::1");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS")).thenReturn("300");
            when(mockProperties.getProperty("WAF_RATE_LIMIT_MAX_COUNTERS")).thenReturn("2");
            when(mockProperties.getProperty(XforwardHeaderFilter.TRUSTED_PROXY_IPS_PROPERTY))
                    .thenReturn("10.0.0.1,10.0.0.2,10.0.0.3,10.0.0.4,10.0.0.5");
            filter.init(mock(FilterConfig.class));

            for (int i = 1; i <= 5; i++) {
                HttpServletRequest forwardedRequest = requestForIp("10.0.0." + i);
                when(forwardedRequest.getHeader("X-Forwarded-For")).thenReturn("192.0.2." + i);
                filter.doFilter(forwardedRequest, response, chain);
            }

            assertThat(filter.forwardedAddressWarningIpCount()).isLessThanOrEqualTo(2);
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
