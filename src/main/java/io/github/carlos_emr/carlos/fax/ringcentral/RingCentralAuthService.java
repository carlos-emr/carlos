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
import java.time.Clock;
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
    private static final int TOKEN_LOCK_STRIPES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    });

    private final Map<Integer, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final Object[] tokenLocks = new Object[TOKEN_LOCK_STRIPES];
    private final Clock clock;

    public RingCentralAuthService() {
        this(Clock.systemUTC());
    }

    RingCentralAuthService(Clock clock) {
        this.clock = clock;
        for (int i = 0; i < tokenLocks.length; i++) {
            tokenLocks[i] = new Object();
        }
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
        Integer cacheKey = faxConfig.getId();
        String credentialFingerprint = fingerprint(faxConfig);
        CachedToken cachedToken = cacheKey == null ? null : tokenCache.get(cacheKey);
        if (cachedToken != null && cachedToken.isUsable(clock, credentialFingerprint)) {
            return cachedToken.accessToken;
        }

        if (cacheKey == null) {
            return authenticate(faxConfig, apiConnector).getAccessToken();
        }

        Object lock = lockFor(cacheKey);
        String accessToken;
        synchronized (lock) {
            cachedToken = tokenCache.get(cacheKey);
            if (cachedToken != null && cachedToken.isUsable(clock, credentialFingerprint)) {
                return cachedToken.accessToken;
            }
            RingCentralResponse.Token token = authenticate(faxConfig, apiConnector);
            long expiresIn = token.getExpiresIn() > 0 ? token.getExpiresIn() : 3600;
            accessToken = token.getAccessToken();
            tokenCache.put(cacheKey, new CachedToken(token.getAccessToken(), credentialFingerprint,
                    Instant.now(clock).plusSeconds(Math.max(1, expiresIn - TOKEN_EXPIRY_SKEW_SECONDS))));
        }
        return accessToken;
    }

    private Object lockFor(Integer cacheKey) {
        return tokenLocks[Math.floorMod(cacheKey.hashCode(), tokenLocks.length)];
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
        private final String credentialFingerprint;
        private final Instant expiresAt;

        private CachedToken(String accessToken, String credentialFingerprint, Instant expiresAt) {
            this.accessToken = accessToken;
            this.credentialFingerprint = credentialFingerprint;
            this.expiresAt = expiresAt;
        }

        private boolean isUsable(Clock clock, String currentCredentialFingerprint) {
            return StringUtils.isNotBlank(accessToken)
                    && StringUtils.equals(credentialFingerprint, currentCredentialFingerprint)
                    && Instant.now(clock).isBefore(expiresAt);
        }
    }

    private static String fingerprint(FaxConfig faxConfig) throws RingCentralException {
        try {
            MessageDigest digest = SHA_256.get();
            digest.reset();
            updateCredentialPart(digest, faxConfig.getRingCentralClientId());
            updateCredentialPart(digest, faxConfig.getRingCentralClientSecret());
            updateCredentialPart(digest, faxConfig.getRingCentralJwtToken());
            byte[] hash = digest.digest();
            StringBuilder encoded = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                encoded.append(HEX[(b >>> 4) & 0x0F]);
                encoded.append(HEX[b & 0x0F]);
            }
            return encoded.toString();
        } catch (IllegalStateException e) {
            throw new RingCentralException("Unable to fingerprint RingCentral credentials", e);
        }
    }

    private static void updateCredentialPart(MessageDigest digest, String value) {
        byte[] bytes = StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
