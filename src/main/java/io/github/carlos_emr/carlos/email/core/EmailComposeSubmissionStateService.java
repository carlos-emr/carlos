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
package io.github.carlos_emr.carlos.email.core;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Short-lived server-side cache for one-time email compose submission state.
 *
 * @since 2026-07-23
 */
@Service
public class EmailComposeSubmissionStateService {
    public static final String EMAIL_PDF_PASSWORD_TOKEN_PARAM = "emailPDFPasswordToken";
    public static final String EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION_KEY =
            "email.compose.deliveryNote.defaultInstruction";
    public static final String DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION =
            "Deliver this password to the patient separately. It is not included in the email.";
    public static final int MAX_PENDING_EMAIL_COMPOSE_STATES = 8;
    public static final int MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES = 1024;
    public static final long PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS = 30L * 60 * 1000;
    static final long PENDING_EMAIL_COMPOSE_STATE_CLEANUP_INTERVAL_MILLIS = 60L * 1000;

    private static final Logger logger = MiscUtils.getLogger();

    private final Object lock = new Object();
    private final Map<EmailComposeSubmissionStateKey, EmailComposeSubmissionState> pendingStates =
            new LinkedHashMap<>();
    private final AtomicReference<ScheduledExecutorService> pruner = new AtomicReference<>();
    private final Clock clock;
    private boolean shutdown;

    public EmailComposeSubmissionStateService() {
        this(Clock.systemUTC());
    }

    EmailComposeSubmissionStateService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Stores generated compose state for one form submission.
     *
     * @param session HTTP session that owns the compose token
     * @param emailPDFPassword generated PDF passphrase to use if encryption is submitted
     * @param emailPDFPasswordClue delivery instruction to show with the generated passphrase
     * @param emailAttachmentList prepared attachment list to bind to the compose token
     * @return opaque token that must be submitted back with the compose form
     * @throws IllegalStateException if the cache is shut down or full
     * @since 2026-07-23
     */
    public String store(
            HttpSession session,
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList
    ) {
        return store(
                session,
                emailPDFPassword,
                emailPDFPasswordClue,
                emailAttachmentList,
                EmailComposeSubmissionContext.direct(""));
    }

    /**
     * Stores generated compose state and server-owned routing context for one form submission.
     *
     * <p>The returned token is scoped to the supplied HTTP session. Stored attachments are copied
     * before insertion, expired entries are pruned, and per-session/global cache capacity limits are
     * enforced before the state is accepted. A {@code null} context is treated as a direct-email
     * context with a blank demographic id.</p>
     *
     * @param session HTTP session that owns the compose token
     * @param emailPDFPassword generated PDF passphrase to use if encryption is submitted
     * @param emailPDFPasswordClue delivery instruction to show with the generated passphrase
     * @param emailAttachmentList prepared attachment list to bind to the compose token
     * @param context server-owned demographic, transaction, and eForm routing context
     * @return opaque token that must be submitted back with the compose form
     * @throws IllegalStateException if the cache is shut down or full
     * @since 2026-07-23
     */
    public String store(
            HttpSession session,
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList,
            EmailComposeSubmissionContext context
    ) {
        String sessionId = session.getId();
        String token = UUID.randomUUID().toString();
        long createdAtMillis = clock.millis();
        EmailComposeSubmissionState state = new EmailComposeSubmissionState(
                emailPDFPassword,
                emailPDFPasswordClue,
                copyAttachments(emailAttachmentList),
                createdAtMillis,
                context == null ? EmailComposeSubmissionContext.direct("") : context);

        synchronized (lock) {
            ensureOpen();
            pruneExpired(createdAtMillis);
            ensureCapacity(sessionId);
            ensurePrunerStarted();
            pendingStates.put(new EmailComposeSubmissionStateKey(sessionId, token), state);
            trim(sessionId);
        }
        return token;
    }

    /**
     * Generates a fresh PDF passphrase and stores the matching one-time compose token state.
     *
     * @param request HttpServletRequest used to bind the token to the active session
     * @param emailPdfPasswordService service used to generate the passphrase
     * @param emailAttachmentList prepared attachment list to bind to the compose token
     * @return generated passphrase display values and opaque submission token
     * @throws IllegalStateException if the compose submission state cache cannot store a new token
     * @since 2026-07-14
     */
    public EmailPdfPasswordSubmissionState preparePdfPasswordSubmissionState(
            HttpServletRequest request,
            EmailPdfPasswordService emailPdfPasswordService,
            List<EmailAttachment> emailAttachmentList
    ) {
        return preparePdfPasswordSubmissionState(
                request,
                emailPdfPasswordService,
                emailAttachmentList,
                EmailComposeSubmissionContext.direct(""));
    }

    /**
     * Generates a fresh PDF passphrase and stores one-time compose token state with context.
     *
     * <p>The passphrase is generated once for the compose page, the delivery instruction is resolved
     * from localized resources when available, and the attachments/context are stored server-side
     * under an opaque token for later single-use submission.</p>
     *
     * @param request HttpServletRequest used to bind the token to the active session
     * @param emailPdfPasswordService service used to generate the passphrase
     * @param emailAttachmentList prepared attachment list to bind to the compose token
     * @param context server-owned demographic, transaction, and eForm routing context
     * @return generated passphrase display values and opaque submission token
     * @throws IllegalStateException if the compose submission state cache cannot store a new token
     * @since 2026-07-23
     */
    public EmailPdfPasswordSubmissionState preparePdfPasswordSubmissionState(
            HttpServletRequest request,
            EmailPdfPasswordService emailPdfPasswordService,
            List<EmailAttachment> emailAttachmentList,
            EmailComposeSubmissionContext context
    ) {
        String emailPDFPassword = emailPdfPasswordService.generatePassphrase();
        String emailPDFPasswordClue = resolveEmailPdfPasswordDeliveryInstruction(request);
        String emailPDFPasswordToken = store(
                request.getSession(), emailPDFPassword, emailPDFPasswordClue, emailAttachmentList, context);
        return new EmailPdfPasswordSubmissionState(
                emailPDFPassword, emailPDFPasswordClue, emailPDFPasswordToken);
    }

    /**
     * Consumes generated compose state for the submitted token.
     *
     * @param request HttpServletRequest containing the compose token parameter
     * @param tokenParameterName name of the request parameter containing the token
     * @return compose state for the current session and token, or {@code null}
     * @since 2026-07-23
     */
    public EmailComposeSubmissionState consume(HttpServletRequest request, String tokenParameterName) {
        String token = request.getParameter(tokenParameterName);
        if (token == null || token.isBlank()) {
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        synchronized (lock) {
            pruneExpired(clock.millis());
            return pendingStates.remove(new EmailComposeSubmissionStateKey(session.getId(), token));
        }
    }

    /**
     * Consumes generated compose state for the submitted email compose token parameter.
     *
     * @param request HttpServletRequest containing the compose token parameter
     * @return compose state for the current session and token, or {@code null}
     * @since 2026-07-23
     */
    public EmailComposeSubmissionState consume(HttpServletRequest request) {
        return consume(request, EMAIL_PDF_PASSWORD_TOKEN_PARAM);
    }

    /**
     * Clears pending compose states for a destroyed HTTP session.
     *
     * @param sessionId HTTP session id whose pending compose states should be removed
     * @return number of pending compose states removed
     * @since 2026-07-23
     */
    public int clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }

        synchronized (lock) {
            int originalSize = pendingStates.size();
            pendingStates.keySet().removeIf(key -> key.sessionId().equals(sessionId));
            return originalSize - pendingStates.size();
        }
    }

    /**
     * Releases cached compose state during bean destruction.
     *
     * @since 2026-07-23
     */
    @PreDestroy
    public void destroy() {
        shutdown();
    }

    /**
     * Stops the background pruner and clears the cache.
     *
     * @return number of pending compose states removed from the cache
     * @since 2026-07-23
     */
    public int shutdown() {
        synchronized (lock) {
            shutdown = true;
            ScheduledExecutorService scheduledPruner = pruner.getAndSet(null);
            if (scheduledPruner != null) {
                scheduledPruner.shutdownNow();
            }
            int originalSize = pendingStates.size();
            pendingStates.clear();
            return originalSize;
        }
    }

    /**
     * Clears a destroyed session's compose state without failing session teardown if Spring
     * is already unavailable.
     *
     * @param sessionId HTTP session id whose pending compose states should be removed
     * @return number of pending compose states removed
     * @since 2026-07-23
     */
    public static int clearDestroyedSessionIfAvailable(String sessionId) {
        try {
            return SpringUtils.getBean(EmailComposeSubmissionStateService.class).clear(sessionId);
        } catch (RuntimeException e) {
            logger.warn("Unable to clear email compose submission states because the service is unavailable", e);
            return 0;
        }
    }

    /**
     * Resolves the non-secret delivery instruction displayed with generated passphrases.
     *
     * @param request current request, or {@code null} to use the default resource bundle
     * @return localized delivery instruction, or a safe default when missing
     * @since 2026-07-14
     */
    public static String resolveEmailPdfPasswordDeliveryInstruction(HttpServletRequest request) {
        try {
            ResourceBundle resources = request == null
                    ? ResourceBundle.getBundle("oscarResources")
                    : ResourceBundle.getBundle("oscarResources", request.getLocale());
            return resources.getString(EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION_KEY);
        } catch (MissingResourceException e) {
            logger.warn("Missing email PDF password delivery instruction resource");
            return DEFAULT_EMAIL_PDF_PASSWORD_DELIVERY_INSTRUCTION;
        }
    }

    private void ensurePrunerStarted() {
        if (pruner.get() != null) {
            return;
        }

        ScheduledExecutorService scheduledPruner = Executors.newSingleThreadScheduledExecutor(new DeamonThreadFactory(
                "EmailComposeSubmissionStatePruner", Thread.NORM_PRIORITY));
        scheduledPruner.scheduleAtFixedRate(
                this::pruneExpired,
                PENDING_EMAIL_COMPOSE_STATE_CLEANUP_INTERVAL_MILLIS,
                PENDING_EMAIL_COMPOSE_STATE_CLEANUP_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
        pruner.set(scheduledPruner);
    }

    private static List<EmailAttachment> copyAttachments(List<EmailAttachment> emailAttachmentList) {
        if (emailAttachmentList == null || emailAttachmentList.isEmpty()) {
            return List.of();
        }
        return emailAttachmentList.stream()
                .map(EmailComposeSubmissionStateService::copyAttachment)
                .toList();
    }

    private static EmailAttachment copyAttachment(EmailAttachment attachment) {
        return new EmailAttachment(
                attachment.getFileName(),
                attachment.getFilePath(),
                attachment.getDocumentType(),
                attachment.getDocumentId(),
                attachment.getFileSize());
    }

    private void ensureOpen() {
        if (shutdown) {
            throw new IllegalStateException("Email compose submission state cache is shut down");
        }
    }

    private void ensureCapacity(String sessionId) {
        boolean addingNewGlobalEntry = count(sessionId) < MAX_PENDING_EMAIL_COMPOSE_STATES;
        if (addingNewGlobalEntry && pendingStates.size() >= MAX_PENDING_EMAIL_COMPOSE_SUBMISSION_STATES) {
            throw new IllegalStateException("Email compose submission state cache is full");
        }
    }

    private void pruneExpired() {
        try {
            synchronized (lock) {
                pruneExpired(clock.millis());
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to prune expired email compose submission states", e);
        }
    }

    private void pruneExpired(long now) {
        pendingStates.entrySet().removeIf(entry ->
                now - entry.getValue().createdAtMillis() > PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS);
    }

    private void trim(String sessionId) {
        while (count(sessionId) > MAX_PENDING_EMAIL_COMPOSE_STATES) {
            EmailComposeSubmissionStateKey oldestKey = oldestKey(sessionId);
            if (oldestKey == null) {
                return;
            }
            pendingStates.remove(oldestKey);
        }
    }

    private int count(String sessionId) {
        int count = 0;
        for (EmailComposeSubmissionStateKey key : pendingStates.keySet()) {
            if (key.sessionId().equals(sessionId)) {
                count++;
            }
        }
        return count;
    }

    private EmailComposeSubmissionStateKey oldestKey(String sessionId) {
        EmailComposeSubmissionStateKey oldestKey = null;
        long oldestCreatedAt = Long.MAX_VALUE;
        for (Map.Entry<EmailComposeSubmissionStateKey, EmailComposeSubmissionState> entry : pendingStates.entrySet()) {
            if (sessionId != null && !entry.getKey().sessionId().equals(sessionId)) {
                continue;
            }
            if (entry.getValue().createdAtMillis() < oldestCreatedAt) {
                oldestKey = entry.getKey();
                oldestCreatedAt = entry.getValue().createdAtMillis();
            }
        }
        return oldestKey;
    }

    private record EmailComposeSubmissionStateKey(String sessionId, String token) {
    }

    /**
     * Generated compose state associated with one opaque compose token.
     *
     * @since 2026-07-23
     */
    public record EmailComposeSubmissionState(
            String emailPDFPassword,
            String emailPDFPasswordClue,
            List<EmailAttachment> emailAttachmentList,
            long createdAtMillis,
            EmailComposeSubmissionContext context
    ) {
    }

    /**
     * Server-owned patient and eForm context bound to one compose submission token.
     *
     * @since 2026-07-23
     */
    public record EmailComposeSubmissionContext(
            String demographicId,
            String fdid,
            TransactionType transactionType,
            boolean openEFormAfterEmail,
            boolean deleteEFormAfterEmail
    ) {
        public EmailComposeSubmissionContext {
            demographicId = demographicId == null ? "" : demographicId;
            fdid = fdid == null ? "" : fdid;
            transactionType = transactionType == null ? TransactionType.DIRECT : transactionType;
        }

        /**
         * Creates direct-email context for a patient demographic.
         *
         * @param demographicId patient demographic id bound to the compose token
         * @return direct-email compose submission context
         * @since 2026-07-23
         */
        public static EmailComposeSubmissionContext direct(String demographicId) {
            return new EmailComposeSubmissionContext(demographicId, "", TransactionType.DIRECT, false, false);
        }

        /**
         * Creates eForm-email context for a patient demographic and source eForm.
         *
         * @param demographicId patient demographic id bound to the compose token
         * @param fdid eForm data id to reopen or delete after send
         * @param openEFormAfterEmail whether the eForm should be reopened after send
         * @param deleteEFormAfterEmail whether the eForm should be deleted after a successful send
         * @return eForm compose submission context
         * @since 2026-07-23
         */
        public static EmailComposeSubmissionContext eform(
                String demographicId,
                String fdid,
                boolean openEFormAfterEmail,
                boolean deleteEFormAfterEmail
        ) {
            return new EmailComposeSubmissionContext(
                    demographicId,
                    fdid,
                    TransactionType.EFORM,
                    openEFormAfterEmail,
                    deleteEFormAfterEmail);
        }
    }

    /**
     * Display values and token prepared for one email compose submission.
     *
     * @since 2026-07-14
     */
    public record EmailPdfPasswordSubmissionState(
            String emailPDFPassword,
            String emailPDFPasswordClue,
            String emailPDFPasswordToken
    ) {
    }
}
