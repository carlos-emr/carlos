/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Issues and consumes one-time approval tickets for incomplete clinical PDF operations.
 */
@Service
public class EFormRenderApprovalService {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;
    private static final long MAX_PENDING_APPROVALS = 1_000L;
    private static final Logger logger = MiscUtils.getLogger();

    public enum Operation {
        PREVIEW,
        FAX,
        DOWNLOAD,
        EDOC
    }

    private final Cache<String, PendingApproval> approvals;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public EFormRenderApprovalService() {
        this(Clock.systemUTC());
    }

    /**
     * Test seam for the two-minute ticket lifetime.
     *
     * <p>A {@code Clock} rather than a Caffeine {@code Ticker}: the ticker would only move cache
     * eviction, while the expiry that actually rejects a ticket is the explicit
     * {@code isAfter(expiresAt)} comparison in {@link #consume}. Injecting a ticker alone would
     * leave that comparison — the thing worth testing — unreachable. The cache still gets the
     * ticker so an evicted entry and an expired one cannot disagree.</p>
     */
    EFormRenderApprovalService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.approvals = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_PENDING_APPROVALS)
                .ticker(() -> clock.instant().toEpochMilli() * 1_000_000L)
                .build();
    }

    /**
     * Stores the exact issue set displayed to the current authenticated user.
     */
    public String issue(HttpServletRequest request, LoggedInInfo loggedInInfo, int fdid,
            String demographicNo, Operation operation, EFormRenderCompletenessReport report) {
        return issue(request, loggedInInfo, fdid, demographicNo, operation, report, null, fdid);
    }

    /**
     * Stores a newly reported incomplete eForm together with any exact issue sets the user already
     * approved while rendering the same composite document.
     */
    public String issue(HttpServletRequest request, LoggedInInfo loggedInInfo, int requestFdid,
            String demographicNo, Operation operation, EFormRenderCompletenessReport report,
            EFormRenderApproval previousApproval, int renderedFdid) {
        Objects.requireNonNull(report, "report must not be null");
        if (report.isComplete()) {
            throw new IllegalArgumentException("An approval ticket requires an incomplete render");
        }
        HttpSession session = Objects.requireNonNull(request.getSession(false),
                "An authenticated session is required");
        String providerNo = requireProvider(loggedInInfo);
        String token = generateToken();
        Map<Integer, String> issueDigests = new LinkedHashMap<>();
        if (previousApproval != null) {
            if (!previousApproval.belongsTo(providerNo)) {
                throw new SecurityException("The prior approval belongs to a different provider");
            }
            // The prior approval's digests are about to be inherited wholesale, so its scope has to
            // match. Without this a composite flow that previewed a document and then reused that
            // approval object to seed a fax ticket would silently promote PREVIEW-approved omissions
            // into a FAX — the clinician consented to look at an incomplete document, not to send
            // one. The demographic half stops one patient's approved digests seeding another's.
            if (!previousApproval.coversSameScope(demographicNo, operation)) {
                throw new SecurityException(
                        "The prior approval was given for a different patient or operation");
            }
            issueDigests.putAll(previousApproval.issueDigests());
        }
        issueDigests.put(renderedFdid, report.digest());
        approvals.put(token, new PendingApproval(
                session.getId(),
                providerNo,
                requestFdid,
                Objects.requireNonNull(demographicNo, "demographicNo must not be null"),
                Objects.requireNonNull(operation, "operation must not be null"),
                Map.copyOf(issueDigests),
                clock.instant().plus(TTL)));
        logger.info(
                "Incomplete eForm render approval requested: fdid={} provider={} operation={} issues={} approvedForms={}",
                requestFdid, providerNo, operation, report.issueCount(), issueDigests.size());
        return token;
    }

    /**
     * Consumes a ticket only when it matches the current user, session, patient, form, and operation.
     */
    public EFormRenderApproval consume(HttpServletRequest request, LoggedInInfo loggedInInfo,
            int fdid, String demographicNo, Operation operation, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        String providerNo = requireProvider(loggedInInfo);
        PendingApproval pending = approvals.asMap().remove(token);
        if (pending == null
                || clock.instant().isAfter(pending.expiresAt())
                || !pending.sessionId().equals(session.getId())
                || !pending.providerNo().equals(providerNo)
                || pending.fdid() != fdid
                || !pending.demographicNo().equals(demographicNo)
                || pending.operation() != operation) {
            logger.warn(
                    "Incomplete eForm render approval rejected: fdid={} provider={} operation={}",
                    fdid, providerNo, operation);
            return null;
        }
        logger.info(
                "Incomplete eForm render approved: fdid={} provider={} operation={}",
                fdid, pending.providerNo(), operation);
        return new EFormRenderApproval(pending.providerNo(), pending.demographicNo(),
                pending.operation(), pending.issueDigests(), pending.expiresAt());
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

    private record PendingApproval(
            String sessionId,
            String providerNo,
            int fdid,
            String demographicNo,
            Operation operation,
            Map<Integer, String> issueDigests,
            Instant expiresAt) {
    }
}
