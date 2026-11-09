/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
@Provider
public final class RxJpaOptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {
    @Override public Response toResponse(OptimisticLockException conflict) {
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(java.util.Map.of("error", "STALE_RECORD")).build();
    }
}
