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
 */

package io.github.carlos_emr.carlos.managers;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.exception.UserSessionNotFoundException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of the {@link UserSessionManager} interface.
 * This class manages user sessions using a HashMap to store the association between user sec codes and HttpSessions.
 */
@Service
public class UserSessionManagerImpl implements UserSessionManager {

    public static final String KEY_USER_SECURITY_CODE = "UserSecurityCode";
    private static final Logger logger = MiscUtils.getLogger();
    private static final Map<Integer, HttpSession> userSessionMap = new HashMap<>();

    /**
     * Registers a user session with the given user sec code and HttpSession.
     * If a session is already registered for the given user sec code, it is invalidated before registering the
     * new provided session.
     * @param userSecurityCode The user sec code.
     * @param session The HttpSession.
     */
    @Override
    public void registerUserSession(Integer userSecurityCode, HttpSession session) {
        if (this.isUserSessionRegistered(userSecurityCode))
            // Explicitly invalidate the previous session if it exists
            try {
                HttpSession previousSession = userSessionMap.get(userSecurityCode);
                logger.debug("Existing User Session found, invalidating: {}", previousSession.getId());

                previousSession.invalidate();

                logger.debug("Previous user session successfully invalidated");
            } catch (IllegalStateException e) {
                MiscUtils.getLogger().debug(e.getMessage());
            }

        // Register the new session, overwrite previous registry
        userSessionMap.put(userSecurityCode, session);
        session.setAttribute(KEY_USER_SECURITY_CODE, userSecurityCode);
        logger.debug("User Session successfully registered: {}", session.getId());
    }

    /**
     * Unregisters the user session associated with the given user sec code.
     * @param userSecurityCode The user sec code.
     * @return The HttpSession that was unregistered.
     * @throws UserSessionNotFoundException If no session is found for the given user sec code.
     */
    @Override
    public HttpSession unregisterUserSession(Integer userSecurityCode) throws UserSessionNotFoundException {
        if (this.isUserSessionRegistered(userSecurityCode)) {
            HttpSession session = userSessionMap.remove(userSecurityCode);
            session.removeAttribute(KEY_USER_SECURITY_CODE);
            logger.debug("User Session successfully unregistered: {}", session.getId());
            return session;
        } else {
            throw new UserSessionNotFoundException("User session not registered");
        }
    }

    /**
     * Retrieves the registered HttpSession for the given user sec code.
     * @param userSecurityCode The user sec code.
     * @return The HttpSession.
     * @throws UserSessionNotFoundException If no session is found for the given user sec code.
     */
    @Override
    public HttpSession getRegisteredSession(Integer userSecurityCode) {
        if (this.isUserSessionRegistered(userSecurityCode)) {
            return userSessionMap.get(userSecurityCode);
        } else {
            throw new UserSessionNotFoundException("User session not registered");
        }
    }

    /**
     * Checks if a session is registered for the given user sec code.
     *
     * @param userSecurityCode The user sec code.
     * @return True if the session is registered, false otherwise.
     */
    private boolean isUserSessionRegistered(Integer userSecurityCode) {
        return Objects.nonNull(userSessionMap.get(userSecurityCode));
    }
}
