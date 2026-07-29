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
    private final String demographicNo;
    private final EFormRenderApprovalService.Operation operation;
    private final Map<Integer, String> issueDigests;
    private final Instant expiresAt;

    /**
     * @param demographicNo the patient the ticket was consumed for
     * @param operation the operation the clinician approved — an approval given for a preview is
     *        not consent to fax
     */
    EFormRenderApproval(String providerNo, String demographicNo,
            EFormRenderApprovalService.Operation operation,
            Map<Integer, String> issueDigests, Instant expiresAt) {
        this.providerNo = Objects.requireNonNull(providerNo, "providerNo must not be null");
        this.demographicNo = Objects.requireNonNull(demographicNo, "demographicNo must not be null");
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
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

    /**
     * Whether this approval was given for the same patient and operation as the one now being asked
     * for.
     *
     * <p>The service binds all of this at {@code consume} time, but the approval object used to
     * discard the operation and patient immediately afterwards — so when one is carried forward as
     * a {@code previousApproval} to seed a composite document's next ticket, nothing could check
     * that a PREVIEW approval was not being promoted into a FAX, or that digests approved for one
     * patient were not being attached to another's ticket. It held only because both carry-forward
     * call sites happen to pass their own operation consistently, which is a property of those call
     * sites rather than of this type.</p>
     */
    boolean coversSameScope(String expectedDemographicNo,
            EFormRenderApprovalService.Operation expectedOperation) {
        return demographicNo.equals(expectedDemographicNo) && operation == expectedOperation;
    }

    @Override
    public String toString() {
        return "[eform-render-approval]";
    }
}
