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
package io.github.carlos_emr.carlos.webserv.rest.util;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Answers an {@link AccessDeniedException} that escapes a REST resource with HTTP 403.
 *
 * <p>Several REST services (pharmacy, program, prescription) refuse a caller by throwing
 * {@code AccessDeniedException(object, action[, subject])}. Unmapped, CXF lets it escape to the
 * servlet container and the client receives the generic HTTP 500 error page; this mapper makes
 * the refusal an explicit 403 with an empty body, matching {@link SecurityExceptionMapper} (#2798).
 * The log records the security object and action, never the subject, which is usually a
 * {@code demographic_no}.</p>
 *
 * @since 2026-10-08
 */
@Provider
public class AccessDeniedExceptionMapper implements ExceptionMapper<AccessDeniedException> {

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public Response toResponse(AccessDeniedException exception) {
        logger.warn("REST request refused: missing {} privilege on {}",
                LogSafe.sanitize(exception.getAction()), LogSafe.sanitize(exception.getPermission()));
        return Response.status(Response.Status.FORBIDDEN).build();
    }
}
