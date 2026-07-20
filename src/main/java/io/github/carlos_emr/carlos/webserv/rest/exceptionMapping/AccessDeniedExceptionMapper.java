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

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the domain {@link AccessDeniedException} to a {@code 403} JSON {@link ErrorResponse}.
 *
 * <p>The denied security object and action are non-sensitive and are surfaced under
 * {@code details} to help the client understand which privilege was missing. The
 * exception's {@code subject} is the demographic / record identifier
 * ({@code demographicNo}) — a PHI-correlating operational identifier — and is therefore
 * <strong>never</strong> placed in the browser-visible body. It is recorded server-side
 * (sanitized via {@link LogSafe}) so authorized operators can still trace the denial.
 *
 * @since 2026-06-21
 */
@Provider
public class AccessDeniedExceptionMapper implements ExceptionMapper<AccessDeniedException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(AccessDeniedException exception) {
        // Log with the exception so the full stack trace is captured server-side.
        logger.warn("Access denied at " + safePath()
                + " [permission=" + LogSafe.sanitize(exception.getPermission())
                + ", action=" + LogSafe.sanitize(exception.getAction())
                + ", subject=" + LogSafe.sanitize(exception.getSubject()) + "]", exception);

        Map<String, Object> details = new LinkedHashMap<>();
        if (exception.getPermission() != null) {
            details.put("permission", exception.getPermission());
        }
        if (exception.getAction() != null) {
            details.put("action", exception.getAction());
        }

        ErrorResponse body = ErrorResponse
                .of("ACCESS_DENIED", "You do not have permission to perform this action.")
                .withDetails(details);

        return Response.status(Response.Status.FORBIDDEN)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
