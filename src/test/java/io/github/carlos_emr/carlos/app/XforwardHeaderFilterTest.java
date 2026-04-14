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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link XforwardHeaderFilter}.
 *
 * <p>Validates that the filter correctly resolves real client IPs from the
 * {@code X-Forwarded-For} header while rejecting spoofed loopback and
 * unspecified addresses that could bypass localhost-gate security checks.</p>
 *
 * @since 2026-04-13
 */
@Tag("unit")
@Tag("security")
@DisplayName("XforwardHeaderFilter")
class XforwardHeaderFilterTest extends CarlosUnitTestBase {

    private XforwardHeaderFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new XforwardHeaderFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
    }

    /**
     * Captures the wrapped request passed to the filter chain.
     */
    private ServletRequest captureWrappedRequest() throws Exception {
        filter.doFilter(request, response, chain);
        ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(captor.capture(), org.mockito.ArgumentMatchers.any());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Loopback/unspecified spoofing prevention")
    class LoopbackSpoofingPrevention {

        @Test
        @DisplayName("should reject X-Forwarded-For: 127.0.0.1 and use raw peer IP")
        void shouldRejectLoopbackIpv4_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("127.0.0.1");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject X-Forwarded-For: 127.0.0.2 (entire 127/8 range)")
        void shouldRejectEntireLoopbackRange_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("127.0.0.2");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject X-Forwarded-For: ::1 (IPv6 loopback)")
        void shouldRejectIpv6Loopback_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("::1");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject X-Forwarded-For: 0:0:0:0:0:0:0:1 (expanded IPv6 loopback)")
        void shouldRejectExpandedIpv6Loopback_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("0:0:0:0:0:0:0:1");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject X-Forwarded-For: 0.0.0.0 (unspecified IPv4)")
        void shouldRejectUnspecifiedIpv4_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("0.0.0.0");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject X-Forwarded-For: :: (unspecified IPv6)")
        void shouldRejectUnspecifiedIpv6_whenSpoofedInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("::");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should reject spoofed loopback in multi-value XFF (first entry)")
        void shouldRejectLoopbackInMultiValueXff_whenFirstEntry() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("127.0.0.1, 203.0.113.50");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }
    }

    @Nested
    @DisplayName("Legitimate X-Forwarded-For handling")
    class LegitimateXffHandling {

        @Test
        @DisplayName("should use XFF value for legitimate external IP")
        void shouldUseXff_whenLegitimateExternalIp() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("203.0.113.50");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("should use first IP from multi-value XFF")
        void shouldUseFirstIp_fromMultiValueXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("203.0.113.50, 10.0.0.1");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("should allow RFC1918 addresses in XFF (hospital LAN clients)")
        void shouldAllowRfc1918_whenInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("10.0.0.50");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("10.0.0.50");
        }

        @Test
        @DisplayName("should allow 192.168.x.x addresses in XFF (hospital LAN clients)")
        void shouldAllow192168_whenInXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("192.168.1.50");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.50");
        }

        @Test
        @DisplayName("should trim whitespace from single-value XFF")
        void shouldTrimWhitespace_fromSingleValueXff() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("  203.0.113.50  ");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("203.0.113.50");
        }

        @Test
        @DisplayName("should reject hostname in XFF without DNS lookup")
        void shouldRejectHostname_withoutDnsLookup() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("evil.attacker.com");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }
    }

    @Nested
    @DisplayName("No X-Forwarded-For header")
    class NoXffHeader {

        @Test
        @DisplayName("should use raw peer IP when XFF header is absent")
        void shouldUseRawPeerIp_whenXffAbsent() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn(null);

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should use raw peer IP when XFF header is empty")
        void shouldUseRawPeerIp_whenXffEmpty() throws Exception {
            when(request.getHeader("X-FORWARDED-FOR")).thenReturn("");

            ServletRequest wrapped = captureWrappedRequest();
            assertThat(wrapped.getRemoteAddr()).isEqualTo("192.168.1.100");
        }
    }

    @Nested
    @DisplayName("isLoopbackOrUnspecified helper method")
    class IsLoopbackOrUnspecifiedMethod {

        @Test
        @DisplayName("should return true for 127.0.0.1")
        void shouldReturnTrue_forIpv4Loopback() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("127.0.0.1")).isTrue();
        }

        @Test
        @DisplayName("should return true for 127.255.255.255")
        void shouldReturnTrue_forEntireLoopbackRange() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("127.255.255.255")).isTrue();
        }

        @Test
        @DisplayName("should return true for ::1")
        void shouldReturnTrue_forIpv6Loopback() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("::1")).isTrue();
        }

        @Test
        @DisplayName("should return true for 0:0:0:0:0:0:0:1")
        void shouldReturnTrue_forExpandedIpv6Loopback() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("0:0:0:0:0:0:0:1")).isTrue();
        }

        @Test
        @DisplayName("should return true for 0.0.0.0")
        void shouldReturnTrue_forUnspecifiedIpv4() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("0.0.0.0")).isTrue();
        }

        @Test
        @DisplayName("should return true for ::")
        void shouldReturnTrue_forUnspecifiedIpv6() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("::")).isTrue();
        }

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrue_forNull() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for blank string")
        void shouldReturnTrue_forBlank() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("  ")).isTrue();
        }

        @Test
        @DisplayName("should return false for legitimate external IP")
        void shouldReturnFalse_forExternalIp() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("203.0.113.50")).isFalse();
        }

        @Test
        @DisplayName("should return false for RFC1918 addresses")
        void shouldReturnFalse_forRfc1918() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("10.0.0.1")).isFalse();
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("172.16.0.1")).isFalse();
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("192.168.1.1")).isFalse();
        }

        @Test
        @DisplayName("should return true for malformed IP literal (safe default)")
        void shouldReturnTrue_forMalformedIpLiteral() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified(":::1")).isTrue();
        }

        @Test
        @DisplayName("should return true for hostname without DNS lookup")
        void shouldReturnTrue_forHostname() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("evil.attacker.com")).isTrue();
        }

        @Test
        @DisplayName("should return true for localhost hostname without DNS lookup")
        void shouldReturnTrue_forLocalhostHostname() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.isLoopbackOrUnspecified("localhost")).isTrue();
        }
    }

    @Nested
    @DisplayName("classifyIp classification method")
    class ClassifyIpMethod {

        @Test
        @DisplayName("should classify 127.0.0.1 as LOOPBACK_OR_UNSPECIFIED")
        void shouldClassifyLoopback_asLoopbackOrUnspecified() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("127.0.0.1"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.LOOPBACK_OR_UNSPECIFIED);
        }

        @Test
        @DisplayName("should classify ::1 as LOOPBACK_OR_UNSPECIFIED")
        void shouldClassifyIpv6Loopback_asLoopbackOrUnspecified() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("::1"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.LOOPBACK_OR_UNSPECIFIED);
        }

        @Test
        @DisplayName("should classify 0.0.0.0 as LOOPBACK_OR_UNSPECIFIED")
        void shouldClassifyUnspecified_asLoopbackOrUnspecified() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("0.0.0.0"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.LOOPBACK_OR_UNSPECIFIED);
        }

        @Test
        @DisplayName("should classify hostname as NOT_IP_LITERAL")
        void shouldClassifyHostname_asNotIpLiteral() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("evil.attacker.com"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.NOT_IP_LITERAL);
        }

        @Test
        @DisplayName("should classify localhost as NOT_IP_LITERAL")
        void shouldClassifyLocalhost_asNotIpLiteral() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("localhost"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.NOT_IP_LITERAL);
        }

        @Test
        @DisplayName("should classify null as NOT_IP_LITERAL")
        void shouldClassifyNull_asNotIpLiteral() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp(null))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.NOT_IP_LITERAL);
        }

        @Test
        @DisplayName("should classify malformed IP literal as UNPARSEABLE")
        void shouldClassifyMalformed_asUnparseable() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("999.999.999.999"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.UNPARSEABLE);
        }

        @Test
        @DisplayName("should classify legitimate external IP as VALID")
        void shouldClassifyExternalIp_asValid() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("203.0.113.50"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.VALID);
        }

        @Test
        @DisplayName("should classify RFC1918 address as VALID")
        void shouldClassifyRfc1918_asValid() {
            assertThat(XforwardHeaderFilter.ModifyRemoteAddress.classifyIp("10.0.0.50"))
                    .isEqualTo(XforwardHeaderFilter.ModifyRemoteAddress.IpClassification.VALID);
        }
    }
}
