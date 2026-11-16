/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SecurityTokenDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.SecurityToken;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Random SOAP bearer credentials, purpose/account bound and persisted only as hashes in the existing token table. */
@Service
public class SoapTokenService {
    private static final String PREFIX = "ct1.";
    private final SecurityTokenDao tokens;
    private final SecurityInfoManager privileges;
    private final Clock clock;
    private final SecureRandom random;
    private final Duration lifetime;

    @Autowired
    public SoapTokenService(SecurityTokenDao tokens, SecurityInfoManager privileges) {
        this(tokens, privileges, Clock.systemUTC(), new SecureRandom(), configuredLifetime());
    }

    SoapTokenService(SecurityTokenDao tokens, SecurityInfoManager privileges, Clock clock,
            SecureRandom random, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("SOAP token lifetime must be positive and at most 24 hours");
        }
        this.tokens = Objects.requireNonNull(tokens);
        this.privileges = Objects.requireNonNull(privileges);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.lifetime = lifetime;
    }

    private static Duration configuredLifetime() {
        try {
            long minutes = Long.parseLong(CarlosProperties.getInstance().getProperty("soap.token.ttl.minutes", "60"));
            if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException();
            return Duration.ofMinutes(minutes);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("soap.token.ttl.minutes must be an integer from 1 through 1440");
        }
    }

    public static boolean looksLikeToken(String value) {
        return value != null && value.matches("ct1\\.[A-Za-z0-9_-]{43}");
    }

    /** Called only after LoginWs authenticates the supplied account/password or current token. */
    @Transactional
    public String issue(Security account) {
        if (!validAccount(account)) throw new SecurityException("SOAP token account is unavailable");
        long issued = clock.millis();
        long expires = Math.addExact(issued, lifetime.toMillis());
        String credential = fingerprint(account);
        for (int attempt = 0; attempt < 3; attempt++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String hash = storedHash(token);
            if (tokens.findSoapToken(hash) != null) continue;
            SecurityToken row = new SecurityToken();
            row.setToken(hash);
            row.setCreated(new Date(issued));
            row.setExpiry(new Date(expires));
            row.setProviderNo(account.getProviderNo());
            row.setData(owner(account.getId()) + issued + ":" + expires + ":" + credential);
            tokens.persist(row);
            // A shared-table digest must never become a bearer credential through a generic token consumer.
            if (tokens.getByTokenAndExpiry(hash, new Date(0)) != null) {
                throw new IllegalStateException("Token DAO does not isolate SOAP credential hashes");
            }
            LoggedInInfo actor = new LoggedInInfo();
            actor.setLoggedInSecurity(account);
            LogAction.addLogStrict(actor, "SoapToken.issue", null, null, null, "securityId=" + account.getId());
            // The proxy commits the row and audit before LoginWs receives a successful return.
            return token;
        }
        throw new IllegalStateException("SOAP token entropy collision");
    }

    @Transactional(readOnly = true)
    public boolean validate(Security account, String token) {
        if (!validAccount(account) || !looksLikeToken(token)) return false;
        SecurityToken row = tokens.findSoapToken(storedHash(token));
        if (row == null) return false;
        if (row.getData() == null) throw new IllegalStateException("Invalid SOAP token storage");
        if (!Objects.equals(row.getProviderNo(), account.getProviderNo())) return false;
        String prefix = owner(account.getId());
        if (!row.getData().startsWith(prefix)) return false;
        String[] metadata = row.getData().substring(prefix.length()).split(":", -1);
        if (metadata.length != 3) throw new IllegalStateException("Invalid SOAP token storage");
        final long issued;
        final long expires;
        try {
            issued = Long.parseLong(metadata[0]);
            expires = Long.parseLong(metadata[1]);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("Invalid SOAP token storage");
        }
        long now = clock.millis();
        if (issued < 0 || expires <= issued || expires - issued > Duration.ofHours(24).toMillis()
                || !metadata[2].matches("[0-9a-f]{64}")) throw new IllegalStateException("Invalid SOAP token storage");
        if (issued > Math.addExact(now, Duration.ofMinutes(1).toMillis()) || expires <= now) return false;
        return MessageDigest.isEqual(metadata[2].getBytes(StandardCharsets.US_ASCII),
                fingerprint(account).getBytes(StandardCharsets.US_ASCII));
    }

    /** Self-service or the existing administrative user-management write policy; never changes other token purposes. */
    @Transactional
    public int revokeAll(LoggedInInfo actor, Integer securityId) {
        if (actor == null || actor.getLoggedInSecurity() == null || securityId == null || securityId <= 0) {
            throw new SecurityException("SOAP token revocation is not authorized");
        }
        if (!securityId.equals(actor.getLoggedInSecurity().getId())
                && !privileges.hasPrivilege(actor, "_admin", "w", null)
                && !privileges.hasPrivilege(actor, "_admin.userAdmin", "w", null)) {
            throw new SecurityException("SOAP token revocation is not authorized");
        }
        int removed = tokens.revokeSoapTokens(owner(securityId));
        LogAction.addLogStrict(actor, "SoapToken.revokeAll", null, null, null, "securityId=" + securityId + " revokedCount=" + removed);
        return removed;
    }

    private boolean validAccount(Security account) {
        return account != null && account.getId() != null && account.getId() > 0 && account.getPassword() != null
                && (account.getDateExpiredate() == null || account.getDateExpiredate().getTime() > clock.millis());
    }

    private static String owner(int securityId) {
        return "soap1:" + securityId + ":";
    }

    private static String fingerprint(Security account) {
        // Length prefixes avoid delimiter collisions; changes to credentials or account binding revoke existing tokens.
        String password = account.getPassword();
        String provider = Objects.toString(account.getProviderNo(), "");
        String user = Objects.toString(account.getUserName(), "");
        return sha256(password.length() + ":" + password + provider.length() + ":" + provider + user.length() + ":" + user);
    }

    private static String storedHash(String token) {
        return "soap1:" + sha256(token);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
