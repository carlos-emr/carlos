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

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * The single reading of the OAuth access-mode switches: {@code oauth.scope.enforcement.enabled} and
 * {@code oauth.scope.legacy.access} (issues #3083, #4419).
 *
 * <p>The default, with neither property set, is {@link Mode#LEGACY_RESTRICTED}: scopes are not checked and
 * an OAuth client may call only the legacy integration endpoints ({@link OAuthScopes#isLegacyRestrictedAllowed}),
 * which is what the legacy patient-engagement integration needs and nothing more. An operator turns scope
 * enforcement on with {@code oauth.scope.enforcement.enabled=true} ({@link Mode#SCOPED}: each call needs the
 * scope its endpoint requires), or widens legacy access with {@code oauth.scope.legacy.access=full}
 * ({@link Mode#LEGACY_FULL}: everything the approving provider can do). Only those exact values change the
 * mode; an absent, blank or unrecognised value leaves the default. In every mode the endpoints in
 * {@link OAuthScopes#isAlwaysBlocked} stay closed.
 *
 * <p>Both properties are server-wide: they apply to every OAuth client at once.
 *
 * <p>The three readers ({@code OscarRequestTokenService} at {@code /initiate}, {@code OAuthInterceptor} on
 * every {@code /ws/services} call, and the consent page) must agree, which is why they all ask here.
 */
public final class OAuthScopeEnforcement {

    /**
     * The carlos.properties key that turns scope enforcement on. Only {@code true}, {@code yes} or
     * {@code on} (case-insensitive) enable it; absent, blank or anything else means the legacy modes.
     */
    public static final String PROPERTY = "oauth.scope.enforcement.enabled";

    /**
     * What an OAuth client may call while {@link #PROPERTY} is not on: {@code restricted} (the default)
     * admits only the legacy integration endpoints ({@link OAuthScopes#isLegacyRestrictedAllowed}); {@code full}
     * admits everything the provider can do, less {@link OAuthScopes#isAlwaysBlocked}. Ignored while
     * enforcement is on.
     */
    public static final String LEGACY_ACCESS_PROPERTY = "oauth.scope.legacy.access";

    /** The three ways {@code /ws/services} can gate an OAuth client. */
    public enum Mode {
        /** Each call needs the scope its endpoint requires ({@code oauth.scope.enforcement.enabled=true}). */
        SCOPED,
        /** Scopes are not checked; only the legacy integration endpoints may be called. The default. */
        LEGACY_RESTRICTED,
        /** Scopes are not checked; every endpoint except the always-blocked ones may be called. */
        LEGACY_FULL
    }

    private static final Logger logger = MiscUtils.getLogger();

    private OAuthScopeEnforcement() {
        // static-utility holder; not instantiable
    }

    /**
     * Whether OAuth 1.0a scopes are enforced, i.e. whether {@link #mode()} is {@link Mode#SCOPED}.
     *
     * @return {@code true} only when the property is explicitly {@code true}, {@code yes} or {@code on}
     */
    public static boolean isEnabled() {
        return mode() == Mode.SCOPED;
    }

    /**
     * The gating mode in force. {@link Mode#SCOPED} only for an explicit on value of {@link #PROPERTY};
     * otherwise {@link Mode#LEGACY_FULL} only for an explicit {@code full} {@link #LEGACY_ACCESS_PROPERTY},
     * and {@link Mode#LEGACY_RESTRICTED} for everything else, including both properties absent.
     *
     * <p>A configuration read failure is {@link Mode#LEGACY_RESTRICTED}: the smallest surface of the three,
     * so a broken configuration can never widen what a token may call.
     *
     * @return the mode; never {@code null}
     */
    public static Mode mode() {
        try {
            CarlosProperties properties = CarlosProperties.getInstance();
            if (isExplicitlyEnabled(properties.getProperty(PROPERTY))) {
                return Mode.SCOPED;
            }
            return isFullLegacyAccess(properties.getProperty(LEGACY_ACCESS_PROPERTY))
                    ? Mode.LEGACY_FULL : Mode.LEGACY_RESTRICTED;
        } catch (RuntimeException e) {
            logger.warn("Could not read {}; limiting OAuth clients to the legacy integration endpoints", PROPERTY, e);
            return Mode.LEGACY_RESTRICTED;
        }
    }

    /** Package-private for the unit test: only the three recognised on values enable enforcement. */
    static boolean isExplicitlyEnabled(String value) {
        if (value == null) {
            return false;
        }
        // ASCII-only fold, shared with OAuthScopes: the values are ASCII tokens, and a
        // locale-sensitive fold adds nothing while tripping the IMPROPER_UNICODE scanner.
        String v = OAuthScopes.asciiLowerCase(value.trim());
        return v.equals("true") || v.equals("yes") || v.equals("on");
    }

    /** Package-private for the unit test: only the one recognised value opens full legacy access. */
    static boolean isFullLegacyAccess(String value) {
        return value != null && OAuthScopes.asciiLowerCase(value.trim()).equals("full");
    }
}
