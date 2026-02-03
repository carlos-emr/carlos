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

import java.util.Locale;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;

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

        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (info != null && info.getLoggedInProvider() == null) {
            // It's possible in the OAuth situation that the session is empty, but we have a valid LoggedInInfo on the request.
            info = LoggedInInfo.getLoggedInInfoFromRequest(request);
        }

        if (info == null) {
            throw new IllegalStateException("Authentication info is not available.");
        }
        return info;
    }

}
