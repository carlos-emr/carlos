/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Issues and consumes one-time approval tickets for incomplete clinical PDF operations.
 */
@Service
public class EFormRenderApprovalService {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration STAGED_FAX_TTL = Duration.ofMinutes(10);
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
    private final Cache<String, PendingApproval> stagedFaxApprovals;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    public EFormRenderApprovalService() {
        this(Clock.systemUTC());
    }

    /**
     * Test seam for the two-minute lifetime of non-staged approval tickets.
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
        this.stagedFaxApprovals = Caffeine.newBuilder()
                // Session cleanup is the primary lifecycle boundary, but a short independent expiry
                // limits how long an abandoned PHI-bearing preview can survive a still-live session.
                .expireAfterWrite(STAGED_FAX_TTL)
                .maximumSize(MAX_PENDING_APPROVALS)
                .scheduler(Scheduler.systemScheduler())
                .ticker(() -> clock.instant().toEpochMilli() * 1_000_000L)
                .executor(Runnable::run)
                .removalListener((String token, PendingApproval pending, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (pending != null && pending.stagedPreview() != null) {
                        pending.stagedPreview().deleteUnlessClaimed();
                    }
                })
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
                Map.copyOf(issueDigests), 0,
                clock.instant().plus(TTL), null));
        logger.info(
                "Incomplete eForm render approval requested: fdid={} provider={} operation={} issues={} approvedForms={}",
                requestFdid, providerNo, operation, report.issueCount(), issueDigests.size());
        return token;
    }

    /**
     * Issues the package-scoped capability used only while rendering a non-deliverable fax preview.
     * The normal ticket is issued only after the render reports blocking omissions.
     */
    public EFormRenderApproval stagedFaxPreviewApproval(HttpServletRequest request,
            LoggedInInfo loggedInInfo, int fdid, String demographicNo) {
        if (fdid <= 0 || demographicNo == null || demographicNo.isBlank()
                || request.getSession(false) == null) {
            throw new IllegalArgumentException("A staged fax preview requires an authenticated eForm scope");
        }
        requireProvider(loggedInInfo);
        return EFormRenderApproval.forStagedFaxPreview();
    }

    /** Stores a completed, non-deliverable fax preview beside its exact one-time approval. */
    public String issueStagedFaxPreview(HttpServletRequest request, LoggedInInfo loggedInInfo, int fdid,
            String demographicNo, Map<Integer, EFormRenderCompletenessReport> formReports,
            int advisoryIssueCount, Path path) {
        if (formReports == null || formReports.values().stream()
                .noneMatch(report -> report != null && report.hasBlockingOmissions())
                || advisoryIssueCount < 0
                || path == null || !Files.isRegularFile(path)
                || !PathValidationUtils.isInApplicationTempDirectory(path.toFile())) {
            throw new IllegalArgumentException("A staged fax preview requires an incomplete application-temporary PDF");
        }
        HttpSession session = Objects.requireNonNull(request.getSession(false), "An authenticated session is required");
        String providerNo = requireProvider(loggedInInfo);
        Map<Integer, String> digests = new LinkedHashMap<>();
        formReports.forEach((renderedFdid, report) -> {
            if (report != null && report.hasBlockingOmissions()) {
                digests.put(renderedFdid, report.digest());
            }
        });
        String token = generateToken();
        stagedFaxApprovals.put(token, new PendingApproval(session.getId(), providerNo, fdid,
                Objects.requireNonNull(demographicNo, "demographicNo must not be null"), Operation.FAX,
                Map.copyOf(digests), advisoryIssueCount, clock.instant().plus(STAGED_FAX_TTL),
                new StagedFaxPreview(path)));
        logger.info("Incomplete eForm fax preview staged: fdid={} provider={} approvedForms={}",
                fdid, providerNo, digests.size());
        return token;
    }

    /** Claims the staged PDF atomically; a ticket can never be used to render or deliver twice. */
    public StagedFaxPreview consumeStagedFaxPreview(HttpServletRequest request, LoggedInInfo loggedInInfo,
            int fdid, String demographicNo, String token) {
        HttpSession session = request.getSession(false);
        if (token == null || token.isBlank() || session == null) return null;
        String providerNo = requireProvider(loggedInInfo);
        PendingApproval pending = stagedFaxApprovals.getIfPresent(token);
        if (pending == null) {
            return null;
        }
        if (clock.instant().isAfter(pending.expiresAt())
                || !matchesStagedFaxScope(pending, session, providerNo, fdid, demographicNo)) {
            stagedFaxApprovals.invalidate(token);
            return null;
        }
        Path path = pending.stagedPreview().claim();
        if (path == null) {
            // A concurrent duplicate submission owns the claimed file; it must be left intact.
            return null;
        }
        // Claim transfers ownership to this invocation. Cache invalidation may race this removal,
        // but the removal listener deliberately leaves claimed files alone; do not destroy the
        // legitimate fax input merely because its bookkeeping entry was already removed.
        stagedFaxApprovals.asMap().remove(token, pending);
        if (!Files.isReadable(path) || !Files.isRegularFile(path)
                || !PathValidationUtils.isInApplicationTempDirectory(path.toFile())) {
            pending.stagedPreview().deleteClaimed();
            return null;
        }
        return new StagedFaxPreview(path, new EFormRenderApproval(pending.providerNo(), pending.demographicNo(),
                pending.operation(), pending.issueDigests(), pending.expiresAt()),
                pending.advisoryIssueCount());
    }

    /**
     * Revokes an unclaimed staged fax preview after the clinician cancels the approval prompt.
     *
     * <p>The full cached scope must match so possession of a token outside its authenticated
     * session cannot be used to delete another clinician's in-progress preview.</p>
     */
    public boolean cancelStagedFaxPreview(HttpServletRequest request, LoggedInInfo loggedInInfo,
            int fdid, String demographicNo, String token) {
        HttpSession session = request.getSession(false);
        if (token == null || token.isBlank() || session == null) {
            return false;
        }
        String providerNo = requireProvider(loggedInInfo);
        PendingApproval pending = stagedFaxApprovals.getIfPresent(token);
        if (pending == null
                || !matchesStagedFaxScope(pending, session, providerNo, fdid, demographicNo)) {
            return false;
        }
        boolean removed = stagedFaxApprovals.asMap().remove(token, pending);
        stagedFaxApprovals.cleanUp();
        if (removed) {
            logger.info("Incomplete eForm fax preview cancelled: fdid={} provider={}",
                    fdid, providerNo);
        }
        return removed;
    }

    /**
     * Removes every unclaimed staged fax preview belonging to a servlet session. Invoked from the
     * session-destruction listener so logging out or timing out of the authenticated
     * session cannot leave its PHI-bearing temporary PDFs available for later approval.
     */
    public void invalidateStagedFaxPreviewsForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        stagedFaxApprovals.asMap().forEach((token, pending) -> {
            if (sessionId.equals(pending.sessionId())) {
                stagedFaxApprovals.invalidate(token);
            }
        });
        stagedFaxApprovals.cleanUp();
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
                || pending.stagedPreview() != null
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

    /**
     * Test seam: returns the token's currently-cached staged preview object without consuming or
     * removing it, so a test can hold the same object reference a concurrent
     * {@link #consumeStagedFaxPreview} caller would have captured before a racing revocation
     * (cancel, expiry, invalidation, or eviction) removes the cache entry.
     */
    StagedFaxPreview peekStagedFaxPreviewForTest(String token) {
        PendingApproval pending = stagedFaxApprovals.getIfPresent(token);
        return pending == null ? null : pending.stagedPreview();
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

    private static boolean matchesStagedFaxScope(PendingApproval pending, HttpSession session,
            String providerNo, int fdid, String demographicNo) {
        return pending.stagedPreview() != null
                && pending.sessionId().equals(session.getId())
                && pending.providerNo().equals(providerNo)
                && pending.fdid() == fdid
                && pending.demographicNo().equals(demographicNo)
                && pending.operation() == Operation.FAX;
    }

    /**
     * A one-time staged fax packet claimed from an authenticated approval ticket.
     *
     * <p>Once {@link EFormRenderApprovalService#consumeStagedFaxPreview} returns this value,
     * ownership of {@link #path()} is transferred to the fax pipeline, which must delete the
     * temporary PDF after use. Unclaimed paths remain service-owned and are deleted when their
     * ticket expires, is invalidated or evicted, or its servlet session ends. {@link #approval()}
     * contains the exact blocking-issue approval for the staged packet.
     * {@link #advisoryIssueCount()} preserves the sanitized fax-preview count.</p>
     *
     * @since 2026-07-28
     */
    public static final class StagedFaxPreview {
        /**
         * AVAILABLE -&gt; CLAIMED (by {@link #claim()}) and AVAILABLE -&gt; REVOKED (by the first call
         * to {@link #deleteUnlessClaimed()}) are the only transitions out of AVAILABLE, both a
         * single CAS, so the two can never both win for the same file — the claim-vs-delete race
         * this state exists to prevent. REVOKED is a one-way terminal state: once cancellation,
         * expiry, session invalidation, or cache eviction has decided this preview is no longer
         * valid, it must never become claimable again, no matter whether the underlying file
         * delete succeeds. An earlier version reverted this state to AVAILABLE on a failed delete
         * so a transient failure would not strand the file — but a caller that already read this
         * object out of the cache (a request racing the exact revocation that is running
         * concurrently) could then still win {@link #claim()} against the reverted state and hand
         * a revoked approval's PDF to the fax pipeline. Retrying the delete itself stays possible
         * without reopening AVAILABLE: {@link #deleteUnlessClaimed()} keeps retrying
         * {@code Files.deleteIfExists} on every call once REVOKED, it just never claims again. The
         * managed temp root's 24h stale-output sweep
         * ({@link EFormBrowserPdfService#sweepStaleRendererRoots}) remains the filesystem-level
         * backstop if this bookkeeping is never revisited at all.
         */
        private enum ClaimState { AVAILABLE, CLAIMED, REVOKED }

        private final Path path;
        private final EFormRenderApproval approval;
        private final int advisoryIssueCount;
        private final AtomicReference<ClaimState> state = new AtomicReference<>(ClaimState.AVAILABLE);

        private StagedFaxPreview(Path path) { this(path, null, 0); }
        private StagedFaxPreview(Path path, EFormRenderApproval approval, int advisoryIssueCount) {
            this.path = path;
            this.approval = approval;
            this.advisoryIssueCount = advisoryIssueCount;
        }
        /** Returns the caller-owned temporary PDF path. */
        public Path path() { return path; }

        /** Returns the exact approval metadata bound to this staged packet. */
        public EFormRenderApproval approval() { return approval; }

        /** Returns the number of non-blocking render conditions reported for the packet. */
        public int advisoryIssueCount() { return advisoryIssueCount; }
        // Package-private (not private) solely so the test seam above can prove a revoked
        // preview stays permanently unclaimable even when a caller races the revocation with the
        // same object reference; production callers reach this only through
        // consumeStagedFaxPreview().
        Path claim() {
            return state.compareAndSet(ClaimState.AVAILABLE, ClaimState.CLAIMED) ? path : null;
        }
        private void deleteUnlessClaimed() {
            boolean revokedNow = state.compareAndSet(ClaimState.AVAILABLE, ClaimState.REVOKED);
            if (!revokedNow && state.get() != ClaimState.REVOKED) {
                // Already claimed: a legitimate claimant owns this file now, leave it alone.
                return;
            }
            // Either this call just revoked it, or a previous call already did and this is a
            // retry of the delete itself -- Files.deleteIfExists is idempotent, so re-attempting
            // it here is always safe and never reopens claim().
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                logger.warn("Unable to delete unclaimed staged fax preview PDF: {}", path, e);
            }
        }
        private void deleteClaimed() {
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                logger.warn("Unable to delete claimed staged fax preview PDF: {}", path, e);
            }
        }
    }

    private record PendingApproval(
            String sessionId,
            String providerNo,
            int fdid,
            String demographicNo,
            Operation operation,
            Map<Integer, String> issueDigests,
            int advisoryIssueCount,
            Instant expiresAt,
            StagedFaxPreview stagedPreview) {
    }
}
