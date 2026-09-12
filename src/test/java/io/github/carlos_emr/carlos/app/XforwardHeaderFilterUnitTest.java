/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("XforwardHeaderFilter")
@Tag("unit")
@Tag("security")
class XforwardHeaderFilterUnitTest {

    @Test
    @DisplayName("should ignore X-Forwarded-For when peer is not trusted")
    void shouldIgnoreHeaderWhenPeerIsNotTrusted() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getRemoteAddr()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("should strip trusted proxy hops from right side of X-Forwarded-For")
    void shouldStripTrustedProxyHopsFromRight_whenPeerIsTrustedExactly() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20, 127.0.0.1");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getRemoteAddr()).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("should ignore spoofed leftmost X-Forwarded-For entries")
    void shouldIgnoreSpoofedLeftmostEntries_whenTrustedProxyAppendsClient() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For"))
                .thenReturn("203.0.113.200, 198.51.100.20");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getRemoteAddr()).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("should honor header when peer is trusted by CIDR")
    void shouldHonorHeaderWhenPeerIsTrustedByCidr() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.42");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request,
                        Set.of(),
                        Set.of(XforwardHeaderFilter.CidrRange.parse("10.0.0.0/24")));

        assertThat(wrapper.getRemoteAddr()).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("should fall back to peer IP when forwarded header is malformed")
    void shouldFallbackWhenHeaderIsMalformed() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getRemoteAddr()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("should reflect forwarded scheme, host and port when peer is trusted")
    void shouldReflectForwardedSchemeHostAndPort_whenPeerIsTrusted() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("clinic.example.ca");
        when(request.getHeader("X-Forwarded-Port")).thenReturn("443");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getScheme()).isEqualTo("https");
        assertThat(wrapper.isSecure()).isTrue();
        assertThat(wrapper.getServerName()).isEqualTo("clinic.example.ca");
        assertThat(wrapper.getServerPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("should ignore forwarded scheme, host and port when peer is not trusted")
    void shouldIgnoreForwardedSchemeHostAndPort_whenPeerIsNotTrusted() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getScheme()).thenReturn("http");
        when(request.isSecure()).thenReturn(false);
        when(request.getServerName()).thenReturn("internal-host");
        when(request.getServerPort()).thenReturn(8080);

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getScheme()).isEqualTo("http");
        assertThat(wrapper.isSecure()).isFalse();
        assertThat(wrapper.getServerName()).isEqualTo("internal-host");
        assertThat(wrapper.getServerPort()).isEqualTo(8080);
    }

    @Test
    @DisplayName("should derive host and port from X-Forwarded-Host when port header is absent")
    void shouldDeriveHostAndPortFromForwardedHost_whenPortHeaderAbsent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("clinic.example.ca:8443");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerName()).isEqualTo("clinic.example.ca");
        assertThat(wrapper.getServerPort()).isEqualTo(8443);
    }

    @Test
    @DisplayName("should infer default port from forwarded scheme when no port is supplied")
    void shouldInferDefaultPortFromScheme_whenNoPortSupplied() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("clinic.example.ca");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("should use first hop from comma-separated forwarded headers")
    void shouldUseFirstHop_whenForwardedHeadersAreCommaSeparated() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https, http");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("clinic.example.ca, internal");
        when(request.getHeader("X-Forwarded-Port")).thenReturn("443, 8080");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getScheme()).isEqualTo("https");
        assertThat(wrapper.getServerName()).isEqualTo("clinic.example.ca");
        assertThat(wrapper.getServerPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("should fall back to peer scheme and port when forwarded values are invalid")
    void shouldFallbackToPeerValues_whenForwardedValuesAreInvalid() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("ftp");
        when(request.getHeader("X-Forwarded-Port")).thenReturn("not-a-port");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getScheme()).isEqualTo("http");
        assertThat(wrapper.getServerPort()).isEqualTo(8080);
    }

    @Test
    @DisplayName("should preserve brackets and extract port for a bracketed IPv6 forwarded host")
    void shouldPreserveBracketsAndExtractPort_forBracketedIpv6Host() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("[2001:db8::1]:8443");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerName()).isEqualTo("[2001:db8::1]");
        assertThat(wrapper.getServerPort()).isEqualTo(8443);
    }

    @Test
    @DisplayName("should keep a bare bracketed IPv6 host intact and infer the scheme default port")
    void shouldKeepBareBracketedIpv6HostIntact_andInferDefaultPort() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("[2001:db8::1]");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerName()).isEqualTo("[2001:db8::1]");
        assertThat(wrapper.getServerPort()).isEqualTo(443);
    }

    @Test
    @DisplayName("should not split an unbracketed IPv6 forwarded host on its colons")
    void shouldNotSplitUnbracketedIpv6Host_onItsColons() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("X-Forwarded-Host")).thenReturn("2001:db8::1");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerName()).isEqualTo("2001:db8::1");
        // No port in the header and no scheme to infer from, so the peer port stands.
        assertThat(wrapper.getServerPort()).isEqualTo(8080);
    }

    @Test
    @DisplayName("should ignore a malformed bracketed forwarded host that has no closing bracket")
    void shouldIgnoreMalformedBracketedHost_withNoClosingBracket() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getServerName()).thenReturn("internal-host");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getHeader("X-Forwarded-Host")).thenReturn("[2001:db8::1");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getServerName()).isEqualTo("internal-host");
        assertThat(wrapper.getServerPort()).isEqualTo(8080);
    }

    @Test
    @DisplayName("should accept mixed-case forwarded proto without locale-sensitive lowercasing")
    void shouldAcceptMixedCaseForwardedProto_withoutLocaleLowercasing() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("HTTPS");

        XforwardHeaderFilter.ModifyRemoteAddress wrapper =
                new XforwardHeaderFilter.ModifyRemoteAddress(
                        request, Set.of("127.0.0.1"), Set.of());

        assertThat(wrapper.getScheme()).isEqualTo("https");
        assertThat(wrapper.isSecure()).isTrue();
    }

    @Test
    @DisplayName("filter should wrap HTTP requests before continuing the chain")
    void shouldWrapHttpRequests() throws Exception {
        XforwardHeaderFilter filter = new XforwardHeaderFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20");

        filter.doFilter(request, response, chain);

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(requestCaptor.capture(), org.mockito.Mockito.eq(response));
        assertThat(requestCaptor.getValue()).isInstanceOf(HttpServletRequestWrapper.class);
    }
}
