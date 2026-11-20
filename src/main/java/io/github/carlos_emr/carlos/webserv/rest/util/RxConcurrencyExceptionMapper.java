/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;
import io.github.carlos_emr.carlos.managers.RxSourceVersionValidator;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
@Provider
public final class RxConcurrencyExceptionMapper implements ExceptionMapper<RxSourceVersionValidator.Conflict> {
    @Override public Response toResponse(RxSourceVersionValidator.Conflict conflict) {
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(java.util.Map.of("error", "RX_SOURCE_CHANGED")).build();
    }
}
