/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.consultation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.carlos_emr.carlos.documentManager.ConsultAttachmentWarning;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-time consent to omit exactly the consultation attachments the provider reviewed.
 * Stores identifiers and reasons only, never PDF bytes, clinical content or request parameters.
 * Callers must authorize and render again, then check the resulting warnings before disclosure.
 */
@Service
public class ConsultAttachmentApprovalService {
    public static final String TOKEN = "consultAttachmentApproval";
    public static final String CONFIRM = "confirmAttachmentOmissions";
    public static final String REVIEW_REQUIRED = "attachmentReviewRequired";
    private static final Duration TTL = Duration.ofMinutes(10);
    private final Cache<String, Approval> approvals;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public enum Operation { PREVIEW, PRINT, FAX }

    /** The constructor Spring uses; the package-private one lets tests set the clock. */
    @Autowired
    public ConsultAttachmentApprovalService() {
        this(Clock.systemUTC());
    }

    ConsultAttachmentApprovalService(Clock clock) {
        this.clock = clock;
        approvals = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(TTL)
                .ticker(() -> clock.instant().toEpochMilli() * 1_000_000L).build();
    }

    /** Issues an opaque ticket for a warning list about to be shown to the authenticated provider. */
    public String issue(HttpServletRequest request, LoggedInInfo info, String consult, String patient,
            Operation operation, Collection<ConsultAttachmentWarning> warnings) {
        HttpSession session = Objects.requireNonNull(request.getSession(false), "Authenticated session required");
        String provider = Objects.requireNonNull(info.getLoggedInProviderNo(), "Provider required");
        Set<String> issues = issues(warnings);
        if (issues.isEmpty()) throw new IllegalArgumentException("Approval requires attachment warnings");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        approvals.put(token, new Approval(session.getId(), provider, consult, patient, operation,
                issues, clock.instant().plus(TTL)));
        return token;
    }

    /** Atomically consumes consent; scope changes, expiry, replay and changed reasons are refused. */
    public boolean consume(HttpServletRequest request, LoggedInInfo info, String consult, String patient,
            Operation operation, Collection<ConsultAttachmentWarning> warnings) {
        return consumeDecision(request, info, consult, patient, operation, warnings) == Decision.ACCEPTED;
    }

    private enum Decision { UNCONFIRMED, ACCEPTED, CHANGED, INVALID }

    private Decision consumeDecision(HttpServletRequest request, LoggedInInfo info, String consult, String patient,
            Operation operation, Collection<ConsultAttachmentWarning> warnings) {
        String token = request.getParameter(TOKEN);
        if (token == null || !"true".equals(request.getParameter(CONFIRM))) return Decision.UNCONFIRMED;
        Approval approval = approvals.asMap().remove(token);
        HttpSession session = request.getSession(false);
        if (approval == null || session == null || info == null
                || !clock.instant().isBefore(approval.expires())
                || !approval.session().equals(session.getId())
                || !approval.provider().equals(info.getLoggedInProviderNo())
                || !approval.consult().equals(consult) || !approval.patient().equals(patient)
                || approval.operation() != operation) return Decision.INVALID;
        if (!approval.issues().equals(issues(warnings))) return Decision.CHANGED;
        // Do not include attachment titles, filenames, render exceptions or clinical text.
        MiscUtils.getLogger().info("Consultation attachment omissions approved: operation={} count={}",
                operation, warnings.size());
        return Decision.ACCEPTED;
    }

    /**
     * Withholds an unapproved packet. A changed issue set gets a new review; a used/expired or
     * mismatched ticket gets an error, never a resend invitation. A concurrent request may already
     * have delivered the packet, so an invalid ticket must not claim that nothing was sent.
     */
    public boolean requiresReview(HttpServletRequest request, LoggedInInfo info, String consult, String patient,
            Operation operation, Collection<ConsultAttachmentWarning> warnings) {
        Decision decision = consumeDecision(request, info, consult, patient, operation, warnings);
        if (decision == Decision.INVALID) {
            request.setAttribute("errorMessage", ConsultAttachmentWarning.bundleText(request.getLocale(),
                    "consultation.attachments.approvalInvalid"));
            return true;
        }
        if (warnings.isEmpty() || decision == Decision.ACCEPTED) return false;
        request.setAttribute(TOKEN, issue(request, info, consult, patient, operation, warnings));
        request.setAttribute(REVIEW_REQUIRED, Boolean.TRUE);
        return true;
    }

    private static Set<String> issues(Collection<ConsultAttachmentWarning> warnings) {
        return warnings.stream().map(warning -> warning.getKey() + ":" + warning.getReason())
                .collect(Collectors.toUnmodifiableSet());
    }

    private record Approval(String session, String provider, String consult, String patient,
            Operation operation, Set<String> issues, Instant expires) { }
}
