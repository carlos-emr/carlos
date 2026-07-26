/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Sanitized summary of conditions that can make a rendered clinical document incomplete.
 *
 * <p>The report intentionally contains counts and booleans only. Resource URLs, DOM text, and
 * other rendered content must not cross this boundary because they can contain PHI. The digest is
 * stable for a given issue set and binds an approval to exactly the conditions shown to the user.</p>
 */
public record EFormRenderCompletenessReport(
        int failedContentResources,
        int excludedContentElements,
        int severeConsoleErrors,
        int containedInteractions,
        boolean signatureMissing,
        boolean timerCompatibilityFailure,
        boolean stabilizationCapped,
        boolean labDecisionSupportStubbed) implements Serializable {

    private static final long serialVersionUID = 3L;

    /**
     * Counters are grouped ahead of the flags deliberately. All three counts and all four flags are
     * same-typed and carry different clinical meanings, so an interleaved layout would let a
     * transposed argument compile cleanly and silently reclassify one omission as another.
     */
    public EFormRenderCompletenessReport {
        if (failedContentResources < 0 || excludedContentElements < 0 || severeConsoleErrors < 0
                || containedInteractions < 0) {
            throw new IllegalArgumentException("Incomplete-render counters must not be negative");
        }
    }

    public static EFormRenderCompletenessReport complete() {
        return new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, false);
    }

    public boolean isComplete() {
        return failedContentResources == 0
                && excludedContentElements == 0
                && severeConsoleErrors == 0
                && containedInteractions == 0
                && !signatureMissing
                && !timerCompatibilityFailure
                && !stabilizationCapped
                && !labDecisionSupportStubbed;
    }

    public int issueCount() {
        int count = Math.addExact(failedContentResources, excludedContentElements);
        count = Math.addExact(count, severeConsoleErrors);
        count = Math.addExact(count, containedInteractions);
        count = Math.addExact(count, signatureMissing ? 1 : 0);
        count = Math.addExact(count, timerCompatibilityFailure ? 1 : 0);
        count = Math.addExact(count, stabilizationCapped ? 1 : 0);
        return Math.addExact(count, labDecisionSupportStubbed ? 1 : 0);
    }

    public EFormRenderCompletenessReport merge(EFormRenderCompletenessReport other) {
        if (other == null) {
            return this;
        }
        return new EFormRenderCompletenessReport(
                Math.addExact(failedContentResources, other.failedContentResources),
                Math.addExact(excludedContentElements, other.excludedContentElements),
                Math.addExact(severeConsoleErrors, other.severeConsoleErrors),
                Math.addExact(containedInteractions, other.containedInteractions),
                signatureMissing || other.signatureMissing,
                timerCompatibilityFailure || other.timerCompatibilityFailure,
                stabilizationCapped || other.stabilizationCapped,
                labDecisionSupportStubbed || other.labDecisionSupportStubbed);
    }

    /**
     * Returns a PHI-free digest used for exact approval matching.
     */
    public String digest() {
        String canonical = failedContentResources + ":"
                + excludedContentElements + ":"
                + severeConsoleErrors + ":"
                + containedInteractions + ":"
                + signatureMissing + ":"
                + timerCompatibilityFailure + ":"
                + stabilizationCapped + ":"
                + labDecisionSupportStubbed;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }
}
