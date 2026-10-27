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
package io.github.carlos_emr.carlos.webserv.oauth;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * How long a newly issued OAuth 1.0a access token lives (issue #5288).
 *
 * <p>Contract:
 * <ul>
 *   <li>A token lasts what the administrator configured for its client in Admin, API clients
 *       ("Token Lifetime (seconds)").</li>
 *   <li>A null, zero or negative stored value means {@link #DEFAULT_LIFETIME_SECONDS} (3600 s).</li>
 *   <li>No token lasts less than {@link #MIN_LIFETIME_SECONDS} (60 s): a stored 1 to 59 becomes 60.</li>
 *   <li>No token lasts longer than the {@link #MAX_LIFETIME_PROPERTY} cap in seconds
 *       (default {@link #DEFAULT_MAX_LIFETIME_SECONDS}, 12 h). The cap also applies to the default. An absent
 *       or blank property uses the default cap silently; a present but non-integer or non-positive one uses it
 *       with a single warning per JVM start. No upper bound is imposed on the cap itself.</li>
 *   <li>The floor wins over the cap: with a cap below 60, every token lasts 60 s.</li>
 *   <li>The cap and the floor apply to <b>new tokens only</b>: a token already issued keeps the lifetime
 *       stored when it was issued.</li>
 * </ul>
 *
 * <p>The caller computes the duration once and uses the same number for the in-memory token and the stored one.
 * Everything is computed in {@code long}, so a hostile stored value cannot overflow.
 *
 * @since 2026-10-26
 */
public final class OAuthAccessTokenLifetime {

    private static final Logger logger = MiscUtils.getLogger();

    /** Lifetime, in seconds, of a token whose client has no usable configured value. */
    public static final long DEFAULT_LIFETIME_SECONDS = 3600L;

    /** Floor, in seconds; it wins over the cap. */
    static final long MIN_LIFETIME_SECONDS = 60L;

    /** The carlos.properties key holding the cap, in seconds, on a new access token's lifetime. */
    public static final String MAX_LIFETIME_PROPERTY = "oauth.access.token.max.lifetime";

    /** Cap, in seconds, used when the property is absent, blank or invalid. Keep in step with carlos.properties. */
    static final long DEFAULT_MAX_LIFETIME_SECONDS = 43200L;

    /**
     * Set once the first property warning is logged. The flag is <b>shared</b> by the two warnings ("could not
     * read" and "invalid value"): after the first, the other is not emitted until the JVM restarts. That is
     * deliberate: one warning per start is enough for the operator to review the property, and a request to
     * {@code /token} must not be able to flood the log.
     */
    private static final AtomicBoolean MAX_WARNED = new AtomicBoolean();

    private OAuthAccessTokenLifetime() {
        // static-utility holder; not instantiable
    }

    /**
     * The lifetime, in seconds, for a new access token issued to {@code client}.
     *
     * @param client the OAuth client, or null when it could not be found
     * @return seconds, never below {@link #MIN_LIFETIME_SECONDS}
     */
    public static long forClient(ServiceClient client) {
        long max = DEFAULT_MAX_LIFETIME_SECONDS;
        String raw = null;
        boolean readable = true;
        try {
            raw = CarlosProperties.getInstance().getProperty(MAX_LIFETIME_PROPERTY);
        } catch (RuntimeException e) {
            readable = false;
            // Log only the property name and the default applied, never a configured value.
            warnOnce("Could not read {}; using {} seconds", MAX_LIFETIME_PROPERTY, DEFAULT_MAX_LIFETIME_SECONDS, e);
        }
        if (readable && raw != null && !raw.isBlank()) {
            OptionalLong parsed = parseMax(raw);
            if (parsed.isPresent()) {
                max = parsed.getAsLong();
            } else {
                // The raw value is deliberately not logged: it is operator input of unknown content.
                warnOnce("{} is not a positive whole number of seconds; using {} seconds",
                        MAX_LIFETIME_PROPERTY, DEFAULT_MAX_LIFETIME_SECONDS);
            }
        }
        return resolve(client == null ? null : client.getLifetime(), max);
    }

    /**
     * Applies the default, the cap and the floor to a stored client lifetime. The outer {@code max} makes the
     * floor win over a cap below it. All arithmetic is in {@code long}, so no stored int can overflow.
     */
    static long resolve(Integer configured, long max) {
        long effective = (configured == null || configured <= 0) ? DEFAULT_LIFETIME_SECONDS : configured.longValue();
        return Math.max(MIN_LIFETIME_SECONDS, Math.min(effective, max));
    }

    /**
     * Parses the cap. Pure: no logging. Empty means "invalid" (null, blank, not an integer, overflowing, or
     * not positive), which lets the caller tell it apart from a valid value.
     */
    static OptionalLong parseMax(String value) {
        if (value == null || value.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long n = Long.parseLong(value.trim());
            return n <= 0 ? OptionalLong.empty() : OptionalLong.of(n);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static void warnOnce(String message, Object... args) {
        if (MAX_WARNED.compareAndSet(false, true)) {
            logger.warn(message, args);
        }
    }

    /** Re-arms the one-warning-per-JVM flag. For tests only; production code must never call it. */
    static void resetWarningForTest() {
        MAX_WARNED.set(false);
    }
}
