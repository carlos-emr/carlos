/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Issues short-lived, reusable capabilities for generated PDF previews.
 *
 * <p>The browser never receives a server filesystem path. Each opaque token is bound to the exact
 * canonical CARLOS-owned temp file, HTTP session, and authenticated provider that prepared the
 * email attachment.</p>
 */
@Service
public class PdfPreviewCapabilityService {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final long MAX_CAPABILITIES = 2_000L;
    private static final int TOKEN_BYTES = 32;

    private final Cache<String, Capability> capabilities;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public PdfPreviewCapabilityService() {
        this(Clock.systemUTC());
    }

    PdfPreviewCapabilityService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.capabilities = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_CAPABILITIES)
                .ticker(() -> clock.instant().toEpochMilli() * 1_000_000L)
                .build();
    }

    public String issue(HttpServletRequest request, LoggedInInfo loggedInInfo, Path requestedPath)
            throws PDFGenerationException {
        HttpSession session = Objects.requireNonNull(request.getSession(false),
                "An authenticated session is required");
        String providerNo = requireProvider(loggedInInfo);
        Path canonicalPath = canonicalApplicationTempFile(requestedPath);
        String token = generateToken();
        capabilities.put(token, new Capability(
                session.getId(), providerNo, canonicalPath, clock.instant().plus(TTL)));
        return token;
    }

    /**
     * Resolves a capability without consuming it because an embedded PDF viewer may make multiple
     * range requests. The two-minute TTL bounds reuse.
     */
    public Path resolve(HttpServletRequest request, LoggedInInfo loggedInInfo, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        String providerNo = requireProvider(loggedInInfo);
        Capability capability = capabilities.getIfPresent(token);
        if (capability == null
                || clock.instant().isAfter(capability.expiresAt())
                || !capability.sessionId().equals(session.getId())
                || !capability.providerNo().equals(providerNo)) {
            return null;
        }
        try {
            Path currentCanonicalPath = canonicalApplicationTempFile(capability.path());
            return currentCanonicalPath.equals(capability.path()) ? currentCanonicalPath : null;
        } catch (PDFGenerationException e) {
            capabilities.invalidate(token);
            return null;
        }
    }

    private static Path canonicalApplicationTempFile(Path requestedPath) throws PDFGenerationException {
        try {
            Path canonicalPath = Objects.requireNonNull(requestedPath, "PDF path must not be null")
                    .toRealPath();
            if (!Files.isRegularFile(canonicalPath)
                    || !PathValidationUtils.isInApplicationTempDirectory(canonicalPath.toFile())) {
                throw new PDFGenerationException("PDF preview is outside the CARLOS temp directory");
            }
            return canonicalPath;
        } catch (IOException | RuntimeException e) {
            throw new PDFGenerationException("PDF preview is unavailable", e);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String requireProvider(LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null
                || loggedInInfo.getLoggedInProviderNo() == null
                || loggedInInfo.getLoggedInProviderNo().isBlank()) {
            throw new SecurityException("Authenticated provider is required");
        }
        return loggedInInfo.getLoggedInProviderNo();
    }

    private record Capability(String sessionId, String providerNo, Path path, Instant expiresAt) {
    }
}
