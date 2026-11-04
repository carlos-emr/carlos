/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A session's one current, expiring draft. Four process-wide slots bound retained original-byte
 * custody to at most 80 MiB (the capture boundary also limits each original to 20 MiB).
 * Generation reserves a slot before loading bytes. Replacement, logout and expiry release custody.
 * Authorization, clinical privilege and CSRF remain enforced by each calling action.
 */
public final class DocumentSummaryReviewSession implements HttpSessionBindingListener, AutoCloseable {
    public static final String SESSION_KEY = DocumentSummaryReviewSession.class.getName();
    public static final String ENABLED = "clinical.ai_document_summary.review.enabled";
    public static final String REGION = "clinical.ai_document_summary.review.region";
    /** How many reviews one CARLOS process may hold at once (each keeps its original, up to 20 MiB). */
    public static final String MAX_ACTIVE = "clinical.ai_document_summary.review.max_active";
    private static final int DEFAULT_MAX_REVIEWS = 4;
    private static final int MAX_CONFIGURABLE_REVIEWS = 32;
    private static final Object LOCK = new Object();
    private static final int MAX_REVIEWS = configuredCapacity(configuredValue());
    private static final Set<DocumentSummaryReviewSession> ACTIVE = new HashSet<>();
    private static int reserved;
    private final Clock clock;
    private final DocumentSummaryReviewBinding binding;
    private final DocumentSummaryReviewBinding.Scope scope;
    private final String provider;
    private final int securityId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private DocumentSummaryReviewBinding.CapturedSource captured;
    private DocumentSummaryReviewBinding.Draft draft;
    private DocumentSummaryReviewBinding.Approval approval;
    private boolean closed;

    @FunctionalInterface public interface SourceLoader { byte[] load() throws IOException; }

    public record View(UUID draftId, DocumentSummary summary, String notice, Instant generatedAt,
            boolean approved, String extractionDisclosure, String originalSha256, String version, Instant revisedAt) {
        @Override public String toString() { return "SummaryReviewView[REDACTED]"; }
    }

    /**
     * Exact bytes and the approved notice travel together. The consuming sender must recheck its
     * own staff/device permissions and bind this package to its final signed PDF and outbox row.
     * This value is not transport authorization and must never be accepted from a browser.
     */
    public static final class ApprovedPackage implements AutoCloseable {
        private byte[] original;
        private final DocumentSummaryReviewBinding.Approval approval;
        private ApprovedPackage(byte[] original, DocumentSummaryReviewBinding.Approval approval) {
            this.original = original;
            this.approval = approval;
        }
        public synchronized byte[] copyOriginal() {
            if (original == null) throw new IllegalStateException("Summary package is closed.");
            return original.clone();
        }
        public DocumentSummaryReviewBinding.Approval approval() { return approval; }
        @Override public synchronized void close() {
            if (original != null) { Arrays.fill(original, (byte) 0); original = null; }
        }
        @Override public String toString() { return "ApprovedSummaryPackage[REDACTED]"; }
    }

    private DocumentSummaryReviewSession(DocumentSummaryReviewBinding.Scope scope, String provider, int securityId,
            DocumentSummaryReviewBinding.CapturedSource captured, DocumentSummaryReviewBinding.Draft draft,
            DocumentSummaryReviewBinding binding, Clock clock) {
        this.scope = scope;
        this.provider = provider;
        this.securityId = securityId;
        this.clock = clock;
        this.binding = binding;
        this.createdAt = clock.instant();
        this.expiresAt = createdAt.plus(Duration.ofMinutes(15));
        this.captured = captured;
        this.draft = draft;
    }

    public static DocumentSummaryReviewSession generate(DocumentSummaryReviewBinding.Scope scope,
            DocumentSummaryReviewBinding.Metadata metadata, SourceLoader loader, DocumentSummaryService generator,
            String modelIdentity, LoggedInInfo actor) throws IOException, ClinicalSummaryGenerationException {
        return generate(scope, metadata, loader, generator, modelIdentity, actor, Clock.systemUTC());
    }

    static DocumentSummaryReviewSession generate(DocumentSummaryReviewBinding.Scope scope,
            DocumentSummaryReviewBinding.Metadata metadata, SourceLoader loader, DocumentSummaryService generator,
            String modelIdentity, LoggedInInfo actor, Clock clock) throws IOException, ClinicalSummaryGenerationException {
        requireActor(actor);
        String provider = actor.getLoggedInProvider().getProviderNo();
        int securityId = actor.getLoggedInSecurity().getSecurityNo();
        Objects.requireNonNull(scope); Objects.requireNonNull(metadata);
        Objects.requireNonNull(loader); Objects.requireNonNull(generator); Objects.requireNonNull(clock);
        synchronized (LOCK) {
            // No background thread: a new generation also disposes expired reviews in other sessions.
            for (var previous : Set.copyOf(ACTIVE)) if (previous.expired()) previous.close();
            if (reserved >= MAX_REVIEWS) throw new CapacityFullException("Summary review capacity (" + MAX_REVIEWS + ") is full. Close an existing review and try again.");
            reserved++;
        }
        DocumentSummaryReviewBinding.CapturedSource capture = null;
        boolean retained = false;
        byte[] loaded = null;
        try {
            loaded = loader.load();
            capture = DocumentSummaryReviewBinding.captureOwned(scope, metadata, loaded);
            Arrays.fill(loaded, (byte) 0); loaded = null;
            var binding = new DocumentSummaryReviewBinding(clock);
            String text = capture.source().extraction().text();
            if (text.isBlank()) throw new IllegalStateException("Readable source text is required for review.");
            var summary = generator.summarize(text);
            var draft = binding.prepare(capture.source(), summary, modelIdentity, "en");
            var review = new DocumentSummaryReviewSession(scope, provider, securityId, capture, draft, binding, clock);
            synchronized (LOCK) { ACTIVE.add(review); }
            retained = true;
            return review;
        } finally {
            if (loaded != null) Arrays.fill(loaded, (byte) 0);
            if (!retained) {
                if (capture != null) capture.close();
                synchronized (LOCK) { reserved--; }
            }
        }
    }

    /** The process-wide cap refused a generation before any original was loaded or model called. */
    public static final class CapacityFullException extends IllegalStateException {
        CapacityFullException(String message) { super(message); }
    }

    /** The cap in force, read once at class load from {@link #MAX_ACTIVE}. */
    public static int capacity() { return MAX_REVIEWS; }

    /** A whole number from 1 to 32; anything else (missing, blank, text, out of range) means the default of 4. */
    static int configuredCapacity(String raw) {
        if (raw == null) return DEFAULT_MAX_REVIEWS;
        try {
            int value = Integer.parseInt(raw.strip());
            return value >= 1 && value <= MAX_CONFIGURABLE_REVIEWS ? value : DEFAULT_MAX_REVIEWS;
        } catch (NumberFormatException invalid) { return DEFAULT_MAX_REVIEWS; }
    }
    private static String configuredValue() {
        // Read once; an unreadable configuration keeps the default rather than failing class loading.
        // hasProperty first: getProperty warns once per JVM about an unset key, and the default
        // path (no key) must start quietly.
        try {
            var properties = CarlosProperties.getInstance();
            return properties.hasProperty(MAX_ACTIVE) ? properties.getProperty(MAX_ACTIVE, "") : "";
        } catch (RuntimeException unavailable) { return ""; }
    }

    /** Exact source bytes for the authenticated clinician's original-document download. */
    public byte[] copyOriginalForReview(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            UUID displayedDraftId, DocumentSummaryReviewBinding.Source currentSource) {
        synchronized (LOCK) {
            authorize(actor, currentScope, currentSource);
            if (!draft.id().equals(displayedDraftId)) throw stale();
            return captured.copyBytes();
        }
    }

    /** True only for the current server login, independent of document/token staleness. */
    public boolean samePrincipal(LoggedInInfo actor) {
        synchronized (LOCK) {
            return actor != null && actor.getLoggedInProvider() != null && actor.getLoggedInSecurity() != null
                    && provider.equals(actor.getLoggedInProvider().getProviderNo())
                    && Objects.equals(securityId, actor.getLoggedInSecurity().getSecurityNo());
        }
    }

    /** Identity guard for cleanup ownership; it grants no source access or approval capability. */
    public boolean belongsTo(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope, UUID displayedDraftId) {
        synchronized (LOCK) {
            return !closed && samePrincipal(actor) && scope.equals(currentScope)
                    && (displayedDraftId == null || draft.id().equals(displayedDraftId));
        }
    }

    public View view(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            DocumentSummaryReviewBinding.Source currentSource) {
        synchronized (LOCK) {
            authorize(actor, currentScope, currentSource);
            return new View(draft.id(), draft.summary(), approval == null ? draft.previewNotice() : approval.patientNotice(),
                    draft.generatedAt(), approval != null, draft.extractionDisclosure(),
                    captured.source().originalSha256(), draft.version(), draft.revisedAt());
        }
    }

    public void approve(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            UUID displayedDraftId, DocumentSummaryReviewBinding.Source currentSource, boolean affirmativeReview) {
        synchronized (LOCK) {
            authorize(actor, currentScope, currentSource);
            if (!draft.id().equals(displayedDraftId)) throw stale();
            var recorded = binding.approve(draft, displayedDraftId, currentSource, actor, affirmativeReview);
            // A duplicate form submission keeps the original server-recorded review time.
            if (approval == null) approval = recorded;
        }
    }

    /** A content update never affirms review, changes original custody or extends this session's expiry. */
    public void revise(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            UUID displayedDraftId, DocumentSummaryReviewBinding.Source currentSource,
            String overview, List<String> pointTexts, Set<Integer> removedPoints) {
        synchronized (LOCK) {
            authorize(actor, currentScope, currentSource);
            if (!draft.id().equals(displayedDraftId)) throw stale();
            var revised = binding.revise(draft, overview, pointTexts, removedPoints);
            draft = revised;
            approval = null;
        }
    }

    /** Inclusion is a separate explicit action after review; merely visiting the page grants none. */
    public ApprovedPackage forDelivery(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            UUID displayedDraftId, DocumentSummaryReviewBinding.Source currentSource, boolean includeSummary) {
        synchronized (LOCK) {
            authorize(actor, currentScope, currentSource);
            if (!includeSummary || approval == null || !draft.id().equals(displayedDraftId)) {
                throw new IllegalStateException("Explicit inclusion and current clinician approval are required.");
            }
            approval.requireCurrent(currentSource, draft);
            return new ApprovedPackage(captured.copyBytes(), approval);
        }
    }

    private void authorize(LoggedInInfo actor, DocumentSummaryReviewBinding.Scope currentScope,
            DocumentSummaryReviewBinding.Source currentSource) {
        requireActor(actor);
        if (closed) throw stale();
        if (!provider.equals(actor.getLoggedInProvider().getProviderNo())
                || securityId != actor.getLoggedInSecurity().getSecurityNo() || !scope.equals(currentScope)) {
            throw new SecurityException("Summary review belongs to a different authenticated scope.");
        }
        if (expired()) { close(); throw stale(); }
        try { draft.requireCurrent(currentSource); }
        catch (IllegalStateException changed) { close(); throw changed; }
    }

    private static void requireActor(LoggedInInfo actor) {
        if (actor == null || actor.getLoggedInProvider() == null
                || actor.getLoggedInProvider().getProviderNo() == null
                || actor.getLoggedInProvider().getProviderNo().isBlank()
                || actor.getLoggedInSecurity() == null || actor.getLoggedInSecurity().getSecurityNo() == null
                || actor.getLoggedInSecurity().getSecurityNo() <= 0) {
            throw new SecurityException("Authenticated reviewer session required.");
        }
    }
    private boolean expired() { return clock.instant().isBefore(createdAt) || !clock.instant().isBefore(expiresAt); }
    private static IllegalStateException stale() { return new IllegalStateException("This document review expired or changed. Generate a fresh draft."); }
    @Override public void valueUnbound(HttpSessionBindingEvent event) { close(); }
    @Override public void close() {
        synchronized (LOCK) {
            if (!closed) {
                closed = true;
                captured.close(); captured = null; draft = null; approval = null;
                ACTIVE.remove(this); reserved--;
            }
        }
    }
    @Override public String toString() { return "DocumentSummaryReviewSession[REDACTED]"; }
}
