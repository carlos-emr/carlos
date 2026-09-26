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

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;

/**
 * Manages user sessions.  Provides methods to register, unregister, and retrieve user sessions based on a user sec code.
 * This interface is implemented by a service class that handles the actual session management logic.
 *
 * <p>One user may hold several authenticated sessions at once (issue #2565). The concurrent-session
 * policy added for issue #3980 uses {@link #countOtherActiveSessions}, {@link #describeOtherActiveSessions}
 * and {@link #invalidateOtherSessions} to tell a user about their other sessions and to sign them
 * out. The "sign out other sessions" chooser concept follows open-osp/Open-O PR #136 (Chitrank
 * Davé, 2025); the CARLOS API differs because the chooser runs before the new session exists.</p>
 *
 * <p>The registry is held in memory by one JVM. It does not see sessions on other Tomcat nodes,
 * so a clustered deployment enforces the policy per node.</p>
 */
public interface UserSessionManager {

    /**
     * Registers a user session.
     * @param userSecurityCode The user's sec code.
     * @param session The HTTP session object.
     */
    void registerUserSession(Integer userSecurityCode, HttpSession session);

    /**
     * Registers a user session and records the address it signed in from, so the concurrent-session
     * chooser can show the user where their other sessions are.
     *
     * @param userSecurityCode the user's sec code
     * @param session the newly authenticated HTTP session
     * @param remoteAddr client address of the sign-in request; may be {@code null}
     */
    void registerUserSession(Integer userSecurityCode, HttpSession session, String remoteAddr);

    /**
     * Unregisters a user session.
     * @param userSecurityCode The user's sec code.
     * @return The unregistered HTTP session object.
     * @throws UserSessionNotFoundException If the user session is not found.
     * @deprecated No production code calls this. It signs out every session for the user, including
     *             the caller's. Use {@link #invalidateOtherSessions(Integer, HttpSession)}, which keeps
     *             the current session and records the revocation for the signed-out browsers.
     */
    @Deprecated(since = "2026.08", forRemoval = true)
    HttpSession unregisterUserSession(Integer userSecurityCode) throws UserSessionNotFoundException;

    /**
     * Unregisters one user session without affecting other active sessions for the same user.
     * @param userSecurityCode The user's sec code.
     * @param session The HTTP session object to unregister.
     * @return The unregistered HTTP session object.
     * @throws UserSessionNotFoundException If the user session is not found.
     */
    HttpSession unregisterUserSession(Integer userSecurityCode, HttpSession session) throws UserSessionNotFoundException;

    /**
     * Retrieves a registered user session.
     * @param userSecurityCode The user's sec code.
     * @return The registered HTTP session object, or null if not found.
     */
    HttpSession getRegisteredSession(Integer userSecurityCode);

    /**
     * Counts the user's live registered sessions other than {@code current}.
     *
     * <p>Sessions the container has already invalidated are purged first and are not counted.</p>
     *
     * @param userSecurityCode the user's sec code
     * @param current session to leave out of the count; may be {@code null} to count every session
     * @return number of other live sessions, never negative
     */
    int countOtherActiveSessions(Integer userSecurityCode, HttpSession current);

    /**
     * Describes the user's live registered sessions other than {@code current}, newest activity
     * first. The result carries no session id, so it is safe to render to the signed-in user.
     *
     * @param userSecurityCode the user's sec code
     * @param current session to leave out; may be {@code null}
     * @return immutable list of session descriptions; empty when there are none
     */
    List<SessionInfo> describeOtherActiveSessions(Integer userSecurityCode, HttpSession current);

    /**
     * Signs out every registered session for the user except {@code keep}.
     *
     * <p>Each session is invalidated, which runs {@code OscarSessionListener}: it releases the
     * session's case-note locks and unregisters it. Each signed-out session is also recorded so its
     * browser is told why on its next request (see {@link RevokedUserSessions}).</p>
     *
     * @param userSecurityCode the user's sec code
     * @param keep session that must stay signed in; may be {@code null} to sign out all of them
     * @return number of sessions signed out
     */
    int invalidateOtherSessions(Integer userSecurityCode, HttpSession keep);

    /**
     * Display-safe description of one registered session.
     *
     * @param signedInAt when the session was created
     * @param lastActiveAt when the session last received a request
     * @param remoteAddr client address recorded at sign-in; may be {@code null} for sessions
     *                   registered without one
     */
    record SessionInfo(Instant signedInAt, Instant lastActiveAt, String remoteAddr) {
    }
}
