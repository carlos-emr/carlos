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
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.response.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/** Returns a stable client-safe error; diagnostic messages, paths and identifiers stay redacted. */
@Provider
public class AccessDeniedExceptionMapper implements ExceptionMapper<AccessDeniedException> {
    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public Response toResponse(AccessDeniedException exception) {
        RestErrorDiagnostics.log(logger, Level.WARN, exception);
        ErrorResponse body = ErrorResponse.of("ACCESS_DENIED", "You do not have permission to perform this action.");
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        if (exception.getPermission() != null && java.util.Set.of("_rx", "_demographic", "_appointment", "_measurement", "_tickler",
                "_edoc", "_provider", "_billing", "_allergy", "_prevention").contains(exception.getPermission())) {
            details.put("permission", exception.getPermission());
        }
        if (exception.getAction() != null && java.util.Set.of("r", "w", "u", "d", "x").contains(exception.getAction())) {
            details.put("action", exception.getAction());
        }
        body.withDetails(details);
        return Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON).entity(body).build();
    }
}
