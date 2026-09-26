/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.UserSessionManager;
import io.github.carlos_emr.carlos.managers.UserSessionManagerImpl;

import jakarta.servlet.http.HttpSession;

/**
 * What a completed sign-in does with the same user's other sessions (issue #3980), and when.
 *
 * <p>Other sessions are signed out only once the new login has fully completed. For a provider in
 * one facility that is the end of {@code Login2Action}'s setup. A provider in several facilities
 * finishes on {@code /select_facility}, which can still end the new session (an unauthorized or
 * missing facility), so there the decision is deferred: the session carries only the decision's
 * name, and {@code SelectFacility2Action} settles it after the facility is applied. If the new
 * session ends first, the deferred decision ends with it and no other session is touched.</p>
 *
 * @since 2026-09-26
 */
public final class OtherSessionSettlement {

    /** Session attribute holding a deferred {@link Mode} name until facility selection succeeds. */
    public static final String DEFERRED_ATTR = "pendingOtherSessionSettlement";

    /** What to do with the user's other sessions. */
    public enum Mode {
        /** No decision was needed; other sessions are left alone and nothing extra is audited. */
        LEAVE,
        /** The user chose to keep them (or an AJAX client could not be asked); audited. */
        KEEP_BY_USER,
        /** The user chose to sign them out; audited with the count. */
        SIGN_OUT_BY_USER,
        /** The {@code single} policy signed them out; audited with the count. */
        SIGN_OUT_BY_POLICY
    }

    private OtherSessionSettlement() {
        // Static settlement rules.
    }

    /**
     * Signs out or audits the user's other sessions, keeping {@code keep}. Runs under the per-user
     * admission lock so it cannot interleave with another sign-in's count.
     *
     * @param sessions session registry
     * @param securityNo security row of the signed-in user
     * @param keep the new, fully signed-in session
     * @param providerNo provider number for the audit row
     * @param ip client address for the audit row
     * @param mode the decision taken at sign-in
     */
    static void settle(UserSessionManager sessions, Integer securityNo, HttpSession keep, String providerNo,
                       String ip, Mode mode) {
        ConcurrentSessionAdmission.serializeUnchecked(securityNo, () -> {
            switch (mode) {
                case SIGN_OUT_BY_USER, SIGN_OUT_BY_POLICY -> {
                    int revoked = sessions.invalidateOtherSessions(securityNo, keep);
                    LogAction.addLog(providerNo, LogConst.LOGIN,
                            mode == Mode.SIGN_OUT_BY_POLICY
                                    ? "concurrent_sessions_revoked_auto" : "concurrent_sessions_revoked",
                            String.valueOf(revoked), ip);
                }
                case KEEP_BY_USER -> LogAction.addLog(providerNo, LogConst.LOGIN, "concurrent_sessions_kept",
                        String.valueOf(sessions.countOtherActiveSessions(securityNo, keep)), ip);
                default -> {
                    // LEAVE: no policy decision was taken; nothing to audit beyond the login itself.
                }
            }
        });
    }

    /**
     * Records the decision on the new session until facility selection completes.
     *
     * @param session the new, signed-in session waiting on {@code /select_facility}
     * @param mode the decision; {@link Mode#LEAVE} records nothing
     */
    static void defer(HttpSession session, Mode mode) {
        if (mode != Mode.LEAVE) {
            session.setAttribute(DEFERRED_ATTR, mode.name()); // nosemgrep: tainted-session-from-http-request -- server-chosen enum name, not request data
        }
    }

    /**
     * Settles a decision deferred by {@link #defer} once the facility has been applied. A session
     * with nothing deferred is left alone.
     *
     * @param sessions session registry
     * @param session the signed-in session that just completed facility selection
     * @param ip client address for the audit row
     */
    public static void completeDeferred(UserSessionManager sessions, HttpSession session, String ip) {
        if (session == null || !(session.getAttribute(DEFERRED_ATTR) instanceof String deferred)) {
            return;
        }
        session.removeAttribute(DEFERRED_ATTR);
        Mode mode;
        try {
            mode = Mode.valueOf(deferred);
        } catch (IllegalArgumentException unknown) {
            return;
        }
        if (session.getAttribute(UserSessionManagerImpl.KEY_USER_SECURITY_CODE) instanceof Integer securityNo
                && session.getAttribute("user") instanceof String providerNo) {
            settle(sessions, securityNo, session, providerNo, ip, mode);
        }
    }
}
