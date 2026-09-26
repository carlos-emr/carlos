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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    /**
     * Session attribute holding the client address the session signed in from. It is shown only to
     * the same user in the concurrent-session chooser.
     */
    public static final String KEY_LOGIN_REMOTE_ADDR = "UserSessionLoginRemoteAddr";
    private static final Logger logger = MiscUtils.getLogger();
    private static final Map<Integer, Set<HttpSession>> userSessionMap = new ConcurrentHashMap<>();

    /**
     * Registers a user session with the given user sec code and HttpSession.
     * @param userSecurityCode The user sec code.
     * @param session The HttpSession.
     */
    @Override
    public void registerUserSession(Integer userSecurityCode, HttpSession session) {
        registerUserSession(userSecurityCode, session, null);
    }

    @Override
    public void registerUserSession(Integer userSecurityCode, HttpSession session, String remoteAddr) {
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
        if (remoteAddr != null) {
            // nosemgrep: tainted-session-from-http-request -- container-provided client address, rendered encoded only to the same user
            session.setAttribute(KEY_LOGIN_REMOTE_ADDR, remoteAddr);
        }
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
    @Deprecated(since = "2026.08", forRemoval = true)
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

    @Override
    public int countOtherActiveSessions(Integer userSecurityCode, HttpSession current) {
        return otherLiveSessions(userSecurityCode, current).size();
    }

    @Override
    public List<SessionInfo> describeOtherActiveSessions(Integer userSecurityCode, HttpSession current) {
        List<SessionInfo> descriptions = new ArrayList<>();
        for (HttpSession session : otherLiveSessions(userSecurityCode, current)) {
            try {
                Object remoteAddr = session.getAttribute(KEY_LOGIN_REMOTE_ADDR);
                descriptions.add(new SessionInfo(
                        Instant.ofEpochMilli(session.getCreationTime()),
                        Instant.ofEpochMilli(session.getLastAccessedTime()),
                        remoteAddr instanceof String ? (String) remoteAddr : null));
            } catch (IllegalStateException e) {
                // Invalidated between the snapshot and this read; it is no longer an active session.
                logger.debug("Skipping session invalidated while describing sessions: {}", e.getMessage());
            }
        }
        descriptions.sort(Comparator.comparing(SessionInfo::lastActiveAt).reversed());
        return List.copyOf(descriptions);
    }

    @Override
    public int invalidateOtherSessions(Integer userSecurityCode, HttpSession keep) {
        // Snapshot first: invalidate() runs OscarSessionListener synchronously on this thread, and
        // the listener's unregisterUserSession() calls computeIfPresent() on this same map entry.
        // Invalidating inside a compute lambda would be a recursive update of the map.
        int revoked = 0;
        for (HttpSession session : otherLiveSessions(userSecurityCode, keep)) {
            String sessionId = sessionIdForComparison(session);
            try {
                session.invalidate();
            } catch (IllegalStateException e) {
                // Already gone (expired or signed out concurrently): nothing to revoke or report.
                logger.debug("Session already invalidated: {}", e.getMessage());
                continue;
            }
            // Mark only after a successful invalidate so a browser whose session simply expired is
            // not later told that another sign-in removed it.
            RevokedUserSessions.mark(sessionId);
            revoked++;
        }
        if (revoked > 0) {
            logger.info("Signed out {} other session(s) for security code {}", revoked, userSecurityCode);
        }
        return revoked;
    }

    /**
     * Returns a snapshot of the user's live registered sessions, excluding {@code current}.
     */
    private List<HttpSession> otherLiveSessions(Integer userSecurityCode, HttpSession current) {
        if (userSecurityCode == null) {
            return List.of();
        }
        purgeInvalidSessions();
        Set<HttpSession> sessions = userSessionMap.get(userSecurityCode);
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        String currentId = current == null ? null : sessionIdForComparison(current);
        List<HttpSession> others = new ArrayList<>();
        for (HttpSession session : sessions) {
            if (current != null && isSameSession(session, current, currentId)) {
                continue;
            }
            if (isLive(session)) {
                others.add(session);
            }
        }
        return others;
    }

    /**
     * Removes entries for HttpSessions that have been invalidated by the container.
     * Safety net for sessions that expire without triggering OscarSessionListener.
     */
    private void purgeInvalidSessions() {
        for (Integer userSecurityCode : userSessionMap.keySet()) {
            userSessionMap.computeIfPresent(userSecurityCode, (key, sessions) -> {
                sessions.removeIf(session -> {
                    if (isLive(session)) {
                        return false;
                    }
                    logger.debug("Purging invalidated session for security code: {}", key);
                    return true;
                });
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }

    /**
     * Reports whether a session can still serve requests.
     *
     * <p>{@code getId()} is not a liveness test: neither Tomcat's session facade nor Spring's
     * {@code MockHttpSession} throws from it after invalidation. {@code getCreationTime()} does throw
     * {@link IllegalStateException} on an invalidated session in both. A session past its
     * {@code maxInactiveInterval} that the container has not reaped yet (Tomcat checks about once a
     * minute) is also treated as gone, so it is not offered to the user as an active session.</p>
     */
    private static boolean isLive(HttpSession session) {
        try {
            session.getCreationTime();
            int maxInactiveSeconds = session.getMaxInactiveInterval();
            return maxInactiveSeconds <= 0
                    || System.currentTimeMillis() - session.getLastAccessedTime() <= maxInactiveSeconds * 1000L;
        } catch (IllegalStateException e) {
            return false;
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
