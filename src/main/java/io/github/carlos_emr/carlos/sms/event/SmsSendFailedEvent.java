package io.github.carlos_emr.carlos.sms.event;

import io.github.carlos_emr.carlos.sms.model.SmsTransaction;

/**
 * Published when an outbound SMS reaches a terminal {@code FAILED} state (provider rejection or
 * exception after retries are exhausted), so the failure can be surfaced to staff for follow-up.
 * <p>
 * Carries only operational identifiers (no message body or free-text error message) and is intended to be handled
 * {@code AFTER_COMMIT} so the failure is already durably persisted before anything reacts to it.
 */
public record SmsSendFailedEvent(
        Long transactionId,
        Integer demographicNo,
        String requestedByHealthcareProviderNo,
        Integer appointmentNo,
        String errorCode
) {
    public static SmsSendFailedEvent from(SmsTransaction transaction) {
        return new SmsSendFailedEvent(
                transaction.getId(),
                transaction.getDemographicNo(),
                transaction.getRequestedByHealthcareProviderNo(),
                transaction.getAppointmentNo(),
                transaction.getErrorCode()
        );
    }
}
