package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;

public record SmsSendCommand(
        Integer demographicNo,
        String recipientPhoneNumber,
        SmsRecipientPhoneType recipientPhoneType,
        String body,
        SmsMessagePurpose messagePurpose,
        String requestedByHealthcareProviderNo,
        Integer requestedBySecurityNo,
        Integer appointmentNo
) {
    public SmsSendCommand {
        if (messagePurpose == null) {
            messagePurpose = SmsMessagePurpose.PATIENT_MESSAGE;
        }
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
