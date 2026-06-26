/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NavPath}.
 *
 * @since 2026-05-27
 */
@DisplayName("NavPath")
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NavPathUnitTest {

    @Mock
    private HttpServletRequest request;

    @Nested
    @DisplayName("forwardAttribute")
    class ForwardAttributeTests {

        @Test
        void shouldReturnValue_whenAttributeIsString() {
            assertThat(NavPath.forwardAttribute("/tickler/ViewTicklerMain"))
                    .isEqualTo("/tickler/ViewTicklerMain");
        }

        @Test
        void shouldReturnEmpty_whenAttributeIsNull() {
            assertThat(NavPath.forwardAttribute(null)).isEmpty();
        }

        @Test
        void shouldReturnEmpty_whenAttributeIsNonStringType() {
            assertThat(NavPath.forwardAttribute(Integer.valueOf(42))).isEmpty();
        }
    }

    @Nested
    @DisplayName("pathMatches")
    class PathMatchesTests {

        @Test
        void shouldReturnTrue_whenTrailingSlashPatternMatchesSegment() {
            assertThat(NavPath.pathMatches("/tickler/ViewTicklerMain", "/tickler/")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenPatternMatchesWithQueryBoundary() {
            assertThat(NavPath.pathMatches("/report/ViewReportindex?param=1", "/report/")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenPatternMatchesExactPath() {
            assertThat(NavPath.pathMatches("/provider/providercontrol", "/provider/providercontrol")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenPatternMatchesWithSlashBoundary() {
            assertThat(NavPath.pathMatches("/provider/appointmentprovideradmin/day", "/provider/appointmentprovideradmin")).isTrue();
        }

        @Test
        void shouldReturnFalse_whenBoundaryCharIsHyphen() {
            assertThat(NavPath.pathMatches("/administration-panel/", "/administration")).isFalse();
        }

        @Test
        void shouldReturnTrue_whenLaterOccurrenceHasValidBoundary() {
            assertThat(NavPath.pathMatches(
                    "/prefix/administration-panel/redirect/administration?tab=users",
                    "/administration")).isTrue();
        }

        @Test
        void shouldReturnFalse_whenPathIsBlank() {
            assertThat(NavPath.pathMatches("", "/tickler/")).isFalse();
        }

        @Test
        void shouldReturnFalse_whenPatternIsBlank() {
            assertThat(NavPath.pathMatches("/tickler/ViewTicklerMain", "")).isFalse();
        }

        @Test
        void shouldReturnFalse_whenPatternNotInPath() {
            assertThat(NavPath.pathMatches("/report/ViewReportindex", "/tickler/")).isFalse();
        }
    }

    @Nested
    @DisplayName("requestPathMatches")
    class RequestPathMatchesTests {

        @Test
        void shouldReturnTrue_whenRequestUriMatchesPattern() {
            when(request.getRequestURI()).thenReturn("/carlos/tickler/ViewTicklerMain");
            when(request.getServletPath()).thenReturn("/tickler/ViewTicklerMain");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(null);
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/tickler/")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenForwardServletPathMatchesPattern() {
            when(request.getRequestURI()).thenReturn("/carlos/some/dispatcher");
            when(request.getServletPath()).thenReturn("/some/dispatcher");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(null);
            when(request.getAttribute("jakarta.servlet.forward.servlet_path"))
                    .thenReturn("/tickler/ViewTicklerMain");

            assertThat(NavPath.requestPathMatches(request, "/tickler/")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenForwardRequestUriMatchesPattern() {
            when(request.getRequestURI()).thenReturn("/carlos/some/dispatcher");
            when(request.getServletPath()).thenReturn("/some/dispatcher");
            when(request.getAttribute("jakarta.servlet.forward.request_uri"))
                    .thenReturn("/carlos/report/ViewReportindex");
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/report/")).isTrue();
        }

        @Test
        void shouldReturnTrue_whenAnyOfMultiplePatternsMatches() {
            when(request.getRequestURI()).thenReturn("/carlos/provider/providercontrol");
            when(request.getServletPath()).thenReturn("/provider/providercontrol");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(null);
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/tickler/", "/provider/providercontrol")).isTrue();
        }

        @Test
        void shouldReturnFalse_whenNoPathMatchesAnyPattern() {
            when(request.getRequestURI()).thenReturn("/carlos/report/ViewReportindex");
            when(request.getServletPath()).thenReturn("/report/ViewReportindex");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(null);
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/tickler/", "/messenger/")).isFalse();
        }

        @Test
        void shouldHandleNullForwardAttributes_withoutException() {
            when(request.getRequestURI()).thenReturn("/carlos/administration");
            when(request.getServletPath()).thenReturn("/administration");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(null);
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/administration")).isTrue();
        }

        @Test
        void shouldHandleNonStringForwardAttribute_withoutException() {
            when(request.getRequestURI()).thenReturn("/carlos/messenger/DisplayMessages");
            when(request.getServletPath()).thenReturn("/messenger/DisplayMessages");
            when(request.getAttribute("jakarta.servlet.forward.request_uri")).thenReturn(Integer.valueOf(1));
            when(request.getAttribute("jakarta.servlet.forward.servlet_path")).thenReturn(null);

            assertThat(NavPath.requestPathMatches(request, "/messenger/")).isTrue();
        }

        @Test
        void shouldReturnFalse_whenRequestIsNull() {
            assertThat(NavPath.requestPathMatches(null, "/tickler/")).isFalse();
        }

        @Test
        void shouldReturnFalse_whenPatternsAreEmpty() {
            assertThat(NavPath.requestPathMatches(request)).isFalse();
        }

        @Test
        void shouldReturnFalse_whenPatternsAreNull() {
            assertThat(NavPath.requestPathMatches(request, (String[]) null)).isFalse();
        }
    }
}
