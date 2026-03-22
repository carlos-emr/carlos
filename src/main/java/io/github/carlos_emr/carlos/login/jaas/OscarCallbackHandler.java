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
package io.github.carlos_emr.carlos.login.jaas;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * JAAS callback handler that supplies username and password credentials to login modules.
 *
 * <p>Handles {@link NameCallback} and {@link PasswordCallback} by providing the
 * pre-configured username and password. Throws {@link UnsupportedCallbackException}
 * for any other callback type.
 *
 * @see LoginModuleFactory
 * @see BaseLoginModule
 * @since 2026-03-17
 */
public class OscarCallbackHandler implements CallbackHandler {

    private String userName;
    private String password;

    /**
     * Constructs a callback handler with the specified credentials.
     *
     * @param userName String the username to provide to login modules
     * @param password String the password to provide to login modules
     */
    public OscarCallbackHandler(String userName, String password) {
        setUserName(userName);
        setPassword(password);
    }

    /** @return String the username */
    public String getUserName() {
        return userName;
    }

    /** @param userName String the username */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /** @return String the password */
    public String getPassword() {
        return password;
    }

    /** @param password String the password */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Handles JAAS callbacks by supplying the stored username and password.
     *
     * @param callbacks Callback[] the array of callbacks to handle
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedCallbackException if a callback type other than NameCallback or PasswordCallback is encountered
     */
    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nameCallback = (NameCallback) callback;
                nameCallback.setName(getUserName());
            } else if (callback instanceof PasswordCallback) {
                PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(getPassword().toCharArray());
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

}
