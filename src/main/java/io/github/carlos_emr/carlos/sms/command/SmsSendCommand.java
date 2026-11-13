package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;

import java.time.Instant;

/**
 * One text to send. {@code expiresAt}, when set, is the moment after which the text must not be sent at all
 * (an appointment reminder must never arrive after its appointment, #884): a text still waiting then is failed,
 * not sent late. {@code null} means no limit, as for every text before.
 */
public record SmsSendCommand(
        Integer demographicNo,
        String recipientPhoneNumber,
        SmsRecipientPhoneType recipientPhoneType,
        String body,
        SmsMessagePurpose messagePurpose,
        String requestedByHealthcareProviderNo,
        Integer requestedBySecurityNo,
        Integer appointmentNo,
        Instant expiresAt
) {
    public SmsSendCommand {
        if (messagePurpose == null) {
            messagePurpose = SmsMessagePurpose.PATIENT_MESSAGE;
        }
    }

    /** A text with no time limit. */
    public SmsSendCommand(Integer demographicNo, String recipientPhoneNumber, SmsRecipientPhoneType recipientPhoneType,
                          String body, SmsMessagePurpose messagePurpose, String requestedByHealthcareProviderNo,
                          Integer requestedBySecurityNo, Integer appointmentNo) {
        this(demographicNo, recipientPhoneNumber, recipientPhoneType, body, messagePurpose,
                requestedByHealthcareProviderNo, requestedBySecurityNo, appointmentNo, null);
    }

    /**
     * An appointment reminder (#884) to the patient's cell number. Its purpose is set here, server side, never
     * from request data. It names its appointment, and is never sent after {@code expiresAt}.
     *
     * @param requestedByHealthcareProviderNo who asked for the reminder (staff who ticked the box)
     * @param expiresAt the latest moment it may still be sent
     */
    public static SmsSendCommand appointmentReminder(Integer demographicNo, String cellPhoneNumber, String body,
                                                     String requestedByHealthcareProviderNo, Integer appointmentNo,
                                                     Instant expiresAt) {
        return new SmsSendCommand(demographicNo, cellPhoneNumber, SmsRecipientPhoneType.CELL, body,
                SmsMessagePurpose.APPOINTMENT_REMINDER, requestedByHealthcareProviderNo, null, appointmentNo, expiresAt);
    }

    /** Whether the text may no longer be sent at {@code now}: its limit has been reached. */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public static SmsSendCommand patientMessage(
            Integer demographicNo,
            String recipientPhoneNumber,
            String body,
            String requestedByHealthcareProviderNo
    ) {
        return patientMessage(demographicNo, recipientPhoneNumber, body, requestedByHealthcareProviderNo, null);
    }

    public static SmsSendCommand patientMessage(
            Integer demographicNo,
            String recipientPhoneNumber,
            String body,
            String requestedByHealthcareProviderNo,
            Integer requestedBySecurityNo
    ) {
        return patientMessage(
                demographicNo,
                recipientPhoneNumber,
                SmsRecipientPhoneType.CELL,
                body,
                requestedByHealthcareProviderNo,
                requestedBySecurityNo
        );
    }

    public static SmsSendCommand patientMessage(
            Integer demographicNo,
            String recipientPhoneNumber,
            SmsRecipientPhoneType recipientPhoneType,
            String body,
            String requestedByHealthcareProviderNo
    ) {
        return patientMessage(
                demographicNo,
                recipientPhoneNumber,
                recipientPhoneType,
                body,
                requestedByHealthcareProviderNo,
                null
        );
    }

    public static SmsSendCommand patientMessage(
            Integer demographicNo,
            String recipientPhoneNumber,
            SmsRecipientPhoneType recipientPhoneType,
            String body,
            String requestedByHealthcareProviderNo,
            Integer requestedBySecurityNo
    ) {
        return new SmsSendCommand(
                demographicNo,
                recipientPhoneNumber,
                recipientPhoneType,
                body,
                SmsMessagePurpose.PATIENT_MESSAGE,
                requestedByHealthcareProviderNo,
                requestedBySecurityNo,
                null
        );
    }
    @Override
    public String toString() {
        return "SmsSendCommand[redacted]";
    }
}
