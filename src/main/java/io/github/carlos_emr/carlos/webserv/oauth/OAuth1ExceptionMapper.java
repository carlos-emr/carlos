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
package io.github.carlos_emr.carlos.webserv.oauth;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps an {@link OAuth1Exception} thrown out of an OAuth 1.0a JAX-RS resource
 * (e.g. the request-token /initiate endpoint) to the HTTP status it carries,
 * instead of letting it surface as a generic 500. Resources such as
 * {@code OscarRequestTokenService} are written to throw {@code OAuth1Exception}
 * and rely on this mapper to render the controlled error response. Registered
 * only on the OAuth jaxrs:server, which is the only place {@code OAuth1Exception}
 * is raised.
 */
@Provider
public class OAuth1ExceptionMapper implements ExceptionMapper<OAuth1Exception> {

    @Override
    public Response toResponse(OAuth1Exception exception) {
        return Response.status(exception.getHttpCode())
                .entity(exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
