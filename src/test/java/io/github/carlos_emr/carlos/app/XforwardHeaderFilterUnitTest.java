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
    @DisplayName("should honor first forwarded client IP when peer is trusted exactly")
    void shouldHonorHeaderWhenPeerIsTrustedExactly() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.20, 127.0.0.1");

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
