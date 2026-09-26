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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Properties;

/**
 * Site policy for a user who signs in while their other sessions are still active (issue #3980).
 *
 * <p>Read from {@code carlos.properties}. {@code CarlosProperties} loads that file once at startup,
 * so a change takes effect after CARLOS is restarted:</p>
 * <ul>
 *   <li>{@code login.concurrent_sessions.policy}: {@code allow} (default; today's behaviour),
 *       {@code prompt} (ask the user whether to keep or sign out their other sessions) or
 *       {@code single} (a new sign-in signs out the older ones automatically).</li>
 *   <li>{@code login.concurrent_sessions.max}: the most sessions one user may hold at once;
 *       {@code 0} (default) means no limit. When a new sign-in would exceed it, the user must sign
 *       out their other sessions to continue. It applies to {@code allow} and {@code prompt};
 *       {@code single} already allows only one.</li>
 * </ul>
 *
 * <p>An unrecognised value falls back to the default and logs a warning. It never fails the login,
 * because a typo in a property file must not lock every user out.</p>
 *
 * @param mode how to treat other active sessions
 * @param maxSessions most sessions per user, or {@code 0} for no limit
 * @since 2026-09-26
 */
public record ConcurrentSessionPolicy(Mode mode, int maxSessions) {

    public static final String POLICY_PROPERTY = "login.concurrent_sessions.policy";
    public static final String MAX_PROPERTY = "login.concurrent_sessions.max";

    /** The default: any number of sessions, no prompt. */
    public static final ConcurrentSessionPolicy DEFAULT = new ConcurrentSessionPolicy(Mode.ALLOW, 0);

    private static final Logger logger = MiscUtils.getLogger();

    /** How a sign-in treats the same user's other active sessions. */
    public enum Mode {
        /** Keep them; ask nothing unless {@code maxSessions} is reached. */
        ALLOW,
        /** Ask the user whether to keep them or sign them out. */
        PROMPT,
        /** Sign them out automatically. */
        SINGLE
    }

    /** What the login flow must do before it creates the new authenticated session. */
    public enum Decision {
        /** Continue; other sessions stay signed in. */
        PROCEED,
        /** Show the chooser with both "keep" and "sign out" available. */
        ASK,
        /** Show the chooser; the limit is reached, so only "sign out" is available. */
        ASK_SIGN_OUT_REQUIRED,
        /** Continue and sign the other sessions out without asking. */
        SIGN_OUT_OTHERS
    }

    public ConcurrentSessionPolicy {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (maxSessions < 0) {
            throw new IllegalArgumentException("maxSessions must not be negative");
        }
    }

    /**
     * Reads the policy from properties, falling back to {@link #DEFAULT} values for missing or
     * invalid entries.
     *
     * @param properties CARLOS properties; {@code null} yields {@link #DEFAULT}
     * @return the configured policy
     */
    public static ConcurrentSessionPolicy fromProperties(Properties properties) {
        if (properties == null) {
            return DEFAULT;
        }
        return new ConcurrentSessionPolicy(
                parseMode(properties.getProperty(POLICY_PROPERTY)),
                parseMax(properties.getProperty(MAX_PROPERTY)));
    }

    /**
     * Decides what a sign-in must do given how many of the user's other sessions are active.
     *
     * @param otherActiveSessions live sessions for the same user, not counting the one signing in
     * @return the action the login flow must take
     */
    public Decision decide(int otherActiveSessions) {
        if (otherActiveSessions <= 0) {
            return Decision.PROCEED;
        }
        if (mode == Mode.SINGLE) {
            return Decision.SIGN_OUT_OTHERS;
        }
        if (isLimitReached(otherActiveSessions)) {
            return Decision.ASK_SIGN_OUT_REQUIRED;
        }
        return mode == Mode.PROMPT ? Decision.ASK : Decision.PROCEED;
    }

    /**
     * Reports whether keeping {@code otherActiveSessions} and adding one more would exceed the
     * limit. Used again when the choice is submitted, because other sessions may have signed in
     * while the chooser was open.
     *
     * @param otherActiveSessions live sessions for the same user, not counting the one signing in
     * @return {@code true} when "keep other sessions" must not be offered
     */
    public boolean isLimitReached(int otherActiveSessions) {
        return maxSessions > 0 && otherActiveSessions + 1 > maxSessions;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive match of an administrator-set property value
    // against three ASCII enum names with Locale.ROOT; not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of an administrator-set property value against ASCII enum names with Locale.ROOT; not a security or authorization decision")
    private static Mode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT.mode();
        }
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warn("Ignoring invalid {}={}; expected allow, prompt or single. Using allow.",
                    POLICY_PROPERTY, LogSafe.sanitize(value));
            return DEFAULT.mode();
        }
    }

    private static int parseMax(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT.maxSessions();
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // Reported below with the same message as a negative value.
        }
        logger.warn("Ignoring invalid {}={}; expected a whole number, 0 for no limit. Using 0.",
                MAX_PROPERTY, LogSafe.sanitize(value));
        return DEFAULT.maxSessions();
    }
}
