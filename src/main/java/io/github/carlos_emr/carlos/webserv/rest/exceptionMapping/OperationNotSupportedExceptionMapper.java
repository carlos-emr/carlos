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

import javax.naming.OperationNotSupportedException;

import org.apache.logging.log4j.Logger;

/**
 * Maps a {@link javax.naming.OperationNotSupportedException} to a {@code 400 Bad Request}
 * JSON {@link ErrorResponse}.
 *
 * <p>REST endpoints (e.g. {@code RxWebService#drugs}) throw this checked exception when the
 * requested operation/status is not one the endpoint supports. It is treated as a client
 * error: the caller asked for something the contract does not offer.
 *
 * @since 2026-06-21
 */
@Provider
public class OperationNotSupportedExceptionMapper
        implements ExceptionMapper<OperationNotSupportedException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(OperationNotSupportedException exception) {
        logger.debug("Unsupported operation requested at " + safePath()
                + ": " + LogSafe.sanitize(exception.getMessage()));

        ErrorResponse body = ErrorResponse.of(
                "UNSUPPORTED_OPERATION",
                "The requested operation is not supported.");

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String safePath() {
        return uriInfo == null ? "unknown" : LogSafe.sanitizeUri(uriInfo.getPath());
    }
}
