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

import io.github.carlos_emr.carlos.managers.UserSessionManager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What {@code /WEB-INF/jsp/login/sessionChoice.jsp} renders: how many other sessions the signing-in
 * user has, when and from where each was last used, and whether "keep other sessions" is allowed.
 *
 * <p>Times are formatted here, in the server's zone and the request locale, because the JSP must
 * not reach back into session objects. Nothing here identifies a session (no session id), and the
 * page encodes every value it prints.</p>
 *
 * @since 2026-09-26
 */
public final class ConcurrentSessionChoiceViewModel {

    /** Request attribute the chooser page reads this view model from. */
    public static final String REQUEST_ATTR = "concurrentSessionChoice";

    private final List<OtherSession> otherSessions;
    private final boolean signOutRequired;
    private final int maxSessions;

    /**
     * @param sessions the user's other live sessions, newest activity first
     * @param signOutRequired {@code true} when the session limit means "keep" must not be offered
     * @param maxSessions the configured limit, or {@code 0} for none
     * @param locale request locale used to format times; {@code null} uses {@link Locale#getDefault()}
     */
    public ConcurrentSessionChoiceViewModel(List<UserSessionManager.SessionInfo> sessions,
                                            boolean signOutRequired, int maxSessions, Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale == null ? Locale.getDefault() : locale)
                .withZone(ZoneId.systemDefault());
        List<OtherSession> rows = new ArrayList<>();
        if (sessions != null) {
            for (UserSessionManager.SessionInfo info : sessions) {
                rows.add(new OtherSession(
                        formatter.format(info.signedInAt()),
                        formatter.format(info.lastActiveAt()),
                        info.remoteAddr()));
            }
        }
        this.otherSessions = Collections.unmodifiableList(rows);
        this.signOutRequired = signOutRequired;
        this.maxSessions = maxSessions;
    }

    public List<OtherSession> getOtherSessions() {
        return otherSessions;
    }

    public int getOtherSessionCount() {
        return otherSessions.size();
    }

    public boolean isSignOutRequired() {
        return signOutRequired;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    /** One row of the chooser's session list. Plain getters so JSP EL can read it. */
    public static final class OtherSession {
        private final String signedInAt;
        private final String lastActiveAt;
        private final String remoteAddr;

        OtherSession(String signedInAt, String lastActiveAt, String remoteAddr) {
            this.signedInAt = signedInAt;
            this.lastActiveAt = lastActiveAt;
            this.remoteAddr = remoteAddr;
        }

        public String getSignedInAt() {
            return signedInAt;
        }

        public String getLastActiveAt() {
            return lastActiveAt;
        }

        /** Client address recorded at sign-in; may be {@code null}. */
        public String getRemoteAddr() {
            return remoteAddr;
        }
    }
}
