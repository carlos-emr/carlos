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
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Returns an explicit refusal without exposing a permission, patient id, or clinical exception message.
 *
 * <p>This provider lives in {@code webserv.rest.util}, not beside the REST services: the
 * {@link WebServiceLoggingAdvice} pointcut covers every public method of a class directly in
 * {@code webserv.rest}, and {@code <aop:aspectj-autoproxy proxy-target-class="true"/>} would then
 * proxy this final class with CGLIB, which fails the web application context at startup.</p>
 */
@Provider
public final class AccessDeniedExceptionMapper implements ExceptionMapper<AccessDeniedException> {
    @Override
    public Response toResponse(AccessDeniedException exception) {
        return Response.status(Response.Status.FORBIDDEN).type(MediaType.TEXT_PLAIN)
                .entity("Access denied").build();
    }
}
