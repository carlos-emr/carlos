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
package io.github.carlos_emr.carlos.fax.ringcentral;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Manages RingCentral OAuth 2.0 JWT bearer tokens for fax provider calls.
 *
 * @since 2026-05-05
 */
@Service
public class RingCentralAuthService {

    private static final long TOKEN_EXPIRY_SKEW_SECONDS = 60;

    private final Map<Integer, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Returns a usable access token for the given fax account.
     *
     * @param faxConfig RingCentral fax account configuration
     * @param apiConnector connector used to request new OAuth tokens
     * @return Bearer token string
     * @throws RingCentralException if credentials are missing or token acquisition fails
     */
    public String getAccessToken(FaxConfig faxConfig, RingCentralApiConnector apiConnector) throws RingCentralException {
        validateCredentials(faxConfig);
        Integer cacheKey = faxConfig.getId();
        CachedToken cachedToken = cacheKey == null ? null : tokenCache.get(cacheKey);
        if (cachedToken != null && cachedToken.isUsable()) {
            return cachedToken.accessToken;
        }

        RingCentralResponse.Token token = apiConnector.authenticate(
                faxConfig.getRingCentralClientId(),
                faxConfig.getRingCentralClientSecret(),
                faxConfig.getRingCentralJwtToken());

        if (StringUtils.isBlank(token.getAccessToken())) {
            throw new RingCentralException("RingCentral OAuth response did not include an access token");
        }

        if (cacheKey != null) {
            long expiresIn = token.getExpiresIn() > 0 ? token.getExpiresIn() : 3600;
            tokenCache.put(cacheKey, new CachedToken(token.getAccessToken(),
                    Instant.now().plusSeconds(Math.max(1, expiresIn - TOKEN_EXPIRY_SKEW_SECONDS))));
        }
        return token.getAccessToken();
    }

    /**
     * Validates required RingCentral OAuth credentials.
     *
     * @param faxConfig fax account configuration
     * @throws RingCentralException when any required credential is missing
     */
    void validateCredentials(FaxConfig faxConfig) throws RingCentralException {
        if (faxConfig == null) {
            throw new RingCentralException("RingCentral fax configuration is required");
        }
        if (StringUtils.isBlank(faxConfig.getRingCentralClientId())) {
            throw new RingCentralException("RingCentral client ID is not configured for this fax account");
        }
        if (StringUtils.isBlank(faxConfig.getRingCentralClientSecret())) {
            throw new RingCentralException("RingCentral client secret is not configured for this fax account");
        }
        if (StringUtils.isBlank(faxConfig.getRingCentralJwtToken())) {
            throw new RingCentralException("RingCentral JWT token is not configured for this fax account");
        }
    }

    private static class CachedToken {
        private final String accessToken;
        private final Instant expiresAt;

        private CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        private boolean isUsable() {
            return StringUtils.isNotBlank(accessToken) && Instant.now().isBefore(expiresAt);
        }
    }
}
