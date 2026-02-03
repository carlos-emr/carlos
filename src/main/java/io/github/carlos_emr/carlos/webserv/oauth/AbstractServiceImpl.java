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
 */

/*
 * Written by Brandon Aubie <brandon@aubie.ca>
 */

/**
 * File: AbstractServiceImpl.java
 *
 * Purpose:
 *   Provides a common base for OAuth-related web service implementations in OSCAR.
 *   Centralizes access to request context, authenticated provider info, and
 *   OAuth access tokens.
 *
 * Responsibilities:
 *   • Retrieve the current HttpServletRequest from the CXF PhaseInterceptorChain.
 *   • Access the bound OAuth1AccessToken from the current CXF message.
 *   • Resolve provider identifiers (providerNo) stored by OAuthInterceptor.
 *   • Expose convenience methods for retrieving the authenticated Provider
 *     and LoggedInInfo objects.
 *
 * Context / Why Added:
 *   Used by OAuth-enabled web service controllers to reduce boilerplate and
 *   ensure consistent request/context handling across endpoints.
 *
 * Notes:
 *   • Assumes OAuthInterceptor has already attached authentication context.
 *   • Stateless; per-request data is always pulled from the current CXF message.
 *   • Throws IllegalStateException if authentication context is missing,
 *     ensuring endpoints fail fast when accessed without valid OAuth.
 */


package io.github.carlos_emr.carlos.webserv.oauth;

import javax.servlet.http.HttpServletRequest;

import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import com.github.scribejava.core.model.OAuth1AccessToken;

/**
 * Base class for OAuth www services
 */
public abstract class AbstractServiceImpl {

    protected HttpServletRequest getHttpServletRequest() {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
        return (request);
    }

    protected OAuth1AccessToken getOAuthAccessToken() {
        Message m = PhaseInterceptorChain.getCurrentMessage();
        OAuth1AccessToken token = m.getContent(OAuth1AccessToken.class);
        return token;
    }
    
    protected String getOAuthProviderNo() {
        HttpServletRequest request = getHttpServletRequest();
        return (String) request.getAttribute("oauth.provider.no");
    }

    protected Provider getCurrentProvider() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        return (loggedInInfo.getLoggedInProvider());
    }

    protected LoggedInInfo getLoggedInInfo() {

        /* The OAuthInterceptor already put its loggedInInfo into the request. */
        HttpServletRequest request = getHttpServletRequest();
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromRequest(request);
        if (info == null) {
            throw new IllegalStateException("Authentication info is not available.");

        }
        return info;
    }

}
