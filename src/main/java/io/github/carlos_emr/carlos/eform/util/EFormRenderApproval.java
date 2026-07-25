/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Server-issued capability accepting one exact incomplete eForm render.
 *
 * <p>Instances can only be created by {@link EFormRenderApprovalService}. Web request parameters
 * are never interpreted directly as approval.</p>
 */
public final class EFormRenderApproval {

    private final String providerNo;
    private final Map<Integer, String> issueDigests;
    private final Instant expiresAt;

    EFormRenderApproval(String providerNo, Map<Integer, String> issueDigests, Instant expiresAt) {
        this.providerNo = Objects.requireNonNull(providerNo, "providerNo must not be null");
        this.issueDigests = Map.copyOf(
                Objects.requireNonNull(issueDigests, "issueDigests must not be null"));
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * Checks the identity, expiry, and exact issue set observed during the rerender.
     */
    public boolean permits(int renderedFdid, String renderedProviderNo,
            EFormRenderCompletenessReport report) {
        return providerNo.equals(renderedProviderNo)
                && Instant.now().isBefore(expiresAt)
                && report != null
                && report.digest().equals(issueDigests.get(renderedFdid));
    }

    Map<Integer, String> issueDigests() {
        return issueDigests;
    }

    boolean belongsTo(String expectedProviderNo) {
        return providerNo.equals(expectedProviderNo);
    }

    @Override
    public String toString() {
        return "[eform-render-approval]";
    }
}
