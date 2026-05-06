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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Map<CacheKey, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, Object> tokenLocks = new ConcurrentHashMap<>();
    private final Clock clock;

    public RingCentralAuthService() {
        this(Clock.systemUTC());
    }

    RingCentralAuthService(Clock clock) {
        this.clock = clock;
    }

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
        CacheKey cacheKey = CacheKey.from(faxConfig);
        CachedToken cachedToken = cacheKey == null ? null : tokenCache.get(cacheKey);
        if (cachedToken != null && cachedToken.isUsable(clock)) {
            return cachedToken.accessToken;
        }

        if (cacheKey == null) {
            return authenticate(faxConfig, apiConnector).getAccessToken();
        }

        Object lock = tokenLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        String accessToken;
        synchronized (lock) {
            cachedToken = tokenCache.get(cacheKey);
            if (cachedToken != null && cachedToken.isUsable(clock)) {
                return cachedToken.accessToken;
            }
            RingCentralResponse.Token token = authenticate(faxConfig, apiConnector);
            long expiresIn = token.getExpiresIn() > 0 ? token.getExpiresIn() : 3600;
            accessToken = token.getAccessToken();
            tokenCache.put(cacheKey, new CachedToken(token.getAccessToken(),
                    Instant.now(clock).plusSeconds(Math.max(1, expiresIn - TOKEN_EXPIRY_SKEW_SECONDS))));
        }
        tokenCache.keySet().removeIf(existingKey -> existingKey.hasSameAccountId(cacheKey) && !existingKey.equals(cacheKey));
        return accessToken;
    }

    private RingCentralResponse.Token authenticate(FaxConfig faxConfig, RingCentralApiConnector apiConnector) throws RingCentralException {
        RingCentralResponse.Token token = apiConnector.authenticate(
                faxConfig.getRingCentralClientId(),
                faxConfig.getRingCentralClientSecret(),
                faxConfig.getRingCentralJwtToken());

        if (token == null) {
            throw new RingCentralException("RingCentral OAuth response was empty");
        }
        if (StringUtils.isBlank(token.getAccessToken())) {
            throw new RingCentralException("RingCentral OAuth response did not include an access token");
        }
        return token;
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

        private boolean isUsable(Clock clock) {
            return StringUtils.isNotBlank(accessToken) && Instant.now(clock).isBefore(expiresAt);
        }
    }

    private static class CacheKey {
        private final Integer accountId;
        private final String credentialFingerprint;

        private CacheKey(Integer accountId, String credentialFingerprint) {
            this.accountId = accountId;
            this.credentialFingerprint = credentialFingerprint;
        }

        private static CacheKey from(FaxConfig faxConfig) throws RingCentralException {
            if (faxConfig.getId() == null) {
                return null;
            }
            return new CacheKey(faxConfig.getId(), fingerprint(faxConfig));
        }

        private boolean hasSameAccountId(CacheKey other) {
            return other != null && Objects.equals(accountId, other.accountId);
        }

        private static String fingerprint(FaxConfig faxConfig) throws RingCentralException {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(StringUtils.defaultString(faxConfig.getRingCentralClientId()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(StringUtils.defaultString(faxConfig.getRingCentralClientSecret()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(StringUtils.defaultString(faxConfig.getRingCentralJwtToken()).getBytes(StandardCharsets.UTF_8));
                byte[] hash = digest.digest();
                StringBuilder encoded = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    encoded.append(HEX[(b >>> 4) & 0x0F]);
                    encoded.append(HEX[b & 0x0F]);
                }
                return encoded.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RingCentralException("Unable to fingerprint RingCentral credentials", e);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheKey)) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(accountId, cacheKey.accountId)
                    && Objects.equals(credentialFingerprint, cacheKey.credentialFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, credentialFingerprint);
        }
    }
}
