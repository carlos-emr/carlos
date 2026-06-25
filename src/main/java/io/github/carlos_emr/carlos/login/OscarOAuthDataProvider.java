/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.login;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceClientDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceOAuthNonceDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao;
import io.github.carlos_emr.carlos.commn.model.ServiceAccessToken;
import io.github.carlos_emr.carlos.commn.model.ServiceClient;
import io.github.carlos_emr.carlos.commn.model.ServiceOAuthNonce;
import io.github.carlos_emr.carlos.commn.model.ServiceRequestToken;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.webserv.oauth.AccessToken;
import io.github.carlos_emr.carlos.webserv.oauth.Client;
// OAuth 1.0a - replaced cxf OAuthServiceException. 
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;
import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Permission;
import io.github.carlos_emr.carlos.webserv.oauth.RequestToken;
import io.github.carlos_emr.carlos.webserv.oauth.RequestTokenRegistration;
import io.github.carlos_emr.carlos.webserv.oauth.UserSubject;

@Component
@Transactional
public class OscarOAuthDataProvider {

    private static final String OOB_CALLBACK = "oob";

    private final org.apache.logging.log4j.Logger logger = MiscUtils.getLogger();

    @Autowired private ServiceRequestTokenDao serviceRequestTokenDao;
    @Autowired private ServiceAccessTokenDao serviceAccessTokenDao;
    @Autowired private ServiceClientDao serviceClientDao;
    @Autowired private ServiceOAuthNonceDao serviceOAuthNonceDao;

    // Throttle stale-nonce pruning so a DELETE does not run on every signed
    // request; once per interval is enough to keep the table bounded.
    private static final long NONCE_PRUNE_INTERVAL_SECONDS = 10L;
    private volatile long lastNoncePruneEpochSeconds = 0L;

    // Matches the varchar(255) columns of ServiceOAuthNonce; a legitimate
    // consumer key cannot exceed the ServiceClient key length either.
    private static final int MAX_NONCE_FIELD_LENGTH = 255;

    public Client getClient(String consumerKey) {
        logger.debug("getClient({})", consumerKey);
        ServiceClient sc = serviceClientDao.findByKey(consumerKey);
        if (sc != null) {
            Client client = new Client(sc.getKey(), sc.getSecret(), sc.getName(), sc.getUri());
            client.setCallbackUri(sc.getUri());
            return client;
        }
        return null;
    }

    public RequestToken createRequestToken(RequestTokenRegistration reg) {
        logger.debug("createRequestToken() called");
        validateCallbackAllowed(reg);
        String tokenId = UUID.randomUUID().toString();
        String tokenSecret = UUID.randomUUID().toString();

        RequestToken rt = new RequestToken(reg.getClient(), tokenId, tokenSecret);
        List<OAuth1Permission> perms = new ArrayList<>();
        StringBuilder scopeStr = new StringBuilder();

        if (reg.getScopes() != null) {
            for (String scope : reg.getScopes()) {
                perms.add(new OAuth1Permission(scope, scope));
                scopeStr.append(scope).append(" ");
            }
        }
        rt.setScopes(perms);
        rt.setCallback(reg.getCallback());

        ServiceRequestToken srt = new ServiceRequestToken();
        srt.setCallback(rt.getCallback());
        srt.setClientId(serviceClientDao.findByKey(rt.getClient().getConsumerKey()).getId());
        srt.setDateCreated(new Date());
        srt.setTokenId(rt.getTokenKey());
        srt.setTokenSecret(rt.getTokenSecret());
        srt.setScopes(scopeStr.toString().trim());
        serviceRequestTokenDao.persist(srt);

        return rt;
    }

    public RequestToken getRequestToken(String tokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(tokenId);
        if (srt == null) return null;

        // expire after 1 hour
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -1);
        if (srt.getDateCreated().before(cal.getTime())) {
            serviceRequestTokenDao.remove(srt);
            return null;
        }

        ServiceClient sc = serviceClientDao.find(srt.getClientId());
        Client client = new Client(sc.getKey(), sc.getSecret(), sc.getName(), sc.getUri());
        client.setCallbackUri(sc.getUri());

        RequestToken rt = new RequestToken(client, srt.getTokenId(), srt.getTokenSecret());
        List<OAuth1Permission> perms = new ArrayList<>();
        for (String scope : srt.getScopes().split(" ")) {
            perms.add(new OAuth1Permission(scope, scope));
        }
        rt.setScopes(perms);
        rt.setCallback(srt.getCallback());
        rt.setVerifier(srt.getVerifier());

        return rt;
    }

    // public String finalizeAuthorization(Token data) throws OAuthServiceException {
    //     logger.debug("finalizeAuthorization() called");
    //     RequestToken requestToken = data.getToken();
    //     requestToken.setVerifier(UUID.randomUUID().toString());
    //     ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
    //     if (srt != null) {
    //         srt.setVerifier(requestToken.getVerifier());
    //         serviceRequestTokenDao.merge(srt);
    //     }
    //     return requestToken.getVerifier();
    // }


    public AccessToken createAccessToken(RequestToken requestToken) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
        if (srt == null) throw new OAuth1Exception(401, "Invalid request token");

        String accessTokenId = UUID.randomUUID().toString();
        String tokenSecret = UUID.randomUUID().toString();
        long issuedAt = System.currentTimeMillis() / 1000;

        AccessToken at = new AccessToken(requestToken.getClient(), accessTokenId, tokenSecret, 3600, issuedAt);
        at.setSubject(new UserSubject(srt.getProviderNo(), new ArrayList<>()));
        at.setScopes(requestToken.getScopes());

        ServiceAccessToken sat = new ServiceAccessToken();
        ServiceClient sc = serviceClientDao.findByKey(requestToken.getClient().getConsumerKey());
        sat.setClientId(sc.getId());
        sat.setDateCreated(new Date());
        sat.setIssued(issuedAt);
        sat.setLifetime(3600);
        sat.setTokenId(accessTokenId);
        sat.setTokenSecret(tokenSecret);
        sat.setProviderNo(srt.getProviderNo());
        sat.setScopes(String.join(" ", requestToken.getScopes().stream()
            .map(OAuth1Permission::getPermission).toArray(String[]::new)));

        serviceAccessTokenDao.persist(sat);
        serviceRequestTokenDao.remove(srt);

        return at;
    }

    public AccessToken getAccessToken(String tokenId) {
        ServiceAccessToken sat = findUnexpiredAccessToken(tokenId);
        if (sat == null) return null;

        ServiceClient sc = serviceClientDao.find(sat.getClientId());
        Client client = getClient(sc.getKey());

        AccessToken at = new AccessToken(client, sat.getTokenId(), sat.getTokenSecret(),
            sat.getLifetime(), sat.getIssued());
        at.setSubject(new UserSubject(sat.getProviderNo(), new ArrayList<>()));

        List<OAuth1Permission> perms = new ArrayList<>();
        for (String scope : sat.getScopes().split(" ")) {
            perms.add(new OAuth1Permission(scope, scope));
        }
        at.setScopes(perms);

        return at;
    }

    public void removeToken(String tokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(tokenId);
        if (srt != null) serviceRequestTokenDao.remove(srt);

        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(tokenId);
        if (sat != null) serviceAccessTokenDao.remove(sat);
    }

    public String finalizeAuthorization(RequestToken requestToken) throws OAuth1Exception {
        return finalizeAuthorization(requestToken, null);
    }

    public String finalizeAuthorization(RequestToken requestToken, String providerNo) throws OAuth1Exception {
        logger.debug("finalizeAuthorization() called");
        // RequestToken requestToken = data.getToken(); - now passing the token directly. 
        requestToken.setVerifier(UUID.randomUUID().toString());
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestToken.getTokenKey());
        if (srt != null) {
            srt.setVerifier(requestToken.getVerifier());
            if (providerNo != null && !providerNo.isBlank()) {
                srt.setProviderNo(providerNo);
            }
            serviceRequestTokenDao.merge(srt);
        }
        return requestToken.getVerifier();
    }
 

    public String getAccessTokenSecret(String accessTokenId) {
        ServiceAccessToken sat = findUnexpiredAccessToken(accessTokenId);
        return sat != null ? sat.getTokenSecret() : null;
    }

    public String getProviderNoByAccessToken(String accessTokenId) {
        ServiceAccessToken sat = findUnexpiredAccessToken(accessTokenId);
        return sat != null ? sat.getProviderNo() : null;
    }

    public String getRequestTokenSecret(String requestTokenId) {
        ServiceRequestToken srt = serviceRequestTokenDao.findByTokenId(requestTokenId);
        return srt != null ? srt.getTokenSecret() : null;
    }

    /**
     * Records an OAuth 1.0a request nonce as consumed, rejecting it as a replay
     * if the same (consumerKey, tokenId, nonce) combination has already been
     * seen. Callers must only invoke this after the request signature has been
     * verified, so an attacker cannot poison the store for a legitimate client.
     *
     * @param consumerKey     the request's oauth_consumer_key (required).
     * @param tokenId         the request's oauth_token, or null for the token
     *                        initiate step; stored as an empty string when absent.
     * @param nonce           the request's oauth_nonce (required).
     * @param oauthTimestamp  the request's oauth_timestamp, retained for pruning.
     * @param retentionSeconds how long a consumed nonce must be remembered; older
     *                        entries are pruned because their timestamp can no
     *                        longer pass the freshness check and cannot be replayed.
     * @throws OAuth1Exception 401 when the nonce has already been consumed.
     */
    public void consumeNonce(String consumerKey, String tokenId, String nonce,
            long oauthTimestamp, long retentionSeconds) throws OAuth1Exception {
        // A non-positive retention would set the prune cutoff at/after "now" and
        // delete still-replayable nonces, silently disabling replay protection.
        if (retentionSeconds <= 0) {
            throw new IllegalArgumentException("retentionSeconds must be positive");
        }
        // consumerKey and nonce are mandatory; only tokenId may be absent (the
        // token-initiate step carries no oauth_token). Reject blank (including
        // whitespace-only) values explicitly rather than silently coalescing, so
        // a replay key is never built from empty values.
        if (consumerKey == null || consumerKey.isBlank() || nonce == null || nonce.isBlank()) {
            throw new OAuth1Exception(400, "invalid_oauth_parameters");
        }
        String key = consumerKey;
        String token = tokenId == null ? "" : tokenId;
        String nonceValue = nonce;

        // Reject oversized values up front so they fail with a clean OAuth error
        // instead of a DB-level fault, and so the only integrity violation the
        // insert below can raise is the unique-key duplicate (a replay).
        if (key.length() > MAX_NONCE_FIELD_LENGTH
                || token.length() > MAX_NONCE_FIELD_LENGTH
                || nonceValue.length() > MAX_NONCE_FIELD_LENGTH) {
            throw new OAuth1Exception(400, "oauth_parameter_too_long");
        }

        // Drop nonces that can no longer pass the timestamp freshness window so
        // the table stays bounded. Throttled so this DELETE does not run on
        // every signed request.
        long now = System.currentTimeMillis() / 1000;
        if (now - lastNoncePruneEpochSeconds >= NONCE_PRUNE_INTERVAL_SECONDS) {
            lastNoncePruneEpochSeconds = now;
            serviceOAuthNonceDao.deleteOlderThan(now - retentionSeconds);
        }

        ServiceOAuthNonce consumed = new ServiceOAuthNonce();
        consumed.setNonceKeyHash(nonceKeyHash(key, token, nonceValue));
        consumed.setConsumerKey(key);
        consumed.setTokenId(token);
        consumed.setNonce(nonceValue);
        consumed.setOauthTimestamp(oauthTimestamp);
        consumed.setDateCreated(new Date());
        try {
            serviceOAuthNonceDao.persist(consumed);
            // Force the INSERT now: the unique key on nonceKeyHash is the single
            // source of truth for replay detection, so a duplicate (sequential or
            // concurrent) is rejected here as a replay rather than surfacing as a
            // 500 at commit time. Only a constraint violation is treated as a
            // replay; other failures propagate so a real DB fault is not masked.
            serviceOAuthNonceDao.flush();
        } catch (DataIntegrityViolationException | ConstraintViolationException duplicate) {
            throw new OAuth1Exception(401, "nonce_replayed");
        }
    }

    /**
     * Derives the unique-key hash for a consumed nonce. Hashing a
     * length-prefixed encoding of the canonical tuple keeps the unique index
     * fixed-size and avoids both index key-length limits and ambiguity between
     * field boundaries (e.g. ("ab","c") vs ("a","bc")).
     */
    private static String nonceKeyHash(String consumerKey, String tokenId, String nonce) {
        String encoded = consumerKey.length() + ":" + consumerKey
                + "|" + tokenId.length() + ":" + tokenId
                + "|" + nonce.length() + ":" + nonce;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java platform, so this cannot happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private ServiceAccessToken findUnexpiredAccessToken(String accessTokenId) {
        ServiceAccessToken sat = serviceAccessTokenDao.findByTokenId(accessTokenId);
        if (sat == null) {
            return null;
        }
        if (isAccessTokenExpired(sat)) {
            logger.debug("Rejecting expired OAuth access token id={}", sat.getId());
            serviceAccessTokenDao.remove(sat);
            return null;
        }
        return sat;
    }

    private boolean isAccessTokenExpired(ServiceAccessToken token) {
        if (token.getLifetime() <= 0 || token.getIssued() <= 0) {
            return true;
        }
        long expiresAt;
        try {
            expiresAt = Math.addExact(token.getIssued(), token.getLifetime());
        } catch (ArithmeticException e) {
            return true;
        }
        return System.currentTimeMillis() / 1000 >= expiresAt;
    }

    private static void validateCallbackAllowed(RequestTokenRegistration reg) {
        Client client = reg.getClient();
        String registeredCallback = client == null ? null : client.getCallbackUri();
        String requestedCallback = reg.getCallback();
        if (requestedCallback == null || requestedCallback.isBlank()) {
            return;
        }
        if (registeredCallback == null || registeredCallback.isBlank()) {
            throw new OAuth1Exception(400, "callback_uri not allowed");
        }

        if (isOutOfBandCallback(registeredCallback)) {
            if (!isOutOfBandCallback(requestedCallback)) {
                throw new OAuth1Exception(400, "callback_uri not allowed");
            }
            return;
        }
        if (isOutOfBandCallback(requestedCallback)) {
            throw new OAuth1Exception(400, "callback_uri not allowed");
        }

        String registered = normalizeCallbackForComparison(registeredCallback);
        String requested = normalizeCallbackForComparison(requestedCallback);
        if (!isCallbackPrefixAllowed(requested, registered)) {
            throw new OAuth1Exception(400, "callback_uri not allowed");
        }
    }

    private static boolean isCallbackPrefixAllowed(String requested, String registered) {
        if (!requested.startsWith(registered)) {
            return false;
        }
        if (requested.length() == registered.length()) {
            return true;
        }
        char next = requested.charAt(registered.length());
        if (registered.contains("?")) {
            return next == '&' || next == '#';
        }
        if (registered.contains("#")) {
            return false;
        }
        return registered.endsWith("/") || next == '/' || next == '?' || next == '#';
    }

    private static String normalizeCallbackForComparison(String callback) {
        try {
            URI uri = URI.create(callback).normalize();
            String scheme = normalizeScheme(uri);
            if (!isHttpScheme(scheme)) {
                throw new OAuth1Exception(400, "invalid_callback_scheme");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new OAuth1Exception(400, "invalid_callback");
            }
            host = toAsciiLowerCase(host);
            int port = uri.getPort();
            if ((port == 80 && "http".equals(scheme))
                    || (port == 443 && "https".equals(scheme))) {
                port = -1;
            }
            String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
            return new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), uri.getFragment()).toString();
        } catch (OAuth1Exception e) {
            throw e;
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
    }

    private static boolean isOutOfBandCallback(String callback) {
        return asciiEqualsIgnoreCase(callback, OOB_CALLBACK);
    }

    private static String normalizeScheme(URI uri) {
        String scheme = uri.getScheme();
        return scheme == null ? null : toAsciiLowerCase(scheme);
    }

    private static boolean isHttpScheme(String scheme) {
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static boolean asciiEqualsIgnoreCase(String actual, String expected) {
        return actual != null && toAsciiLowerCase(actual).equals(expected);
    }

    private static String toAsciiLowerCase(String value) {
        StringBuilder lowered = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            lowered.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return lowered.toString();
    }

}
