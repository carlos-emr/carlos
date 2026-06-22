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
 * Maps an {@link IllegalArgumentException} to a {@code 400 Bad Request} JSON
 * {@link ErrorResponse}.
 *
 * <p>This is the mapper that fixes the reported defect: an unknown path segment such as
 * {@code /rx/drugs/active/1} reaches {@code RxStatus.valueOf("ACTIVE")}, which throws
 * {@code IllegalArgumentException}. Previously that propagated uncaught and produced an
 * HTML {@code 500}; now it becomes a structured {@code 400}. Because
 * {@code NumberFormatException} extends {@code IllegalArgumentException}, malformed numeric
 * path/query parameters are covered by the same mapper.
 *
 * <p><strong>PHI safety:</strong> the raw message (e.g. {@code "No enum constant
 * ...RxStatus.ACTIVE"}) names internal classes, so it is logged server-side only; the
 * client receives a generic validation message.
 *
 * @since 2026-06-21
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        logger.debug("Rejected invalid request parameter at " + safePath()
                + ": " + LogSafe.sanitize(exception.getMessage()));

        ErrorResponse body = ErrorResponse.of(
                "VALIDATION_ERROR",
                "One or more request parameters are invalid or malformed.");

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
