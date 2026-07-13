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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.exception.UserSessionNotFoundException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the {@link UserSessionManager} interface.
 * This class manages user sessions using a ConcurrentHashMap to store the association between user sec codes and HttpSessions.
 */
@Service
public class UserSessionManagerImpl implements UserSessionManager {

    public static final String KEY_USER_SECURITY_CODE = "UserSecurityCode";
    private static final Logger logger = MiscUtils.getLogger();
    private static final Map<Integer, Set<HttpSession>> userSessionMap = new ConcurrentHashMap<>();

    /**
     * Registers a user session with the given user sec code and HttpSession.
     * @param userSecurityCode The user sec code.
     * @param session The HttpSession.
     */
    @Override
    public void registerUserSession(Integer userSecurityCode, HttpSession session) {
        purgeInvalidSessions();

        userSessionMap.compute(userSecurityCode, (key, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(session);
            return sessions;
        });
        // nosemgrep: tainted-session-from-http-request -- userSecurityCode is an internally generated security token, not user input
        session.setAttribute(KEY_USER_SECURITY_CODE, userSecurityCode);
        if (logger.isDebugEnabled()) {
            logger.debug("User Session successfully registered: {}", sessionIdForLog(session));
        }
    }

    /**
     * Unregisters the user session associated with the given user sec code.
     * @param userSecurityCode The user sec code.
     * @return The HttpSession that was unregistered.
     * @throws UserSessionNotFoundException If no session is found for the given user sec code.
     */
    @Override
    public HttpSession unregisterUserSession(Integer userSecurityCode) throws UserSessionNotFoundException {
        Set<HttpSession> sessions = userSessionMap.remove(userSecurityCode);
        if (sessions == null || sessions.isEmpty()) {
            throw new UserSessionNotFoundException("User session not registered");
        }

        HttpSession session = sessions.iterator().next();
        for (HttpSession registeredSession : sessions) {
            removeSecurityCodeAttribute(registeredSession);
            invalidateSession(registeredSession);
        }
        logger.debug("User Sessions successfully unregistered for security code: {}", userSecurityCode);
        return session;
    }

    @Override
    public HttpSession unregisterUserSession(Integer userSecurityCode, HttpSession session) throws UserSessionNotFoundException {
        AtomicBoolean removed = new AtomicBoolean(false);
        String sessionId = sessionIdForComparison(session);
        userSessionMap.computeIfPresent(userSecurityCode, (key, sessions) -> {
            removed.set(sessions.removeIf(registeredSession ->
                    isSameSession(registeredSession, session, sessionId)));
            return sessions.isEmpty() ? null : sessions;
        });

        if (!removed.get()) {
            throw new UserSessionNotFoundException("User session not registered");
        }

        removeSecurityCodeAttribute(session);
        if (logger.isDebugEnabled()) {
            logger.debug("User Session successfully unregistered: {}", sessionIdForLog(session));
        }
        return session;
    }

    /**
     * Retrieves the registered HttpSession for the given user sec code.
     * @param userSecurityCode The user sec code.
     * @return The HttpSession, or null if no session is found for the given user sec code.
     */
    @Override
    public HttpSession getRegisteredSession(Integer userSecurityCode) {
        Set<HttpSession> sessions = userSessionMap.get(userSecurityCode);
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.iterator().next();
    }

    /**
     * Removes entries for HttpSessions that have been invalidated by the container.
     * Safety net for sessions that expire without triggering OscarSessionListener.
     */
    private void purgeInvalidSessions() {
        for (Integer userSecurityCode : userSessionMap.keySet()) {
            userSessionMap.computeIfPresent(userSecurityCode, (key, sessions) -> {
                sessions.removeIf(session -> {
                    try {
                        session.getId(); // throws IllegalStateException if invalidated
                        return false;
                    } catch (IllegalStateException e) {
                        logger.debug("Purging invalidated session for security code: {}", key);
                        return true;
                    }
                });
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }

    private void invalidateSession(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException e) {
            logger.debug("Session already invalidated: {}", e.getMessage());
        }
    }

    private boolean isSameSession(HttpSession registeredSession, HttpSession session, String sessionId) {
        if (registeredSession == session) {
            return true;
        }
        return sessionId != null && sessionId.equals(sessionIdForComparison(registeredSession));
    }

    private String sessionIdForComparison(HttpSession session) {
        try {
            return session.getId();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private void removeSecurityCodeAttribute(HttpSession session) {
        try {
            session.removeAttribute(KEY_USER_SECURITY_CODE);
        } catch (IllegalStateException e) {
            logger.debug("Session already invalidated: {}", e.getMessage());
        }
    }

    private String sessionIdForLog(HttpSession session) {
        try {
            return session.getId();
        } catch (IllegalStateException e) {
            return "invalidated";
        }
    }
}
