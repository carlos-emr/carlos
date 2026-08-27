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

package io.github.carlos_emr.carlos.webserv.rest.exceptionMapping;

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.Logger;

/**
 * Catch-all mapper that converts any otherwise-unmapped {@link Throwable} into a
 * uniform {@code 500} JSON {@link ErrorResponse} instead of the servlet container's
 * default HTML error page.
 *
 * <p>This is the least specific mapper in the chain: JAX-RS selects the most specific
 * registered {@code ExceptionMapper} for a thrown type, so domain mappers (access
 * denied, validation, conversion, etc.) take precedence and only genuinely unexpected
 * failures land here.
 *
 * <p><strong>PHI safety:</strong> the full exception (including stack trace) is logged
 * server-side for diagnosis, but the client body carries only a generic, static message.
 * Exception messages are never echoed to the client because an unexpected failure may
 * embed internals, query fragments, or identifiers.
 *
 * @since 2026-06-21
 */
@Provider
public class GeneralExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    /**
     * Maps any otherwise-unmapped {@link Throwable} to a {@code 500 Internal Server Error}
     * JSON {@link ErrorResponse}. The full exception (with stack trace) is logged
     * server-side; the client body carries only a generic, static message.
     *
     * @param exception the unhandled failure
     * @return a {@code 500} JSON response with code {@code INTERNAL_ERROR}
     */
    @Override
    public Response toResponse(Throwable exception) {
        // Log the full exception (with stack trace) server-side for diagnosis.
        logger.error("Unhandled REST exception at " + safePath(), exception);

        // Deliberately generic: never expose the exception message to the client.
        ErrorResponse body = ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred while processing the request.");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
