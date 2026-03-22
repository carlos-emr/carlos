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
package io.github.carlos_emr.carlos.utility;

import java.util.HashMap;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;

import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.ext.WSPasswordCallback;

/**
 * CXF outbound interceptor that adds WS-Security UsernameToken authentication
 * headers to SOAP web service requests.
 *
 * <p>Configures WSS4J with UsernameToken action using plaintext password type
 * and acts as its own {@link CallbackHandler} to provide the password during
 * token generation.
 *
 * @since 2026-03-17
 */
public class AuthenticationOutWSS4JInterceptor extends WSS4JOutInterceptor implements CallbackHandler {
    private String password = null;

    /**
     * Creates an interceptor configured for UsernameToken authentication.
     *
     * @param user     Object the username (converted to String via {@code toString()})
     * @param password String the password for the UsernameToken
     */
    public AuthenticationOutWSS4JInterceptor(Object user, String password) {
        this.password = password;
        HashMap<String, Object> properties = new HashMap();
        properties.put("action", "UsernameToken");
        properties.put("user", user.toString());
        properties.put("passwordType", "PasswordText");
        properties.put("passwordCallbackRef", this);
        this.setProperties(properties);
    }

    /**
     * Handles WSS4J password callbacks by setting the stored password on any
     * {@link WSPasswordCallback} instances.
     *
     * @param callbacks Callback[] the security callbacks to handle
     */
    public void handle(Callback[] callbacks) {
        Callback[] arr$ = callbacks;
        int len$ = callbacks.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            Callback callback = arr$[i$];
            if (callback instanceof WSPasswordCallback) {
                WSPasswordCallback wsPasswordCallback = (WSPasswordCallback) callback;
                wsPasswordCallback.setPassword(this.password);
            }
        }

    }
}
