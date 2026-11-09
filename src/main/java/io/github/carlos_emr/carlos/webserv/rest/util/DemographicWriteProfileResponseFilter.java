/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Acknowledge only a write profile actually selected by this request's resource method. */
@Provider
public final class DemographicWriteProfileResponseFilter implements ContainerResponseFilter {
    @Override public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        DemographicWriteProfile profile = DemographicWriteProfile.selected(PhaseInterceptorChain.getCurrentMessage());
        if (profile != null) response.getHeaders().putSingle(DemographicWriteProfile.HEADER, profile.wireName());
    }
}
