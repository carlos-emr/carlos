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
 * The single reading of the {@code oauth.scope.enforcement.enabled} switch (issues #3083, #4419).
 *
 * <p>Scope enforcement is <b>on unless an operator explicitly turns it off</b>. The switch shipped
 * default-off with #3083, which was harmless while the OAuth JAX-RS servers were never published. #4415
 * published {@code /ws/oauth} and {@code /ws/services} on every packaged install, and an app a provider
 * approved for one listed scope could then call every read and write that provider can. So an absent or
 * blank value now means enabled, and only a recognised "off" value ({@code false}, {@code no},
 * {@code off}) disables it. Any other value, including a typo, keeps enforcement on.
 *
 * <p>The three readers ({@code OscarRequestTokenService} at {@code /initiate}, {@code OAuthInterceptor} on
 * every {@code /ws/services} call, and the consent page) must agree, which is why they all ask here.
 */
public final class OAuthScopeEnforcement {

    /** The carlos.properties key. Absent or blank means enabled. */
    public static final String PROPERTY = "oauth.scope.enforcement.enabled";

    private static final Logger logger = MiscUtils.getLogger();

    private OAuthScopeEnforcement() {
        // static-utility holder; not instantiable
    }

    /**
     * Whether OAuth 1.0a scopes are enforced.
     *
     * <p>A configuration read failure leaves enforcement <em>on</em>. Failing open would hand every token
     * its provider's full API access, which is the defect this switch exists to prevent; failing closed
     * costs an integrator a 403 until the configuration is readable.
     *
     * @return {@code false} only when the property is explicitly set to {@code false}, {@code no} or
     *         {@code off} (case-insensitive); {@code true} otherwise
     */
    public static boolean isEnabled() {
        try {
            return !isExplicitlyDisabled(CarlosProperties.getInstance().getProperty(PROPERTY));
        } catch (RuntimeException e) {
            logger.warn("Could not read {}; enforcing OAuth scopes", PROPERTY, e);
            return true;
        }
    }

    /** Package-private for the unit test: the value-to-decision rule without the singleton. */
    static boolean isExplicitlyDisabled(String value) {
        if (value == null) {
            return false;
        }
        // ASCII-only fold, as elsewhere in this package: the values are ASCII tokens, and a
        // locale-sensitive fold adds nothing while tripping the IMPROPER_UNICODE scanner.
        StringBuilder folded = new StringBuilder();
        for (char c : value.trim().toCharArray()) {
            folded.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        String v = folded.toString();
        return v.equals("false") || v.equals("no") || v.equals("off");
    }
}
