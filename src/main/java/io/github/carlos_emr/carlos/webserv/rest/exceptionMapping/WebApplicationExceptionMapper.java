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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.Logger;

/**
 * Maps a {@link WebApplicationException} to a JSON {@link ErrorResponse} while preserving
 * the HTTP status the application deliberately chose when it threw.
 *
 * <p>Without this mapper, a {@code WebApplicationException} carrying, say, a {@code 404}
 * would still render through the container's HTML error page once the body is reshaped by
 * the rest of the chain. Here we keep the original status code and surface a stable,
 * machine-readable {@code code} derived from that status.
 *
 * <p><strong>PHI safety:</strong> the client message is the standard HTTP reason phrase for
 * the status, never the raw exception message (which can embed request fragments). The
 * original message is recorded server-side only.
 *
 * @since 2026-06-21
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        int statusCode = exception.getResponse() != null
                ? exception.getResponse().getStatus()
                : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

        Response.Status status = Response.Status.fromStatusCode(statusCode);
        String reason = status != null ? status.getReasonPhrase() : "Request failed";
        String code = status != null ? status.name() : "REQUEST_FAILED";

        // 5xx are genuine server faults worth an error log; 4xx are client errors logged at debug.
        if (statusCode >= 500) {
            logger.error("REST WebApplicationException (" + statusCode + ") at "
                    + safePath() + ": " + LogSafe.sanitize(exception.getMessage()), exception);
        } else {
            logger.debug("REST WebApplicationException (" + statusCode + ") at "
                    + safePath() + ": " + LogSafe.sanitize(exception.getMessage()));
        }

        ErrorResponse body = ErrorResponse.of(code, reason);

        return Response.status(statusCode)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
