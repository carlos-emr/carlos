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

import io.github.carlos_emr.carlos.commn.model.ServiceClient;

/**
 * OSCAR 19 OAuth 1.0a lifetime semantics. A duration is measured in seconds;
 * exactly -1 means no expiry. No floor, cap or one-hour fallback is applied.
 * Client settings affect new grants only; existing tokens retain their own
 * persisted issuance time and duration, including grants imported from O19.
 *
 * @since 2026-10-26
 */
public final class OAuthAccessTokenLifetime {
    private OAuthAccessTokenLifetime() { }

    /**
     * Reads the O19 duration for a newly issued grant without changing existing grants.
     *
     * @param client the registered client, or {@code null} if it no longer exists
     * @return the exact configured seconds, including zero or {@code -1} for no expiry
     * @throws OAuth1Exception with HTTP 401 if the client is absent, or HTTP 400 if its
     *         setting is absent or is a negative value other than {@code -1}
     */
    public static long forClient(ServiceClient client) {
        if (client == null) {
            throw new OAuth1Exception(401, "invalid_consumer");
        }
        Integer seconds = client.getLifetime();
        if (seconds == null || seconds < -1) {
            throw new OAuth1Exception(400, "invalid_token_lifetime");
        }
        return seconds.longValue();
    }

    /**
     * The O19 CXF validator expires a finite grant only AFTER issued + lifetime,
     * in epoch seconds. Zero is not a request for a default duration. Malformed
     * timestamps, unknown negative durations and arithmetic overflow fail closed.
     *
     * @param issued the original issuance time in epoch seconds, which must be positive
     * @param lifetime the persisted duration in seconds, or exactly {@code -1} for no expiry
     * @param now the current time in epoch seconds
     * @return {@code true} for a finite grant past its O19 expiry boundary or malformed
     *         metadata; {@code false} for a current grant, including a valid non-expiring grant
     */
    public static boolean isExpired(long issued, long lifetime, long now) {
        if (issued <= 0 || lifetime < -1) {
            return true;
        }
        if (lifetime == -1) {
            return false;
        }
        try {
            return now > Math.addExact(issued, lifetime);
        } catch (ArithmeticException _) {
            return true;
        }
    }
}
