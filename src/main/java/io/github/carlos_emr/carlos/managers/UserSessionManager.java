/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.exception.UserSessionNotFoundException;

import javax.servlet.http.HttpSession;

/**
 * Manages user sessions.  Provides methods to register, unregister, and retrieve user sessions based on a user sec code.
 * This interface is implemented by a service class that handles the actual session management logic.
 *
 */
public interface UserSessionManager {

    /**
     * Registers a user session.
     * @param userSecurityCode The user's sec code.
     * @param session The HTTP session object.
     */
    void registerUserSession(Integer userSecurityCode, HttpSession session);

    /**
     * Unregisters a user session.
     * @param userSecurityCode The user's sec code.
     * @return The unregistered HTTP session object.
     * @throws UserSessionNotFoundException If the user session is not found.
     */
    HttpSession unregisterUserSession(Integer userSecurityCode) throws UserSessionNotFoundException;

    /**
     * Retrieves a registered user session.
     * @param userSecurityCode The user's sec code.
     * @return The registered HTTP session object, or null if not found.
     */
    HttpSession getRegisteredSession(Integer userSecurityCode);
}
