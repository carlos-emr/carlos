/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.EmailLog;

/**
 * Result of one synchronous email-send attempt.
 *
 * <p>The transport outcome is deliberately separate from the persisted {@link EmailLog} status.
 * A message can be accepted by the transport while the subsequent status update fails or loses a
 * race with an administrator resolving the row. Callers must not interpret that persistence
 * problem as a transport failure and invite a duplicate send.</p>
 */
public final class EmailSendResult {

    public enum TransportOutcome {
        ACCEPTED,
        UNCONFIRMED,
        FAILED
    }

    private final EmailLog emailLog;
    private final TransportOutcome transportOutcome;
    private final boolean transportOutcomeRecorded;

    private EmailSendResult(EmailLog emailLog, TransportOutcome transportOutcome,
            boolean transportOutcomeRecorded) {
        this.emailLog = Objects.requireNonNull(emailLog, "emailLog must not be null");
        this.transportOutcome = Objects.requireNonNull(
                transportOutcome, "transportOutcome must not be null");
        this.transportOutcomeRecorded = transportOutcomeRecorded;
    }

    public static EmailSendResult accepted(EmailLog emailLog,
            boolean transportOutcomeRecorded) {
        return new EmailSendResult(
                emailLog, TransportOutcome.ACCEPTED, transportOutcomeRecorded);
    }

    public static EmailSendResult failed(EmailLog emailLog,
            boolean transportOutcomeRecorded) {
        return new EmailSendResult(
                emailLog, TransportOutcome.FAILED, transportOutcomeRecorded);
    }

    public static EmailSendResult unconfirmed(EmailLog emailLog) {
        return new EmailSendResult(emailLog, TransportOutcome.UNCONFIRMED, false);
    }

    public EmailLog getEmailLog() {
        return emailLog;
    }

    public TransportOutcome getTransportOutcome() {
        return transportOutcome;
    }

    public boolean isTransportAccepted() {
        return TransportOutcome.ACCEPTED.equals(transportOutcome);
    }

    public boolean isDeliveryUnconfirmed() {
        return TransportOutcome.UNCONFIRMED.equals(transportOutcome);
    }

    public boolean isTransportOutcomeRecorded() {
        return transportOutcomeRecorded;
    }
}
