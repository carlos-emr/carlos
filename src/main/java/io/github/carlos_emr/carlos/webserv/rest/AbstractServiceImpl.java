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

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;

import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;


/**
 * Base class for RESTful www services
 */
@Produces({"application/xml"})
@Consumes({"application/xml"})
public abstract class AbstractServiceImpl {

    protected HttpServletRequest getHttpServletRequest() {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
        return (request);
    }

    /**
     * Gets the values of one HTTP header of the request being served.
     *
     * <p>Read from CXF's current message, like {@link #getHttpServletRequest()}. Do not give a
     * resource a {@code @Context} field for this, or for any other per-request value. The published
     * resources are singletons behind a Spring CGLIB proxy (the REST audit advice applies to every
     * one of them). CXF injects a {@code @Context} field into the object it was given, which is the
     * proxy; the resource method runs on the proxy's target, where the field is still {@code null}.
     * A {@code @Context} method parameter is passed through the proxy and is not affected.</p>
     *
     * @param name the header name, matched without regard to case
     * @return one entry for each header line the request carries, in order and not split on
     *     commas, or {@code null} when the request has no such header
     * @throws IllegalStateException when called outside the handling of a REST request
     */
    protected List<String> getRequestHeader(String name) {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        if (message == null) {
            // Never read as "no such header": a caller may be asking whether a guard was requested.
            throw new IllegalStateException("No REST request is being processed.");
        }
        // What CXF's own injected HttpHeaders delegates to for this message.
        return new HttpHeadersImpl(message).getRequestHeader(name);
    }

    /**
     * Gets the client's locale
     *
     * @return
     */
    protected Locale getLocale() {
        return getHttpServletRequest().getLocale();
    }

    /**
     * Get the UI resource bundle for locale specific messages
     *
     * @return
     */
    protected ResourceBundle getResourceBundle() {
        return ResourceBundle.getBundle("uiResources", getLocale());
    }

    /**
     * Gets current providers.
     *
     * @return Returns the providers authenticated for the current request processing.
     */
    protected Provider getCurrentProvider() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        return (loggedInInfo.getLoggedInProvider());
    }

    /**
     * Gets the login information associated with the current request.
     *
     * @return Returns the login information
     * @throws IllegalStateException IllegalStateException is thrown in case authentication info is not available
     */
    protected LoggedInInfo getLoggedInInfo() {

        Message message = PhaseInterceptorChain.getCurrentMessage();
        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);

        // The request first: only server code puts a principal there, and on /ws/services it is the
        // provider OAuthInterceptor just authenticated from the signed access token. That caller has
        // no session, so the old session-first lookup failed every OAuth data call (issue #3446).
        // The session /ws/rs surface keeps its principal in the session. getSession(false), so an
        // OAuth call does not leave a session behind.
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromRequest(request);
        if (info == null) {
            HttpSession session = request.getSession(false);
            info = session == null ? null : LoggedInInfo.getLoggedInInfoFromSession(session);
        }

        if (info == null) {
            throw new IllegalStateException("Authentication info is not available.");
        }
        return info;
    }

}
