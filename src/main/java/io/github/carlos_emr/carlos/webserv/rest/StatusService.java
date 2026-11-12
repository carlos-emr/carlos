/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Authentication probe for REST clients.
 *
 * <p><b>Authorization (#2798).</b> {@code /checkIfAuthed} is deliberately not gated by a security
 * object. It is the call a client makes to learn whether its own session or OAuth token is still
 * valid, and it returns only the caller's own provider number, which the caller already holds.
 * There is no patient, configuration or other provider's data to protect, and gating it on an
 * object would be circular: a user without that object would be told they are logged out. It still
 * fails closed for anonymous callers. {@code AuthenticationInInterceptor} answers 401 on
 * {@code /ws/rs} without a logged-in session, {@code OAuthInterceptor} rejects {@code /ws/services}
 * requests that carry no valid OAuth credentials, and {@code getLoggedInInfo()} throws when no
 * caller is attached to the request.</p>
 */
@Path("/status")
@Consumes(MediaType.APPLICATION_JSON)
public class StatusService extends AbstractServiceImpl {

    @Autowired
    ProviderManager providerManager;

    /**
     * Confirms the caller is authenticated.
     *
     * @return a success response whose body is the caller's own provider number
     */
    @GET
    @Path("/checkIfAuthed")
    @Produces("application/json")
    public RestResponse<String> checkIfAuthed() {

        return RestResponse.successResponse(getLoggedInInfo().getLoggedInProviderNo());
    }

}
