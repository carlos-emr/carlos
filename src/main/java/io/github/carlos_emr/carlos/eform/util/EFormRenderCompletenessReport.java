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

    /**
     * Whether the render produced no reportable condition at all, advisory ones included.
     *
     * <p>This is the reporting predicate, not the gating one — see {@link #hasBlockingOmissions()}
     * for what actually withholds a document. Approval binds a digest over every component, so this
     * must keep considering all of them.</p>
     */
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

    /**
     * Whether any condition present is serious enough to withhold the document pending approval.
     *
     * <p>Every component blocks except {@link #severeConsoleErrors}, which is advisory: it counts
     * uncaught exceptions thrown by the <em>form's own</em> script. Across the shared-eForm corpus
     * that is the single most common condition — decades-old hand-authored forms routinely throw
     * once during load (a {@code getElementById(...)} returning null for a field the form no longer
     * has) while rendering every bit of their clinical content correctly. Blocking on it withheld
     * complete documents far more often than it caught truncated ones.</p>
     *
     * <p>It stays in the report rather than being discarded, because a script that aborted midway
     * through injecting a score, a dose or a letter body leaves no other observable — every
     * subresource returned 200 and the page divs still measure. Callers that can show it must; see
     * {@link #advisoryIssueCount()}.</p>
     */
    public boolean hasBlockingOmissions() {
        return blockingIssueCount() > 0;
    }

    /** Count of conditions that withhold the document. */
    public int blockingIssueCount() {
        return Math.subtractExact(issueCount(), advisoryIssueCount());
    }

    /**
     * Count of conditions that are reported to the user but never withhold the document.
     *
     * <p>{@link #timerCompatibilityFailure} joins {@link #severeConsoleErrors} here. It reports that
     * one of the form's own legacy string timers threw. That is worth telling the reader, but it is
     * not sound grounds for withholding the document: measured across the shared-form corpus, the
     * overwhelmingly common such timer is {@code setTimeout("SubmitButton.click()", 1800)} — a form
     * trying to submit itself — whose failure on a render surface is the correct outcome, not a lost
     * field. The renderer now waits for these timers to run before capturing (see the shim's
     * {@code whenIdle}), so a failure here is a real one rather than a race, and a timer that
     * populates content gets to do so.</p>
     */
    public int advisoryIssueCount() {
        return Math.addExact(severeConsoleErrors, timerCompatibilityFailure ? 1 : 0);
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

    /**
     * Names the conditions that are present, for operator diagnosis.
     *
     * <p>Every gate log used to carry aggregate counts only ({@code issues=9 blocking=2}), which
     * says how many but never which — so diagnosing a blocked render meant inferring components from
     * the <em>absence</em> of other log lines, and the three that have no log line of their own
     * could not be distinguished at all. Each entry is a fixed identifier plus a count, so this is
     * safe by construction under the same counts-and-booleans contract as the rest of the record:
     * no URL, filename or rendered text can reach it.</p>
     *
     * @param blockingOnly when true, omit advisory conditions (the ones that never withhold the
     *        document), so the caller can log precisely what caused a refusal
     * @return a compact {@code name=value} summary, or {@code "none"} when nothing qualifies
     */
    public String describe(boolean blockingOnly) {
        StringBuilder description = new StringBuilder();
        appendCount(description, "failedContentResources", failedContentResources);
        appendCount(description, "excludedContentElements", excludedContentElements);
        if (!blockingOnly) {
            appendCount(description, "severeConsoleErrors", severeConsoleErrors);
            appendFlag(description, "timerCompatibilityFailure", timerCompatibilityFailure);
        }
        appendCount(description, "containedInteractions", containedInteractions);
        appendFlag(description, "signatureMissing", signatureMissing);
        appendFlag(description, "stabilizationCapped", stabilizationCapped);
        appendFlag(description, "labDecisionSupportStubbed", labDecisionSupportStubbed);
        return description.isEmpty() ? "none" : description.toString();
    }

    private static void appendCount(StringBuilder target, String name, int value) {
        if (value > 0) {
            appendSeparator(target);
            target.append(name).append('=').append(value);
        }
    }

    private static void appendFlag(StringBuilder target, String name, boolean value) {
        if (value) {
            appendSeparator(target);
            target.append(name);
        }
    }

    private static void appendSeparator(StringBuilder target) {
        if (!target.isEmpty()) {
            target.append(' ');
        }
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
