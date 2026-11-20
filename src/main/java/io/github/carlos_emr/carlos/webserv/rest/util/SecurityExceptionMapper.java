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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Answers a {@link SecurityException} that escapes a REST resource with HTTP 403.
 *
 * <p>REST services refuse a caller who lacks a security object by throwing
 * {@code SecurityException("missing required sec object (_x)")}, the same convention the Struts
 * actions follow (#2798). Without a mapper CXF lets the exception escape to the servlet container,
 * which answers with the generic HTTP 500 error page: the call fails closed, but a client cannot
 * tell a permission refusal from a server fault. Registered on both REST surfaces ({@code /ws/rs}
 * in {@code spring_ws.xml}, {@code /ws/services} in {@code applicationContextREST.xml}).</p>
 *
 * <p>The body is empty, as for a {@code jakarta.ws.rs.ForbiddenException}, so nothing about the
 * refused resource reaches the caller. The log names the security object only when the message
 * follows the convention; any other message is not logged, because a SecurityException raised
 * deeper in a call may carry a record identifier.</p>
 *
 * @since 2026-10-08
 */
@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    private static final Logger logger = MiscUtils.getLogger();

    private static final Pattern MISSING_OBJECT = Pattern.compile("^missing required sec object \\((_[\\w.$]+)\\)$");

    @Override
    public Response toResponse(SecurityException exception) {
        Matcher missing = MISSING_OBJECT.matcher(String.valueOf(exception.getMessage()));
        if (missing.matches()) {
            logger.warn("REST request refused: missing required sec object ({})", missing.group(1));
        } else {
            logger.warn("REST request refused by a {}", exception.getClass().getSimpleName());
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }
}
