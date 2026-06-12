package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsTransactionType;

public record SmsSendCommand(
        Integer demographicNo,
        String recipientPhoneNumber,
        SmsRecipientPhoneType recipientPhoneType,
        String body,
        SmsTransactionType transactionType,
        String requestedByHealthcareProviderNo,
        Integer requestedBySecurityNo,
        Integer appointmentNo
) {
    public SmsSendCommand {
        if (transactionType == null) {
            transactionType = SmsTransactionType.DIRECT;
        }
    }

    public static SmsSendCommand direct(
            Integer demographicNo,
            String recipientPhoneNumber,
            String body,
            String requestedByHealthcareProviderNo
    ) {
        return direct(demographicNo, recipientPhoneNumber, body, requestedByHealthcareProviderNo, null);
    }

    public static SmsSendCommand direct(
            Integer demographicNo,
            String recipientPhoneNumber,
            String body,
            String requestedByHealthcareProviderNo,
            Integer requestedBySecurityNo
    ) {
        return direct(
                demographicNo,
                recipientPhoneNumber,
                SmsRecipientPhoneType.CELL,
                body,
                requestedByHealthcareProviderNo,
                requestedBySecurityNo
        );
    }

    public static SmsSendCommand direct(
            Integer demographicNo,
            String recipientPhoneNumber,
            SmsRecipientPhoneType recipientPhoneType,
            String body,
            String requestedByHealthcareProviderNo
    ) {
        return direct(
                demographicNo,
                recipientPhoneNumber,
                recipientPhoneType,
                body,
                requestedByHealthcareProviderNo,
                null
        );
    }

    public static SmsSendCommand direct(
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
                SmsTransactionType.DIRECT,
                requestedByHealthcareProviderNo,
                requestedBySecurityNo,
                null
        );
    }
}
