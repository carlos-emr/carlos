package io.github.carlos_emr.carlos.sms.command;

import io.github.carlos_emr.carlos.sms.SmsTransactionType;

public record SmsSendCommand(
        Integer demographicNo,
        String recipientPhoneNumber,
        String body,
        SmsTransactionType transactionType,
        String requestedByProviderNo,
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
            String requestedByProviderNo
    ) {
        return direct(demographicNo, recipientPhoneNumber, body, requestedByProviderNo, null);
    }

    public static SmsSendCommand direct(
            Integer demographicNo,
            String recipientPhoneNumber,
            String body,
            String requestedByProviderNo,
            Integer requestedBySecurityNo
    ) {
        return new SmsSendCommand(
                demographicNo,
                recipientPhoneNumber,
                body,
                SmsTransactionType.DIRECT,
                requestedByProviderNo,
                requestedBySecurityNo,
                null
        );
    }
}
